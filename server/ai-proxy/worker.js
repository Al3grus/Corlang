/**
 * Corlang AI proxy - Cloudflare Worker.
 *
 * Sits between the app and the Anthropic API so the real API key NEVER ships in the APK:
 *
 *   app --(x-corlang-auth)--> this worker --(x-api-key: ANTHROPIC_API_KEY)--> api.anthropic.com
 *
 * Deploy (see README.md next to this file), then set AiConfig.proxyBaseUrl in the app to the
 * worker URL. Secrets are Worker environment bindings, never in this file.
 *
 * Environment:
 *   ANTHROPIC_API_KEY  (secret) the real key, billed to the Corlang account
 *   APP_AUTH_TOKEN     (secret) shared token the app presents pre-billing; replace the check
 *                      in authorize() with Play purchase-token verification when billing ships
 *                      (docs/server-ai.md has the checklist).
 *   RATE_KV            (KV namespace binding, optional but STRONGLY recommended) daily
 *                      request counter. Create with `wrangler kv namespace create RATE_KV`
 *                      and add the binding to wrangler.toml. If absent the worker still runs
 *                      but volume is unbounded — a token extracted from the public APK could
 *                      loop requests. Pair with a Cloudflare WAF rate-limit rule per IP.
 */

const ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
const ANTHROPIC_VERSION = "2023-06-01";

// Guardrails: only the models the app actually uses, and a hard output cap, so a leaked
// token can't run up a large PER-REQUEST bill. Aggregate volume is bounded by the KV daily
// quota below + the dashboard rate-limit rule; the Anthropic prepaid balance is the backstop.
const ALLOWED_MODELS = new Set([
  "claude-haiku-4-5-20251001",
  "claude-sonnet-5",
]);
const MAX_TOKENS_CAP = 2048;
const MAX_BODY_BYTES = 100_000;

// Daily ceilings (UTC midnight reset). Generous for real users — a heavy learner makes a few
// dozen calls/day — but a hard stop for an extracted-token loop. Global cap protects the
// prepaid balance even against a botnet spreading over IPs.
const DAILY_LIMIT_PER_IP = 300;
const DAILY_LIMIT_GLOBAL = 3000;

// The request may only carry the fields the app actually sends. Anything else — tools,
// mcp_servers, stream, metadata, containers — is stripped, so an extracted token cannot be
// used as a general-purpose Claude proxy beyond plain single-turn text calls.
const ALLOWED_FIELDS = new Set([
  "model", "max_tokens", "system", "messages", "thinking",
]);

/** Constant-time comparison so the shared token can't be brute-forced byte-by-byte. */
function timingSafeEqual(a, b) {
  const enc = new TextEncoder();
  const ab = enc.encode(a);
  const bb = enc.encode(b);
  if (ab.length !== bb.length) return false;
  let diff = 0;
  for (let i = 0; i < ab.length; i++) diff |= ab[i] ^ bb[i];
  return diff === 0;
}

function authorize(request, env) {
  // Pre-billing: a shared app token. Post-billing: verify the Play purchase token here
  // (cache verdicts in KV; see docs/server-ai.md).
  // An unset/empty secret must DENY, never allow: without this guard, a missing binding
  // made `"" === ""` authorize every request that sent an empty header.
  if (!env.APP_AUTH_TOKEN) return false;
  const presented = request.headers.get("x-corlang-auth") || "";
  return timingSafeEqual(presented, env.APP_AUTH_TOKEN);
}

/**
 * Per-IP + global daily counters in KV. Fail-open on KV errors (an outage must not brick
 * the tutor), fail-closed on limits. KV is eventually consistent, so a determined attacker
 * can overshoot by a small factor — fine: this bounds the damage curve, the WAF rule and
 * the prepaid balance do the rest.
 */
async function checkRateLimit(request, env) {
  if (!env.RATE_KV) return { ok: true };
  const day = new Date().toISOString().slice(0, 10);
  const ip = request.headers.get("cf-connecting-ip") || "unknown";
  const ipKey = `rl:${day}:ip:${ip}`;
  const globalKey = `rl:${day}:global`;
  try {
    const [ipCount, globalCount] = await Promise.all([
      env.RATE_KV.get(ipKey), env.RATE_KV.get(globalKey),
    ]);
    const ipN = parseInt(ipCount ?? "0", 10);
    const gN = parseInt(globalCount ?? "0", 10);
    if (ipN >= DAILY_LIMIT_PER_IP || gN >= DAILY_LIMIT_GLOBAL) {
      return { ok: false };
    }
    // Two-day TTL: today's keys expire on their own, no cleanup job needed.
    await Promise.all([
      env.RATE_KV.put(ipKey, String(ipN + 1), { expirationTtl: 172800 }),
      env.RATE_KV.put(globalKey, String(gN + 1), { expirationTtl: 172800 }),
    ]);
    return { ok: true };
  } catch {
    return { ok: true }; // KV hiccup: serve the user, don't 500 the tutor
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method !== "POST" || url.pathname !== "/v1/messages") {
      return json(404, { error: { message: "Not found." } });
    }
    if (!authorize(request, env)) {
      return json(403, { error: { message: "Not authorized." } });
    }
    const rate = await checkRateLimit(request, env);
    if (!rate.ok) {
      return json(429, { error: { message: "Daily limit reached. Try again tomorrow." } });
    }

    const raw = await request.text();
    // Byte length, not UTF-16 code units: a fully multi-byte body is ~3x its .length.
    if (new TextEncoder().encode(raw).length > MAX_BODY_BYTES) {
      return json(413, { error: { message: "Request too large." } });
    }

    let parsed;
    try {
      parsed = JSON.parse(raw);
    } catch {
      return json(400, { error: { message: "Invalid JSON." } });
    }
    // JSON.parse happily returns null/numbers/strings — only an object is a valid request.
    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
      return json(400, { error: { message: "Invalid request body." } });
    }

    // Allowlist fields (drops tools/stream/anything the app doesn't send).
    const body = {};
    for (const key of ALLOWED_FIELDS) {
      if (key in parsed) body[key] = parsed[key];
    }

    if (!ALLOWED_MODELS.has(body.model)) {
      return json(400, { error: { message: "Model not allowed." } });
    }
    // Coerce max_tokens defensively: a non-integer would serialize as NaN→null → upstream 400.
    const requested = Number.isInteger(body.max_tokens) ? body.max_tokens : 1024;
    body.max_tokens = Math.max(1, Math.min(requested, MAX_TOKENS_CAP));

    const upstream = await fetch(ANTHROPIC_URL, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-api-key": env.ANTHROPIC_API_KEY,
        "anthropic-version": ANTHROPIC_VERSION,
      },
      body: JSON.stringify(body),
    });

    // Pass the Anthropic response through verbatim; the app already parses this shape.
    return new Response(upstream.body, {
      status: upstream.status,
      headers: { "content-type": "application/json" },
    });
  },
};

function json(status, obj) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "content-type": "application/json" },
  });
}
