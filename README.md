# Corlang 🇭🇷

**Learn Croatian daily, pass the official B1 exam, keep going to near-native.**

Corlang is a native Android app (Kotlin + Jetpack Compose) built for one job: making daily
Croatian practice so easy that consistency takes care of itself — and anchored to the official
standards so what you learn is exam-valid. Fully offline, free to run, local progress.

> **Validation**: every piece of curriculum cites published official sources — the ASOO state
> curriculum for Croatian as a foreign language, the NN 100/2021 exam regulation, Croaticum's
> syllabus and sample exam, and the CEFR grid. Digests live in `docs/sources/`; the provenance
> rule is enforced by `ContentValidationTest` on every build.

## The daily loop (gym-proof)

1. **Words** — the habit anchor: spaced-repetition flashcards in **sets of 7 cards** that fit a
   rest between exercise sets. Swipe to grade (← again · → good · ↑ easy), haptic feedback,
   TTS pronunciation, and exact resume if you pocket the phone mid-set. Established words flip
   to **English → Croatian production** (recall, which is what speaking needs).
2. **Today** — hero "Continue" button always knows the next best action; then the day's plan.
3. **Streak with freezes** — any finished set/session/quiz counts; every 7 consecutive days
   banks a streak freeze (max 2) that auto-covers one missed day. A **19:00 reminder** (rotating
   copy) pings only on days you haven't studied.

## Tabs

| Tab | What it does |
|-----|--------------|
| **Today** | Goal-oriented hero (due words → today's plan day), streak + freezes, 60-day plan, reminder toggle. |
| **Words** | SRS flashcards in gym sets; ~800 validated words so far (target ~2,500 through B1); daily goal ring; 10/15/20 new-words pace setting. |
| **Quiz** | Level quizzes A0→C1 **and the full B1 mock exam** in the official 5-section format, scored by the real pass rule (≥60% ×3 + pass writing/speaking). |
| **Learn** | Cheatsheet · **Grammar** (the full ASOO syllabus per level with reference declension/conjugation/clitic tables) · Feynman teach-back. |
| **Progress** | Streak/level stats, CEFR ladder with official can-do descriptors, **exam-readiness view** (section results + can-do self-checklist), resources, quiz history. |

## Resources it's built around

The plan and resource list lean on the **free University of Zagreb (Croaticum) e-courses**:

- [E-tečaj A1.hr](https://a1.ffzg.unizg.hr/) — 80 units of ~45 min with listening, pronunciation,
  writing and comprehension. The plan's main structured course.
- [E-tečaj A2.hr](https://a2.ffzg.unizg.hr/) — the ready-made next stage after the 60-day sprint.

plus [easy-croatian.com](https://www.easy-croatian.com) (the definitive free grammar course),
an italki tutor for speaking, and r/croatian for questions.

## Architecture (and why it scales)

**Content is data, not code.** Every piece of curriculum lives as JSON under
`app/src/main/assets/content/<lang>/`:

```
content/hr/   meta · cheatsheet · levels · plan · quizzes · feynman · resources · vocab  (.json)
content/fr/   (complete French course, currently hidden — see below)
```

The same Compose screens render whichever language is selected. Adding content — or an entire new
language — means adding JSON, with no UI changes.

- **UI:** Jetpack Compose + Material 3 (custom "Adriatic" light/dark palette), single-activity,
  Compose Navigation.
- **Content:** `kotlinx.serialization`, loaded by `ContentRepository` and cached in memory.
- **Progress:** Room (`LanguageProgress`, `DayCompletion`, `QuizAttempt`, `FeynmanAttempt`,
  `WordReview`), independent per language. Settings via DataStore.
- **SRS:** pure Leitner scheduler (`data/Srs.kt`, unit-tested) — boxes 0–6, intervals 1→45 days.
- **Reminder:** WorkManager daily worker; skips the notification if you already studied that day.
- **Offline & free:** no backend, no API keys. Grading is deterministic (accent/case-insensitive).

### Re-enabling French

The full French course still ships in `assets/content/fr/`. To show it again, add `"fr"` back to
`availableLanguages` in `ContentRepository` — the language picker reappears automatically.

## Build & run

You need **Android Studio** (it bundles the JDK, Android SDK and Gradle). See **[SETUP.md](SETUP.md)**
for step-by-step instructions and an end-to-end smoke-test checklist.

## Roadmap (tracked in docs/sources/vocab-coverage.md)

- Vocabulary batches 04–09: A2 (+~600) and B1 (+~700 incl. the ASOO verb families) toward the
  ~2,500-word exam-ready deck. Batches 00–03 (806 words) are shipped and id-frozen.
- Plan Phases 2–3: A2 track (~90 days vs a2.ffzg.unizg.hr) and B1 track with exam-prep weeks.
- Typed-recall mode for production cards; hands-free audio set mode.
- Optional cloud sync + accounts for cross-device progress; Play Store release.

## License

Personal learning project. Content authored for educational use.
