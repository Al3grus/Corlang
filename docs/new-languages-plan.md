# Adding German, Italian and Spanish (target B1)

Working plan for the next three courses, in order: **German → Italian → Spanish**, each taught
to **B1**. Every phase is a stopping point: it ends with work committed and the build green, so
a session that runs out of budget mid-plan loses nothing. `docs/language-standard.md` is the
contract; this file is only the schedule.

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

250 lessons, matching the standard's floor and Croatian's B1-target precedent:

| Level | Lessons | Cumulative |
|---|---|---|
| A0 onramp | 15 | 15 |
| A1 | 55 | 70 |
| A2 | 85 | 155 |
| B1 | 95 | 250 |

Per the every-level checkpoint rule: **4 quizzes** (A0, A1, A2, B1), **3 readiness milestones**
and **3 mock exams** (A1, A2, B1). A0 gets a quiz only, since no official A0 exam exists.
Deck: **≥2500 words** (250 lessons × 10 a lesson), packs in SRS introduction order.

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
session will not find the work unless it is told where to look. **Current German build
directory:**

```
C:\Users\al3gr\AppData\Local\Temp\claude\C--Users-al3gr-Desktop-Github-Corlang\
  4eb9e133-fe10-44a6-adf4-e0fbfa9f0267\scratchpad\
```

with the assembled course under `de-build/` and the raw authored batches (`de_vocab_*.json`,
`de_a0.json`, `de_a1*.json`) beside it. The shared authoring tools live in the same directory:
`LESSON_SPEC.md`, `PLACEMENT_SPEC.md`, `check_batch.py`, `check_placement.py`, `merge_plan.py`,
`merge_vocab.py`, `purge_dashes.py`. Update this path when a later session starts a new build.

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
| L.0 research and provenance | done | | |
| L.1 skeleton, identity, code wiring | done | | |
| L.2 vocabulary deck | done, 2524 words | | |
| L.3 A0 and A1 lessons | done, 70 lessons | | |
| L.4 A2 lessons | done, 85 lessons | | |
| L.5 B1 lessons | done, 95 lessons | | |
| L.6 assessment set | next | | |
| L.7 integrate and ship | | | |

**German stands at 250 lessons and 2524 words, fully assembled** in `de-build/` as `plan/`
(phase0-a0 15, phase1-a1 55, phase2-a2 85, phase3-b1 95, days contiguous 1 to 250) and
`vocab/` (18 packs in ladder order), plus the five identity files. Everything passes
`check_de.py`. What remains for German is L.6 and L.7 only.

### What L.6 still needs, with the decisions already made

`quizzes.json`: 4 quizzes keyed `levelId` A0, A1, A2, B1, 8 to 10 questions each, same
question shape as the lesson EXERCISE types.

`exams.json`: a JSON ARRAY (not an object) of 3 mock exams, each with `id`, `levelId`, `title`,
`description`, `passRule`, `sources`, `sections`. Sections carry `id`, `kind`
(READING/LISTENING/WRITING/SPEAKING), `title`, `instructions`, `passPercent`, and then either
`passages` plus `questions` for the scored skills or `prompts` (with `modelAnswer` and `rubric`)
for writing and speaking.

**The `passPercent` values are load-bearing and differ per level, so get them right:**

- A1 and A2 mocks: `passPercent: null` on every section. These exams are NOT modular, and
  `ExamRules.goetheGlobalPassed` grades the four parts together at 60% overall.
- B1 mock: `passPercent: 60` on every section, because the Goethe B1 IS modular and
  `ExamRules.examPassed` requires every section to have a passing latest attempt.

One thing to verify before authoring the B1 mock: `sectionPassed` returns false when `total`
is 0, and WRITING and SPEAKING sections carry `prompts` rather than scored `questions`. Check
how a self-assessed section records its verdict in `ExamScreen`, because if such a section
reaches `sectionPassed` with no questions, a `passPercent` of 60 would make the B1 mock
impossible to pass. The Portuguese mocks never hit this because CAPLE averages instead.

`placement.json`: `title`, `intro`, `questions`, each question carrying `level`, `startDay`,
`type`, `difficulty`, `prompt`, `options`, `answer`, `explanation`. The gate requires exactly
three items per band and the `level` to match the level of the plan day at `startDay`. Planned
German bands, 14 bands and 42 questions:

| startDay | 1 | 16 | 30 | 44 | 58 | 71 | 88 | 105 | 122 | 139 | 156 | 180 | 205 | 228 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| level | A0 | A1 | A1 | A1 | A1 | A2 | A2 | A2 | A2 | A2 | B1 | B1 | B1 | B1 |

Two tools were added to the scratchpad during the German build and are worth reusing for
Italian and Spanish:

- `build_language.py` assembles a course **from scratch** into the `plan/` and `vocab/` layout
  the app expects. `merge_plan.py` and `merge_vocab.py` only append to a language that already
  exists in `assets/content`, so neither could start a new one.
- `check_de.py` layers the German-only checks over `check_batch.py`: regional Austrian and
  Swiss forms, southern perfect auxiliaries, and pre-reform sharp-s spellings. Both the
  regional check and the auxiliary check are **activity-scoped**, so a form may appear when the
  lesson is teaching against it (as an incorrect MCQ option, or beside its standard
  counterpart) but not otherwise. Italian and Spanish need the same shape of file, with
  regionalisms and Latin American forms respectively.

A note for whoever writes the deck next: budget the vocabulary batches to **exceed** 2500
before deduplication, not to hit it. Parallel authors cannot see each other's output, and the
German run lost 376 words to cross-batch duplication, which needed a third round of top-ups
authored against an explicit exclusion list.

German lesson sequencing is fixed in `plan/TOPICS-A0-A1.md`, `plan/TOPICS-A2.md` and
`plan/TOPICS-B1.md` inside the build directory. Those files are the contract the batch
authoring agents work against, so a resumed session should author against them rather than
inventing a new sequence.

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
