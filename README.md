# Corlang

**A study-based language-learning app for serious learners preparing for official exams.**

Corlang is a native Android app (Kotlin + Jetpack Compose) built to teach a language properly —
not with points, leagues, or streaks-as-games, but with an evidence-based study method and a
day-by-day course that takes you from zero to a real, official language exam.

It is built as a **multi-language platform**. The first (and currently only) language is
**Croatian**, with a complete A1 → B1 course aligned to the official exam. More languages are
planned — the app's content is data, so adding a language means adding content, not rewriting the
app.

## Philosophy

- **Study, not gamification.** The daily flow follows what learning research actually supports —
  retrieval practice and spaced (distributed) practice — rather than game mechanics.
- **Exam-focused.** Everything builds toward passing the official language exam for the target
  language (for Croatian, the B1 certificate exam).
- **Validated content.** The curriculum is anchored to official sources — the state curriculum,
  the exam regulations, and the CEFR framework — with the provenance recorded in `docs/sources/`.
- **Offline & free.** No backend, no accounts, no ads. Progress is stored on the device.

## What's inside

- **Lesson** — a guided, day-by-day course. Each day is a self-contained lesson: material to
  read and listen to, interactive exercises, and conversation scripts to practise aloud. Days
  must be done in order, so nothing is skipped.
- **Words** — spaced-repetition flashcards for the core vocabulary, with audio and a daily goal.
- **Quiz** — level quizzes and full mock exams in the official exam format.
- **Learn** — a reference library: cheatsheet, full grammar syllabus with tables, and teach-back.
- **Progress** — your streak, level, vocabulary and course progress, plus a level-by-level map.

## Croatian

The Croatian course is a 240-day path from A1 to B1, aligned to the official ASOO curriculum and
the Croaticum / University of Zagreb exam format. It pairs with the free University of Zagreb
e-courses ([A1.hr](https://a1.ffzg.unizg.hr/), [A2.hr](https://a2.ffzg.unizg.hr/)) and ends with
guided exam preparation, timed mock exams, and instructions for booking the real exam.

## Adding a language

Content lives as JSON under `app/src/main/assets/content/<lang>/` (meta, plan, vocab, grammar,
quizzes, exams, …). The same screens render whichever language is selected, so a new language is a
new folder of content — no UI changes. A content-validation test enforces that every piece is
well-formed and cites an official source.

## Build & run

You need **Android Studio** (it bundles the JDK, Android SDK and Gradle). See
**[SETUP.md](SETUP.md)** for step-by-step instructions.

## Updates

Installed builds check for a newer version on launch and can download and install it from within
the app — no manual reinstalling.

## License

Personal learning project. Content authored for educational use.
