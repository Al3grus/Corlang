# -*- coding: utf-8 -*-
"""Build the corlang.app static site into site/.

The privacy page is GENERATED FROM PRIVACY.md rather than written twice. Google re-checks the
privacy policy URL after publishing, and the app's own repo copy is what gets edited, so two
hand-maintained copies is a promise to drift. One source, one truth.

    python tools/site/build_site.py
    npx wrangler pages deploy site --project-name corlang --branch main --commit-dirty=true

Live at https://corlang.app/ (Cloudflare Pages project `corlang`). site/ is GENERATED: editing
those files by hand is throwing work away, because the next build overwrites them.
"""
import io
import os
import re
import html

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, 'site')

# The app's own light palette, so the site and the product look like one thing.
CSS = """
:root{
  --bg:#F6F0E6; --surface:#FFFBF3; --ink:#2B2118; --muted:#6B5B48;
  --outline:#D8CDBA; --accent:#2f7fae; --flame:#c8402c;
}
@media (prefers-color-scheme: dark){
  :root{
    --bg:#14171A; --surface:#1C2126; --ink:#ECE6DC; --muted:#A2988A;
    --outline:#2E353C; --accent:#63A8D6; --flame:#c8402c;
  }
}
*{box-sizing:border-box}
body{
  margin:0; background:var(--bg); color:var(--ink);
  font:16px/1.65 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;
  -webkit-font-smoothing:antialiased;
}
.wrap{max-width:720px;margin:0 auto;padding:0 24px}
header{padding:64px 0 8px}
.mark{display:flex;align-items:center;gap:14px;text-decoration:none;color:inherit}
.mark svg{width:40px;height:40px;flex:none}
/* LOGO_USAGE.md: the wordmark is Helvetica. */
.mark b{font-size:27px;letter-spacing:-0.02em;font-weight:600;
        font-family:'Helvetica Neue',Helvetica,Arial,sans-serif}
.cta{display:inline-block;background:var(--accent);color:#fff;font-weight:600;
     padding:14px 26px;border-radius:999px;margin:40px 0 12px}
.sub{color:var(--muted);font-size:15px;margin:0}
h1{font-size:clamp(30px,6vw,44px);line-height:1.15;letter-spacing:-0.03em;margin:36px 0 12px}
.lede{font-size:19px;color:var(--muted);margin:0 0 32px}
h2{font-size:20px;letter-spacing:-0.01em;margin:40px 0 10px}
h3{font-size:17px;margin:28px 0 8px}
p,li{color:var(--ink)}
a{color:var(--accent)}
.cards{display:grid;gap:14px;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));margin:28px 0}
.card{background:var(--surface);border:1px solid var(--outline);border-radius:14px;padding:18px}
.card h3{margin:0 0 6px;font-size:16px}
.card p{margin:0;color:var(--muted);font-size:15px}
.note{background:var(--surface);border:1px solid var(--outline);border-radius:14px;
      padding:16px 18px;color:var(--muted);font-size:15px}
footer{margin:64px 0 48px;padding-top:24px;border-top:1px solid var(--outline);
       color:var(--muted);font-size:14px}
footer a{margin-right:18px}
article h1{margin-top:24px}
article ul{padding-left:22px}
article code{background:var(--surface);border:1px solid var(--outline);
             border-radius:5px;padding:1px 5px;font-size:14px}
.eff{color:var(--muted);font-size:15px}
"""

# The real Orbit Core mark, copied from
# "Corlang language learning logo/design_handoff_corlang_loader/logo-orbit-core.svg".
# Two BROKEN rings (the dash arrays and rotations are the mark, not decoration) around a solid
# core. LOGO_USAGE.md: brand #2f7fae, core #c8402c, and both keep their colour on dark, so they
# are hard-coded here rather than themed to the page palette.
LOGO = """<svg viewBox="0 0 100 100" aria-hidden="true">
  <circle cx="50" cy="50" r="33" fill="none" stroke="#2f7fae" stroke-width="6"
          stroke-linecap="round" stroke-dasharray="132 76" transform="rotate(-52 50 50)"/>
  <circle cx="50" cy="50" r="21" fill="none" stroke="#2f7fae" stroke-width="6"
          stroke-linecap="round" stroke-dasharray="80 52" transform="rotate(128 50 50)"/>
  <circle cx="50" cy="50" r="9" fill="#c8402c"/>
</svg>"""


def page(title, description, body, canonical):
    return f"""<!doctype html>
<html lang="en-GB">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{html.escape(title)}</title>
<meta name="description" content="{html.escape(description)}">
<link rel="canonical" href="{canonical}">
<meta property="og:title" content="{html.escape(title)}">
<meta property="og:description" content="{html.escape(description)}">
<meta property="og:type" content="website">
<style>{CSS}</style>
</head>
<body>
<div class="wrap">
<header>
  <a class="mark" href="/">{LOGO}<b>Corlang</b></a>
</header>
{body}
<footer>
  <a href="/">Home</a><a href="/privacy/">Privacy</a><a href="mailto:support@corlang.app">Contact</a>
  <div style="margin-top:10px">Corlang &middot; core + language</div>
</footer>
</div>
</body>
</html>
"""


def md_to_html(md):
    """Enough Markdown for PRIVACY.md: headings, bullets, ordered items, bold, code, links."""
    def inline(t):
        t = html.escape(t)
        t = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', t)
        # single-asterisk italics, AFTER bold so the bold pass has consumed its pairs
        t = re.sub(r'(?<!\*)\*([^*\n]+?)\*(?!\*)', r'<em>\1</em>', t)
        t = re.sub(r'`(.+?)`', r'<code>\1</code>', t)
        t = re.sub(r'\[(.+?)\]\((.+?)\)', r'<a href="\2">\1</a>', t)
        # bare URLs and emails, after escaping so we do not re-link inside an existing tag
        t = re.sub(r'(?<!["\'>])(https?://[^\s<)]+)', r'<a href="\1">\1</a>', t)
        t = re.sub(r'(?<![">:])\b([\w.+-]+@[\w-]+\.[\w.]+)\b', r'<a href="mailto:\1">\1</a>', t)
        return t

    out, para, list_kind = [], [], None

    def flush_para():
        if para:
            out.append('<p>' + inline(' '.join(para)) + '</p>')
            para.clear()

    def flush_list():
        nonlocal list_kind
        if list_kind:
            out.append(f'</{list_kind}>')
            list_kind = None

    for raw in md.split('\n'):
        line = raw.rstrip()
        stripped = line.strip()
        if not stripped:
            flush_para()
            flush_list()
            continue
        h = re.match(r'^(#{1,4})\s+(.*)$', stripped)
        if h:
            flush_para()
            flush_list()
            level = len(h.group(1))
            out.append(f'<h{level}>{inline(h.group(2))}</h{level}>')
            continue
        b = re.match(r'^[-*]\s+(.*)$', stripped)
        o = re.match(r'^(\d+)\.\s+(.*)$', stripped)
        if b or o:
            flush_para()
            want = 'ul' if b else 'ol'
            if list_kind != want:
                flush_list()
                out.append(f'<{want}>')
                list_kind = want
            out.append('<li>' + inline((b or o).group(1 if b else 2)) + '</li>')
            continue
        if list_kind and line.startswith(('  ', '\t')):
            # continuation of the current bullet
            out[-1] = out[-1][:-len('</li>')] + ' ' + inline(stripped) + '</li>'
            continue
        para.append(stripped)
    flush_para()
    flush_list()
    return '\n'.join(out)


def build():
    os.makedirs(os.path.join(OUT, 'privacy'), exist_ok=True)

    landing = """
<h1>Ten minutes a day is enough.</h1>
<p class="lede">Short lessons in Croatian and Portuguese that fit into a real day, and stay with
you afterwards.</p>

<div class="cards">
  <div class="card"><h3>Little and often</h3><p>A lesson takes about ten minutes. Do it on the
    bus.</p></div>
  <div class="card"><h3>Words that stick</h3><p>Every word comes back just before you would
    forget it.</p></div>
  <div class="card"><h3>All the way to B1</h3><p>A full path from your first words to the
    official exam.</p></div>
  <div class="card"><h3>Yours alone</h3><p>No account, no ads. Your progress stays on your
    phone.</p></div>
</div>

<!-- LAUNCH SWITCH: replace this whole block with the Play button on the day the app goes
     public. Tracked in docs/PENDING.md under "On the day you go live". -->
<p class="cta">Coming soon to Google Play</p>
<p class="sub">Want in early? <a href="mailto:support@corlang.app">Ask for a test invite.</a></p>
"""
    io.open(os.path.join(OUT, 'index.html'), 'w', encoding='utf-8', newline='\n').write(
        page("Corlang — learn Croatian and Portuguese",
             "Short daily lessons in Croatian and Portuguese, from your first words to B1. "
             "No account, no ads.",
             landing, "https://corlang.app/"))

    md = io.open(os.path.join(ROOT, 'PRIVACY.md'), encoding='utf-8').read()
    body = '<article>' + md_to_html(md) + '</article>'
    io.open(os.path.join(OUT, 'privacy', 'index.html'), 'w', encoding='utf-8', newline='\n').write(
        page("Privacy Policy — Corlang",
             "Corlang has no account, no analytics and no tracking. What it stores, where, and "
             "the one case in which anything leaves your device.",
             body, "https://corlang.app/privacy/"))

    # Long-lived caching would strand a policy edit behind a CDN cache, and this is a document
    # Google re-reads. HTML revalidates every time; nothing here is big enough for it to matter.
    io.open(os.path.join(OUT, '_headers'), 'w', encoding='utf-8', newline='\n').write(
        "/*\n  X-Content-Type-Options: nosniff\n  Referrer-Policy: no-referrer\n"
        "  X-Frame-Options: DENY\n\n/*.html\n  Cache-Control: public, max-age=0, must-revalidate\n")
    print('built site/index.html and site/privacy/index.html')


if __name__ == '__main__':
    build()
