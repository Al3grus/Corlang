# -*- coding: utf-8 -*-
"""Build the corlang.app static site into site/.

    python tools/site/build_site.py
    npx wrangler pages deploy site --project-name corlang --branch main --commit-dirty=true

Live at https://corlang.app/ (Cloudflare Pages project `corlang`). site/ is GENERATED: editing
those files by hand is throwing work away, because the next build overwrites them.

Two things here are deliberate and easy to undo by accident:

1. The privacy page is GENERATED FROM PRIVACY.md rather than written twice. Google re-checks the
   privacy policy URL after publishing, and the repo copy is the one that gets edited, so two
   hand-maintained copies is a promise to drift.
2. The fonts are SELF-HOSTED in site/fonts/. A page whose pitch is "no tracking" must not ask
   every visitor's browser to announce itself to Google Fonts. Both files are the latin variable
   subsets, 70KB together.
"""
import io
import os
import re
import html

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, 'site')

# ---------------------------------------------------------------------------------------------
# Design direction
#
# The page has one job: make the METHOD legible in five seconds, because the method is the only
# thing here a classroom cannot do. So the signature element is the review interval itself, drawn
# to scale: a word returning after 1, 3, 7, 21, 60 days, with the gaps visibly widening. That
# widening IS spaced repetition, it is the actual mechanism in the app (FSRS), and it is the one
# graphic no competitor's landing page leads with.
#
# Palette is cool paper and ink rather than the warm cream every AI-designed page arrives at.
# Cool suits a page about memory research, and it lets the brand's two fixed colours do specific
# jobs: the blue carries structure, and the red core appears ONLY on the word being recalled.
#
# Type is Manrope (display, eyebrows, interval numerals) over Inter (body). Space Grotesk went
# first and was the wrong register: its quirky a/g/G read as a developer tool, and this is a page
# about learning. Manrope is humanist and slightly rounded, so it stays warm at 74px without
# becoming a personality font, and it holds up at 12px in the eyebrow and the tick labels, which
# a display-only face would not. Two families, no more.
# ---------------------------------------------------------------------------------------------

CSS = """
@font-face{font-family:'Manrope';src:url('/fonts/manrope.woff2') format('woff2');
  font-weight:400 800;font-style:normal;font-display:swap}
@font-face{font-family:'InterVar';src:url('/fonts/inter.woff2') format('woff2');
  font-weight:100 900;font-style:normal;font-display:swap}

:root{
  --paper:#FBFBFD;      /* cool paper, not cream */
  --raise:#FFFFFF;
  --ink:#131A22;
  --muted:#5C6B7A;
  --line:#E3E8EE;
  --blue:#2f7fae;       /* brand, from the logo */
  --deep:#123B57;
  --core:#c8402c;       /* brand core, used only for the word being recalled */
  --display:'Manrope',-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
  --body:'InterVar',-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;
}

*{box-sizing:border-box}
html{-webkit-text-size-adjust:100%}
body{margin:0;background:var(--paper);color:var(--ink);font-family:var(--body);
  font-size:17px;line-height:1.6;-webkit-font-smoothing:antialiased}
.wrap{max-width:960px;margin:0 auto;padding:0 28px}
a{color:var(--blue)}
:focus-visible{outline:2px solid var(--blue);outline-offset:3px;border-radius:4px}

/* ---- header ---- */
.top{display:flex;align-items:center;justify-content:space-between;padding:26px 0}
.mark{display:flex;align-items:center;gap:11px;text-decoration:none;color:inherit}
.mark svg{width:32px;height:32px;flex:none}
.mark b{font-family:var(--display);font-weight:800;font-size:20px;letter-spacing:-.03em}
.top nav a{font-size:14px;color:var(--muted);text-decoration:none;margin-left:22px}
.top nav a:hover{color:var(--ink)}

/* ---- hero ---- */
.hero{display:grid;grid-template-columns:1fr 300px;gap:44px;align-items:center;
  padding:62px 0 26px}
.hero-copy{max-width:600px}
/* Two phones, one of each theme, because "there is a dark one too" lands better shown than
   said. Slight rotation and overlap so they read as a pair rather than two loose rectangles. */
.hero-shots{position:relative;height:430px}
.hero-shots .ph{position:absolute;width:196px;border-radius:22px;padding:6px;
  border:1px solid #DCE2E9;background:var(--raise);
  box-shadow:0 22px 46px -20px rgba(19,26,34,.34)}
.hero-shots .ph img{display:block;width:100%;height:auto;border-radius:17px}
.hero-shots .ph.back{left:0;top:6px;transform:rotate(-5deg)}
.hero-shots .ph.front{right:0;top:64px;transform:rotate(4deg)}
.eyebrow{font-family:var(--display);font-weight:500;font-size:12px;letter-spacing:.16em;
  text-transform:uppercase;color:var(--blue);margin:0 0 22px}
h1{font-family:var(--display);font-weight:800;font-size:clamp(38px,7vw,70px);line-height:1.05;
  letter-spacing:-.04em;margin:0 0 24px}
h1 .soft{color:var(--muted)}
.lede{font-size:20px;line-height:1.55;color:var(--muted);margin:0;max-width:600px}
.actions{display:flex;align-items:center;gap:18px;flex-wrap:wrap;margin:38px 0 0}
.cta{display:inline-block;background:var(--ink);color:#fff;text-decoration:none;font-weight:600;
  font-size:16px;padding:15px 28px;border-radius:10px}
.cta:hover{background:var(--deep)}
.avail{font-size:14px;color:var(--muted)}

/* ---- the loop: four steps, then the interval ruler ---- */
.section{padding:54px 0 0;border-top:1px solid var(--line);margin-top:46px}
.section-head{display:flex;align-items:baseline;gap:16px;flex-wrap:wrap;margin:0 0 30px}
h2{font-family:var(--display);font-weight:800;font-size:clamp(25px,3.2vw,34px);
  letter-spacing:-.03em;margin:0}
.section-head p{margin:0;color:var(--muted);font-size:16px}

.loop{display:grid;grid-template-columns:repeat(4,1fr);gap:0;margin:0 0 8px}
.step{position:relative;padding:0 26px 0 0}
.step:last-child{padding-right:0}
.step .n{font-family:var(--display);font-weight:500;font-size:12px;letter-spacing:.14em;
  color:var(--blue);display:block;margin:0 0 12px}
.step h3{font-family:var(--display);font-weight:700;font-size:19px;letter-spacing:-.015em;
  margin:0 0 8px}
.step p{margin:0;color:var(--muted);font-size:15px;line-height:1.55}
/* the arrow between steps: drawn, not a character, so it lines up with the type */
.step:not(:last-child)::after{content:"";position:absolute;top:5px;right:12px;width:9px;height:9px;
  border-top:1.5px solid var(--line);border-right:1.5px solid var(--line);transform:rotate(45deg)}

.ruler{margin:38px 0 0;border:1px solid var(--line);border-radius:14px;background:var(--raise);
  padding:30px 30px 22px}
.ruler-cap{font-size:15px;color:var(--muted);margin:0 0 26px;max-width:640px}
/* Margins, not padding: an absolutely positioned child resolves its % against the PADDING box,
   so padding would not keep the 0% and 100% labels off the edges. */
.track{position:relative;height:72px;margin:0 52px}
.track .line{position:absolute;left:-18px;right:0;top:35px;height:1px;background:var(--line)}
.origin{position:absolute;left:-18px;top:26px}
.origin::before{content:"";display:block;width:2px;height:18px;background:var(--core);
  border-radius:1px}
.origin span{position:absolute;top:24px;left:50%;transform:translateX(-50%);
  font-family:var(--display);font-weight:500;font-size:12.5px;color:var(--core);white-space:nowrap}
.tick{position:absolute;top:0;transform:translateX(-50%);text-align:center}
.tick .dot{width:11px;height:11px;border-radius:50%;background:var(--blue);margin:30px auto 0}
.tick .lab{font-family:var(--display);font-weight:500;font-size:12.5px;color:var(--muted);
  margin-top:9px;white-space:nowrap}
.gap{position:absolute;top:14px;transform:translateX(-50%);font-family:var(--display);
  font-size:11px;letter-spacing:.06em;color:#9AA7B4;white-space:nowrap}

/* ---- the app itself ----
   Three phones, the middle one raised, on a band that separates them from the page. A horizontal
   scroller below 760px rather than a stack: three portrait phones stacked is a very long page,
   and swiping a row of screens is the gesture people already use in a store listing. */
.shots{margin:0;background:linear-gradient(180deg,#F4F6F9,#EEF1F5);border:1px solid var(--line);
  border-radius:18px;padding:44px 0 48px;overflow:hidden}
.shots-row{display:flex;gap:26px;justify-content:center;align-items:flex-end;padding:0 34px}
.phone{flex:0 0 236px;border-radius:26px;background:var(--raise);padding:7px;
  border:1px solid #DDE3EA;box-shadow:0 18px 40px -18px rgba(19,26,34,.28)}
.phone:nth-child(2){transform:translateY(-24px)}
.phone img{display:block;width:100%;height:auto;border-radius:20px}
.shots-cap{text-align:center;color:var(--muted);font-size:14.5px;margin:30px 34px 0}

@media (max-width:760px){
  .shots{border-radius:16px;padding:34px 0 38px}
  .shots-row{overflow-x:auto;justify-content:flex-start;scroll-snap-type:x mandatory;
    -webkit-overflow-scrolling:touch;padding:0 22px 8px}
  .phone{flex:0 0 210px;scroll-snap-align:center}
  .phone:nth-child(2){transform:none}
}

/* ---- plain claims ---- */
.claims{display:grid;grid-template-columns:repeat(3,1fr);gap:22px;margin:0}
.claim{border-top:2px solid var(--ink);padding-top:16px}
.claim h3{font-family:var(--display);font-weight:700;font-size:17px;margin:0 0 6px;
  letter-spacing:-.01em}
.claim p{margin:0;color:var(--muted);font-size:15px;line-height:1.55}

/* ---- invite dialog ----
   A native <dialog>: it traps focus, closes on Escape and returns focus on its own, which a div
   pretending to be a modal does not. */
dialog{border:none;border-radius:18px;padding:0;max-width:430px;width:calc(100% - 40px);
  background:var(--raise);color:var(--ink);box-shadow:0 30px 70px -24px rgba(19,26,34,.42)}
dialog::backdrop{background:rgba(14,20,26,.46);backdrop-filter:blur(2px)}
.dlg{padding:30px 30px 26px}
.dlg h3{font-family:var(--display);font-weight:800;font-size:23px;letter-spacing:-.025em;
  margin:0 0 8px}
.dlg p{margin:0 0 20px;color:var(--muted);font-size:15px}
.dlg label{display:block;font-size:13px;color:var(--muted);margin:0 0 7px}
.dlg input[type=email]{width:100%;font:inherit;font-size:16px;padding:13px 14px;
  border:1px solid var(--line);border-radius:10px;background:var(--paper);color:var(--ink)}
.dlg input[type=email]:focus{outline:none;border-color:var(--blue);
  box-shadow:0 0 0 3px rgba(47,127,174,.18)}
.hp{position:absolute;left:-9999px;width:1px;height:1px;overflow:hidden}
.dlg-row{display:flex;gap:10px;margin-top:18px}
.dlg-row button{font:inherit;font-weight:600;font-size:15px;padding:12px 20px;border-radius:10px;
  border:1px solid var(--line);background:var(--raise);color:var(--ink);cursor:pointer}
.dlg-row button.go{background:var(--ink);border-color:var(--ink);color:#fff;flex:1}
.dlg-row button.go:disabled{opacity:.55;cursor:default}
.msg{margin:14px 0 0;font-size:14.5px;min-height:20px}
.msg.bad{color:var(--core)}
.msg.good{color:var(--blue)}

/* ---- footer ---- */
footer{border-top:1px solid var(--line);margin-top:56px;padding:30px 0 48px;font-size:14px;
  color:var(--muted);display:flex;justify-content:space-between;gap:20px;flex-wrap:wrap}
footer a{color:var(--muted);text-decoration:none;margin-right:20px}
footer a:hover{color:var(--ink)}

/* ---- the privacy document ---- */
article{max-width:680px;padding:26px 0 0}
article h1{font-size:clamp(32px,5vw,44px);margin-bottom:18px}
article h2{margin:44px 0 12px}
article p,article li{font-size:16.5px;color:#2A343F}
article ul,article ol{padding-left:22px}
article li{margin-bottom:7px}
article code{background:var(--raise);border:1px solid var(--line);border-radius:5px;
  padding:1px 5px;font-size:14px}

@media (max-width:900px){
  .hero{grid-template-columns:1fr;gap:26px;padding-top:44px}
  .hero-shots{height:340px;max-width:420px;margin:0 auto;width:100%}
  .hero-shots .ph{width:172px}
  .hero-shots .ph.back{left:4%}
  .hero-shots .ph.front{right:4%;top:52px}
}
@media (max-width:760px){
  .track{margin:0 34px;height:78px}
  .tick .lab,.origin span{font-size:11.5px}
  .gap{font-size:10px}
  .ruler{padding:24px 18px 18px}
  .loop{grid-template-columns:1fr 1fr;gap:30px 0}
  .step:nth-child(2)::after{display:none}
  .claims{grid-template-columns:1fr;gap:18px}
  .hero{padding-top:48px}
  .section{padding:40px 0 0;margin-top:34px}
}
@media (max-width:430px){
  .loop{grid-template-columns:1fr}
  .step::after{display:none!important}
  .step{padding-right:0}
}

/* ---- motion ----------------------------------------------------------------------------
   Two ideas only. On load, the hero settles in one short sequence. On scroll, each group
   reveals once as it arrives, and the ruler plays left to right so the eye reads the widening
   gaps in the order they actually happen.

   Everything is opacity and transform, which the compositor handles without touching layout.
   Under prefers-reduced-motion NOTHING below applies, and because the resting state of every
   animated element is defined here rather than in the base rules, a reduced-motion visitor
   simply gets the finished page. */
@media (prefers-reduced-motion:no-preference){
  .rise{opacity:0;transform:translateY(14px);
    transition:opacity .62s cubic-bezier(.22,.68,.3,1),transform .62s cubic-bezier(.22,.68,.3,1)}
  .rise.in{opacity:1;transform:none}

  /* hero, on load: eyebrow, headline, lede, actions, one after another */
  .hero .rise{transition-delay:var(--d,0s)}

  /* the loop steps stagger across, the way you would read them */
  .loop.in .step{opacity:0;transform:translateY(12px);
    animation:rise .55s cubic-bezier(.22,.68,.3,1) forwards}
  .loop.in .step:nth-child(1){animation-delay:.02s}
  .loop.in .step:nth-child(2){animation-delay:.10s}
  .loop.in .step:nth-child(3){animation-delay:.18s}
  .loop.in .step:nth-child(4){animation-delay:.26s}
  @keyframes rise{to{opacity:1;transform:none}}

  /* the arrows draw themselves in after the step they follow */
  .loop.in .step::after{opacity:0;animation:fade .4s ease forwards;animation-delay:.34s}
  @keyframes fade{to{opacity:1}}

  /* the ruler: dots and gap labels, left to right, only once it is on screen */
  .tick,.gap{opacity:0}
  .track.in .tick{animation:pop .5s cubic-bezier(.2,.7,.3,1) forwards}
  .track.in .gap{animation:pop .5s ease forwards}
  .track.in .origin{animation:fade .4s ease forwards}
  @keyframes pop{from{opacity:0;transform:translateX(-50%) translateY(7px)}
                 to{opacity:1;transform:translateX(-50%) translateY(0)}}

  .claims.in .claim{opacity:0;animation:rise .5s cubic-bezier(.22,.68,.3,1) forwards}
  .claims.in .claim:nth-child(2){animation-delay:.08s}
  .claims.in .claim:nth-child(3){animation-delay:.16s}

  /* micro-interactions: small, and only where something is actually interactive */
  .cta{transition:background .18s ease,transform .18s ease}
  .cta:active{transform:translateY(1px)}
  .step h3,.tick .dot{transition:color .2s ease,transform .2s ease}
  .step:hover .n{color:var(--core)}
  .step .n{transition:color .2s ease}
}
"""

# The real Orbit Core mark, from
# "Corlang language learning logo/design_handoff_corlang_loader/logo-orbit-core.svg".
# Two BROKEN rings around a solid core; the dash arrays and rotations ARE the mark. LOGO_USAGE.md
# fixes brand #2f7fae and core #c8402c, including on dark, so they are not themed.
LOGO = """<svg viewBox="0 0 100 100" aria-hidden="true">
  <circle cx="50" cy="50" r="33" fill="none" stroke="#2f7fae" stroke-width="6"
          stroke-linecap="round" stroke-dasharray="132 76" transform="rotate(-52 50 50)"/>
  <circle cx="50" cy="50" r="21" fill="none" stroke="#2f7fae" stroke-width="6"
          stroke-linecap="round" stroke-dasharray="80 52" transform="rotate(128 50 50)"/>
  <circle cx="50" cy="50" r="9" fill="#c8402c"/>
</svg>"""


SCRIPT = """<script>
/* Reveal-on-arrive. Guarded three ways so the page is never left invisible: reduced-motion
   returns early, a browser without IntersectionObserver returns early, and every element is
   marked visible the moment it has played once. The CSS resting state is inside the
   prefers-reduced-motion query, so if this script never runs at all the page still renders
   finished rather than blank. */
(function(){
  var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (reduce || !('IntersectionObserver' in window)) return;
  var hero = document.querySelectorAll('.hero .rise');
  for (var i = 0; i < hero.length; i++) {
    hero[i].style.setProperty('--d', (0.06 * i + 0.05).toFixed(2) + 's');
  }
  requestAnimationFrame(function(){
    for (var j = 0; j < hero.length; j++) hero[j].classList.add('in');
  });
  var io = new IntersectionObserver(function(entries){
    entries.forEach(function(e){
      if (!e.isIntersecting) return;
      e.target.classList.add('in');
      io.unobserve(e.target);
    });
  }, { threshold: 0.2, rootMargin: '0px 0px -8% 0px' });
  document.querySelectorAll('.loop, .track, .claims, .shots, .section .rise')
    .forEach(function(el){ io.observe(el); });
})();

/* Invite dialog. Posts to /api/invite, which stores the address in Cloudflare KV.
   Progressive: the button does nothing without JS, so the mailto link stays in the footer as the
   way to reach a person either way. */
(function(){
  var dlg = document.getElementById('invite');
  var form = dlg && dlg.querySelector('form');
  var msg = document.getElementById('invite-msg');
  var send = document.getElementById('invite-send');
  var email = document.getElementById('invite-email');
  if (!dlg || !form || !dlg.showModal) return;

  document.querySelectorAll('[data-invite]').forEach(function(b){
    b.addEventListener('click', function(){
      msg.textContent = '';
      msg.className = 'msg';
      send.disabled = false;
      send.textContent = 'Send';
      dlg.showModal();
    });
  });

  form.addEventListener('submit', function(e){
    /* method="dialog" closes on any button. Only intercept the send button, so Close still
       closes and Escape still works. */
    if (dlg.returnValue === 'cancel') return;
    var pressed = e.submitter && e.submitter.value;
    if (pressed !== 'send') return;
    e.preventDefault();
    if (!email.checkValidity()) { email.reportValidity(); return; }

    send.disabled = true;
    send.textContent = 'Sending';
    msg.className = 'msg';
    msg.textContent = '';

    fetch('/api/invite', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        email: email.value,
        website: form.querySelector('[name=website]').value
      })
    }).then(function(r){ return r.json().catch(function(){ return {}; }).then(function(j){
      return { ok: r.ok && j.ok, error: j.error };
    }); }).then(function(res){
      if (res.ok) {
        msg.className = 'msg good';
        msg.textContent = 'Thanks. You are on the list.';
        send.textContent = 'Done';
        setTimeout(function(){ dlg.close(); }, 1400);
      } else {
        msg.className = 'msg bad';
        msg.textContent = res.error || 'That did not go through. Try again in a moment.';
        send.disabled = false;
        send.textContent = 'Send';
      }
    }).catch(function(){
      msg.className = 'msg bad';
      msg.textContent = 'No connection. Try again in a moment.';
      send.disabled = false;
      send.textContent = 'Send';
    });
  });
})();
</script>"""


def page(title, description, body, canonical, wide=True):
    return f"""<!doctype html>
<html lang="en-GB">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{html.escape(title)}</title>
<meta name="description" content="{html.escape(description)}">
<link rel="canonical" href="{canonical}">
<meta name="theme-color" content="#FBFBFD">
<meta property="og:title" content="{html.escape(title)}">
<meta property="og:description" content="{html.escape(description)}">
<meta property="og:type" content="website">
<meta property="og:url" content="{canonical}">
<link rel="preload" href="/fonts/manrope.woff2" as="font" type="font/woff2" crossorigin>
<link rel="preload" href="/fonts/inter.woff2" as="font" type="font/woff2" crossorigin>
<link rel="icon" href="/favicon.svg" type="image/svg+xml">
<style>{CSS}</style>
</head>
<body>
<div class="wrap">
  <header class="top">
    <a class="mark" href="/">{LOGO}<b>Corlang</b></a>
    <nav><a href="/privacy/">Privacy</a><a href="mailto:support@corlang.app">Contact</a></nav>
  </header>
{body}
  <dialog id="invite">
    <form class="dlg" method="dialog">
      <h3>Ask for a test invite</h3>
      <p>Leave your email and you will get a Play test link when the next round opens. Nothing
      else is sent, and the address is used for this only.</p>
      <label for="invite-email">Email address</label>
      <input id="invite-email" name="email" type="email" required autocomplete="email"
             placeholder="you@example.com" inputmode="email">
      <div class="hp" aria-hidden="true">
        <label for="invite-website">Leave this empty</label>
        <input id="invite-website" name="website" type="text" tabindex="-1" autocomplete="off">
      </div>
      <p class="msg" id="invite-msg" role="status" aria-live="polite"></p>
      <div class="dlg-row">
        <button class="go" value="send" id="invite-send">Send</button>
        <button value="cancel" formnovalidate>Close</button>
      </div>
    </form>
  </dialog>

  <footer>
    <div><a href="/">Home</a><a href="/privacy/">Privacy</a><a
      href="mailto:support@corlang.app">Contact</a></div>
    <div>Corlang &middot; core + language</div>
  </footer>
</div>
{SCRIPT}</body>
</html>
"""


def md_to_html(md):
    """Enough Markdown for PRIVACY.md: headings, bullets, ordered items, bold, italics, code,
    links. Verified after every build by diffing headings and list counts against the source."""
    def inline(t):
        t = html.escape(t)
        t = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', t)
        # single-asterisk italics AFTER bold, so the bold pass has consumed its pairs
        t = re.sub(r'(?<!\*)\*([^*\n]+?)\*(?!\*)', r'<em>\1</em>', t)
        t = re.sub(r'`(.+?)`', r'<code>\1</code>', t)
        t = re.sub(r'\[(.+?)\]\((.+?)\)', r'<a href="\2">\1</a>', t)
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
            out.append(f'<h{len(h.group(1))}>{inline(h.group(2))}</h{len(h.group(1))}>')
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
            out[-1] = out[-1][:-len('</li>')] + ' ' + inline(stripped) + '</li>'
            continue
        para.append(stripped)
    flush_para()
    flush_list()
    return '\n'.join(out)


# The interval ladder, drawn to scale on a sqrt axis: real day numbers, compressed enough that
# two months still fits beside one day. These are the app's own early intervals, not invented.
#
# The ladder starts at the FIRST REVIEW, not at the lesson. Including day 0 put the biggest
# visual gap between "learned" and "1 day" (12.9% against the next gap's 9.5%), so the picture
# said the gaps SHRINK and then grow, which is the opposite of the caption beside it. The lesson
# is drawn as an origin rule instead: clearly where the ladder starts, not a rung on it.
INTERVALS = [(1, "1 day", "+2 days"), (3, "3 days", "+4 days"), (7, "1 week", "+2 weeks"),
             (21, "3 weeks", "+5 weeks"), (60, "2 months", "")]


def ruler():
    span = INTERVALS[-1][0] ** 0.5
    ticks, gaps = [], []
    for i, (day, label, delta) in enumerate(INTERVALS):
        x = (day ** 0.5) / span * 100
        delay = f'animation-delay:{0.11 * i + .2:.2f}s'
        ticks.append(f'<div class="tick" style="left:{x:.2f}%;{delay}">'
                     f'<div class="dot"></div><div class="lab">{label}</div></div>')
        if delta:
            nxt = (INTERVALS[i + 1][0] ** 0.5) / span * 100
            gaps.append(
                f'<div class="gap" style="left:{(x + nxt) / 2:.2f}%;{delay}">{delta}</div>')
    return ('<div class="track"><div class="line"></div>'
            '<div class="origin"><span>you learn it</span></div>'
            + ''.join(gaps) + ''.join(ticks) + '</div>')


# The three the site shows. All LIGHT captures, because the page is light: a dark phone on cool
# paper reads as a foreign object. They are also three different answers to "what is this?" —
# the course, the exam it aims at, and the tutor.
# The hero pair: one screen in each theme, so "there is a dark one too" is shown rather than
# claimed. Different screens from the showcase below, so nothing repeats down the page.
HERO = [
    ('flashcard.jpeg', 'hero-dark.jpg'),
    ('light-theme-learn-tab.jpeg', 'hero-light.jpg'),
]

SHOWCASE = [
    ('light-theme-review-tab.jpeg', 'The Review tab, with words due today and the packs behind them'),
    ('light-theme-mock-exam-2.jpeg', 'A mock exam writing task in the official format'),
    ('light-theme-ai-tutor.jpeg', 'The AI tutor correcting a sentence in Portuguese'),
]


def shots():
    """Resize the captures for the web. 720px wide is 2x the size they are shown at, so they stay
    crisp on a phone, and JPEG q80 keeps each one under ~60KB."""
    from PIL import Image
    src = os.path.join(ROOT, 'docs', 'store-assets', 'screenshots')
    dst = os.path.join(OUT, 'shots')
    os.makedirs(dst, exist_ok=True)
    out = []
    hero = {}
    for name, stem in HERO:
        q = os.path.join(src, name)
        if not os.path.isfile(q):
            print('  missing hero shot %s' % name)
            continue
        im = Image.open(q).convert('RGB')
        w = 720
        im = im.resize((w, round(im.height * w / im.width)), Image.LANCZOS)
        im.save(os.path.join(dst, stem), 'JPEG', quality=80, optimize=True, progressive=True)
        hero[stem] = (im.width, im.height)
        print('  hero %s  %dx%d' % (stem, im.width, im.height))
    for name, alt in SHOWCASE:
        p = os.path.join(src, name)
        if not os.path.isfile(p):
            print('  missing screenshot %s, skipping' % name)
            continue
        im = Image.open(p).convert('RGB')
        w = 720
        im = im.resize((w, round(im.height * w / im.width)), Image.LANCZOS)
        stem = os.path.splitext(name)[0].replace('light-theme-', '') + '.jpg'
        im.save(os.path.join(dst, stem), 'JPEG', quality=80, optimize=True, progressive=True)
        out.append((stem, alt, im.width, im.height))
    return out, hero


def build():
    os.makedirs(os.path.join(OUT, 'privacy'), exist_ok=True)

    shot_list, hero = shots()
    hd, hdh = hero.get('hero-dark.jpg', (720, 1467))
    hl, hlh = hero.get('hero-light.jpg', (720, 1436))
    SHOTS_HTML = "\n".join(
        f'        <div class="phone"><img src="/shots/{f}" alt="{html.escape(alt)}" '
        f'width="{w}" height="{h}" loading="lazy" decoding="async"></div>'
        for f, alt, w, h in shot_list)

    landing = f"""
  <section class="hero">
    <div class="hero-copy">
      <p class="eyebrow rise">Spaced repetition &middot; 10 minutes a day</p>
      <h1 class="rise">Learn a language.<br><span class="soft">Remember it.</span></h1>
      <p class="lede rise">Short daily lessons built on how memory actually works, so the word
      you learn on Monday is still there in a month. No classroom, no commute, and no hundreds
      of euros before you can say a word.</p>
      <div class="actions rise">
        <button class="cta" type="button" data-invite>Ask for a test invite</button>
        <span class="avail">Coming soon to Google Play &middot; Croatian and Portuguese
        available</span>
      </div>
    </div>
    <div class="hero-shots rise">
      <div class="ph back"><img src="/shots/hero-dark.jpg" alt="A Corlang flashcard in the dark
        theme" width="{hd}" height="{hdh}" fetchpriority="high" decoding="async"></div>
      <div class="ph front"><img src="/shots/hero-light.jpg" alt="The Corlang Learn tab in the
        light theme" width="{hl}" height="{hlh}" fetchpriority="high" decoding="async"></div>
    </div>
  </section>

  <section class="section">
    <div class="section-head">
      <h2>How a word actually sticks</h2>
      <p>The same loop, every word, for as long as it takes.</p>
    </div>
    <div class="loop">
      <div class="step"><span class="n">01</span><h3>Meet it</h3>
        <p>A new word arrives inside a lesson, in a sentence you would really say.</p></div>
      <div class="step"><span class="n">02</span><h3>Use it</h3>
        <p>You write it and say it, rather than picking it out of four options.</p></div>
      <div class="step"><span class="n">03</span><h3>Recall it</h3>
        <p>The lesson ends by asking for it again, from memory, with no prompt.</p></div>
      <div class="step"><span class="n">04</span><h3>Meet it again</h3>
        <p>It comes back days later, and the gap grows every time you get it right.</p></div>
    </div>

    <div class="ruler">
      <p class="ruler-cap">That last step is the whole method. A word you answer correctly is
      scheduled further and further out, always just before you would have forgotten it. Fewer
      reviews, better recall.</p>
      {ruler()}
    </div>
  </section>

  <section class="section">
    <div class="section-head">
      <h2>This is the whole app</h2>
      <p>No dashboards to configure. A lesson, your reviews, and a tutor if you want one.</p>
    </div>
    <div class="shots rise">
      <div class="shots-row">
{SHOTS_HTML}
      </div>
      <p class="shots-cap">Shown in the light theme. There is a dark one too.</p>
    </div>
  </section>

  <section class="section">
    <div class="section-head">
      <h2>Three things that make it different</h2>
      <p>The rest is just a language course.</p>
    </div>
    <div class="claims">
      <div class="claim"><h3>A finish line</h3>
        <p>A fixed course from your first words to the official B1 exam, in its real format.
        Not an endless feed.</p></div>
      <div class="claim"><h3>Nothing to sign up for</h3>
        <p>No account, no ads, no tracking. Your progress stays on your phone, and you can
        export it whenever you like.</p></div>
      <div class="claim"><h3>Ten minutes</h3>
        <p>One lesson and its reviews. On the bus, in a queue, before bed.</p></div>
    </div>
  </section>
"""

    io.open(os.path.join(OUT, 'index.html'), 'w', encoding='utf-8', newline='\n').write(
        page("Corlang — learn a language, remember it",
             "Short daily lessons built on spaced repetition, from your first words to the "
             "official B1 exam. No account, no ads, no tracking.",
             landing, "https://corlang.app/"))

    md = io.open(os.path.join(ROOT, 'PRIVACY.md'), encoding='utf-8').read()
    io.open(os.path.join(OUT, 'privacy', 'index.html'), 'w', encoding='utf-8',
            newline='\n').write(
        page("Privacy Policy — Corlang",
             "Corlang has no account, no analytics and no tracking. What it stores, where, and "
             "the one case in which anything leaves your device.",
             '<article>' + md_to_html(md) + '</article>', "https://corlang.app/privacy/"))

    io.open(os.path.join(OUT, 'favicon.svg'), 'w', encoding='utf-8', newline='\n').write(
        LOGO.replace(' aria-hidden="true"', ' xmlns="http://www.w3.org/2000/svg"'))

    # `/*.html` was the rule here once and it matched nothing anyone visits: the pages are served
    # at `/` and `/privacy/`, never `/index.html`, so both fell through to default edge caching
    # and a redeploy kept serving the previous page. `/*` matches every path. Fonts are
    # content-hashed by nothing, so they get a short cache and revalidate.
    io.open(os.path.join(OUT, '_headers'), 'w', encoding='utf-8', newline='\n').write(
        "/*\n  X-Content-Type-Options: nosniff\n  Referrer-Policy: no-referrer\n"
        "  X-Frame-Options: DENY\n  Cache-Control: public, max-age=0, must-revalidate\n"
        "\n/fonts/*\n  Cache-Control: public, max-age=604800\n")
    print('built site/index.html, site/privacy/index.html, favicon, _headers')


if __name__ == '__main__':
    build()
