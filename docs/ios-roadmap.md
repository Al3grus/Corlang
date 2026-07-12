# iOS roadmap (future task — deferred)

Bringing Corlang to iPhone. **Not started** — this is a multi-week migration captured for later, not
part of any current release. Recorded 2026-07 after confirming the technical path is viable.

## Why Kotlin Multiplatform + Compose Multiplatform

The app is Kotlin + Jetpack Compose + Room + DataStore, so the natural path is **KMP + Compose
Multiplatform** (share the existing Kotlin code, including the UI) rather than a native Swift rewrite or
a cross-platform rewrite (Flutter/RN) that throws the Android app away.

As of 2025–2026 this is fully supported:
- Compose Multiplatform for iOS went **stable** with CMP 1.8.0 (May 2025).
- **Room 2.8+, DataStore 1.1.7, ViewModel 2.9.4** now officially support Kotlin Multiplatform.

## Portability inventory (from a full codebase audit)

- **Shareable to `commonMain` ~as-is:** all UI screens/components/theme (~34 files), the SRS/FSRS,
  drill-gen, grading, streak, exam-rules logic (~10 files), `data/model/Content.kt`, the Room
  `@Entity` classes, `backup/BackupManager.kt` and `ai/AiConfig.kt` (already Android-framework-free),
  `speech/SpeechLocales.kt`, `billing/PremiumManager.kt`. Tests move to `commonTest`.
- **Need `expect`/`actual`:** `speech/TtsManager` (→ `AVSpeechSynthesizer`), `speech/SpeechInput`
  (→ `SFSpeechRecognizer` + `AVAudioEngine`), `ui/Haptics` (→ `UIImpactFeedbackGenerator`/Core Haptics),
  `data/ContentRepository` asset read (→ Compose-Resources `Res.readBytes`), `LanguagePrefs` DataStore
  path factory, `AppDatabase` Room driver (bundled SQLite), `AppContainer` (drop the `Context` param).
  `ai/AiClient` HTTP (`HttpURLConnection`) → Ktor multiplatform.
- **Android-only, dropped on iOS:** `update/Updater.kt` (sideload installer — iOS is App-Store-only),
  `reminder/` (WorkManager — reworked as `UNUserNotificationCenter` proactive local notifications),
  and the `MainActivity`/`CorlangApp` entry points (replaced by an iOS `UIViewController` host + a
  Kotlin entry point).

## Phases

1. **i1 — Restructure to KMP.** Split `:app` into `:shared` (common/android/ios) + a thin
   `:androidApp`; move shared code to `commonMain`. Upgrade Room 2.6.1→2.8+ (KMP driver + migration
   API) and DataStore→1.1.7 KMP. Android stays the shipping target; keep every unit test green. No
   Android behavior change.
2. **i2 — Platform layer.** iOS `actual`s for TTS, speech, haptics, asset reading, DataStore factory,
   Room driver; `AiClient` → Ktor; drop the updater; rework reminders as local notifications.
3. **i3 — iOS app shell.** Xcode `iosApp` hosting the Compose UIViewController; Kotlin entry point
   building `AppContainer` without `Context`; Info.plist usage strings (mic, speech), notification
   auth; app icon + launch screen; backup picker → `UIDocumentPickerViewController`.
4. **i4 — Ship.** TestFlight → App Store.

## Prerequisites / constraints
- **Apple Developer Program ($99/yr)** and a **Mac with Xcode** for builds and submission.
- iOS updates go through the **App Store** (no in-app self-updater).
- iOS Haptics can't reproduce arbitrary amplitude/duration waveforms — the `Strength` model degrades.
- Biggest technical risk: the Room/DataStore KMP upgrade and the reminder rework — do them behind green
  tests with Android unaffected. The migration is **additive, not a rewrite**; Android keeps shipping.

## References
- CMP iOS stable: https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/
- Jetpack libraries KMP: https://www.kmpship.app/blog/jetpack-libraries-kmp-support-2025
