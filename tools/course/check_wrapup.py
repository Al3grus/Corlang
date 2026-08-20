# -*- coding: utf-8 -*-
"""The wrap-up checker: is the day's closing recall actually answerable?

Every lesson ends with a from-memory recall that is GENERATED, not authored: SessionPlayer
takes the day's LEARN items, keeps the ones that look like producible phrases, and asks the
learner to type the target language for the English gloss (Drills.kt, wrapupRecallPhrases).
That makes wrap-up quality a property of the LEARN items, and it is invisible while authoring
them, because nothing in the lesson file says "this row will become a typing test".

It shipped broken. Field report 2026-08-20: a day 11 Croatian wrap-up scored 0/8 against
linguistically perfect answers, because every expected string looked like "kava -> kavu" with a
real arrow in it, and no phone keyboard has one. Alongside it: prompts written in Croatian, a
pronunciation table served as a production test, and 112 days whose wrap-up silently degraded
into a replay of the exercise the learner had just done.

This is the fast offline gate for authoring (seconds, no build). ContentValidationTest carries
the same four rules as the hard build gate; keep them in step.

What it checks, per day:
  1. TYPABLE   every recall answer and prompt is text a phone keyboard can produce.
  2. SINGLE    one ask, one answer. Never "original -> new", never a comma-separated list.
  3. UNIQUE    no two asks in a day share an English prompt (unanswerable by construction).
  4. ENOUGH    at least MIN_ITEMS producible phrases, so the wrap-up is real.
  5. ENGLISH   day and activity titles read in English; the target language may appear inside
               as the example being named ("Big numbers: sto, tisuca, milijun").

Usage:  python check_wrapup.py hr            # whole course
        python check_wrapup.py hr A0 A1      # only these levels
"""
import io
import json
import os
import re
import sys
import unicodedata

ROOT = os.path.join("app", "src", "main", "assets", "content")

# Mirrors Drills.kt PAIR_SYMBOLS. A relation between two forms is a table row, not a phrase.
PAIR_SYMBOLS = ["→", "←", "↔", "⇒", "=", "+", "«", "»",
                "–", "—",
                # A right/wrong contrast row ("V Jucer sam radio. X Jucer radio sam") teaches
                # well and is impossible to type. The correct half is the ask; the mistake
                # belongs in the note.
                "✓", "✗"]

# Mirrors SessionPlayer: below this a day has no real wrap-up, and Drills.WrapupRecall.take(8).
MIN_ITEMS = 4
ASKED = 8

# Letters no other course language uses, so their presence in a title is decisive.
HR_LETTERS = "čćđšž"

# Croatian function words frequent in titles and safe against English collisions.
# "i", "u", "do", "to" are deliberately absent: they are English words too.
HR_TITLE_MARKERS = {
    "je", "su", "se", "sam", "si", "smo", "ste", "na", "za", "od", "iz", "li",
    "sto", "kako", "koliko", "kada", "gdje", "tko", "zasto", "moj", "tvoj", "nas", "vas",
    "brojevi", "glagoli", "rijeci", "vjezba", "ponavljanje", "pitanja", "mnozina",
}


def normalize(s, strict=False):
    """Port of Grading.normalize."""
    base = s.strip().lower()
    if strict:
        de = unicodedata.normalize("NFC", base)
    else:
        de = "".join(c for c in unicodedata.normalize("NFD", base)
                     if unicodedata.category(c) != "Mn").replace("đ", "d")
    de = re.sub(r"[.,!?;:\"'’]", "", de)
    return re.sub(r"\s+", " ", de).strip()


def recall_candidates(day):
    """Port of Drills.kt recallCandidates: what the day OFFERS the wrap-up, before the guards."""
    out = []
    for act in day.get("activities", []):
        if act.get("type") != "LEARN":
            continue
        for it in act.get("items", []):
            hr, en = it["hr"], it["en"]
            if not en.strip() or "…" in hr or "..." in hr:
                continue
            if " — " in hr:
                if " — " not in en:
                    continue
                hr, en = hr.split(" — ")[0].strip(), en.split(" — ")[0].strip()
            if normalize(hr, True) in normalize(en, True):
                continue
            if not (2 <= len(hr) <= 40):
                continue
            out.append((hr, en))
    seen, ded = set(), []
    for hr, en in out:
        if hr.lower() in seen:
            continue
        seen.add(hr.lower())
        ded.append((hr, en))
    return ded


def check_day(day, lang):
    errs = []
    where = f"day {day['day']} ({day.get('level', '?')})"
    cands = recall_candidates(day)

    for hr, en in cands:
        for sym in PAIR_SYMBOLS:
            if sym in hr or sym in en:
                errs.append(f"{where}: untypable '{sym}' in {hr!r} / {en!r}")
        # A parenthesis is fine in the English prompt, where it disambiguates. In the TARGET
        # it is part of the expected string: Grading.normalize strips only .,!?;:'" so
        # "Radila sam. (zena)" cannot be matched unless the learner types the brackets too.
        # The gloss belongs on the English side, or in the note.
        if "(" in hr or ")" in hr or "·" in hr:
            errs.append(f"{where}: gloss inside the answer {hr!r}, move it to the prompt or note")
        # One ask, one answer. "dvjesto, tristo, petsto" is three answers wearing one prompt.
        # A real sentence also carries commas ("Ne, ne govorim."), so the tell is that EVERY
        # comma-separated part is a bare word and the whole thing is not punctuated as a
        # sentence.
        # A real sentence carries commas too ("Ne, ne govorim."), and it ends like a sentence.
        # A bare comma-separated run that does not ("u gradu, na poslu, u skoli") is three
        # answers wearing one prompt, and the grader accepts only all three, typed in order.
        parts = [p.strip() for p in hr.split(",")]
        if len(parts) > 1 and all(parts) and hr.rstrip()[-1] not in ".?!":
            errs.append(f"{where}: multi-answer ask {hr!r} / {en!r}, split it")

    prompts = {}
    for hr, en in cands[:ASKED]:
        prompts.setdefault(normalize(en, True), []).append(hr)
    for prompt, answers in prompts.items():
        if len(answers) > 1:
            errs.append(f"{where}: prompt {prompt!r} has {len(answers)} right answers: "
                        + ", ".join(answers))

    usable = [c for c in cands
              if not any(s in c[0] or s in c[1] for s in PAIR_SYMBOLS)]
    if len(usable) < MIN_ITEMS:
        errs.append(f"{where}: only {len(usable)} recallable phrases, "
                    f"wrap-up degrades to an exercise replay")

    if lang == "hr":
        for label, title in ([("title", day["title"])] +
                             [(a["type"], a["title"]) for a in day.get("activities", [])]):
            head = title.split(":")[0].split("(")[0].strip()
            words = [w for w in re.split(r"[^\w]+", head.lower(), flags=re.UNICODE) if w]
            hits = ([w for w in words if w in HR_TITLE_MARKERS]
                    + [c for c in HR_LETTERS if c in head.lower()])
            if hits:
                errs.append(f"{where}: {label} title is not English: {title!r} ({', '.join(hits)})")
    return errs


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    lang, levels = argv[0], set(argv[1:])
    index = json.load(io.open(os.path.join(ROOT, lang, "plan", "_index.json"), encoding="utf-8"))
    total = bad = 0
    for phase in index:
        doc = json.load(io.open(os.path.join(ROOT, lang, "plan", phase), encoding="utf-8"))
        for day in doc["days"]:
            if levels and day.get("level") not in levels:
                continue
            total += 1
            errs = check_day(day, lang)
            bad += len(errs)
            for e in errs:
                print(f"    - {e}")
    scope = "/".join(sorted(levels)) if levels else "all levels"
    print(f"\n{lang} {scope}: {total} days checked, {bad} problems")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
    sys.exit(main(sys.argv[1:]))
