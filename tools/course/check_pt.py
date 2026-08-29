# -*- coding: utf-8 -*-
"""Portuguese (European, pt-PT) specific batch checks, layered on top of check_batch.py.

The shared checker enforces the invariants every language shares. This adds the ways a machine
author drifts specifically in Portuguese: BRAZILIAN forms creeping into what should be standard
European Portuguese. Corlang teaches Portugal's variety exclusively (most platforms only offer
Brazilian), so this is the highest-value drift class here, exactly parallel to check_hr.py's
Serbian guard, check_de.py's Austrian/Swiss guard and check_fr.py's Belgian/Swiss/Quebec guard.

Two independent checks, each scoped differently on purpose:

  1. BRAZIL_LEXIS: the same 17-word Brazilian-vs-European table as
     ContentValidationTest.kt's `portuguese content contains no Brazilianisms` (the Kotlin gate
     that already ships), with the SAME activity-scoped exemption logic: a Brazilian form is
     allowed only when its European counterpart appears anywhere in the SAME scope. Kotlin
     flattens EVERY string in an activity (or, outside any activity, the whole file) regardless
     of key, so an English explanation that names and rejects the Brazilian form also satisfies
     the exemption; this mirrors that (unscoped by key), but per DAY rather than per file for
     the "outside an activity" bucket (title/objective/paretoFocus/drills/reviewBlock/
     resources) -- equal-or-stricter than Kotlin (never lets a correction on a distant day
     rescue a slip on this one), verified empirically to add zero false positives against the
     shipped course (see the audit report; nothing in pt's plan files relies on a cross-day
     rescue).

  2. Brazilian progressive (estar/andar/continuar + GERUND, where European Portuguese uses
     estar a / andar a / continuar a + INFINITIVE): scoped by KEY like check_hr.py / check_de.py
     / check_fr.py -- only strings that ARE Portuguese the learner is taught to produce (hr,
     target, answer, options[], ordered[], accepted[]), never English commentary. MCQ wrong
     distractors are exempt via distractors_of(), and a string that also shows the correct
     "estar a" form is treated as contrastive teaching.

Two more European-Portuguese authoring requirements named in docs/language-standard.md were
investigated and DELIBERATELY NOT implemented as regex checks, because the real shipped course
was checked first and both would be false-positive generators:

  - tu vs você: você is NOT simply "the Brazilian default" here. Corlang's own A1 content has a
    whole lesson (phase1-a1.json, "Tu, você ou o senhor?") that teaches você as a genuine,
    correctly-used European word (avoided as a bare address term because it can sound blunt, but
    real and taught, including as vocab id "você" and as MCQ prompts/answers ABOUT it). A
    blanket "você is wrong" rule would flag that lesson's own correct content -- exactly the
    failure mode check_fr.py hit with "char"/"blonde". Not implemented.

  - enclise vs proclise (chamo-me vs me chamo): genuinely too syntactically complex for a
    reliable regex within key-scoping. Proof from the real course: phase1-a1.json day 34 ("Os
    verbos reflexos") has the FILL answer "te chamas" -- correct ONLY because "Como" (the
    proclisis trigger) sits in the PROMPT, a key this checker does not and should not scan (it's
    English/mixed instructional text). Day 38 ("me sinto", triggered by "Não" in the prompt) and
    phase2-a2.json day 8 ("me viu", triggered by "que" in the prompt) are the same pattern: a
    checker that flags any answer/accepted string starting with me/te/lhe/lhes would flag these
    THREE genuinely correct answers as errors, on a course that also has dedicated, correctly
    taught lessons on exactly this rule (phase2-a2.json day 8 is literally titled "Enclise e
    proclise"). A trigger-word-aware version would need to see the prompt, which reintroduces
    the English-commentary false-positive class the KEY-scoping discipline exists to avoid, and
    reliably parsing "does this prompt contain a proclisis trigger" is not a regex task. Flagged
    here as a limitation for human/AI review, not automated.

Usage:  python check_pt.py <file.json> [...]
"""
import io
import json
import os
import re
import sys

import check_batch

# ---------------------------------------------------------------------------------------------
# 1. Brazilian lexis, activity-scoped (mirrors ContentValidationTest.kt exactly).
# ---------------------------------------------------------------------------------------------

BRAZIL_LEXIS = {
    "ônibus": "autocarro", "celular": "telemóvel", "banheiro": "casa de banho",
    "sorvete": "gelado", "geladeira": "frigorífico", "açougue": "talho",
    "esporte": "desporto", "aeromoça": "hospedeira", "café da manhã": "pequeno-almoço",
    "caminhão": "camião", "usuário": "utilizador", "gerenciar": "gerir",
    "bonde": "elétrico", "encanador": "canalizador", "faxina": "limpeza",
    "grampeador": "agrafador", "história em quadrinhos": "banda desenhada",
}


def _flatten(node):
    """All strings under node, space joined, regardless of key (mirrors Kotlin's flatten())."""
    out = []

    def rec(x):
        if isinstance(x, dict):
            for v in x.values():
                rec(v)
        elif isinstance(x, list):
            for v in x:
                rec(v)
        elif isinstance(x, str):
            out.append(x)

    rec(node)
    return " ".join(out)


def _day_scopes(day):
    """One scope per LEARN/EXERCISE/DIALOGUE activity, plus one scope for the day's own
    non-activity text (title, objective, paretoFocus, drills, reviewBlock, resources).
    Mirrors Kotlin's scopes(): activities are not recursed into further once found."""
    activities = []
    outside = {k: v for k, v in day.items() if k != "activities"}
    for a in day.get("activities", []):
        if a.get("type") in ("LEARN", "EXERCISE", "DIALOGUE"):
            activities.append(_flatten(a))
    activities.append(_flatten(outside))
    return activities


def check_brazil_lexis(days):
    errs = []
    for di, day in enumerate(days):
        tag = f"[{di}] {str(day.get('title', '?'))[:40]}"
        for scope in _day_scopes(day):
            text = scope.lower()
            for term, european in BRAZIL_LEXIS.items():
                present = re.search(rf"(?<!\w){re.escape(term)}(?!\w)", text) is not None
                if present and european not in text:
                    errs.append(f"{tag}: Brazilian lexis {term!r} without its European "
                                f"counterpart {european!r} in the same activity")
    return errs


# ---------------------------------------------------------------------------------------------
# 2. Brazilian progressive (estar/andar/continuar + gerund), key-scoped.
# ---------------------------------------------------------------------------------------------

# A closed whitelist of actual gerund forms of common verbs, not a bare "\w+ndo" suffix regex.
# That was tried first and rejected: real shipped pt content contains "quando" (115x, "when"),
# "lindo" (an adjective, "O por do sol estava lindo" = "the sunset was beautiful", a genuine
# estar+adjective sentence in vocab/15), "Fernando" (a name), "compreendo"/"entendo"/
# "arrependo"/"mando"/"recomendo" (present-tense verb forms, not gerunds) and "referendo"/
# "remendo" (nouns) -- all matching that suffix. A closed list of real gerunds is the same
# "narrow reliable list beats a broad regex" call check_fr.py made for Quebec lexis after its
# own false-positive round with char/blonde/barrer/jaser.
GERUND_WHITELIST = (
    r"trabalhando|estudando|fazendo|comendo|dormindo|chovendo|vivendo|aprendendo|ouvindo|"
    r"dizendo|tentando|ajudando|mudando|chegando|saindo|entrando|voltando|ficando|passando|"
    r"contando|pensando|sentindo|morando|brincando|jogando|cozinhando|correndo|escrevendo|"
    r"lendo|comprando|vendendo|pagando|esperando|procurando|gostando|precisando|treinando|"
    r"falando|trocando|marcando|avariando|adaptando|chorando|dançando|cantando|nadando|"
    r"chamando|mexendo|mentindo|brigando|arrumando|limpando|conduzindo|construindo|"
    r"discutindo|repetindo|assistindo|caminhando|correndo|nascendo|crescendo|acontecendo"
)

AUX_FORMS = (
    r"estou|est[áa]s|est[áa]|estamos|est[ãa]o|estava|estavas|est[áa]vamos|estavam|estive|"
    r"esteve|estivemos|estiveram|ando|andas|anda|andamos|andam|andava|andavas|and[áa]vamos|"
    r"andavam|continuo|continuas|continua|continuamos|continuam|continuava|continuavas|"
    r"continu[áa]vamos|continuavam"
)

ESTAR_GERUND = re.compile(
    rf"\b({AUX_FORMS})\b[^.?!]{{0,15}}\b({GERUND_WHITELIST})\b", re.IGNORECASE)

# A string that ALSO shows the correct "estar/andar/continuar a" form is contrastive teaching
# (an explanation printing both forms side by side), not drift, mirroring check_de.py's
# taught_against() for the southern-perfect auxiliary.
AUX_A_TAUGHT = re.compile(rf"\b({AUX_FORMS})\s+a\b", re.IGNORECASE)


def pt_strings_of(node):
    """Only target-language text and graded answer surfaces."""
    for path, s in check_batch.walk_strings(node):
        if (path.endswith(".hr") or path.endswith(".target") or path.endswith(".answer")
                or ".options[" in path or ".ordered[" in path or ".accepted[" in path):
            yield s


def distractors_of(day):
    """Wrong MCQ options, which are allowed to contain exactly what the lesson forbids.

    Teaching "say estou a trabalhar, not estou trabalhando" requires printing the wrong form as
    an option (this course does exactly that, repeatedly). The correct answer is NOT excluded:
    if the wrong form ever became the answer, that is a genuine defect and must still fire.
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


def check_gerund(days):
    errs = []
    for di, day in enumerate(days):
        tag = f"[{di}] {str(day.get('title', '?'))[:40]}"
        wrong = distractors_of(day)
        for s in pt_strings_of(day):
            if s in wrong:
                continue
            m = ESTAR_GERUND.search(s)
            if m and not AUX_A_TAUGHT.search(s):
                errs.append(f"{tag}: Brazilian progressive {m.group(0)!r} in {s[:70]!r}, "
                            f"European Portuguese uses estar/andar/continuar + a + infinitivo")
    return errs


# ---------------------------------------------------------------------------------------------
# Generic (non-day-shaped) files: vocab packs, quizzes, placement, exams, grammar, meta,
# levels, feynman, cheatsheet. These do not have day/activities structure, so they are scanned
# whole-file instead of per-day, which is exactly how ContentValidationTest.kt's own Brazilianism
# gate treats any file with no LEARN/EXERCISE/DIALOGUE activity in it (the whole file becomes
# ONE "outside" scope). MCQ distractors are found anywhere in the tree, not just under
# day.activities, since quizzes/placement/exams put "type": "MCQ" questions directly under
# "questions" with no activity wrapper.
# ---------------------------------------------------------------------------------------------


def _generic_distractors(data):
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


def check_generic_lexis(label, data):
    text = _flatten(data).lower()
    errs = []
    for term, european in BRAZIL_LEXIS.items():
        if (re.search(rf"(?<!\w){re.escape(term)}(?!\w)", text)
                and european not in text):
            errs.append(f"{label}: Brazilian lexis {term!r} without its European "
                        f"counterpart {european!r} anywhere in the file")
    return errs


def check_generic_gerund(label, data):
    wrong = _generic_distractors(data)
    errs = []
    for s in pt_strings_of(data):
        if s in wrong:
            continue
        m = ESTAR_GERUND.search(s)
        if m and not AUX_A_TAUGHT.search(s):
            errs.append(f"{label}: Brazilian progressive {m.group(0)!r} in {s[:70]!r}, "
                        f"European Portuguese uses estar/andar/continuar + a + infinitivo")
    return errs


def _unwrap(obj):
    """Assembled course files are {"title": ..., "days": [...]}; pre-merge batches are a bare
    array. Accept either so this checker runs against both."""
    if isinstance(obj, dict) and isinstance(obj.get("days"), list):
        return obj["days"]
    return obj


def _is_day_shaped(days):
    return (isinstance(days, list) and len(days) > 0 and isinstance(days[0], dict)
            and "activities" in days[0])


def check_portuguese(path):
    """Day-shaped plan files only (pre-merge batch or assembled {title, days})."""
    errs = []
    try:
        days = _unwrap(json.load(io.open(path, encoding="utf-8")))
    except Exception:
        return []
    if not _is_day_shaped(days):
        return []

    errs += check_brazil_lexis(days)
    errs += check_gerund(days)
    return errs


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    total, bad = 0, 0
    # No arguments means the whole shipped course. Reporting "0 days total, 0 problems"
    # and exiting 0 because nobody passed a file is a green light that checked nothing.
    paths = sys.argv[1:] or check_batch.course_files("pt")
    if not paths:
        print("nothing to check: pass batch files, or restore app/src/main/assets/content/pt/")
        sys.exit(2)
    for path in paths:
        if not os.path.exists(path):
            print(f"MISSING {path}")
            bad += 1
            continue
        raw = json.load(io.open(path, encoding="utf-8"))
        days = _unwrap(raw)
        if _is_day_shaped(days):
            errs = check_batch.check_file(path) + check_portuguese(path)
            n = len(days)
            label = f"{n} days"
        else:
            errs = (check_generic_lexis(os.path.basename(path), raw)
                    + check_generic_gerund(os.path.basename(path), raw))
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
