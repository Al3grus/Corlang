# -*- coding: utf-8 -*-
"""Italian-specific batch checks, layered on check_batch.py.

Same design as check_de.py: the shared checker enforces what every language shares, this adds
the ways a machine author drifts in Italian specifically.

  1. MISSING ACCENTS. The highest-frequency real error, and it changes meaning: perche for
     perché, piu for più, cosi for così, citta for città, e' for è. Every form listed here is
     not a word without its accent, so a bare hit is always an error.
  2. WRONG ARTICLE FORM. Italian picks the article by the sound that follows: lo before
     s+consonant, z, gn, ps, x and y, l' before a vowel, il elsewhere. "il studente" and
     "il amico" are the classic learner mistakes and must never appear in taught text.
  3. PASSATO REMOTO at A1 and A2. The course teaches the passato prossimo for the past. The
     passato remoto is normal in written narrative and in southern speech, so it is only
     flagged below B1, where it would simply be off-syllabus.

Scoped by KEY like the German checker: only strings that ARE Italian the learner is taught to
produce, never English commentary that names a wrong form in order to reject it.

Usage:  python check_it.py <file.json> [...]

2026-07-28: check_italian() never unwrapped the assembled {"title", "days"} shape (the same
silent-no-op bug found and fixed in check_hr.py/K8 and check_de.py/K13) — every real shipped
Italian file is this shape, so this checker had validated nothing since it was written. Fixed
with the same _unwrap()/_is_day_shaped() pattern, plus a generic-shape fallback for vocab packs
and quizzes/placement/exams (check_de.py's K14 fix), and KEY-scoped throughout from the start
(no equivalent of check_de.py's K16 English-collision bug: MISSING_ACCENT_LOWER's three forms
are not common English words, so this class of false positive doesn't apply here, but the
scoping is still correct practice and cheap insurance).
"""
import io
import json
import os
import re
import sys

import check_batch

# Not a word at all without its accent, in any casing, so a bare match is always wrong.
MISSING_ACCENT = re.compile(
    r"\b(perche|poiche|benche|affinche|cosi|piu|gia|citta|universita|qualita|societa|"
    r"liberta|verita|caffe|puo|lunedi|martedi|mercoledi|giovedi|venerdi)\b",
    re.IGNORECASE)

# Future-tense forms whose unaccented spelling IS a word: "Sara" and "Fara" are names, so these
# are matched lowercase only. Deliberately excluded entirely: te (the stressed pronoun, a real
# word, only "tè" the drink takes the accent), meta (a goal) and eta (a letter name).
MISSING_ACCENT_LOWER = re.compile(
    r"\b(sara|fara|andra|verra|potra|dovra|vorra)\b")
APOSTROPHE_E = re.compile(r"(?<![\w'])e'(?!\w)")

# lo / l' territory: s+consonant, z, gn, ps, pn, x, y, and any vowel.
WRONG_IL = re.compile(
    r"\b(il)\s+(s[bcdfgklmnpqrtvz]|z|gn|ps|pn|x|y|[aeiouàèéìòù])", re.IGNORECASE)
WRONG_UN_APOST = re.compile(r"\bun'\s*[bcdfghlmnpqrstvz]", re.IGNORECASE)
WRONG_UNO = re.compile(r"\bun\s+(s[bcdfgklmnpqrtvz]|z|gn|ps|pn|x|y)", re.IGNORECASE)

PASSATO_REMOTO = re.compile(
    r"\b(fui|fosti|fummo|foste|furono|ebbi|ebbe|ebbero|andai|andò|andarono|"
    r"dissi|disse|dissero|feci|fece|fecero|venni|venne|vennero|vidi|vide|videro|"
    r"presi|prese|presero|nacque|nacquero|scrisse|scrissero)\b",
    re.IGNORECASE)


def italian_strings_of(node):
    """Only target-language text and graded answer surfaces. See check_de.german_strings_of."""
    for path, s in check_batch.walk_strings(node):
        if (path.endswith(".hr") or path.endswith(".target") or path.endswith(".answer")
                or ".options[" in path or ".ordered[" in path or ".accepted[" in path):
            yield s


def distractors_of(day):
    """Wrong MCQ options may legitimately contain what the lesson forbids."""
    out = set()
    for a in day.get("activities", []):
        if a.get("type") != "EXERCISE":
            continue
        for q in a.get("questions", []):
            if q.get("type") == "MCQ":
                for opt in q.get("options", []):
                    if opt != q.get("answer"):
                        out.add(opt)
    return out


def _unwrap(obj):
    """Assembled course files are {"title": ..., "days": [...]}; pre-merge batches are a bare
    array. Accept either so this checker runs against both."""
    if isinstance(obj, dict) and isinstance(obj.get("days"), list):
        return obj["days"]
    return obj


def _is_day_shaped(days):
    return (isinstance(days, list) and len(days) > 0 and isinstance(days[0], dict)
            and "activities" in days[0])


def _checks_on_string(s, level):
    errs = []
    m = MISSING_ACCENT.search(s) or MISSING_ACCENT_LOWER.search(s)
    if m:
        errs.append(f"missing accent on {m.group(0)!r} in {s[:60]!r}")
    if APOSTROPHE_E.search(s):
        errs.append(f"wrote e' for è in {s[:60]!r}")
    m = WRONG_IL.search(s)
    if m:
        errs.append(f"wrong article, {m.group(0)!r} needs lo or l' in {s[:60]!r}")
    m = WRONG_UNO.search(s)
    if m:
        errs.append(f"wrong article, {m.group(0)!r} needs uno in {s[:60]!r}")
    if WRONG_UN_APOST.search(s):
        errs.append(f"un' before a consonant in {s[:60]!r}, un' is feminine only")
    if level in ("A1", "A2"):
        m = PASSATO_REMOTO.search(s)
        if m:
            errs.append(f"passato remoto {m.group(0)!r} at {level}, "
                        f"the course teaches the passato prossimo")
    return errs


def check_italian(path):
    errs = []
    try:
        days = _unwrap(json.load(io.open(path, encoding="utf-8")))
    except Exception:
        return []
    if not _is_day_shaped(days):
        return []

    for di, day in enumerate(days):
        tag = f"[{di}] {str(day.get('title', '?'))[:40]}"
        wrong_options = distractors_of(day)
        level = day.get("level", "")

        for s in italian_strings_of(day):
            if s in wrong_options:
                continue
            for msg in _checks_on_string(s, level):
                errs.append(f"{tag}: {msg}")

        # THE LEVEL CEILING, over PROMPTS and ACTIVITY TITLES. `italian_strings_of` scans
        # .hr/.target/.answer and the option arrays, so Italian embedded in an English prompt or
        # in an activity title was never read. In the Spanish course that exact blind spot hid
        # FOUR real B1-ceiling violations, found by a Phase 8c audit and not by any checker.
        # A sweep of all 2,205 prompt/title strings across the 245 shipped Italian days found
        # ZERO hits, so this guard is prevention rather than a fix: it is here so the hole cannot
        # be walked into later. Level-gated messages ONLY -- a prompt is largely English
        # instructional prose, and running the orthography or article checks over it would
        # false-fire on ordinary English (the K16/K18 shape).
        for path_, s in check_batch.walk_strings(day):
            if not (path_.endswith(".prompt") or path_.endswith(".title")):
                continue
            for msg in _checks_on_string(s, level):
                if "at " + level in msg:
                    errs.append(f"{tag}: {msg} (in a prompt or title, which is learner-visible)")
    return errs


def _generic_distractors(node):
    """Wrong MCQ options anywhere in an arbitrary JSON tree, not just under day.activities:
    quizzes/placement/exams put "type": "MCQ" questions directly under "questions" with no
    activity wrapper."""
    out = set()

    def rec(x):
        if isinstance(x, dict):
            if x.get("type") == "MCQ":
                ans = x.get("answer")
                for opt in x.get("options", []):
                    if opt != ans:
                        out.add(opt)
            for v in x.values():
                rec(v)
        elif isinstance(x, list):
            for v in x:
                rec(v)

    rec(node)
    return out


def check_italian_generic(label, raw):
    """Same checks as check_italian(), for non-day-shaped files (vocab packs, quizzes.json,
    placement.json, exams.json, grammar.json...) that check_italian() can't parse because they
    have no `activities` structure to scope by, and no `level` field to gate PASSATO_REMOTO on
    (so that check is skipped here, not applied at every level)."""
    errs = []
    wrong = _generic_distractors(raw)
    for path, s in check_batch.walk_strings(raw):
        if not (path.endswith(".hr") or path.endswith(".target") or path.endswith(".answer")
                or ".options[" in path or ".ordered[" in path or ".accepted[" in path):
            continue
        if s in wrong:
            continue
        m = MISSING_ACCENT.search(s) or MISSING_ACCENT_LOWER.search(s)
        if m:
            errs.append(f"{label}: missing accent on {m.group(0)!r} in {s[:60]!r}")
        if APOSTROPHE_E.search(s):
            errs.append(f"{label}: wrote e' for è in {s[:60]!r}")
        m = WRONG_IL.search(s)
        if m:
            errs.append(f"{label}: wrong article, {m.group(0)!r} needs lo or l' in {s[:60]!r}")
        m = WRONG_UNO.search(s)
        if m:
            errs.append(f"{label}: wrong article, {m.group(0)!r} needs uno in {s[:60]!r}")
        if WRONG_UN_APOST.search(s):
            errs.append(f"{label}: un' before a consonant in {s[:60]!r}, un' is feminine only")
    return errs


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    total, bad = 0, 0
    # No arguments means the whole shipped course. Reporting "0 days total, 0 problems"
    # and exiting 0 because nobody passed a file is a green light that checked nothing.
    paths = sys.argv[1:] or check_batch.course_files("it")
    if not paths:
        print("nothing to check: pass batch files, or restore app/src/main/assets/content/it/")
        sys.exit(2)
    for path in paths:
        if not os.path.exists(path):
            print(f"MISSING {path}")
            bad += 1
            continue
        raw = json.load(io.open(path, encoding="utf-8"))
        days = _unwrap(raw)
        if _is_day_shaped(days):
            errs = check_batch.check_file(path) + check_italian(path)
            n = len(days)
            label = f"{n} days"
        else:
            errs = check_italian_generic(os.path.basename(path), raw)
            n = 0
            label = "generic file"
        total += n
        print(f"{os.path.basename(path):<28} {label:<12} "
              f"{'OK ' if not errs else str(len(errs)) + ' PROBLEMS'}")
        for e in errs[:30]:
            print(f"    - {e}")
        if len(errs) > 30:
            print(f"    ... and {len(errs) - 30} more")
        bad += len(errs)
    print(f"\n{total} days total, {bad} problems")
    sys.exit(1 if bad else 0)
