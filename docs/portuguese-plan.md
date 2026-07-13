# Corlang Portuguese (European, pt-PT): Exam-Validated Content Plan (A1 → B2)

## STATUS (2026-07): IN PROGRESS

- [x] **PT-0** — official anchors verified live (CAPLE ladder + ≥55% Suficiente rule;
      Português Fundamental 2,217-word core; Referencial Camões PLE); digests in
      `docs/sources/` (caple.md, portugues-fundamental.md, referencial-camoes.md); source
      keys registered.
- [ ] **PT-A** — code wiring: SpeechLocales pt→pt-PT, meta.json, reminder/settings/splash pt
      branches, `ExamRules.caplePassed` (≥55% average) + wiring, gate extension (guarded).
- [ ] **PT-B** — levels.json (QECR + CAPLE milestones) + grammar.json (~38 topics, European
      specifics: ênclise/próclise, estar a + inf, PPS vs PPC, conjuntivo incl. futuro do
      conjuntivo, infinitivo pessoal).
- [ ] **PT-C** — vocabulary ~2,500–2,900 words A1→B2, split packs + `_index.json`, gender on
      every noun, examples, frozen ids (`frozen-word-ids-pt.txt`), **Brazilianism blocklist**
      enforced by the integrator.
- [ ] **PT-D** — plan ~105 days (phase1-a1, phase2-a2, phase3-b1, phase4-b2), self-contained
      LEARN/EXERCISE/DIALOGUE days ending in CAPLE mock days.
- [ ] **PT-E** — placement (14Q A0→B2), quizzes (A1–B2), exams.json (pt-b1-deple-mock,
      pt-b2-diple-mock), cheatsheet (European phonetics: vowel reduction, nasal ão/õe/ãe,
      lh/nh, s/z sandhi, open vs closed e/o), feynman (~9 concepts), resources.json (official
      only: Instituto Camões, CAPLE modelos, Português Fundamental, RTP Ensina).
- [ ] **PT-F** — audits (answer-in-prompt leaks, Brazilianisms, noun genders), add "pt" to
      `ContentRepository.availableLanguages` + gate strict list, release.
      **The user IS the native reviewer** (native pt-PT speaker) — review happens in-app
      after ship.

## Positioning

Every major platform teaches Brazilian Portuguese; Corlang teaches **European Portuguese**
properly — European lexis, ênclise by default, estar a + infinitivo, tu/você/o senhor register,
and the CAPLE exam path (CIPLE A2 → DEPLE B1 → **DIPLE B2** as the job-proficiency target).
This is a differentiator none of the majors offer.

## Anchors (verified live 2026-07, digests in docs/sources/)

- **CAPLE** (caple.letras.ulisboa.pt): exam ladder, 4-component structure, pass = Suficiente
  ≥55% global (no per-section floor) → `ExamRules.caplePassed`, exam ids `*deple*`/`*diple*`.
- **Português Fundamental** (CLUL 1984): 2,217-word spoken-corpus core + 30 availability
  themes → deck ledger.
- **Referencial Camões PLE** + QuaREPE: per-level grammar/theme inventories → grammar.json
  and plan syllabus.
- **QECR** (CEFR): can-do descriptors per level.

## Mechanics (mirrors the French build exactly)

Same architecture, same quality gate, same "official-anchors-only" rule as hr/fr: content as
JSON under `assets/content/pt/` (meta, levels, grammar, vocab/ split packs, plan/ split
phases, placement, quizzes, exams, cheatsheet, feynman, resources), `ContentValidationTest`
as the permanent gate, frozen word ids, authoring via parallel subagents + an integrator
script (`pt_integrate.py`, adapted from `fr_integrate.py`) that additionally rejects
Brazilianisms (ônibus, trem, celular, café da manhã, banheiro, sorvete, geladeira, açougue,
esporte, time, aeromoça…). Word model note: the `hr` JSON field holds the TARGET-language
text (legacy field name), so pt words put Portuguese in `hr`.

Deck targets: A1 ≈ 700, A2 ≈ 750, B1 ≈ 600, B2 ≈ 550 (≈2,600 total; ≥ the 2,217 Básico core).
Plan targets: ~105 days ≈ A1 30 / A2 30 / B1 25 / B2 20, each day LEARN + EXERCISE + DIALOGUE
with a wrap-up recall; final week of B1 = DEPLE drills + mock, of B2 = DIPLE drills + mock.
