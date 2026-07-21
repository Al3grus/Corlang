# -*- coding: utf-8 -*-
"""Croatian-specific batch checks, layered on check_batch.py.

The shared checker enforces what every language shares. This adds the ONE way Croatian content
drifts that no generic gate catches: SERBIAN forms creeping into what should be standard
Croatian. This is the highest-value variety check in the project, because the drift is subtle
and an exam grades it wrong. The failure modes, from the shipped varietyRules and a real field
report where the tutor "corrected" trebam uciti into the Serbian trebam da ucim:

  1. da + present after a modal, where Croatian uses the INFINITIVE
     (trebam uciti, mogu doci, zelim ici; NEVER trebam da ucim).
  2. da li questions, where Croatian uses the -li enclitic or je li
     (Dolazis li?, Je li tocno?; NEVER da li dolazis).
  3. ekavian reflexes, where Croatian is ijekavian (lijepo not lepo, mlijeko not mleko,
     vrijeme not vreme, htjeti not hteti, dijete not dete).
  4. Serbian lexis where Croatian has its own word (tjedan not nedelja for "week", kruh not
     hleb, tisuca not hiljada, zrak not vazduh, vlak not voz, mrkva not sargarepa, and the
     Serbian month names januar/februar... where Croatian uses sijecanj/veljaca...).

Scoped by KEY exactly like check_de / check_it: only strings that ARE Croatian the learner is
taught to produce (hr, example target, answer, options, ordered, accepted), never the English
commentary, which legitimately names a Serbian form in order to reject it. Incorrect MCQ
options are also exempt, since teaching "say je li, not da li" requires printing the wrong form.

Usage:  python check_hr.py <file.json> [...]
"""
import io
import json
import os
import re
import sys

import check_batch

# da + (optional clitic/pronoun) + a present-tense verb, right after a modal or semi-modal.
# The modal set is what actually takes a bare infinitive in Croatian.
DA_PRESENT = re.compile(
    r"\b(trebam|trebas|treba|trebamo|trebate|trebaju|mogu|mozes|moze|mozemo|mozete|"
    r"moraju|moram|moras|mora|moramo|morate|zelim|zelis|zeli|zelimo|zelite|zele|"
    r"pokusavam|pokusavas|pocinjem|volim|volis|voli|namjeravam|smijem|smije)\s+da\b",
    re.IGNORECASE)

DA_LI = re.compile(r"\bda\s+li\b", re.IGNORECASE)

# ekavian reflexes whose ijekavian form is the standard. Each bare stem here is Serbian, so a
# whole-word hit is an error. Kept tight to avoid catching unrelated words.
EKAVIAN = re.compile(
    r"\b(lepo|lep|lepa|lepi|mleko|mleka|vreme|dete|deca|hteti|hteo|"
    r"covek|coveka|reka|reke|beo|belo|bela|sneg|snega|cvet|cveta|mesto|mesta|"
    r"nedelja(?=\s|,|\.|$))\b",  # nedelja = Serbian "week"; Croatian tjedan (Sunday is nedjelja)
    re.IGNORECASE)

# Serbian lexis with a distinct Croatian counterpart. Month names use a STEM match with an
# optional case ending, because Croatian inflects heavily (januar, januara, januaru all Serbian;
# Croatian is sijecanj). "nedjelja" (with j, = Sunday) is CORRECT Croatian and not listed.
SERBIAN_LEX = re.compile(
    r"\b(hleb\w*|hiljad\w*|vazduh\w*|voz|vozu|vozom|vozovi|vozova|vozovima|vozove|sargarep\w*|"
    r"januar\w*|februar\w*|avgust\w*|septembar\w*|septembr\w*|oktobar\w*|oktobr\w*|"
    r"novembar\w*|novembr\w*|decembar\w*|decembr\w*|"
    r"fudbal\w*|kasik\w*|viljusk\w*|takode\w*|ostrv\w*|vaspitanj\w*|porodic\w*)\b",
    re.IGNORECASE)


def hr_strings_of(node):
    """Only target-language text and graded answer surfaces."""
    for path, s in check_batch.walk_strings(node):
        if (path.endswith(".hr") or path.endswith(".target") or path.endswith(".answer")
                or ".options[" in path or ".ordered[" in path or ".accepted[" in path):
            yield s


def distractors_of(day):
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


def check_croatian(path):
    errs = []
    try:
        days = json.load(io.open(path, encoding="utf-8"))
    except Exception:
        return []
    if not isinstance(days, list):
        return []

    for di, day in enumerate(days):
        tag = f"[{di}] {str(day.get('title', '?'))[:40]}"
        wrong = distractors_of(day)
        for s in hr_strings_of(day):
            if s in wrong:
                continue
            if DA_PRESENT.search(s):
                errs.append(f"{tag}: Serbian 'da + present' after a modal in {s[:70]!r}, "
                            f"Croatian uses the infinitive")
            if DA_LI.search(s):
                errs.append(f"{tag}: Serbian 'da li' question in {s[:70]!r}, "
                            f"Croatian uses -li or 'je li'")
            m = EKAVIAN.search(s)
            if m:
                errs.append(f"{tag}: ekavian form {m.group(0)!r} in {s[:60]!r}, "
                            f"Croatian is ijekavian")
            m = SERBIAN_LEX.search(s)
            if m:
                errs.append(f"{tag}: Serbian lexis {m.group(0)!r} in {s[:60]!r}, "
                            f"use the Croatian word")
    return errs


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    total, bad = 0, 0
    for path in sys.argv[1:]:
        if not os.path.exists(path):
            print(f"MISSING {path}")
            bad += 1
            continue
        errs = check_batch.check_file(path) + check_croatian(path)
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
