# -*- coding: utf-8 -*-
"""German-specific batch checks, layered on top of check_batch.py.

The shared checker enforces the invariants every language shares. This adds the ones only
German has, which are exactly the ways a machine author drifts:

  1. Austrian and Swiss regional forms. The course teaches Standarddeutsch, the same way the
     Portuguese course guards against Brazilian forms. As there, a regional word is allowed
     when it is being TAUGHT contrastively, so the check is activity-scoped: the regional form
     is accepted only if its standard counterpart appears in the same activity.
  2. Southern perfect auxiliaries (ist gestanden / gesessen / gelegen). Standard written German
     takes haben, and this is the single most common variety slip.
  3. Swiss spelling, which has abolished ss entirely. Any of a short list of words that must
     carry ss in standard spelling is flagged if it appears with ss instead.

Usage:  python check_de.py <file.json> [...]
"""
import io
import json
import os
import re
import sys

import check_batch

# Regional form -> the standard form that must accompany it for the use to count as teaching.
REGIONAL = {
    "jänner": "januar", "feber": "februar",
    "semmel": "brötchen", "sackerl": "tüte", "paradeiser": "tomate",
    "erdapfel": "kartoffel", "erdäpfel": "kartoffeln", "marille": "aprikose",
    "karfiol": "blumenkohl", "topfen": "quark", "obers": "sahne",
    "rahm": "sahne", "velo": "fahrrad", "grüezi": "guten tag",
    "znüni": "frühstück", "spital": "krankenhaus", "trottoir": "gehweg",
    "heuer": "dieses jahr", "servus": "hallo", "gelse": "mücke",
}

SOUTHERN_PERFECT = re.compile(
    r"\b(ist|sind|bist|bin|war|waren)\b[^.?!]{0,40}\b(gestanden|gesessen|gelegen)\b",
    re.IGNORECASE)

# The standard counterpart, for the same participle. A note that teaches the rule has to print
# the wrong form in order to reject it ("hat gestanden, never ist gestanden"), and it will
# almost always print the right form alongside. Finding both in one string means the string is
# contrastive, which is teaching rather than drift, so it is exempt on the same principle as a
# regional word appearing next to its standard counterpart.
def taught_against(text, participle):
    return re.search(rf"\b(hat|hab|habe|haben|hatte|hatten)\b[^.?!]{{0,40}}\b{participle}\b",
                     text, re.IGNORECASE) is not None

# Words whose standard spelling is ss; Swiss orthography would keep ss here too, so what we
# are catching is the opposite error, an author writing ß after a short vowel.
SHARP_S_ERRORS = re.compile(
    r"\b(daß|muß|müßen|läßt|gewißheit|küßen|haß|paß|schluß|fluß|kuß|nuß|riß|biß)\b",
    re.IGNORECASE)


def strings_of(node):
    for _, s in check_batch.walk_strings(node):
        yield s


def german_strings_of(node):
    """Only the strings that ARE German the learner is taught to produce.

    The variety checks exist to stop a non-standard form being TAUGHT. English explanatory
    prose that names a wrong form in order to reject it ("hat gesessen, not ist gesessen", "the
    form ist gestanden is a southern variant and is not used here") is the lesson doing its job,
    and scanning it produced nothing but false positives. Distance-based heuristics could not
    separate the two reliably, because how far the correction sits from the participle is a
    matter of English sentence style.

    So the scan is scoped by KEY instead: target-language text (`hr`, example `target`), and the
    answer surfaces a learner is graded against (`answer`, `accepted`, `options`, `ordered`).
    A wrong form appearing in any of those is a real defect. Anywhere else it is commentary.
    """
    for path, s in check_batch.walk_strings(node):
        if (path.endswith(".hr") or path.endswith(".target") or path.endswith(".answer")
                or ".options[" in path or ".ordered[" in path or ".accepted[" in path):
            yield s


def distractors_of(day):
    """Wrong MCQ options, which are allowed to contain exactly what the lesson forbids.

    Teaching "say hat gesessen, not ist gesessen" requires printing the wrong form as an
    option. Flagging that would punish the lesson for doing its job, so incorrect options are
    excluded from the variety checks. The correct answer is NOT excluded: if the wrong form
    ever became the answer, that is a genuine defect and must still fire.
    """
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


def check_german(path):
    errs = []
    try:
        days = json.load(io.open(path, encoding="utf-8"))
    except Exception:
        return []  # check_batch already reported the parse failure
    if not isinstance(days, list):
        return []

    for di, day in enumerate(days):
        tag = f"[{di}] {str(day.get('title', '?'))[:40]}"

        wrong_options = distractors_of(day)

        for s in german_strings_of(day):
            if s in wrong_options:
                continue
            m = SOUTHERN_PERFECT.search(s)
            if m and not taught_against(s, m.group(2)):
                errs.append(f"{tag}: southern perfect auxiliary in {s[:70]!r}, "
                            f"standard German takes haben")
            m = SHARP_S_ERRORS.search(s)
            if m:
                errs.append(f"{tag}: pre-reform or wrong sharp s {m.group(0)!r} in {s[:60]!r}")

        # Regional forms, scoped to the activity so contrastive teaching is allowed.
        for a in day.get("activities", []):
            blob = " ".join(s for s in strings_of(a) if s not in wrong_options).lower()
            for regional, standard in REGIONAL.items():
                # Both sides matched as whole words. A substring test would let the English
                # gloss "January" stand in for the German "Januar" and silently excuse
                # "Jänner", which is precisely the slip this check exists to catch.
                if (re.search(rf"\b{re.escape(regional)}\b", blob)
                        and not re.search(rf"\b{re.escape(standard)}\b", blob)):
                    errs.append(f"{tag}/{a.get('type')}: regional form {regional!r} without its "
                                f"standard counterpart {standard!r} in the same activity")
    return errs


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    total, bad = 0, 0
    for path in sys.argv[1:]:
        if not os.path.exists(path):
            print(f"MISSING {path}")
            bad += 1
            continue
        errs = check_batch.check_file(path) + check_german(path)
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
