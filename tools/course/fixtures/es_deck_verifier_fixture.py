# -*- coding: utf-8 -*-
"""Negative-test fixture for verify_deck_es.py (Gold Book Phase 4, registry K5).

The deck verifier is the thing that decides whether a delivered vocab pack is acceptable
(registry P8: agent self-validation is not verification), so it is code, and code that has
never failed a planted defect is not known to check anything. Re-run after every change:

    python tools/course/fixtures/es_deck_verifier_fixture.py

The verb cases are the ones that matter most. The first version of the headword-in-example
check used a prefix match and immediately false-positived on 'poder' / 'No puedo salir hoy',
because radical-changing verbs alter the stem VOWEL. It now matches on the consonant skeleton,
and these cases pin that behaviour in both directions.
"""
import io
import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

import verify_deck_es as V


def pack(words, level="A1", sources=None):
    return {"packs": [{"id": "p", "title": "t", "level": level,
                       "sources": ["freq-es"] if sources is None else sources,
                       "words": words}]}


def w(**kw):
    d = {"id": "el libro", "hr": "el libro", "en": "book", "pos": "n. m.",
         "example": {"target": "Este libro es interesante.", "gloss": "This book is interesting."}}
    d.update(kw)
    return d


def v(lemma, tgt):
    return w(id=lemma, hr=lemma, en="x", pos="v.", example={"target": tgt, "gloss": "x"})


CASES = [
    ("clean baseline", pack([w()]), False, None),
    ("id not the lowercase of hr", pack([w(id="El Libro")]), True, "id should be"),
    ("duplicate id", pack([w(), w()]), True, "duplicate id"),
    ("unknown pos", pack([w(pos="noun")]), True, "unknown pos"),
    ("noun without its article", pack([w(id="libro", hr="libro")]), True,
     "without its definite article"),
    ("article and gender disagree", pack([w(id="la libro", hr="la libro", pos="n. m.",
        example={"target": "La libro es aqui.", "gloss": "x"})]), True, "but pos is"),
    ("el agua without an explanatory note", pack([w(id="el agua", hr="el agua", en="water",
        pos="n. f.", example={"target": "El agua está fría.", "gloss": "x"})]), True,
     "needs a note"),
    ("el agua with a note is correct", pack([w(id="el agua", hr="el agua", en="water",
        pos="n. f.", note="feminine, but takes el in the singular",
        example={"target": "El agua está fría.", "gloss": "x"})]), False, None),
    ("example does not show the headword (C18)", pack([w(
        example={"target": "Me gusta leer por la noche.", "gloss": "x"})]), True,
     "does not contain the headword"),

    # Radical-changing and irregular verbs: must NOT fire.
    ("o>ue verb: poder / puedo", pack([v("poder", "No puedo salir hoy.")]), False, None),
    ("e>ie verb: querer / quiero", pack([v("querer", "Quiero un café.")]), False, None),
    ("e>i verb: pedir / pido", pack([v("pedir", "Siempre pido lo mismo.")]), False, None),
    ("yo-form verb: poner / pongo", pack([v("poner", "Pongo la mesa cada día.")]), False, None),
    ("pronominal verb: levantarse / me levanto",
     pack([v("levantarse", "Me levanto a las siete.")]), False, None),
    ("stated exemption: ir / voy", pack([v("ir", "Voy al mercado.")]), False, None),
    ("stated exemption: hacer / hago",
     pack([v("hacer", "Hago la compra los sábados.")]), False, None),
    # Orthographic alternations: Spanish changes the SPELLING to keep the sound. Found by the
    # first real authored pack, which flagged both of these as missing their headword.
    ("g>j spelling change: coger / cojo",
     pack([v("coger", "Cojo el paraguas antes de salir.")]), False, None),
    ("g>j spelling change: recoger / recojo",
     pack([v("recoger", "Recojo mis libros de la mesa.")]), False, None),
    ("c>z spelling change: vencer / venzo",
     pack([v("vencer", "Nunca venzo en el ajedrez.")]), False, None),
    ("c>zc spelling change: conocer / conozco",
     pack([v("conocer", "Conozco muy bien esta ciudad.")]), False, None),
    # ...but a genuinely unrelated verb example must still fire.
    ("unrelated verb example still fires",
     pack([v("hablar", "Me gusta el cine y la música.")]), True, "does not contain the headword"),

    # Adjectives inflect for gender at the very END, so a prefix of the whole word fails:
    # 'bajo' does not appear in 'Mi hermana es baja'. Found by the second real authored pack.
    ("adjective agreeing in the example: bajo / baja",
     pack([w(id="bajo", hr="bajo", en="short", pos="adj.",
             example={"target": "Mi hermana es baja.", "gloss": "My sister is short."})]),
     False, None),
    ("adjective agreeing in the example: rubio / rubia",
     pack([w(id="rubio", hr="rubio", en="blond", pos="adj.",
             example={"target": "Mi hija es rubia como su madre.", "gloss": "x"})]), False, None),
    ("adjective in a genuinely unrelated example still fires",
     pack([w(id="alto", hr="alto", en="tall", pos="adj.",
             example={"target": "La casa es muy bonita.", "gloss": "x"})]), True,
     "does not contain the headword"),

    # Proper nouns take no article in Spanish, so the article rule cannot apply to them, but
    # their gender then has nowhere to live except the note.
    ("proper noun with a note is correct",
     pack([w(id="espa\u00f1a", hr="Espa\u00f1a", en="Spain", pos="n. f.",
             note="proper noun, used without an article",
             example={"target": "Espa\u00f1a tiene muchas ciudades bonitas.", "gloss": "x"})]),
     False, None),
    ("proper noun without a note leaves its gender unlearnable",
     pack([w(id="espa\u00f1a", hr="Espa\u00f1a", en="Spain", pos="n. f.",
             example={"target": "Espa\u00f1a tiene muchas ciudades bonitas.", "gloss": "x"})]),
     True, "only learnable from a note"),
    # Days and months are the Gold Book's named standing exception to the article rule:
    # "el enero" is unnatural Spanish and the shipped fr/it/pt courses all list them bare.
    ("day of the week without an article is correct",
     pack([w(id="lunes", hr="lunes", en="Monday", pos="n. m.",
             note="invariable in the plural: el lunes, los lunes",
             example={"target": "Los lunes trabajo desde casa.", "gloss": "x"})]), False, None),
    ("month without an article is correct",
     pack([w(id="enero", hr="enero", en="January", pos="n. m.",
             example={"target": "Enero es el primer mes del año.", "gloss": "x"})]), False, None),
    ("an ordinary noun still needs its article",
     pack([w(id="mes", hr="mes", en="month", pos="n. m.",
             example={"target": "Este mes tengo mucho trabajo.", "gloss": "x"})]), True,
     "without its definite article"),

    # The articles and object pronouns are themselves headwords, so a single-token headword
    # that happens to BE an article is not a noun carrying one.
    ("the article 'el' as its own headword",
     pack([w(id="el", hr="el", en="the (masculine singular)", pos="art.",
             note="used before a masculine singular noun: el coche",
             example={"target": "El coche es nuevo.", "gloss": "x"})]), False, None),
    ("the object pronoun 'las' as its own headword",
     pack([w(id="las", hr="las", en="them (feminine, direct object)", pos="pron.",
             example={"target": "Las necesito hoy.", "gloss": "x"})]), False, None),
    ("a noun phrase tagged as a verb still fires",
     pack([w(id="la casa", hr="la casa", en="house", pos="v.",
             example={"target": "La casa es grande.", "gloss": "x"})]), True,
     "which cannot take one"),
    # ...but a determiner or pronoun phrase cited WITH its article is correct Spanish, and no
    # mechanical test separates it from a mis-tag, so the check does not fire on adj./pron.
    ("determiner phrase cited with its article is correct",
     pack([w(id="el mismo", hr="el mismo", en="the same", pos="adj.",
             note="agrees in gender and number: el mismo, la misma, los mismos, las mismas",
             example={"target": "Vivimos en el mismo barrio.", "gloss": "x"})]), False, None),
    ("phrase headword whose verb conjugates inside it",
     pack([w(id="vivir con", hr="vivir con", en="to live with", pos="v.",
             example={"target": "Vivo con mis padres y mi hermano.", "gloss": "x"})]),
     False, None),

    ("a common noun cannot escape the article rule by being capitalised",
     pack([w(id="libro", hr="libro", en="book", pos="n. m.",
             example={"target": "El libro es interesante.", "gloss": "x"})]),
     True, "without its definite article"),

    # Multi-word headwords are PHRASES whose parts inflect and reorder independently.
    ("phrase headword with an inflected verb",
     pack([w(id="coger el metro", hr="coger el metro", en="to take the metro", pos="expr.",
             example={"target": "Cogemos el metro para ir al centro.", "gloss": "x"})]),
     False, None),
    ("phrase headword, noun phrase",
     pack([w(id="el paso de peatones", hr="el paso de peatones", en="pedestrian crossing",
             pos="n. m.",
             example={"target": "Cruza siempre por el paso de peatones.", "gloss": "x"})]),
     False, None),
    ("phrase headword in a genuinely unrelated example still fires",
     pack([w(id="el carn\u00e9 de conducir", hr="el carn\u00e9 de conducir", en="driving licence",
             pos="n. m.",
             example={"target": "Hoy hace mucho calor en la playa.", "gloss": "x"})]),
     True, "does not contain the headword"),

    # Epicene nouns take either article, and forcing one reproduces the exact defect the
    # Portuguese audit found (epicene nouns wrongly locked masculine).
    ("epicene noun with el",
     pack([w(id="el turista", hr="el turista", en="tourist", pos="n. m./f.",
             note="same form for both genders: el turista, la turista",
             example={"target": "El turista pregunta por el museo.", "gloss": "x"})]),
     False, None),
    ("epicene noun with la",
     pack([w(id="la estudiante", hr="la estudiante", en="student", pos="n. m./f.",
             note="same form for both genders",
             example={"target": "La estudiante llega temprano.", "gloss": "x"})]),
     False, None),
    ("a genuinely gendered noun still has to agree",
     pack([w(id="el casa", hr="el casa", en="house", pos="n. f.",
             example={"target": "El casa es grande.", "gloss": "x"})]),
     True, "but pos is"),

    # Irregular past participles share no usable stem with their infinitive (romper / roto),
    # so the skeleton match cannot see them.
    ("irregular participle: romperse / se ha roto",
     pack([v("romperse", "Se ha roto la pierna esquiando.")]), False, None),
    ("irregular participle: escribir / he escrito",
     pack([v("escribir", "Ya he escrito la carta.")]), False, None),
    ("irregular participle: volver / ha vuelto",
     pack([v("volver", "Mi hermano ha vuelto de Sevilla.")]), False, None),
    ("a verb whose example really is unrelated still fires after the participle rule",
     pack([v("romper", "El tren llega a las ocho.")]), True, "does not contain the headword"),

    # Spanish plurals carry a spelling change too: final -z becomes -ces.
    ("z to ces plural: la ra\u00edz / las ra\u00edces",
     pack([w(id="la ra\u00edz", hr="la ra\u00edz", en="root", pos="n. f.",
             example={"target": "Las ra\u00edces de ese \u00e1rbol son enormes.", "gloss": "x"})]),
     False, None),
    ("z to ces plural: el l\u00e1piz / los l\u00e1pices",
     pack([w(id="el l\u00e1piz", hr="el l\u00e1piz", en="pencil", pos="n. m.",
             example={"target": "Los l\u00e1pices est\u00e1n en el caj\u00f3n.", "gloss": "x"})]), False, None),
    ("a noun whose example really is unrelated still fires",
     pack([w(id="la ra\u00edz", hr="la ra\u00edz", en="root", pos="n. f.",
             example={"target": "El coche es muy r\u00e1pido.", "gloss": "x"})]), True,
     "does not contain the headword"),

    # Strong preterites replace the stem's consonants outright, so neither a prefix nor a
    # consonant skeleton survives them. Matched by suffix so compounds are covered too.
    ("strong preterite: detenerse / se detuvo",
     pack([v("detenerse", "El tren se detuvo en la \u00faltima estaci\u00f3n.")]), False, None),
    ("strong preterite inside a phrase: ponerse a / se puso a",
     pack([w(id="ponerse a", hr="ponerse a", en="to start doing", pos="expr.",
             example={"target": "En cuanto lleg\u00f3 a casa, se puso a estudiar.", "gloss": "x"})]),
     False, None),
    ("strong preterite in a compound: proponer / propuso",
     pack([v("proponer", "Mi jefe propuso otra fecha.")]), False, None),
    ("a strong-preterite verb with an unrelated example still fires",
     pack([v("detenerse", "El pan est\u00e1 muy rico hoy.")]), True,
     "does not contain the headword"),

    ("em dash in the gloss", pack([w(en="book \u2014 a thing")]), True, "em/en dash"),
    ("missing example", pack([{"id": "el sol", "hr": "el sol", "en": "sun", "pos": "n. m."}]),
     True, "missing or incomplete example"),
    ("pack citing pcic, which is unearned for vocabulary",
     pack([w()], sources=["freq-es", "pcic"]), True, "NOT earned for vocabulary"),
    ("pack with no sources at all", pack([w()], sources=[]), True, "missing sources"),
    ("check_es still applies: missing enye", pack([w(id="el ano", hr="el ano", en="year",
        pos="n. m.", example={"target": "Este ano voy a Espana.", "gloss": "x"})]), True,
     "missing ñ"),
    ("check_es still applies: the B1 ceiling",
     pack([v("viajar", "Si tuviera dinero, viajaría más.")], level="B1"), True,
     "imperfect subjunctive"),
]


def main():
    tmp = os.path.join(os.path.dirname(os.path.abspath(__file__)), "_deck_tmp.json")
    failures = []
    for label, obj, should, expect in CASES:
        with io.open(tmp, "w", encoding="utf-8") as fh:
            json.dump(obj, fh, ensure_ascii=False)
        try:
            _, problems = V.verify([tmp])
        finally:
            os.remove(tmp)
        fired = bool(problems)
        if fired != should:
            failures.append(f"{label}: expected fire={should}, got {problems}")
        elif should and expect and not any(expect in p for p in problems):
            failures.append(f"{label}: fired for the wrong reason, "
                            f"expected {expect!r}, got {problems}")
    if failures:
        print(f"FIXTURE FAILED: {len(failures)} of {len(CASES)} cases wrong\n")
        for f in failures:
            print(f"  - {f}\n")
        sys.exit(1)
    print(f"verify_deck_es.py fixture: {len(CASES)}/{len(CASES)} cases correct "
          f"({sum(1 for c in CASES if c[2])} planted defects caught, "
          f"{sum(1 for c in CASES if not c[2])} correct cases left alone)")


if __name__ == "__main__":
    main()
