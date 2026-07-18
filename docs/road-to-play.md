# Road to Play Store testers — complete, ordered

Everything needed to get Corlang from "AAB on disk" to "testers testing on Play, billing and
all." Do it top to bottom. Steps marked **(browser)** are yours (Play Console / Google Cloud);
the code is already done.

## The three Google systems (what each actually is)

1. **Google Play Console** — the website where you publish the app, create the paid products,
   manage testers and tracks. You have it ($25 one-time, identity verified).
2. **Google Play Billing** — the in-app purchase system. The APP code is done (BillingManager +
   paywall); you just **create the products** in Play Console and the app reads their live
   prices. This is what shows the payment popups.
3. **Google Cloud service account** — a robot login that lets the SERVER (our Cloudflare worker)
   ask Google "is this subscription still active?", so a person can't subscribe, refund, and
   keep the AI. It is **anti-fraud only**, needed **before PUBLIC production**, NOT before
   testers. The worker code is done and dormant until you add its key. (Setup: docs/server-ai.md.)

## IMPORTANT: where the payment popups actually appear

The real Play payment sheet only shows when the app is **installed from a Play track** by a
tester/license-tester account, **with the products created**. A **sideloaded APK cannot show
it** — Play doesn't recognise the install. So:
- The **gated-preview APK** (DEV_PREMIUM=false, sent separately) shows the LOCKED structure:
  Learn tab hidden, "🔒 Unlock A2" buttons, the paywall screen layout — but prices read
  "unavailable" and the buttons don't open a real sheet.
- To see the **real popups + prices + purchase → unlock**, you must reach **step 8** below
  (install from the Internal testing track with a license-tester account).

---

## Steps

### 0. Already done
Play Console account + identity ✓ · keystore ✓ · worker deployed with rate limits + sub cap +
verification-ready ✓ · AAB built ✓ (`app/build/outputs/bundle/playRelease/app-play-release.aab`,
v0.20.33, versionCode 86).

### 1. Create the app **(browser)**
Play Console → All apps → **Create app**. Name "Corlang", default language, type **App**,
**Free**. Accept the declarations.

### 2. "Set up your app" dashboard tasks **(browser)** — all required before any release
- **App access**: "All functionality is available without special access" (no login).
- **Ads**: contains no ads.
- **Content rating**: fill the questionnaire (educational, no objectionable content → Everyone/PEGI 3).
- **Target audience**: 13+ (avoids the strict children's-policy requirements).
- **Data safety**: **No data collected, no data shared** (Corlang is local-only). Declare an
  in-app data-deletion path = "uninstalling removes all data" (true — it's all on-device).
- **Privacy policy**: paste the PRIVACY.md raw URL
  (`https://raw.githubusercontent.com/Al3grus/Corlang/main/PRIVACY.md`).
- Government/financial/health features: No.

### 3. Main store listing **(browser)** — copy is drafted below
App name, short + full description (below), **app icon 512×512 PNG**, **feature graphic
1024×500 PNG**, **≥2 phone screenshots** (grab 4–8 from the app: Today, a lesson exercise,
Review, the journey, the tutor). Category **Education**. Contact email support@corlang.app.

### 4. Create the billing products **(browser)** — Monetize tab; IDs must match EXACTLY
- **Subscriptions → create `corlang_ai_premium`**:
  - base plan `monthly`, auto-renewing, **€9.99/month**
  - base plan `annual`, auto-renewing, **€99/year**; add an **Offer** on it = **7-day free
    trial** phase
- **In-app products (managed) → create**: `unlock_a2` €4.99 · `unlock_b1` €7.99 ·
  `unlock_b2` €7.99 · `unlock_all` €14.99. **Activate** each; accept Google's regional prices.

### 5. Upload the AAB to **Internal testing** first **(browser)**
Internal testing = live in minutes, no review wait, up to 100 testers, **billing works**.
Testing → Internal testing → **Create release** → upload the AAB → add a release note → review
→ **Roll out**. (Closed testing — step 9 — is the separate 14-day production-eligibility clock.)

### 6. License testers **(browser)** — so purchases are free
Setup → **License testing** → add your + testers' Gmail addresses. These accounts get test
purchases: the real Play sheet appears but nothing is charged (auto-refunded), and trials/renewals
run on an accelerated clock. This is how you and testers exercise buying without paying.

### 7. Add testers + share the opt-in link **(browser)**
On the Internal testing track → **Testers** → create an email list → add testers → copy the
**opt-in URL** → send it. Each tester opens it, taps "Become a tester", installs from Play.

### 8. TEST the real billing **(you, on your phone)**
Install Corlang **from the Play opt-in link** with a license-tester Google account. Now:
- A2+ day → paywall shows **real prices** and the **real Play payment sheet** → buy → level unlocks.
- Profile → Get Premium → See plans → subscribe (7-day trial) → **Learn tab appears**, AI works.
- This is where you "see how the payment popups look."

### 9. Start the Closed-testing 14-day clock **(browser)** — for PRODUCTION eligibility
New personal developer accounts must run **Closed testing with ≥12 testers opted-in for ≥14
consecutive days** before they can apply for production. Create a Closed testing track, add the
same ≥12 testers, roll out the same AAB, and let the clock run **in parallel** with everything else.

### 10. Production hardening **(browser + one CLI)** — before PUBLIC launch, not before testers
- **Google Cloud service account** for subscription verification: the 4 steps in
  docs/server-ai.md ("Play subscription verification"), ending in
  `wrangler secret put PLAY_SERVICE_ACCOUNT`. Turns on server-side anti-refund enforcement.
- **Anthropic spend alert**: console.anthropic.com → Billing → usage alert (~$5).
- (Optional) Cloudflare WAF rate-limit rule on the worker route.

---

## Store listing copy (draft — edit freely)

**App name:** Corlang — Croatian, Portuguese & French

**Short description (≤80 chars):**
Learn Croatian, European Portuguese or French with a real day-by-day plan.

**Full description (≤4000 chars):**
Corlang is a focused, no-nonsense way to actually learn Croatian, European Portuguese, or
French — built around a structured day-by-day plan that takes you from absolute beginner (A0)
to upper-intermediate (B2), the level real exams and real life ask for.

Every day is one guided lesson: new words, short exercises, and spaced-repetition review that
brings vocabulary back exactly when you're about to forget it. No endless stre­ak-baiting, no
cartoon detours — just steady, measurable progress toward a level you can use.

What's inside:
• A complete A0→B2 course, one lesson a day, with clear objectives.
• Spaced-repetition flashcards (FSRS) so words actually stick.
• Quizzes and full mock exams modelled on the official tests (Croatian residency/citizenship,
  DELF for French, CAPLE for Portuguese).
• A progress journey you can see, and a daily streak to keep the habit.
• The whole course works offline. Your data stays on your device, no account, no tracking.
  (Only the optional AI tutor needs a connection.)

Free forever: the entire A0 and A1 course, all its words, quizzes and the A1 exam.
Optional upgrades: unlock A2, B1 and B2 as one-time purchases, and subscribe to Corlang Premium
for an AI tutor that chats with you in your language, reviews your exam writing, and checks your
explanations, all graded for your target level.

Croatian isn't on the big apps. European Portuguese gets treated as an afterthought. Corlang
takes both — and French — seriously, with exam-focused content and correct, native-quality
language.

**Contact email:** support@corlang.app
