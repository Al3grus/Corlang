# Corlang Language Standard

**Every language must pass everything in this document before it ships.** This is the single
checklist that captures every class of defect found in the field (2026-07) plus the
research-backed AI requirements — so adding a language never depends on remembering past
lessons. When a new failure class is discovered, it gets added HERE and, wherever possible,
as an automated test.

Enforcement levels used below:
- **[AUTO]** — enforced by `ContentValidationTest` on every build. Languages are DISCOVERED
  from `assets/content/<code>/` (never a hardcoded list), so a new language enters every
  automated gate the moment its folder exists.
- **[SCRIPT]** — runnable check, not yet in the build (run before launch).
- **[HUMAN]** — requires a person; record who/when in the PR that ships the language.

---

## 1. Content package (structure)

A language is a folder `app/src/main/assets/content/<code>/` containing:

| File | Required | Notes |
|---|---|---|
| `meta.json` | yes | name, nativeName, flagEmoji, paretoSummary |
| `levels.json` | yes | CEFR levels, milestones, can-dos, exam spec on exam levels |
| `plan/` + `_index.json` | yes | contiguous days 1..N, embedded activities (LEARN/EXERCISE/DIALOGUE) |
| `vocab/` + `_index.json` | yes | deck order = SRS introduction order — it is load-bearing |
| `quizzes.json` | yes | per-level, 10 questions easy→hard |
| `exams.json` | yes | official exam format, per-section pass rules |
| `placement.json` | yes | maps score → plan day + level |
| `cheatsheet.json`, `feynman.json`, `resources.json` | yes | reference + teach-back + curated links |

- [AUTO] All files strict-parse (unknown JSON keys fail the build).
- [AUTO] Plan days contiguous 1..N; activities are complete lessons, not references.
- [AUTO] `_index.json` in sync with directory contents (order = concatenation order).
- [AUTO] Word ids globally unique, NFC-normalized; frozen ids never renamed (SRS keys on them).
- [AUTO] Every `sources` key is registered in `docs/sources/README.md` (provenance rule:
  curricula/exam material is *referenced and mapped*, never copied).

## 2. Exercise authoring rules (the answer-leak classes)

Every one of these was a real field-reported defect. All are now enforced:

- **[AUTO] REORDER prompts carry MEANING, not the answer.** The prompt must be the English
  gloss (`Build the French for: '…'`) — never the target sentence, verbatim or ≥70% of its
  tokens. (Field: "Put in order: 'Il faut que je finisse mon travail.'" — 195 cases.)
- **[AUTO] FILL answers never appear verbatim in their own prompt** (multi-word; single-word
  lemma-hint coincidences like `pet ___ (godina)` → gen.pl. `godina` are the intended task).
  (Field: a format example that WAS the answer: "npr. 'sutra u 7'".)
- **[AUTO] Wrap-up recall never mangles or leaks:** "A / B" alternatives are kept whole and
  any alternative is accepted; `headword — example` demo items recall only the headword and
  require the dash on BOTH sides; items whose gloss contains their own answer are excluded.
  (Field: "he / she is" grading against a bare "on"; "ão — não, pão, mão" demanded verbatim.)
- **Instruction text must not name the answer** ("Emphasize the object *with que*" → answer
  `que`). No automated catch for every phrasing — reviewers watch for it. [HUMAN]
- MCQ options are shuffled at display time (app-level, free); REORDER tokens display
  lowercased with edge punctuation stripped so capitals/periods can't betray position
  (app-level, free).
- Content JSON must never rely on app display tricks: write prompts as if shown verbatim.

## 3. App wiring checklist (code touchpoints per language)

Adding language `xx` requires exactly these code edits — grep each symbol:

1. `ContentRepository.availableLanguages` — add `"xx"` (display order).
2. `SpeechLocales` — TTS + speech-recognition locale mapping.
3. `ExamRules` — whole-exam pass rule if the official exam differs from existing rules
   (NN per-section / DELF ≥50-with-floors / CAPLE ≥55% average).
4. `TalkScreen.varietyRules("xx")` — **mandatory, see §4.**
5. `TalkScreen.seedGreeting/seedOpener("xx")` — **native-authored, see §4.**
6. Onboarding gender-forms copy if the language is gendered like hr.
7. Reminder copy/languages — works automatically via meta.
8. Placement: verify `setWordDeckStart` fires (placement must gate the vocab deck — field:
   Day-61 placement served day-1 words).

## 4. AI requirements (tutor chat, writing feedback, teach review)

The AI features are exam-prep tools: a variety mistake taught by the tutor is a defect of the
highest severity (field: the tutor "corrected" correct Croatian *trebam učiti* into Serbian
*trebam da učim*). Research basis in §6.

**Per-language, mandatory before the language's AI goes live:**

1. **Variety rules block** in the system prompt (`varietyRules`), containing:
   - The exact standard being taught (e.g. *standard Croatian*, *European Portuguese*).
   - **Concrete contrastive examples** of the neighbor variety's most common bleed forms,
     marked WRONG (hr: `da`+present after modals, ekavian, sr. lexicon; pt: gerund
     progressive, BR lexicon, proclisis; add per language).
   - The **anti-false-correction rule**: "if the student's sentence is already correct
     standard <variety>, do not invent a correction." (LLMs over-correct toward the dominant
     variety — this line is load-bearing.)
   - Plain-text-only output rule (chat bubbles render verbatim; no markdown).
2. **Native-authored seed exchange** (`seedGreeting` + hidden `seedOpener`): the first thing
   in every payload is correct target-variety text — a few-shot anchor (strongest measured
   lever: 5-shot lifted language consistency 86%→99%).
3. **Model tier: Sonnet-class or better for anything that teaches** (chat corrections,
   feedback, explanations). Duolingo precedent: explanation features only shipped at
   GPT-4-class accuracy. Haiku-class is acceptable only for non-teaching utility calls.
4. **Low temperature (≤0.3)** — measurably fewer wrong-language word intrusions.
5. **History trimming** in chat (seed + last ~12 messages): variety and CEFR-level adherence
   demonstrably DRIFT as conversations grow; short windows blunt it and cap cost.
6. **CEFR level restated in the system prompt** and replies kept short (2–5 sentences) —
   prompt-only level control drifts; short replies and a stated level slow that decay.

**Pre-ship AI eval gate [SCRIPT] — run per language, per model change, per prompt change:**

- Adversarial variety suite: ≥20 prompts designed to elicit the neighbor variety
  (translations of sentences whose natural rendering differs between varieties; learner
  messages containing CORRECT target-variety forms the model might "fix"; requests to
  explain grammar where the varieties differ).
- Judge each transcript with an LLM-as-judge using a **category rubric** (lexicon, verb
  constructions, orthography, clitic/word order, false corrections) — category-based LLM
  judging matches or beats human-human agreement (κ 0.81 vs 0.69) and beats fine-tuned
  variety classifiers.
- Pass bar: **zero false corrections** of correct target-variety input; zero neighbor-variety
  constructions presented as correct. Any failure → prompt iteration or model upgrade, rerun.
- For hr specifically: the Helsinki-NLP explainable BCMS classifier
  (github.com/Helsinki-NLP/explainable-bcms-classification) can provide word-level
  Serbian-marker screening as a second signal.

## 5. Human gates

- **Native-speaker content review** — full pass over lessons/quizzes/exams (review doc under
  `docs/review/`), corrections applied back to JSON. Blocker for launch. [HUMAN]
- **Native-speaker AI spot-check** — 15-minute tutor conversation by a native speaker with
  the §4 eval transcript in hand; they flag anything a checker missed. [HUMAN]
- **TTS spot-check on a real device** — names, diacritics, tricky words. [HUMAN]
- **Field test**: at least days 1–2 of the course played end-to-end on a real device by a
  real learner before announcing the language. [HUMAN]

## 6. Research appendix (why §4 says what it says)

Adversarially verified findings (deep-research run, 2026-07-16; 3-0 verification votes):

- **CEFR alignment drift**: prompted level constraints degrade over multi-turn dialogue;
  level differences shrink as conversations continue (arxiv.org/html/2505.08351v1,
  arxiv.org/html/2506.04072v2).
- **Variety default bias**: without explicit instructions most LLMs default to pt-BR over
  pt-PT (training-data imbalance); even a pt-PT fine-tuned Llama still showed BR bias
  (aclanthology.org/2026.mellm-1.23).
- **Single instruction decays**: an initial variety instruction erodes across turns 1–3 in
  weakly-aligned models — variety must be pinned continuously (system prompt every call,
  seed anchor, short windows) (ibid.).
- **Steerability scales with model strength**: newer/larger models follow variety
  instructions far better (e.g. 99.8 vs 47.7/100 across model families) — supports
  Sonnet-tier for tutoring (ibid.).
- **LLM-as-judge with category rubrics** beats fine-tuned variety classifiers and exceeded
  human-human agreement for variety evaluation (κ 0.81 vs 0.69) (ibid.).
- **Token Miss Rate** (share of output tokens outside the learner's known vocabulary)
  correlates ρ=0.78 with human comprehensibility judgments — our per-day vocab decks make
  this computable for free as a future automated check (arxiv.org/html/2506.04072v2).

Supporting (source-verified, adversarial pass cut short by session limits):

- Few-shot in-language examples: line-level language consistency 86.2%→99.0% with 5 shots
  (arxiv.org/html/2406.20052 — Language Confusion Benchmark; also the template for our
  line/word-level eval design).
- Temperature 0.0 vs 1.0: word-level wrong-language intrusions drop measurably at low
  temperature (ibid.).
- Weaker models asked for pt-PT produce *hybrid* outputs (BR grammar + partial PT lexicon) —
  the exam-penalized failure mode (aclanthology.org/2026.mellm-1.23).
- Duolingo shipped "Explain my Answer" only at GPT-4-class accuracy — one wrong term in an
  explanation teaches the error (openai.com/index/duolingo).
- Immediate vs delayed corrective feedback: no significant learning-gain difference, but
  immediate feedback scores higher on perceived effectiveness/UX — gentle in-conversation
  correction is the right default (researchgate.net/publication/391485619).

---

## New-language launch checklist (condensed)

```
[ ] Content folder complete (§1) — build green = structure + leak gates pass [AUTO]
[ ] Exercise authoring rules reviewed on a content sample (§2) [HUMAN]
[ ] Code wiring: availableLanguages, SpeechLocales, ExamRules, varietyRules,
    seedGreeting/opener, placement deck gating (§3)
[ ] AI eval suite run & passed: zero false corrections, zero variety bleed (§4) [SCRIPT]
[ ] Native content review done + corrections applied (§5) [HUMAN]
[ ] Native AI spot-check done (§5) [HUMAN]
[ ] TTS spot-check on device (§5) [HUMAN]
[ ] Field test days 1–2 by a real learner (§5) [HUMAN]
```
