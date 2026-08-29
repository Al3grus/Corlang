# -*- coding: utf-8 -*-
"""French-specific batch checks, layered on top of check_batch.py.

The shared checker enforces the invariants every language shares. This adds the ONE way
French content drifts that no generic gate catches, mirroring check_de.py's Austrian/Swiss
guard and check_hr.py's Serbian guard: Belgian/Swiss/Quebecois regional and national-variety
forms creeping into what should be standard Metropolitan French, the variety the course targets
(France is the only Corlang course where the legal target is B2, via DELF/DALF, and the exam is
administered in Metropolitan French).

  1. Belgian/Swiss numbers (septante, octante/huitante, nonante), where standard French uses
     soixante-dix, quatre-vingts, quatre-vingt-dix.
  2. Quebecois lexis with a distinct standard-French counterpart: char (voiture), magasiner
     (faire du shopping / faire les courses), blonde/chum as "girlfriend/boyfriend" (petite
     amie/petit ami), fin de semaine as the PRIMARY word for "weekend" (le week-end is standard
     in France; fin de semaine is the Quebec avoidance of the anglicism), jaser (bavarder/
     discuter), achaler (embêter/agacer), niaiser (se moquer/perdre son temps), barrer
     (fermer à clé), and the meal-name SHIFT that is the single most-cited French variety trap:
     in Quebec, déjeuner/dîner/souper = breakfast/lunch/dinner; in standard (Metropolitan)
     French, they shift one meal later: petit-déjeuner/déjeuner/dîner = breakfast/lunch/dinner.
     Teaching bare "déjeuner" to mean breakfast, or "dîner" to mean lunch, is the Quebec shift
     and a genuine defect in a France-targeted course.

Scoped by KEY exactly like check_de.py / check_hr.py: only strings that ARE French the learner
is taught to produce (hr, example target, answer, options, ordered, accepted), never the English
commentary, which legitimately names a Quebec form in order to reject it. Incorrect MCQ options
are also exempt, since teaching "say soixante-dix, not septante" requires printing the wrong form.
Regional/Quebec forms are activity-scoped like check_de.py's REGIONAL table: a form is allowed
when its standard counterpart appears in the SAME activity (contrastive teaching), the same
principle that stopped check_de.py's early false-positive rounds.

Usage:  python check_fr.py <file.json> [...]
"""
import io
import json
import os
import re
import sys

import check_batch

# Belgian/Swiss number -> the standard Metropolitan French counterpart that must accompany it
# for the use to count as teaching (contrastive), not drift.
REGIONAL_NUMBERS = {
    "septante": "soixante-dix",
    "huitante": "quatre-vingts",
    "octante": "quatre-vingts",
    "nonante": "quatre-vingt-dix",
}

# Quebecois lexis -> its standard French counterpart, same activity-scoped exemption. Kept
# deliberately SHORT: a false-positive probe found "char" (also standard French for "tank" or a
# parade float), "blonde" (also the ordinary standard-French adjective "blonde-haired"), "barrer"
# (also standard for "to cross out" or "to steer/lock"), and "jaser" (mild standard-French use
# too) all have a legitimate non-Quebec reading that a whole-word match cannot distinguish from
# the regional sense (the same K7/K3 lesson check_hr.py and check_it.py already learned: prefer a
# narrower, reliable checker over a broad one that fires on legitimate standard French). Only
# entries with no common standard-French homograph are listed.
REGIONAL_LEXIS = {
    # each regional term maps to a TUPLE of acceptable standard counterparts, since more than
    # one standard phrasing is often equally valid (found via a real false positive: content
    # correctly taught "magasiner... pour faire les courses", not the single counterpart
    # originally listed here, "faire du shopping" — both are standard French).
    "magasiner": ("faire du shopping", "faire les courses", "faire les magasins"),
    "achaler": ("embêter", "agacer", "déranger"),
    "niaiser": ("se moquer", "plaisanter"),
}

# "chum"/"blonde"/"char" as informal Quebec terms for a partner/car/tank are real but too
# ambiguous for a whole-word regex to catch reliably; left for human review, not auto-flagged.

# The meal-name shift: Quebec déjeuner/dîner/souper = breakfast/lunch/dinner; standard French
# shifts one meal later. A bare "déjeuner" glossed/used as "breakfast" or "dîner" as "lunch" is
# the Quebec shift. Scoped narrowly: only fires when paired with the wrong English gloss context
# is impossible to detect from Croatian-style hr/en pairs alone without an activity-level check,
# so this checks the FR/EN pair directly where both are present in the same LEARN item.
MEAL_SHIFT_WRONG_GLOSS = re.compile(
    r"\bd[ée]jeuner\b.{0,3}(?:breakfast)|\bd[iî]ner\b.{0,3}(?:lunch)",
    re.IGNORECASE)


def fr_strings_of(node):
    """Only target-language text and graded answer surfaces."""
    for path, s in check_batch.walk_strings(node):
        if (path.endswith(".hr") or path.endswith(".target") or path.endswith(".answer")
                or ".options[" in path or ".ordered[" in path or ".accepted[" in path):
            yield s


def strings_of(node):
    for _, s in check_batch.walk_strings(node):
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


def _unwrap(obj):
    if isinstance(obj, dict) and isinstance(obj.get("days"), list):
        return obj["days"]
    return obj


def _is_day_shaped(days):
    return (isinstance(days, list) and len(days) > 0 and isinstance(days[0], dict)
            and "activities" in days[0])


def _generic_distractors(data):
    """Wrong MCQ options anywhere in a non-day-shaped file, so the contrastive exemption that
    applies to lessons applies here too: printing the wrong form is how you teach against it."""
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

    rec(data)
    return out


def check_generic_french(label, data):
    """The same French checks, over a file that has no days: placement.json, quizzes.json,
    exams.json and the reference files.

    K14, found again here: this checker only ever ran check_batch's DAY validator, which rejects
    any other shape outright, so every one of those files has been reported as a shape error and
    never actually checked for French drift since the checker was written. The de checker had
    exactly this bug and the pt checker has carried the fix for a while; fr never got it.

    File-scoped rather than activity-scoped, because there are no activities to scope to: a
    regional form is allowed when its standard counterpart appears anywhere in the same file.
    That is looser than the lesson rule, and deliberately so, an over-strict check on reference
    files is how false positives get taught to be ignored.
    """
    wrong = _generic_distractors(data)
    errs = []
    text = " ".join(s for s in strings_of(data) if s not in wrong).lower()

    for s in strings_of(data):
        if s in wrong:
            continue
        m = MEAL_SHIFT_WRONG_GLOSS.search(s)
        if m:
            errs.append(f"{label}: Quebec meal-name shift {m.group(0)!r} in {s[:70]!r}, "
                        f"standard French is petit-déjeuner=breakfast, déjeuner=lunch, "
                        f"dîner=dinner")

    for regional, standard in REGIONAL_NUMBERS.items():
        if (re.search(rf"\b{re.escape(regional)}\b", text)
                and not re.search(rf"\b{re.escape(standard)}\b", text)):
            errs.append(f"{label}: Belgian/Swiss number {regional!r} without its standard "
                        f"counterpart {standard!r} anywhere in the file")
    for regional, standards in REGIONAL_LEXIS.items():
        if (re.search(rf"\b{re.escape(regional)}\b", text)
                and not any(re.search(rf"\b{re.escape(std)}\b", text) for std in standards)):
            errs.append(f"{label}: Quebecois lexis {regional!r} without any of its standard "
                        f"counterparts {standards!r} anywhere in the file")
    return errs


def check_french(path):
    errs = []
    try:
        days = _unwrap(json.load(io.open(path, encoding="utf-8")))
    except Exception:
        return []
    if not isinstance(days, list):
        return []

    for di, day in enumerate(days):
        tag = f"[{di}] {str(day.get('title', '?'))[:40]}"
        wrong = distractors_of(day)

        for s in fr_strings_of(day):
            if s in wrong:
                continue
            m = MEAL_SHIFT_WRONG_GLOSS.search(s)
            if m:
                errs.append(f"{tag}: Quebec meal-name shift {m.group(0)!r} in {s[:70]!r}, "
                             f"standard French is petit-déjeuner=breakfast, "
                             f"déjeuner=lunch, dîner=dinner")

        # Regional numbers and lexis, scoped to the activity so contrastive teaching is allowed.
        for a in day.get("activities", []):
            blob = " ".join(s for s in strings_of(a) if s not in wrong).lower()
            for regional, standard in REGIONAL_NUMBERS.items():
                if (re.search(rf"\b{re.escape(regional)}\b", blob)
                        and not re.search(rf"\b{re.escape(standard)}\b", blob)):
                    errs.append(f"{tag}/{a.get('type')}: Belgian/Swiss number {regional!r} "
                                f"without its standard counterpart {standard!r} in the same "
                                f"activity")
            for regional, standards in REGIONAL_LEXIS.items():
                if (re.search(rf"\b{re.escape(regional)}\b", blob)
                        and not any(re.search(rf"\b{re.escape(std)}\b", blob) for std in standards)):
                    errs.append(f"{tag}/{a.get('type')}: Quebecois lexis {regional!r} without "
                                f"any of its standard counterparts {standards!r} in the same "
                                f"activity")
    return errs


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    total, bad = 0, 0
    # No arguments means the whole shipped course. Reporting "0 days total, 0 problems"
    # and exiting 0 because nobody passed a file is a green light that checked nothing.
    paths = sys.argv[1:] or check_batch.course_files("fr")
    if not paths:
        print("nothing to check: pass batch files, or restore app/src/main/assets/content/fr/")
        sys.exit(2)
    for path in paths:
        if not os.path.exists(path):
            print(f"MISSING {path}")
            bad += 1
            continue
        raw = json.load(io.open(path, encoding="utf-8"))
        days = _unwrap(raw)
        if _is_day_shaped(days):
            errs = check_batch.check_file(path) + check_french(path)
            n = len(days)
            label = f"{n:>3} days"
        else:
            # Not a plan file: run the French checks over the whole string tree instead of
            # handing it to check_batch's day validator, which can only reject it.
            errs = check_generic_french(os.path.basename(path), raw)
            n = 0
            label = "generic file"
        total += n
        print(f"{os.path.basename(path):<22} {label:<12} "
              f"{'OK ' if not errs else str(len(errs)) + ' PROBLEMS'}")
        for e in errs[:30]:
            print(f"    - {e}")
        if len(errs) > 30:
            print(f"    ... and {len(errs) - 30} more")
        bad += len(errs)
    print(f"\n{total} days total, {bad} problems")
    sys.exit(1 if bad else 0)
