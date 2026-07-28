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
| C16 | Sources keys citing documents never consulted (provenance overclaim) | 2026-07-20, de/it decks (user question) | Gold Book Phase 8b; no mechanical gate possible | ✅ | ✅ | ▢ | ✅ | ✅ |
| C17 | MCQ explanation names a distractor by POSITION ("the second option...") which the app shuffles at render, so the explanation can land on and disparage the CORRECT answer after shuffling | 2026-07-27, hr A0 days 15/16 | no automated gate yet; a proctor regex for "the (first\|second\|third\|last) option" across every explanation would catch it | ✅ | ▢ | ▢ | ▢ | ▢ |
| C18 | Perfective/imperfective headword's own `example.target` shows only the OTHER aspect partner's form, never the headword itself | 2026-07-27, hr deck (8+ real hits: shvatiti, krenuti, odrediti, dozvoliti, obuhvatiti, olakšati, posvetiti se, sjediti) | no automated gate yet; a check for "headword stem present in example.target" would catch it mechanically | ✅ | ▢ | ▢ | ▢ | ▢ |
| C19 | Grammar-note commentary concatenated directly into the target-language field (`hr`) instead of a separate `note`/`en` field, so TTS and the learner see partly-English "Croatian" | 2026-07-27, hr B1 days 311/312/315/316 (13 items, one late authoring batch) | no automated gate yet; a per-language ASCII-density check on `.hr`-suffixed keys would catch it | ✅ | ▢ | ▢ | ▢ | ▢ |

*C10–C14 sweep counts (2026-07-20): hr 118, fr 56, pt 45, de 80 problems. 🔧 clears to ✅
when `proctor.py` runs clean on that language. hr re-verified clean 2026-07-27 after the
full-course audit and fix pass below.*

## II. Content: language-specific variety and orthography

| ID | Failure class | Found | Automated by | Applies | Status |
|---|---|---|---|---|---|
| V1 | Serbian drift in hr (da-constructions, ekavian, Serbian lexis) | field report, Tutor | `varietyRules("hr")`; model eval; `check_hr.py` batch gate (built 2026-07-21, negative-tested). On its first assembly run it CAUGHT A SHIPPED DEFECT: the A0 day-12 lesson taught 'Da li govoris?' as the correct MCQ answer, now fixed to the standard 'Govoris li?' | hr | ✅ |
| V2 | Brazilianisms in pt without European counterpart in same activity | pt expansion | Kotlin gate (activity-scoped) | pt | ✅ |
| V3 | Austrian/Swiss forms in de without standard counterpart in same activity | de build | `check_de.py` REGIONAL (whole-word both sides) | de | ✅ |
| V4 | Southern perfect auxiliaries (ist gesessen/gestanden/gelegen) | de build | `check_de.py`, scoped to produced-German keys | de | ✅ |
| V5 | Pre-reform sharp s (daß, muß, läßt) | de build | `check_de.py` | de | ✅ |
| V6 | Missing Italian accents where the bare form is not a word (perche, piu, e') | it build; 1 real hit in it_a2a | `check_it.py`, casing-aware for Sara/fara | it | ✅ |
| V7 | Wrong Italian article form (il studente, un amico, un'cane) | it build | `check_it.py` | it | ✅ |
| V8 | Passato remoto taught below B1 | it build | `check_it.py`, level-scoped (B1 recognition lesson exempt) | it | ✅ |
| V9 | Deck nouns without their article (gender unlearnable) | de convention; 3 real hits in it_vocab_a | ad-hoc verification scripts per deck | de, it, es | ✅ de/it |
| V10 | Article/gender cross-mismatch in deck (der X tagged n. f.) | de deck verification | ad-hoc verification scripts | de, it, es | ✅ de/it |
| V11 | False "distinctly Croatian" vocab notes: a standard Croatian word framed as the non-Croatian alternative to a register/loanword variant that is ALSO standard (insekt, redovno, protest, geografija, šporet, mobilni telefon all wrongly cast as "Serbian"), the same overclaim class check_hr.py exists to prevent, running backwards | 2026-07-27, hr deck (7+ real hits) | no automated gate; requires a native-checked whitelist of genuine hr/sr lexical pairs vs. register pairs | hr | 🔧 (7 found and fixed 2026-07-27; no gate built yet, re-sweep needed if more vocab is authored) |

*New language rule: es (and every future language) gets its own `check_<code>.py` covering its
drift modes (for es: Latin American forms vs Castilian, per the pt/Brazilian precedent), plus
V9/V10 if the deck carries articles. Every V-row is a candidate check for every new language.*

| V12 | Belgian/Swiss numbers (septante/octante/nonante) and Quebecois lexis/meal-name shift (déjeuner=breakfast, dîner=lunch) taught as standard Metropolitan French, or without a contrastive counterpart in the same activity | 2026-07-27, fr full-course audit (built check_fr.py — fr had no per-language checker before this) | `check_fr.py`, built and negative-tested this session (activity-scoped exemption, same design as check_de.py's REGIONAL table); found only 1 real hit course-wide (day 185's dialogue line dropped its own lesson's contrastive pairing), now fixed | fr | ✅ |

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
| S8 | Course top-light vs guided-hours weighting (volume rule hiding thin B1) | 2026-07-20 (user question) | Kotlin `levelFloor` per language + debt map | 🔧 pt +70 (fr and hr CLEARED 2026-07-21) |
| S18 | Hand-reassembling a shipped deck lost/duplicated words because shipped vocab FILES contain multiple packs each (fr 00-a1-core.json holds 10), but the assembler and my splice both assumed one pack per file | 2026-07-21, fr integration | never rebuild a shipped deck; restore its files from git untouched and only ADD new pack files, inserting them into _index at the right ladder position |
| S17 | Deck LARGER than lessons × 10, so the tail never unlocks and the words are dead weight (de ships 2913 words against a 2850 cap, 63 unreachable) | 2026-07-20, de gap-close planning | the deck is a FIXED-CAPACITY pipe: vocabulary work is a SWAP, not an add. Author missing core words by displacing the least valuable existing ones, or add lessons to widen the pipe |
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
| P12 | A large single-agent batch (45 lessons, 750 words) is disproportionately likely to hit a mid-response connection drop, wasting the whole run; observed twice on 2026-07-20 | 2026-07-20, fr B2 | keep a batch to ~25 lessons or ~500 words; the write-before-report rule still saves completed chunks, but smaller batches lose less and finish more reliably |
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
| K7 | A stem-with-suffix regex over-matching a legitimate word family (check_hr's voz\w* flagged vozac/vozilo/voziti, all standard Croatian, when only the Serbian train noun voz is the target) | 2026-07-21, hr | match a Serbian noun by its exact case-forms, not a stem+\w*, when a legitimate same-stem family exists |
| K6 | Flagging a correct inflected form as the wrong variety (check_hr flagged "vremena", the correct Croatian genitive of vrijeme, as ekavian) | 2026-07-21, hr | list only forms that are the wrong variety in EVERY inflection; the ije-to-e alternation is regular in Croatian oblique cases, so match bare "vreme" not the "vremen-" stem |
| K8 | check_hr.py's Serbian-drift regexes were ASCII-only (no š/č/ć/ž/đ character classes), so they never matched real shipped Croatian, which always carries proper diacritics; the checker had been auditing nothing since the day it stopped seeing pre-diacritic planning drafts. Also could not parse the assembled `{title, days}` phase-file shape at all (expected a bare array), so it silently skipped the entire shipped course | 2026-07-27, hr full-course audit | rewrote the regexes with `[sš]`/`[cč]`/`[zž]`/`(?:đ\|dj)` character classes matching both spellings; added a `{title,days}` unwrap to both check_hr.py's CLI and check_batch.check_file (new check_batch.check_file_obj helper); negative-tested against both ASCII and diacritic-bearing planted defects post-fix |

---

## Open sweeps (the queue this registry exists to drain)

1. **C10–C14**: DRAINED for all four shipped languages 2026-07-20 (hr 118, de 80, fr 56, pt 45, all to zero, independently verified); it audits at assembly. The CI proctor step can flip to hard-fail.
1a. **hr full-course audit (2026-07-27)**: 19-agent audit (11 lesson-range reviewers, 4 deck
   reviewers, quizzes/placement, exams, reference, syllabus) plus `proctor.py` + full Kotlin
   gate + a diacritic-aware Serbian-drift sweep. Found and FIXED: 31 Critical (wrong clitic
   order stated in 5 places incl. `feynman.json`, 13 English-contaminated `hr` fields in one
   batch, a gender-agreement dialogue, a broken exam answer key, a broken quiz answer key, 3
   reversed/corrupted `grammar.json` table cells), 49 High (case-government errors, aspect-
   partner mismatches, false-Serbianism vocab notes = V11, real institutions/athletes named in
   lesson text, an MCQ-shuffle-position bug = C17), plus most Medium findings. Deck trimmed
   90→ back to exactly the 3,440-word cap by removing 76 off-level/redundant B1 words.
   `check_hr.py` itself was broken (K8: ASCII-only, couldn't parse the assembled format) and is
   now fixed and negative-tested. NOT attempted: 314 `check_batch.py` structural findings (168
   lessons missing a DIALOGUE activity, 46 3-option MCQs, 55 over-length dialogues, 27 duplicate
   prompts) — this is new Phase-5-scale lesson authoring, not a fix, and needs its own pass. Also
   open: Phase 8b found the ASOO A1 directional-preposition unit (u/na/po/za + accusative) is
   never taught. One judgment call surfaced a conflict with `[[corlang-session-resume]]`'s
   2026-07-18 note "HRT immersion mentions kept deliberately" — this audit treated repeated HRT/
   Ruđer Bošković Institute/Dinamo/Hajduk naming as boundary-rule violations and genericized all
   of it; flagged for the user to confirm or revert given the prior deliberate decision.
2. **C16**: de CHECKED 2026-07-20 (DWDS mirrors + all three official PDFs, complete): deck
   covers 46.3% of the official A1..B1 inventory, 262 official A1 lemmas absent entirely, so
   the `goethe-wortliste` key was removed from all 22 packs, then EARNED BACK the same day for
   A1 and A2 by a 477-word gap-close and a 540-word swap that lifted coverage to 81.9% and
   89.2%; it stays off the B1 packs, whose 41% coverage cannot be fixed without more lessons. **it CHECKED and ACTED ON before shipping**: the cross-check found 5 required
   structures missing (demonstratives from A1 among them), all now authored, plus a 300-word
   fondamentale top-up against a measured 56.2% coverage gap. **fr CHECKED**: three keys were pure overclaims (referentiel-fr on 723 citations, plus
   francais-fondamental and freq-fr; none of those documents was ever fetchable). referentiel-fr
   re-pointed to the Eaquals/CIEP Inventaire which WAS fetched complete, the other two retired in
   favour of Lexique 3.83. hr and pt digests still unchecked.
4. **C4**: live re-verification of hr and fr resources.json URLs; de's DW link was confirmed
   by search only (fetcher blocks dw.com), noted in its digest.
5. **S8**: weighted-floor debt, fr +145 (legally B2, priority) → hr +90 → pt +70.
6. **fr full-course audit (2026-07-27)**: no per-language checker existed for fr before this
   session; wrote and negative-tested `check_fr.py` (V12), then ran a 24-agent audit (16 over
   all 418 lessons with extra rigor on B2, the legally load-bearing level, 5 over the 4,223-word
   deck, plus quizzes/placement/exams/reference/syllabus). Found and FIXED: 20 Critical (13
   real people/institutions incl. Camus, Victor Hugo and France Éducation international named
   in `grammar.json`/`levels.json` itself; `grammar.json` missing accents on every French word
   in the whole file, all 37 topics restored; a mock-exam elision bug; 5 self-contradicting
   grammar rules where the course's own other examples proved the stated rule wrong), 41 High
   (6 epicene nouns tagged masculine-only contradicting their own example; a DALF C1 task
   labelled "the DELF B2 synthesis task", a real risk given B2 is the legal citizenship target
   here; all 4 DELF mocks missing required document types, now authored). Also closed: 4
   placement bands under-diversified (same pattern as hr), all 4 quiz difficulty orderings,
   the recurring FILL-punctuation-artifact pattern (11 instances), the œ-ligature spelling gap.
   Still open: `decret-2025-648` (the naturalisation law, "the most important citation" per the
   digest) is never cited despite the requirement being asserted as fact 3 times; `grammar.json`
   was not resynced with the 12 new B2/B1/A2 gap-fill topics from the 250→418 day expansion.
