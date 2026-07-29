# Spanish (es): legal driver, exam family, syllabus and frequency reference

**Digest date: 2026-07-29.** Every fact below was fetched live on that date; nothing here comes
from training-data memory (standing rule, `verify-external-resources`). Where a document could
not be fetched, this digest says so instead of papering over it.

Corlang target level: **B1**. See "Target level" below for why that is one level above the
legal minimum, and what that does and does not let the course claim.

---

## 1. The legal driver

**Source:** Orden JUS/1625/2016, de 30 de septiembre, sobre la tramitacion de los procedimientos
de concesion de la nacionalidad espanola por residencia. Consolidated text fetched live
2026-07-29 from `boe.es/buscar/act.php?id=BOE-A-2016-9314`.

- **Art. 10.1** requires applicants for Spanish nationality by residence to pass **two** exams,
  both designed and administered by the Instituto Cervantes: the **DELE** and the **CCSE**.
- **Art. 10.2** sets the language bar: *"el diploma de espanol como lengua extranjera (DELE)
  como minimo de nivel A2"*, for applicants over 18 without judicially modified capacity.
  Nationals of the Spanish-speaking countries (20 Latin American states plus Equatorial Guinea
  and Puerto Rico) are exempt from the DELE but not from the CCSE.
- **Art. 10.4**: the CCSE certificate is valid for 4 years. The DELE does not expire.
- **Art. 10.5 / 10.6**: dispensations for illiteracy and learning difficulties, and for
  applicants schooled through compulsory secondary education in Spain.

Re-checked live the same day for any 2026 reform raising the bar to B1: **none found**. A2
remains the requirement. (Contrast France, which really did move B1 to B2 on 2026-01-01; that
is why the check is made per language and per session rather than assumed.)

**CCSE is out of scope.** It is sat in Spanish but tests the Spanish constitution, institutions,
culture and society, not language ability. Corlang teaches the language and says so
(`docs/language-standard.md` §1, "Civics exams are out of scope"). No Corlang content prepares
the CCSE and none may claim to.

### Target level

The law requires A2. `docs/language-standard.md` §1 reasons that A2 alone is too thin to be a
shippable product and sets **B1 as the floor** for A2-requirement countries, which is exactly
the precedent already shipped for Portuguese (Portugal also requires only A2). Spanish follows
that precedent: **A1 to B1, no B2.**

What the course may therefore claim: it prepares the DELE at A1, A2 and B1, and passing the A2
mock corresponds to the level the nationality procedure actually asks for. What it may **not**
claim: any preparation for the CCSE, or that B1 is legally required.

`ContentValidationTest.levelFloor` already carries an `es` row (A1 45, A2 70, B1 125), the
600-FSI baseline group shared with it and pt. That is the shape Phase 1 must hit.

---

## 2. The exam family: DELE (Instituto Cervantes)

The DELE is issued by the Instituto Cervantes on behalf of Spain's Ministry of Education. It is
the only certificate art. 10.2 names, so it is the single exam family the course targets; there
is no CILS/CELI-style second body to reconcile here, unlike Italian.

Primary sources, all **fetched complete** 2026-07-29 as official Instituto Cervantes PDFs from
`examenes.cervantes.es`, text extracted and kept in `raw/`:

| Key | Document | Raw |
|---|---|---|
| `dele-a1` | *Guia del examen DELE A1* (Instituto Cervantes, ed. 2019, NIPO 503-14-007-X) | [raw/dele-a1-guia-extracted.txt](raw/dele-a1-guia-extracted.txt) |
| `dele-a2` | *Guia del examen DELE A2* (Instituto Cervantes, ed. 2019, NIPO 503-14-007-X) | [raw/dele-a2-guia-extracted.txt](raw/dele-a2-guia-extracted.txt) |
| `dele-b1` | *Guia del examen DELE B1* (Instituto Cervantes, NIPO 503-14-010-3) | [raw/dele-b1-guia-extracted.txt](raw/dele-b1-guia-extracted.txt) |

### 2.1 The pass rule, and why it is new to this codebase

Identical at A1, A2 and B1, quoted from each guide's *Calificacion del examen*:

> La puntuacion maxima que puede alcanzarse en el examen es de 100 puntos. A efectos de
> calificacion, las distintas pruebas se agrupan de la siguiente manera:
> **Grupo 1**: Comprension de lectura (25 puntos), Expresion e interaccion escritas (25 puntos).
> **Grupo 2**: Comprension auditiva (25 puntos), Expresion e interaccion orales (25 puntos).
> Existen dos calificaciones posibles: "Apto" y "No apto". La calificacion global de "Apto" se
> obtiene si se logra una puntuacion igual o superior a la puntuacion minima exigida para cada
> uno de los grupos de pruebas: **Grupo 1: 30,00 puntos. Grupo 2: 30,00 puntos.**

So the rule is **grouped**, and it is neither of the two shapes the app already implements:

- it is **not modular per section** (`examPassed`): a candidate scoring 25/25 reading and 5/25
  writing still passes Grupo 1 with 30/50;
- it is **not global/averaged** (`caplePassed`, `goetheGlobalPassed`): 50/50 in Grupo 1 and
  10/50 in Grupo 2 averages to 60% and still fails;
- it is **not the DELF shape** (`delfPassed`, total >= 50 with a 5/25 per-section floor): DELE
  has no per-section floor at all, and its 30/50 threshold binds per *pair*.

**Consequence for Phase 2:** DELE needs a genuinely new `ExamRules` function, which the Gold
Book sanctions ("only if the exam family's pass rule is genuinely new" - it is). Design:
pair the four sections by `ExamSectionKind` (Grupo 1 = READING + WRITING, Grupo 2 = LISTENING
+ SPEAKING), normalize each to /25, require **>= 30.0 of 50 in each group**. Grouping must be
by kind, never by array position, or a reordered `exams.json` silently changes the verdict.

**Consequence for Phase 6:** `passPercent` semantics. The group threshold is 30/50 = **60%**,
but it is a *group* threshold, so per-section `passPercent` values are the wrong lever and must
be `null` on all four sections; the whole-exam rule function is the only place the 60% lives.
(This is the same trap S3 records for modular-vs-global, in a third flavour.)

There is a real fidelity caveat worth writing into the level's `passRule` string: an exam-taker
can fail a section badly and still pass, so the honest phrasing is "30 of 50 points in each of
the two groups", not "60% overall".

### 2.2 Structure, per level (verbatim from the *Estructura del examen* tables)

The exam has **four pruebas at every level and no grammar section**. (Do not add a `GRAMMAR`
section to the es mocks the way the Italian CILS mocks correctly carry `strutture`: CILS has
one, DELE does not.)

Note that the guides use "Grupo 1 / Grupo 2" in **two different senses**, and conflating them
is an easy authoring error: the *administration* grouping (three pruebas on the official exam
date, the oral possibly on an adjacent day) is not the *grading* grouping quoted in §2.1. Only
the grading grouping is load-bearing for `exams.json`.

**DELE A1** (100 points, 25 per prueba)

| Prueba | Duration | Tareas | Items |
|---|---|---|---|
| 1. Comprension de lectura | 45 min | 4 | 25 (5 / 6 / 6 / 8) |
| 2. Comprension auditiva | 25 min | 4 | 25 (5 / 5 / 8 / 7) |
| 3. Expresion e interaccion escritas | 25 min | 2 | - |
| 4. Expresion e interaccion orales | 10 min (+10 prep) | 3 | - |

**DELE A2** (100 points, 25 per prueba)

| Prueba | Duration | Tareas | Items |
|---|---|---|---|
| 1. Comprension de lectura | 60 min | 4 | 25 (5 / 8 / 6 / 6) |
| 2. Comprension auditiva | 40 min | 4 | 25 (6 / 6 / 6 / 7) |
| 3. Expresion e interaccion escritas | 45 min | 2 | - |
| 4. Expresion e interaccion orales | 12 min (+12 prep) | 3 | - |

**DELE B1** (100 points, 25 per prueba) - the course's finish line

| Prueba | Duration | Tareas | Items |
|---|---|---|---|
| 1. Comprension de lectura | 70 min | 5 | 30 (6 each) |
| 2. Comprension auditiva | 40 min | 5 | 30 (6 each) |
| 3. Expresion e interaccion escritas | 60 min | 2 | - |
| 4. Expresion e interaccion orales | 15 min (+15 prep) | 4 | - |

### 2.3 Task inventory (what the Phase 6 mocks must mirror, and Phase 8e audits against)

**B1 Comprension de lectura** (5 tareas, 6 items each)
1. Match people's statements to texts. Adverts, listings, personal messages, notices; 40 to 60
   words each.
2. Read one informative text, 3-option MCQ. 400 to 450 words.
3. Match questions/statements to **three** input texts. Anecdotes, travel-guide practical
   information, experiences, news, diaries, biographies, job adverts; 100 to 120 words each.
4. Reconstruct a text: complete paragraphs with short statements (cohesion).
5. Cloze-style: select the correct option to complete a text.

**B1 Comprension auditiva** (5 tareas, 6 items each)
1. Six short monologues (adverts, personal messages, notices), 3-option MCQ each; 40 to 60
   words each.
2. One long sustained monologue describing personal experiences, 3-option MCQ; 400 to 450 words.
3. A radio/TV news programme carrying **6 news items**, 3-option MCQ; 350 to 400 words total.
4. Match statements to six short informal monologues/conversations on a shared theme; 50 to 70
   words each.
5. A conversation between two people, 3-option MCQ.

**B1 Expresion e interaccion escritas** (2 tareas)
1. Write a letter or forum/email/blog message in response to a short input text (note, advert,
   letter, message). **100 to 120 words.**
2. Write a composition, diary entry or biography from a short news item, including description
   or narration plus personal opinion. **Two options, choose one. 130 to 150 words.**

**B1 Expresion e interaccion orales** (4 tareas): brief prepared presentation; conversation on
that presentation; describe a photograph then converse about it; simulated-situation dialogue.

**A2 Comprension de lectura** (4 tareas): (1) one personal letter/email, 5 x 3-option MCQ, 250
to 300 words; (2) eight short informative/promotional texts, 8 x 3-option MCQ, 50 to 80 words
each; (3) three texts, match 6 statements to the right one, 100 to 120 words each; (4) one
narrative text (biographical sketch, diary, blog entry, story, news), 6 x 3-option MCQ, 375 to
425 words.

**A2 Comprension auditiva** (4 tareas): (1) six short face-to-face informal conversations and
transactional exchanges, 6 x 3-option MCQ with images, 50 to 80 words each; (2) six short radio
headlines/cuts, 6 x 3-option MCQ, 40 to 60 words each; (3) one conversation, attribute 6
statements to the man, the woman or neither, 225 to 275 words; (4) seven short announcements /
answering-machine messages matched to 7 of 10 statements, 30 to 50 words each.

**A2 Expresion e interaccion escritas** (2 tareas): (1) note, postcard, message, email or short
letter answering a given 35-to-45-word input text, **60 to 70 words**; (2) a second written
task (see raw extract for the full spec).

**A2 Expresion e interaccion orales** (3 tareas): (1) prepared 2-to-3-minute monologue on a
topic chosen from two; (2) 2-to-3-minute description of a photograph of an everyday scene;
(3) simulated-situation conversation with the examiner.

**A1 Comprension de lectura** (4 tareas): (1) one short epistolary text (email/postcard),
5 x 3-option MCQ, last option carrying images, 150 to 175 words; (2) nine very short texts
(notes, signs, catalogues, instructions), match six to statements, 20 to 30 words each;
(3) nine short adverts, match six to people's statements, 20 to 30 words each; (4) one text,
8 x 3-option MCQ (adverts, signs, labels, tickets).

**A1 Comprension auditiva** (4 tareas): (1) five very short dialogues, 5 x 3-option image MCQ,
30 to 40 words each; (2) five very short announcements matched to 5 of 8 images, 15 to 30 words
each; (3) eight sentence-openings matched to 8 of 11 continuations from one monologue, 15 to 25
words each; (4) one conversation, complete 7 statements from 8 options, 160 to 185 words.

**A1 Expresion e interaccion escritas** (2 tareas, both written interaction).
**A1 Expresion e interaccion orales** (3 tareas): personal presentation; prepared exposition of
a topic; conversation with the interviewer.

### 2.4 Norma linguistica: the variety policy, quoted

This is the single most consequential finding for the Spanish course's variety discipline, and
it is **not uniform across levels**:

- **A1 and A2 guides**, identically: *"en los textos de entrada -tanto orales como escritos-
  utilizados en el examen DELE A1/A2 se emplean textos de diversas fuentes y de variedades del
  **espanol peninsular contemporaneo**"*.
- **B1 guide**: *"se emplean textos de diversas fuentes y de **diferentes variedades del
  espanol**"*.
- **All three**, on candidate production: *"sera considerada valida toda norma linguistica
  hispanica seguida coherentemente y respaldada por grupos amplios de hablantes cultos"*.

What that means for authoring, and it is a genuine three-part rule, not a preference:

1. **The course teaches Castilian/peninsular Spanish** as its production standard. This matches
   the A1/A2 input-text policy, matches `docs/new-languages-plan.md`'s standing rule ("Spanish
   -> Castilian, with contrastive notes against Latin American forms, mirroring how Portuguese
   guards against Brazilian"), and matches the pt-PT precedent the app already enforces.
2. **B1 receptive material legitimately contains Latin American varieties**, by the exam's own
   design. B1 lessons and the B1 mock's reading/listening should therefore *expose* American
   forms, labelled contrastively, rather than pretending they do not exist. Omitting them would
   be an exam-fidelity defect at exactly the level that matters most.
3. **The candidate is never penalised for a coherently-followed American norm.** So the course
   must not teach that American forms are *wrong*, only that it teaches the peninsular one and
   asks the learner to stay internally consistent. This is the same anti-false-correction
   discipline `varietyRules` already encodes for hr and pt, and it must be stated that way in
   the tutor prompt.

Practical consequence for `check_es.py` (Phase 3): the checker's job is **not** "ban American
Spanish". It is the activity-scoped contrastive rule used by `check_de.py`/`check_fr.py`/
`check_pt.py`: an American form appearing as a taught production target with no peninsular
counterpart in the same activity is a defect; the same form as a labelled contrast, or as a
wrong MCQ distractor, is correct content. Candidate drift classes to cover are listed in §5.

---

## 3. The official syllabus: Plan curricular del Instituto Cervantes (PCIC)

**Key `pcic`.** *Plan curricular del Instituto Cervantes. Niveles de referencia para el espanol*
(NRE), the Instituto Cervantes' development of the CEFR levels for Spanish. Fetched live
2026-07-29 from `cvc.cervantes.es/ensenanza/biblioteca_ele/plan_curricular/`.

This is the right anchor and not merely a plausible one: **each DELE guide names it explicitly**
as the source of the exam's content repertoire ("El repertorio de contenidos linguisticos que
pueden ser incluidos en el examen DELE ... se recoge en el documento *Niveles de referencia para
el espanol* (NRE), desarrollado por el Instituto Cervantes"). The syllabus and the exam are by
the same body, so the Spanish course has a tighter curriculum-to-exam link than any other
Corlang language.

**Structure fetched and confirmed.** Thirteen inventories:

1. Objetivos generales
2. Gramatica
3. Pronunciacion y prosodia
4. Ortografia
5. Funciones
6. Tacticas y estrategias pragmaticas
7. Generos discursivos y productos textuales
8. Nociones generales
9. Nociones especificas
10. Referentes culturales
11. Saberes y comportamientos socioculturales
12. Habilidades y actitudes interculturales
13. Procedimientos de aprendizaje

Each inventory is published in three level-banded pages (A1-A2, B1-B2, C1-C2), each laid out as
a two-column table with the lower level on the left and the higher on the right, so **the
per-level split is machine-readable page by page**. Pages spot-checked live and confirmed
fetchable:

- `niveles/02_gramatica_inventario_a1-a2.htm` and `.../b1-b2.htm` - 15 top-level grammar
  sections at both bands (El sustantivo, El adjetivo, El articulo, Los demostrativos, Los
  posesivos, Los cuantificadores, El pronombre, El adverbio y las locuciones adverbiales, El
  verbo, El sintagma nominal, El sintagma adjetival, El sintagma verbal, La oracion simple,
  Oraciones compuestas por coordinacion, Oraciones compuestas por subordinacion).
- `niveles/09_nociones_especificas_inventario_a1-a2.htm` - the 20 thematic vocabulary sections
  (Individuo: dimension fisica; Individuo: dimension perceptiva y animica; Identidad personal;
  Relaciones personales; Alimentacion; Educacion; Trabajo; Ocio; Informacion y medios de
  comunicacion; Vivienda; Servicios; Compras, tiendas y establecimientos; Salud e higiene;
  Viajes, alojamiento y transporte; Economia e industria; Ciencia y tecnologia; Gobierno,
  politica y sociedad; Actividades artisticas; Religion y filosofia; Geografia y naturaleza).

Those 20 thematic sections are the **level-banded lexical inventory** for Spanish, the
functional equivalent of the Goethe *Wortliste* for German. They are what the Phase 8b deck
cross-check diffs against, and they are what the deck's A1/A2/B1 banding must be justified by.

**Provenance status: `pcic` is NOT YET EARNED.** Its table of contents and band structure have
been fetched; the per-level inventories have not yet been pulled page by page and diffed against
anything, because no content exists yet. `pcic` may be cited by content only after the Phase 8b
cross-check records a coverage figure here, exactly as `goethe-wortliste` had to be earned back
for German (registry C16). Until then no vocab pack, lesson or grammar topic carries the key.

---

## 4. Frequency reference

**Key `freq-es`.** OpenSubtitles-2018 Spanish frequency list (Hermit Dave, *FrequencyWords*),
50,000 ranked word forms, fetched complete 2026-07-29 from
`raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/es/es_50k.txt`. Kept as
[raw/es-freq-50k.txt](raw/es-freq-50k.txt).

**Two honest caveats, both of which shape how the key may be used:**

1. **Register.** It is a subtitle corpus, so it is spoken-dialogue-weighted. That is a good
   match for a course whose exam has two oral/aural pruebas out of four, and a poor match for
   the written-register vocabulary B1 reading tasks draw on. It ranks; it does not band.
2. **Variety.** Spanish subtitle corpora mix peninsular and American sources and the list does
   not distinguish them. It therefore cannot be used to decide *which* of two variant forms the
   course teaches. That decision comes from §2.4 and the PCIC, never from this list.

**What was attempted and failed, recorded rather than hidden:**

- **RAE CREA frequency lists** (`corpus.rae.es/frec/10000_formas.TXT`, `CREA_total.zip`):
  HTTP **403** on 2026-07-29. The obvious "official" Spanish frequency authority is not
  fetchable.
- **SUBTLEX-ESP** (Cuetos et al., Ghent): both known distribution URLs **404** on 2026-07-29.
- *A Frequency Dictionary of Spanish* (Davies, Routledge) is a commercial book, not fetchable,
  and is deliberately not registered as a key - the same overclaim that got `freq-fr` and
  `francais-fondamental` retired for French.

**Provenance status: `freq-es` is PARTIALLY EARNED** - the document is genuinely in hand and
genuinely usable for ranking, but the caveats above mean it must never be cited as the authority
for level banding or for a variety choice. Deck packs may cite it for *ordering* only, and only
after the Phase 8b cross-check confirms the deck was actually ordered against it.

---

## 5. Variety drift classes for `check_es.py` (Phase 3 input)

Derived from §2.4 plus the error registry's V-rows (V2/V3/V12/V13 are the direct precedents:
each is "neighbour variety taught as standard without a contrastive counterpart in the same
activity"). To be negative-tested with planted defects before the checker is trusted, per K5,
and written with the K8/K13/K17 assembled-shape unwrap and the K14 generic-shape fallback in
from the first line rather than discovered later.

Candidate classes, to be confirmed against the PCIC before encoding:

1. **Pronoun paradigm.** `ustedes` as the *only* plural-you taught, with no `vosotros`;
   `vosotros` verb forms missing from conjugation tables. Peninsular teaches both, American
   only `ustedes`. This is the highest-value check: it is systematic, it is unambiguous, and it
   is exactly the "gap in a paradigm" defect no generic checker catches.
2. **Voseo** (`vos tenes`, `vos sos`, `vos queres`) presented as a production target.
3. **Perfect vs preterite usage.** Peninsular uses `he comido hoy` for hodiernal past where
   much of America uses `comi hoy`. Genuinely hard to regex safely (both are correct sentences
   in isolation), so this is a *reviewer* item, not a checker item, unless a narrow
   today-adverb-plus-preterite pattern proves to be false-positive-free on real content.
4. **Lexical pairs** where the American form is taught without its peninsular counterpart in the
   same activity: `carro`/`coche`, `manejar`/`conducir`, `computadora`/`ordenador`,
   `celular`/`movil`, `papa`/`patata`, `jugo`/`zumo`, `boleto`/`billete`, `departamento`/`piso`,
   `plata`/`dinero`, `saco`/`chaqueta`, `frijoles`/`judias`, `durazno`/`melocoton`,
   `elevador`/`ascensor`, `refrigerador`/`nevera`, `lentes`/`gafas`, `chico`(adj. small)/
   `pequeno`, `tomar`(to take transport)/`coger`. The list must be built as whole-word matches
   on BOTH sides (K1) and scoped to produced-Spanish keys only (K2/K16).
   **`coger` needs an explicit note**: it is the ordinary peninsular verb and vulgar in much of
   America. A course teaching Castilian teaches `coger`; the checker must not flag it, and
   lesson text must not moralise about it.
5. **Orthography that changes meaning.** Missing written accents where the unaccented form is a
   different word (`si`/`si`, `tu`/`tu`, `el`/`el`, `mas`/`mas`, `se`/`se`, `de`/`de`,
   `esta`/`esta`, `como`/`como`, `que`/`que`, `porque`/`por que`/`porque`/`por que`), missing
   `n` (`ano` for `ano` is the canonical example and is crude, so it must fire), and missing
   opening `¿` / `¡`. The K3 lesson applies directly: only list forms that are **not words**
   without the accent, and be casing-aware for name collisions.
   This class is expected to be the Spanish analogue of Italian's missing-`e` bug (registry
   item 9), and it is the reason a **written-accent unit belongs early in the course**.
6. **Seseo/ceceo spellings** (`corason`, `sapato`) - phonetic misspellings, always defects.
7. **Off-syllabus grammar**, the Spanish analogue of V8's passato remoto, and after the Phase 1
   PCIC extraction this is the **highest-value check in the whole file**, not an afterthought.
   The PCIC level split (pulled page by page 2026-07-29 into
   `es-research/02_gramatica_inventario_*-split.txt`) puts the **imperfecto de subjuntivo at
   B2**, not B1: section 9.2 lists only 9.2.1 Presente in the B1 column, with 9.2.2 Preterito
   imperfecto, 9.2.3 Preterito perfecto and 9.2.4 Pluscuamperfecto all in B2. A course that
   ends at B1 therefore must not teach `tuviera`/`tuviese`, and by direct consequence must not
   teach the **counterfactual conditional `si` + imperfecto de subjuntivo + condicional**
   (`si tuviera dinero, viajaria`) at all. B1 gets real conditions with `si` + present
   indicative and nothing more.

   This will be violated. Nearly every commercial Spanish syllabus puts the counterfactual at
   B1, so it is what a parallel authoring agent reaches for unprompted, exactly the way German
   agents reached for civics text. A regex over produced-Spanish keys for the `-ra`/`-se`
   imperfect-subjunctive endings of frequent verbs is mechanical and cheap; the K3 lesson
   applies (`fuera` also means "outside", `viera`/`viere` collisions, `-ra` endings that are
   ordinary nouns), so the list must be built from forms that are unambiguous, and the checker
   must be negative-tested against both a planted `si tuviera` defect and the innocent
   `Espera fuera` before it is trusted.

   Also off-syllabus below B1: **futuro perfecto** and **condicional compuesto** (9.1.9,
   9.1.10, both B2), and the **preterito anterior** (`hubo llegado`), which is absent from the
   B1 inventory entirely. `pluscuamperfecto de indicativo` (9.1.8) and `condicional simple`
   (9.1.5) ARE in the B1 column and are taught.

---

## 6. What Phase 8b must do to earn the keys

- Pull the PCIC `gramatica` and `nociones especificas` inventories for A1-A2 and B1-B2 page by
  page, diff **both ways** against the authored topic sequence and deck: what the official
  inventory requires that the course lacks, and what the course teaches that is off-level.
  Record the coverage figure here. Only then may content cite `pcic`.
- Confirm the deck was ordered against `freq-es` before any pack cites it, and re-state the
  register/variety caveats wherever it is cited.
- Compare each mock section by section against §2.2/§2.3 above (task types, item counts, timing,
  pass rule). Structure only, never content.

## 7. Registered keys

| Key | What it asserts |
|---|---|
| `jus-1625-2016` | The legal driver: DELE A2 minimum plus CCSE for nationality by residence. **EARNED** (BOE consolidated text fetched 2026-07-29). |
| `dele-a1` | DELE A1 structure, task inventory, grouped pass rule. **EARNED** (official guide fetched complete). |
| `dele-a2` | DELE A2 structure, task inventory, grouped pass rule. **EARNED** (official guide fetched complete). |
| `dele-b1` | DELE B1 structure, task inventory, grouped pass rule. **EARNED** (official guide fetched complete). |
| `pcic` | Per-level grammar, function and lexical inventories for Spanish. **NOT YET EARNED** - awaiting the Phase 8b cross-check. |
| `freq-es` | Frequency ranking for deck order. **PARTIALLY EARNED** - usable for ordering only, never for banding or variety choice. |
