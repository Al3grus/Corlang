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

- **Placement is banded and adaptive.** `placement.json` is a ladder of ability BANDS, each an
  anchor `(level, startDay)` carrying EXACTLY THREE independent items, cleared on 2 of 3. Three
  items per band is not decoration: with one item a four-option guess promotes a learner a whole
  band 25% of the time, and an advanced learner has a 34% chance of being placed too low by one
  careless tap. The three items must probe DIFFERENT things (a form choice, a meaning choice, a
  usage choice), or one remembered fact clears the band. The app binary-searches the bands, so
  ~6 to 12 items are asked whatever the learner's level.
- **[AUTO] The vocabulary deck covers the whole course: at least 2500 words.** The SRS unlocks
  `deck[0 .. lesson * newWordsPerDay]`, so at the default pace of 10 a lesson a 250-lesson course
  consumes 2500 words. Deck order IS introduction order, so top-up packs are APPENDED, never
  inserted. Faster paces (15 and 20 are offered) exhaust any finite deck sooner; that is expected,
  and the lesson then says so and turns to review only. Do not try to size a deck for 20 a lesson.
- **[AUTO] A course ships at least 250 lessons, allocated by level weight, to the level the law
  actually requires.** The old form of this rule was a flat 250-lesson floor with a suggested
  shape of A1 45, A2 55, B1 70, B2 80. That floor did its job (French and Portuguese had shipped
  at 108 and 105 lessons) but it is a VOLUME rule wearing the costume of a quality rule: two
  courses can both satisfy it while covering wildly different ground, and the suggested shape was
  backwards.

  **Levels are not equal in size.** Cumulative guided-learning hours run about 60 to 80 for A1,
  160 to 200 for A2, 350 to 400 for B1 and 500 to 600 for B2. As increments that is roughly 70,
  110, 195 and 175 hours, so **B1 alone costs about 2.8x what A1 costs**. Every Corlang course
  built before this rule front-loaded the cheap levels and thinned out exactly where the hours
  concentrate.

  **Allocate by weight, A1 1.0 : A2 1.6 : B1 2.8 : B2 2.5**, then scale the total by how far the
  language sits from English. FSI class hours to professional proficiency give the distance:
  Spanish, Italian and Portuguese 600, French and German 750, Croatian 1100.

  Scale by the **square root** of that ratio, not the ratio itself. FSI hours are dominated by
  practice and production time, while a lesson count measures how many distinct things there are
  to teach, and those do not grow at the same rate. Croatian has more syllabus (seven cases,
  aspect pairs) than Spanish, but not 1.83x more. Undamped scaling produces courses of 460
  lessons and is not defensible.

  **Every figure rounds to the nearest 5**, so the numbers stay legible and plannable.

  | Language | FSI | Mult | Target | A0 | A1 | A2 | B1 | B2 | Total |
  |---|---|---|---|---|---|---|---|---|---|
  | Spanish, Italian, Portuguese | 600 | 1.00 | B1 | 0 to 15 | 45 | 70 | 125 | – | **240** |
  | German | 750 | 1.12 | B1 | 15 | 50 | 80 | 140 | – | **285** |
  | French | 750 | 1.12 | B2 | – | 50 | 80 | 140 | 125 | **395** |
  | Croatian | 1100 | 1.35 | B1 | 15 | 60 | 95 | 170 | – | **340** |

  A0 is an onramp for scripts and sounds, added only where the language needs it, and carries no
  floor of its own.

- **[AUTO] The target level is the one the country's law requires, and never higher.** Corlang
  exists for exam preparation with legal stakes, so the finish line is set by the requirement,
  not by symmetry between courses. Verified live 2026-07-20, and worth re-verifying, since two of
  these changed recently:

  | Country | Requirement for citizenship | Corlang target |
  |---|---|---|
  | Germany | B1 (§ 10 StAG, and the settlement permit) | B1 |
  | Italy | B1 since Dec 2018 (CILS or CELI B1 cittadinanza) | B1 |
  | Croatia | B1 (commission exam, 60% to pass) | B1 |
  | Portugal | **A2** (CIPLE) | B1, with the shipped B2 kept as legacy |
  | Spain | **A2** (DELE A2) plus the CCSE civics test | B1 |
  | France | **B2 since 1 Jan 2026**, raised from B1 | B2 |

  France is the only language where B2 is load-bearing (law 2024-42, decree 2025-648, applying to
  applications filed from 2026). Portugal and Spain require only A2, so B2 work there is beyond
  the driver that justifies the course; A2 is too thin to be a product, so B1 is the floor.
  **B2 is not a target for any other language.** Real B2 comes from living in the language, not
  from an app, and a course that claims it without the hours behind it is the over-claim this
  standard exists to prevent.

- **Civics exams are out of scope.** France added a civic exam in 2026 and Spain has long had the
  CCSE. Both are sat in the target language but test knowledge of a country's institutions,
  history and culture, not language ability. Corlang teaches the language and says so; it does not
  claim to prepare the civics component.
- **Every CEFR level (A1 and up) ends in the three journey checkpoints**: a level quiz
  (`quizzes.json`, one per level), an exam readiness milestone (`exam` object on the level in
  `levels.json`: name, pass rule, section list, can-do skills), and a mock exam in the official
  format (`exams.json`, one per level). The journey renders them as ? / ☑ / flag stones after
  the level's last lesson; a missing entry silently drops that checkpoint, so author all three.
  An A0 onramp is the one exception: it ends with its quiz only (no official A0 exam exists).
  Mocks for levels below the first real certificate still use the official format and pass
  rules, labeled as practice, never claiming a certificate that does not exist at that level.
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
5a. `TalkScreen.starters("xx")` and `composerHint("xx")` — starter buttons + the input
    placeholder, both in the target language. (Field bug: these were hardcoded Croatian and
    showed on the Portuguese tutor.) Grep TalkScreen for any hardcoded target-language string
    — every learner-facing string there must be keyed by `lang`.
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
3. **Model tier is PER-LANGUAGE, gated by the eval (§4), not assumed.** Run the gate on the
   cheap model first; only escalate the languages that fail. Findings (2026-07-17):
   - **hr → Sonnet 5 WITH thinking.** Haiku bled into Serbian (~30% fail); thinking-disabled
     Sonnet slipped on adversarial "explain 'da'" prompts. Only Sonnet + reasoning passes
     12/12 consistently. This is the expensive path — accepted because Croatian variety is
     exam-critical.
   - **pt, fr → Haiku 4.5.** Both pass 12/12 (higher-resource, no self-contradiction trap).
     Haiku doesn't think by default → already the cheap path; do NOT send it
     `thinking:{type:disabled}` (older models 400 on that; they use `budget_tokens`).
   - Model choice lives in `TalkScreen.send()` (`lang == "hr"` → FEEDBACK_MODEL else DEFAULT_MODEL).
     `AiClient.complete(disableThinking=)` disables Sonnet's adaptive thinking (Sonnet accepts
     `{type:disabled}`; Fable family 400s) — verified, currently unused by chat because hr needs
     the reasoning and Haiku is already thinking-free.
   Duolingo precedent: explanation features only shipped at GPT-4-class accuracy.
4. **Low temperature (≤0.3)** — measurably fewer wrong-language word intrusions.
5. **History trimming** in chat (seed + last ~12 messages): variety and CEFR-level adherence
   demonstrably DRIFT as conversations grow; short windows blunt it and cap cost.
6. **CEFR level restated in the system prompt** and replies kept short (2–5 sentences) —
   prompt-only level control drifts; short replies and a stated level slow that decay.
7. **Prompt caching does NOT apply at current prompt sizes.** Min cacheable prefix is 2048
   tokens (Sonnet 5) / 4096 (Haiku); the tutor system prompt + seed is ~600 tokens, so
   `cache_control` silently caches nothing. Revisit only if the shared prefix ever grows past
   the floor (e.g. large few-shot blocks). Don't add `cache_control` speculatively — it's a
   1.25× write with zero reads at this size.

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

Deep-research run **completed 2026-07-17** (105 agents, 25 claims verified, **0 refuted**,
14 findings after synthesis). Nothing here contradicts what the app implements; the empirical
`ai-variety-eval.py` gate (§4) remains the authority for our specific Claude tiers, since **no
benchmark tested Sonnet 5 / Haiku 4.5 on variety fidelity** — every Portuguese-variety number
below is from other model families (Gemini/Gemma/Llama/EuroLLM/AMALIA), so they inform but do
not decide our config.

Verified findings (3-0 unless noted):

- **CEFR alignment drift**: prompted level constraints degrade over a dialogue; A1/B1/C1-prompted
  levels converge within ~5–9 turns. Prompt-only level control is brittle → re-anchor per turn.
  (arxiv 2505.08351, 2506.04072)
- **Variety default bias**: LLMs default to the dominant variety (pt-BR over pt-PT); fine-tuning
  alone is insufficient (a pt-PT Llama still leaned BR). The Croatian↔Serbian case is the direct
  analogue. (P3B3 / MeLLM 2026, AMALIA report)
- **Single instruction decays over turns 1–3** in weakly-aligned models → pin variety
  continuously (system prompt every call + seed anchor + short window). (P3B3 §5.2)
- **Model strength helps but data dominates**: explicit prompting nearly eliminates bleed for
  strong families (Gemini-3-Flash 63.7→99.8) but not weak ones (Llama 47.7–67.8); and a targeted
  9B (AMALIA) beat a general 12B (Gemma). So a *fast* model isn't inherently worse at variety —
  which is exactly why our gate, not model size, decides per language. (P3B3, AMALIA)
- **Generation is the unsolved half**: models comprehend minority varieties far better than they
  produce them; even frontier models score <70% on dialect ID (DialectLLM tested Haiku-4.5 +
  Sonnet-4). (arxiv 2607.07669, 2601.22888)
- **Hybrid-output failure mode**, ready-made validator checklist: weak pt-PT output mixes BR
  grammar (proclisis, gerund vs `estar a`+inf, `você`) with partial EP lexicon + inconsistent
  orthography — mirrors our `varietyRules` WRONG-lists. (P3B3 §5.3)
- **Three-part pre-ship eval that can disagree**: (a) automated variety classifier, (b) adversarial
  provoking-prompt suite, (c) blind native-speaker pairwise rubric. Our stack has (b)+(c-lite via
  LLM judge); (a) for Croatian exists off-the-shelf — **Helsinki-NLP BERTic/XLM-R BCMS classifiers**
  (sentence-level B/C/M/S). Caveat: Twitter-domain, multi-label-ambiguous → a screening signal, not
  a gate. (DiaLLM; Miletić et al. 2025)
- **LLM-as-judge with category rubrics** ≈ or beats human agreement for variety eval (κ 0.81) —
  what our `ai-variety-eval.py` judge does. (2-1; P3B3)
- **Token Miss Rate** (share of output tokens above the learner's vocab level) ρ=0.78 with human
  comprehensibility; traditional readability ≈0 — computable for free from our per-day decks as a
  future level-check. (arxiv 2506.04072)
- **Stronger model materially worth it for one-shot teaching feedback**: Duolingo shipped "Explain
  my Answer" only at GPT-4-class accuracy — one wrong term teaches an error. Supports Sonnet for
  exam-writing feedback + teach-review. (Duolingo/OpenAI case study)
- **Correction timing is a wash for learning** (no significant gain, immediate vs delayed) but
  immediate scores higher on UX — gentle in-conversation correction is the right default.
  (Frontiers in Education 2026, n≈54–66, English-only)
- Few-shot in-language exemplars: line-level language consistency 86%→99% with 5 shots — the
  basis for our native seed greeting as a few-shot anchor. (Language Confusion Benchmark, 2406.20052)

Open questions the literature does NOT answer for us (our gate/field-testing must):
1. How much Sonnet 5 / Haiku 4.5 specifically drift on pt-PT and standard-Croatian over a tutor
   chat, and whether per-turn re-anchoring measurably helps. (We re-anchor by design.)
2. A Croatian-vs-Serbian validator for tutor-length PROSE (the BCMS classifier is Twitter-domain).
3. The cost/quality crossover for THIS app — answered empirically: hr needs Sonnet, pt/fr pass on
   Haiku (§4).
4. Whether the timing-is-a-wash result holds for variety/register errors (study was general grammar).

---

## 0. Research FIRST, author second

Before a single lesson is written for a new language, research online, fresh, never from
memory:

1. **The official curriculum** — the state or council framework the language's public
   education and certification actually follow (the analogue of Croatia's NN decisions,
   France's CECRL référentiels, Portugal's Camões referencial). Lessons map to it; it is what
   makes "built on official curricula" true.
2. **The official exam, in its current form** — sections, timing, pass rules, who sits it and
   why (citizenship, residency, university). Mock exams reproduce THIS structure, quizzes
   drill its section formats, and the B-level goal is defined by it.
3. **Verify everything live** — exam formats and fees change year to year, and a dead
   reference has shipped once already (a training-data YouTube link that no longer existed).
   Sources get registered in docs/sources/README.md and referenced, never copied.

Only then does §1 authoring begin, with the plan designed around that exam as its finish line.

## 7. Voice, wording and boundaries (field sweep 2026-07-18/19)

Every rule here came out of a real defect found while dogfooding the shipped courses. The two
mechanically-checkable ones are enforced by ContentValidationTest and marked [AUTO]; the rest
are authoring discipline a new language must follow from its first line.

- **[AUTO] App-only content: a lesson NEVER sends the learner elsewhere to study.** No URLs,
  no course sites, no sign-in instructions, no named institutions (exam bodies included), no
  competitor apps — anywhere in learner-visible content. `resources.json` (Profile →
  References) is the ONE sanctioned home for external material. Named-media immersion habits
  ("watch the evening news") are fine; courses/sites/apps are not. Runtime backstop:
  SessionPlayer's `isExternal` filter drops any drill that slips through.
  (Field: lesson 1 of Croatian opened with "Sign in at a1.ffzg.unizg.hr and do Unit 1" —
  the entire hr plan had been authored around an external e-course; 300+ references purged.)
- **[AUTO] Course positions are "lesson N", never "day N"** — in content text exactly as in
  the UI. Learners do not necessarily study daily. Calendar durations keep "day" ("a 7-day
  streak"); they put the number first and the gate never matches them.
- **The exam is described generically.** What passing requires (pass rules, section shapes)
  is exam knowledge and belongs in content; WHO administers it and where to register does not.
  (Field: a quiz whose correct answer was an institution's course URL.)
- **Pro-drop languages: write recall/FILL targets either bare or pronoun-ful, and add the
  other form to `accepted` ONLY when it is grammatical.** The grader already accepts
  gloss-licensed pronoun variants ("I work" → both "radim" and "ja radim") and refuses
  wrong-person pronouns. The trap is clitics: never author a naive pronoun-prepend across a
  second-position clitic ("Šaljem ti poruku" + ja must become "Ja ti šaljem poruku", so no
  variant is added at all). French is not pro-drop; write full sentences.
- **Claims made to the learner must match shipped content.** "A mock exam after every level"
  was once false and had to be reworded; since 2026-07 the every-level checkpoint rule (§1)
  makes it true by construction, and it must STAY true: adding a plan level without its quiz,
  readiness milestone and mock re-breaks the claim. Before writing any coverage claim, count
  the files.
- **No em or en dashes in learner-visible strings.** Commas, or split the sentence. Hyphens
  inside compounds ("spaced-repetition") are fine.
- **Strictness is explained in plain words, not jargon.** The answer box says "Write your
  answer"; never "(diacritics count!)" — a beginner does not know the word.
- **Collect nothing a feature does not consume.** Onboarding asks course, name (used by the
  reminder greeting + tutor prompt), word forms, pace, level — and nothing else. The
  where-are-you-from step died because nothing read it. A new language adds NO profile
  questions unless something consumes the answer the day it ships.

## New-language launch checklist (condensed)

```
[ ] Curriculum + official exam researched ONLINE and current (§0) — plan designed
    around the real exam before any authoring [HUMAN]
[ ] Content folder complete (§1) — build green = structure + leak gates pass [AUTO]
[ ] Exercise authoring rules reviewed on a content sample (§2) [HUMAN]
[ ] Voice/boundary rules hold (§7): app-only content + lesson-not-day pass [AUTO];
    exam described generically, claims counted against files, pro-drop accepted
    variants grammatical, no dashes, no unconsumed data [HUMAN]
[ ] Code wiring: availableLanguages, SpeechLocales, ExamRules, varietyRules,
    seedGreeting/opener, placement deck gating (§3)
[ ] AI eval suite run & passed: zero false corrections, zero variety bleed (§4) [SCRIPT]
[ ] Native content review done + corrections applied (§5) [HUMAN]
[ ] Native AI spot-check done (§5) [HUMAN]
[ ] TTS spot-check on device (§5) [HUMAN]
[ ] Field test lessons 1–2 by a real learner (§5) [HUMAN]
```
