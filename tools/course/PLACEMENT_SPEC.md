# Corlang placement item authoring spec

You are rebuilding a language placement test so it can decide each ability BAND from three
items instead of one. Read this fully before writing.

## Why three items per band

Today one question decides a whole band, and one wrong tap ends the test. That gives a pure
guesser a 25% chance of being promoted a band, and an advanced learner a 34% chance of being
placed too low by a single careless slip. With three items per band and a 2-of-3 pass rule,
those become 15.6% and 3%. The three items in a band must therefore be INDEPENDENT probes of
the same ability level: three different structures or vocabulary areas, never three rewordings
of one fact, or a learner who happens to know that one fact passes all three.

## Output

Write ONE json file at the given path, replacing the whole test:

```json
{
 "title": "<short title, in ENGLISH>",
 "intro": "<2 sentences in ENGLISH: what this is, that it takes about two minutes, and that it can be retaken any time>",
 "questions": [ ...items... ]
}
```

UTF-8, 1-space indent, LF, trailing newline.

Each item:

```json
{
 "level": "<A0|A1|A2|B1|B2>",
 "startDay": 61,
 "type": "MCQ",
 "difficulty": 4,
 "prompt": "<the question, in ENGLISH, or a short target-language sentence with a gap>",
 "options": ["<4 options>"],
 "answer": "<the correct option, VERBATIM from options>",
 "explanation": "<one ENGLISH sentence: why this is right>"
}
```

## The bands

You will be given the exact list of `(level, startDay)` bands. Author EXACTLY THREE items for
every band, in band order, easiest band first. `level` and `startDay` must match the band
exactly, because they are what places the learner in the course.

Within a band, all three items must sit at the same difficulty, and that difficulty must rise
monotonically across bands.

## HARD RULES (automated build gates)

1. Exactly 4 options per item, all distinct, with `answer` appearing VERBATIM among them.
2. Options must be plausible: every distractor should be a real error a learner at that level
   might make (wrong case, wrong gender, wrong tense, near-synonym), never filler.
3. No item may give away another item's answer.
4. No duplicated prompts anywhere in the file.
5. No em dashes or en dashes. Commas, or split the sentence.
6. No URLs, websites, institutions, publishers or brand names.
7. Correct spelling and diacritics in the target language, always.
8. A prompt must not contain its own answer.
9. English for prompts and explanations, target language only for the material being tested.

## Calibration, the part that matters most

A band's items must be passable by someone who has genuinely finished the lessons up to that
band's `startDay`, and hard for someone who has not. Concretely:

- Test what those lessons actually taught: the grammar and the everyday vocabulary of that
  stretch of the course, not trivia and not rare words.
- Do not test a structure the course teaches LATER than this band.
- Prefer recognition of correct form over production of obscure vocabulary.
- The three items in a band should probe different skills: one grammar/form choice, one
  vocabulary or meaning choice, one usage or sentence-level choice.

Reply with ONLY: the file path, the number of bands, the number of items, and anything you
had to adapt.
