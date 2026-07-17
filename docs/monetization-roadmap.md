# Corlang Monetization & Progression Roadmap

The model the app is moving toward (decided 2026-07-17). Supersedes the older "exams free
forever, Premium = AI" note in `docs/server-ai.md`.

## Two independent money axes

1. **Premium subscription = AI features only.** Unlocks the **Learn** tab (AI tutor chat, AI
   teach-back review, AI examiner feedback on writing). Recurring. This is the ONLY thing the
   word "Premium" refers to in the app. **Shipped (UI): v0.20.27** — Learn appears in the nav
   only when entitled; Get Premium lives in Profile. Backend billing still pending (Play).

2. **One-time level unlocks.** A0/A1 are **free** (the whole course through A1, including that
   level's quizzes and its end-of-level exam). A2, B1, B2 each cost a **small fixed one-time
   amount** to unlock. NOT a subscription. **Not built yet — needs Play Billing.**

These are orthogonal: someone can buy B1 without Premium (no AI), or subscribe to Premium
(AI) while still on the free A1 course. Entitlement layer (`PremiumManager`) currently models
only axis 1; axis 2 needs its own per-level entitlement store.

## Exams become end-of-level lessons (removes the Practice tab)

Instead of a separate destination, the **exam is the next day-lesson after a level's last
day**. Finish A1's final day → your next "Today" lesson IS the A1 mock exam. Passing it is the
gate to A2 (which then also needs its one-time unlock to begin).

Implications to design when building:
- Plan/progression: insert an exam checkpoint after each level's last day; `completeDay`/
  `advancePosition` must treat the exam as a first-class step (pass required to advance level).
- The mock-exam runner (`ExamScreen` / section runners) gets driven from the Today flow
  rather than reached from Progress. Quizzes similarly become part of the level's lessons.
- Once done, retire the `PRACTICE` nav route and the "Practice: quizzes & mock exam" button
  in Progress. Until then, Practice stays reachable from Progress so nothing is lost.
- Level-locked days: days beyond the free A1 boundary show a "unlock A2" paywall (axis 2)
  instead of the lesson, until purchased.

## Build order when we pick this up

1. Play Console live + Play Billing library wired (`billing/PlayBillingConnector.kt`,
   `/v1/verify` on the worker) — blocks everything money.
2. Axis 2 entitlement: per-level unlock flag store + `PremiumManager`-style gate; products in
   Play Console (managed products, not subs, for the one-time unlocks).
3. Exam-as-checkpoint progression change + Today-flow integration; retire Practice tab.
4. Free/paid boundary enforcement at A1→A2 with the unlock paywall.
5. Pricing: small fixed per-level (anchor: a few € each) + the AI subscription (see
   `docs/server-ai.md` pricing note for the subscription anchors).

## What is already true in the app (v0.20.27)

- Nav: Today · Review · **Learn (only when Premium)** · Progress · Profile.
- Profile = Settings · Language · Get Premium · References (uniform rows). Language switching
  and Settings both live here now; the top bar is a static flag indicator only.
- Progress = stats, assessment, exam readiness, CEFR ladder.
- Learn = Teach + Tutor (AI), subscription-gated (DEV_PREMIUM unlocks it for testing).
- Quizzes + mock exams still reachable from Progress (interim, until step 3 above).
