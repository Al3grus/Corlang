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
- **App access**: **"All or some functionality is restricted."** Not the no-login answer —
  there is no sign-in, but most of the course is behind a one-time purchase, and a reviewer who
  cannot reach it cannot rate it. Add one instruction with **no** credentials:

  > Corlang has no account system and no sign-in, so no username or password is needed.
  > Installing gives immediate access to the whole of level A0 in both courses, 16 lessons in
  > Croatian and 10 in Portuguese, together with their vocabulary reviews, exercises and progress
  > screens. That covers every screen type in the app. The remaining levels are unlocked by
  > one-time in-app purchases. To review the paid content, please use the promo codes below.

  Then attach promo codes (Monetize → Promo codes, one per product). **Promo codes cannot be
  created until the products exist AND a build containing them has been uploaded to a track**,
  so this task is finished after step 5, not before it.
- **Ads**: contains no ads.
- **Content rating**: fill the questionnaire (educational, no objectionable content → Everyone/PEGI 3).
- **Target audience**: 13+ (avoids the strict children's-policy requirements).
- **Data safety**: learning data is local-only (deletion = uninstall), BUT declare the AI
  tutor's flow: user-typed chat text + profile name are transmitted off-device to our
  endpoint/Anthropic when the optional AI feature is used. Google counts transmitted-off-device
  as "collected"; declaring "no data collected" flat-out is a misdeclaration risk. Category
  "Other in-app messages", purpose app functionality, not shared for ads, optional. Matches
  PRIVACY.md.
- **Privacy policy**: paste the PRIVACY.md raw URL
  (`https://raw.githubusercontent.com/Al3grus/Corlang/main/PRIVACY.md`).
- Government/financial/health features: No.

### 3. Main store listing **(browser)** — copy is drafted below
App name, short + full description (below), **app icon 512×512 PNG**, **feature graphic
1024×500 PNG**, **≥2 phone screenshots** (grab 4–8 from the app: Today, a lesson exercise,
Review, the journey, the tutor). Category **Education**. Contact email support@corlang.app.

> **Order matters, and it is the reverse of what it looks like.** Play will not let you create
> in-app products until a build that includes the Play Billing Library has been **published to a
> track** — internal testing counts. So do step 5 (upload the AAB) BEFORE step 4. The Monetize
> tab's in-app products page stays inert until that upload exists.
>
> Confirmed against
> [Getting ready](https://developer.android.com/google/play/billing/getting-ready): "you should
> build and publish your app, creating your app and then publishing to any track, including the
> internal test track."

### 4. Create the billing products **(browser)** — Monetize tab; IDs must match EXACTLY
**Do this AFTER the upload in step 5.** Product IDs cannot be changed or reused once created, so
copy them character for character — a typo means creating a second product and abandoning the
first forever.

- **Subscriptions → create `corlang_ai_premium`**:
  - base plan `monthly`, auto-renewing, **€9.99/month**; add an **Offer** on it = **3-day free trial** phase (Google's minimum length; a trial spends real tokens, so it is set at the floor). No annual plan (AI economics may shift within a year).
  - **In-app products (managed) → create six**, three per language:
  `unlock_hr_a1` €4.99 · `unlock_hr_a2` €12.99 · `unlock_hr_b1` €24.99, and `unlock_pt_a1` ·
  `unlock_pt_a2` · `unlock_pt_b1` at the same prices. Each unlock is **cumulative** — it grants
  its level and every level below it — so the B1 product is the whole course and there is no
  separate bundle. **Activate** each; accept Google's regional prices. **Do not create an
  `unlock_*_b2`** — neither course has a B2 lesson, so it would sell nothing.

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
- Profile → Get Premium → See plans → subscribe (3-day trial) → **Learn tab appears**, AI works.
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

**App name:** Corlang — Learn Croatian & Portuguese

> Croatian and Portuguese ONLY. French, German, Italian and Spanish are authored but hidden
> (absent from `content/_index.json`), so naming them here would advertise something the
> installed app does not contain. Add a language to this copy on the release that unhides it.

**Short description (≤80 chars):**
Learn Croatian or European Portuguese with a real day-by-day plan.

**Full description (≤4000 chars):**
Corlang is a focused, no-nonsense way to actually learn Croatian or European Portuguese — built
around a structured day-by-day plan that takes you from the beginning to B1, the level real
exams and real life ask for.

Every day is one guided lesson: new words, short exercises, and spaced-repetition review that
brings vocabulary back exactly when you're about to forget it. No endless streak-baiting, no
cartoon detours — just steady, measurable progress toward a level you can use.

What's inside:
• A complete course to B1, one lesson a day, with clear objectives.
• Spaced-repetition flashcards (FSRS) so words actually stick.
• Quizzes and full mock exams modelled on the official tests (Croatian residency/citizenship,
  CAPLE for Portuguese).
• A progress journey you can see, and a daily streak to keep the habit.
• The whole course works offline. Your data stays on your device, no account, no tracking.
  (Only the optional AI tutor needs a connection.)

Free to start: the first lessons of each course, with their words and reviews, no account and
no payment. After that, unlock a level at a time or the whole course to B1 as a one-time
purchase — every unlock includes the levels below it, so nothing you have already studied ever
closes. Corlang Premium is a separate optional subscription for an AI tutor that chats with you
in your language, reviews your exam writing, and checks your explanations, all graded for your
target level.

Croatian isn't on the big apps. European Portuguese gets treated as an afterthought. Corlang
takes both seriously, with exam-focused content and correct, native-quality language.

**Contact email:** support@corlang.app
