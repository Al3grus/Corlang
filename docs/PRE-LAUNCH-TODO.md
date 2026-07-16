# Corlang — Pre-launch checklist

Things to close before a public launch, so nothing gets forgotten. Grouped by area.
(Design/UX and the core app are done; this is the last mile.)

## Content correctness (blocker)
- [ ] **Native-speaker review — Croatian** (use `docs/review/hr-content-review.html`, print or PDF).
- [ ] **Native-speaker review — Portuguese** (`docs/review/pt-content-review.html`).
- [ ] **Native-speaker review — French** (`docs/review/fr-content-review.html`).
- [ ] Apply the corrections back into the JSON under `app/src/main/assets/content/<lang>/`.
- [ ] Spot-check TTS pronunciation per language on a real device (names, tricky words).

## QA (blocker)
- [ ] Full end-to-end pass on a **real device**: onboarding → placement → lesson (all step types) →
      review/SRS → quizzes → mock exam → cross a midnight (streak) → switch languages.
- [ ] Verify streak resets correctly after a genuinely missed day.
- [x] Verify lesson resume lands on the right step/exercise after Exit — field-tested and fixed
      (v0.20.11/12, see log below); re-verify once more after the next few lessons.
- [ ] Confirm backup export/import round-trips cleanly.

## Field-testing log — fixed 2026-07-16 (v0.20.9 → v0.20.18)

Real-device findings from dogfooding days 1-6 (Croatian), all fixed + released same day:

- **Today ring partial credit** (v0.20.9): the dashboard ring now credits exercises cleared
  inside an unfinished step (3/8 done shows as 3/8 of a step, same math as the session bar).
- **REORDER answers leaked position** (v0.20.11): "tap the words in order" tokens now display
  lowercased with edge punctuation stripped — the capital and final dot betrayed first/last word.
- **Exercise solve lost on exit-from-feedback** (v0.20.11): a correct answer now persists at
  Check time, not on the Next tap.
- **Missed exercise dropped by resume + false "Perfect"** (v0.20.12): resume state is now WHICH
  questions were cleared (::q<i>) + a ::missed flag, not a bare count; the re-queued missed
  question survives exit/resume and the finish message stays honest. Quizzes/mock exams audited:
  structurally immune (linear, no requeue, no partial persistence).
- **Language switch mid-session** (v0.20.13): the top-bar picker locks (static badge) while a
  lesson/review/quiz/exam section/placement/teach-back/tutor chat is active; crossfade 1.3s.
- **"he / she is" graded against bare "on"** (v0.20.14): recall grading is slash-aware —
  "on je" / "ona je" / "on/ona je" all pass; alternatives are never truncated.
- **Streak celebration flashed away** (v0.20.14): completing a day no longer retargets the open
  lesson to the next day; the celebration stays until tapped, then lands on the dashboard.
- **Celebration phrases on ordinary days** (v0.20.15): milestone lines only (7/14/30/…) — the
  streak-1 line recurred on every restart.
- **Misleading "Learn extra words" button** (v0.20.15/16): the streak hero only offers review
  (when due > 0); all Start/Continue/Revisit actions live on the lesson card (outlined style,
  v0.20.17).
- **Revisits could re-credit the streak** (v0.20.17): re-marking a completed day is blocked in
  the UI and completeDay is idempotent in the data layer — one streak credit per new day, ever.
- **PT "ão — não, pão, mão" demanded verbatim** (v0.20.18): "headword — example" LEARN demos
  now recall just the headword against its gloss headword; items whose gloss contains their own
  answer are excluded. A permanent ContentValidationTest sweep enforces both across hr/fr/pt.

## Legal / trust
- [x] **Contact email** in `PRIVACY.md`: support@corlang.app (Cloudflare Email Routing →
      corlang@proton.me; domain corlang.app registered 2026-07-16).
- [x] Privacy policy at a stable public URL — GitHub `PRIVACY.md` for launch; move to
      corlang.app/privacy when the one-page site goes up (Cloudflare Pages).
- [ ] Sanity-check content licensing: confirm curricula/exam material is referenced/mapped, not copied.

## Release engineering
- [ ] **Regenerate the release keystore before a real Play launch** (the current key's password passed
      through a chat session). `keytool -genkeypair ... -keystore corlang-release.jks` with a password
      only you know; update `keystore.properties`.
- [ ] **Back up** `corlang-release.jks` + `keystore.properties` somewhere safe (offline). Losing them
      means you can never update the app under the same signature.
- [ ] Decide APK vs AAB for distribution (Play wants an **AAB**).
- [ ] (Optional) enable R8/shrinking (`isMinifyEnabled = true`) and verify with the proguard rules.

## Distribution decision
- [x] ~~Choose sideload vs Play~~ Solved with the `distribution` flavor split (2026-07-16): the
      `sideload` flavor keeps the in-app updater; the `play` flavor compiles it out and carries no
      REQUEST_INSTALL_PACKAGES/FileProvider. Both channels can coexist.
- [x] Target API: bumped to compileSdk/targetSdk **35** (2026-07-16). Watch for the next Play
      floor raise (~Aug 2026 → API 36).
- [ ] If Play: create the listing (title, description, screenshots, feature graphic), set content
      rating, complete the **Data safety** form (Corlang collects nothing — easy), and upload a
      signed AAB built from the `play` flavor (`:app:bundlePlayRelease`).

## Monetization (only if going Play)
- [ ] Google Play Developer account + Play Billing integration for Premium (client/server already built).
- [ ] Deploy the AI/premium backend (Cloudflare) — required for any AI features: the BYO-key path
      was fully removed (2026-07-16), so AI stays dark until the proxy is live.

## Nice-to-have (not blockers)
- [ ] App icon / store feature graphic polish.
- [ ] Revisit `docs/launch-plan.md` against this list.
