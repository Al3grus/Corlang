# Corlang — Master pending checklist

Single source of truth for everything left to do, as of **2026-08-23**. Pick items one by one.
Deep detail lives in: `road-to-play.md` (Play steps + store copy), `server-ai.md` (subscription
verification setup), `monetization-roadmap.md` (pricing + product IDs), `runbook.md` (every
command: build, validators, deploys, reading the KV sign-up lists), `PRE-LAUNCH-TODO.md`
(historical content/QA log, superseded). Legend: **(browser)** = Play Console / Google Cloud, you do it · **(me)** =
ask Claude to do it · **(you, phone)** = on-device.

---

## 📍 Where this actually stands (2026-08-21)

**Content is finished and machine-clean for both live courses.** hr and pt each pass every gate
there is: `check_wrapup` (344/0 and 240/0), `check_hr`/`check_pt`, `check_batch`,
`check_deck_examples`, `check_deck_sync`, `proctor`, and the 175-test Kotlin suite. fr, de, it and
es are authored but HIDDEN from `content/_index.json` and are not part of this launch.

**The Play AAB builds, signed, from the current source**: `./gradlew :app:bundlePlayRelease` →
`app/build/outputs/bundle/playRelease/app-play-release.aab` (current source is v0.59.0,
versionCode 194). Verified
on that artifact: signed, ships only hr and pt, carries `com.android.vending.BILLING`, and does
NOT carry `REQUEST_INSTALL_PACKAGES` (the self-updater is compiled out of the play flavor, which
Play requires). `corlang.devPremium=true` in local.properties is sideload-only and cannot reach
it: the play flavor hardcodes `DEV_PREMIUM=false`. **Rebuild it right before uploading** so the
versionCode is fresh.

**Nothing on the critical path is waiting on authoring or code.** The eight framed
screenshots exist and are spec-checked, both graphics are current, and the listing copy is
written. Everything left before testers is browser work in Play Console, plus the one attestation
below that must be made true before closed testing starts.

**Store listing, in progress 2026-08-23:** app name, short description, full description, both
graphics, screenshots, category (Education), tags, contact details and the AI asset declaration
("Don't label" — every asset is either a Pillow-drawn logo or a real device capture, no
generative model in the path).

---

## ✅ Already done this session — DO NOT redo
- Deep code audit (36 findings) fully fixed; 73/73 tests green (v0.20.30–32).
- AI proxy token **rotated** (old leaked token is dead/403); worker hardened + deployed:
  per-IP 300/day + global 3000/day rate limits, empty-secret deny, timing-safe auth,
  field allowlist, byte-accurate body cap.
- **Billing layer built** (v0.20.32): Play Billing 7.1.1, `BillingManager`, `PaywallScreen`,
  level gate, Get Premium, entitlement layer. Subscription + one-time unlocks.
- **30 msg/day per-subscriber cap** live in worker (keyed on Play sub token), disclosed
  on the paywall. Real cost
  measured: hr €0.0036/msg, pt/fr €0.0007/msg.
- **Server-side Play subscription verification** coded + deployed (v0.20.33), **dormant** until
  the `PLAY_SERVICE_ACCOUNT` secret is added (see Track B).
- Pricing research done (deep-research, 23 verified claims) + plan written.
- Play AAB built: `app/build/outputs/bundle/playRelease/app-play-release.aab` (rebuilt
  2026-08-21 at v0.47.2 / vc 176 — always rebuild right before uploading, step A5).
- Gated-preview sideload APK (DEV_PREMIUM=false) built + sent to you.
- **2026-07-19 (vc 120-134)**: all three courses at 250 lessons + full checkpoint set
  (quiz/readiness/mock per level), decks >=2500 words, adaptive 3-item-band placement,
  review-limit setting, per-course Reset progress, em-dash purge, strict accent grading,
  cross-language stale-frame fix, A0-default fixes; 108 tests green. See the session-resume
  memory for the full log.

---

## ✅ DONE 2026-08-22 — A0 is the basics, ten lessons, in both courses

Three passes to get here, and the last one was the cheap one. Recorded because the two wrong
turns are both attractive.

**Wrong turn one: relabelling.** Moving A1's opening lessons down into A0 leaves A1 below its
floor, and the floor is a quality claim rather than a total.
`everyCourseMeetsTheWeightedLessonFloor` refused it.

**Wrong turn two: a phrasebook.** The next attempt authored survival transactions for A0, ten
for Portuguese and seven for Croatian. They read well and they were wrong for the level: a
learner parroting "Zadržite ostatak" or "Tem troco de vinte euros?" owns no word in either
sentence, and the numbers inside them had never been taught. A0 has to hand over the pieces.

**What shipped.** A0 is now letters, greetings, introductions, numbers, yes and no, question
words, this and that, in both courses, ten lessons each. Neither course grew:

- **Croatian** moved six pure-grammar lessons up into A1 (genders, pronouns, verb endings,
  family vocabulary, nominative, accusative). A0 10, A1 67, A2 96, B1 171, still 344.
- **Portuguese** swapped its two opening blocks: the foundation came down into A0, and the
  survival lessons written in the previous pass went up to open A1, where they now reinforce
  grammar the learner has actually met. A0 10, A1 45, A2 70, B1 125, still 250.

Everything from Croatian lesson 17 and Portuguese lesson 21 kept its number, so no placement
band above those points moved.

**The constraint that forced the cheap answer, worth remembering:** the deck floor is
`deck >= lessons x 10`, and Croatian sits exactly on it at 3440 words for 344 lessons. Zero
spare. Portuguese had 68 words of slack, which is the only reason its earlier A0 could be
authored on top at all. Croatian cannot gain one lesson without ten more words.

The seven Croatian survival lessons drafted on the way are deleted rather than parked. They
would have to go into A1, which Croatian cannot grow into, and a draft nothing can consume is
dead data. They are in the history of this commit if the deck ever gains room.

## 🟡 TERMS OF SERVICE — live, with one clause deliberately missing

`TERMS.md` is the source and `https://corlang.app/terms/` is generated from it, the same way the
privacy page is generated from `PRIVACY.md`. It describes the app as it actually is: free first
level, one-time per-language unlocks, a monthly AI subscription, Google Play handling every
payment and refund, the tutor's daily allowance, and that Corlang prepares for official exams
without awarding anything.

**Governing law added 2026-08-22:** Belgian law, courts of Brussels, with the mandatory consumer
protections of the user's own EU country preserved above them. The operator is established in
Belgium. What remains is the controller identity, which has its own entry below.

Written as plain English, not by a lawyer. It is a reasonable and honest starting point for a
one-person app in testing; it is worth a professional read once real revenue is arriving.

Play Console has a field for a terms URL alongside the privacy one: use
`https://corlang.app/terms/`.

## ‼️ BLOCKING BEFORE CLOSED TESTING — the App access attestation is not yet true

App access was answered **Yes** (the app is payment-restricted) and saved on 2026-08-22 with the
box ticked that says *"Sign-in details in this declaration provide full access to all the features
and content within this app, including premium or paid content."* Play would not save the section
without it.

**That claim is currently false.** The sign-in details carry no promo codes, because codes need
products and products need a published build. Internal testing is not reviewed the way closed
testing and production are, so nothing breaks today. Before starting closed testing it must be
made true, and the form says plainly what happens otherwise: updates blocked, or removal.

To close it: create the products, generate **three** codes (unlocks are cumulative, so the B1
product is the whole course), and replace the text in App access with:

```
No accounts and no sign-in exist, so there is no username or password.

Installing gives immediate access to level A0 in both courses, ten lessons each: teaching blocks,
exercises, dialogues, vocabulary review, the level quiz and every progress screen. That covers
every screen type in the app.

Paid content is the later course levels (one-time purchases) and the AI tutor tab (subscription).
Redeem in the Play Store to unlock everything:
Croatian: <code>
Portuguese: <code>
AI tutor: <code>
```

Codes needed: `unlock_hr_b1`, `unlock_pt_b1`, `corlang_ai_premium`. Username and password stay
empty; the app genuinely has neither.

## 🔴 TRACK A — Get to Play testers (do these in this order)

**The order matters and is not the obvious one:** Play does not enable the in-app products page
until a build containing the Play Billing Library is published to a track, and promo codes need
the products to exist. So it is upload, then products, then codes, then App access. Verified
against Play Console Help 2026-08-22.

State verified 2026-08-22: everything below that is not a browser step is DONE.

- App: **v0.56.1, versionCode 191**. Build with
  `./gradlew :app:bundlePlayRelease` (JAVA_HOME = Android Studio JBR); the artefact lands at
  `app/build/outputs/bundle/playRelease/app-play-release.aab`. Confirmed signed, with
  `DEV_PREMIUM=false` and `ENABLE_UPDATER=false`, so no store build can ship the developer
  unlock.
- Listing assets, all present and spec-checked: `docs/store-assets/play-icon-512.png` (512x512),
  `feature-graphic-1024x500.png` (1024x500), and eight framed screenshots at 1080x1920 in
  `docs/store-assets/play/`.
- Listing copy: drafted in `road-to-play.md`, naming **Croatian and Portuguese only**.
- Privacy policy: `https://corlang.app/privacy/` (live).
- Terms of service: `https://corlang.app/terms/` (live). See the terms entry above for the one
  clause still missing.
- Data safety answers: drafted field by field in `docs/play-data-safety.md`, derived from the
  built manifest and dependency list rather than from memory.

1. **Create the app** in Play Console: name "Corlang", type App, Free.

2. **Main store listing.** Copy from `road-to-play.md`, assets from `docs/store-assets/`.
   Category Education, contact `support@corlang.app`.

3. **App content, everything except App access.** Ads: none. Content rating questionnaire:
   educational, no objectionable content, so Everyone / PEGI 3. Target audience: 13+. Data
   safety: follow `docs/play-data-safety.md` exactly. Privacy policy URL as above.

4. **Upload the AAB to Internal testing.** Live in minutes, no review wait, up to 100 testers,
   and billing works. This is what unlocks step 5.

5. **Create the seven products.** IDs are case-sensitive and can never be changed or reused once
   created, so copy them character for character.

   | Product | Type | Price |
   |---|---|---|
   | `corlang_ai_premium` | subscription, one base plan `monthly`, with a **3-day** free-trial offer | EUR 9.99/month |
   | `unlock_hr_a1` | one-time | EUR 4.99 |
   | `unlock_hr_a2` | one-time | EUR 12.99 |
   | `unlock_hr_b1` | one-time | EUR 24.99 |
   | `unlock_pt_a1` | one-time | EUR 4.99 |
   | `unlock_pt_a2` | one-time | EUR 12.99 |
   | `unlock_pt_b1` | one-time | EUR 24.99 |

   Unlocks are **cumulative**, so the B1 product is the whole course and there is deliberately no
   `_all`. There is no `_b2` either: neither course has a B2 lesson. Activate each and accept
   Google's regional prices. No annual plan (decided 2026-07-18: AI costs can shift within a
   year, and a sold annual locks twelve months of service at old economics).

6. **Generate promo codes** (Monetize, Promo codes), one per product, for the reviewer.

7. **App access: restricted.** There is no login, but the course is payment-gated, and a reviewer
   who cannot reach the paid content cannot rate it. Paste the wording from
   `docs/road-to-play.md` §2 and attach the codes from step 6.

8. **License testers** (Setup, License testing): your own and your testers' Google accounts, so
   every product can be bought with auto-refunded transactions and the whole
   paywall -> purchase -> unlock path gets exercised for real.

9. **Closed testing.** 12 testers, 14 continuous days, which is the production-eligibility
   requirement for a personal account created after 2023-11-13.

**Test on a device, because no unit test can:** that Play's purchase sheet, the acknowledge call
and restore-after-reinstall all land entitlement where `PremiumManager` expects it. `PaywallGateTest`
already pins everything offline (free window, cumulative grants, cross-language isolation, the
exam gate, the placement-seed ceiling, retired product ids).

## 🔐 TRACK B — Security hardening (before PUBLIC production; NOT needed for testers)

1. **(browser + 1 CLI)** Google Cloud **service account** for subscription verification. The
   worker code is written and dormant: it wakes the moment `PLAY_SERVICE_ACCOUNT` exists.

   Verified against Google's docs 2026-08-22, and one step in the old version of this list is
   gone: **you no longer link the developer account to a Cloud project.** The docs say so
   explicitly. Create the project, create the account in it, and grant that account access in
   Play Console; no linking anywhere.

   - Google Cloud Console, **new project** (any name; it exists only to own the account).
   - **APIs and Services**, enable **Google Play Android Developer API**.
   - **IAM and Admin**, **Service Accounts**, **Create service account**. No Cloud IAM role is
     needed: the only permission that matters is the Play Console one below.
   - On the account, **Keys**, **Add key**, **Create new key**, **JSON**. It downloads once.
   - Play Console, **Users and permissions**, **Invite new users**, paste the service account's
     email, and grant **View financial data, orders, and cancellation survey responses**. That
     is the permission `purchases.subscriptionsv2.get` needs, which is the call the worker
     makes.
   - Load the key as a secret. **`wrangler secret put` will not work here:** its hidden prompt
     reads ONE line and a service-account file is multi-line, so the paste is truncated. Use
     `secret bulk`, which takes a file and means the key is never pasted into a terminal at all.

     From `server/ai-proxy`, with the downloaded key at `KEYPATH`:

     ```
     python -c "import json,sys,io; raw=io.open(sys.argv[1],encoding='utf-8').read(); io.open('secrets.json','w',encoding='utf-8').write(json.dumps({'PLAY_SERVICE_ACCOUNT': raw}))" KEYPATH
     npx wrangler secret bulk secrets.json
     del secrets.json
     ```

     The wrapper stores the whole file as ONE JSON string, so the `
` escapes inside
     `private_key` survive: the worker's `JSON.parse` then hands `importKey` a key with real
     newlines, which is what it needs. Verified by round-tripping a dummy key of the same shape.
     Delete `secrets.json` AND the downloaded key afterwards; neither is gitignored by name.

     **Do not set a placeholder value to "test the plumbing".** The worker turns subscription
     verification ON the moment this secret exists, so a dummy key means every real subscriber
     gets a 403.
   - **Verify with `server/ai-proxy/check-play-access.py`**, because you cannot tell from
     outside whether this worked: `verifySubscription` FAILS OPEN. A signing failure, a disabled
     API and a missing Play Console permission all end in `return true`, so a broken setup does
     not error anywhere, it silently stops checking entitlement and lets everyone through. The
     fail-open is deliberate (a Google outage must not lock out paying users) and it is exactly
     what makes a silent misconfiguration possible.

     `python check-play-access.py "<path to key>"` mints a token the way the worker does and
     calls the same endpoint with a deliberately invalid purchase token. A 400/404/410 back
     means authorised and working; 401/403 means the Play Console grant is missing or has not
     propagated; an OAuth failure means the key or the API enablement.

   Until this exists, entitlement is granted client-side after Play's local signature check.
   That is fine for closed testing with license testers and is NOT fine for public launch.

2. ✅ **Anthropic spend alert + limits set** (2026-07-18); auto-reload confirmed off is the
   guard that bounds worst-case token abuse to the prepaid balance.
3. **(browser, optional)** Cloudflare WAF rate-limit rule on the worker route (belt-and-suspenders
   over the KV daily caps).

---

## 🎨 TRACK C — Creative assets (blocks the store listing, step A3)

All assets live in `docs/store-assets/` — see the README there.

1. ✅ **App icon** 512×512 — `docs/store-assets/play-icon-512.png`. Generated from the same
   Orbit Core geometry as the launcher icon, so store and phone match.
2. ✅ **Feature graphic** 1024×500 — `docs/store-assets/feature-graphic-1024x500.png`.
   Both are reproducible via `docs/store-assets/make_assets.py` (Pillow).
3. ✅ **Phone screenshots** — eight framed 1080x1920 PNGs in `docs/store-assets/play/`, built by
   `tools/store/make_store_shots.py` from the raw captures in `docs/store-assets/screenshots/`,
   mixing dark and light. Dimensions checked against Play's rules 2026-08-22.

   One quality note rather than a blocker: two of the captures were taken on a nearly empty
   install, so `01-learn-tab` shows a 2-day streak on lesson 1 of 61, and
   `03-light-theme-review-tab` shows 0 learned and 0 mastered. They are honest and they sell
   nothing. Re-shoot those two on a device with real progress and re-run the script; everything
   else about the listing is ready either way.

   Note the lesson counts in `01` are also pre-v0.54.0, from before A0 became ten lessons.

---

## 🗣️ TRACK D — Content quality (native-speaker review; parallel, non-blocking)

**Only hr and pt matter for this launch** — fr, de, it and es are hidden, so their review docs can
wait until they are unhidden. Nothing here blocks Internal or Closed testing.

1. Croatian native review (friend) — `docs/review/hr-content-review.html`.
2. Portuguese native review (sister) — `docs/review/pt-content-review.html`.
3. French native review (friend) — `docs/review/fr-content-review.html`.
4. German native review (reviewer found) — `docs/review/de-content-review.html`.
5. Italian native review — **(browser) find a reviewer, none assigned yet** —
   `docs/review/it-content-review.html`.
   **Docs regenerated 2026-08-03 against current content (all 5 shipped courses, dialogue
   scripts now included as a 5th section — previously only vocab/grammar/cheatsheet/quizzes
   were covered, so ~300+ machine-authored lesson dialogues had never surfaced to a
   reviewer). Reviewer-facing instructions + reporting format: `docs/review/REVIEWER-INSTRUCTIONS.md`.**
6. **(me)** Fold returned corrections into the JSON; tests + ai-variety-eval re-verify.
7. **(you, phone)** TTS pronunciation spot-check per language.

---

## 🧪 TRACK E — QA on a real device (before production)

1. Full end-to-end: onboarding → placement → lesson (all step types) → review → quizzes →
   mock exam → cross midnight (streak) → switch languages.
2. Verify streak resets correctly after a genuinely missed day.
3. Backup export/import round-trips cleanly.
4. Confirm the paywall/purchase/unlock flow on the Internal track (overlaps Track A step 8).

---

## 🇪🇺 GDPR — the operator is established in Belgium (confirmed 2026-08-22)

That makes Corlang an EU data controller, and two things follow that are not yet done. Neither
blocks closed testing; both should be settled before public launch.

1. **The privacy policy does not identify the controller.** GDPR Article 13 requires the
   controller's identity and contact details. The policy gives `support@corlang.app` and nothing
   else. It needs the operator's real name, and an address, which is a decision to make
   deliberately rather than something to be guessed at: a personal Play account publishes a
   personal address on the listing unless a business address is used instead.
2. **Play requires a trader address** for developers in the EU, shown publicly on the store
   listing. Same decision, same time.

Neither is a wording problem I can fix alone, which is why this sits here rather than being
quietly written into PRIVACY.md.

Terms of service already handle the law question: Belgian law, courts of Brussels, with the
mandatory consumer protections of the user's own country preserved above them.

## 🚀 ON THE DAY YOU GO LIVE (do not let these rot)

The app and the site both currently SAY they are not public. Both are true today and both become
wrong the moment production goes live, so they are listed here rather than trusted to memory.

1. **The landing page still says "in testing on Google Play and is not publicly available yet".**
   It is in `tools/site/build_site.py`, marked with a `LAUNCH SWITCH` comment. Replace that note
   with the Play link, then:
   ```bash
   python tools/site/build_site.py
   npx wrangler pages deploy site --project-name corlang --branch main --commit-dirty=true
   ```
   Editing `site/*.html` by hand does nothing: those files are generated and overwritten.
2. **README "Status" section** says the same thing. Update it too.
3. **Play Console privacy policy URL** → `https://corlang.app/privacy/`. Use this, not the raw
   GitHub link: that link was the last thing keeping the repo public.

   The site is LIVE at https://corlang.app/ (Cloudflare Pages project `corlang`, apex and
   www both CNAME to corlang.pages.dev, proxied; certificate active 2026-08-21).
4. **Then, and only then, the repo can go private** — the raw GitHub privacy URL is the last
   thing depending on it (the self-updater that also depended on it was removed in v0.48.0).
   `releases/` becomes deletable at the same time: nothing reads `version.json` any more.

---

## 🔮 TRACK F — Future / optional (not launch blockers)

1. **Voice tutor** — on-device STT/TTS → Claude text (≈ free on top of current cost; the app
   already has `TtsManager` + `SpeechInput`). A normal feature release.
2. **Realtime "Lily-style" voice** (streaming audio model) — deferred, genuinely expensive.
3. One-page **corlang.app** site (Cloudflare Pages) — nice-to-have; move privacy policy there.
4. **Repo private?** — the two blockers are GONE as of 2026-08-21: the self-updater was removed
   (v0.48.0) and the privacy policy now lives at https://corlang.app/privacy/, so nothing depends
   on the repo being public once Play Console points at that URL. `releases/` can be deleted at
   the same time; nothing reads `version.json` any more. Original note, kept for the reasoning:
   going private breaks the raw.githubusercontent updater and
   Play APKs are extractable anyway. Revisit for content-IP *after* testers move to the Play track.
5. `tools/provider-bench.py` — compare Gemini/GPT cost/quality (needs your keys), if ever curious.

---

## Accounts / services summary
| Service | Status | Action |
|---|---|---|
| Google Play Console | ✅ have ($25, verified) | create app + products + tracks (Track A) |
| Google Cloud (service account) | ⬜ create | Track B step 1 |
| Anthropic | ✅ have (Corlang acct, ~$9 prepaid) | ✅ spend alert + limits set |
| Cloudflare (worker + KV) | ✅ deployed | optional WAF rule (Track B step 3) |
| corlang.app domain + proton email | ✅ have | optional website (Track F) |

## Key facts the next session needs
- Latest release: **v0.47.2 / versionCode 176**. Live courses: **hr and pt only**.
- Worker: `https://corlang-ai-proxy.ricardo-infante.workers.dev`; secrets `ANTHROPIC_API_KEY`,
  `APP_AUTH_TOKEN` (rotated), KV `RATE_KV` id `7869cfd96a8f4851905855404e6d4df0`; add
  `PLAY_SERVICE_ACCOUNT` in Track B.
- Package name: `com.corlang.app`. Product IDs: `corlang_ai_premium` (base plan `monthly`
  only) plus one `unlock_<lang>_<level>` per paid level of each live course — the app derives
  these from content, so the full list is whatever `BillingManager.levelProductIds` builds.
  Unlocks are cumulative, so the top level's id is the whole-course bundle and there is no
  `_all`. Prices in `docs/monetization-roadmap.md`.
- **`local.properties` is currently `devPremium=true`**, which affects the SIDELOAD build only
  (friends keep AI). The play flavor hardcodes `DEV_PREMIUM=false`, so no Play build can ship
  free Premium by accident.
- Build: `./gradlew` IS in the repo; JAVA_HOME = Android Studio's JBR (JDK 21).
  Prices are NOT in code — they come from Play Console, so changing them never needs a rebuild.

---

## 📬 Where the test-invite emails go

The landing page's "Ask for a test invite" dialog posts to `/api/invite`
(`functions/api/invite.js`, a Cloudflare Pages Function). Addresses are stored in a Cloudflare
**KV namespace** on your own account, and nowhere else. No third-party form service is involved.

- Namespace: **`corlang_invites`**, id `8126fcfb51954368a9ba136df17fb5af`
- Bound to the Pages project `corlang` (production) as **`INVITES`** — declared in the root
  `wrangler.toml`, so a redeploy keeps it.
- One key per address, `invite:<email>`, holding `{email, at, country}`. A repeat submission
  overwrites rather than piling up.
- Rate-limit keys `rate:<ip>` also live there and expire after an hour.

Read the list:

```bash
npx wrangler kv key list --namespace-id 8126fcfb51954368a9ba136df17fb5af --remote
npx wrangler kv key get  --namespace-id 8126fcfb51954368a9ba136df17fb5af --remote "invite:someone@example.com"
```

**`--remote` is not optional.** Wrangler v4 reads LOCAL storage by default, so without it every
command returns empty and looks exactly like a broken endpoint — including `delete`, which will
cheerfully report success while leaving the real key in place.

Disclosed in `PRIVACY.md` under "The website": what is stored, why, that it goes nowhere else,
and that asking gets it deleted. If you ever switch this off, remove that section too.
