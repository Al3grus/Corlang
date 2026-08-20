# Corlang lesson authoring spec (read this fully before writing anything)

You are authoring lesson days for Corlang, an offline-first CEFR language course app.
You will be told: the LANGUAGE, the CEFR LEVEL, the PHASE string, the OUTPUT FILE, and a
numbered list of TOPICS. Author exactly one day object per topic, in the given order.

## Output

Write ONE json file at the given path: a JSON array of day objects (no wrapper object).
UTF-8, 1-space indent, LF newlines, trailing newline. No comments, no trailing commas.
Do not create any other file. Do not modify repo files.

## Day object schema (every key required unless marked optional)

```json
{
  "title": "Les adjectifs possessifs",
  "objective": "One sentence in ENGLISH: what the learner can do after this lesson.",
  "paretoFocus": "One sentence in ENGLISH: why this is high-leverage for real use.",
  "drills": ["3 short ENGLISH task lines the learner does offline"],
  "reviewBlock": { "minutes": 15, "items": ["3 ENGLISH recall prompts"] },
  "activities": [ LEARN, EXERCISE, DIALOGUE ],
  "day": 0,
  "week": 0,
  "phase": "<PHASE STRING GIVEN TO YOU>",
  "level": "<LEVEL GIVEN TO YOU>",
  "resources": ["<RESOURCES ARRAY GIVEN TO YOU, verbatim>"]
}
```

`day` and `week` are ALWAYS 0: the merge tool assigns real numbers. Do not compute them.

### Activity 1 — LEARN (exactly 5 items)

```json
{
  "type": "LEARN",
  "title": "<short title in ENGLISH>",
  "intro": "2 to 3 sentences in ENGLISH explaining the rule/pattern plainly.",
  "items": [
    { "hr": "<ONE word or ONE phrase in the TARGET language, exactly what the learner should produce>",
      "en": "<English gloss, specific enough to have exactly one right answer>",
      "note": "<optional short English note: the rule, the source form, an example>" }
  ],
  "sources": ["<SOURCES GIVEN TO YOU>"]
}
```

**These items become the lesson's closing test.** The app builds the wrap-up by asking the
learner to TYPE the `hr` for each `en` (`Drills.kt`, `wrapupRecallPhrases`) — so every item is
a question whether you wrote it as one or not. Two consequences, both non-negotiable:

- `hr` is ONE producible thing. Never a relation between two forms (`kava -> kavu`), never a
  list (`dvjesto, tristo, petsto`), never a letter plus an example (`c, caj`). When a rule is
  about a transformation, the item is the RESULT and the source form goes in `note`:
  `{"hr": "kavu", "en": "coffee, as the object of a verb", "note": "From kava. Feminine -a
  becomes -u: Pijem kavu."}`
- `en` must identify one answer and must not contain the answer. Two items glossed the same way
  in one day is an unanswerable question, and a gloss that spells its own answer (including
  phonetically, "man / person (CHOH-vyek)") is a leak.

NOTE the legacy key name: the target-language text field is called `"hr"` in EVERY language
(French and Portuguese included). Using `"fr"` or `"pt"` fails the strict parser.

### Activity 2 — EXERCISE (exactly 5 questions: 2 MCQ, 2 FILL, 1 REORDER)

```json
{
  "type": "EXERCISE",
  "title": "<short title in the TARGET language>",
  "intro": "One ENGLISH sentence saying what to practise.",
  "questions": [
    { "type": "MCQ", "prompt": "...", "difficulty": 5, "options": ["a","b","c","d"],
      "answer": "b", "explanation": "Why b is right, in English." },
    { "type": "FILL", "prompt": "...", "difficulty": 5, "answer": "...",
      "accepted": ["optional alternative spellings"], "explanation": "..." },
    { "type": "REORDER", "prompt": "Build the <Language> for: 'English sentence.'",
      "difficulty": 5, "options": ["token","token"], "ordered": ["token","token"],
      "explanation": "..." }
  ],
  "sources": ["<SOURCES GIVEN TO YOU>"]
}
```

`difficulty` is 1-10: A1 3-5, A2 4-6, B1 5-8, B2 7-10.
MCQ needs exactly 4 options, one of which is `answer` VERBATIM.
REORDER: `options` and `ordered` hold the SAME tokens; `ordered` is the correct sentence.

### Activity 3 — DIALOGUE (6 to 8 lines, alternating, starting with "Partner")

```json
{
  "type": "DIALOGUE",
  "title": "<short scene title in the TARGET language>",
  "intro": "Play 'Me'. <One English sentence setting the scene and the target structure.>",
  "lines": [ { "speaker": "Partner", "hr": "<target language>", "en": "<English>" },
             { "speaker": "Me", "hr": "...", "en": "..." } ],
  "sources": ["<SOURCES GIVEN TO YOU>"]
}
```

`speaker` is only ever "Partner" or "Me".

## HARD RULES (each one is an automated build gate; a violation fails the build)

1. **REORDER prompts carry MEANING, never the answer.** The prompt must be the English gloss
   (`Build the French for: 'I have to finish my work.'`). Never put the target sentence, or
   most of its words, in the prompt.
2. **FILL answers never appear in their own prompt.** Do not write the answer anywhere in the
   prompt text, and do not give it away in the instruction ("Emphasize the object *with que*"
   when the answer is `que` is a violation).
3. **No question or prompt text may be duplicated** inside the same file.
4. **Never send the learner outside the app.** No URLs, no website names, no "sign in", no
   "Unit 3 of ...", no named schools, publishers, course platforms or exam centres, no
   "watch/listen on <service>". Lessons are entirely self-contained. Generic cultural
   references ("French news", "a song you like") are fine; named products/institutions are not.
5. **No em dashes or en dashes anywhere.** Use commas, or split the sentence. Hyphens inside
   compound words are fine.
6. **Correct spelling and diacritics** in the target language, always. Typed answers are
   graded strictly: é and è are different answers.
7. **Learner-facing position words say "lesson", never "day N"**. Avoid "day 3" phrasing.
8. **English for everything the learner reads ABOUT the lesson**: objective, paretoFocus,
   drills, reviewBlock, intro, explanation, `en` fields, **and every title** (the day's and
   every activity's). Target language for `hr` fields and dialogue lines only. A title may
   NAME target-language material, as the example it is about, after a colon or in brackets:
   `Big numbers: sto, tisuca, milijun`, `At the market (Na trznici)`. It may not BE
   target-language: `Veliki brojevi: sto, tisuca, milijun` shipped, and a learner on lesson 16
   cannot read the one thing that tells them what the lesson is.
   *(This rule previously said the opposite for activity titles, which is where 472 Croatian
   titles came from. Registry C23.)*
9. Do not mention CEFR level codes inside learner-facing sentences unless the topic is
   explicitly about exam preparation.
10. **Every typed answer must be typable on a phone keyboard.** No arrows, no `=`, no `+`, no
   guillemets, in any `hr` field, any FILL/TRANSLATE `answer`, or any `accepted` variant. The
   grader compares against the authored string and strips only ordinary punctuation, so one
   arrow makes the item unanswerable by anyone. Registry C21.
11. **Give the day at least 6 producible LEARN items** across its LEARN activities. Below 4 the
   app cannot build a wrap-up at all and silently replays the exercise instead. Registry C24.

## Quality bar

- Every lesson must teach something concrete and NEW, no filler and no repeat of another
  topic in your list. Assume the learner has done all earlier lessons of this course.
- Sentences must be natural, modern, everyday usage that a native speaker would actually say.
- Vocabulary and structures must fit the CEFR level: A1 present tense and immediate needs,
  A2 past/future and everyday transactions, B1 opinions/hypotheses/narration, B2 argument,
  nuance, abstraction and register.
- The DIALOGUE must actually exercise the lesson's structure, not generic small talk.
- Explanations state the rule, not just "this is correct".

When finished, reply with ONLY: the file path, the number of day objects written, and any
topic you had to adapt. Do not paste the content back.
