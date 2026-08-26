/**
 * Review state store for the hosted workbook (Cloudflare Pages Function).
 *
 *   GET  /api/review?k=<token>   -> the saved state, or {} if nothing saved yet
 *   PUT  /api/review?k=<token>   -> replaces the saved state
 *
 * The token is the whole access model, and that is a deliberate choice rather than an oversight.
 * One named reviewer holds one long random link; there are no accounts to build, nothing to
 * reset, and nothing for them to remember. The content behind it is already public in the repo,
 * so the only thing the token actually protects is the reviewer's own work from being
 * overwritten by a passer-by.
 *
 * Tokens live in the REVIEW_TOKENS secret as a comma-separated list of `name:token` pairs, so a
 * second reviewer is a secret update and no code change. The name becomes the KV key, which is
 * what lets two people review the same course without colliding.
 *
 * Bindings (see wrangler.toml):
 *   REVIEW_KV       KV namespace holding one entry per reviewer
 *   REVIEW_TOKENS   secret, e.g. "ana:8f3c...,marko:1b7e..."
 */

const MAX_BODY = 4 * 1024 * 1024; // a full review of 15k items is ~1MB of JSON; this is slack

/** Constant-time compare, so the endpoint cannot be used to guess a token byte by byte. */
function safeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string" || a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

/** Resolves a presented token to a reviewer name, or null. */
function reviewerFor(env, token) {
  if (!token || !env.REVIEW_TOKENS) return null;
  for (const pair of env.REVIEW_TOKENS.split(",")) {
    const idx = pair.indexOf(":");
    if (idx < 0) continue;
    const name = pair.slice(0, idx).trim();
    const secret = pair.slice(idx + 1).trim();
    if (name && secret && safeEqual(token, secret)) return name;
  }
  return null;
}

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json",
      // The workbook is served from this same origin, so no cross-origin access is ever needed.
      "cache-control": "no-store",
    },
  });

export async function onRequest(context) {
  const { request, env } = context;
  const url = new URL(request.url);
  const who = reviewerFor(env, url.searchParams.get("k"));

  if (!who) return json({ error: "unknown or missing token" }, 403);
  if (!env.REVIEW_KV) return json({ error: "REVIEW_KV binding missing" }, 500);

  const key = `review:${who}`;

  if (request.method === "GET") {
    const saved = await env.REVIEW_KV.get(key);
    return json(saved ? JSON.parse(saved) : {});
  }

  if (request.method === "PUT") {
    const len = Number(request.headers.get("content-length") || 0);
    if (len > MAX_BODY) return json({ error: "too large" }, 413);
    let body;
    try {
      body = await request.json();
    } catch {
      return json({ error: "invalid JSON" }, 400);
    }
    if (!body || typeof body !== "object") return json({ error: "invalid state" }, 400);

    // Stamped server-side: a reviewer's clock is not evidence of when work landed, and this is
    // the only way to tell "they stopped for the night" from "they stopped three weeks ago".
    body.savedAt = new Date().toISOString();
    body.reviewerKey = who;
    await env.REVIEW_KV.put(key, JSON.stringify(body));
    return json({ ok: true, savedAt: body.savedAt });
  }

  return json({ error: "method not allowed" }, 405);
}
