# -*- coding: utf-8 -*-
"""Independent re-verification of a delivered Spanish vocabulary pack (Gold Book Phase 4).

Registry P8: "every delivered batch independently re-verified; agent self-validation is not
verification." An Italian vocab agent once reported a file as validated and it had three
article misses. This script is what actually decides, and it is deliberately not the same code
the authoring agent ran.

Checks, in the order the Gold Book lists them:
  1. Shape and strict-ish parse: {"packs": [{id, title, level, sources, words: [...]}]}.
  2. id is the lowercase NFC of hr, character for character.
  3. Ids unique within the file, and across every file passed in one run.
  4. pos drawn from the allowed vocabulary.
  5. Article and gender cross-check: a noun must carry a definite article, and the article must
     agree with the declared pos. The el/la exception for feminine nouns beginning with a
     stressed a (el agua, el aula, el hambre) is allowed when pos says n. f. AND the note
     explains it, which is the only way a learner can tell it from a masculine noun.
  6. Every word has an example whose target actually CONTAINS the headword. This is registry
     C18 in Spanish clothing: the Croatian deck shipped aspect pairs whose example never showed
     the headword itself, and nobody noticed because the example was about the right topic.
  7. Dash scan and NFC normalisation over every string.
  8. Level tag matches the requested level.
  9. check_es.py run over the whole file, so the variety, accent and ceiling rules apply to the
     deck exactly as they apply to lessons.

Usage:  python verify_deck_es.py --level A1 --expect 130 <file.json> [...]
        python verify_deck_es.py <file.json> [...]        (no count/level assertion)
"""
import argparse
import io
import json
import os
import re
import sys
import unicodedata

import check_es

POS_OK = {
    "n. m.", "n. f.", "n. m./f.", "n. m. pl.", "n. f. pl.",
    "v.", "adj.", "adv.", "prep.", "conj.", "pron.", "num.", "expr.", "art.", "interj.",
}
DASH = re.compile(r"[–—]")
ARTICLES = {"el": "n. m.", "la": "n. f.", "los": "n. m.", "las": "n. f."}
# Feminine nouns taking el in the singular because they begin with a stressed a sound.
EL_FEMININE = {
    "agua", "aula", "hambre", "alma", "águila", "arma", "área", "aula", "ala", "ave", "hacha",
}
# The Gold Book's standing exception to the article rule, quoted: "Nouns carry their article in
# `hr` AND `id` (gender is unlearnable otherwise); days and months are the standing exception in
# Italian-type languages." Spanish months essentially never take an article, so "el enero" would
# be unnatural, and the shipped fr/it/pt courses all list them bare. Closed set on purpose.
DAYS_AND_MONTHS = {
    "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo",
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
}


def strip_accents(s):
    return "".join(c for c in unicodedata.normalize("NFD", s)
                   if unicodedata.category(c) != "Mn")


def headword_core(hr):
    """The part of the headword an example must contain, article removed."""
    parts = hr.split()
    if parts and parts[0].lower() in ARTICLES:
        parts = parts[1:]
    return " ".join(parts) if parts else hr


# Verbs whose stem changes beyond any mechanical resemblance in the forms an example is likely
# to use (ir -> voy, ser -> soy, decir -> digo, hacer -> hago, saber -> sé). For these the
# containment check is skipped and only the presence of an example is required. Keeping the
# list explicit and short is the point: it is a stated exemption, not a silently loosened check.
STEM_UNRECOGNISABLE = {
    "ser", "ir", "irse", "haber", "hacer", "saber", "ver", "dar", "decir", "oír", "estar",
    "caber", "valer", "andar", "jugar",
}
# Irregular past participles share no usable stem with their infinitive (romper -> roto), so a
# skeleton match cannot see them. Listed explicitly rather than exempting the whole verb, which
# would also switch the check off for that verb's regular forms.
IRREGULAR_PARTICIPLES = {
    "romper": "roto", "escribir": "escrito", "poner": "puesto", "volver": "vuelto",
    "abrir": "abierto", "morir": "muerto", "cubrir": "cubierto", "resolver": "resuelto",
    "freír": "frito", "imprimir": "impreso", "describir": "descrito", "devolver": "devuelto",
    "envolver": "envuelto", "descubrir": "descubierto", "componer": "compuesto",
    "proponer": "propuesto", "suponer": "supuesto", "deshacer": "deshecho",
    "satisfacer": "satisfecho", "prever": "previsto", "disolver": "disuelto",
}
CONSONANTS = re.compile(r"[^aeiou]")
# Spanish keeps a sound constant across a conjugation and changes the SPELLING to do it, so a
# raw consonant skeleton is not stable after all. Collapsing each alternating pair onto one
# letter makes it stable. Found by the first real authored pack, which flagged 'coger' against
# 'Cojo el paraguas' and 'recoger' against 'Recojo mis libros', both perfectly correct Spanish.
#   g/j    coger -> cojo, recoger -> recojo, elegir -> elijo
#   c/z/q  vencer -> venzo, empezar -> empiece, buscar -> busqué, conocer -> conozco
#   h      silent throughout: oler -> huelo
SPELLING_ALTERNATIONS = str.maketrans({"j": "g", "z": "c", "q": "c", "h": None})


def _skeleton(s):
    """Consonant skeleton, accent-free, with the regular orthographic alternations collapsed.
    Radical-changing verbs alter only the stem VOWEL (poder -> puedo, querer -> quiero,
    pedir -> pido), so the consonants survive and are the right thing to match on. A naive
    prefix match does not survive e>ie / o>ue and false-positived on 'poder' against 'No puedo
    salir hoy' the first time this script ran."""
    skel = "".join(CONSONANTS.findall(strip_accents(s.lower())))
    return skel.translate(SPELLING_ALTERNATIONS)


def example_shows_headword(hr, target, pos):
    """The example must actually show the headword or a plausible inflection of it. This is
    registry C18 in Spanish clothing: the Croatian deck shipped aspect pairs whose example never
    contained the headword, and nobody noticed because the example was about the right topic."""
    core = strip_accents(headword_core(hr).lower())
    tgt = strip_accents(target.lower())
    if not core:
        return False
    if core in tgt:
        return True

    # A multi-word headword is a PHRASE (coger el metro, el paso de peatones, la habitación
    # doble). Its parts inflect and reorder independently, so demanding the whole string is
    # wrong: 'Cogemos el metro para ir al centro' is a perfect example of 'coger el metro' and
    # contains none of it verbatim. Require one substantial token instead.
    tokens = [t for t in core.split() if len(t) >= 4]
    if len(core.split()) > 1:
        if not tokens:
            return True
        return any(t[:4] in tgt or _skeleton(t)[:3] in _skeleton(tgt) for t in tokens)

    first = core.split()[0]

    if pos and pos.startswith("v."):
        if first in STEM_UNRECOGNISABLE:
            return True                      # stated exemption, see above
        # A pronominal headword is the same verb: romperse -> romper.
        plain = re.sub(r"se$", "", first) if first.endswith("se") else first
        part = IRREGULAR_PARTICIPLES.get(plain) or IRREGULAR_PARTICIPLES.get(first)
        if part and strip_accents(part) in tgt:
            return True
        stem = re.sub(r"(arse|erse|irse|ar|er|ir)$", "", first)
        skel = _skeleton(stem)[:3]
        if len(skel) < 2:
            return True                      # too short to discriminate (dar, ver, ir)
        return skel in _skeleton(tgt)

    # Adjectives inflect for gender AND number at the very end (bajo/baja, rubio/rubia,
    # trabajador/trabajadora, inteligente/inteligentes), so a prefix of the whole word is not
    # safe: 'bajo' does not appear in 'Mi hermana es baja'. Strip the inflecting final vowel
    # first. Found by the second real authored pack.
    if pos and pos.startswith("adj."):
        stem = re.sub(r"[oae]$", "", first)
        return bool(stem) and stem in tgt

    # Nouns and the rest inflect only for number, so a prefix is safe and stricter.
    stem = first[:5] if len(first) > 5 else first
    return bool(stem) and stem in tgt


def verify(paths, level=None, expect=None):
    problems = []
    seen_ids = {}
    total = 0

    for path in paths:
        base = os.path.basename(path)
        try:
            raw = json.load(io.open(path, encoding="utf-8"))
        except Exception as e:
            problems.append(f"{base}: does not parse as JSON: {e}")
            continue

        if not isinstance(raw, dict) or not isinstance(raw.get("packs"), list):
            problems.append(f"{base}: top level must be {{'packs': [...]}}")
            continue

        for pack in raw["packs"]:
            for field in ("id", "title", "level", "words"):
                if field not in pack:
                    problems.append(f"{base}: pack missing '{field}'")
            if not pack.get("sources"):
                problems.append(f"{base}/{pack.get('id')}: missing sources (provenance rule)")
            for s in pack.get("sources", []):
                if s == "pcic":
                    problems.append(
                        f"{base}/{pack.get('id')}: cites 'pcic', which is NOT earned for "
                        f"vocabulary until the Phase 8b deck cross-check")
            if level and pack.get("level") != level:
                problems.append(f"{base}/{pack.get('id')}: level is "
                                f"{pack.get('level')!r}, expected {level!r}")

            words = pack.get("words", [])
            total += len(words)

            for w in words:
                wid, hr = w.get("id"), w.get("hr")
                tag = f"{base}/{wid or hr or '?'}"
                if not hr or not wid:
                    problems.append(f"{tag}: missing id or hr")
                    continue
                if not w.get("en", "").strip():
                    problems.append(f"{tag}: empty gloss")

                # 2. id is the lowercase NFC of hr
                want = unicodedata.normalize("NFC", hr).lower()
                if unicodedata.normalize("NFC", wid) != want:
                    problems.append(f"{tag}: id should be {want!r}, got {wid!r}")
                if unicodedata.normalize("NFC", wid) != wid:
                    problems.append(f"{tag}: id is not NFC-normalised")

                # 3. uniqueness across the whole run
                key = unicodedata.normalize("NFC", wid)
                if key in seen_ids:
                    problems.append(f"{tag}: duplicate id, already in {seen_ids[key]}")
                else:
                    seen_ids[key] = base

                # 4. pos vocabulary
                pos = w.get("pos")
                if not pos:
                    problems.append(f"{tag}: missing pos")
                elif pos not in POS_OK:
                    problems.append(f"{tag}: unknown pos {pos!r}")

                # 5. article and gender cross-check
                first = hr.split()[0].lower() if hr.split() else ""
                # A single capitalised token is a PROPER noun (España, Francia, Madrid), and
                # Spanish country and city names take no article, so the article rule cannot
                # apply to them. Narrow on purpose: one token, initial capital. A multi-word
                # name keeps its article (el Ebro), and a capitalised common noun does not
                # escape, because it would have to be a single token to qualify. It must still
                # carry a note, since that is the only way the learner learns the gender.
                is_proper = (len(hr.split()) == 1 and hr[:1].isupper())
                is_calendar = hr.strip().lower() in DAYS_AND_MONTHS
                if pos and pos.startswith("n.") and is_calendar:
                    pass                     # Gold Book standing exception, see DAYS_AND_MONTHS
                elif pos and pos.startswith("n.") and is_proper:
                    if not (w.get("note") or "").strip():
                        problems.append(f"{tag}: proper noun takes no article, so its gender is "
                                        f"only learnable from a note, and it has none")
                elif pos and pos.startswith("n."):
                    if first not in ARTICLES:
                        problems.append(f"{tag}: noun without its definite article "
                                        f"(gender is unlearnable without it)")
                    else:
                        expected = ARTICLES[first]
                        noun = headword_core(hr).split()[0].lower() if headword_core(hr) else ""
                        el_fem_ok = (first == "el" and pos == "n. f."
                                     and strip_accents(noun) in
                                     {strip_accents(x) for x in EL_FEMININE})
                        if el_fem_ok and not (w.get("note") or "").strip():
                            problems.append(
                                f"{tag}: 'el' with a feminine noun needs a note saying why, "
                                f"or the learner reads it as masculine")
                        elif pos == "n. m./f.":
                            # Epicene noun: one form, both genders, distinguished only by the
                            # article (el turista / la turista, el estudiante / la estudiante).
                            # Either article is correct, and FORCING one would reproduce the
                            # exact defect the Portuguese audit found, where epicene nouns were
                            # wrongly locked masculine.
                            pass
                        elif not el_fem_ok and pos not in (expected, expected + " pl."):
                            problems.append(f"{tag}: article {first!r} says {expected!r} "
                                            f"but pos is {pos!r}")
                elif (first in ARTICLES and len(hr.split()) > 1
                        and pos not in ("expr.",)):
                    # Multi-token only. The articles and object pronouns are themselves
                    # headwords (el, la, los, las), and a single-token headword that happens to
                    # BE an article is not a noun carrying one.
                    problems.append(f"{tag}: carries an article but pos is {pos!r}, not a noun")

                # 6. the example must actually show the headword
                ex = w.get("example")
                if not isinstance(ex, dict) or not ex.get("target") or not ex.get("gloss"):
                    problems.append(f"{tag}: missing or incomplete example")
                elif not example_shows_headword(hr, ex["target"], pos):
                    problems.append(f"{tag}: example does not contain the headword: "
                                    f"{ex['target'][:60]!r}")

                # 7. dashes and normalisation
                for field in ("hr", "en", "note"):
                    v = w.get(field)
                    if isinstance(v, str):
                        if DASH.search(v):
                            problems.append(f"{tag}: em/en dash in {field}")
                        if unicodedata.normalize("NFC", v) != v:
                            problems.append(f"{tag}: {field} is not NFC-normalised")
                if isinstance(ex, dict):
                    for field in ("target", "gloss"):
                        v = ex.get(field, "")
                        if DASH.search(v):
                            problems.append(f"{tag}: em/en dash in example.{field}")

        # 9. the language checker, over the whole file
        problems += check_es.check_spanish_generic(base, raw)

    if expect is not None and total != expect:
        problems.append(f"word count is {total}, expected {expect}")

    return total, problems


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("paths", nargs="+")
    ap.add_argument("--level")
    ap.add_argument("--expect", type=int)
    args = ap.parse_args()

    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    total, problems = verify(args.paths, args.level, args.expect)
    print(f"{total} words across {len(args.paths)} file(s): "
          f"{'OK' if not problems else str(len(problems)) + ' PROBLEMS'}")
    for p in problems[:60]:
        print(f"    - {p}")
    if len(problems) > 60:
        print(f"    ... and {len(problems) - 60} more")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
