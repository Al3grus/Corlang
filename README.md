# Frenchai 🇫🇷🇭🇷

**Learn French and Croatian fast — the 20% that drives 80% of the results.**

Frenchai is a native Android app (Kotlin + Jetpack Compose) that teaches **French** and **Croatian**
to **English** speakers through a focused, two-month daily plan. It's fully offline, free to run, and
tracks your progress independently for each language.

> The philosophy: most of everyday fluency comes from a small core — high-frequency words, a handful
> of verbs and tenses, and a few grammar patterns. Frenchai teaches that core deliberately, in order,
> and makes you *use* it every day.

## What's inside

Pick a language from the top bar (🇫🇷 / 🇭🇷) and everything below adapts to it. Five tabs:

| Tab | What it does |
|-----|--------------|
| **Today** | Your daily session from a 60-day plan: objective, the high-leverage focus, drills, linked resources, and a built-in **15-minute spaced review**. "Mark complete" advances the plan and grows your streak. |
| **Cheatsheet** | The whole language on one page — bullets, diagrams and examples — designed for a **5-minute review**. |
| **Quiz** | Per level, **10 questions ordered easy → hard**. Each answer is graded instantly with an explanation of what you missed. Best scores are saved. |
| **Teach** | The **Feynman loop**: read a concept in plain English, re-explain it in your own words, then self-check against a rubric. Missed points reveal a re-teach snippet so you loop until you can explain it cleanly. |
| **Progress** | The **CEFR ladder A0 → C1** with a milestone and can-do statements per level, your streak/level/day stats, the **top-5 resources** for the language, and your quiz history. |

CEFR levels covered: **A0, A1, A2, B1, B2, C1**. (C2 is intentionally excluded — effectively
unreachable for most non-native learners.) v1 ships **deep A0 + A1** content for both languages
(full cheatsheet, 60-day plan, quizzes, Feynman concepts) with A2–C1 milestones in place to grow into.

## Architecture (and why it scales)

**Content is data, not code.** Every piece of curriculum lives as JSON under
`app/src/main/assets/content/<lang>/`:

```
content/fr/   meta · cheatsheet · levels · plan · quizzes · feynman · resources  (.json)
content/hr/   meta · cheatsheet · levels · plan · quizzes · feynman · resources  (.json)
```

The same Compose screens render whichever language is selected. **Adding content — or an entire new
language — means adding JSON, with no UI changes.** That's the lever that lets this help thousands of
learners and grow well beyond two languages.

- **UI:** Jetpack Compose + Material 3, single-activity, Compose Navigation.
- **Content:** `kotlinx.serialization`, loaded by `ContentRepository` and cached in memory.
- **Progress:** Room database (`LanguageProgress`, `DayCompletion`, `QuizAttempt`, `FeynmanAttempt`),
  fully **independent per language**. Selected language persisted via DataStore.
- **Offline & free:** no backend, no API keys, no network required. Grading is deterministic
  (accent/case-insensitive matching); the Feynman loop uses rubric self-assessment.

```
app/src/main/java/com/frenchai/app/
├─ data/        model (serialization) · ContentRepository · db (Room) · prefs (DataStore)
├─ ui/
│  ├─ navigation/   bottom-nav destinations
│  ├─ screens/      today · cheatsheet · quiz · teach · progress (+ Grading)
│  ├─ components/    shared UI + language top bar
│  └─ theme/
├─ AppContainer.kt  (manual DI + Application)
└─ MainActivity.kt
```

## Build & run

You need **Android Studio** (it bundles the JDK, Android SDK and Gradle). See **[SETUP.md](SETUP.md)**
for step-by-step instructions and an end-to-end smoke-test checklist.

## Roadmap

- Fill in A2–C1 content (the schema and screens already support it).
- Audio (text-to-speech / native recordings) for pronunciation.
- A spaced-repetition scheduler reusing quiz history.
- Optional cloud sync + accounts for cross-device progress.
- Play Store release.

## License

Personal learning project. Content authored for educational use.
