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

// Play Developer API — server-side subscription verification (production entitlement gate).
// Enabled only when the PLAY_SERVICE_ACCOUNT secret is set (the full service-account JSON);
// until then the worker keeps its pre-verification behavior so closed testing / sideload work.
// Setup: docs/server-ai.md → "Play subscription verification".
const PACKAGE_NAME = "com.corlang.app";
const ANDROIDPUBLISHER = "https://androidpublisher.googleapis.com/androidpublisher/v3";
const PLAY_SCOPE = "https://www.googleapis.com/auth/androidpublisher";

// Guardrails: only the models the app actually uses, and a hard output cap, so a leaked
// token can't run up a large PER-REQUEST bill. Aggregate volume is bounded by the KV daily
// quota below + the dashboard rate-limit rule; the Anthropic prepaid balance is the backstop.
const ALLOWED_MODELS = new Set([
  "claude-haiku-4-5-20251001",
  "claude-sonnet-5",
]);
const MAX_TOKENS_CAP = 2048;
// 30KB triples the largest legitimate request (tutor system prompt + 12 capped messages);
// a pasted document is refused here even from a modified client.
const MAX_BODY_BYTES = 30_000;

// Daily ceilings (UTC midnight reset). Generous for real users — a heavy learner makes a few
// dozen calls/day — but a hard stop for an extracted-token loop. Global cap protects the
// prepaid balance even against a botnet spreading over IPs.
const DAILY_LIMIT_PER_IP = 300;
const DAILY_LIMIT_GLOBAL = 3000;
// Per-subscriber daily message cap, keyed on the Play subscription token (x-corlang-sub).
// This is the cost guardrail that makes the flat-price subscription safe: at 30/day a
// cap-maxing Croatian user costs ~€3.3/mo (measured €0.0036/msg), comfortably under the
// monthly plan's net revenue. DISCLOSED on the paywall ("fair use: up to 30 AI messages a
// day") — the number here and that copy must move together.
const DAILY_LIMIT_PER_SUB = 30;

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
  const sub = request.headers.get("x-corlang-sub");
  const ipKey = `rl:${day}:ip:${ip}`;
  const globalKey = `rl:${day}:global`;
  // Per-subscriber cap only when a sub token is presented (real Play subscribers). DEV_PREMIUM
  // / sideload sends none and is bounded by the per-IP cap instead.
  const subKey = sub ? `rl:${day}:sub:${sub}` : null;
  try {
    const [ipCount, globalCount, subCount] = await Promise.all([
      env.RATE_KV.get(ipKey), env.RATE_KV.get(globalKey),
      subKey ? env.RATE_KV.get(subKey) : Promise.resolve(null),
    ]);
    const ipN = parseInt(ipCount ?? "0", 10);
    const gN = parseInt(globalCount ?? "0", 10);
    const sN = parseInt(subCount ?? "0", 10);
    if (ipN >= DAILY_LIMIT_PER_IP || gN >= DAILY_LIMIT_GLOBAL) {
      return { ok: false, reason: "ip/global" };
    }
    if (subKey && sN >= DAILY_LIMIT_PER_SUB) {
      return { ok: false, reason: "sub" };
    }
    // Two-day TTL: today's keys expire on their own, no cleanup job needed.
    await Promise.all([
      env.RATE_KV.put(ipKey, String(ipN + 1), { expirationTtl: 172800 }),
      env.RATE_KV.put(globalKey, String(gN + 1), { expirationTtl: 172800 }),
      subKey ? env.RATE_KV.put(subKey, String(sN + 1), { expirationTtl: 172800 })
             : Promise.resolve(),
    ]);
    return { ok: true };
  } catch {
    return { ok: true }; // KV hiccup: serve the user, don't 500 the tutor
  }
}

// ---- Play subscription verification ----

function b64url(bytes) {
  let s = "";
  const b = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  for (let i = 0; i < b.length; i++) s += String.fromCharCode(b[i]);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
function b64urlStr(str) { return b64url(new TextEncoder().encode(str)); }

/** Import the service account's PKCS8 PEM private key for RS256 signing. */
async function importKey(pem) {
  const body = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
  return crypto.subtle.importKey(
    "pkcs8", der.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]
  );
}

/** OAuth access token for the service account (cached in KV until ~5 min before expiry). */
async function getAccessToken(env) {
  const cacheKey = "play:access_token";
  if (env.RATE_KV) {
    const cached = await env.RATE_KV.get(cacheKey);
    if (cached) return cached;
  }
  const sa = JSON.parse(env.PLAY_SERVICE_ACCOUNT);
  const now = Math.floor(Date.now() / 1000);
  const header = b64urlStr(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = b64urlStr(JSON.stringify({
    iss: sa.client_email, scope: PLAY_SCOPE,
    aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600,
  }));
  const key = await importKey(sa.private_key);
  const sig = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claim}`)
  );
  const jwt = `${header}.${claim}.${b64url(sig)}`;
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt,
    }),
  });
  const data = await res.json();
  if (!data.access_token) throw new Error("no access_token from Google");
  if (env.RATE_KV) {
    await env.RATE_KV.put(cacheKey, data.access_token,
      { expirationTtl: Math.max(60, (data.expires_in ?? 3600) - 300) });
  }
  return data.access_token;
}

/**
 * True if [subToken] is an active Play subscription. Verdicts are cached in KV (6h for valid,
 * 10m for invalid) so we don't hit the Play API on every message. Fail-OPEN on transport errors
 * (a Google outage must not brick paying users); a definitive "not active" fails closed.
 */
async function verifySubscription(env, subToken) {
  const verdictKey = `play:verdict:${subToken}`;
  if (env.RATE_KV) {
    const cached = await env.RATE_KV.get(verdictKey);
    if (cached === "1") return true;
    if (cached === "0") return false;
  }
  try {
    const access = await getAccessToken(env);
    const url = `${ANDROIDPUBLISHER}/applications/${PACKAGE_NAME}` +
      `/purchases/subscriptionsv2/tokens/${encodeURIComponent(subToken)}`;
    const res = await fetch(url, { headers: { Authorization: `Bearer ${access}` } });
    if (res.status === 410) { // token permanently gone
      if (env.RATE_KV) await env.RATE_KV.put(verdictKey, "0", { expirationTtl: 600 });
      return false;
    }
    if (!res.ok) return true; // transient (5xx/quota): fail open, don't punish a real user
    const data = await res.json();
    const ok = data.subscriptionState === "SUBSCRIPTION_STATE_ACTIVE" ||
      data.subscriptionState === "SUBSCRIPTION_STATE_IN_GRACE_PERIOD";
    if (env.RATE_KV) {
      await env.RATE_KV.put(verdictKey, ok ? "1" : "0",
        { expirationTtl: ok ? 21600 : 600 });
    }
    return ok;
  } catch {
    return true; // signing / network failure: fail open
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
    // Production entitlement gate: when the service account is configured AND the caller
    // presents a sub token, it must verify as an ACTIVE Play subscription. A refunded/expired/
    // forged token is rejected here even though the client already granted itself entitlement.
    // No sub token (DEV_PREMIUM sideload) skips this and stays bounded by the per-IP cap.
    const subToken = request.headers.get("x-corlang-sub");
    if (env.PLAY_SERVICE_ACCOUNT && subToken) {
      const valid = await verifySubscription(env, subToken);
      if (!valid) {
        return json(403, {
          error: { message: "Your subscription isn't active. Reopen the app to refresh it." },
        });
      }
    }

    const rate = await checkRateLimit(request, env);
    if (!rate.ok) {
      const msg = rate.reason === "sub"
        ? "You've reached today's message limit. It resets tomorrow."
        : "Daily limit reached. Try again tomorrow.";
      return json(429, { error: { message: msg } });
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
