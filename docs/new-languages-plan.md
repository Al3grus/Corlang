# Adding German, Italian and Spanish (target B1)

Working plan for the next three courses, in order: **German → Italian → Spanish**, each taught
to **B1**. Every phase is a stopping point: it ends with work committed and the build green, so
a session that runs out of budget mid-plan loses nothing. `docs/language-standard.md` is the
contract; this file is only the schedule.

> **ALL THREE ARE NOW BUILT AND SHIPPED. This file is history, not a queue.**
> German, Italian and Spanish are all live in `assets/content/`, and Spanish completed Phases 0
> through 9 on 2026-07-30: 250 lessons (A1 45 / A2 75 / B1 130), a 2,962-word deck across 20
> packs, 3 quizzes, 3 DELE mocks, a 10-band placement test, `check_es.py` + `verify_deck_es.py` +
> `pcic_crosscheck.py` written and negative-tested, and 124/124 Kotlin tests green with six
> languages registered. Its full record is `docs/sources/es-exams.md`; its defect harvest is in
> `docs/error-registry.md` (V17-V20, S20, K18-K25).
> **The one gate no machine can close is still open for every course: native-speaker review
> (Track D).** No shipped Corlang course has been read by a native speaker, and the
> machine-authored expansion lessons least of all. That is a `[HUMAN]` item, not a backlog item.

---

## Why these three, and why B1

Corlang's niche is exam preparation with legal stakes, not casual learning. All three chosen
languages have a citizenship or residence exam at their target level, which is the same reason
Croatian, Portuguese and French are in the app:

| Language | Legal driver | Exam family at target level |
|---|---|---|
| German | Citizenship and settlement require B1 | Goethe-Zertifikat / telc / ÖSD |
| Italian | Citizenship requires B1 (since 2018) | CILS / CELI (B1 cittadinanza) |
| Spanish | Nationality requires A2 (+ CCSE civics) | DELE A2 and B1 |

B1 is the finish line for all three because that is where the legal requirement sits. B2 can be
added later as a fourth phase per language without disturbing anything below it.

---

## Shape of each course

**Superseded for German and Italian, and never applied to Spanish.** This section originally
specified a flat 250 lessons shaped A0 15 / A1 55 / A2 85 / B1 95. That shape was backwards: the
standard moved to **weighted per-level floors** (A1 1.0 : A2 1.6 : B1 2.8, scaled by the square
root of the FSI hour ratio) mid-way through the German build, and German was rebalanced from 250
to 285 as a result. The live floors are the `levelFloor` table in `ContentValidationTest`, which
is the only authority.

Spanish sits in the 600-FSI baseline group with Italian and Portuguese, so its floors are:

| Level | Floor | Cumulative |
|---|---|---|
| A0 onramp | none (optional) | — |
| A1 | 45 | 45 |
| A2 | 70 | 115 |
| B1 | 125 | 240 |

Per the every-level checkpoint rule: **3 quizzes** (A1, A2, B1) plus one more if an A0 onramp
ships, **3 readiness milestones** and **3 mock exams** (A1, A2, B1). A0 gets a quiz only, since
no official A0 exam exists. Deck = lessons × 10 (the SRS pace), which is a **ceiling as well as
a floor** — every word past `lessons × 10` is unreachable and dead (registry S17).

---

## The hard constraint that shapes the schedule

`ContentValidationTest` **discovers languages from `assets/content/`**, deliberately, so a new
language enters every gate the moment its folder exists. A half-built `content/de/` therefore
turns the build red (no 250 lessons, no 2500 words, no checkpoints).

**So: author into `scratchpad/<code>-build/`, validate with the Python mirrors of the gates
(`check_batch.py`, `check_placement.py`), and move the folder into `assets/content/` only in the
final phase of that language,** when it can pass everything at once. The code wiring lands in
the same final phase. Nothing incomplete is ever committed into the assets tree.

The cost of that choice is that in-flight work lives outside git, in a session-scoped temp
directory. Scratchpads are not deleted when a session ends, so nothing is lost, but a new
session will not find the work unless it is told where to look.

**Current build: Spanish (es), started 2026-07-29.**

```
C:\Users\al3gr\AppData\Local\Temp\claude\C--Users-al3gr-Desktop-Github-Corlang\
  ff2e8d95-47f4-45d1-b3cc-50b4059d1c9c\scratchpad\
```

with the course under `es-build/` and the Phase 0 research downloads (the three DELE guide PDFs
and their text extracts, the frequency list) under `es-research/`. The four documents that
matter were also copied into the repo at `docs/sources/raw/`, so Phase 0 survives even if the
scratchpad does not; the digest is `docs/sources/es-exams.md`.

**Previous German build directory** (kept for reference; the course is shipped and lives in
`assets/content/de/` now):

```
C:\Users\al3gr\AppData\Local\Temp\claude\C--Users-al3gr-Desktop-Github-Corlang\
  4eb9e133-fe10-44a6-adf4-e0fbfa9f0267\scratchpad\
```

with the assembled course under `de-build/` and the raw authored batches (`de_vocab_*.json`,
`de_a0.json`, `de_a1*.json`) beside it. The shared authoring tools now live IN THE REPO at `tools/course/` (they were session-scoped
until 2026-07-20, which nearly lost them). The canonical workflow is `docs/course-gold-book.md`,
invocable as the `/new-language` skill. Update this path when a later session starts a new build.

---

## Phases (repeat per language; L = de, it, es)

### L.0 — Research and provenance  *(blocking; no authoring before this)*
Standard §0. Verify **live**, since exam formats and citizenship law change:
- the official state/curriculum framework for the language as a foreign language,
- the official exam at A1, A2 and B1: sections, timing, pass rule, who administers it and why
  learners sit it,
- a frequency/core-vocabulary reference for the deck.

Deliverable: source digests in `docs/sources/`, keys registered in `docs/sources/README.md`,
and the per-level exam facts recorded for L.1 and L.6. **Commit.**

### L.1 — Skeleton, identity and code wiring
`meta.json`, `levels.json` (milestones, can-dos, CEFR-grid skills, `exam` object on A1/A2/B1),
`cheatsheet.json`, `feynman.json`, `resources.json`. Code touchpoints from §3 prepared but the
language still **absent from `availableLanguages`**: `SpeechLocales`, `ExamRules` if the pass
rule is new, `TalkScreen` variety rules, seed greeting/opener, starters, composer hint, and
onboarding gender-forms copy if the language is gendered. **Commit** (code compiles; no content
folder yet, so gates stay green).

### L.2 — Vocabulary deck (≥2500 words)
Authored in SRS introduction order, packs grouped by level and theme, every word with `pos`,
an example sentence and gloss. Dedup against itself; ids NFC and unique. Roughly 8 to 10
authoring agents. Validate word count and shape offline. **Commit** to scratchpad only.

### L.3 — Plan: A0 + A1 (70 lessons)
### L.4 — Plan: A2 (85 lessons)
### L.5 — Plan: B1 (95 lessons)
Each lesson: LEARN (5 items), EXERCISE (2 MCQ + 2 FILL + 1 REORDER), DIALOGUE (6 to 8 lines),
plus objective, pareto focus, drills and review block, per `LESSON_SPEC.md`. Author in batches
of 12 to 16 through parallel agents; every batch passes `check_batch.py` before it is kept.

### L.6 — Assessment set
4 quizzes (10 questions each), 3 mock exams in the **official format** for that language's exam
family, and a banded placement test (3 independent items per band, ~9 bands) per
`PLACEMENT_SPEC.md`. Validate with `check_placement.py`.

### L.7 — Integrate, verify, ship
Move `scratchpad/<code>-build/` into `assets/content/<code>/`, add the code to
`availableLanguages`, wrap any new per-language screen in `key(lang)`, run the **full Kotlin
gate suite** (`--rerun-tasks`, since assets are not tracked inputs), fix whatever the real gates
catch, build, release, push. **The language goes live in this phase and only this phase.**

---

## Progress

| Phase | German | Italian | Spanish |
|---|---|---|---|
| L.0 research and provenance | done | done | **done 2026-07-29** |
| L.1 skeleton, identity, code wiring | done | done | **done 2026-07-29** |
| L.2 vocabulary deck | done, 2913 words | done, 2836 words | **done, 2852 words (trim to 2500 deferred to after L.5, see registry P14)** |
| L.3 A0 and A1 lessons | done, 65 lessons | done, 47 lessons | **done, A0 10 + A1 45** |
| L.4 A2 lessons | done, 80 lessons | done, 72 lessons | |
| L.5 B1 lessons | done, 140 lessons | done, 126 lessons | |
| L.6 assessment set | done | done | |
| L.7 integrate and ship | **done, live** | **done, live** | |

**German is live.** 285 lessons (A0 15, A1 50, A2 80, B1 140), 2913 words, 4 quizzes, 3 Goethe
mock exams, a 14-band placement test and a grammar syllabus. `"de"` is in `availableLanguages`
and the full Kotlin gate suite passes.

The shape changed mid-build when the standard moved to weighted per-level floors, so German was
authored at 250 and rebalanced to 285. Three things the REAL gates caught that the Python
mirrors did not, worth knowing before Italian and Spanish:

1. **Exam FILL answers need `strictDiacritics: true`.** Typed exam answers are graded strictly
   and the gate enforces the flag explicitly. Six questions lacked it.
2. **Placement anchors must be remapped after any rebalance.** The bands were written against
   the old 250-lesson boundaries, so every startDay past A1 pointed at the wrong level. Author
   placement LAST, after the plan is final, or expect to remap.
3. **Lesson `resources` strings must match `resources.json` names character for character.**
   The lessons said "Goethe-Institut Deutsch üben" and the resource was named
   "Goethe-Institut, Deutsch üben". Decide the exact strings before authoring lessons.

Also: `grammar.json` is optional in code but every shipped language has one, so it belongs in
L.1 rather than being discovered missing at integration time.

---

## Order of work

1. **German** L.0 → L.7
2. **Italian** L.0 → L.7
3. **Spanish** L.0 → L.7

Italian and Spanish reuse every tool and lesson learned from German, so they should run faster
even though the volume is identical.

---

## Effort, honestly

Per language: 250 lessons + 2500 words + 3 exams + 4 quizzes + ~27 placement items. For scale,
the 2026-07-19 session authored 297 lessons and 422 words across the three existing languages
and hit the session limit once. **Budget roughly two to three sessions per language, six to
nine in total.** The plan is phased precisely so that limit is a pause, never a loss.

---

## Standing rules that apply throughout

- Research the curriculum and exam **before** authoring (§0); verify claims live.
- No em or en dashes anywhere in learner-visible content.
- Never send the learner outside the app; external material belongs in `resources.json` only.
- The target-language text key is `"hr"` in **every** language, including these three.
- Variety discipline per language: German → standard German (note Austrian/Swiss divergences
  rather than mixing); Italian → standard Italian, not regionalisms; Spanish → Castilian, with
  contrastive notes against Latin American forms, mirroring how Portuguese guards against
  Brazilian.
- Every content unit cites a registered source key.
- Machine-authored content is **not** reviewed content: each language still needs a native
  speaker pass before it can be called finished (Track D).
