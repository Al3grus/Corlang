# Corlang — Master pending checklist

Single source of truth for everything left to do, as of **2026-07-18**. Pick items one by one.
Deep detail lives in: `road-to-play.md` (Play steps + store copy), `server-ai.md` (subscription
verification setup), `monetization-roadmap.md` (pricing + product IDs), `PRE-LAUNCH-TODO.md`
(content/QA log). Legend: **(browser)** = Play Console / Google Cloud, you do it · **(me)** =
ask Claude to do it · **(you, phone)** = on-device.

---

## ✅ Already done this session — DO NOT redo
- Deep code audit (36 findings) fully fixed; 73/73 tests green (v0.20.30–32).
- AI proxy token **rotated** (old leaked token is dead/403); worker hardened + deployed:
  per-IP 300/day + global 3000/day rate limits, empty-secret deny, timing-safe auth,
  field allowlist, byte-accurate body cap.
- **Billing layer built** (v0.20.32): Play Billing 7.1.1, `BillingManager`, `PaywallScreen`,
  level gate, Get Premium, entitlement layer. Subscription + one-time unlocks.
- **40 msg/day per-subscriber cap** live in worker (keyed on Play sub token). Real cost
  measured: hr €0.0036/msg, pt/fr €0.0007/msg.
- **Server-side Play subscription verification** coded + deployed (v0.20.33), **dormant** until
  the `PLAY_SERVICE_ACCOUNT` secret is added (see Track B).
- Pricing research done (deep-research, 23 verified claims) + plan written.
- Play AAB built: `app/build/outputs/bundle/playRelease/app-play-release.aab` (v0.20.33, vc 86).
- Gated-preview sideload APK (DEV_PREMIUM=false) built + sent to you.

---

## 🔴 TRACK A — Get to Play testers (critical path, in order)

1. **(browser)** Create the app in Play Console (name "Corlang", App, Free).
2. **(browser)** "Set up your app" tasks — all required before any release:
   - App access: all functionality available without special access (no login).
   - Ads: no ads.
   - Content rating questionnaire (educational → Everyone/PEGI 3).
   - Target audience: 13+.
   - Data safety: **no data collected, no data shared** (local-only); deletion = uninstall.
   - Privacy policy URL: `https://raw.githubusercontent.com/Al3grus/Corlang/main/PRIVACY.md`.
3. **(browser + assets)** Main store listing. Copy is drafted in `road-to-play.md`. Needs the
   assets from Track C (icon, feature graphic, screenshots).
4. **(browser)** Create the 4 billing products — **IDs must match exactly** (in `road-to-play.md`
   / `monetization-roadmap.md`):
   - Subscription `corlang_ai_premium`: base plan `monthly` €9.99, base plan `annual` €99 +
     **7-day free-trial offer** on annual.
   - Managed products: `unlock_a2` €4.99, `unlock_b1` €7.99, `unlock_b2` €7.99, `unlock_all` €14.99.
   - Activate all; accept Google's regional prices.
5. **(browser)** Upload the AAB to **Internal testing** (live in minutes, billing works).
6. **(browser)** License testing (Setup → License testing): add your + testers' Gmail addresses
   so their purchases are free / auto-refunded.
7. **(browser)** Add testers + share the opt-in link.
8. **(you, phone)** Install from the Play opt-in link with a license-tester account → **see the
   real payment popups**, buy A2 → unlock, subscribe → Learn tab appears. First real end-to-end
   billing test.
9. **(browser)** Start **Closed testing** with **≥12 testers for ≥14 consecutive days** — the
   production-eligibility clock for new personal dev accounts. Run in parallel with everything.

---

## 🔐 TRACK B — Security hardening (before PUBLIC production; NOT needed for testers)

1. **(browser + 1 CLI)** Google Cloud **service account** for subscription verification — the
   4 steps in `server-ai.md` → "Play subscription verification":
   - Enable Google Play Android Developer API in the linked Cloud project.
   - Create service account `corlang-play-verifier`, download JSON key.
   - Grant its email "View financial data / app info" in Play Console → Users and permissions.
   - `cd server/ai-proxy && wrangler secret put PLAY_SERVICE_ACCOUNT` (paste JSON at hidden prompt).
   - Verify: invalid sub token → 403, real subscriber → 200. (Turns the dormant worker code ON.)
2. **(browser)** **Anthropic spend alert**: console.anthropic.com → Billing → usage alert (~$5).
3. **(browser, optional)** Cloudflare WAF rate-limit rule on the worker route (belt-and-suspenders
   over the KV daily caps).

---

## 🎨 TRACK C — Creative assets (blocks the store listing, step A3)

1. **App icon** 512×512 PNG. **(me** can generate a candidate; **you** finalize.)
2. **Feature graphic** 1024×500 PNG. **(me** can generate.)
3. **Phone screenshots** ×4–8 — capture from the app: Today dashboard, a lesson exercise,
   Review/SRS, the level journey, the AI tutor. **(you, phone** — or **me** via emulator.)

---

## 🗣️ TRACK D — Content quality (native-speaker review; parallel, non-blocking)

1. Croatian native review (`docs/review/hr-content-review.html`).
2. Portuguese native review (sister; `docs/review/pt-content-review.html`).
3. French native review (`docs/review/fr-content-review.html`).
4. **(me)** Fold returned corrections into the JSON; tests + ai-variety-eval re-verify.
5. **(you, phone)** TTS pronunciation spot-check per language.

---

## 🧪 TRACK E — QA on a real device (before production)

1. Full end-to-end: onboarding → placement → lesson (all step types) → review → quizzes →
   mock exam → cross midnight (streak) → switch languages.
2. Verify streak resets correctly after a genuinely missed day.
3. Backup export/import round-trips cleanly.
4. Confirm the paywall/purchase/unlock flow on the Internal track (overlaps Track A step 8).

---

## 🔮 TRACK F — Future / optional (not launch blockers)

1. **Voice tutor** — on-device STT/TTS → Claude text (≈ free on top of current cost; the app
   already has `TtsManager` + `SpeechInput`). A normal feature release.
2. **Realtime "Lily-style" voice** (streaming audio model) — deferred, genuinely expensive.
3. One-page **corlang.app** site (Cloudflare Pages) — nice-to-have; move privacy policy there.
4. **Repo private?** — deferred: going private now breaks the raw.githubusercontent updater and
   Play APKs are extractable anyway. Revisit for content-IP *after* testers move to the Play track.
5. `tools/provider-bench.py` — compare Gemini/GPT cost/quality (needs your keys), if ever curious.

---

## Accounts / services summary
| Service | Status | Action |
|---|---|---|
| Google Play Console | ✅ have ($25, verified) | create app + products + tracks (Track A) |
| Google Cloud (service account) | ⬜ create | Track B step 1 |
| Anthropic | ✅ have (Corlang acct, ~$9 prepaid) | set spend alert (Track B step 2) |
| Cloudflare (worker + KV) | ✅ deployed | optional WAF rule (Track B step 3) |
| corlang.app domain + proton email | ✅ have | optional website (Track F) |

## Key facts the next session needs
- Latest release: **v0.20.33 / versionCode 86**. Play AAB path above.
- Worker: `https://corlang-ai-proxy.ricardo-infante.workers.dev`; secrets `ANTHROPIC_API_KEY`,
  `APP_AUTH_TOKEN` (rotated), KV `RATE_KV` id `7869cfd96a8f4851905855404e6d4df0`; add
  `PLAY_SERVICE_ACCOUNT` in Track B.
- Package name: `com.corlang.app`. Product IDs: `corlang_ai_premium` (base plans `monthly`,
  `annual`), `unlock_a2`, `unlock_b1`, `unlock_b2`, `unlock_all`.
- **`local.properties` is currently `devPremium=false`** (for your gated preview). The committed
  public sideload release is still devPremium=true (friends keep AI). Decide per future release.
- Build: no gradlew; JBR at Android Studio + gradle 8.14.5 in `~/.gradle/wrapper/dists`.
  Prices are NOT in code — they come from Play Console, so changing them never needs a rebuild.
