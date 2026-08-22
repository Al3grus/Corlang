# Corlang AI proxy

The server half of Corlang Premium's AI tutor. Keeps the Anthropic API key off the device:
the app calls this worker, the worker calls Anthropic. Already matched to the client
(`ai/AiClient.kt` proxy mode); deploying it and setting one URL in the app is all that's left.

## Deploy (Cloudflare Workers, free tier is plenty to start)

1. Create a Cloudflare account (free) and install the CLI: `npm install -g wrangler`
2. From this directory: `wrangler login`
3. Set the secrets:
   - `wrangler secret put ANTHROPIC_API_KEY`  (the Corlang-owned key from console.anthropic.com)
   - `wrangler secret put APP_AUTH_TOKEN`     (any long random string)
4. `wrangler deploy` - it prints the worker URL, e.g. `https://corlang-ai-proxy.<account>.workers.dev`

## Point the app at it

In `app/src/main/java/com/corlang/app/ai/AiConfig.kt`:
- `proxyBaseUrl` = the worker URL
- `PROXY_AUTH_TOKEN` = the same value as `APP_AUTH_TOKEN`

Build and ship. Every install now uses server-side AI; the on-device developer key path
stops being used automatically.

## Cost guardrails built in

- Model allowlist (only the two models the app uses)
- max_tokens hard cap (2048)
- Request size cap (100 KB)
- Auth token required on every request

## When Google Play Billing ships

Replace the shared-token check in `authorize()` with per-user Play purchase verification.
The full checklist is in `docs/server-ai.md`.

## Daily-allowance headers

Every response the worker produces carries the caller's remaining allowance, so the tutor can
show a counter without a second round trip:

- `x-corlang-remaining` — messages left today, after this one is counted.
- `x-corlang-limit` — the cap it is measured against.

The cap reported is the one that actually **binds this caller**: `DAILY_LIMIT_PER_SUB` (30) when
a `x-corlang-sub` token is presented, and `DAILY_LIMIT_PER_IP` (300) otherwise, which is the
sideload/DEV_PREMIUM case. Both headers ride on the 429 too, with `remaining: 0`, so the app
knows the difference between "slow down" and "that is all for today".

They are **omitted entirely** when the worker cannot say (no KV binding, or a KV error). The app
shows no counter in that case rather than a guessed one, so an older deploy simply means no
counter instead of a wrong one.
