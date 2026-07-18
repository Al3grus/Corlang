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
5. Pricing: see the research-backed table below.

## Pricing plan v1 (2026-07-18, from deep-research run wf_ef1ba23b-cca)

All prices are **charm, VAT-inclusive** — Google determines/charges/remits EU VAT, and EU
display rules require the shown price to equal the amount paid, so the €X.99 you set is what
the buyer pays. **Accept Google's suggested regional price tiers** (Croatia & Portugal auto-
adjust down ~25-35% — this matters because the Croatian citizenship market IS in Croatia).
Google's cut is **~15%** (10% service + 5% billing under the June 2026 EEA rules; ~10% if we
ever add external billing), so assume **~85% net**.

### FREE forever (the whole A0+A1 course)
Lessons, SRS words, quizzes, and the A1 end-of-level exam. No ads. This is the funnel — the
first paywall is at A1→A2, after the learner has felt the product work.

### One-time level unlocks (managed products — content has ZERO recurring cost → lifetime access)
| Product | Price | Note |
|---|---|---|
| **A2 unlock** | **€4.99** | first paid step, low-friction |
| **B1 unlock** | **€7.99** | the exam level: NN citizenship (B1), DELF/CAPLE B1 — highest willingness-to-pay |
| **B2 unlock** | **€7.99** | DELF/CAPLE B2 (research: DELF B1 & B2 are priced *identically* — don't tier B2 above B1) |
| **Full course bundle (A2+B1+B2)** | **€16.99** | ~20% off à-la-carte (€20.97); the "unlock everything" hero. This IS the lifetime-content option. |

Anchors: DELF one-time prep courses run **€59** (PrepMyFuture, Alliance Française); official
citizenship-exam apps sell at **£5.99 + a content-unlock IAP**. Our per-level prices sit well
below full-course anchors, matching the "small one-time purchase" design. Citizenship takers
already pay €50-150 for the test itself, so B1 at €7.99 is trivially cheap for that segment —
a future **"Exam Prep Pack"** (B1 + intensive mocks + AI-examiner credits) at ~€19.99 is a
lever to revisit once the base funnel is proven; NOT in v1.

### AI subscription — "Corlang Premium" (auto-renewing; unlocks the Learn tab: tutor chat + exam-writing feedback + teach-back review)
| Plan | Price | Effective | Note |
|---|---|---|---|
| **Monthly** | **€9.99** | — | education-app median; well under Ling €16.99 & Duolingo Max €14.99 |
| **7-day free trial** | — | once per user | on the monthly plan. Google requires 3d-3y; niche-language norm (Ling ships a 7-day trial) |

**No annual plan** — decided 2026-07-18. AI models and per-message costs can shift within a
year; a sold annual locks 12 months of service at old economics, and Play sub price increases
need subscriber notice/opt-in, so monthly keeps both sides free. Earlier drafts priced an
annual at €59.99 and €99; both are superseded.

**No lifetime AI** — deliberate. The AI has a real recurring per-user cost (Anthropic API), so
a one-time fee can't cover perpetual calls. (Babbel can sell lifetime because its content is
static and marginal-cost-zero; our AI is not.) Lifetime *content* exists — it's the bundle above.

### Margin guardrail (measured, not estimated)
Live-measured per message: Croatian on **Sonnet** €0.0036 (incl. thinking); pt/fr on **Haiku**
€0.0007. The per-subscriber cap is **30 msgs/day**, enforced in the worker and DISCLOSED on
the paywall ("fair use: up to 30 AI messages a day").
- Worst case (hr, cap-maxed every day): 30 x 30.4 x €0.0036 ≈ **€3.28/mo** API.
- Monthly €9.99 → roughly €6.8 net after VAT + Play fee: **profitable even at the cap**; a
  typical 5-15 msg/day user costs well under €1.7/mo.
- Backstops: the **300 req/day per-IP cap** and 3000/day global (anti-abuse) already exist.

### Revenue sanity check (set expectations honestly)
Freemium converts only **~2% of installs to paid** (RevenueCat; even mature Duolingo monetizes
~5% of MAU). So 1,000 installs ≈ ~20 payers. v1 monetization's job is **sustainability** (cover
API + Play fee + domain), not profit — and at these prices the AI sub covers its own API cost
with margin, so the app never loses money by being used. Growth is a separate problem from pricing.

### Play Console products to create (IDs must match BillingManager EXACTLY)
The billing layer shipped in the app (v0.20.32); it reads prices live from Play, so create
these products in Play Console → Monetize. Until they exist the paywall shows "unavailable"
(never a wrong/free price). Product IDs are case-sensitive and must match verbatim:

**Subscription** — Monetize → Subscriptions → create `corlang_ai_premium`:
- ONE base plan `monthly` — auto-renewing, **€9.99/month**. Add an **Offer** on it: a
  **7-day free trial** phase. Google requires trials of 3d-3y, once per user.
- No annual base plan (see the decision above). The app requests only `monthly`.

**One-time unlocks** — Monetize → In-app products (managed products):
- `unlock_a2` — **€4.99**
- `unlock_b1` — **€7.99**
- `unlock_b2` — **€7.99**
- `unlock_all` — **€16.99**  (bundle → grants A2+B1+B2, ~20% off à-la-carte; the app maps it to all three)

Accept Google's suggested **regional prices** for each. Set them as the tax-inclusive charm
prices above; Google handles EU VAT. The 30-msg/day per-subscriber cap is enforced in the
worker (keyed on the Play sub token the app sends), so the flat price stays safe.

### Testing implication
Closed-testing **license testers** (Play Console → Settings → License testing) can buy every
product above with auto-refunded/never-charged transactions — so the full purchase flow
(paywall → Play sheet → entitlement unlock → worker `/v1/verify`) gets real coverage before
production. Create all 5 products (3 level unlocks + bundle + the monthly-only subscription)
in Play Console when the billing build lands mid-testing-window.

## What is already true in the app (v0.20.27)

- Nav: Today · Review · **Learn (only when Premium)** · Progress · Profile.
- Profile = Settings · Language · Get Premium · References (uniform rows). Language switching
  and Settings both live here now; the top bar is a static flag indicator only.
- Progress = stats, assessment, exam readiness, CEFR ladder.
- Learn = Teach + Tutor (AI), subscription-gated (DEV_PREMIUM unlocks it for testing).
- Quizzes + mock exams still reachable from Progress (interim, until step 3 above).
