# The Error Registry

Every defect class ever found in a Corlang course, with its automation and its sweep status
across ALL languages. This is the self-improving loop made concrete: an error found once in
one language becomes a check run against every language, forever.

**The lifecycle (mandatory, same commit as the fix):**

1. **Register.** A new failure class gets a row here when it is found. An error without a
   registry row is not fixed, it is postponed.
2. **Scope.** Decide what it applies to: one language, all languages, decks, exams, Kotlin
   copy, or process.
3. **Sweep.** Run the check against every in-scope language NOW. A sweep that cannot happen
   now goes in **Open sweeps** below with what blocks it. The registry's founding example: the
   dash rule was "fixed" twice (content purge 2026-07-19, seed-greeting gate 2026-07-20) and a
   grep while writing this file still found ~40 dash-bearing strings in UI Kotlin. Fixed twice
   is not swept.
4. **Automate.** Encode it at the strongest layer that fits: Kotlin gate (`ContentValidationTest`)
   > assembler (`build_language.py`) > batch checker (`check_batch.py` / `check_<lang>.py`)
   > course audit (`proctor.py`). A rule that lives only in prose will be violated by the
   next parallel agent.
5. **Prevent.** Encode the upstream cause in the Gold Book / specs / agent prompts, so the
   error stops being produced, not just caught.

Checker bugs are errors too (§V): a validator that silently stops checking is worse than none.

Sweep key: ✅ swept clean · 🔧 swept, fixes pending · ▢ not yet swept · — not applicable.

---

## I. Content: language-independent

| ID | Failure class | Found | Automated by | hr | fr | pt | de | it |
|---|---|---|---|---|---|---|---|---|
| C1 | Em/en dashes in learner-visible content | 2026-07-19, all courses (3822 purged) | Kotlin gate `content_usesNoEmOrEnDashes`; DASH regex in every tool | ✅ | ✅ | ✅ | ✅ | ✅ |
| C2 | Dashes in learner-visible KOTLIN copy (gate above only walks assets) | 2026-07-20, tutor seed greetings | Kotlin gate over ALL string literals (16 swept, Drills.kt delimiters allowlisted) | ✅ | ✅ | ✅ | ✅ | ✅ |
| C3 | Sending learners outside the app (URLs, institutions, platforms) | 2026-07-18, hr Croaticum/FFZG refs | EXTERNAL regex in `check_batch.py`; Kotlin gate | ✅ | ✅ | ✅ | ✅ | ✅ |
| C4 | Dead or unverified resource URLs | pre-2026-07 (dead YouTube link shipped) | Live-verify rule, Gold Book Phase 2; no automated gate possible | ▢ | ▢ | ✅ | 🔧 | ✅ |
| C5 | FILL answer appearing in its own prompt | fr/pt expansion | `check_batch.py`; Kotlin gate | ✅ | ✅ | ✅ | ✅ | ✅ |
| C6 | REORDER prompt leaking the answer tokens | fr/pt expansion | `check_batch.py` 70% token-overlap rule | ✅ | ✅ | ✅ | ✅ | ✅ |
| C7 | REORDER with fewer than 3 tokens | 2026-07-20, it_a1a "il sole" | `check_batch.py` | ✅ | ✅ | ✅ | ✅ | ✅ |
| C8 | Duplicate prompts within a container | fr/pt expansion | `check_batch.py`; Kotlin gate | ✅ | ✅ | ✅ | ✅ | ✅ |
| C9 | "day N" phrasing in learner text | fr/pt expansion | `check_batch.py`; Kotlin gate | ✅ | ✅ | ✅ | ✅ | ✅ |
| C10 | Objective restated verbatim in drills/intros ("In this lesson you will…") | 2026-07-20, pt lesson 1 (user report) | `proctor.py` check 1 | ✅ | ✅ | ✅ | ✅ | ✅ |
| C11 | Stamped-out instructional boilerplate across lessons | 2026-07-20, de dialogues | `proctor.py` check 2 (ritual app nudges exempt) | ✅ | ✅ | ✅ | ✅ | ✅ |
| C12 | Duplicate taught sentences across days | 2026-07-20 proctor sweep | `proctor.py` check 3 | ✅ | ✅ | ✅ | ✅ | ✅ |
| C13 | Quiz/exam prompts colliding with lesson prompts (tests memory, not language) | 2026-07-20 proctor design | `proctor.py` check 4 | ✅ | ✅ | ✅ | ✅ | ✅ |
| C14 | MCQ answer visible in its own prompt | 2026-07-20 proctor design | `proctor.py` check 4 | ✅ | ✅ | ✅ | ✅ | ✅ |
| C15 | Longest-option-is-answer bias > 55% (guessable course) | 2026-07-20 proctor design | `proctor.py` check 5 | ✅ | ✅ | ✅ | ✅ | ✅ |
| C16 | Sources keys citing documents never consulted (provenance overclaim) | 2026-07-20, de/it decks (user question) | Gold Book Phase 8b; no mechanical gate possible | ▢ | ▢ | ▢ | 🔧 | ✅ |

*C10–C14 sweep counts (2026-07-20): hr 118, fr 56, pt 45, de 80 problems. 🔧 clears to ✅
when `proctor.py` runs clean on that language.*

## II. Content: language-specific variety and orthography

| ID | Failure class | Found | Automated by | Applies | Status |
|---|---|---|---|---|---|
| V1 | Serbian drift in hr (da-constructions, ekavian, Serbian lexis) | field report, Tutor | `varietyRules("hr")`; model eval `tools/ai-variety-eval.py` | hr | ✅ |
| V2 | Brazilianisms in pt without European counterpart in same activity | pt expansion | Kotlin gate (activity-scoped) | pt | ✅ |
| V3 | Austrian/Swiss forms in de without standard counterpart in same activity | de build | `check_de.py` REGIONAL (whole-word both sides) | de | ✅ |
| V4 | Southern perfect auxiliaries (ist gesessen/gestanden/gelegen) | de build | `check_de.py`, scoped to produced-German keys | de | ✅ |
| V5 | Pre-reform sharp s (daß, muß, läßt) | de build | `check_de.py` | de | ✅ |
| V6 | Missing Italian accents where the bare form is not a word (perche, piu, e') | it build; 1 real hit in it_a2a | `check_it.py`, casing-aware for Sara/fara | it | ✅ |
| V7 | Wrong Italian article form (il studente, un amico, un'cane) | it build | `check_it.py` | it | ✅ |
| V8 | Passato remoto taught below B1 | it build | `check_it.py`, level-scoped (B1 recognition lesson exempt) | it | ✅ |
| V9 | Deck nouns without their article (gender unlearnable) | de convention; 3 real hits in it_vocab_a | ad-hoc verification scripts per deck | de, it, es | ✅ de/it |
| V10 | Article/gender cross-mismatch in deck (der X tagged n. f.) | de deck verification | ad-hoc verification scripts | de, it, es | ✅ de/it |

*New language rule: es (and every future language) gets its own `check_<code>.py` covering its
drift modes (for es: Latin American forms vs Castilian, per the pt/Brazilian precedent), plus
V9/V10 if the deck carries articles. Every V-row is a candidate check for every new language.*

## III. Structure, assessment and app integration

| ID | Failure class | Found | Automated by | Status |
|---|---|---|---|---|
| S1 | Lenient diacritic grading (é accepted for è) | field report, fr lesson 1 | `gradeFill` always strict; exam FILL `strictDiacritics` Kotlin gate | ✅ all |
| S2 | Exam FILL missing `strictDiacritics: true` | de integration (6 questions) | Kotlin gate | ✅ all |
| S3 | Wrong `passPercent` semantics (modular needs N per section, global needs null) | de/it research | `ExamRules` + tests; Gold Book Phase 6 | ✅ all |
| S4 | Placement anchors stale after a plan rebalance (band points at wrong level) | de integration (every band past A1 wrong) | Kotlin gate asserts anchor level == plan level; Gold Book: placement authored LAST | ✅ all |
| S5 | Placement bands with <3 items (silent under-placement under 2-of-3) | v0.20.77 review | Kotlin gate `placementBandsCarryExactlyThreeItemsEach` | ✅ all |
| S6 | Lesson `resources` strings not matching `resources.json` names | de integration; it caught pre-integration | assembler gate + Kotlin gate; Gold Book: freeze names BEFORE lesson authoring | ✅ all |
| S7 | Vocab packs out of ladder order (top-ups introducing A2 words after B1) | de assembly | `build_language.py` stable level sort | ✅ all |
| S8 | Course top-light vs guided-hours weighting (volume rule hiding thin B1) | 2026-07-20 (user question) | Kotlin `levelFloor` per language + debt map | 🔧 fr +145, hr +90, pt +70 |
| S9 | Deck smaller than lessons × 10 (last lessons teach no words) | fr/pt expansion | Kotlin gate `everyDeckCoversTheWholeCourse` | ✅ all |
| S10 | Level-tag deck seeding reaching past the placement point (1886 words unlearnable) | v0.20.73, reverted | deck-index windows only; `PlacementSeedingTest` | ✅ |
| S11 | Stale composition state across language switch (day-8 A0 learner shown A1) | field report | `key(lang)` rule on every per-language screen | ✅ |
| S12 | New language missing from `TUTOR_LANGS` despite authored tutor content | de wiring | debug `assertTutorLangRegistered` | ✅ |
| S13 | New language missing Reminder branches (German learner greeted in Croatian) | de wiring | none possible; Gold Book Phase 2 checklist (3 branches) | ✅ de/it wired |
| S14 | `grammar.json` optional in code but expected by every shipped language | de integration | Gold Book Phase 2 file list | ✅ |
| S15 | Stored `currentLevel` default "A0" shown raw in A1-start courses | field report (pt fresh profile) | floor-at-plan-level rule in Words/Progress | ✅ |

## IV. Process (how errors get produced)

| ID | Failure class | Found | Prevention |
|---|---|---|---|
| P1 | Parallel vocab authors duplicating each other (376 lost in de, 300 in it) | de/it decks | disjoint slices + exclusion-list file + overshoot ≥15%; Gold Book Phase 4 |
| P2 | Exclusion file never written because chained behind a failing validator with `&&` | it top-up | never chain file writes behind validators; verify the file exists before dispatch |
| P3 | Session limit killing agents mid-authoring | de B1 (7 of 9), it B1 (3 of 3) | agents write output files BEFORE reporting; re-validate from disk, never re-author |
| P4 | In-flight work invisible to later sessions (scratchpad not in git) | de build | build path recorded in `docs/new-languages-plan.md`; tools promoted to `tools/course/` |
| P5 | Python mirrors drifting from the real Kotlin gates | de integration (3 misses) | mirrors are pre-checks only; Phase 9 always runs the full suite `--rerun-tasks` |
| P6 | Content-only changes silently skipping gates (test task doesn't track assets) | fr/pt expansion | `--rerun-tasks` always, encoded in Gold Book Phase 8f/9 |
| P7 | Retiring lessons by deleting authored files | de rebalance (avoided) | title-based retire lists with reasons, loud-fail on mismatch |
| P8 | Agent self-validation accepted as verification | it vocab (agent's "validated" file had 3 article misses) | every delivered batch independently re-verified; Gold Book Phase 4/5 |
| P11 | Re-running the assembler AFTER auditing the assembled build silently discards every audit fix, because plan files are regenerated from the source batches (Italian went 0 problems back to 48 on a reassembly to add vocabulary) | 2026-07-20, it | audit LAST, after the final assembly; if a reassembly is unavoidable, keep the fix script and re-apply it, and always re-run the proctor after ANY assembly |
| P10 | An authoring RULE worded identically in every agent prompt becomes boilerplate in the output (the spiral-review rule produced "with one review point from an earlier lesson" in 17 Italian lessons) | 2026-07-20, it proctor | state the rule as an INTENT plus an explicit instruction to vary the surface wording; proctor catches the residue |
| P9 | Claiming feature gaps without auditing the implementation (TTS listening and AI writing feedback both existed while being reported as missing) | 2026-07-20 roadmap discussion | audit the code BEFORE writing any gap analysis; a roadmap claim about the app is a claim about code and gets verified like one |

## V. Checker bugs (the checks themselves are code and fail)

| ID | Failure class | Found | Prevention |
|---|---|---|---|
| K1 | Substring match letting the English gloss excuse the error ("January" excusing "Jänner") | check_de v1 | whole-word matching on BOTH sides of a counterpart rule |
| K2 | Scanning English commentary that names a wrong form in order to reject it (4 rounds of exemption-patching) | check_de v2-4 | scope by KEY: only `hr`/`target`/`answer`/`options`/`ordered`/`accepted` |
| K3 | Flagging correct words as unaccented (Sara the name, te the pronoun, meta the noun) | check_it v1 | only list forms that are not words without the accent; casing-aware for name collisions |
| K4 | Flagging deliberate ritual repetition as boilerplate (Words-tab nudge, 185×) | proctor v1 | exempt phrases naming app surfaces; repetition by design is not a defect |
| K5 | A loosened check silently becoming a no-op | risk, de | keep a planted-defect fixture per checker; re-run it after EVERY change |

---

## Open sweeps (the queue this registry exists to drain)

1. **C10–C14**: DRAINED for all four shipped languages 2026-07-20 (hr 118, de 80, fr 56, pt 45, all to zero, independently verified); it audits at assembly. The CI proctor step can flip to hard-fail.
2. **C16**: de CHECKED 2026-07-20 (DWDS mirrors + all three official PDFs, complete): deck
   covers 46.3% of the official A1..B1 inventory, 262 official A1 lemmas absent entirely, so
   the `goethe-wortliste` key was REMOVED from all 22 packs until the gap-close authoring pass
   earns it back. **it CHECKED and ACTED ON before shipping**: the cross-check found 5 required
   structures missing (demonstratives from A1 among them), all now authored, plus a 300-word
   fondamentale top-up against a measured 56.2% coverage gap. hr/fr/pt digests still unchecked.
4. **C4**: live re-verification of hr and fr resources.json URLs; de's DW link was confirmed
   by search only (fetcher blocks dw.com), noted in its digest.
5. **S8**: weighted-floor debt, fr +145 (legally B2, priority) → hr +90 → pt +70.
