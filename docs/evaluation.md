# Corlang launch-readiness evaluation

A set of independent audits to run before releasing the app to the public (Google Play).

**How to use this.** Each section below is a self-contained prompt. Run them one at a time, ideally
with the right expertise for each (a native Croatian teacher for the content audit, an Android
engineer for the code audit, and so on). Each produces its own ranked findings report. When all are
done, run the final "Synthesis" prompt to combine them into a single go/no-go picture.

**Shared context to give every audit:** Corlang is a Croatian language-learning Android app
(Kotlin + Jetpack Compose) that guides a learner from beginner to the official B1 exam through a
240-day course. Content is JSON under `app/src/main/assets/content/hr/`. It is built as a
multi-language platform, with Croatian as the first language. Assume real learners will depend on
it, so a wrong grammar answer or a crash is a serious defect. For every finding, give: severity
(blocker / high / medium / low), the exact file+line or content location (day number, activity,
question), why it is wrong, and a concrete fix. Rank findings by severity. Do not pad with praise.

---

## Audit 1: Croatian language and teaching correctness

**Run this with a native or near-native Croatian teacher's eye.** This is the most important audit:
the whole product is worthless if the Croatian is wrong.

Scope: all of `app/src/main/assets/content/hr/` (plan phases, vocab, grammar, quizzes, exams,
cheatsheet, feynman).

Check:
- Every lesson item, exercise, dialogue line, quiz, and mock-exam question for correct diacritics,
  case endings, verb aspect, clitic order, gender agreement, and natural phrasing. Flag any Croatian
  a native speaker would not say.
- Every multiple-choice question: is the marked answer actually correct, and are the distractors
  plausible but genuinely wrong? Every fill-in: are all accepted spellings present? Every reorder:
  is the "correct order" natural Croatian?
- Does each day teach what its objective claims, at the right difficulty for its level (A0 stays
  nominative-only, cases introduced in order, aspect at the right point, and so on)?
- Are the "Me" dialogue lines consistently in the male learner's forms (masculine participles)?
- Any factual errors (dates, places, cultural claims, the Croatian-vs-Serbian usage notes)?

Deliverable: a ranked list of every content error with its exact location and the corrected
Croatian, plus a short verdict on whether the course is teaching correct Croatian.

---

## Audit 2: Exam alignment

**Run this against the official exam sources in `docs/sources/`.**

Check:
- Do the A1, A2, and B1 mock exams in `exams.json` match the real exam's five-section structure,
  task types, and the pass rule (>=60% on listening/reading/grammar, pass on writing/speaking)?
- Does the grammar and vocabulary coverage across the 240-day plan actually cover what the B1 exam
  tests? Identify any gap between what the app teaches and what the exam demands.
- Is the exam-prep block (days 216-240) realistic preparation, or does it miss section-specific
  skills the real exam expects?

Deliverable: a gap analysis (what the exam needs vs what the app delivers) and a verdict on whether
a learner who completes the app is genuinely ready to sit the exam.

---

## Audit 3: Code correctness and robustness

**Run this with an Android engineer's eye.** Scope: `app/src/main/java/com/corlang/app/`.

Check:
- Crashes and ANRs: null paths, index-out-of-bounds, empty-collection assumptions, unhandled
  exceptions, coroutine and lifecycle misuse.
- Compose state: recomposition correctness, `remember` vs `rememberSaveable` (does in-progress work
  survive rotation and process death everywhere?), state that resets unexpectedly, `LaunchedEffect`
  keying bugs.
- Room: migrations v1 to v4 correct against the entity definitions; an upgrade from any prior
  version cannot crash or lose data; DAO queries correct (especially exam-attempt and streak).
- Core logic with edge cases: the spaced-repetition scheduler, streak/freeze math, grading,
  midnight rollover, timezone, day skipping, and review-mode vs daily-session interaction.
- The self-updater: download failures, partial downloads, install-intent edge cases, version
  comparison, offline and metered-connection behavior.
- Concurrency: the in-memory content cache and any shared mutable state under recomposition or
  background work.

Deliverable: a ranked list of bugs with file+line and a concrete fix for each.

---

## Audit 4: UX, accessibility, and device coverage

Check:
- Accessibility: TalkBack labels on every interactive element, content descriptions, large-font
  layouts (200% scale), color contrast (WCAG AA) in light and dark themes, 48dp touch targets.
- First-run experience: is there onboarding? Does a brand-new user know what to do, or does the app
  assume context? What do empty, loading, and error states look like?
- Layout across small phones, tablets, and foldables; portrait lock (confirm intended); edge-to-edge
  and keyboard (IME) behavior.
- Navigation dead-ends, back-button behavior, any way to get stuck.

Deliverable: a ranked list of UX and accessibility issues, each with the screen and a fix.

---

## Audit 5: Google Play compliance and legal

**This is where a launch actually gets blocked. Be strict.**

Check:
- Permissions: justify every one. Flag any that violate Play policy or need a declaration.
  Specifically judge whether the in-app self-update mechanism and `REQUEST_INSTALL_PACKAGES` are
  allowed on Play at all (they very likely are not).
- Required policies: privacy policy (present and accurate?), the Data Safety form (what is
  collected, even locally, including notifications), content rating (IARC), and current target-API
  requirements.
- Store listing: app name and trademark availability for "Corlang", icon, feature graphic,
  screenshots, and a description free of AI-tells and unverifiable claims.
- Content licensing: is building on the official ASOO curriculum and Croaticum/UNIZG materials
  legally clean? Confirm no copyrighted text was copied. Check every external link and any
  referenced brand. Check use of national symbols or third-party trademarks.

Deliverable: a checklist of what must be fixed or produced before Play will accept the app, ranked
by whether it is a hard blocker or a warning.

---

## Audit 6: Release engineering and security

Check:
- Release build: is minification/R8 and resource shrinking on, with correct keep rules? Is there a
  release signing config and a plan for Play App Signing? Is debug logging/networking stripped from
  release?
- Crash reporting and analytics: is there any way to know if the app crashes in the field? If not,
  what is the minimum viable setup?
- Data in transit: the updater fetches an APK over HTTPS from a public repo. Assess tamper/MITM risk
  and whether it is acceptable (and moot once the updater is removed for Play).
- Secrets: confirm nothing sensitive is embedded.

Deliverable: a release-readiness checklist with each item marked done, todo, or blocker.

---

## Audit 7: Testing and confidence

Check:
- Unit-test coverage of the critical logic (scheduler, grading, streak, exam pass-rule, content
  validation). What is untested that could fail silently?
- What instrumented/UI tests exist, and what should exist before public release?
- Propose a concrete pre-launch test plan: device matrix, an internal-testing track, and a beta
  group.

Deliverable: a coverage gap list and a pre-launch test plan.

---

## Audit 8: Multi-language platform readiness

Check:
- Confirm that adding a language truly requires only content (no code changes). Identify anything
  hardcoded to Croatian (strings, assumptions, TTS locale, level names) that would block a second
  language.

Deliverable: a list of what must be generalized before a second language can ship.

---

## Synthesis (run last)

Given the eight audit reports above, produce: a single ranked list of blockers, a one-paragraph
"is this launch-ready and what are the top 5 things standing in the way" summary, and a suggested
order of operations to get from here to a Google Play release.
