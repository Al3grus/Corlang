/**
 * POST /api/request  { language, other, level, email, website }  ->  { ok: true }
 *
 * A language request from https://corlang.app/requests/, which exists so the next course is
 * chosen from what people actually ask for rather than from a guess. Same shape and the same
 * spam handling as the invite endpoint next door; see invite.js for why a Pages Function rather
 * than a form service.
 *
 * Stored in the REQUESTS namespace, one key per address per language, so somebody asking twice
 * for the same thing overwrites and somebody asking for two languages counts twice. Read them
 * with:
 *
 *     npx wrangler kv key list --namespace-id f50b424a885f41d18d93978d31fec609 --remote
 *
 * The --remote matters. Wrangler v4 reads LOCAL storage by default, so without it the list comes
 * back empty and looks exactly like a broken endpoint.
 */

const MAX_EMAIL = 254;                 // RFC 5321
const MAX_OTHER = 40;
const RATE_SECONDS = 3600;

const LOOKS_LIKE_EMAIL = /^[^\s@]+@[^\s@.]+\.[^\s@]{2,}$/;

// The page offers a fixed list, so anything else is a hand-crafted POST. "other" is the one
// escape hatch and carries its own free-text field, length-capped, because the whole point of
// this endpoint is to hear about languages that are not on our list yet.
const LANGUAGES = new Set([
  'croatian', 'portuguese', 'french', 'german', 'italian', 'spanish',
  'dutch', 'polish', 'greek', 'czech', 'swedish', 'danish', 'norwegian',
  'finnish', 'romanian', 'hungarian', 'bulgarian', 'slovak', 'slovenian',
  'serbian', 'ukrainian', 'turkish', 'irish', 'catalan', 'other',
]);
const LEVELS = new Set(['A1', 'A2', 'B1', 'B2', 'C1']);

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store',
    },
  });

export async function onRequestPost({ request, env }) {
  let data;
  try {
    data = await request.json();
  } catch {
    return json({ ok: false, error: 'Send JSON.' }, 400);
  }

  // Honeypot: pretend it worked, because telling a bot it failed only teaches it to retry.
  if (typeof data.website === 'string' && data.website.trim() !== '') {
    return json({ ok: true });
  }

  const language = String(data.language || '').trim().toLowerCase();
  if (!LANGUAGES.has(language)) {
    return json({ ok: false, error: 'Pick a language from the list.' }, 400);
  }

  const other = String(data.other || '').trim().slice(0, MAX_OTHER);
  if (language === 'other' && other === '') {
    return json({ ok: false, error: 'Tell us which language.' }, 400);
  }

  const level = String(data.level || '').trim().toUpperCase();
  if (!LEVELS.has(level)) {
    return json({ ok: false, error: 'Pick a level.' }, 400);
  }

  const email = String(data.email || '').trim().toLowerCase();
  if (!email || email.length > MAX_EMAIL || !LOOKS_LIKE_EMAIL.test(email)) {
    return json({ ok: false, error: 'That does not look like an email address.' }, 400);
  }

  if (!env.REQUESTS) {
    // A missing binding is a deploy mistake, not a visitor mistake. Saying so plainly beats
    // accepting a request that goes nowhere.
    return json({ ok: false, error: 'Requests are not open right now.' }, 503);
  }

  const ip = request.headers.get('cf-connecting-ip') || 'unknown';
  const rateKey = `rate:${ip}`;
  if (await env.REQUESTS.get(rateKey)) {
    return json({ ok: false, error: 'You already sent one. Try again later.' }, 429);
  }

  // Keyed on address AND language: two different requests from one person are two data points,
  // the same one twice is not.
  await env.REQUESTS.put(
    `request:${language}:${email}`,
    JSON.stringify({
      language,
      other: other || null,
      level,
      email,
      at: new Date().toISOString(),
      country: request.headers.get('cf-ipcountry') || null,
    })
  );
  await env.REQUESTS.put(rateKey, '1', { expirationTtl: RATE_SECONDS });

  return json({ ok: true });
}

// A curious GET gets an answer instead of a Pages 404 page. Exported as onRequestGet and NOT as
// a catch-all onRequest: exporting both makes the catch-all run first as middleware, which cost
// an afternoon on the invite endpoint.
export async function onRequestGet() {
  return json({ ok: false, error: 'POST a language request.' }, 405);
}
