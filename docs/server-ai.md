# Server-side AI + Premium subscription: what's built, what remains

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
| Dev fallback | Locally stored Anthropic key = dev entitlement | Done, keeps pre-launch testing working |

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
6. Remove the dev-key fallback from `PremiumManager` before public launch, or keep it behind
   `BuildConfig.DEBUG` only.

## Pricing decision (open)

Freemium: the full course, SRS, exams stay free forever; Premium = the AI tutor. Comparable
anchors: Duolingo Max ~$14-30/mo, Busuu Premium Plus ~$7-14/mo, Speak ~$8-20/mo. A fair
opening position for a single-language niche app: ~EUR 5-8/month or ~EUR 40-60/year.
Decide at Play launch; nothing in the code depends on the price.
