# Server-side AI + Premium subscription: what's built, what remains

> The full go-live sequence (repo private, updater removal, Play setup, in order of need)
> lives in docs/launch-plan.md; this file is the AI/billing detail it references.

Status (2026-07-12): the client and the server are BUILT. The only missing pieces are the two
external accounts: hosting (Cloudflare, free) and Google Play Billing (needs the Play Console
account used for publication).

## Already built

| Piece | Where | State |
|---|---|---|
| Entitlement layer | `app/.../billing/PremiumManager.kt` | Done. Everything gates on `premium.entitled` |
| Premium gating | Talk tab, exam writing feedback, Settings status card | Done |
| Proxy-mode AI client | `app/.../ai/AiClient.kt` + `ai/AiConfig.kt` | Done. Flips on when `proxyBaseUrl` is set |
| AI proxy server | `server/ai-proxy/` (Cloudflare Worker) | Done. Deploy with `wrangler deploy` |
| Entitlement source | Play purchase flag only (BYO-key fallback fully removed 2026-07-16) | Done |

## Step 1: hosting (do anytime, ~15 minutes)

Follow `server/ai-proxy/README.md`. Result: a worker URL. Set `AiConfig.proxyBaseUrl` and
`PROXY_AUTH_TOKEN`, ship a release. From that build on, the AI runs server-side on the
Corlang-owned key and the app never contains a secret.

## Step 2: Google Play Billing (needs the Play Console account)

1. Play Console: create the app listing, then Monetize > Products > Subscriptions:
   one subscription, suggested id `premium`, with a monthly and a yearly plan.
2. App: add the billing dependency in `app/build.gradle.kts`:
   `implementation("com.android.billingclient:billing-ktx:7.1.1")` (check latest).
3. New `billing/PlayBillingConnector.kt`: BillingClient lifecycle, `queryPurchasesAsync` on
   start, purchase flow launcher, and a listener that on purchase calls the proxy's
   verification endpoint, then `premium.grantFromPlayPurchase()`; on expiry/refund calls
   `premium.revoke()`. PremiumManager and all gating stay untouched.
4. Settings Premium card: swap "Coming soon" for a Subscribe button that launches the flow.
5. Server: add a `/v1/verify` route to the worker that checks the purchase token against the
   Play Developer API (service-account JSON as a secret), returns a short-lived session token,
   and change `authorize()` to accept that token (cache verdicts in Workers KV).
6. ~~Remove the dev-key fallback from `PremiumManager`~~ Done (2026-07-16): the BYO-key path
   was removed entirely; Play purchase is the sole entitlement source.

## Pricing decision (open)

Freemium: the full course, SRS, exams stay free forever; Premium = the AI tutor. Comparable
anchors: Duolingo Max ~$14-30/mo, Busuu Premium Plus ~$7-14/mo, Speak ~$8-20/mo. A fair
opening position for a single-language niche app: ~EUR 5-8/month or ~EUR 40-60/year.
Decide at Play launch; nothing in the code depends on the price.

## Play subscription verification (production hardening) — SETUP STEPS

The worker (`server/ai-proxy/worker.js`) already contains full server-side verification:
it exchanges a service-account JWT for a Google OAuth token and calls the Play Developer API
(`purchases.subscriptionsv2`) to confirm the `x-corlang-sub` token is an ACTIVE subscription,
caching verdicts in KV (6h valid / 10m invalid) so it doesn't call Google on every message.
It is **inert until the `PLAY_SERVICE_ACCOUNT` secret exists** — so closed testing and the
DEV_PREMIUM sideload build keep working now, and verification switches on the moment you add
the secret. Do these once, before the PUBLIC production launch (not needed for closed testing):

1. **Google Cloud service account.** In the Google Cloud project linked to your Play account:
   APIs & Services → Enable **Google Play Android Developer API**. Then IAM & Admin → Service
   Accounts → Create (e.g. `corlang-play-verifier`), no roles needed. Open it → Keys → Add key
   → JSON → download the key file.
2. **Grant it Play access.** Play Console → Users and permissions → Invite user → paste the
   service account's email (`...@...iam.gserviceaccount.com`) → grant **View financial data**
   and **View app information** (app-level is enough). Save.
3. **Give the worker the key** (type/paste the JSON at the hidden prompt — never in chat/args):
   `cd server/ai-proxy && wrangler secret put PLAY_SERVICE_ACCOUNT` then paste the entire JSON
   file contents, Enter. Redeploy is automatic on next `wrangler deploy` (or deploy once now).
4. **Verify.** With the secret set, a request carrying an invalid `x-corlang-sub` returns 403;
   a real subscriber's token returns 200. (The app also self-heals: `BillingManager` re-queries
   Play on every resume and revokes locally if the sub lapsed.)

Package name is pinned in the worker (`PACKAGE_NAME = "com.corlang.app"`). The verification
fails OPEN on transient Google errors (a Play outage must not brick paying users) and closed on
a definitive "not active" (410 / non-active state).
