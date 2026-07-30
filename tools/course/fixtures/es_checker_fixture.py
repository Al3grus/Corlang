# -*- coding: utf-8 -*-
"""Negative-test fixture for check_es.py (Gold Book Phase 3, registry K5).

A checker that has never failed a planted defect is not known to check anything, and after any
loosening this fixture proves it still fires. Run it after EVERY change to check_es.py:

    python tools/course/fixtures/es_checker_fixture.py

Each case is (label, json_object, expect_fire, substring_expected_in_message). Half the cases
are planted defects that MUST fire; the other half are correct content that must NOT, and those
are the ones that actually matter, because every checker bug in the registry (K1, K3, K6, K7,
K11, K12, K16) was a false positive rather than a miss.
"""
import io
import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

import check_es


def day(level, title, activities):
    return [{
        "day": 1, "week": 1, "phase": "Phase 1", "level": level, "title": title,
        "objective": "o", "paretoFocus": "p", "reviewBlock": {"minutes": 15, "items": ["r"]},
        "activities": activities,
    }]


def learn(items, title="Learn"):
    return {"type": "LEARN", "title": title, "intro": "", "sources": ["pcic"], "items": items}


def mcq(prompt, options, answer, title="Practice"):
    return {"type": "EXERCISE", "title": title, "intro": "", "sources": ["pcic"], "questions": [
        {"type": "MCQ", "prompt": prompt, "difficulty": 4, "options": options,
         "answer": answer, "explanation": "e"}
    ]}


def dialogue(lines, title="Dialogue"):
    return {"type": "DIALOGUE", "title": title, "intro": "", "sources": ["pcic"], "lines": lines}


CASES = [
    # ---------------------------------------------------------------- must FIRE
    ("planted: American lexis with no peninsular counterpart", day("B1", "El transporte", [
        learn([{"hr": "Tengo un carro nuevo.", "en": "I have a new car."}])
    ]), True, "American form 'carro'"),

    ("planted: voseo as a production target", day("A2", "Saludos", [
        learn([{"hr": "Vos sos mi amigo.", "en": "You are my friend."}])
    ]), True, "voseo form 'sos'"),

    ("planted: seseo misspelling", day("A1", "Cortesía", [
        learn([{"hr": "Muchas grasias por todo.", "en": "Thank you for everything."}])
    ]), True, "seseo misspelling"),

    ("planted: missing written accent", day("A1", "Lugares", [
        learn([{"hr": "El cafe esta aqui, muy cerca.", "en": "The cafe is here, very near."}])
    ]), True, "missing written accent"),

    ("planted: missing accent on a -cion noun", day("A2", "Viajes", [
        learn([{"hr": "La estacion queda lejos.", "en": "The station is far away."}])
    ]), True, "missing written accent on 'estacion'"),

    ("planted: missing enye, the canonical case", day("A1", "El tiempo", [
        learn([{"hr": "El ano que viene voy a Espana.", "en": "Next year I am going to Spain."}])
    ]), True, "missing ñ"),

    ("planted: question with no opening mark", day("A1", "Preguntas", [
        dialogue([{"speaker": "Me", "hr": "Cómo te llamas?", "en": "What is your name?"}])
    ]), True, "missing opening ¿"),

    ("planted: exclamation with no opening mark", day("A1", "Saludos", [
        dialogue([{"speaker": "Partner", "hr": "Qué alegría verte!", "en": "How lovely to see you!"}])
    ]), True, "missing opening ¡"),

    # The A2 ceiling: the future in -re and the conditional are B1. Level-gated, so the same
    # sentence must fire at A2 and stay quiet at B1.
    ("planted: future tense at A2", day("A2", "Planes", [
        learn([{"hr": "Mañana hablaré con el jefe.", "en": "Tomorrow I will speak to the boss."}])
    ]), True, "future tense"),

    ("planted: short irregular future at A2", day("A2", "Planes", [
        learn([{"hr": "Iré al médico el lunes y será rápido.", "en": "I will go on Monday."}])
    ]), True, "future tense"),

    ("planted: conditional at A1", day("A1", "Cortesía", [
        learn([{"hr": "¿Podrías abrir la puerta?", "en": "Could you open the door?"}])
    ]), True, "conditional"),

    ("correct: the same future is fine at B1", day("B1", "Planes", [
        learn([{"hr": "Mañana hablaré con el jefe y me gustaría verte.",
                "en": "Tomorrow I will speak to the boss and I would like to see you."}])
    ]), False, None),

    # The collisions a naive future/conditional regex produces. Each was measured against real
    # authored content before the check was trusted.
    ("correct: present -emos is not a future", day("A2", "Presente", [
        learn([{"hr": "Queremos descansar un poco.", "en": "We want to rest a little."},
               {"hr": "Vosotros coméis muy tarde.", "en": "You all eat very late."}])
    ]), False, None),

    ("correct: preterite of an -ar-stem verb is not a future", day("A2", "Pasado", [
        learn([{"hr": "Ayer preparé la cena y paré el coche delante.",
                "en": "Yesterday I made dinner and parked the car outside."}])
    ]), False, None),

    ("correct: -ería shops and the name María are not conditionals", day("A2", "Tiendas", [
        learn([{"hr": "La librería está detrás de la panadería.", "en": "x"},
               {"hr": "Te presento a María, de la peluquería.", "en": "x"}])
    ]), False, None),

    ("correct: alemán, capitán, jamás, además, detrás are not futures", day("A2", "Repaso", [
        learn([{"hr": "Es alemán, y además jamás mira atrás.", "en": "x"},
               {"hr": "El capitán está allá, quizá detrás del sofá.", "en": "x"}])
    ]), False, None),

    # A distractor is still learner-visible, so the LEVEL ceiling applies to it even though the
    # variety and orthography checks exempt wrong options.
    ("planted: out-of-level tense hiding in a distractor", day("A2", "Costumbres", [
        mcq("Elige la frase que expresa una costumbre.",
            ["Cuando llego a casa, ceno enseguida.",
             "Cuando llegue a casa, cenaré enseguida.",
             "Cuando llegué a casa, cené enseguida.",
             "Cuando he llegado, he cenado."],
            "Cuando llego a casa, ceno enseguida.")
    ]), True, "in a distractor"),

    ("planted: imperfect subjunctive, the B2 ceiling breach", day("B1", "Condicionales", [
        learn([{"hr": "Si tuviera dinero, viajaría por el mundo.",
                "en": "If I had money, I would travel the world."}])
    ]), True, "imperfect subjunctive"),

    ("planted: imperfect subjunctive in -se", day("B1", "Deseos", [
        learn([{"hr": "Ojalá hubiese venido antes.", "en": "If only he had come earlier."}])
    ]), True, "imperfect subjunctive"),

    ("planted: quisiera, which this course replaces with querría", day("B1", "Cortesía", [
        learn([{"hr": "Quisiera reservar una mesa.", "en": "I would like to book a table."}])
    ]), True, "imperfect subjunctive"),

    # REGULAR imperfect subjunctives, which the strong-stem list cannot see and which are the
    # COMMON case. Two safe routes: the unambiguous plural endings, and the singular endings
    # only after si/que.
    ("planted: regular imperfect subjunctive after que", day("B1", "Peticiones", [
        learn([{"hr": "Me pidió que esperara un momento.", "en": "He asked me to wait."}])
    ]), True, "imperfect subjunctive"),

    ("planted: regular imperfect subjunctive, 2sg after si", day("B1", "Condicionales", [
        learn([{"hr": "Si esperaras un poco, lo verías.", "en": "If you waited, you would see."}])
    ]), True, "imperfect subjunctive"),

    ("planted: regular imperfect subjunctive, plural ending", day("B1", "Deseos", [
        learn([{"hr": "Ojalá hablaran más despacio.", "en": "If only they spoke more slowly."}])
    ]), True, "imperfect subjunctive"),

    ("planted: regular imperfect subjunctive, 1pl ending", day("B1", "Deseos", [
        learn([{"hr": "Quería que comiéramos juntos.", "en": "He wanted us to eat together."}])
    ]), True, "imperfect subjunctive"),

    # The nouns and adjectives a bare -ara/-aras sweep would swallow.
    ("correct: cara, clara, cámara, máscara and their plurals are not subjunctives",
     day("B1", "Objetos", [
         learn([{"hr": "La cara de mi hermana es muy clara.", "en": "x"},
                {"hr": "Tengo dos cámaras y varias máscaras.", "en": "x"},
                {"hr": "Las caras claras se ven mejor.", "en": "x"}])
     ]), False, None),

    # The false positives that made a batch-8 authoring agent REPHRASE CORRECT SPANISH to get
    # past the gate. `quieran` is the present subjunctive of querer, which this course teaches at
    # B1, and the rest are ordinary present-tense forms of verbs whose stem ends in -ar.
    ("correct: quieran is the present subjunctive of querer, not an imperfect one",
     day("B1", "Deseos", [
         learn([{"hr": "Quiero que quieran venir con nosotros.", "en": "x"},
                {"hr": "Espero que adquieran la costumbre.", "en": "x"}])
     ]), False, None),

    ("correct: present tense of -ar-stem verbs is not an imperfect subjunctive",
     day("B1", "Rutinas", [
         learn([{"hr": "Ellos aclaran la duda y comparan los precios.", "en": "x"},
                {"hr": "Los vecinos separan el vidrio y preparan la cena.", "en": "x"},
                {"hr": "Reparan el coche y declaran el total.", "en": "x"}])
     ]), False, None),

    ("correct: pasen is the present subjunctive of pasar", day("B1", "Cortesía", [
        learn([{"hr": "Espero que pasen pronto por la oficina.", "en": "x"}])
    ]), False, None),

    ("correct: envase, clase, frase, base are not subjunctives", day("B1", "Textos", [
        learn([{"hr": "Estas frases son las bases del texto.", "en": "x"},
               {"hr": "Compré el envase en esa clase de tienda.", "en": "x"}])
    ]), False, None),

    # The PERFECT subjunctive (haya hecho) is PCIC 9.2.3, also B2, and was missing entirely.
    ("planted: perfect subjunctive", day("B1", "Reacciones", [
        learn([{"hr": "Me alegro de que hayas venido.", "en": "I am glad you came."}])
    ]), True, "compound tense above B1"),

    ("correct: the present subjunctive it is built on is fine", day("B1", "Reacciones", [
        learn([{"hr": "Me alegro de que estés aquí.", "en": "I am glad you are here."}])
    ]), False, None),

    # The agentive passive is a B2 register move; B1 teaches impersonal and passive se.
    ("planted: agentive passive with por", day("B1", "Pasiva", [
        learn([{"hr": "El puente fue construido por los romanos.", "en": "x"}])
    ]), True, "agentive passive"),

    ("correct: estar + participle as a result state is B1", day("B1", "Estados", [
        learn([{"hr": "La puerta está cerrada y la tienda está abierta.", "en": "x"}])
    ]), False, None),

    ("planted: condicional compuesto", day("B1", "Hipótesis", [
        learn([{"hr": "Habría llegado antes con más tiempo.",
                "en": "He would have arrived earlier with more time."}])
    ]), True, "compound tense above B1"),

    ("planted: American form as the MCQ ANSWER still fires", day("B1", "Tecnología", [
        mcq("Choose the peninsular word for a computer.",
            ["computadora", "bicicleta", "teléfono", "cocina"], "computadora")
    ]), True, "American form 'computadora'"),

    # ---------------------------------------------------------- must NOT fire
    ("correct: American form paired contrastively in the same activity", day("B1", "Variedades", [
        learn([
            {"hr": "En España se dice coche.", "en": "In Spain they say coche."},
            {"hr": "En América se dice carro.", "en": "In America they say carro."},
        ])
    ]), False, None),

    ("correct: voseo shown against its tuteo counterpart", day("B1", "Variedades", [
        learn([
            {"hr": "En España decimos tú eres.", "en": "In Spain we say tú eres."},
            {"hr": "En Argentina dicen vos sos.", "en": "In Argentina they say vos sos."},
        ])
    ]), False, None),

    ("correct: American form as a WRONG MCQ option is exempt", day("B1", "Tecnología", [
        mcq("¿Cuál es la palabra peninsular?",
            ["ordenador", "computadora", "celular", "carro"], "ordenador")
    ]), False, None),

    ("correct: 'fuera' the adverb must not be read as a subjunctive", day("A2", "En casa", [
        learn([{"hr": "Espera fuera, por favor.", "en": "Wait outside, please."}])
    ]), False, None),

    ("correct: plural -ciones carries no accent", day("A2", "Viajes", [
        learn([{"hr": "Las estaciones están cerradas.", "en": "The stations are closed."}])
    ]), False, None),

    ("planted: -sión noun that Spanish spells with a single s", day("B1", "Trabajo", [
        learn([{"hr": "Su profesion es muy exigente.", "en": "His profession is very demanding."}])
    ]), True, "missing written accent on 'profesion'"),

    # The measured K16 case. An English word sitting in a scoped key (an MCQ option glossing a
    # meaning, a translation answer) must never be read as a Spanish accent error. -sion endings
    # collide across English, French and German; -cion collides with nothing, which is why the
    # generic rule matches only -cion and -sion is a closed single-s list.
    ("correct: English -sion words in scoped keys must not fire", day("B1", "Traducción", [
        mcq("Which English word translates 'la decisión'?",
            ["decision", "television", "version", "conclusion"], "decision")
    ]), False, None),

    ("planted: lowercase 'dia' and 'mia' are missing their accents", day("A1", "El tiempo", [
        learn([{"hr": "Cada dia leo un poco.", "en": "Every day I read a little."},
               {"hr": "Esa casa es mia.", "en": "That house is mine."}])
    ]), True, "missing written accent"),

    ("correct: the same forms in title case are names, not accent errors (K3)",
     day("A2", "Presentaciones", [
         dialogue([
             {"speaker": "Me", "hr": "Te presento a Mia, mi compañera.",
              "en": "This is Mia, my colleague."},
             {"speaker": "Partner", "hr": "Encantado. Yo soy Leon, el hermano de Tia.",
              "en": "Nice to meet you. I am Leon, Tia's brother."},
         ])
     ]), False, None),

    ("correct: 'esta' and 'si' and 'tu' are real words and must not fire", day("A1", "Casa", [
        learn([{"hr": "Si quieres, esta es tu casa.", "en": "If you like, this is your home."}])
    ]), False, None),

    ("correct: 'hubo' and 'habrá' alone are B1 forms of haber", day("B1", "Sucesos", [
        learn([
            {"hr": "Ayer hubo una fiesta en la plaza.", "en": "There was a party in the square."},
            {"hr": "Mañana habrá mucha gente.", "en": "Tomorrow there will be a lot of people."},
        ])
    ]), False, None),

    ("correct: 'para' and 'cara' must not match the -ra subjunctive sweep", day("A2", "Compras", [
        learn([{"hr": "Esta cara es para ti, pero la otra es muy cara.",
                "en": "This face is for you, but the other one is very expensive."}])
    ]), False, None),

    ("correct: vosotros -ís forms are the taught variety, not voseo", day("A2", "Vosotros", [
        learn([
            {"hr": "Vosotros vivís en Madrid.", "en": "You all live in Madrid."},
            {"hr": "¿De dónde venís?", "en": "Where are you coming from?"},
            {"hr": "¿Qué decís?", "en": "What are you saying?"},
        ])
    ]), False, None),

    ("correct: English commentary naming a wrong form is not scanned (K2/K16)",
     day("A1", "Ortografía", [
         learn([{"hr": "año", "en": "year",
                 "note": "Do not write ano or espanol: without the enye they are different "
                         "words. English speakers also say gracias as grasias, which is wrong."}])
     ]), False, None),

    ("correct: accented forms are clean", day("B1", "Repaso", [
        learn([
            {"hr": "Aquí está la estación, y también el café.",
             "en": "Here is the station, and the cafe too."},
            {"hr": "¿Cuándo llegaste? ¡Qué rápido!", "en": "When did you arrive? How fast!"},
            {"hr": "El año pasado el señor pequeño enseñaba español.",
             "en": "Last year the small gentleman taught Spanish."},
        ])
    ]), False, None),

    # --- The PROMPT / TITLE blind spot -----------------------------------------------------
    # A Phase 8c audit found four real B1-ceiling violations that no check could see, because
    # `.prompt` and an activity `.title` were in no scanned set. The patterns were always right;
    # the fields were never read. These six cases pin the fix in both directions.
    ("planted: imperfect subjunctive inside an MCQ PROMPT", day("B1", "Pasado", [
        mcq("¿Qué frase indica que el tren ya se había ido antes de que llegaran?",
            ["El tren salió antes", "El tren llegó tarde", "El tren no salió", "El tren espera"],
            "El tren salió antes")
    ]), True, "imperfect subjunctive"),

    ("planted: imperfect subjunctive in a DIALOGUE TITLE", day("B1", "Deseos", [
        dialogue([{"hr": "Hola, ¿qué tal?", "en": "Hi, how are you?"},
                  {"hr": "Muy bien, gracias.", "en": "Very well, thanks."}],
                 title="Si pudiera elegir")
    ]), True, "imperfect subjunctive"),

    ("correct: an ENGLISH prompt containing 'cafe' and 'menu' must not fire the accent list",
     day("A1", "Bar", [
         mcq("Order a cafe from the menu and note down the price you are given.",
             ["un café", "una casa", "una mesa", "un menú"], "un café")
     ]), False, None),

    ("correct: a prompt naming a present subjunctive the course teaches (K23)",
     day("B1", "Subjuntivo", [
         mcq("Elige la opción correcta: Haz lo que quieras cuando quieras.",
             ["quieras", "querías", "querrás", "quisiste"], "quieras")
     ]), False, None),

    ("correct: a prompt with present-tense -aran/-eran collisions (K23)",
     day("B1", "Datos", [
         mcq("Los datos aclaran que preparan la comida y comparan los precios.",
             ["aclaran", "aclaraban", "aclararon", "aclarar"], "aclaran")
     ]), False, None),

    ("planted: a future in -ré inside an A1 prompt, where the ceiling forbids it",
     day("A1", "Planes", [
         mcq("Completa la frase: Mañana hablaré con ella por teléfono.",
             ["hablaré", "hablo", "hablas", "habla"], "hablaré")
     ]), True, "future tense"),
]

# Generic-shape cases: the K14 class, where the checker used to crash or silently skip.
GENERIC_CASES = [
    ("planted: vocab pack with a missing enye",
     {"packs": [{"id": "p", "title": "t", "level": "A1", "sources": ["freq-es"], "words": [
         {"id": "manana", "hr": "manana", "en": "tomorrow", "pos": "adv."}
     ]}]}, True, "missing ñ"),

    ("planted: quiz whose FILL ANSWER is an American form",
     {"quizzes": [{"id": "q", "levelId": "B1", "title": "t", "questions": [
         {"type": "FILL", "prompt": "Voy en ___ al trabajo.", "difficulty": 5,
          "answer": "carro", "explanation": "e"}
     ]}]}, True, "American form 'carro'"),

    ("planted: exam FILL answer with a missing accent",
     [{"id": "e", "levelId": "A2", "title": "t", "passRule": "r", "sources": ["dele-a2"],
       "sections": [{"id": "s", "kind": "READING", "title": "t", "questions": [
           {"type": "FILL", "prompt": "Voy a la ___ de tren.", "difficulty": 5,
            "answer": "estacion", "explanation": "e", "strictDiacritics": True}
       ]}]}], True, "missing written accent"),

    ("correct: quizzes.json shape parses without crashing and stays clean",
     {"quizzes": [{"id": "q", "levelId": "A1", "title": "t", "questions": [
         {"type": "MCQ", "prompt": "¿Cómo se dice 'car' en España?",
          "difficulty": 4, "options": ["coche", "carro", "casa", "calle"],
          "answer": "coche", "explanation": "e"}
     ]}]}, False, None),

    ("correct: placement.json shape parses without crashing",
     {"title": "t", "intro": "i", "questions": [
         {"level": "A1", "startDay": 1, "type": "MCQ", "difficulty": 2,
          "prompt": "¿Dónde está el señor?", "options": ["aquí", "año", "casa", "mesa"],
          "answer": "aquí", "explanation": "e"}
     ]}, False, None),

    # --- `.name` / `.title` orthography ----------------------------------------------------
    # `Diccionario de la lengua espanola` and `panhispanico de dudas` both shipped unaccented in
    # the es resources.json. The accent check ran on that file and found nothing, because a
    # resource title lives at `.resources[n].name`, which was in no scanned set.
    ("planted: resource NAME missing its enye",
     {"resources": [{"rank": 1, "name": "Diccionario de la lengua espanola (RAE)",
                     "type": "book", "url": "https://example.org/", "why": "w"}]},
     True, "missing enye"),

    ("planted: resource NAME missing a written accent",
     {"resources": [{"rank": 2, "name": "Diccionario panhispanico de dudas (RAE)",
                     "type": "book", "url": "https://example.org/", "why": "w"}]},
     True, "missing written accent"),

    ("planted: a TITLE-CASE missing enye, invisible to the old lowercase-only pattern",
     {"resources": [{"rank": 3, "name": "Manana y pasado manana",
                     "type": "book", "url": "https://example.org/", "why": "w"}]},
     True, "missing enye"),

    ("correct: the accented resource names are quiet",
     {"resources": [
         {"rank": 1, "name": "Diccionario de la lengua española (RAE)", "type": "book",
          "url": "https://example.org/", "why": "w"},
         {"rank": 2, "name": "Diccionario panhispánico de dudas (RAE)", "type": "book",
          "url": "https://example.org/", "why": "w"},
         {"rank": 3, "name": "FundéuRAE, Fundación del Español Urgente", "type": "book",
          "url": "https://example.org/", "why": "w"},
     ]}, False, None),

    ("correct: English resource names and an American-variety proper name are not errors",
     {"resources": [
         {"rank": 3, "name": "Notes in Spanish", "type": "podcast",
          "url": "https://example.org/", "why": "w"},
         {"rank": 4, "name": "Radio Ambulante", "type": "podcast",
          "url": "https://example.org/", "why": "w"},
         {"rank": 4, "name": "News in Slow Spanish", "type": "podcast",
          "url": "https://example.org/", "why": "w"},
     ]}, False, None),

    ("correct: title-case Montana is the US state and carries no tilde",
     {"resources": [{"rank": 5, "name": "Montana", "type": "video",
                     "url": "https://example.org/", "why": "w"}]}, False, None),

    ("correct: the PCIC level names in levels.json need no accents",
     {"levels": [{"code": "A1", "title": "Beginner (Acceso)"},
                 {"code": "A2", "title": "Elementary (Plataforma)"},
                 {"code": "B1", "title": "Intermediate (Umbral)"}]}, False, None),
]


def main():
    failures = []
    for label, obj, should_fire, expect in CASES:
        tmp = os.path.join(os.path.dirname(os.path.abspath(__file__)), "_tmp_case.json")
        with io.open(tmp, "w", encoding="utf-8") as fh:
            json.dump(obj, fh, ensure_ascii=False)
        try:
            errs = check_es.check_spanish(tmp)
        finally:
            os.remove(tmp)
        fired = bool(errs)
        if fired != should_fire:
            failures.append(f"{label}: expected fire={should_fire}, got {errs}")
        elif should_fire and expect and not any(expect in e for e in errs):
            failures.append(f"{label}: fired but not for the right reason. "
                            f"expected {expect!r}, got {errs}")

    for label, obj, should_fire, expect in GENERIC_CASES:
        errs = check_es.check_spanish_generic("fixture", obj)
        fired = bool(errs)
        if fired != should_fire:
            failures.append(f"{label}: expected fire={should_fire}, got {errs}")
        elif should_fire and expect and not any(expect in e for e in errs):
            failures.append(f"{label}: fired but not for the right reason. "
                            f"expected {expect!r}, got {errs}")

    total = len(CASES) + len(GENERIC_CASES)
    if failures:
        print(f"FIXTURE FAILED: {len(failures)} of {total} cases wrong\n")
        for f in failures:
            print(f"  - {f}\n")
        sys.exit(1)
    print(f"check_es.py fixture: {total}/{total} cases correct "
          f"({sum(1 for c in CASES + GENERIC_CASES if c[2])} planted defects caught, "
          f"{sum(1 for c in CASES + GENERIC_CASES if not c[2])} correct cases left alone)")


if __name__ == "__main__":
    main()
