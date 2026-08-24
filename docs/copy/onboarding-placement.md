# Copy: onboarding, placement, first review

Every word a learner reads before their first lesson, in the order they meet it.

**How to use this file:** edit the text inside the fenced blocks and tell me. I will apply the
changes to the source and rebuild. Do not edit the `SOURCE:` lines — they are how each block is
found again. If you want a block deleted, write `DELETE` on its own line above it; if you want one
added, describe it in a `> NOTE:` line and I will place it.

Blocks marked **(content)** live in per-language JSON and must be edited for every course
separately — the same block is listed once per language. Everything else is one string shared by
all courses.

Verified against the app at v0.65.0 / versionCode 200.

---

## 1. Onboarding — Welcome

First screen on a fresh install. The logo lockup above already reads "Corlang", which is why the
title is only a greeting.

SOURCE: `ui/screens/OnboardingScreen.kt` STEP_WELCOME

Title:
```
Welcome!
```

Button:
```
Get started →
```

---

## 2. Onboarding — How it works

Answers "what will I be doing", deliberately not "what will I be certified at".

SOURCE: `ui/screens/OnboardingScreen.kt` STEP_HOW

Title:
```
How it works
```

Paragraph 1:
```
Ten minutes, six steps, the same order every day. A few new words, a short teaching block, exercises, a dialogue you say out loud, and a wrap-up that asks for that lesson's phrases back from memory.
```

Paragraph 2:
```
At the end of the lesson, the words you have already learned in previous days come back. Each one is scheduled on its own and returns just before you would have forgotten it, so what you learn today is still there in a month.
```

Paragraph 3:
```
Quizzes and full mock exams in the official format per level, and an optional AI tutor for conversation practice and written feedback.
```

Paragraph 4 (smaller, muted):
```
No accounts, no tracking, no data collection. Lessons, review, quizzes and exams all work offline and stay on your device. Only the AI tutor needs a connection.
```

Button (says "Next →" instead when only one course is live):
```
Choose your language →
```

---

## 3. Onboarding — Which language

Only appears when more than one course is live. Right now that is Croatian and Portuguese.

SOURCE: `ui/screens/OnboardingScreen.kt` STEP_LANG

Title:
```
Which language do you want to learn?
```

Button:
```
Next →
```

---

## 4. Onboarding — Name

SOURCE: `ui/screens/OnboardingScreen.kt` STEP_NAME

Title:
```
What's your name?
```

Subtitle:
```
Your reminders and your tutor will use it.
```

Field label:
```
Your name
```

---

## 5. Onboarding — Grammatical gender

The title inserts the course name, and Croatian gets its own subtitle because it can show the
change concretely. `$langName` is replaced at runtime.

SOURCE: `ui/screens/OnboardingScreen.kt` STEP_GENDER

Title:
```
Which forms should $langName use for you?
```

Subtitle, Croatian only:
```
Croatian words change with the speaker: a man says "Ja sam Amerikanac, radio sam", a woman says "Ja sam Amerikanka, radila sam".
```

Subtitle, every other course:
```
Many words and endings change with the speaker's gender, so we'll use the right forms for you.
```

Options:
```
Male forms
Female forms
```

---

## 6. Onboarding — Daily review limit

`10` below is `Fsrs.NEW_WORDS_PER_DAY`, inserted at runtime. Changing the number here changes
nothing; it follows the pace set in code.

SOURCE: `ui/screens/OnboardingScreen.kt` STEP_GOAL

Title:
```
Daily review limit
```

Subtitle:
```
Every lesson teaches 10 new words. Older words get reviewed to keep them fresh: how many of those do you want each day? You can change this any time in Settings.
```

---

## 7. Onboarding — Do you already know some?

The fork into the placement test. `$langName` is replaced at runtime.

SOURCE: `ui/screens/OnboardingScreen.kt` STEP_LEVEL

Title:
```
Last one: do you already know some $langName?
```

Option A:
```
I'm new, start me at Lesson 1
```

Option B:
```
I know some, take the placement test
```

Button:
```
Start learning →
```

---

## 8. Switching to a course you have never studied

A dialog, not a screen. Appears when you pick a language with no progress in it — including
straight after onboarding if you chose the placement test there. `$langName` is the course name.

SOURCE: `MainActivity.kt` — new-language prompt

Title:
```
Start $langName
```

Body:
```
Take a quick placement test so $langName starts at the right level? It will take about two minutes.
```

Buttons:
```
Take placement test
Start at Lesson 1
```

---

## 9. Placement — the start gate

Read this, then start deliberately. Nothing is asked until "Start placement" is tapped.

### 9a. Title and explanation **(content — per language)**

SOURCE: `assets/content/pt/placement.json` and `assets/content/hr/placement.json` → `intro`
(one shared text since the rewrite; each course keeps its own title, "Find your starting point" and "Where should you start?")

```
Find your starting point
```

```
A short check that adapts to you. It starts in the middle rather than at the beginning, so the first question may seem hard or easy: answer it wrong and the next one is easier, get it right and the next question is harder. You can only take this assessment once when you start a course, so answer as carefully as you can: there is no retake, and the only way back to lesson one is resetting the course in Settings. If you don't know an answer, don't try to guess it, or you might end up in the wrong place.
```


### 9b. Shared second paragraph and buttons

`20` is computed from the course's own ladder, so it is 20 for Portuguese and may differ elsewhere.

SOURCE: `ui/screens/PlacementScreen.kt` — the `!started` gate

```
Around 20 questions at most, and fewer if the test settles early. Answer as well as you can without guessing: this only decides where you begin, and getting it wrong in either direction costs you time later.
```

Buttons:
```
Start placement
Not now
```

---

## 10. Placement — during the test

SOURCE: `ui/screens/PlacementScreen.kt` — question view

Progress label, where both numbers are runtime values:
```
Question 1 of 20
```

Buttons:
```
Next →
I don't know this one
```

---

## 11. Placement — leaving before the end

A confirmation, because the back gesture is one swipe and a dozen answers are at stake. The second
version appears when leaving would drop you back into a course you were already studying.

SOURCE: `ui/screens/PlacementScreen.kt` — `confirmLeave`

Title:
```
Leave the placement test?
```

Body, when there is no course to fall back to:
```
Your answers so far will be lost and you won't be placed. You'll be asked again how you want to start this language.
```

Body, when there is one (`$returnTo` is that course's name):
```
Your answers so far will be lost and nothing will be placed. You'll go back to $returnTo.
```

Buttons:
```
Leave
Keep going
```

---

## 12. Placement — the ordinary result

What most learners see. `B1` and `Lesson 96` are runtime values.

SOURCE: `ui/screens/PlacementScreen.kt` — result, not at ceiling

Heading:
```
You're placed at
```

Hero:
```
B1 · Lesson 96
```

Body:
```
Your lessons will start here, and earlier lessons stay available to review any time.
```

Extra paragraph, only when words are queued for review. `about 600` is a runtime count:
```
Because this test is short, the words from the lessons just before here, about 600 of them, are added to your reviews so nothing slips through the cracks. They arrive a few a day, hardest first, never more than half your daily review limit. Anything you already know you will review once and rarely see again.
```

Buttons:
```
Start at B1 · Lesson 96
Cancel
```

---

## 13. Placement — top of the course

Shown when every question was answered correctly and the test ran out of ladder.

SOURCE: `ui/screens/PlacementScreen.kt` — result, at ceiling

Heading:
```
You're at the top of this course
```

Paragraph 1:
```
You answered everything this test can ask, so it has placed you at the final lesson. Your real level may well be higher: the test stops here because the course also does.
```

Paragraph 2, shown when the learner owns the whole course:
```
Every lesson and quiz in the course is open to you now, to practise and review in any order. The mock exams are probably what you came for, and word review will keep any gaps honest.
```

Paragraph 2, shown when they do not:
```
Nothing in the course is ahead of you any more: every lesson you own is open to practise and review in any order. Levels you have not unlocked stay locked, because a placement test measures where you are, it does not buy the course.
```

Paragraph 3:
```
Want to go further than this course goes? Ask for the level you need and we will email you when it exists.
```

Buttons:
```
Request a language or level
Open the course at its last lesson
Cancel
```

---

## 14. First word review

A dialog on the first visit to the Review tab. The arrows are the swipe directions.

SOURCE: `ui/screens/WordsScreen.kt` — how-it-works dialog

Title:
```
How reviewing works
```

Body:
```
You'll see a word. Try to recall it, tap the card to reveal the answer, then rate how hard it was:
```

Ratings:
```
← Hard  ·  you forgot it or barely knew it (it comes back soon)
↑ Medium  ·  you recalled it with some effort
Easy →  ·  you knew it instantly (it won't return for a while)
```

Closing line:
```
Tap a button, or swipe the card in that arrow's direction. Rating honestly is what makes the spacing method work.
```

Button:
```
Got it
```

---

## House rules for this copy

From `MISSION.md`, so an edit does not quietly undo a decision:

- No em dashes or en dashes in any string. A Kotlin test fails the build on them.
- Never "scientifically proven" or "evidence-based" without naming the mechanism.
- Never promise fluency, a certificate, or any B2/C1 level. Neither course has a B2 lesson.
- Never say the course is free. Ten lessons are free.
- Never claim a placement retake. There isn't one.
- Plain and unexcited. No exclamation marks beyond the existing "Welcome!", no urgency, no
  invented popularity.
