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
- [ ] Verify lesson resume lands on the right step/exercise after Exit.
- [ ] Confirm backup export/import round-trips cleanly.

## Legal / trust
- [ ] **Add a real contact email** to `PRIVACY.md` (replace the `[add your contact email here]` placeholder).
- [ ] Host the privacy policy at a stable public URL (GitHub serves `PRIVACY.md` fine).
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
- [ ] Choose: **stay sideload + in-app updater** (independent, local-first) **or** go **Play Store**.
      They conflict — Play forbids self-updating APKs, so going Play means removing/disabling the
      in-app updater (`update/Updater.kt`) and shipping signed release builds.
- [ ] If Play: create the listing (title, description, screenshots, feature graphic), set content
      rating, complete the **Data safety** form (Corlang collects nothing — easy), confirm target API
      level meets Play's current minimum, and upload a signed AAB.

## Monetization (only if going Play)
- [ ] Google Play Developer account + Play Billing integration for Premium (client/server already built).
- [ ] Deploy the AI/premium backend (Cloudflare) **only if** offering managed AI; bring-your-own-key
      already works without it.

## Nice-to-have (not blockers)
- [ ] App icon / store feature graphic polish.
- [ ] Revisit `docs/launch-plan.md` against this list.
