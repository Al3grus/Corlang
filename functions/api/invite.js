/**
 * POST /api/invite  { email, website }  ->  { ok: true }
 *
 * Stores an address so the tester can be invited to the Play closed test. A Cloudflare Pages
 * Function rather than a form service, for the same reason the fonts are self-hosted: a page
 * that says "no tracking" should not hand a visitor's address to a third party on the way in.
 *
 * The addresses live in the INVITES KV namespace, one key per address, so a repeat submission
 * overwrites rather than piling up. Read them with:
 *
 *     npx wrangler kv key list --namespace-id 8126fcfb51954368a9ba136df17fb5af --remote
 *
 * The --remote matters. Wrangler v4 reads LOCAL storage by default, so without it the list comes
 * back empty and looks exactly like a broken endpoint.
 *
 * Spam handling, in order of how much they actually stop:
 *   - `website` is a honeypot. It is hidden from people and irresistible to bots; anything in it
 *     is accepted with a cheerful 200 and thrown away, because telling a bot it failed just
 *     teaches it to try again.
 *   - one submission per IP per hour, counted in the same namespace.
 *   - length and shape checks, so the store cannot be used as free text storage.
 */

const MAX_EMAIL = 254;                 // RFC 5321
const RATE_SECONDS = 3600;

// Deliberately loose. Strict email regexes reject real addresses, and the only thing this needs
// to prevent is storing something that is obviously not an address.
const LOOKS_LIKE_EMAIL = /^[^\s@]+@[^\s@.]+\.[^\s@]{2,}$/;

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

  // Honeypot: pretend it worked.
  if (typeof data.website === 'string' && data.website.trim() !== '') {
    return json({ ok: true });
  }

  const email = String(data.email || '').trim().toLowerCase();
  if (!email || email.length > MAX_EMAIL || !LOOKS_LIKE_EMAIL.test(email)) {
    return json({ ok: false, error: 'That does not look like an email address.' }, 400);
  }

  if (!env.INVITES) {
    // Missing binding is a deploy mistake, not a visitor mistake: say so plainly rather than
    // accepting an address that goes nowhere.
    return json({ ok: false, error: 'Signups are not available right now.' }, 503);
  }

  const ip = request.headers.get('cf-connecting-ip') || 'unknown';
  const rateKey = `rate:${ip}`;
  if (await env.INVITES.get(rateKey)) {
    return json({ ok: false, error: 'You already asked. Check back shortly.' }, 429);
  }

  await env.INVITES.put(
    `invite:${email}`,
    JSON.stringify({
      email,
      at: new Date().toISOString(),
      country: request.headers.get('cf-ipcountry') || null,
    })
  );
  await env.INVITES.put(rateKey, '1', { expirationTtl: RATE_SECONDS });

  return json({ ok: true });
}

// A curious GET gets an answer instead of a Pages 404 page.
//
// Exported as onRequestGet, NOT as a catch-all onRequest: exporting both onRequest and
// onRequestPost makes the catch-all a middleware that runs FIRST, and returning undefined from
// it to "fall through" is not a documented contract. It cost an afternoon of a POST that
// answered {ok:true} while writing nothing.
export async function onRequestGet() {
  return json({ ok: false, error: 'POST an email address.' }, 405);
}
