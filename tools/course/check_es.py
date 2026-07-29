# -*- coding: utf-8 -*-
"""Spanish-specific batch checks, layered on check_batch.py.

Same design as check_de.py / check_it.py / check_pt.py: the shared checker enforces what every
language shares, this adds the ways a machine author drifts in Spanish specifically. Written
with every lesson from K1-K17 baked in from the first line rather than discovered later:

  * KEY-scoped throughout (K2/K16). Only strings that ARE Spanish the learner is taught to
    produce, never English commentary that prints a wrong form in order to reject it.
  * Assembled {"title", "days"} shape unwrapped (K8/K13/K17), and a generic-shape fallback for
    vocab packs, quizzes.json, placement.json, exams.json and grammar.json (K14). A checker
    that silently returns [] on the only file shape that ships is worse than no checker.
  * Whole-word matching on BOTH sides of every contrastive rule (K1).
  * Only forms that are genuinely unambiguous (K3). The list of things deliberately NOT checked
    is as important as the list of things checked, and is written out below with reasons.

THE VARIETY POLICY THIS ENCODES (docs/sources/es-exams.md §2.4). The DELE guides state that
A1/A2 input texts use peninsular Spanish, that B1 input texts span all varieties, and that any
Hispanic norm followed coherently is valid in candidate production. So this checker's job is
NOT "ban American Spanish". It is the activity-scoped contrastive rule used by check_de.py:
an American form taught as a production target with no peninsular counterpart in the same
activity is a defect; the same form as a labelled contrast, or as a wrong MCQ option, is
correct content and must not fire.

The checks:

  1. AMERICAN lexis without its peninsular counterpart in the same activity. Contrastive
     teaching is allowed and expected, especially in B1 receptive material.
  2. VOSEO forms (vos sos, tenés, hablás) as production targets, with the same same-activity
     exemption against their tuteo counterparts.
  3. SESEO SPELLINGS (grasias, sapato, corason). Always errors, never a variety: these are
     phonetic misspellings, not the American norm, which spells exactly as Spain does.
  4. MISSING WRITTEN ACCENTS and MISSING Ñ, restricted to forms that are not words at all
     without them. This is the Spanish analogue of the systemic missing-è bug found in Italian
     (registry item 9), and it matters practically because exam FILL answers are graded
     strictly.
  5. MISSING ¿ and ¡ on a produced Spanish question or exclamation. Mechanical and unambiguous.
  6. OFF-SYLLABUS GRAMMAR ABOVE B1, which per docs/sources/es-exams.md §5.7 is the
     highest-value check in this file. The PCIC puts the imperfecto de subjuntivo at B2, so
     `si tuviera dinero, viajaría` is beyond a B1 course, and nearly every commercial Spanish
     syllabus puts it at B1, which is exactly why a parallel authoring agent reaches for it
     unprompted. Also flagged: futuro perfecto, condicional compuesto and pretérito anterior.

DELIBERATELY NOT CHECKED, and why. Each of these was considered and rejected because it cannot
be matched without false positives, which is the K3/K6/K7/K12 failure mode:

  * `esta`/`está`, `si`/`sí`, `tu`/`tú`, `el`/`él`, `mas`/`más`, `que`/`qué`, `como`/`cómo`,
    `se`/`sé`, `de`/`dé`, `donde`/`dónde`, `cuando`/`cuándo`, `quien`/`quién`. Both members of
    every pair are real words, so a bare form is never evidence of an error. These are reviewer
    items and are covered by the Phase 8c language audit instead.
  * `ingles` for `inglés`. High frequency and tempting, but `ingles` is the plural of `ingle`
    (groin) and the language name is lowercase in Spanish, so casing cannot separate them.
  * `hacia` for `hacía` and `sabia` for `sabía`: both bare forms are ordinary words (towards,
    wise). `rio` for `río` likewise (the preterite of reír is spelled `rio`).
  * `papa`, `plata`, `saco`, `chico`, `lentes`, `departamento`, `tomar`, `manejar` as American
    lexis. Every one of them is also an ordinary peninsular word in another sense (the Pope,
    silver, I take out, boy, contact lenses, a department, to take, to handle), so listing them
    would flag correct Spanish. `carro` and `jugo` ARE listed despite a similar risk, because
    the same-activity counterpart rule contains it and the pairs are too central to omit; the
    residual false positive (a shopping-trolley or meat-juices lesson) is loud, not silent.
  * Voseo forms ending in -ís (`vivís`, `venís`, `salís`, `decís`, `escribís`). These are
    IDENTICAL to the peninsular vosotros forms this course teaches, so matching them would
    flag the course's own target variety. Only the unambiguous -ás/-és/`sos` forms are listed.
  * The vosotros paradigm being absent from a conjugation table. Real and important, but it is
    a gap rather than a string, and no per-string checker can see a missing row. Reviewer item.
  * `he comido hoy` against `comí hoy`. The peninsular/American perfect split is a usage
    difference between two sentences that are both grammatical in isolation, so any regex over
    it would fire on correct content. Reviewer item.

Usage:  python check_es.py <file.json> [...]
"""
import io
import json
import os
import re
import sys

import check_batch

# --- 1. American lexis -> the peninsular form this course teaches -------------------------
# Whole-word on BOTH sides (K1). Scoped to produced-Spanish keys only (K2/K16). Exempted when
# the peninsular counterpart appears in the SAME activity, which is how contrastive teaching
# and the B1 "expose the American form" requirement stay legal.
AMERICAN = {
    "carro": "coche", "carros": "coches",
    "computadora": "ordenador", "computadoras": "ordenadores",
    "celular": "móvil", "celulares": "móviles",
    "jugo": "zumo", "jugos": "zumos",
    "boleto": "billete", "boletos": "billetes",
    "frijoles": "judías", "frijol": "judía",
    "durazno": "melocotón", "duraznos": "melocotones",
    "elevador": "ascensor", "elevadores": "ascensores",
    "refrigerador": "nevera", "refrigeradora": "nevera",
    "alberca": "piscina", "albercas": "piscinas",
    "arvejas": "guisantes", "arveja": "guisante",
    "banana": "plátano", "bananas": "plátanos",
}

# --- 2. Voseo -> the tuteo form ------------------------------------------------------------
# Only forms that cannot collide with the peninsular vosotros paradigm. The -ís forms (vivís,
# venís, salís, decís, escribís) are excluded because they ARE the vosotros forms.
VOSEO = {
    "sos": "eres",
    "tenés": "tienes", "querés": "quieres", "podés": "puedes", "sabés": "sabes",
    "hacés": "haces", "ponés": "pones", "venés": "vienes", "debés": "debes",
    "hablás": "hablas", "trabajás": "trabajas", "estudiás": "estudias",
    "comés": "comes", "bebés": "bebes", "creés": "crees",
}

# --- 3. Seseo/ceceo misspellings: always wrong, in every variety ---------------------------
SESEO = re.compile(
    r"\b(grasias|sapato|sapatos|corason|corason|sielo|veses|entonses|"
    r"desir|haser|empesar|comensar|serveza|asul)\b", re.IGNORECASE)

# --- 4a. Missing written accent: forms that are NOT words without it ------------------------
MISSING_ACCENT = re.compile(
    r"\b(aqui|alli|asi|ahi|tambien|despues|ademas|adios|quizas|jamas|detras|atras|"
    r"estan|estais|estare|estara|adonde|aun(?=\s+no)|"
    r"cafe|menu|autobus|jardin|jamon|corazon|razon|salon|balcon|rincon|marron|"
    r"frances|aleman|japones|portugues|dificil|facil|util|debil|arbol|azucar|"
    r"telefono|musica|rapido|gramatica|numero|ultimo|proximo|publico|medico|"
    r"tenia|habia|podia|queria|decia|veia|comia|salia|vivia|ponia|venia|traia|leia|"
    r"creia|dormia|sentia|seguia|pedia|frio|"
    r"buenisimo|buenisima|altisimo|altisima)\b",
    re.IGNORECASE)

# Same rule, but matched LOWERCASE ONLY because the bare form collides with a proper name in
# title case (Mia, Mio, Tia, Dia, Dias, Leon). This is check_it.py's MISSING_ACCENT_LOWER
# precedent, which exists because check_it v1 flagged "Sara" the name as an unaccented verb (K3).
MISSING_ACCENT_LOWER = re.compile(r"\b(dia|dias|tio|tia|mio|mia|leon)\b")

# Every Spanish noun in -ción / -sión carries the accent in the SINGULAR and loses it in the
# plural (estación -> estaciones), so the singular is matched and the plural is not.
#
# -CION ONLY, deliberately. A first draft matched -[cs]ion, and an empirical run of that pattern
# over the scoped keys of all five shipped courses returned 40+ distinct -sion hits, every one
# of them an ordinary French or German word (conclusion, décision, télévision, pression,
# expression, version, discussion, profession, occasion...) and not one a Spanish error. -cion
# returned ZERO hits in the same run, because no English, French or German word ends in it.
# That is the K16 English-collision bug caught by measurement before it could ship rather than
# after it false-flagged real content.
MISSING_ACCENT_CION = re.compile(r"\b\w{2,}cion\b", re.IGNORECASE)

# The -sión nouns that CAN be matched safely: Spanish spells them with a single s where English
# and French use ss or cc, so the bare Spanish form is not a word in either. Everything whose
# unaccented spelling is also an English word (decision, television, version, tension, division,
# revision, dimension, pension, confusion, conclusion) is deliberately absent, and the missing
# accent on those is a reviewer item instead. Zero false positives is worth a few known misses.
MISSING_ACCENT_SION = re.compile(
    r"\b(presion|impresion|expresion|profesion|discusion|mision|ocasion|admision|"
    r"comision|sesion|posesion|agresion|obsesion|progresion)\b",
    re.IGNORECASE)

# --- 4b. Missing ñ: the bare form is a different word, or no word at all --------------------
# Lowercase-only where the bare form collides with a proper name (Nina, Ana). "ano" for "año"
# is the canonical reason this check exists and is crude enough that it must always fire.
MISSING_ENYE = re.compile(
    r"\b(espanol|espanola|espanoles|manana|senor|senora|senores|senorita|"
    r"pequeno|pequena|pequenos|pequenas|bano|banos|sueno|suenos|ensenar|ensena|"
    r"companero|companera|montana|montanas|otono|ninos|ninas|"
    r"cumpleanos|ano|anos)\b")

# --- 5. Missing opening ¿ / ¡ --------------------------------------------------------------
def _missing_opener(s):
    """A produced Spanish sentence ending in ? or ! must carry its opening mark. Applied only
    to whole target-language sentences (.hr / .target), never to answers or options, which are
    legitimately fragments."""
    t = s.strip()
    if t.endswith("?") and "¿" not in t:
        return "?"
    if t.endswith("!") and "¡" not in t:
        return "!"
    return None

# --- 6. Above the B1 ceiling (docs/sources/es-exams.md §5.7) --------------------------------
# The imperfecto de subjuntivo is PCIC B2, so it is off-syllabus for this course at EVERY
# level, unlike check_it.py's passato remoto which is only wrong below B1. `fuera` is
# deliberately absent: it is also the ordinary adverb "outside" (espera fuera), the exact K3
# collision this rule set exists to avoid. `fuese` has no such collision and is listed.
# Unambiguous strong stems only. This is deliberately NOT a bare "-ra" sweep: a generic
# \w+ara pattern would swallow ordinary nouns (cara, para, tiara, máscara) and adjectives
# (rara, clara, avara), which is the K7 over-matching failure in a new costume.
IMPERFECT_SUBJ = re.compile(
    r"\b(tuvi|hici|pudi|quisi|estuvi|supi|dij|vini|hubi|pusi|traj|anduvi|cupi|condujer)"
    r"(era|eras|éramos|erais|eran|ese|eses|ésemos|eseis|esen)\b",
    re.IGNORECASE)
# Compound tenses above B1: they are only wrong when a participle actually follows, because
# habrá / habría / hubo on their own are perfectly good B1 forms of haber.
COMPOUND_ABOVE_B1 = re.compile(
    r"\b(habr[éáíé]\w*|habría\w*|habrías|hubo|hubieron)\s+\w+[aií]d[oa]s?\b",
    re.IGNORECASE)

# --- 6b. Above the A2 ceiling: the future in -ré and the conditional are B1 -------------------
# Level-scoped like check_it.py's passato remoto: correct at B1, off-syllabus below it. Found
# missing when a batch-6 agent reported that it had caught a future-tense distractor BY HAND
# because no check existed. A measurement over the six authored A1/A2 batches then found three
# real violations (¿Podrías abrir la puerta? at A1, "Cuando llegue a casa, cenaré" at A2, and
# "volveré" at A2), so the gap was live and not theoretical.
#
# ONLY THE UNAMBIGUOUS FORMS ARE MATCHED, because the obvious regexes are unsafe and the same
# measurement proved it:
#   * `-aré` collides with the preterite of a verb whose STEM ends in -ar: preparé, paré,
#     comparé, declaré. Both are stem + é. Excluded entirely.
#   * `-emos` / `-eremos` collides with the ordinary present of -er verbs: `queremos` contains
#     "eremos". All first-person-plural futures excluded.
#   * a generic conditional `-aría|-ería|-iría` collides with a whole noun class (librería,
#     panadería, peluquería, cafetería, categoría) and with the name María. Closed list only.
# What remains is safe: the 2sg, 3sg and 3pl futures of all three conjugations, the -eré and
# -iré first persons (the -er/-ir preterite is -í, so there is no collision), and a closed list
# of the frequent conditionals.
#
# Two refinements the fixture forced, both measured rather than guessed:
#   * `-éis` is OUT of the generic pattern. It collides with the present vosotros of -er verbs
#     whose stem ends in -er: `queréis` is "quer" + "éis" and is ordinary present tense, while
#     the future is `querréis` with two r's. (`coméis` does not collide, because its stem has no
#     -er.) The vosotros future is rare in course content, so this costs almost nothing.
#   * `-aré` IS matched, because excluding it missed two of the three real defects this check was
#     built for (`cenaré`, `hablaré`). Its one collision is the preterite of a verb whose STEM
#     ends in -ar (preparé, paré, declaré), and that is an ENUMERABLE set, so it is exempted by
#     name below rather than by dropping the whole class. Match the class, list the collisions.
FUTURE_ABOVE_A2 = re.compile(
    r"\b\w*(?:ar|er|ir)(?:ás|á|án)\b"
    r"|\b\w*(?:ar|er|ir)é\b"
    r"|\b(?:tendr|pondr|saldr|vendr|podr|sabr|querr|habr|har|dir|valdr)"
    r"(?:é|ás|á|án|éis)\b",
    re.IGNORECASE)
# Preterites of -ar verbs whose stem itself ends in -ar, which therefore look like -aré futures.
AR_STEM_PRETERITES = {
    "preparé", "paré", "comparé", "separé", "declaré", "disparé", "reparé", "aclaré",
    "amparé", "encaré", "deparé", "maré", "aparé", "prepararé",
}
CONDITIONAL_ABOVE_A2 = re.compile(
    r"\b(?:podría|tendría|haría|diría|sería|estaría|querría|sabría|habría|iría|vendría|"
    r"saldría|pondría|gustaría|encantaría|debería|valdría|vería|daría|pediría|diríamos)"
    r"(?:s|mos|is|n)?\b",
    re.IGNORECASE)


def spanish_strings_of(node):
    """Only target-language text and graded answer surfaces (K2). See check_de.german_strings_of."""
    for path, s in check_batch.walk_strings(node):
        if (path.endswith(".hr") or path.endswith(".target") or path.endswith(".answer")
                or ".options[" in path or ".ordered[" in path or ".accepted[" in path):
            yield s


def sentence_strings_of(node):
    """Whole target-language sentences only, for the ¿ / ¡ check. Answers, options and reorder
    tokens are legitimately fragments and must not be asked to carry an opening mark."""
    for path, s in check_batch.walk_strings(node):
        if path.endswith(".hr") or path.endswith(".target"):
            yield s


def distractors_of(day):
    """Wrong MCQ options may legitimately contain what the lesson forbids."""
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
    array. Accept either, or this checker silently validates nothing (K8/K13/K17)."""
    if isinstance(obj, dict) and isinstance(obj.get("days"), list):
        return obj["days"]
    return obj


def _is_day_shaped(days):
    return (isinstance(days, list) and len(days) > 0 and isinstance(days[0], dict)
            and "activities" in days[0])


def _checks_on_string(s, level=""):
    """Everything that can be decided from one string alone. The contrastive rules (AMERICAN,
    VOSEO) need activity context and live in the callers. [level] gates the checks that are
    level-dependent rather than absolute."""
    errs = []
    if level in ("A1", "A2"):
        m = FUTURE_ABOVE_A2.search(s)
        if m and m.group(0).lower() not in AR_STEM_PRETERITES:
            errs.append(f"future tense {m.group(0)!r} at {level} in {s[:60]!r}, "
                        f"the future in -re is B1")
        m = CONDITIONAL_ABOVE_A2.search(s)
        if m:
            errs.append(f"conditional {m.group(0)!r} at {level} in {s[:60]!r}, "
                        f"the conditional is B1")
    m = SESEO.search(s)
    if m:
        errs.append(f"seseo misspelling {m.group(0)!r} in {s[:60]!r}")
    m = (MISSING_ACCENT.search(s) or MISSING_ACCENT_LOWER.search(s)
         or MISSING_ACCENT_CION.search(s) or MISSING_ACCENT_SION.search(s))
    if m:
        errs.append(f"missing written accent on {m.group(0)!r} in {s[:60]!r}")
    m = MISSING_ENYE.search(s)
    if m:
        errs.append(f"missing ñ in {m.group(0)!r} in {s[:60]!r}")
    m = IMPERFECT_SUBJ.search(s)
    if m:
        errs.append(f"imperfect subjunctive {m.group(0)!r} in {s[:60]!r}, "
                    f"PCIC puts it at B2 and this course stops at B1")
    m = COMPOUND_ABOVE_B1.search(s)
    if m:
        errs.append(f"compound tense above B1 {m.group(0)!r} in {s[:60]!r}")
    return errs


def _contrastive(blob, table, kind):
    """An entry fires only when its counterpart is absent from the same scope. Both sides are
    matched as whole words (K1): a substring test would let 'coches' stand in for 'coche' or,
    worse, let an English gloss excuse the form it names."""
    errs = []
    for form, standard in table.items():
        if (re.search(rf"\b{re.escape(form)}\b", blob)
                and not re.search(rf"\b{re.escape(standard)}\b", blob)):
            errs.append(f"{kind} {form!r} without its peninsular counterpart {standard!r}")
    return errs


def check_spanish(path):
    errs = []
    try:
        days = _unwrap(json.load(io.open(path, encoding="utf-8")))
    except Exception:
        return []  # check_batch already reported the parse failure
    if not _is_day_shaped(days):
        return []

    for di, day in enumerate(days):
        tag = f"[{di}] {str(day.get('title', '?'))[:40]}"
        wrong_options = distractors_of(day)

        for s in spanish_strings_of(day):
            # The distractor exemption applies to the VARIETY and ORTHOGRAPHY checks, where a
            # lesson must be able to print a wrong form in order to reject it. It does NOT apply
            # to the LEVEL ceiling: a wrong option is still learner-visible, so an off-syllabus
            # tense sitting in a distractor teaches that tense anyway, and a learner who cannot
            # parse the option cannot use it to answer either. Found in three real items across
            # the A1/A2 batches, all of them correct Spanish used as a register contrast the
            # learner had no way to evaluate.
            level = day.get("level", "")
            if s in wrong_options:
                for msg in _checks_on_string(s, level):
                    if "at " + level in msg:      # level-gated messages only
                        errs.append(f"{tag}: {msg} (in a distractor, which is still visible)")
                continue
            for msg in _checks_on_string(s, level):
                errs.append(f"{tag}: {msg}")

        for s in sentence_strings_of(day):
            if s in wrong_options:
                continue
            mark = _missing_opener(s)
            if mark:
                opener = "¿" if mark == "?" else "¡"
                errs.append(f"{tag}: missing opening {opener} in {s[:60]!r}")

        # Variety, scoped to the ACTIVITY so contrastive teaching stays legal. This is the
        # whole design: the B1 course is REQUIRED to show American forms, because the B1 exam's
        # own texts do, so the rule is about pairing rather than banning.
        for a in day.get("activities", []):
            blob = " ".join(s for s in spanish_strings_of(a) if s not in wrong_options).lower()
            for msg in _contrastive(blob, AMERICAN, "American form"):
                errs.append(f"{tag}/{a.get('type')}: {msg} in the same activity")
            for msg in _contrastive(blob, VOSEO, "voseo form"):
                errs.append(f"{tag}/{a.get('type')}: {msg} in the same activity")
    return errs


def _generic_distractors(node):
    """Wrong MCQ options anywhere in an arbitrary JSON tree, not just under day.activities:
    quizzes/placement/exams put "type": "MCQ" questions directly under "questions" with no
    activity wrapper (K14)."""
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

    rec(node)
    return out


def check_spanish_generic(label, raw):
    """The same checks for non-day-shaped files (vocab packs, quizzes.json, placement.json,
    exams.json, grammar.json...). These shapes have no `activities` to scope the contrastive
    rules by, so AMERICAN and VOSEO are evaluated over the whole file: a deck that teaches
    `carro` somewhere and `coche` somewhere else is genuinely fine, since a deck is one
    contrastive unit."""
    errs = []
    wrong = _generic_distractors(raw)
    strings, sentences = [], []
    for path, s in check_batch.walk_strings(raw):
        if (path.endswith(".hr") or path.endswith(".target") or path.endswith(".answer")
                or ".options[" in path or ".ordered[" in path or ".accepted[" in path):
            strings.append(s)
        if path.endswith(".hr") or path.endswith(".target"):
            sentences.append(s)

    for s in strings:
        if s in wrong:
            continue
        for msg in _checks_on_string(s):
            errs.append(f"{label}: {msg}")
    for s in sentences:
        if s in wrong:
            continue
        mark = _missing_opener(s)
        if mark:
            opener = "¿" if mark == "?" else "¡"
            errs.append(f"{label}: missing opening {opener} in {s[:60]!r}")

    blob = " ".join(s for s in strings if s not in wrong).lower()
    for msg in _contrastive(blob, AMERICAN, "American form"):
        errs.append(f"{label}: {msg} anywhere in the file")
    for msg in _contrastive(blob, VOSEO, "voseo form"):
        errs.append(f"{label}: {msg} anywhere in the file")
    return errs


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    total, bad = 0, 0
    for path in sys.argv[1:]:
        if not os.path.exists(path):
            print(f"MISSING {path}")
            bad += 1
            continue
        raw = json.load(io.open(path, encoding="utf-8"))
        days = _unwrap(raw)
        if _is_day_shaped(days):
            errs = check_batch.check_file(path) + check_spanish(path)
            n = len(days)
            label = f"{n} days"
        else:
            errs = check_spanish_generic(os.path.basename(path), raw)
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
