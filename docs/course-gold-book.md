# The Gold Book: building and auditing a Corlang language course

This is the canonical workflow for adding a language to Corlang, or auditing one already
shipped. It compiles everything learned building Croatian, French, Portuguese, German and
Italian, including every mistake, so none repeats. `docs/language-standard.md` remains the
contract of WHAT a course must contain; this book is HOW to produce one that passes it, in
order, with the failure that motivated each rule attached.

Corlang's promise is exam preparation with legal stakes: citizenship and work-proficiency
language exams, taught to the level the law of that country actually requires. Every decision
below serves that promise. A course that merely resembles a language course is a failure even
if every automated gate is green.

The tools live in `tools/course/` (moved out of session scratchpads after nearly losing them):
`LESSON_SPEC.md`, `PLACEMENT_SPEC.md`, `check_batch.py`, `check_de.py`, `check_it.py`,
`build_language.py`, `fix_resources.py`, `proctor.py`, and `de_retired.txt` as the model
retire list.

---

## Phase 0 — Research, verified live (blocking; nothing is authored before this)

**Why it is first:** research overturned the plan three separate times. France raised
naturalisation from B1 to B2 on 2026-01-01, making French the one course where B2 is
load-bearing. Portugal turned out to require only A2, so the Portuguese B2 level was three
levels above its own driver and was hidden. CILS B1 turned out to be modular at 55% per
ability while CELI is global, which changed which pass rule the code implements. None of that
was in anyone's head; all of it came from fetching official pages the same day.

1. **The legal requirement** for citizenship / settlement / work in that country, from official
   or primary sources. This SETS THE TARGET LEVEL. Never target higher than the law requires
   (French B2 is the only current exception, because the law requires it). Civics exams (the
   French examen civique, the Spanish CCSE) are out of scope: they are sat in the language but
   test knowledge of the country.
2. **The exam family**: which certificates the state accepts, every section, its timing, and
   the PASS RULE stated numerically, with "modular" (every section on its own) vs "global"
   (averaged) made explicit. This drives `ExamRules` and the `passPercent` values in
   `exams.json` directly.
3. **The official syllabus and word lists** (Goethe Wortliste, CILS sillabo, Profilo della
   lingua italiana, Instituto Cervantes Plan Curricular): FETCH THEM. They are what the deck's
   level banding and the topic sequence must be checked against in Phase 8.
4. **A frequency reference** for the deck.

Deliverables: a dated digest in `docs/sources/<lang>-exams.md`, keys registered in
`docs/sources/README.md` AND in `ContentValidationTest.knownSourceKeys`. If an official page
cannot be fetched, SAY SO IN THE DIGEST rather than papering over it; the Italian digest's
flagged gaps are the model.

**The provenance rule, learned the hard way:** a `sources` key on content asserts that the
content was checked against that source. Early German and Italian content cited
`goethe-wortliste` and `freq-it` without anyone ever opening those documents, which is an
overclaim at the scale of hundreds of lessons. A key is EARNED by the Phase 8 cross-check,
not by being plausible.

## Phase 1 — Course shape

- Target level from Phase 0. Lesson counts from the weighted floor table in
  `docs/language-standard.md` (A1 1.0 : A2 1.6 : B1 2.8 : B2 2.5, scaled by the square root
  of the FSI hour ratio, every figure rounded to the nearest 5). The floors are enforced
  per-language in `ContentValidationTest.levelFloor`; a new language must add its row or the
  gate fails it by name.
- A0 onramp only where the sound system or script needs one (German yes, Italian no).
- Deck size = lessons × 10 (the SRS pace), and the authoring budget must EXCEED it by at
  least 15% before deduplication: parallel authors cannot see each other and German lost 376
  words to cross-batch duplicates.
- Write the complete topic sequence into `plan/TOPICS*.md` in the build directory BEFORE any
  lesson is authored. Sequencing rule: nothing is used before it is taught; name the
  keystone dependencies explicitly (the Satzklammer before separable verbs, the auxiliary
  split before narration, the congiuntivo before opinion topics).
- Every level ends with quiz → readiness → mock exam (the journey checkpoint rule); A0 ends
  with its quiz only.
- Author in `scratchpad/<code>-build/`, NEVER in `assets/content/`, because the gates discover
  languages from that directory and a half-built folder turns the build red. Record the build
  path in `docs/new-languages-plan.md` immediately: scratchpads survive sessions but are
  invisible to the next session unless written down.

## Phase 2 — Identity files and code wiring (language still absent from `availableLanguages`)

Seven content files: `meta.json`, `levels.json` (with the `exam` object on every real level,
passRule in the target language, facts from the digest), `cheatsheet.json`, `feynman.json`,
`grammar.json`, `resources.json`, and the `plan/TOPICS*.md` from Phase 1. `grammar.json` is
optional in code but every shipped language has one; German "discovered" this at integration.

**resources.json before lessons, names frozen.** Verify every URL live; a resource that cannot
be fetched does not ship (the dead-link rule exists because a dead YouTube link shipped once).
Then freeze the exact `name` strings and hand THOSE to every lesson agent verbatim. Both
German and Italian had lesson batches referencing resource names that no longer existed after
a URL failed verification and its entry was renamed. The assembler now hard-fails on this, but
the fix is to not create the mismatch.

Code touchpoints, all keyed by language and inert until the final phase:
- `SpeechLocales`: locale AND tag, region pinned deliberately (pt-PT, de-DE) with a comment
  saying why.
- `Reminder.kt`: THREE branches (language name, in-language title, proverb). German shipped
  its first wiring pass without this and the nudge would have greeted a German learner in
  Croatian.
- `TalkScreen`: starters, composer hint, seed greeting, seed opener, varietyRules, AND
  `TUTOR_LANGS`. Forgetting `TUTOR_LANGS` means `assertTutorLangRegistered` throws on first
  Tutor open in a debug build; that assert exists precisely to catch shipping on the English
  fallback, and it nearly caught us.
- `ExamRules` + tests, only if the exam family's pass rule is genuinely new. Check first
  whether an existing rule expresses it: CILS's per-section 55% needed no new function because
  `examPassed` over sections with `passPercent: 55` says exactly that.
- Onboarding gender-forms copy if the language is gendered.

Commit. The build stays green because no content folder exists yet.

## Phase 3 — The per-language validator, negative-tested before use

Write `tools/course/check_<code>.py` layered on `check_batch.py`, covering the ways a machine
author drifts in THIS language: variety (Austrian/Swiss forms, Brazilian forms, regionalisms),
orthography that changes meaning (ß/ss, Italian accents and double consonants, e' for è),
off-syllabus grammar (passato remoto below B1).

Three design rules, each paid for:
- **Scope by key.** Check only the strings the learner is taught to produce (`hr`, example
  `target`, `answer`, `accepted`, `options`, `ordered`), never English commentary. The German
  checker went through four rounds of exemption-patching because it scanned English notes that
  print a wrong form in order to reject it; scoping by key ended that class of false positive
  in one move.
- **Exempt wrong MCQ options, not answers.** A lesson teaching against a form must print it as
  a distractor. The same form as the ANSWER is a real defect and must still fire.
- **Negative-test with planted defects and keep the fixture.** Every checker found real bugs
  in its own first draft (the English gloss "January" silently excusing "Jänner"; "Sara" the
  name flagged as an unaccented verb). A checker that has never failed a planted defect is
  not known to check anything, and after any loosening the fixture proves it still fires.

## Phase 4 — Vocabulary deck

- SRS introduction order. Pack 00 is genuine first contact; frequency-first.
- Nouns carry their article in `hr` AND `id` (gender is unlearnable otherwise); days and
  months are the standing exception in Italian-type languages. Verbs carry the auxiliary,
  irregular forms, separability and governed preposition WITH CASE in `note`.
- Parallel authors get disjoint slices AND an exclusion list of existing ids. **Verify the
  exclusion file exists before dispatching the agent**: one never got written because it was
  chained after a validator with `&&` and the validator exited non-zero; the agent noticed and
  reconstructed it, but only because it was told the file was the authority. Never chain a
  file write behind a validator.
- Re-verify every delivered pack independently: id = lowercase NFC of `hr`, article/gender
  cross-check (the der/die/das prefix must match the declared `pos`), pos vocabulary, example
  presence, dash scan. Do not take the agent's own validation as the verification.

## Phase 5 — Lessons

- Batches of 10 to 27 through parallel agents, each prompt carrying: the spec path, the exact
  SEQUENCING inventory (what is known by this point, what is banned because not yet taught), the
  frozen resource strings, difficulty band, per-language hard rules, and content notes for
  anything factual.
- **Agents write their output file BEFORE reporting.** Session limits killed 7 of 9 German B1
  agents and all 3 final Italian agents mid-run; in the German case every file survived
  because writing precedes reporting. This pattern has saved multi-hour reauthoring twice.
- Validate every batch the moment it lands with `check_<code>.py`. Fix small defects in place
  (a two-token REORDER, an e' for è); the checkers catch them one at a time when they are
  cheap.
- Real-world procedure claims (sick-leave rules, holiday minima, licence exchange) get
  LISTED as they are authored, for the Phase 8 fact audit. Prefer describing how a process
  works over quoting figures that change.
- Retiring lessons (rebalances): never delete authored files. Use a title-based retire list
  (`de_retired.txt` is the model) with a reason per entry, applied by the assembler, which
  hard-fails if a listed title stops matching. Retire only thematic/practice lessons whose
  content survives elsewhere at greater depth; never retire a grammar lesson.

## Phase 6 — Assessment set (AFTER the plan is final, never before)

- **Placement is authored LAST.** German's placement bands were written against the
  pre-rebalance plan and every anchor past A1 pointed at the wrong level. Bands: 3 items
  each, `startDay`/`level` pairs taken from the FINAL plan, difficulty climbing across bands,
  every item decidable in seconds with one defensible answer.
- Quizzes: one per level including A0, ~10 questions, sampling the load-bearing grammar of
  that level.
- Mock exams in the official format, all texts fresh, never imitating real items, provider
  named only in `sources`. The `passPercent` semantics are load-bearing: `null` on every
  section of a GLOBAL exam (the app's rule function averages), the required percentage on
  every section of a MODULAR exam (`examPassed` then demands each section pass). Exam FILL
  questions need `strictDiacritics: true` — the gate enforces it and German lost a test run
  to it. Listening sections carry `audioOnly: true` transcripts written as things genuinely
  heard. Writing/speaking sections carry `prompts` with `modelAnswer` and a rubric of 4 to 6
  objectively tickable points; they self-assess, so a modular `passPercent` is safe on them.

## Phase 7 — Assembly

`python tools/course/build_language.py <build-dir> --title ... --retire ... --lessons ...
--vocab ...` It enforces: contiguous days, week numbers, one contiguous block per level in
ladder order, vocab packs sorted to ladder order regardless of authoring file (top-ups land
late but must not introduce A2 words after B1), global vocab dedup keeping first introduction,
retire-list loud failure, resource-name gate, dash gate. Trust its failures; every one was a
shipped or nearly-shipped bug.

## Phase 8 — Proctoring (the full audit; nothing ships without it)

This phase exists because green structural gates measure less than they seem to. Run it on the
assembled build BEFORE integration, and on shipped languages as an audit.

**8a. Mechanical sweep:** `python tools/course/proctor.py <build-dir>` catches what per-file
checkers cannot: a day's objective restated verbatim in its drills or intros (the Portuguese
lesson-1 "In this lesson you will / You will recognize" duplication is the founding example),
stamped-out instructional boilerplate across lessons (ritual app-feature nudges like the
Words-tab reminder are exempt BY DESIGN), duplicate taught sentences across days, quiz/exam
prompts colliding with lesson prompts (testing memory of the lesson, not the language), MCQ
answers appearing in their own prompt, and longest-option-is-answer bias above 55% (a course
guessable without knowing the language). Fix everything it flags; re-run until clean.

**8b. Syllabus cross-check (this is what EARNS the `sources` keys):** diff the deck and the
topic sequence against the official word list and syllabus fetched in Phase 0. Two questions,
both ways: what does the official inventory require that the course lacks, and what does the
course teach that is off-level. Record the diff and the fixes in the language's digest. If the
official list was unfetchable, the digest must say the banding is by internal judgement, and
the content must NOT cite the list as a source.

**8c. Language quality audit, adversarial:** fan out reviewer agents with a REFUTATION stance
(find errors, do not confirm correctness) over every batch: grammar and naturalness of every
`hr` sentence, agreement, register, level-appropriateness, dialogue plausibility, explanation
correctness (does the explanation state the actual rule), and translation fidelity of every
gloss. One reviewer per batch, plus a second pass on everything the first pass flagged.

**8d. Fact audit:** every real-world procedure claim listed in Phase 5 gets verified live or
softened to a description of how the process works.

**8e. Exam fidelity:** compare each mock against the official model exam (Modellsatz, prova
d'esame) section by section: task types, item counts, timing note, pass rule. Structure only,
never content.

**8f. App-implementation comparison:** run the FULL Kotlin gate suite with `--rerun-tasks`
(the test task does not track assets as inputs; without the flag it silently reuses stale
results). Then hand-check on device or via review: the journey renders lesson → ? → ☑ → flag
per level, placement lands where it claims, the Tutor opens in the right variety, TTS speaks
the right locale, and typed grading treats diacritics as the course teaches them.

**Honesty boundary:** 8a–8f is designed to catch what a native reviewer would catch, and it is
what lets a course ship. Anything a check cannot decide gets FLAGGED in the language's review
doc, not passed silently. The native-speaker pass (Track D) remains the final quality claim;
the gold book's job is to make that pass find nothing.

## Phase 9 — Integration and ship

1. Copy `<code>-build/` into `assets/content/<code>/` (strip `TOPICS*.md`).
2. Add the code to `availableLanguages`. Wrap any new per-language screen in `key(lang)` (the
   cross-language stale-frame bug: per-language screens keep composition state across a
   switch).
3. Full gate suite `--rerun-tasks`. Expect the real gates to catch what the Python mirrors
   did not; fix, never bypass.
4. Update the progress table and remove the language from any debt map.
5. Commit content and wiring together; then the release flow (versionCode/versionName,
   `releases/version.json`, `assembleSideloadRelease`, copy, push).

---

## The error loop (applies to every phase, and is how this book improves)

`docs/error-registry.md` is the list of every defect class ever found, its automation, and its
sweep status per language. The lifecycle is mandatory and happens in the SAME commit as any
fix: **register → scope → sweep every language → automate at the strongest layer → encode the
prevention here.** An error without a registry row is not fixed, it is postponed; an error
fixed where it was found but not swept elsewhere is a bug you have chosen to keep in three
other languages. The registry's founding example: the dash rule was "fixed" twice and a sweep
still found ~40 dash-bearing strings in UI Kotlin.

When building a NEW language, the registry doubles as its pre-flight: every V-row is a
candidate check for the new `check_<code>.py`, every C-row already guards it through the
shared tools, and every P-row is a process trap the build must not repeat.

## Standing rules that apply to every phase

- No em dashes, no en dashes, anywhere learner-visible. Commas, or split the sentence.
- The target-language text key is `"hr"` in EVERY language. `"de"`, `"it"`, `"es"` fail the
  strict parser.
- Never send the learner outside the app; external material exists only in `resources.json`.
- English for instructional text; the target language for `hr` fields, activity titles,
  dialogue lines.
- Position words say "lesson", never "day N".
- No named companies, platforms, apps, parties, politicians, or institutions in lesson text;
  institutions of state are allowed where they are the subject matter. Invented proper nouns
  (a fictional firm answering the phone) are acceptable where the convention needs a name.
- Difficulty bands: A0 1–4, A1 3–5, A2 4–6, B1 5–8, B2 7–10.
- Every content unit cites only sources it was actually checked against (Phase 8b).
- Commit at every phase boundary; the session limit is a pause, never a loss, but only if the
  work is on disk and the build path is written down.
