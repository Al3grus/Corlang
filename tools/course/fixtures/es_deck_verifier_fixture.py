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
    # ...but a genuinely unrelated verb example must still fire.
    ("unrelated verb example still fires",
     pack([v("hablar", "Me gusta el cine y la música.")]), True, "does not contain the headword"),

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
