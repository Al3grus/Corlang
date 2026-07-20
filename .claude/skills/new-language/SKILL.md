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
    process trap not to repeat. Before declaring an audit done, drain or explicitly defer
    every entry in the registry's Open sweeps section.

## Current state (update this section whenever it changes)

- Shipped: hr, fr, pt, de. In progress: it (research, wiring, deck 2536, 200/240 lessons in
  the scratchpad build; missing it_b1c 35 lessons, 5 of it_b1d's 35, assessment set,
  proctoring, integration).
- Proctor backlog on shipped languages (2026-07-20): hr 118, fr 56, pt 45, de 80 problems.
- Weighted-floor debt: fr +145 (B2 legally required since 2026-01-01, highest priority),
  hr +90, pt +70.
