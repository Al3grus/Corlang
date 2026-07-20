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


def check_italian(path):
    errs = []
    try:
        days = json.load(io.open(path, encoding="utf-8"))
    except Exception:
        return []
    if not isinstance(days, list):
        return []

    for di, day in enumerate(days):
        tag = f"[{di}] {str(day.get('title', '?'))[:40]}"
        wrong_options = distractors_of(day)
        level = day.get("level", "")

        for s in italian_strings_of(day):
            if s in wrong_options:
                continue
            m = MISSING_ACCENT.search(s) or MISSING_ACCENT_LOWER.search(s)
            if m:
                errs.append(f"{tag}: missing accent on {m.group(0)!r} in {s[:60]!r}")
            if APOSTROPHE_E.search(s):
                errs.append(f"{tag}: wrote e' for è in {s[:60]!r}")
            m = WRONG_IL.search(s)
            if m:
                errs.append(f"{tag}: wrong article, {m.group(0)!r} needs lo or l' in {s[:60]!r}")
            m = WRONG_UNO.search(s)
            if m:
                errs.append(f"{tag}: wrong article, {m.group(0)!r} needs uno in {s[:60]!r}")
            if WRONG_UN_APOST.search(s):
                errs.append(f"{tag}: un' before a consonant in {s[:60]!r}, un' is feminine only")
            if level in ("A1", "A2"):
                m = PASSATO_REMOTO.search(s)
                if m:
                    errs.append(f"{tag}: passato remoto {m.group(0)!r} at {level}, "
                                f"the course teaches the passato prossimo")
    return errs


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    total, bad = 0, 0
    for path in sys.argv[1:]:
        if not os.path.exists(path):
            print(f"MISSING {path}")
            bad += 1
            continue
        errs = check_batch.check_file(path) + check_italian(path)
        try:
            n = len(json.load(io.open(path, encoding="utf-8")))
        except Exception:
            n = 0
        total += n
        print(f"{os.path.basename(path):<16} {n:>3} days  "
              f"{'OK ' if not errs else str(len(errs)) + ' PROBLEMS'}")
        for e in errs[:30]:
            print(f"    - {e}")
        if len(errs) > 30:
            print(f"    ... and {len(errs) - 30} more")
        bad += len(errs)
    print(f"\n{total} days total, {bad} problems")
    sys.exit(1 if bad else 0)
