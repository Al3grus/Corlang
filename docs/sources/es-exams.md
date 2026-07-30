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
   words each. **Six people (1-6) against TEN texts (A-J), three of which are not used.** Like
   tarea 4, the guide leaves the count out and the model paper supplies it: `b1_cl_t1.pdf`,
   fetched and rendered 2026-07-30 (the file is image-only, no text layer), reads "Usted va a leer
   seis textos en los que unas personas hablan de sus gustos cinematograficos y diez resumenes de
   peliculas extraidos de la cartelera. Relacione a las personas (1-6) con los textos de la
   cartelera (A-J). HAY TRES TEXTOS QUE NO DEBE RELACIONAR." Its header also confirms the section
   totals independently: "Esta prueba contiene cinco tareas. Usted debe responder a 30 preguntas.
   Duracion: 70 minutos." Day 238 had taught this as "seis textos breves", i.e. six texts and no
   spares, which is a different task; corrected in Phase 8c.
2. Read one informative text, 3-option MCQ. 400 to 450 words.
3. Match questions/statements to **three** input texts. Anecdotes, travel-guide practical
   information, experiences, news, diaries, biographies, job adverts; 100 to 120 words each.
4. Reconstruct a text: complete paragraphs with short statements (cohesion). **Six gaps, EIGHT
   candidate fragments (A-H), two of which are not used.** The guide itself does not state the
   fragment count (its `Formato de la tarea` cell is empty in the PDF), so this was verified
   directly from the official model paper `b1_cl_t4.pdf`, fetched live 2026-07-30, whose
   instructions read: "Lea el siguiente texto, del que se han extraido seis fragmentos. A
   continuacion lea los ocho fragmentos propuestos (A-H) y decida en que lugar del texto (19-24)
   hay que colocar cada uno de ellos. HAY DOS FRAGMENTOS QUE NO TIENE QUE ELEGIR." Recorded
   because day 241 teaches this ratio to the learner four times and the claim was previously
   unsourced in this digest; the content was correct, only the evidence was missing. Text is 400
   to 450 words.
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

**Provenance status: `pcic` is EARNED FOR GRAMMAR, NOT YET EARNED FOR VOCABULARY.** The split is
deliberate and each half is stated separately rather than averaged into a single comfortable
verdict:

- **Grammar and topic sequence: EARNED (2026-07-29, Phase 1).** The `gramatica` inventory pages
  for A1-A2 and B1-B2 were fetched in full, their two-column tables were machine-split by level
  into `raw/pcic-gramatica-a1-a2-split.txt` and `raw/pcic-gramatica-b1-b2-split.txt`, and the
  250-lesson topic sequence was derived from that split rather than from prior belief. The
  cross-check produced a real, non-obvious result that changed the plan (the imperfecto de
  subjuntivo sitting at B2, §5.7 below), which is what distinguishes a consulted source from a
  cited one. `grammar.json`, `levels.json` and lesson activities carrying grammar may therefore
  cite `pcic`.
- **Vocabulary banding: NOT YET EARNED.** The `nociones especificas` inventory has had only its
  20 section headings fetched, not its per-level word lists, and nothing has been diffed against
  a deck that does not exist yet. **No vocab pack may cite `pcic` until the Phase 8b deck
  cross-check records a coverage figure in this digest**, exactly as `goethe-wortliste` had to be
  earned back for German (registry C16).

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

## 6c. Phase 8b RESULT (2026-07-30): the cross-check ran, and it found a real defect

All four `nociones` inventory pages fetched live 2026-07-30 (HTTP 200, no failures):
`09_nociones_especificas_inventario_{a1-a2,b1-b2}.htm` and
`08_nociones_generales_inventario_{a1-a2,b1-b2}.htm`. Better than expected on granularity: the
pages are published per band, but each noción is a two-column table (`<th>A1</th><th>A2</th>`), so
a TRUE per-level split is available rather than a banded approximation. 7,093 inventory items
parsed. One trap handled: 15 of 91 *específicas* and 14 of 50 *generales* nociones are empty in the
A1 column, carry no A1 requirement, and are excluded from the denominator, or the figure would be a
lie.

**The defect (registry S20).** The deck held 2,838 words and yet NOT ONE of 72 probed core A1/A2
items was a taught headword: no `el día`, `la semana`, `el mes`, `el año`, `la hora`; no numbers
11-19; no `bueno`, `malo`, `grande`, `pequeño`, `fácil`, `difícil`; no `aquí`, `allí`, `cerca`,
`lejos`; no `gustar`; the whole weather, seasons and animal sets absent. 51 of the 72 appear inside
example sentences, so the deck *used* the words while never teaching them: `buenos días` and
`día festivo` but never `el día`; `al gusto` but never `gustar`. Verified twice independently,
including positive controls to prove the matcher worked, because a 72-of-72 miss rate should look
like a broken instrument before it looks like a finding.

**Fixed** by authoring 124 words (102 in two agent waves, 22 directly) into the A1 and A2 packs
specifically. Position matters: `WordsRepository.unlockedNewWords` takes `allWords.take(uptoDay *
10)`, so deck position decides whether a lesson ever introduces a word. All 124 verified present
and inside the introduced window (`once`@131, `día`@142, `aquí`@151, `perro`@868, `dentro de`@1455),
no duplicate SRS ids across 2,962.

**Coverage, before -> after** (exact headword match / partial):

| inventory | before | after |
|---|---|---|
| específicas A1 | 55.6% | **60.4%** / 78.5% |
| específicas A2 | 41.6% | **46.8%** / 67.1% |
| específicas B1 | 27.0% | 27.7% / 46.7% |
| generales A1 | 37.0% | **51.7%** / 72.0% |
| generales A2 | 26.0% | **35.8%** / 60.1% |
| generales B1 | 26.0% | 27.0% / 45.6% |

Every A1 and A2 noción with extractable content is now populated. The four remaining A1 zeros are
collocation-only entries with no headword to author (`Desempleo`, `Características de un
trabajador`, `Juegos`, `Televisión y radio`), plus one parser artifact where the source writes
`quizá(s)` and the tokeniser split it (`quizás` IS in the deck).

**Key verdicts.** `pcic` stays **NOT EARNED for vocabulary** and no pack cites it. The
"record a coverage figure" condition is discharged, but the citation is declined on *provenance*:
a `sources: ["pcic"]` claims the words were selected from that inventory, and they were not -- the
deck is `freq-es` plus thematic need, and 124 words were retrofitted *because* the cross-check
found them missing. Citing the syllabus we failed against, on the strength of having then patched
it, is the `goethe-wortliste` overclaim that cost 431 German citations. `freq-es` stays
**PARTIALLY EARNED** and must not be upgraded: the missing words included ranks 50, 90, 95, 122,
129, 134, 136, 166, 184, 206, 243, 244 and 325, so the deck was demonstrably not assembled by
walking the frequency list. B1 coverage at 27.7% is a *coverage fact*, not a defect: PCIC B1 lists
1,695 *específicas* plus 826 *generales* items and no 3,000-word deck can hold them. Re-measure
offline any time with `pcic_crosscheck.py`.

## 6b. The deck ships at 2,838 against a 2,500 capacity: a recorded S17 deviation

Decided 2026-07-30, with the user, after four separate attempts to find a defensible mechanical
basis for cutting 352 words. **All four failed, and they failed for the same reason**, which is
worth writing down because the next language will face it too.

| Signal tried | Why it failed |
|---|---|
| Corpus frequency (`freq-es`) | Proposed dropping `español` and `España` from a Spanish course, the whole A2 reflexive routine, and `sugerir`/`aconsejar`/`recomendar`, which `grammar.json` names as subjunctive triggers. Earlier it proposed the tens and three weekdays, because film dialogue rarely says "ochenta". |
| Named in the course's own topic files | Topic TITLES are not lesson content, so it flagged `el brazo`, `el dedo`, `buenas tardes` and `perdón` as untaught. |
| Never used in any of the 250 lessons | Flagged `gordo`, `delgado`, `guapo`, the entire body-part set and `comprender`. The deck exists precisely to carry vocabulary the lessons have no room for, so absence from lessons is no evidence at all. |
| All three of the above agreeing | Still flagged `el apellido`, `el armario`, the body parts, and `analizar`/`evaluar`/`juzgar`, the abstract verbs a B1 opinion-writer needs. |

The common blind spot: every one measures **textual presence**, and what matters is **pedagogical
need**. A learner needs `el codo` to describe a symptom whether or not any lesson happens to
contain the word.

A near-synonym analysis over the whole deck found only 22 genuinely redundant clusters, and on
inspection 8 of those were not synonyms at all (`meter`/`poner`, `acordar`/`estar de acuerdo`,
`ampliar`/`expandirse` differ in sense or transitivity). **So trimming to 2,500 would have meant
deliberately deleting about 330 useful words to satisfy a number.**

What shipped instead: the 14 genuinely redundant duplicates were dropped, leaving **2,838 words**.

Why this is defensible rather than a shrug:
- The Kotlin gate `everyDeckCoversTheWholeCourse` requires deck >= lessons x 10 and **passes** at
  2,838 >= 2,500. S17 is a quality concern, not a gate failure.
- Both shipped precedents are over capacity: German 2,913 against 2,850, Italian 2,836 against
  2,450. Spanish at 2,838 against 2,500 is squarely inside existing practice.
- The words past index 2,500 are not unreachable, only unreachable **at the default pace**. The
  standard itself says the 15 and 20 words-a-lesson paces exist and that faster paces "exhaust any
  finite deck sooner; that is expected". At pace 15 the whole deck is reachable by lesson 190.
- The alternative that fully satisfies S17 is widening the pipe to about 286 lessons, which is
  real added value rather than destroyed value, and remains available later without disturbing
  anything below it.

**This is recorded as a known deviation, not hidden.** If a future session wants S17 satisfied
exactly, the fix is to add lessons, not to delete vocabulary.

## 7. Registered keys

| Key | What it asserts |
|---|---|
| `jus-1625-2016` | The legal driver: DELE A2 minimum plus CCSE for nationality by residence. **EARNED** (BOE consolidated text fetched 2026-07-29). |
| `dele-a1` | DELE A1 structure, task inventory, grouped pass rule. **EARNED** (official guide fetched complete). |
| `dele-a2` | DELE A2 structure, task inventory, grouped pass rule. **EARNED** (official guide fetched complete). |
| `dele-b1` | DELE B1 structure, task inventory, grouped pass rule. **EARNED** (official guide fetched complete). |
| `pcic` | Per-level grammar, function and lexical inventories for Spanish. **EARNED for grammar and the topic sequence** (inventory fetched, level-split and diffed in Phase 1); **NOT YET EARNED for vocabulary banding** - no vocab pack may cite it until the Phase 8b deck cross-check. |
| `freq-es` | Frequency ranking for deck order. **PARTIALLY EARNED** - usable for ordering only, never for banding or variety choice. |
