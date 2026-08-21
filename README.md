# Corlang

**A study-based app for learning a European language properly — for citizenship, for work, or for
the official exam.** Croatian and European Portuguese are available today.

Corlang is not a game. There are no points, no leagues and no mascot. It is a day-by-day course
built on how memory actually works, and it takes you from your first words to the level the
official exam asks for.

## Languages

| Language | Course | Aimed at |
|---|---|---|
| **Croatian** | 344 lessons, A0 → B1 | the official B1 exam for citizenship |
| **European Portuguese** | 240 lessons, A1 → B1 | CIPLE and DEPLE |


## What makes it different

- **Study, not games.** Every session asks you to *produce* the language from memory rather than
  recognise it, and the words you have met come back on a schedule tuned to just before you would
  forget them.
- **Aimed at the real exam.** Each course follows the official curriculum for its language and
  the mock exams mirror the real paper's format.
- **Finite and finishable.** A fixed number of lessons with an end, not an endless feed.
- **Yours, offline.** No account, no sign-in, no ads, no analytics, no tracking. Your progress
  lives on your phone and nowhere else. See [PRIVACY POLICY](https://www.corlang.app/privacy/).

## What's inside

- **Learn** — your course, one day at a time. Each day is one complete lesson: a few new words, a
  teaching block, exercises that make you produce rather than pick, a dialogue, and a closing
  recall of everything it just taught you.
- **Review** — spaced-repetition flashcards for the whole vocabulary, with audio. Words become
  *learned* when you still recall them a week later, and *mastered* at three weeks.
- **Tutor** — an AI conversation partner in the language you are learning, which corrects you as
  you go. Premium.
- **Progress** — your streak, your level, a month calendar of what you actually did, and quizzes
  and full mock exams in the real exam format.
- **Profile** — languages, settings, backup and restore.

## Status

In testing on Google Play, not publicly available yet. Everything below is for anyone reading the
source.

## How it is built

Android, Kotlin, Jetpack Compose. A **fixed app skeleton renders per-language JSON content**: a
course is data, so growing one or adding another needs no code change. Content lives in
`app/src/main/assets/content/<lang>/`, the schema is `data/model/Content.kt`, and a suite of
offline validators in `tools/course/` plus 175 unit tests guard it.

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:assembleSideloadDebug :app:testSideloadDebugUnitTest
```

Two build flavors: `sideload` (a directly installed APK) and `play` (the Play bundle, via
`:app:bundlePlayRelease`). Neither ships a self-updater.

## License

Personal learning project. Content made for educational use.
