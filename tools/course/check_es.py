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
    r"buenisimo|buenisima|altisimo|altisima|"
    # Reference-work vocabulary, added because `Diccionario panhispanico de dudas` shipped
    # unaccented in the es resources.json. Unaccented these are words in no language at all.
    r"panhispanico|panhispanica|panhispanicos|linguistico|linguistica|gramatico)\b",
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
# CASE-INSENSITIVE, because Spanish routinely starts a sentence with one of these and the
# title-case form was invisible to the lowercase-only original: "Manana vamos al cine" passed
# every check in this file. Verified safe by measurement rather than assumption -- a sweep of the
# title-case forms over the whole es build returned ZERO occurrences, so widening flags nothing
# retroactively, and none of the words below collides with a Spanish proper noun in title case
# (Banos and Espanola exist as place names but still carry the tilde).
MISSING_ENYE = re.compile(
    r"\b(espanol|espanola|espanoles|manana|senor|senora|senores|senorita|"
    r"pequeno|pequena|pequenos|pequenas|bano|banos|sueno|suenos|ensenar|ensena|"
    r"companero|companera|otono|ninos|ninas|"
    r"cumpleanos|ano|anos)\b",
    re.IGNORECASE)

# Kept LOWERCASE-ONLY: title-case "Montana" is the US state, a real place name that correctly
# carries no tilde, so this one word cannot join the case-insensitive set above.
MISSING_ENYE_LOWER = re.compile(r"\b(montana|montanas)\b")

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
    r"\b(habr[éáíé]\w*|habría\w*|habrías|hubo|hubieron|"
    # The PERFECT SUBJUNCTIVE (haya hecho) is PCIC 9.2.3, also B2. It was missing entirely,
    # which mattered because three B1 authoring briefs had already been told it was banned.
    r"haya|hayas|hayamos|hayáis|hayan)\s+\w+[aií]d[oa]s?\b",
    re.IGNORECASE)

# REGULAR imperfect subjunctives, which the strong-stem list above cannot see. `esperara`,
# `hablara`, `comiera`, `viviera` are the COMMON case, so missing them left the check covering
# only the irregular minority.
#
# A bare `-ara` sweep is unusable: it swallows cara, clara, rara, avara, tiara, máscara, cámara,
# para. So two safe routes are used instead.
#   (a) The plural and first-person-plural endings are unambiguous on their own. No Spanish noun
#       ends in -áramos, -iéramos, -ásemos, -iésemos, -aran, -ieran, -asen or -iesen.
#   (b) The singular endings are matched only after `si` or `que`, the two words that actually
#       introduce an imperfect subjunctive, with a stem of at least two characters (which alone
#       excludes para, cara and fiera) plus a short exemption list for the survivors.
# Context-free, and ONLY the accented first-person plurals. These are genuinely unambiguous:
# the present subjunctive 1pl is -emos/-amos with no accent (hablemos, comamos), so nothing
# collides with -áramos or -ásemos.
IMPERFECT_SUBJ_REGULAR_PLURAL = re.compile(
    r"\b\w{2,}(?:áramos|iéramos|ásemos|iésemos)\b", re.IGNORECASE)
# The 3pl endings -aran and -ieran are NOT safe context-free, and this was measured, not
# assumed: 8 of 12 probe sentences false-fired, because the present tense of any verb whose STEM
# ends in -ar or -ier contains them. `aclaran`, `declaran`, `preparan`, `separan`, `comparan`,
# `reparan` and `quieran` are all perfectly correct forms. A batch-8 authoring agent hit
# `quieran` (the present subjunctive of querer, which this course teaches at B1) and REPHRASED
# CORRECT SPANISH to get past the gate, which is the worst outcome a checker can produce.
# So they are contextual, and the collisions are exempted by name below.
# Split by TRIGGER, because the two triggers have opposite ambiguity profiles, and this is the
# principle the two earlier patches missed:
#
#   After `si`, Spanish never puts ANY subjunctive. The course teaches exactly that at lesson 129.
#   So after `si`, a subjunctive-shaped form IS an imperfect subjunctive, unambiguously, and the
#   full ending set can be matched including the 2sg.
#
#   After `que` or `ojalá`, the PRESENT subjunctive is legal and, at B1, ubiquitous. The 2sg
#   endings -aras/-ieras/-ases/-ieses therefore collide fatally: `que quieras`, `que prefieras`,
#   `que sugieras`, `que repases` and `que adquieras` are all ordinary present subjunctives, and
#   `que aclaras`/`que separas` are ordinary present indicatives. So the 2sg endings are excluded
#   after those triggers, which removes the entire collision class rather than patching it one
#   word at a time. That is what the first two attempts got wrong: exempting `quieran` and not
#   `quieras` made a second authoring agent rewrite correct Spanish (`Haz lo que quieras`).
IMPERFECT_SUBJ_AFTER_SI = re.compile(
    r"\bsi\s+(?:\w+\s+)?"
    r"(\w{2,}(?:aras|ieras|ases|ieses|aran|ieran|asen|iesen|ara|iera|ase|iese))\b",
    re.IGNORECASE)
IMPERFECT_SUBJ_AFTER_QUE = re.compile(
    r"\b(?:que|ojalá|ojala)\s+(?:\w+\s+)?"
    r"(\w{2,}(?:aran|ieran|asen|iesen|ara|iera|ase|iese))\b",
    re.IGNORECASE)
NOT_SUBJUNCTIVE = {
    # Singulars and plurals both, because the 2sg endings (-aras, -ieras) collide with the
    # PLURAL of every one of these nouns and adjectives.
    "clara", "claras", "rara", "raras", "avara", "avaras", "tiara", "tiaras",
    "mascara", "máscara", "mascaras", "máscaras", "camara", "cámara", "camaras", "cámaras",
    "sahara", "guitarra", "guitarras", "pizarra", "pizarras",
    "envase", "envases", "clase", "clases", "frase", "frases", "base", "bases", "fase", "fases",
    # Present-tense forms of verbs whose STEM ends in -ar, which therefore contain "-aran".
    "aclaran", "declaran", "preparan", "separan", "comparan", "reparan", "disparan",
    "amparan", "encaran", "deparan", "maran",
    # Present forms containing "-ieran": the subjunctive of querer and its compounds, which this
    # course teaches at B1 and must never be flagged.
    "quieran", "quieras", "adquieran", "adquieras", "requieran", "requieras",
    "inquieran", "inquieras", "prefieran", "prefieras", "sugieran", "sugieras",
    "hieran", "hieras", "difieran", "difieras", "ingieran", "ingieras",
    "repasen", "repases", "atrasen", "engrasen", "rebasen", "traspasen", "sobrepasen",
}

# The AGENTIVE PASSIVE with ser + participle + por is a B2 register move; B1 teaches impersonal
# and passive `se`. The `por` is what distinguishes it from an ordinary estar + participle
# result state, which IS B1 (la puerta está cerrada).
# NARROWED after two false positives on real content, both of them correct Spanish:
#   'El barrio es conocido por su diversidad'  -> por introduces a CAUSE, not an agent, and
#      `conocido` has lexicalised as an adjective. `ser conocido por` is a normal B1 collocation.
#   'La terraza es muy soleada por la tarde'    -> por introduces a TIME.
# The distinction between an agentive passive and ser + adjectival participle + causal por is
# semantic, so the check now demands three things at once: a participle that is not one of the
# lexicalised adjectival ones, `por` followed by a DEFINITE or INDEFINITE article (an agent is
# normally a noun phrase, while causal por takes a possessive or a bare noun), and that article
# not beginning a time expression. Narrow and honest beats broad and wrong: this check has found
# no real defect yet, and a check that rewrites correct content is worse than one that misses.
ADJECTIVAL_PARTICIPLES = {
    'conocido', 'conocida', 'conocidos', 'conocidas', 'soleada', 'soleado',
    'querido', 'querida', 'apreciado', 'apreciada', 'reconocido', 'reconocida',
    'valorado', 'valorada', 'buscado', 'buscada', 'preocupado', 'preocupada',
    'cansado', 'cansada', 'interesado', 'interesada', 'aburrido', 'aburrida',
    'sorprendido', 'sorprendida', 'preparado', 'preparada', 'cerrado', 'cerrada',
    'abierto', 'abierta', 'famoso', 'famosa',
}
TIME_AFTER_POR = {'tarde', 'mañana', 'manana', 'noche', 'semana', 'día', 'dia', 'hora',
                  'momento', 'rato', 'verano', 'invierno', 'primavera', 'otoño', 'otono'}
AGENTIVE_PASSIVE = re.compile(
    r"\b(?:fue|fueron|es|son|será|serán|era|eran)\s+(\w+[ai]d[oa]s?)"
    r"\s+por\s+(el|la|los|las|un|una)\s+(\w+)\b",
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


# The message fragments that identify a LEVEL-CEILING finding, as opposed to an orthography or
# variety one. Kept as a named constant so the prompt/title scan below cannot silently widen if
# a new check is added with a different message shape.
CEILING_MARKERS = ("imperfect subjunctive", "compound tense above", "future tense",
                   "conditional", "agentive passive")


def ceiling_strings_of(node):
    """Learner-visible text that is NOT target-language-only: question prompts and activity
    titles. These mix English instructions with embedded Spanish, so they are checked for the
    level ceiling alone (see the call site)."""
    for path, s in check_batch.walk_strings(node):
        if path.endswith(".prompt") or path.endswith(".title"):
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
    m = MISSING_ENYE.search(s) or MISSING_ENYE_LOWER.search(s)
    if m:
        errs.append(f"missing ñ in {m.group(0)!r} in {s[:60]!r}")
    m = IMPERFECT_SUBJ.search(s)
    if m:
        errs.append(f"imperfect subjunctive {m.group(0)!r} in {s[:60]!r}, "
                    f"PCIC puts it at B2 and this course stops at B1")
    m = COMPOUND_ABOVE_B1.search(s)
    if m:
        errs.append(f"compound tense above B1 {m.group(0)!r} in {s[:60]!r}")
    m = IMPERFECT_SUBJ_REGULAR_PLURAL.search(s)
    if m:
        errs.append(f"imperfect subjunctive {m.group(0)!r} in {s[:60]!r}, "
                    f"PCIC puts it at B2 and this course stops at B1")
    for rx, where in ((IMPERFECT_SUBJ_AFTER_SI, "si"), (IMPERFECT_SUBJ_AFTER_QUE, "que")):
        m = rx.search(s)
        if m and m.group(1).lower() not in NOT_SUBJUNCTIVE:
            errs.append(f"imperfect subjunctive {m.group(1)!r} after {where} in {s[:60]!r}, "
                        f"PCIC puts it at B2 and this course stops at B1")
            break
    m = AGENTIVE_PASSIVE.search(s)
    if (m and m.group(1).lower() not in ADJECTIVAL_PARTICIPLES
            and m.group(3).lower() not in TIME_AFTER_POR):
        errs.append(f"agentive passive {m.group(0)!r} in {s[:60]!r}, "
                    f"B1 teaches impersonal and passive se instead")
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

        # THE LEVEL CEILING, over PROMPTS and ACTIVITY TITLES. These fields were outside every
        # scanned set (spanish_strings_of takes .hr/.target/.answer and the option arrays), and a
        # Phase 8c audit found FOUR real B1-ceiling violations living in exactly this blind spot:
        # an imperfect subjunctive inside an MCQ prompt, one inside a FILL prompt, and a dialogue
        # TITLED "Si pudiera elegir". The pattern fired correctly on all of them the moment the
        # string was handed to it -- nothing was ever wrong except which fields got read.
        # Only the CEILING checks run here, never the orthography or variety ones: a prompt is
        # mostly English instructional prose, and `cafe`/`menu` are English words that would
        # false-fire the accent list (the K18 shape). The ceiling patterns are safe on English
        # because they require either a Spanish accent or a Spanish trigger word.
        for s in ceiling_strings_of(day):
            for msg in _checks_on_string(s, day.get("level", "")):
                if any(k in msg for k in CEILING_MARKERS):
                    errs.append(f"{tag}: {msg} (in a prompt or title, which is learner-visible)")

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


def _accent_only(s):
    """Just the orthography checks: missing written accent and missing enye. Used on `.name` and
    `.title` fields in the reference files, where the surrounding prose is English and the wider
    battery would be wrong to apply. Deliberately NOT including MISSING_ACCENT_CION here: `-cion`
    is a real English ending (suspicion), and these fields mix English and Spanish freely, which
    is the K18 false-positive shape exactly. The closed-list patterns are safe because every
    alternative they match is a form that is not a word in either language."""
    errs = []
    m = (MISSING_ACCENT.search(s) or MISSING_ACCENT_LOWER.search(s)
         or MISSING_ACCENT_SION.search(s))
    if m:
        errs.append(f"missing written accent on {m.group(0)!r} in {s[:60]!r}")
    m = MISSING_ENYE.search(s) or MISSING_ENYE_LOWER.search(s)
    if m:
        errs.append(f"missing enye in {m.group(0)!r} in {s[:60]!r}")
    return errs


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
        # `.name` and `.title` carry SPANISH PROPER NAMES in the reference files -- the resource
        # titles in resources.json, the PCIC level names in levels.json ("Elementary (Plataforma)").
        # They were outside the scanned set, which is exactly how `Diccionario de la lengua
        # espanola` and `panhispanico` shipped unaccented into the es build: the accent check ran,
        # but never over the field holding the word. Only the ORTHOGRAPHY checks are wanted here,
        # so these go through _accent_only rather than the full string battery: a resource title
        # is a real-world name and may legitimately contain an American form (Radio Ambulante)
        # without owing the file a peninsular counterpart.
        if path.endswith(".name") or path.endswith(".title"):
            for msg in _accent_only(s):
                errs.append(f"{label}: {msg}")
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
    # No arguments means the whole shipped course. Reporting "0 days total, 0 problems"
    # and exiting 0 because nobody passed a file is a green light that checked nothing.
    paths = sys.argv[1:] or check_batch.course_files("es")
    if not paths:
        print("nothing to check: pass batch files, or restore app/src/main/assets/content/es/")
        sys.exit(2)
    for path in paths:
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
