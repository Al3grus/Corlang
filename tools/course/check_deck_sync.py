# -*- coding: utf-8 -*-
"""Pack timing: is a themed block of vocabulary introduced before the lesson that teaches it?

Deck order IS the SRS introduction order (`WordsRepository.unlockedNewWords` serves
`allWords.take(lesson * 10)`), and the deck is authored FREQUENCY-FIRST on purpose. `DeckOrder.kt`
says so outright: the deck runs alongside the lessons rather than behind them, and only about half
of any course's deck ever appears in lesson text at all. So "every deck word must appear in a
lesson first" is NOT a rule, and a checker built on that idea reports a design decision as a bug.

The real defect, and the only one this file tests, is the one DeckOrder was written to prevent:

    a THEMED pack arriving long before the lesson that teaches its theme.

Croatian taught all twelve months as flashcards at lesson 8, sixteen lessons before "Days, months
and schedules" at lesson 25. Nobody learns siječanj cold from a card; they meet it, fail it, and it
comes back until the lesson finally explains what it was. `VocabPack.fromDay` fixes it by holding
the pack until its lesson, and `DeckOrder.ordered` reorders (never filters) so the deck keeps its
exact size.

Introduction lessons are computed through a port of `DeckOrder.ordered`, NOT from raw authored
position: a pack that is already gated is already correct, and measuring it raw reports it as
broken. That mistake is why the shipped `calendar` pack looked like a defect in the first survey.

Usage:  python check_deck_sync.py hr [--report]
"""
import io
import json
import os
import re
import sys

ROOT = os.path.join("app", "src", "main", "assets", "content")
PER_LESSON = 10

# How many of a pack's words a lesson must use before it counts as the lesson that teaches it.
THEME_HITS = 3
# A gap this size or larger is a defect. Below it the block is close enough to its lesson.
MAX_EARLY = 15

# A gate may never push a pack past its own CEFR level. hobbies-sport is an A1 pack whose theme
# lesson is 191, deep in B1: holding A1 vocabulary until B1 to satisfy a timing rule would starve
# the level it belongs to, which is worse than the drift it fixes. Where the theme lies beyond the
# level, the gate is capped at the level's last lesson and the residue is a PLAN observation (no
# lesson at that level covers the theme), reported separately rather than failed.
LEVEL_END = {"A0": 16, "A1": 77, "A2": 173, "B1": 344}


def load_packs(lang):
    idx = json.load(io.open(os.path.join(ROOT, lang, "vocab", "_index.json"), encoding="utf-8"))
    packs = []
    for f in idx:
        doc = json.load(io.open(os.path.join(ROOT, lang, "vocab", f), encoding="utf-8"))
        for p in doc["packs"]:
            packs.append({
                "id": p["id"],
                "file": f,
                "level": p.get("level"),
                "fromDay": p.get("fromDay", 0),
                "words": [{"id": w["id"],
                           "hr": (w.get("hr") or w["id"]).strip(),
                           "fromDay": w.get("fromDay", 0)} for w in p["words"]],
            })
    return packs


def slot_of(from_day):
    return (from_day - 1) * PER_LESSON if from_day > 0 else 0


def ordered(packs):
    """Port of DeckOrder.ordered. Same walk, same tie-breaks, same stragglers rule."""
    authored = []
    for p in packs:
        for w in p["words"]:
            authored.append((w, p, w["fromDay"] if w["fromDay"] > 0 else p["fromDay"]))
    if all(gate <= 0 for _, _, gate in authored):
        return [(w, p) for w, p, _ in authored]

    out, waiting = [], []

    def release():
        while True:
            i = next((i for i, (_, _, g) in enumerate(waiting) if slot_of(g) <= len(out)), None)
            if i is None:
                return
            w, p, _ = waiting.pop(i)
            out.append((w, p))

    for w, p, gate in authored:
        if slot_of(gate) <= len(out):
            out.append((w, p))
            release()
        else:
            waiting.append((w, p, gate))
    release()
    out.extend((w, p) for w, p, _ in waiting)
    return out


def lesson_tokens(lang):
    """Every word the learner can READ in each lesson: targets, dialogue and activity titles."""
    plan_dir = os.path.join(ROOT, lang, "plan")
    idx = json.load(io.open(os.path.join(plan_dir, "_index.json"), encoding="utf-8"))
    out = {}
    for ph in idx:
        for d in json.load(io.open(os.path.join(plan_dir, ph), encoding="utf-8"))["days"]:
            toks = set()
            for a in d.get("activities", []):
                blobs = [i["hr"] for i in a.get("items", [])]
                blobs += [l["hr"] for l in a.get("lines", [])]
                blobs.append(a.get("title", ""))
                for b in blobs:
                    toks |= set(re.findall(r"[^\W\d_]+", b.lower(), re.UNICODE))
            out[d["day"]] = toks
    return out


def audit(lang):
    packs = load_packs(lang)
    deck = ordered(packs)
    toks = lesson_tokens(lang)
    lessons = sorted(toks)

    # Introduction lesson = the slot the pack's FIRST word lands in after gating.
    intro = {}
    for i, (w, p) in enumerate(deck):
        intro.setdefault(p["id"], i // PER_LESSON + 1)

    problems, report, plan_gaps = [], [], []
    for p in packs:
        words = [w["hr"].lower() for w in p["words"]]
        theme = None
        for ln in lessons:
            hits = sum(1 for w in words if all(part in toks[ln] for part in w.split()))
            if hits >= THEME_HITS:
                theme = ln
                break
        got = intro.get(p["id"])
        if theme is None or got is None:
            report.append((p["id"], p["level"], got, None, None, p["fromDay"]))
            continue
        target = min(theme, LEVEL_END.get(p["level"], theme))
        gap = target - got
        report.append((p["id"], p["level"], got, theme, gap, p["fromDay"]))
        if gap >= MAX_EARLY:
            problems.append(
                f"pack '{p['id']}' ({p['level']}, {p['file']}) is introduced at lesson {got} "
                f"but its theme is taught at lesson {theme} (+{theme - got}); "
                f"set \"fromDay\": {target}")
        elif theme - got >= MAX_EARLY:
            plan_gaps.append(
                f"pack '{p['id']}' ({p['level']}) is introduced at lesson {got}; its theme is "
                f"only taught at lesson {theme}, past the end of {p['level']}. The deck is fine, "
                f"the PLAN has no {p['level']} lesson on this theme.")
    return deck, report, problems, plan_gaps


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    lang = argv[0]
    deck, report, problems, plan_gaps = audit(lang)

    ids = [w["id"] for w, _ in deck]
    if len(ids) != len(set(ids)):
        print("    - deck lost or duplicated words under gating")
        return 1

    if "--report" in argv:
        print(f"{'pack':<28}{'lvl':<5}{'intro':>6}{'theme':>7}{'gap':>6}{'gate':>6}")
        for pid, lvl, got, theme, gap, fd in sorted(report, key=lambda r: -(r[4] or -999)):
            print(f"{pid:<28}{lvl or '':<5}{got or 0:>6}{theme or 0:>7}"
                  f"{gap if gap is not None else 0:>6}{fd:>6}")
        print()

    for p in problems:
        print("    - " + p)
    for g in plan_gaps:
        print("    i " + g)
    print(f"\n{lang}: {len(deck)} deck words, {len(report)} packs, {len(problems)} problems, "
          f"{len(plan_gaps)} plan observations")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
    sys.exit(main(sys.argv[1:]))
