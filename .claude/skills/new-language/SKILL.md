---
name: new-language
description: Build a new Corlang language course, or audit an existing one, following the Gold Book workflow (research → shape → wiring → deck → lessons → assessment → assembly → proctoring → ship). Use when the user asks to add a language or audit a course.
---

# New language / course audit

READ `docs/course-gold-book.md` IN FULL before doing anything. It is the canonical workflow;
this skill only tells you how to drive it. `docs/language-standard.md` is the contract the
result must satisfy. Tools live in `tools/course/`.

## Arguments

- A language name or code ("add Spanish", "es") → **build mode**: execute Phases 0 through 9
  in order.
- An existing language code plus "audit" ("audit fr") → **audit mode**: execute Phase 3 (write
  or update `check_<code>.py` if the language has none) and Phase 8 in full against
  `app/src/main/assets/content/<code>/`, fix what is found, and report what needed a human.

## Non-negotiables when executing

1. Phases run IN ORDER. Phase 0 research is blocking and verified live the same day; the
   target level comes from the country's legal requirement, never from symmetry with other
   courses.
2. Nothing is authored into `assets/content/` before Phase 9. Author in the session scratchpad
   under `<code>-build/`, and record that path in `docs/new-languages-plan.md` in the same
   commit that starts the build.
3. Every phase boundary is a commit with the build green.
4. Subagents write their output files BEFORE reporting, and every delivered batch is
   re-verified independently the moment it lands. Do not accept an agent's self-validation.
5. The per-language checker is negative-tested with planted defects before it is trusted, and
   the fixture is kept and re-run after every change to the checker.
6. `sources` keys are earned by the Phase 8b syllabus cross-check, not by plausibility.
7. Phase 8 proctoring runs to zero problems before integration. The mechanical sweep is
   `python tools/course/proctor.py <build-dir>`; 8b through 8f are agent and research work.
8. A new language adds its row to `ContentValidationTest.levelFloor` and its source keys to
   `knownSourceKeys`; a shipped fix removes its entry from any debt map.
9. Follow every standing rule in the Gold Book's final section (dashes, the `hr` key, no
   external references, frozen resource names, difficulty bands).
10. **The error loop.** Any defect found, in any phase, in any language, follows the lifecycle
    in `docs/error-registry.md` in the same commit as the fix: register → scope → sweep every
    language → automate → encode prevention. Before authoring a new language, read the
    registry: every V-row is a candidate check for its `check_<code>.py`, every P-row a
    process trap not to repeat. **Never leave a finding "flagged as a follow-up" regardless of
    size** (standing rule, 2026-07-28): drain every entry in the registry's Open sweeps section
    before declaring an audit done, however large the remaining work (authoring 168 missing
    activities is in scope, not just wording fixes). The only acceptable non-closure is a real
    external blocker — a human-only decision or a live fact needing the user's verification —
    surfaced explicitly and asked about, never silently deferred.

## Current state (update this section whenever it changes)

- Shipped: hr, fr, pt, de. In progress: it (research, wiring, deck 2536, 200/240 lessons in
  the scratchpad build; missing it_b1c 35 lessons, 5 of it_b1d's 35, assessment set,
  proctoring, integration).
- **hr full-course audit + fix FULLY DONE (2026-07-27/28)**: 19-agent audit found 31 Critical/49
  High/~85 Medium/~90 Low across every lesson, the deck, quizzes/placement, mock exams and
  reference content; all fixed and verified. `docs/error-registry.md` C17-C19/V11/K8 added.
  Second pass (2026-07-28) closed everything the first pass had deferred, per the standing
  never-leave-pending rule: 308 `check_batch.py` structural findings (168 missing DIALOGUE
  activities authored, 57 3-option MCQs widened, 55 over-length dialogues trimmed, 26 cross-range
  duplicate prompts reworded), the ASOO A1 directional-preposition unit (u/na/po/za + accusative,
  ići present — added to day 24, no renumbering), and 4 more checker bugs found and fixed along
  the way (K9-K12: check_batch's day-count CLI bug, a diacritic-restoration false-positive, and
  two check_hr.py false positives). Fully verified: `check_batch.py`/`check_hr.py` 344 days/0
  problems, `proctor.py` 0 problems, Kotlin `ContentValidationTest` green. Nothing open.
- **fr full-course audit + fix FULLY DONE (2026-07-28)**: fr had no per-language checker before
  this; wrote and negative-tested `check_fr.py` (V12) first, then a 24-agent audit found 20
  Critical/41 High/~45 Medium/~55 Low across all 418 lessons (extra rigor on B2, the legally
  load-bearing level), the 4,223-word deck, quizzes/placement, all 4 DELF mocks, reference
  content, and the CECRL/DELF syllabus cross-check. All fixed and verified. Biggest find:
  `grammar.json` was missing accents on every French word in the file, all 37 topics restored;
  `levels.json` had the same bug. All 4 DELF mocks were structurally missing required documents
  (multi-document listening/reading) — authored to close every gap. New standing rule from this
  pair of audits: no real people/institutions anywhere in lesson content (`docs/course-gold-book.md`
  + `docs/language-standard.md` §7 updated; `resources.json` remains the one sanctioned
  exception). Second pass closed the 2 remaining items: `decret-2025-648` now cited in the 3
  activities asserting the naturalisation fact plus `levels.json`'s B2 exam and the naturalisation
  vocab pack; `grammar.json`'s topic index resynced with 6 real new topics from the 250→418
  expansion. Nothing open.
- **pt full-course audit + fix FULLY DONE (2026-07-28)**: pt had no per-language checker before
  this; wrote and negative-tested `check_pt.py` (V13, mirrors the Kotlin Brazilianism gate plus
  a closed-whitelist gerund-progressive check) first, then a 7-agent audit (4 over all 170
  lessons, 1 over the 2,568-word deck, 1 over quizzes/placement/exams, 1 over reference content
  + Phase 8b CAPLE/Camões syllabus cross-check, which found no curriculum gap). Found and fixed
  85 Critical/45 High/~40 Medium/~55 Low. Real-people/institutions was by far the largest class
  (Amália Rodrigues, Camões, Fernando Pessoa, Saramago, Gil Vicente, CAPLE, Universidade de
  Lisboa, Livraria Lello, Câmara Municipal de Braga with a fabricated realistic contact block,
  real CP train brands, Multibanco, Benfica — 12 of them concentrated in one vocab pack). Also:
  34 3-option MCQs in one 10-day span, epicene-noun gender bugs, broken idiom headwords, a
  translation leak, CIPLE mock's weighting silently wrong vs. the real 45/30/25 CAPLE split,
  every mock's reading/listening sections single-document against the real plural-document
  format, 3 placement-band items testing content taught days later, quizzes at 8 questions
  instead of 10, 17 consecutive B1 days on a thinner authoring template than their neighbors —
  all authored up to depth. `docs/error-registry.md` open-sweeps item 7 has the full breakdown.
  Fully verified: `check_batch.py`/`check_pt.py`/`proctor.py` 0 problems, Kotlin
  `ContentValidationTest` green, 0 dashes. `docs/sources/README.md`/`caple.md` stale "target
  DIPLE B2" headers corrected (real target is DEPLE B1, DIPLE B2 legacy since 2026-07-20).
  Nothing open.
- **de full-course audit + fix FULLY DONE (2026-07-28)**: `check_de.py` already existed but had
  the K13 silent-no-op bug (fixed and negative-tested first). K14 (crashed outright on vocab/
  assessment file shapes) and K15 (no Swiss ss-for-ß direction check, which surfaced K16, a
  latent English-word-collision scoping bug) were found and fixed the same day, negative-tested,
  and re-verified against real content — K14's fix alone caught one real pre-existing defect
  ("der Fluß" as a quiz answer instead of a wrong distractor). A 13-agent audit (10 over all
  285 lessons, 1 over the 2,850-word deck, 1 over quizzes/placement/exams, 1 over reference +
  Phase 8b Goethe/telc syllabus cross-check, clean, no
  curriculum gap) — all 13 hit a shared session-limit wall at once and were cleanly relaunched.
  **de is noticeably cleaner than hr/fr/pt**: zero real-people/institution violations anywhere
  in the 2,850-word deck. Still found and fixed ~9 Critical/~21 High/rest Medium/Low — biggest:
  a `goethe-wortliste` source-key regression (431 stale citations across `grammar.json`'s B1
  section and every B1 lesson activity, reintroducing an overclaim a prior digest had closed);
  and B1 days 220-223's civics mini-unit quoting Article 1/20 GG verbatim and naming the
  Bundesverfassungsgericht, the exact carve-out-removal pattern the Gold Book exists to prevent
  (fixed by genericizing only day 222, the one day with actual violations — days 220/221/223's
  generic constitutional vocabulary was correctly left alone). Also: 5 placement bands testing
  future content, all 6 mock-exam receptive sections single-document vs. the real multi-Teil
  format, B1 Schreiben missing its 3rd task, 8 duplicate reflexive-verb vocab entries (removing
  them dropped the deck 8 words under the `everyDeckCoversTheWholeCourse` floor — caught by the
  Kotlin gate, closed by authoring 8 new non-duplicate B1 words rather than reverting the dedup).
  `docs/error-registry.md` open-sweeps item 8 has the full breakdown. Fully verified:
  `check_batch.py`/`check_de.py`/`proctor.py` 0 problems, Kotlin `ContentValidationTest` green,
  0 dashes. Nothing open.
- Proctor backlog: hr, fr, pt, and de all CLEARED this session (2026-07-27/28). **Next: es from
  zero** (PCIC-anchored, not yet built), then native review (Track D, open on all 4 shipped
  courses — the machine-authored expansion lessons have never been human-read).
- Weighted-floor debt UNCHANGED by the hr/fr/pt/de audit passes above (they added LEARN/EXERCISE
  depth within existing days, not new lesson days, so the day-count floor gate is untouched for
  pt): fr +145 (B2 legally required since 2026-01-01, highest priority), hr +90, pt +70 (A2 short
  15, B1 short 55, per `ContentValidationTest.weightedRuleDebt`). de has no floor debt.
