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
 */

const ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
const ANTHROPIC_VERSION = "2023-06-01";

// Guardrails: only the models the app actually uses, and a hard output cap, so a leaked
// token can't run up the bill.
const ALLOWED_MODELS = new Set([
  "claude-haiku-4-5-20251001",
  "claude-sonnet-5",
]);
const MAX_TOKENS_CAP = 2048;
const MAX_BODY_BYTES = 100_000;

function authorize(request, env) {
  // Pre-billing: a shared app token. Post-billing: verify the Play purchase token here
  // (cache verdicts in KV; see docs/server-ai.md).
  return request.headers.get("x-corlang-auth") === env.APP_AUTH_TOKEN;
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

    const raw = await request.text();
    if (raw.length > MAX_BODY_BYTES) {
      return json(413, { error: { message: "Request too large." } });
    }

    let body;
    try {
      body = JSON.parse(raw);
    } catch {
      return json(400, { error: { message: "Invalid JSON." } });
    }

    if (!ALLOWED_MODELS.has(body.model)) {
      return json(400, { error: { message: "Model not allowed." } });
    }
    body.max_tokens = Math.min(body.max_tokens ?? 1024, MAX_TOKENS_CAP);

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
