# Corlang — operating rules

Android/Kotlin + Compose language-learning app. A **fixed app skeleton** renders **per-language JSON content**. The whole design goal: content grows without touching code. These rules exist to keep that true. Deeper playbook (model choice, workflow, token efficiency): **[docs/WORKFLOW.md](docs/WORKFLOW.md)**.

Current: v0.24.1 (versionCode 144). Live courses: `hr fr de it pt es`. See `MEMORY.md` (auto-memory) for the live resume point.

---

## The skeleton/content contract (most important rule)

**Content is pure data. The skeleton is code and stays fixed.** Adding or growing a language must not require code changes — with a small set of sanctioned exceptions listed below.

- **Content** = JSON under `app/src/main/assets/content/<lang>/`. Grow a course by adding pack files and listing them in the relevant `_index.json`. Never edit Kotlin to add lessons/words/quizzes.
- **Schema** = `app/src/main/java/com/corlang/app/data/model/Content.kt` (`@Serializable` data classes; parser uses `ignoreUnknownKeys = true`). Only ever *add optional fields* here — never repurpose or remove one.
- **Loader** = `app/src/main/java/com/corlang/app/data/ContentRepository.kt`. Language-agnostic; never add `when(lang)` here.
- **Vocab `id`s are permanent SRS keys** (`Content.kt:239` — "NEVER rename"). Renaming one resets a learner's spaced-repetition history. Add new ids; never rename shipped ones.
- **`_index.json` order is authoritative.** For `vocab/`, that order *is* the SRS introduction order — not filename order.

### Adding a new language — now (almost) pure data
The registry, TTS locale, and reminder copy are **data-driven** (fixed 2026-07-25). To add a language:
1. Add the content folder `app/src/main/assets/content/<code>/` (`meta.json`, `levels.json`, `plan/`, `vocab/`, …). — data
2. Register the code (in display order) in `app/src/main/assets/content/_index.json`. — data
3. Fill the wiring fields in that language's `meta.json`: `speechTag` (BCP-47, e.g. `"pt-PT"`), `reminderTitle`, `reminderTitleNamed` (must contain the `{name}` placeholder), `reminderProverb`. — data

`ContentValidationTest` fails the build if the manifest entry or any wiring field is missing, so a half-wired language can't ship. **No Kotlin edit is required** for the above.

Remaining optional code touchpoints (generic fallbacks apply if you skip them):
- `ui/screens/TalkScreen.kt` — AI-tutor per-language strings (starters, greeting, variety rules). Not yet data-driven.
- `ui/screens/Grading.kt` — optional language-specific answer grading; generic logic otherwise.

> If adding a language forces a change in `data/` (schema/loader), `speech/`, or `reminder/`, stop — that's a skeleton violation. Those are data now.

---

## Content authoring procedure (the Gold Book)

Follow `docs/course-gold-book.md` (the HOW) against `docs/language-standard.md` (the WHAT — the ship checklist). Stages: research → shape → wiring → deck → lessons → assessment → assembly → proctoring → ship. Use the `/new-language` skill for a new or audited course.

Offline validators in `tools/course/` — run before content reaches the app:
- `check_batch.py` — shared pre-merge invariants (bans external URLs, em/en dashes, etc.).
- `check_hr.py` / `check_de.py` / `check_it.py` — per-language drift checks (Serbianisms; Austrian/Swiss regionalisms; Italian accents/register). New defect classes get a new check — see `docs/error-registry.md` (error found once = check run forever).
- `build_language.py` — assembles authored batches into the `_index.json` + phase/pack layout.
- `proctor.py` — course-wide audit (cross-lesson repetition, answer leakage, boilerplate). Run on the assembled build before shipping.
- `fix_resources.py` — repoint lesson `resources` to names that exist in `resources.json`.

A `PostToolUse` hook (`.claude/settings.json` → `tools/hooks/validate_content_json.py`) warns immediately if an edited `content/**/*.json` stops parsing — but it only catches malformed JSON, not content defects; the `tools/course/` validators and `proctor.py` remain the real gate.

**Verify external resources are live before shipping** — a dead link has shipped before. Do not add settings/fields/questions without a consumer already wired (no dead data).

---

## Build & release

`gradlew` + wrapper jar are present. Build/test from CLI (Android Studio JBR = JDK 21):
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:assembleSideloadDebug :app:testSideloadDebugUnitTest --console=plain
```
Flavors (`distribution` dimension): `sideload` (default, in-app updater) and `play` (updater compiled out; AAB via `:app:bundlePlayRelease`). `versionCode`/`versionName` live in `app/build.gradle.kts` (currently 144 / 0.24.1). Room migrations required for any DB schema change (`data/db/AppDatabase`).

**Release flow:** bump `versionCode`+`versionName` → build → `cp app/build/outputs/apk/sideload/debug/app-sideload-debug.apk releases/corlang.apk` → update `releases/version.json` (versionCode MUST match the built APK) → commit + push. The in-app updater reads `raw.githubusercontent.com/.../releases/version.json`, so the repo must stay **public** (verified no secrets committed).

**AI proxy:** `server/ai-proxy/worker.js` (Cloudflare Worker) keeps the Anthropic key out of the APK. Config in `wrangler.toml`; secrets set via CLI. Worker changes need a `wrangler deploy` (not in the APK). Client wiring: `ai/AiConfig.kt`.

---

## Model selection (tell me which, I'll switch)

At the **start of each task**, state the recommended model and ask the user to `/model` to it, then proceed. Rationale + edge cases in [docs/WORKFLOW.md](docs/WORKFLOW.md).

| Task | Model | Why |
|---|---|---|
| Kotlin/skeleton code, multi-step orchestration, important content where correctness is critical | **latest Opus** | Best coding/agentic; the default. "latest Opus" = Opus 5 if the `/model` picker offers it, else Opus 4.8. Fast mode available. |
| Bulk content authoring (lessons, vocab packs, quizzes), routine well-specified edits | **Sonnet 5** | Near-Opus quality at ~⅗ the cost — content is the token-heavy work. |
| Hardest reasoning: final content/linguistic audits, thorny architecture, deep debugging | **Fable 5** | Most capable; reserve for it (highest cost). |
| Trivial mechanical passes: running validators, renames, formatting, simple search/summarize subagents | **Haiku 4.5** | Cheapest/fastest. |

Default working model is the **latest Opus**. Escalate to Fable 5 only when a task is genuinely at the edge of difficulty; drop to Sonnet 5 for volume content and Haiku 4.5 for mechanical work. (If Opus 5 and Fable 5 are both offered and a task is at the absolute limit, prefer whichever the picker marks as most capable — currently Fable 5 in the published catalog; re-check when Opus 5 lands.)

### Session signals — Claude must say these out loud, unprompted
Don't wait to be asked. Proactively tell the user when to act:
- **Switch model:** at the start of every task/phase — "This is bulk content authoring → `/model` to Sonnet 5" — and again whenever the phase changes (content → code, or normal → hard audit).
- **`/clear`:** when the next task is unrelated to the current context (e.g. finishing a Croatian batch before touching the worker), say "We're switching topics — run `/clear` first to reset context," ideally *with the exact first prompt to paste into the fresh session*.
- **New session via `/loop`:** when the work is a long, repetitive grind (e.g. authoring N lesson batches to floor), propose the concrete command, e.g. ``/loop /model sonnet then author the next batch to spec and run check_batch.py`` — and note it needs the session left open.
- **Plan mode:** before a large/uncertain change, say "Let's plan this first" rather than editing immediately.
Phrase each as a one-line recommendation with the exact command, not a question.

---

## Working style

- **Full spec up front.** State goal + success criteria + a verification check in the first ask (run a validator / build / test), so I self-check instead of you being the loop.
- **Plan mode** for uncertain or large changes before editing.
- **Subagents for research** (sourcing, audits, sweeps) to keep the main context lean; report the conclusion, not file dumps.
- **`/clear` between unrelated tasks** to reset context.
- Open threads and per-course status live in `MEMORY.md` and `docs/PENDING.md` — check them at session start.
