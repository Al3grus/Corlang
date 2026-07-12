# Corlang French: Exam-Validated Content Plan (A1 → B2, job-proficiency target)

Mirrors the Croatian playbook (docs/sources/method.md, the hr content structure, and the
Croatian plan) exactly — same architecture, same quality gate, same "official-anchors-only,
no shortcuts" rule — retargeted to French and the DELF exams.

## Target: DELF B2 (with DELF B1 as the certified milestone)

For **job / professional proficiency**, the standard bar is **B2** ("independent user, can work
in the language"): it's what most employers, French universities, and many immigration tracks
require. **B1** is the independent-user threshold and a real checkpoint on the way. So the ladder
is **A1 → A2 → B1 → B2**, with a certified DELF B1 mock at the B1 gate and DELF B2 as the finish.
Same green-light cadence as Croatian: build and validate one level at a time; only advance when
you say the current level is done.

## Where French stands today (the honest baseline)

The `content/fr/` folder holds **pre-validation-era** content: a 60-day plan with **no embedded
activities**, **0 vocab words**, 6 quizzes, 10 teach-back concepts, levels A0–C1. It's a seed to
audit, not "done" by the current standard. French gets the same rebuild Croatian got: embedded
day activities, a split source-anchored vocab deck with frozen IDs, a grammar syllabus, DELF
mock exams, and a placement test — all behind the automated gate.

## Validation anchors (verified live, 2026-07)

"Validated" = every item anchored to a published official document with recorded provenance in
`docs/sources/`, enforced by tests. Not institutional certification (only FEI grants that).

- **CECRL** — the CEFR in French; official self-assessment grid + descriptors
  (coe.int; FEI B2 descriptors: france-education-international.fr/document/cecrldescripteursb2).
- **DELF official sample subjects** (France Éducation international, the body that runs the exam):
  B1 and B2 *exemples de sujets* → the mock-exam templates
  (france-education-international.fr/diplome/delf-tout-public/niveau-b2/exemples-sujets).
- **Référentiels "Niveau A1/A2/B1/B2 pour le français"** (Beacco et al., Didier + Council of
  Europe) — the official per-level content inventories; the primary grammar/vocab anchor,
  analogous to Croatian's ASOO curriculum.
- **Frequency list** — *Le Français Fondamental* (the historic official Ministry/CIEP core list)
  plus a modern named list (*A Frequency Dictionary of French*, Lonsdale & Le Bras, Routledge)
  as the cross-check.
- **DELF pass rule (verified):** 4 sections (listening / reading / writing / speaking), each /25,
  total /100. Pass = **≥ 50/100 AND ≥ 5/25 in every section** (below 5 in any one = fail). This
  differs from the Croatian NN rule and needs its own pass function.

Source keys to register in `docs/sources/README.md`: `cecrl`, `delf-b1-sample`, `delf-b2-sample`,
`referentiel-fr`, `francais-fondamental`, `freq-fr`.

---

## Phase F0 — Source digests + gate extension (first; everything cites these)

- `docs/sources/` French digests: `referentiel-fr.md` (per-level grammar/topic/vocab inventories,
  A1→B2), `delf-b1-sample.md` + `delf-b2-sample.md` (section formats, item counts, timings →
  mock templates), `cecrl-grid.md` (5-skill descriptors), `francais-fondamental.md` (core list),
  update `README.md` with the new keys + provenance rule. Fetch the DELF sample PDFs and the
  référentiel inventories, digest their contents.
- Extend `ContentValidationTest` to enforce the hr-level invariants on **fr** too: embedded-activity
  completeness, split-vocab `_index` sync + frozen IDs, provenance keys, placement/exam consistency,
  plan contiguity, NFC. (Today the test parses fr but only strictly; the deep invariants are hr-only.)
- New `src/test/resources/frozen-word-ids-fr.txt` — frozen once the A1 batch lands.

## Phase FA — Code: make the app truly multi-language (small, code-only)

The content model is already language-neutral; these are the last hard-coded-to-Croatian spots:
- **`speech/TtsManager.kt`** — locale is hard-coded `hr-HR`. Make it per active language
  (`fr` → `Locale.FRENCH`/`fr-FR`), driven by the selected language.
- **`speech/SpeechInput.kt`** — same, hr-HR → fr-FR for the speaking checks.
- **`ContentRepository.availableLanguages`** — add `"fr"` to unhide French (do this only when A1
  is validated, so users never see an empty French).
- **`ai/AiClient` tutor prompt** — the Talk/writing-feedback system prompts are Croatian-specific
  in the screens; parameterize the language name so French gets a French tutor persona.
- **`ExamRules`** — add `delfPassed(sections)` = total ≥ 50/100 with a ≥5/25 floor per section,
  and unit-test it (mirrors the existing NN pass-rule test).

## Phase FC — Levels + grammar syllabus (first content phase)

- Rewrite `content/fr/levels.json`: verbatim CECRL can-do per skill per level; `exam` on B1 =
  DELF B1 and on B2 = DELF B2 (name, 4 sections, the 50/100 + 5/25 rule, source keys). A0 kept as
  the on-ramp; C1/C2 marked as future continuation.
- New `content/fr/grammar.json` from the référentiels — the "no shortcuts" correctness layer,
  French specifics as mono-diagram tables: **A1** (gender + articles le/la/un/une/du/de la,
  present of être/avoir/aller/faire + regular -ER/-IR/-RE, ne…pas, question forms, adjective
  agreement, futur proche, passé composé intro); **A2** (passé composé vs imparfait, all object &
  reflexive pronouns, futur simple, comparatives, common prepositions); **B1** (subjonctif présent,
  conditionnel, relative pronouns qui/que/dont/où, pronominal verbs, discours indirect, plus-que-
  parfait); **B2** (subjonctif in nuance, gérondif/participe présent, passive, concordance des
  temps, connecteurs logiques for argumentation, register).
- Reuse `GrammarScreen`; no new code.

## Phase FD — Vocabulary → ~3,000–3,500 through B2 (longest pole; batch-parallel)

- `content/fr/vocab/` + `_index.json`, ordered A1→B2 (order = SRS introduction order):
  `00-a1-core` … `NN-b2-*`. Targets from the référentiel/Français-Fondamental digests
  (A1 ≈ 600, A2 ≈ 800, B1 ≈ 900, B2 ≈ 900).
- **Theme ledger first**: `docs/sources/vocab-coverage-fr.md` (themes × level × target counts).
- Batches of ~100, each with mandatory `fr` (correct accents, NFC), `en`, `pos` (**gender on every
  noun**, e.g. "n. m." / "n. f."; verb group), `example` sentence (A1/A2 mandatory for TTS value),
  cross-checked against a named frequency list.
- **Independent QA per batch** (verifier ≠ author): accents/gender/gloss/example agreement,
  duplicates vs the whole deck; ≥10% spot-check vs the digest; ContentValidationTest green.

## Phase FE — Plan → ~270 days A1→B2 (needs FC; parallel with FD)

- `content/fr/plan/` + `_index.json`: `phase1-a1`, `phase2-a2`, `phase3-b1`, `phase4-b2`. Audit and
  fold the useful parts of the existing 60-day seed into phase 1, upgraded to the new schema.
- Each day is a **self-contained lesson** with embedded `activities` (LEARN with items, EXERCISE
  with graded MCQ/FILL/REORDER, DIALOGUE scripts using "Me"/"Partner"), plus the day's wrap-up
  recall from its own LEARN phrases — identical to the Croatian structure. Final weeks of B1 and of
  B2 = one DELF-section drill per day + full mock exams.
- Append official French resources to `resources.json` (official path only: FEI/DELF prep pages,
  TV5MONDE Apprendre le français, RFI Savoirs / Français facile, France Éducation international,
  a conversation partner). No blogs/forums.

## Phase FF — Exam practice + DELF mocks + readiness (needs FA, FC)

- `content/fr/exams.json`: **DELF B1 mock** and **DELF B2 mock**, each modeled on the FEI sample
  subject — LISTENING (TTS passages, audio-only), READING (passages + questions), WRITING
  (OpenPrompt: forum contribution / formal letter + model answer + rubric), SPEAKING (monologue +
  interaction prompts). Score each section /25; summary applies `delfPassed`.
- `content/fr/placement.json`: A0→B2 MCQs mapping to plan days (mirrors hr placement).
- `content/fr/quizzes.json`: per-level; plus DELF-section drill quizzes.
- `ProgressScreen` readiness card already generic — surfaces per-skill can-do + best mock section
  scores vs the DELF bar.

## Phase FG — French-specific: pronunciation & listening

French rewards early phonetics work more than Croatian:
- Cheatsheet + a grammar "Sounds" topic: **nasal vowels** (on/an/in), **silent final consonants**,
  **liaison**, **é/è/ê**, the uvular R. Worked examples spoken via fr-FR TTS.
- LISTEN-mode exercises seeded from A1 (DELF's compréhension de l'oral is a full section) — the
  `audioText` field already supports this.

## Execution order & parallelism

F0 → FA → FC → **FD (continuous batches)** ∥ FE ∥ FF ∥ FG. Vocabulary batches are the long tail;
code + grammar land first so batches have a home. Author ≠ verifier subagents, each batch behind
the gate. Multi-session; the gate keeps every landed piece final-quality. Unhide French
(`availableLanguages += "fr"`) only when A1 is fully validated.

## Key risks accepted / to watch

- **DELF ≠ NN exam**: different sections and pass rule → the new `delfPassed` function + B2 model.
- **Gender is pervasive in French** and errors compound → gender mandatory on every noun, QA-checked.
- **fr-FR TTS** is device-dependent; keep the transcript-reveal fallback (already built).
- **Existing seed is thin**, not wrong — treat as audit input, not a shortcut around Phase FD/FE.
- SRS pace: ~3,200 words ÷ 10/day ≈ 320 intro-days; the 10/15/20 setting is the release valve.

## Verification (per landed piece)

1. `gradlew assembleDebug test` green (ContentValidationTest extended to fr + `delfPassed` test).
2. On-device: fr-FR TTS speaks nasal vowels/liaison; LISTEN plays with hidden transcript.
3. DELF mock end-to-end: 4 sections record attempts; summary applies 50/100 + 5/25.
4. Per-batch QA protocol (independent verifier + ≥10% spot-check) recorded in the batch commit.
5. Regression: hr unaffected; French switch works; day 1→N navigable; streak/goal-ring language-scoped.
