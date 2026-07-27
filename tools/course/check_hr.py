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

Patterns match BOTH plain-ASCII input (a training/planning artifact where diacritics were
dropped) AND properly-diacriticized standard Croatian text (č/ć/š/ž/đ), since shipped content
always carries real diacritics. 2026-07-27: found via audit that the ASCII-only version of this
checker had NEVER matched real shipped content (real Croatian text carries diacritics, so
"trebas" never matches inside "trebaš"), meaning check_hr.py had been auditing nothing since the
day it stopped seeing pre-diacritic planning drafts.

Also 2026-07-27: this file, and check_batch.py underneath it, expect a bare JSON array of day
objects. Assembled course files are wrapped as {"title": ..., "days": [...]}. The CLI now
unwraps a top-level "days" key automatically so this checker can run directly against the
shipped course, not just pre-merge batches.

Usage:  python check_hr.py <file.json> [...]
"""
import io
import json
import os
import re
import sys

import check_batch

# Character classes covering both the ASCII-dropped and properly-diacriticized spelling of the
# same sound, so a pattern matches whichever way the string was actually typed.
_S = "[sš]"
_C = "[cč]"
_Z = "[zž]"
_DJ = "(?:đ|dj)"

# da + (optional clitic/pronoun) + a present-tense verb, right after a modal or semi-modal.
# The modal set is what actually takes a bare infinitive in Croatian.
DA_PRESENT = re.compile(
    r"\b(trebam|treba{s}|treba|trebamo|trebate|trebaju|mogu|mo{z}e{s}|mo{z}e|mo{z}emo|mo{z}ete|"
    r"moraju|moram|mora{s}|mora|moramo|morate|{z}elim|{z}eli{s}|{z}eli|{z}elimo|{z}elite|{z}ele|"
    r"poku{s}avam|poku{s}ava{s}|po{c}injem|volim|voli{s}|voli|volimo|namjeravam|smijem|smije{s}|"
    r"smije|ho{c}u|ho{c}e{s}|ho{c}e|ho{c}emo|ho{c}ete|umijem|znam)\s+da\b".format(
        s=_S, z=_Z, c=_C),
    re.IGNORECASE)

DA_LI = re.compile(r"\bda\s+li\b", re.IGNORECASE)

# ekavian reflexes whose ijekavian form is the standard. Each bare stem here is Serbian, so a
# whole-word hit is an error. Kept tight to avoid catching unrelated words, and to avoid the K6
# regression (flagging "vremena", the correct genitive of vrijeme, as ekavian): match only the
# bare ekavian stem, never a stem+suffix that could also be a correct ijekavian oblique form.
EKAVIAN = re.compile(
    r"\b(lepo|lep|lepa|lepi|lepe|lepog|lepom|mleko|mleka|mleku|vreme|dete|deca|dece|deci|decu|"
    r"{c}ovek|{c}oveka|{c}oveku|reka|reke|beo|belo|bela|beli|sneg|snega|cvet|cveta|cve{c}e|"
    r"mesto|mesta|mestu|uspeh|uspeha|pesma|pesme|pesmu|devojka|devojke|devojku|devojci|"
    r"ponedeljak|ponedeljka|nedelja|nedelje|nedelju|sused|suseda|susedi|ovde|gde|negde|nigde|"
    r"posle|uvek|celo|cela|celi|dve|zvezda|zvezde|re{c}nik|telo|tela|uspe{s}no|uspe{s}an|"
    r"verovatno|razumem|razume{s}|razume|razumemo|razumete|umem|ume{s}|hteti|hteo|htela|hteli|"
    r"leto|leta|letu|mesec|meseca|mesecu|meseci|vetar|vetra|vera|vere|veru|svetlo|svetla|"
    r"nedeljno|prevoz|prevoza|se{c}anje|se{c}am|ose{c}am|ose{c}a{s}|ose{c}anje|smejati|smeje{s}|"
    r"smeh|pobeda|pobede|savet|saveta|saveti)\b".format(c=_C, s=_S),
    re.IGNORECASE)

# Serbian lexis with a distinct Croatian counterpart. Month names use a STEM match with an
# optional case ending, because Croatian inflects heavily (januar, januara, januaru all Serbian;
# Croatian is sijecanj). "nedjelja" (with j, = Sunday) is CORRECT Croatian and not listed.
# voz matched by EXACT case-forms, not stem+\w* (K7: a stem+\w* regex over-matched vozac/vozilo/
# voziti, all standard Croatian, when only the Serbian train noun voz is the target).
SERBIAN_LEX = re.compile(
    r"\b(hleb\w*|hiljad\w*|vazduh\w*|voz|voza|vozu|vozom|vozovi|vozova|vozovima|vozove|"
    r"{s}argarep\w*|ka{s}ik\w*|vilju{s}k\w*|tako{dj}e\b|ostrv\w*|"
    r"vaspit\w*|porodic\w*|porodi{c}n\w*|fudbal\w*|pozori{s}t\w*|"
    r"januar\w*|februar\w*|avgust\w*|septembar\w*|septembr\w*|oktobar\w*|oktobr\w*|"
    r"novembar\w*|novembr\w*|decembar\w*|decembr\w*|"
    r"{z}elezni{c}\w*|obezbed\w*|sedmic\w*)\b".format(s=_S, dj=_DJ, c=_C, z=_Z),
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


def _unwrap(obj):
    """Assembled course files are {"title": ..., "days": [...]}; pre-merge batches are a bare
    array. Accept either so this checker runs against both."""
    if isinstance(obj, dict) and isinstance(obj.get("days"), list):
        return obj["days"]
    return obj


def check_croatian(path):
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
        days = _unwrap(json.load(io.open(path, encoding="utf-8")))
        if not isinstance(days, list):
            print(f"{os.path.basename(path):<16} SKIPPED (not a day array or {{title,days}})")
            continue
        errs = check_batch.check_file(path) + check_croatian(path)
        n = len(days)
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
