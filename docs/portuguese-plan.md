# Corlang Portuguese (European, pt-PT): Exam-Validated Content Plan (A1 → B2)

## STATUS (2026-07): CONTENT COMPLETE — shipped behind the standard gate; in-app native review (the user) ongoing

- [x] **PT-0** — official anchors verified live (CAPLE ladder + ≥55% Suficiente rule;
      Português Fundamental 2,217-word core; Referencial Camões PLE); digests in
      `docs/sources/` (caple.md, portugues-fundamental.md, referencial-camoes.md); source
      keys registered.
- [x] **PT-A** — code wiring: SpeechLocales pt→pt-PT, meta.json, reminder/settings/splash pt
      branches, `ExamRules.caplePassed` (≥55% average) + wiring, gate extension (guarded).
- [x] **PT-B** — levels.json (QECR + CAPLE milestones) + grammar.json (41 topics, European
      specifics: ênclise/próclise, estar a + inf, PPS vs PPC, conjuntivo incl. futuro do
      conjuntivo, infinitivo pessoal).
- [x] **PT-C** — vocabulary 2,300 words A1→B2 (28 packs), split packs + `_index.json`, gender
      on every noun, examples, frozen ids (`frozen-word-ids-pt.txt`), **Brazilianism blocklist**
      enforced by the integrator.
- [x] **PT-D** — plan 105 days (phase1-a1 A1 1–30, phase2-a2 A2 31–60, phase3-b1 B1 61–85,
      phase4-b2 B2 86–105), self-contained LEARN/EXERCISE/DIALOGUE days; day 60 = CIPLE
      checkpoint, days 81–85 = DEPLE drills + mock, days 101–105 = DIPLE drills + mock.
- [x] **PT-E** — placement (14Q A0→B2), quizzes (pt-a1..pt-b2, 8Q each), exams.json
      (pt-b1-deple-mock, pt-b2-diple-mock: 4 CAPLE components, Suficiente ≥55% global),
      cheatsheet ("O português europeu numa página", 10 phonetics/orthography sections),
      feynman (9 concepts), resources.json (Camões, CAPLE, Practice Portuguese, RTP Ensina,
      Priberam — every URL verified live at authoring time).
- [x] **PT-F** — audits passed (answer-in-prompt leaks 0, Brazilianisms 0, all 1,218 nouns
      carry gender), "pt" added to `ContentRepository.availableLanguages` + the test-gate
      strict list. **The user IS the native reviewer** (native pt-PT speaker) — review
      happens in-app now that it ships.

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
