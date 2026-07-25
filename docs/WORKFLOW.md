# Corlang — working playbook

How to run this project efficiently in Claude Code. `CLAUDE.md` has the rules that load every session; this file is the reference behind them. Not auto-loaded — read it when setting up or when a workflow question comes up.

---

## 1. Which model, when — and why

Four models are selectable via `/model`. Prices are per 1M tokens (input / output), for cost intuition — you are not billed per-token in the Claude Code subscription, but relative cost still guides where to spend the strongest model.

| Model | Cost (in/out) | Character | Use it for |
|---|---|---|---|
| **Fable 5** | $10 / $50 | Most capable; thinking always on; longest-horizon reasoning | The hardest 5%: final linguistic/content audits (proctor-grade judgment), gnarly architecture calls, deep multi-file debugging. Reserve — it is the expensive one. |
| **Opus 4.8** | $5 / $25 | Best coding/agentic model; the default here | Kotlin/skeleton changes, migrations, multi-step orchestration, and any content generation where an error is costly. Fast mode available (Opus 4.8/4.7 only). |
| **Sonnet 5** | $3 / $15 (intro $2 / $10 through 2026-08-31) | Near-Opus quality on coding/agentic at a fraction of the cost | The workhorse for **bulk content authoring** — lesson batches, vocab packs, quizzes — and routine, well-specified edits. This is where most Corlang tokens go, so this is where the savings are. |
| **Haiku 4.5** | $1 / $5 | Fastest, cheapest | Mechanical passes: running validators, bulk renames, formatting, and cheap research/summarize subagents. |

**The rule (as you asked):** at the start of a task I name the model that fits and ask you to `/model` to it before we start. Rough policy:
- Default sits at **Opus 4.8**.
- A phase that is *generating a lot of content* → drop to **Sonnet 5** first.
- A phase that is *judging content quality* or *hard architecture* → escalate to **Fable 5**.
- A phase that is *pure mechanics* → **Haiku 4.5** (or a Haiku subagent).

Don't thrash models mid-task — the switch resets prompt caching. Pick per *phase*, not per message.

---

## 2. Task division & subagents

**Do it inline** when the work is small, or when you'll reference what's read in your next edit.

**Use a subagent (the Agent tool / "Explore")** when a side task would flood the main context with output you won't reuse — sourcing a curriculum, auditing a course for a defect class, sweeping many files, verifying external links. The subagent works in its own context and returns only the conclusion. That keeps the main session lean, which is the single biggest lever on quality over a long session (a full context degrades reasoning).

**Run several in parallel** by issuing multiple Agent calls in one turn — independent work (e.g. "audit hr" + "audit fr" + "check server config") runs concurrently. Cost scales with the number of agents (each carries its own context), so fan out for genuinely independent work, not to split one small job.

**Cost tradeoff:** a research subagent is roughly break-even vs. reading the files inline (you'd pay for the reads either way) but *protects* the main context. Parallel fan-out costs more tokens but buys wall-clock time — worth it for large independent sweeps, overkill for a quick lookup.

**Custom subagent for a repeated pattern:** the `/new-language` skill already encodes the Gold Book course-build flow. Reach for it rather than re-deriving the steps.

---

## 3. Automation & self-pacing

Three distinct mechanisms — they are not the same thing:

- **`/loop`** — repeats a prompt/command on an interval (or self-paced) *within this open session*. Good for: grinding a content backlog with periodic check-ins, polling a build. Requires the session to stay open. This is the closest thing to an "auto-prompter" — there is no separate auto-prompter feature; it's `/loop` + a good prompt.
- **Scheduled cloud agents / routines (`/schedule`)** — a saved prompt that runs on Anthropic's infrastructure on a cron/interval or on a trigger, whether or not your machine is on. Good for: nightly course validation, backlog maintenance. Runs unattended, so scope it tightly.
- **Hooks (settings.json)** — deterministic shell commands fired on lifecycle events. Not automation of *thinking*; enforcement of *rules*. See §5.

Rule of thumb: **`/loop`** = "keep me going while I watch", **routine** = "do this while I'm away", **hook** = "always run this on event X, no matter what".

---

## 4. Token efficiency without quality loss

Prioritized — top items have the highest return:

1. **Give a verification check in the prompt** (a validator to run, a build/test to pass). Without one, work stops when it "looks done" and you become the correction loop; with one, it self-corrects. Biggest single lever.
2. **`/clear` between unrelated tasks.** Croatian batch done → clearing before French unhiding drops a large stale context.
3. **Plan mode for uncertain/large changes** — exploration happens without polluting the working context, then implement against the plan.
4. **Subagents for exploration** (§2) — keep search output out of the main thread.
5. **Keep `CLAUDE.md` lean** (it loads every session). High-signal rules only; anything derivable from code doesn't belong there.
6. **Pick the right model per phase** (§1) — Sonnet 5 for volume content is a large, quality-neutral saving.
7. **Prompt caching is automatic across turns** in a session; the enemy is churn — switching model mid-task or rewriting the system context invalidates it. Batch same-model work together.

Context is auto-summarized/compacted when it fills; you don't need to wrap up early, but you also shouldn't let one session sprawl across unrelated work — `/clear` is cheaper than compaction.

---

## 5. Hooks worth configuring

Configure via the `update-config` skill (it writes `settings.json` correctly). Real hook events: `PreToolUse`, `PostToolUse`, `UserPromptSubmit`, `SessionStart`, `PreCompact`, `Stop`, `SubagentStop`, `Notification`, `SessionEnd`. (Ignore any other event names — some tools hallucinate events like `FileChanged`/`PermissionRequest`; those aren't real.)

Candidates for Corlang:
- **`PostToolUse`** matching Edit/Write on `content/**/*.json` → run `check_batch.py` (or `jq .` for a JSON sanity check) on the touched file. Catches malformed content the instant it's written.
- **`SessionStart`** → echo a one-line reminder to read `MEMORY.md` + `docs/PENDING.md` for the resume point.
- **`Notification`** → desktop ping when a long run needs input.

Keep hooks for things that *must* happen every time. Advice belongs in `CLAUDE.md`, not a hook.

---

## 6. Prompting well (reduces rework)

- **Full spec up front:** goal + explicit success criteria + the check to run. Underspecified asks revealed across many turns cost the most tokens and produce the most rework.
- **Point at sources:** reference exact files/paths instead of describing them, so the right place gets investigated.
- **Reference existing patterns:** "follow how `check_hr.py` scopes to learner strings" beats "add a checker".
- **Symptom + check:** describe the failure *and* what success looks like, so work is self-verifiable.
- **Plan mode** before a big refactor; ask for verification (run the validator/build) after significant changes.

---

## 7. Deep research & agent quality

Use the `/deep-research` skill for cited, adversarially-verified findings (curriculum specs, resource-liveness audits, design decisions). To make agents perform in depth without errors:

- **Scope tightly and give acceptance criteria** — "report must answer [1][2][3]", not "research X".
- **Feed context** — link the relevant files/memory so the agent starts grounded.
- **Demand verification** — for content/linguistic claims, have a second pass (or a Fable 5 audit) try to refute the finding before you act on it. This mirrors what `proctor.py` does for assembled courses: a fresh, adversarial look catches what the generating pass can't see.
- **Relay conclusions, not transcripts** — a subagent's job is to return the decision-relevant summary.

---

## 8. Status of the setup improvements

**Done (2026-07-25):**
- **Language registry is data-driven.** `availableLanguages` now reads `content/_index.json`; TTS locale (`speechTag`) and reminder copy (`reminderTitle`/`reminderTitleNamed`/`reminderProverb`) moved into each `meta.json`. Adding a language for those is content-only; `ContentValidationTest` fails the build if a wiring field is missing. Verified: `:app:testSideloadDebugUnitTest` passes.
- **Content-validation hook** (`.claude/settings.json` → `PostToolUse` → `tools/hooks/validate_content_json.py`): warns when an edited `content/**/*.json` no longer parses. (New settings files may need one `/hooks` open or a restart before the watcher picks them up.)

**Still open:**
- **`TalkScreen.kt` and `Grading.kt`** remain the last per-language code touchpoints (AI-tutor strings; optional answer grading). Fair candidates to make data-driven later, but lower value than the registry was.
- **Native-speaker review (Track D)** — the open quality gap across all courses; no automation substitutes for it. Plan it explicitly per course.
- **Worker body-cap change** still needs a `wrangler deploy` to take effect (not in the APK).
- **Open content work:** Portuguese to floor, Spanish from zero (`/new-language`, PCIC-anchored), walkthrough-simulation capstone after content is final. Track in `docs/PENDING.md` / `MEMORY.md`.

> **Model note:** "latest Opus" in §1 means Opus 5 if the `/model` picker offers it, otherwise Opus 4.8. The published model catalog this was written against has Opus 4.8 as newest Opus and Fable 5 as most capable; re-check the picker when Opus 5 appears and treat it as the Opus-tier default.
