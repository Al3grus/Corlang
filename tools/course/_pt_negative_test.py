# -*- coding: utf-8 -*-
"""Negative-test fixture for check_pt.py: plants correct European forms and incorrect Brazilian
forms, and asserts the checker flags exactly the incorrect ones with zero false positives.
Deleted after the audit confirms the checker; not part of the shipped tool set."""
import sys

sys.path.insert(0, ".")
import check_pt as pt


def day(title, **overrides):
    d = {
        "day": 0, "week": 0, "phase": "test", "level": "A1", "title": title,
        "objective": "test", "paretoFocus": "test", "resources": [],
        "drills": ["a", "b"], "reviewBlock": {"minutes": 5, "items": ["a", "b"]},
        "activities": [],
    }
    d.update(overrides)
    return d


def exercise(questions):
    return {"type": "EXERCISE", "sources": ["test"], "questions": questions}


def learn(items):
    return {"type": "LEARN", "sources": ["test"], "items": items}


cases = []  # (description, day_dict, expect_lexis_hit, expect_gerund_hit)

# ---- Brazilian lexis: bare, no correction anywhere in the day -> MUST flag ----
for term, european in pt.BRAZIL_LEXIS.items():
    cases.append((
        f"bare Brazilian lexis {term!r}",
        day(f"lex-bad-{term}", activities=[learn([
            {"hr": f"Vou apanhar o {term}.", "en": "irrelevant"}])]),
        True, False,
    ))

# ---- Brazilian lexis: correction present in the SAME activity -> must NOT flag ----
cases.append((
    "contrastive lexis (MCQ distractor + correct answer, same activity)",
    day("lex-contrastive", activities=[exercise([
        {"type": "MCQ", "prompt": "How do you say bus in Portugal?", "difficulty": 2,
         "options": ["autocarro", "ônibus", "camião", "elétrico"],
         "answer": "autocarro", "explanation": "Brazil says onibus, Portugal says autocarro."},
    ])]),
    False, False,
))
cases.append((
    "contrastive lexis (explanation names + rejects Brazilian form, same activity)",
    day("lex-contrastive-2", activities=[learn([
        {"hr": "telemóvel", "en": "cell phone",
         "note": "Brazil says celular; Portugal says telemóvel."},
    ])]),
    False, False,
))

# ---- Brazilian lexis: pure European vocabulary, all 17 European counterparts, no Brazilian
# term anywhere -> must NOT flag (this is what a clean course actually looks like) ----
cases.append((
    "all-European vocabulary, no Brazilian terms present",
    day("lex-clean", activities=[learn([
        {"hr": w, "en": "x"} for w in pt.BRAZIL_LEXIS.values()
    ])]),
    False, False,
))

# ---- Gerund progressive: bare Brazilian progressive as the ANSWER -> MUST flag ----
cases.append((
    "Brazilian progressive as MCQ answer",
    day("ger-bad-answer", activities=[exercise([
        {"type": "MCQ", "prompt": "I am working:", "difficulty": 3,
         "options": ["Estou trabalhando.", "Trabalho.", "Trabalhei.", "Vou trabalhar."],
         "answer": "Estou trabalhando.", "explanation": "wrong on purpose for the test"},
    ])]),
    False, True,
))
cases.append((
    "Brazilian progressive as FILL answer",
    day("ger-bad-fill", activities=[exercise([
        {"type": "FILL", "prompt": "Complete: ___ (I am studying)", "difficulty": 3,
         "answer": "Estou estudando", "accepted": ["estou estudando"],
         "explanation": "wrong on purpose"},
    ])]),
    False, True,
))

# ---- Gerund progressive: as a wrong MCQ distractor (the real, correct teaching pattern this
# course actually uses) -> must NOT flag ----
cases.append((
    "Brazilian progressive as wrong MCQ distractor only",
    day("ger-distractor", activities=[exercise([
        {"type": "MCQ", "prompt": "The European Portuguese way to say 'I am working' is…",
         "difficulty": 3,
         "options": ["Estou a trabalhar.", "Estou trabalhando.", "Vou a trabalhar.",
                     "Sou a trabalhar."],
         "answer": "Estou a trabalhar.",
         "explanation": "Portugal uses estar a + infinitive; estou trabalhando is Brazilian."},
    ])]),
    False, False,
))

# ---- Gerund progressive: contrastive within the SAME string -> must NOT flag ----
cases.append((
    "Brazilian progressive named contrastively in the same answer string",
    day("ger-same-string", activities=[exercise([
        {"type": "FILL", "prompt": "Say it the European way:",
         "answer": "Estou a trabalhar, nunca estou trabalhando.", "difficulty": 3},
    ])]),
    False, False,
))

# ---- Correct estar a + infinitivo -> must NOT flag ----
cases.append((
    "correct estar a + infinitivo",
    day("ger-correct", activities=[learn([
        {"hr": "Estou a trabalhar.", "en": "I am working"},
        {"hr": "Está a chover.", "en": "It is raining"},
        {"hr": "Andamos a estudar português.", "en": "We've been studying Portuguese"},
    ])]),
    False, False,
))

# ---- False-positive traps for the gerund suffix (words that end -ando/-endo/-indo but are NOT
# gerunds of whitelisted verbs, or are legitimate non-progressive uses) -> must NOT flag ----
cases.append((
    "estar + adjective (estava lindo), not estar + gerund",
    day("ger-trap-lindo", activities=[learn([
        {"hr": "O pôr do sol estava lindo.", "en": "The sunset was beautiful"},
    ])]),
    False, False,
))
cases.append((
    "quando (when) near an estar form, not a gerund",
    day("ger-trap-quando", activities=[learn([
        {"hr": "Estou em casa quando chove.", "en": "I am home when it rains"},
    ])]),
    False, False,
))
cases.append((
    "adverbial gerund with no estar (correct European Portuguese)",
    day("ger-trap-adverbial", activities=[learn([
        {"hr": "Respondeu sorrindo.", "en": "He answered smiling"},
        {"hr": "Vai andando, eu já te apanho.", "en": "Go on ahead, I will catch up"},
    ])]),
    False, False,
))
cases.append((
    "present-tense verb forms that end in -endo, not gerunds (entendo, compreendo)",
    day("ger-trap-present", activities=[learn([
        {"hr": "Eu não entendo esta frase.", "en": "I do not understand this sentence"},
        {"hr": "Compreendo perfeitamente.", "en": "I understand perfectly"},
    ])]),
    False, False,
))
cases.append((
    "proper name Fernando, not a gerund",
    day("ger-trap-name", activities=[learn([
        {"hr": "O meu tio chama-se Fernando.", "en": "My uncle is called Fernando"},
    ])]),
    False, False,
))

# ---- Enclise/proclise real-course false-positive traps: confirm we do NOT implement a rule
# that would misfire on these genuinely correct, trigger-in-the-prompt answers. Since no
# enclise/proclise check exists, these simply must not be flagged by anything. ----
cases.append((
    "'te chamas' as FILL answer, correct because Como (the trigger) is in the prompt",
    day("encl-trap-1", activities=[exercise([
        {"type": "FILL", "prompt": "Fill in the two words: 'Como ___ ___?'", "difficulty": 5,
         "answer": "te chamas", "accepted": ["Te chamas"],
         "explanation": "Como pulls the pronoun in front of the verb."},
    ])]),
    False, False,
))
cases.append((
    "'me sinto' as FILL answer, correct because Não (the trigger) is in the prompt",
    day("encl-trap-2", activities=[exercise([
        {"type": "FILL", "prompt": "Fill in: 'Não ___ ___ nada bem.'", "difficulty": 5,
         "answer": "me sinto", "accepted": ["Me sinto"],
         "explanation": "The negative não pulls the pronoun forward."},
    ])]),
    False, False,
))


def run():
    fails = []
    for desc, d, expect_lexis, expect_gerund in cases:
        lexis_hits = pt.check_brazil_lexis([d])
        gerund_hits = pt.check_gerund([d])
        got_lexis = len(lexis_hits) > 0
        got_gerund = len(gerund_hits) > 0
        ok = (got_lexis == expect_lexis) and (got_gerund == expect_gerund)
        status = "OK" if ok else "FAIL"
        if not ok:
            fails.append(desc)
        print(f"[{status}] {desc}  (lexis expect={expect_lexis} got={got_lexis}, "
              f"gerund expect={expect_gerund} got={got_gerund})")
        if not ok:
            for e in lexis_hits + gerund_hits:
                print(f"        -> {e}")

    print(f"\n{len(cases)} cases, {len(fails)} failed")
    if fails:
        print("FAILED:", fails)
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(run())
