# Setup & Run

This is a native Android app. It builds with **Android Studio**, which bundles everything you need
(JDK, Android SDK, Gradle). You do **not** need to install Java/Gradle separately.

## 1. Install Android Studio

1. Download from <https://developer.android.com/studio> and install (Windows).
2. On first launch, let the setup wizard install the **Android SDK** and a **virtual device (emulator)**.

## 2. Open the project

1. **File ▸ Open** → select this `Corlang` folder.
2. Android Studio will run a **Gradle sync** and download dependencies (Compose, Room, etc.) the first
   time — give it a few minutes. The Gradle wrapper is configured for Gradle 8.9.
   - If it asks about the Gradle JDK, choose the bundled JDK 17.

> Note: the Gradle **wrapper JAR** is not committed (it's a binary). Android Studio regenerates it
> automatically on open. If you ever build purely from the command line and it's missing, run
> `gradle wrapper` once (with a system Gradle), then use `./gradlew`.

## 3. Run

1. Pick a device: start an emulator from **Device Manager**, or plug in a phone with **USB debugging** on.
2. Press **Run ▶** (or `Shift+F10`). The app installs and launches as **Corlang**.

## 4. End-to-end smoke test

Walk through this once to confirm everything works:

- [ ] App launches to the **Today** tab in **French** (🇫🇷 in the top bar).
- [ ] **Today** shows *Day 1 – The sounds of French*, with objective, drills, and a highlighted
      **15-minute review** block. Tap **Next ›** / **‹ Prev** to browse days.
- [ ] Tap **Mark day complete** → button shows *Completed ✓*.
- [ ] **Cheatsheet** scrolls through 10 sections with bullets, mono diagrams, and examples.
- [ ] **Quiz** → open *A0 — Survival French* → answer 10 questions; each shows ✅/❌ + an explanation;
      a final score screen appears and is saved.
- [ ] **Teach** → open a concept → type an explanation → **Check what I missed** → tick rubric points;
      unticked ones reveal a *Re-teach* box. **Save & finish**.
- [ ] **Progress** → streak / days / level tiles update; CEFR ladder A0→C1 shows with the current level
      highlighted; top-5 resources and quiz history are listed.
- [ ] Open the **top-bar language picker** → switch to **🇭🇷 Croatian**. All tabs now show Croatian
      content, and progress is **independent** (its own streak/day/level).
- [ ] Fully close and reopen the app → your progress and selected language **persist** (Room + DataStore).

## Adding or editing content

All curriculum is JSON under `app/src/main/assets/content/<lang>/`. To change a lesson, quiz, or
cheatsheet, just edit the JSON — no code changes needed. To add a **new language**:

1. Create `app/src/main/assets/content/<code>/` with the seven files
   (`meta, cheatsheet, levels, plan, quizzes, feynman, resources`.json) — copy `fr/` as a template.
2. Add the `<code>` to `availableLanguages` in
   `app/src/main/java/com/corlang/app/data/ContentRepository.kt`.

Validate your JSON before building (from the repo root, with Node installed):

```bash
node -e "const fs=require('fs'),p=require('path'),r='app/src/main/assets/content';for(const l of fs.readdirSync(r))for(const f of fs.readdirSync(p.join(r,l)))if(f.endsWith('.json'))JSON.parse(fs.readFileSync(p.join(r,l,f),'utf8'));console.log('All JSON valid')"
```
