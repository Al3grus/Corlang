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
import json
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
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
.hero-shots .ph{position:absolute;width:196px;--r:26px;--bez:7px}
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
/* The ruler is drawn TO SCALE, so its labels sit at fixed percentages and cannot reflow. Below
   about 547px they start overlapping (measured: "2 months" against "3 weeks" goes first), and a
   phone gives the track roughly 216px. Rather than shrink the type into illegibility or drop the
   annotations that carry the point, the track keeps its floor and the container scrolls.

   Only the TRACK scrolls, not the caption above it, which should stay put and readable.

   660px, not the 547 the maths gives. That figure is what the TRACK needs; the track also sits
   inside side margins the min-width does not cover, and those margins differ by breakpoint. 560
   left "1 day" and "3 days" 5px short, and 620 still failed in the narrow band where the floor
   has kicked in but the wider desktop margins are still applied. 660 clears both. */
.track-scroll{overflow-x:auto;overflow-y:hidden;-webkit-overflow-scrolling:touch;
  scrollbar-width:thin;scrollbar-color:var(--line) transparent}
.track-scroll::-webkit-scrollbar{height:6px}
.track-scroll::-webkit-scrollbar-thumb{background:var(--line);border-radius:3px}
/* Margins, not padding: an absolutely positioned child resolves its % against the PADDING box,
   so padding would not keep the 0% and 100% labels off the edges. */
.track{position:relative;height:72px;margin:0 52px;min-width:660px}
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

/* ---- device shell ----
   A real handset silhouette rather than a white card: a dark body, a hairline rail catching
   light along the top edge, a hole-punch camera, and the two side buttons every phone has. The
   buttons are what sell it at a glance; a plain rounded rectangle reads as a card no matter how
   good the shadow is.

   --r and --bez drive everything, so one number changes the whole frame and the screen radius
   follows automatically. The screen radius MUST be the outer radius minus the bezel, or the
   corners look nested wrong at any size. */
.device{--r:30px;--bez:9px;position:relative;border-radius:var(--r);padding:var(--bez);
  background:linear-gradient(158deg,#39414B 0%,#1C2229 38%,#12161B 100%);
  box-shadow:
    inset 0 0 0 1px rgba(255,255,255,.09),
    inset 0 1px 0 rgba(255,255,255,.20),
    0 26px 52px -22px rgba(15,22,30,.46),
    0 3px 10px rgba(15,22,30,.14)}
.device .screen{position:relative;border-radius:calc(var(--r) - var(--bez));overflow:hidden;
  background:#0C1014;line-height:0}
.device .screen img{display:block;width:100%;height:auto}
/* hole-punch camera, centred: the app bar puts its logo on the left, so nothing is covered */
.device .screen::after{content:"";position:absolute;top:9px;left:50%;transform:translateX(-50%);
  width:7px;height:7px;border-radius:50%;background:#05070A;
  box-shadow:0 0 0 1px rgba(255,255,255,.14)}
/* power button, right */
.device::after{content:"";position:absolute;right:-2px;top:23%;width:2.5px;height:44px;
  border-radius:0 2px 2px 0;background:linear-gradient(#39414B,#1A1F26)}
/* volume rocker, left */
.device::before{content:"";position:absolute;left:-2px;top:17%;width:2.5px;height:70px;
  border-radius:2px 0 0 2px;background:linear-gradient(#39414B,#1A1F26)}
.shots{margin:0;background:linear-gradient(180deg,#F4F6F9,#EEF1F5);border:1px solid var(--line);
  border-radius:18px;padding:44px 0 48px;overflow:hidden}
.shots-row{display:flex;gap:26px;justify-content:center;align-items:flex-end;padding:0 34px}
.phone{flex:0 0 236px;--r:28px;--bez:8px}
.phone:nth-child(2){transform:translateY(-24px)}
.shots-cap{text-align:center;color:var(--muted);font-size:14.5px;margin:30px 34px 0}

@media (max-width:760px){
  .shots{border-radius:16px;padding:34px 0 38px}
  .shots-row{overflow-x:auto;justify-content:flex-start;scroll-snap-type:x mandatory;
    -webkit-overflow-scrolling:touch;padding:0 22px 8px}
  .phone{flex:0 0 210px;scroll-snap-align:center;--r:25px;--bez:7px}
  .phone:nth-child(2){transform:none}
}

/* ---- plain claims ---- */
.claims{display:grid;grid-template-columns:repeat(3,1fr);gap:22px;margin:44px 0 0}
.claim{border-top:2px solid var(--ink);padding-top:16px}
.claim h3{font-family:var(--display);font-weight:700;font-size:17px;margin:0 0 6px;
  letter-spacing:-.01em}
.claim p{margin:0;color:var(--muted);font-size:15px;line-height:1.55}

/* ---- the method ----
   A hairline list rather than another card grid: the page already has a numbered row (the loop)
   and a three-across block (the claims), and a third variation on "boxes in a row" would read as
   filler. Term on the left, argument on the right, which is the shape of the thing being said.

   The left column is allowed to shrink but never past the point where a two-word heading wraps
   awkwardly, hence minmax rather than a fraction. */
.method{margin:32px 0 0;border-top:1px solid var(--line)}
.method .row{display:grid;grid-template-columns:minmax(190px,.85fr) 2fr;gap:14px 30px;
  padding:24px 0;border-bottom:1px solid var(--line)}
.method .term{display:flex;align-items:baseline;gap:10px}
.method .n{font-family:var(--mono,var(--body));font-size:12px;font-weight:700;letter-spacing:.14em;
  color:var(--blue);flex:none}
.method h3{font-family:var(--display);font-weight:700;font-size:19px;letter-spacing:-.015em;
  margin:0;line-height:1.25}
.method p{margin:0;color:var(--muted);font-size:16px;line-height:1.62}
.method .row:last-child{border-bottom:0}
.lede + .lede{margin-top:15px}

/* ---- languages ----
   Real SVG flags, not emoji. A regional-indicator pair renders as a flag on Android, iOS and
   macOS and as the two bare letters "HR" on Windows, which has no flag glyphs at all, so an
   emoji here would have been broken for a large share of the people this page is for. The two
   files are public-domain Wikimedia SVGs with a viewBox added so they scale.

   The hairline border and the 3:2 box keep both flags the same visual size even though their
   official ratios differ (Croatia is 2:1, Portugal 3:2); object-fit does the rest without
   distorting either. */
.langs{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:18px;
  margin:30px 0 0}
.lang{display:flex;align-items:center;justify-content:center;text-align:center;gap:16px;
  padding:18px 20px;border:1px solid var(--line);
  border-radius:14px;background:var(--raise)}
.lang img{width:54px;height:36px;object-fit:cover;border-radius:4px;flex:none;
  border:1px solid rgba(19,26,34,.14);background:var(--paper)}
.lang h3{font-family:var(--display);font-weight:700;font-size:18px;letter-spacing:-.01em;
  margin:0 0 3px;line-height:1.2}
.lang .native{color:var(--muted);font-size:14px;display:block;margin:0}

/* ---- request form ----
   Only on /requests/, which nothing links to. Native selects rather than a custom dropdown: they
   are keyboard and screen-reader correct for free, and on a phone they open the platform picker
   the visitor already knows. */
.reqform{margin:26px 0 0;max-width:520px}
.reqform label{display:block;font-size:14.5px;font-weight:600;margin:0 0 6px}
.reqform select,.reqform input{width:100%;box-sizing:border-box;font:inherit;font-size:16px;
  padding:12px 13px;border:1px solid var(--line);border-radius:10px;background:var(--raise);
  color:var(--ink);margin:0 0 18px}
.reqform select:focus,.reqform input:focus{outline:2px solid var(--blue);outline-offset:1px}
.reqform .hp{position:absolute;left:-9999px;width:1px;height:1px;overflow:hidden}
.reqform button{font:inherit;font-weight:600;font-size:16px;padding:13px 22px;border:0;
  border-radius:10px;background:var(--ink);color:#fff;cursor:pointer}
.reqform button:disabled{opacity:.55;cursor:default}
.req-note{margin:18px 0 0;color:var(--muted);font-size:14.5px;line-height:1.6;max-width:60ch}

/* ---- FAQ ----
   Native <details>, so it works with JavaScript off, is keyboard operable and is announced
   correctly by a screen reader without a line of ARIA. `name` makes them mutually exclusive,
   which is the whole accordion behaviour with no script at all.

   The marker is a chevron DRAWN with two borders, pointing down when closed and rotating to
   point up when open. It was briefly a plus swapped for a minus through content:"\\2212",
   which reached the page as the digit 2: that escape sat inside an ordinary Python string,
   where \\221 is octal, so the CSS received a control character and a "2". Drawing it leaves
   nothing for a generator to mangle, and no font has to have the glyph. */
.faq{margin:30px 0 0;border-top:1px solid var(--line)}
.faq details{border-bottom:1px solid var(--line)}
.faq details:last-child{border-bottom:0}
.faq summary{list-style:none;cursor:pointer;padding:18px 34px 18px 0;position:relative;
  font-family:var(--display);font-weight:600;font-size:17.5px;letter-spacing:-.01em}
.faq summary::-webkit-details-marker{display:none}
.faq summary::after{content:"";position:absolute;right:7px;top:50%;width:8px;height:8px;
  border-right:2px solid var(--muted);border-bottom:2px solid var(--muted);
  transform:translateY(-70%) rotate(45deg);
  transition:transform .24s cubic-bezier(.16,.7,.3,1),border-color .24s ease}
.faq details[open] summary::after{transform:translateY(-30%) rotate(225deg);
  border-color:var(--blue)}
.faq summary:hover{color:var(--blue)}
.faq summary:hover::after{border-color:var(--blue)}
.faq p{margin:0 0 20px;color:var(--muted);font-size:16px;line-height:1.62;max-width:70ch}

/* The panel slides rather than snapping.
   ::details-content is the only handle on a <details> panel that does not mean wrapping the
   content in a div and reimplementing the disclosure in JavaScript. Two pieces make it work:

   - `interpolate-size: allow-keywords` lets block-size animate from 0 to AUTO. Without it the
     only options are a fixed height, which cuts long answers off, or a max-height guess, which
     makes short answers appear to pause before they finish opening.
   - `content-visibility` is a discrete property, so it flips instantly and would yank the
     content away the moment a panel starts closing. `allow-discrete` holds it visible for the
     whole transition, which is the difference between a close that slides and one that
     vanishes mid-slide.

   Inside the reduced-motion guard like every other movement on this page. A visitor who asks
   for stillness gets the instant open the browser does natively, and so does any browser
   without ::details-content, which is exactly the behaviour this replaces. Nothing to fall
   back FROM. */
@media (prefers-reduced-motion:no-preference){
  :root{interpolate-size:allow-keywords}
  .faq details::details-content{
    block-size:0;overflow:hidden;opacity:0;
    transition:block-size .32s cubic-bezier(.16,.7,.3,1),opacity .24s ease,
               content-visibility .32s allow-discrete}
  .faq details[open]::details-content{block-size:auto;opacity:1}
}

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

/* Which phone, asked before the address. Real radios in a real fieldset, moved off-screen and
   drawn as two pills: a radiogroup is what this IS, and rebuilding one out of buttons would mean
   reimplementing arrow-key roving and the checked state that screen readers already announce.
   The pill is the adjacent span, so `input:checked + span` and `input:focus-visible + span`
   style it without :has(). */
.pick{border:0;padding:0;margin:0 0 18px;min-width:0}
.pick legend{padding:0;font-size:13px;color:var(--muted);margin:0 0 7px}
.pick .opts{display:flex;gap:10px}
.pick label{flex:1;margin:0}
.pick input{position:absolute;opacity:0;width:1px;height:1px}
.pick span{display:block;text-align:center;font:inherit;font-size:15px;font-weight:600;
  padding:12px 10px;border:1px solid var(--line);border-radius:10px;background:var(--paper);
  color:var(--ink);cursor:pointer}
.pick input:checked+span{border-color:var(--ink);background:var(--ink);color:#fff}
.pick input:focus-visible+span{outline:2px solid var(--blue);outline-offset:2px}
.dlg-row{display:flex;gap:10px;margin-top:18px}
.dlg-row button{font:inherit;font-weight:600;font-size:15px;padding:12px 20px;border-radius:10px;
  border:1px solid var(--line);background:var(--raise);color:var(--ink);cursor:pointer}
.dlg-row button.go{background:var(--ink);border-color:var(--ink);color:#fff;flex:1}
.dlg-row button.go:disabled{opacity:.55;cursor:default}
.msg{margin:14px 0 0;font-size:14.5px;min-height:20px}
.msg.bad{color:var(--core)}
.msg.good{color:var(--blue)}

.addr{display:flex;align-items:center;gap:10px;margin:0 0 4px;
  border:1px solid var(--line);border-radius:10px;background:var(--paper);padding:12px 14px}
.addr code{flex:1;font-family:var(--body);font-size:16px;background:none;border:none;padding:0;
  color:var(--ink);word-break:break-all}
.addr button{font:inherit;font-size:14px;font-weight:600;padding:8px 14px;border-radius:8px;
  border:1px solid var(--line);background:var(--raise);color:var(--ink);cursor:pointer;flex:none}
.addr button:hover{border-color:var(--blue);color:var(--blue)}
.linkish{background:none;border:none;padding:0;font:inherit;color:var(--muted);cursor:pointer;
  text-decoration:none}
.linkish:hover{color:var(--ink)}


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
  .hero-shots .ph{width:172px;--r:23px;--bez:6px}
  .hero-shots .ph.back{left:4%}
  .hero-shots .ph.front{right:4%;top:52px}
}
@media (max-width:760px){
  .track{margin:0 30px;height:78px}
  .tick .lab,.origin span{font-size:11.5px}
  .gap{font-size:10px}
  .ruler{padding:24px 18px 18px}
  .loop{grid-template-columns:1fr 1fr;gap:30px 0}
  .step:nth-child(2)::after{display:none}
  .claims{grid-template-columns:1fr;gap:18px;margin-top:32px}
  .method .row{grid-template-columns:1fr;gap:7px;padding:20px 0}
  .hero{padding-top:48px}
  .section{padding:40px 0 0;margin-top:34px}
}
@media (max-width:430px){
  .loop{grid-template-columns:1fr}
  .step::after{display:none!important}
  .step{padding-right:0}
}

/* ---- motion ----------------------------------------------------------------------------
   ONE mechanism: a transition from a resting state to an arrived state, switched by an `.in`
   class. Nothing here uses @keyframes.

   The first version mixed the two, transitions on containers and forwards-filling animations on
   their children, and it flickered on scroll. An animation with `forwards` holds the last frame
   rather than truly settling, so any repaint of that element (a lazy image decoding beside it, a
   scroll that promotes a new compositor layer) can show a frame of the pre-animation state. Two
   systems also meant two sources of truth for what "finished" looks like.

   Slower and shorter than before: 720ms over 10px rather than 550ms over 14px. Movement you
   notice is movement that interrupts.

   Under prefers-reduced-motion NOTHING here applies. Every resting state lives inside this
   query, so a visitor with it on, or with JS off, gets the finished page rather than a blank one. */
@media (prefers-reduced-motion:no-preference){
  .rise,.loop .step,.claims .claim,.method .row,.langs .lang,.tick,.gap,.origin{
    transition:opacity .72s cubic-bezier(.16,.7,.3,1),transform .72s cubic-bezier(.16,.7,.3,1)}

  .rise{opacity:0;transform:translateY(10px)}
  .rise.in{opacity:1;transform:none}
  .hero .rise{transition-delay:var(--d,0s)}

  /* Children rest and arrive on the SAME property pair as their container, differing only in
     delay, so a group settles as one movement instead of four. */
  .loop .step,.claims .claim,.method .row,.langs .lang{opacity:0;transform:translateY(10px)}
  .loop.in .step,.claims.in .claim,.method.in .row,.langs.in .lang{opacity:1;transform:none}
  .loop .step:nth-child(2),.claims .claim:nth-child(2),
  .method .row:nth-child(2),.langs .lang:nth-child(2){transition-delay:.08s}
  .loop .step:nth-child(3),.claims .claim:nth-child(3),
  .method .row:nth-child(3){transition-delay:.16s}
  .method .row:nth-child(4){transition-delay:.24s}
  .method .row:nth-child(5){transition-delay:.32s}
  .method .row:nth-child(6){transition-delay:.40s}
  .loop .step:nth-child(4){transition-delay:.24s}

  /* The arrows are ::after on the steps, so they inherit the step's opacity and need no rule of
     their own. That is one fewer thing that can be caught mid-animation. */

  /* Ticks carry translateX(-50%) as POSITIONING, so both states must keep it: dropping it in one
     of them slides every label half its width across the ruler. */
  .tick,.gap{opacity:0;transform:translateX(-50%) translateY(8px)}
  .track.in .tick,.track.in .gap{opacity:1;transform:translateX(-50%)}
  .origin{opacity:0}
  .track.in .origin{opacity:1}

  /* micro-interactions: small, and only where something is actually interactive */
  .cta{transition:background .2s ease,transform .2s ease}
  .cta:active{transform:translateY(1px)}
  .step .n{transition:color .25s ease}
  .step:hover .n{color:var(--core)}
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


HEAD_SCRIPT = """<script>
/* A RELOAD starts at the top. Browsers restore the old scroll position on reload, which on a
   page with reveal animations means arriving mid-page with everything already settled, looking
   like the top of the page simply failed to render.

   Scoped to reloads on purpose: setting scrollRestoration to manual unconditionally would also
   break BACK from the privacy page, which should return you exactly where you left. Runs in the
   head, before the browser gets a chance to restore. */
(function(){
  try{
    var nav = performance.getEntriesByType && performance.getEntriesByType('navigation')[0];
    var reloaded = nav ? nav.type === 'reload'
      : (performance.navigation && performance.navigation.type === 1);
    if (!reloaded) return;
    if ('scrollRestoration' in history) history.scrollRestoration = 'manual';
    window.addEventListener('load', function(){ window.scrollTo(0, 0); });
  }catch(e){}
})();
</script>"""


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
  }, { threshold: 0.1, rootMargin: '0px 0px -60px 0px' });
  document.querySelectorAll('.loop, .track, .claims, .method, .langs, .shots, .section .rise')
    .forEach(function(el){ io.observe(el); });
})();

/* FAQ: one answer open at a time.
   Modern browsers do this from the `name` attribute alone. This is the fallback for one that
   does not: where `name` is honoured the loop finds nothing still open and does nothing. */
(function(){
  var items = document.querySelectorAll('.faq details');
  if (!items.length) return;
  if ('name' in document.createElement('details')) return;   // handled natively
  items.forEach(function(d){
    d.addEventListener('toggle', function(){
      if (!d.open) return;
      items.forEach(function(o){ if (o !== d) o.open = false; });
    });
  });
})();

/* Contact dialog. No form and no endpoint: it shows the address, copies it, and offers a
   mailto for people who do have a mail client. A contact FORM would mean storing message bodies,
   a second thing to disclose in the privacy policy, and a place for spam to accumulate that
   somebody has to actually watch. */
(function(){
  var dlg = document.getElementById('contact');
  if (!dlg || !dlg.showModal) return;
  var copy = document.getElementById('contact-copy');
  var msg = document.getElementById('contact-msg');
  var addr = 'support@corlang.app';

  document.querySelectorAll('[data-contact]').forEach(function(b){
    b.addEventListener('click', function(e){
      /* The trigger is a real mailto: link, so it works with JS off. Here we take it over and
         show the dialog instead, which does not hijack the browser into a mail client. */
      e.preventDefault();
      msg.textContent = '';
      msg.className = 'msg';
      copy.textContent = 'Copy';
      dlg.showModal();
    });
  });

  copy.addEventListener('click', function(){
    function done(){
      copy.textContent = 'Copied';
      msg.className = 'msg good';
      msg.textContent = 'Address copied to your clipboard.';
    }
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(addr).then(done, select);
    } else {
      select();
    }
    /* Fallback: select the text so a manual copy is one keystroke, rather than failing silently
       on http or in a browser without the clipboard API. */
    function select(){
      var node = document.getElementById('contact-addr');
      var r = document.createRange();
      r.selectNodeContents(node);
      var sel = window.getSelection();
      sel.removeAllRanges();
      sel.addRange(r);
      msg.className = 'msg';
      msg.textContent = 'Selected. Press Ctrl+C or Cmd+C to copy.';
    }
  });
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
  var androidBox = document.getElementById('invite-android');
  var iosBox = document.getElementById('invite-ios');
  if (!dlg || !form || !dlg.showModal) return;

  function device(){
    var c = form.querySelector('[name=device]:checked');
    return c && c.value;
  }

  /* One place decides what is on screen, so the three states cannot drift apart: nothing chosen
     (just the question), Android (the form), iPhone (the reason there is nothing to join).

     `required` is toggled rather than left on the input, because a hidden control that is
     required makes the browser refuse to submit while having nothing it can focus to say so. */
  function sync(){
    var d = device();
    androidBox.hidden = d !== 'android';
    iosBox.hidden = d !== 'ios';
    send.hidden = d !== 'android';
    email.required = d === 'android';
  }

  form.querySelectorAll('[name=device]').forEach(function(r){
    r.addEventListener('change', sync);
  });

  document.querySelectorAll('[data-invite]').forEach(function(b){
    b.addEventListener('click', function(){
      msg.textContent = '';
      msg.className = 'msg';
      send.disabled = false;
      send.textContent = 'Send';
      form.querySelectorAll('[name=device]').forEach(function(r){ r.checked = false; });
      sync();
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
    if (device() !== 'android') return;
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
        device: 'android',
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


ROBOTS_NOINDEX = """
<meta name="robots" content="noindex,nofollow">"""


def page(title, description, body, canonical, wide=True, noindex=False):
    # Unlinked is not the same as unfindable: without this, a crawler that meets the URL
    # anywhere would index a page built to be reached deliberately.
    # Unlinked is not the same as unfindable: without this, a crawler that meets the URL
    # anywhere would index a page built to be reached deliberately.
    robots_tag = ROBOTS_NOINDEX if noindex else ""
    return f"""<!doctype html>
<html lang="en-GB">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{html.escape(title)}</title>
<meta name="description" content="{html.escape(description)}">
<link rel="canonical" href="{canonical}">{robots_tag}
<meta name="theme-color" content="#FBFBFD">
<meta property="og:title" content="{html.escape(title)}">
<meta property="og:description" content="{html.escape(description)}">
<meta property="og:type" content="website">
<meta property="og:url" content="{canonical}">
<link rel="preload" href="/fonts/manrope.woff2" as="font" type="font/woff2" crossorigin>
<link rel="preload" href="/fonts/inter.woff2" as="font" type="font/woff2" crossorigin>
<link rel="icon" href="/favicon.svg" type="image/svg+xml">
{HEAD_SCRIPT}
<style>{CSS}</style>
</head>
<body>
<div class="wrap">
  <header class="top">
    <a class="mark" href="/">{LOGO}<b>Corlang</b></a>
    <nav><a href="/privacy/">Privacy</a><a href="/terms/">Terms</a><a
      href="mailto:support@corlang.app" data-contact>Contact</a></nav>
  </header>
{body}
  <dialog id="invite">
    <form class="dlg" method="dialog">
      <h3>Ask for a test invite</h3>
      <p>Leave your email and you will get a Play test link when the next round opens. Nothing
      else is sent, and the address is used for this only.</p>
      <fieldset class="pick">
        <legend>Which phone do you use?</legend>
        <div class="opts">
          <label><input type="radio" name="device" value="android"><span>Android</span></label>
          <label><input type="radio" name="device" value="ios"><span>iPhone</span></label>
        </div>
      </fieldset>
      <div id="invite-android" hidden>
        <label for="invite-email">Email address</label>
        <input id="invite-email" name="email" type="email" autocomplete="email"
               placeholder="you@example.com" inputmode="email">
      </div>
      <p id="invite-ios" hidden>Corlang is an Android app. There is no iPhone version and none
      planned, so there is nothing for you to test yet. Sorry.</p>
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

  <dialog id="contact">
    <form class="dlg" method="dialog">
      <h3>Get in touch</h3>
      <p>Questions, bugs, or an invite that never arrived.</p>
      <div class="addr">
        <code id="contact-addr">support@corlang.app</code>
        <button type="button" id="contact-copy">Copy</button>
      </div>
      <p class="msg" id="contact-msg" role="status" aria-live="polite"></p>
      <div class="dlg-row">
        <a class="cta" style="flex:1;text-align:center"
           href="mailto:support@corlang.app">Open in mail app</a>
        <button value="close">Close</button>
      </div>
    </form>
  </dialog>

  <footer>
    <div><a href="/">Home</a><a href="/privacy/">Privacy</a><a href="/terms/">Terms</a><a
      href="mailto:support@corlang.app" data-contact>Contact</a></div>
    <div>Corlang</div>
  </footer>
</div>
{SCRIPT}</body>
</html>
"""


def live_languages():
    """
    The courses that are actually live, read from the app's own content manifest.

    Generated rather than typed, because a hand-written list on a marketing page is a promise
    that rots: French, German, Italian and Spanish are all authored and sitting in the repo
    behind a hidden flag, and the day one of them is switched on this section should say so
    without anybody remembering to come back here.
    """
    croot = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'content')
    codes = json.load(io.open(os.path.join(croot, '_index.json'), encoding='utf-8'))
    out = []
    for code in codes:
        meta = json.load(io.open(os.path.join(croot, code, 'meta.json'), encoding='utf-8'))
        out.append({
            'code': code,
            'name': meta['name'],
            'native': meta.get('nativeName', ''),
        })
    return out


REQUEST_BODY = '''  <section class="hero" style="grid-template-columns:1fr">
    <div class="hero-copy">
      <p class="eyebrow">Requests</p>
      <h1>Which language next?</h1>
      <p class="lede">Corlang adds one course at a time, and which one comes next is decided by
      what people ask for. Tell us what you want and how far you need to get.</p>

      <form class="reqform" id="reqform" novalidate>
        <label for="req-language">Language</label>
        <select id="req-language" name="language" required>
        <option value="croatian">Croatian</option>
        <option value="portuguese">Portuguese</option>
        <option value="french">French</option>
        <option value="german">German</option>
        <option value="italian">Italian</option>
        <option value="spanish">Spanish</option>
        <option value="dutch">Dutch</option>
        <option value="polish">Polish</option>
        <option value="greek">Greek</option>
        <option value="czech">Czech</option>
        <option value="swedish">Swedish</option>
        <option value="danish">Danish</option>
        <option value="norwegian">Norwegian</option>
        <option value="finnish">Finnish</option>
        <option value="romanian">Romanian</option>
        <option value="hungarian">Hungarian</option>
        <option value="bulgarian">Bulgarian</option>
        <option value="slovak">Slovak</option>
        <option value="slovenian">Slovenian</option>
        <option value="serbian">Serbian</option>
        <option value="ukrainian">Ukrainian</option>
        <option value="turkish">Turkish</option>
        <option value="irish">Irish</option>
        <option value="catalan">Catalan</option>
        <option value="other">Another language</option>
        </select>

        <div id="req-other-wrap" hidden>
          <label for="req-other">Which language?</label>
          <input id="req-other" name="other" type="text" maxlength="40" autocomplete="off">
        </div>

        <label for="req-level">How far do you need to get?</label>
        <select id="req-level" name="level" required>
        <option value="A1">A1</option>
        <option value="A2">A2</option>
        <option value="B1">B1</option>
        <option value="B2">B2</option>
        <option value="C1">C1</option>
        </select>

        <label for="req-email">Your email</label>
        <input id="req-email" name="email" type="email" required autocomplete="email"
               placeholder="you@example.com">

        <div class="hp" aria-hidden="true">
          <label for="req-website">Leave this empty</label>
          <input id="req-website" name="website" type="text" tabindex="-1" autocomplete="off">
        </div>

        <button type="submit" id="req-send">Send request</button>
        <p class="msg" id="req-msg" role="status" aria-live="polite"></p>
      </form>

      <p class="req-note">Your address is stored so we can tell you when that course exists, and
      for nothing else. No newsletter, no sharing, no other use. Ask us to delete it at any time
      at support@corlang.app. See the <a href="/privacy/">privacy policy</a>.</p>
    </div>
  </section>
'''

REQUEST_SCRIPT = '''<script>
/* The "Another language" text box only exists once it is needed. */
(function(){
  var f = document.getElementById('reqform');
  if (!f) return;
  var lang = document.getElementById('req-language');
  var wrap = document.getElementById('req-other-wrap');
  var other = document.getElementById('req-other');
  var msg = document.getElementById('req-msg');
  var send = document.getElementById('req-send');

  function sync(){
    var isOther = lang.value === 'other';
    wrap.hidden = !isOther;
    other.required = isOther;
  }
  lang.addEventListener('change', sync);
  sync();

  f.addEventListener('submit', function(e){
    e.preventDefault();
    msg.className = 'msg';
    msg.textContent = '';
    send.disabled = true;
    fetch('/api/request', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        language: lang.value,
        other: other.value,
        level: document.getElementById('req-level').value,
        email: document.getElementById('req-email').value,
        website: document.getElementById('req-website').value
      })
    }).then(function(r){ return r.json().catch(function(){ return {}; }); })
      .then(function(d){
        if (d && d.ok) {
          msg.className = 'msg good';
          msg.textContent = 'Noted. We will write when that course exists.';
          f.reset(); sync();
        } else {
          msg.className = 'msg';
          msg.textContent = (d && d.error) || 'That did not send. Try again shortly.';
        }
      })
      .catch(function(){
        msg.className = 'msg';
        msg.textContent = 'That did not send. Check your connection.';
      })
      .then(function(){ send.disabled = false; });
  });
})();
</script>'''


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
        delay = f'transition-delay:{0.09 * i + .12:.2f}s'
        ticks.append(f'<div class="tick" style="left:{x:.2f}%;{delay}">'
                     f'<div class="dot"></div><div class="lab">{label}</div></div>')
        if delta:
            nxt = (INTERVALS[i + 1][0] ** 0.5) / span * 100
            gaps.append(
                f'<div class="gap" style="left:{(x + nxt) / 2:.2f}%;{delay}">{delta}</div>')
    return ('<div class="track-scroll"><div class="track"><div class="line"></div>'
            '<div class="origin"><span>you learn it</span></div>'
            + ''.join(gaps) + ''.join(ticks) + '</div></div>')


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
        f'        <div class="phone device"><div class="screen">'
        f'<img src="/shots/{f}" alt="{html.escape(alt)}" '
        f'width="{w}" height="{h}" loading="lazy" decoding="async"></div></div>'
        for f, alt, w, h in shot_list)

    landing = f"""
  <section class="hero">
    <div class="hero-copy">
      <p class="eyebrow rise">Spaced repetition &middot; 10 minutes a day</p>
      <h1 class="rise">Learn a language.<br><span class="soft">The proven way.</span></h1>
      <p class="lede rise">Short daily lessons built on how memory works, so the word you learn
      today is still there in a month.</p>
      <p class="lede rise">No classroom, no commute. Learn anywhere, anytime.</p>
      <p class="lede rise">Prepare for work, citizenship or an official exam.</p>
      <div class="actions rise">
        <button class="cta" type="button" data-invite>Ask for a test invite</button>
        <span class="avail">Coming soon to Google Play</span>
      </div>
    </div>
    <div class="hero-shots rise">
      <div class="ph back device"><div class="screen"><img src="/shots/hero-dark.jpg"
        alt="A Corlang flashcard in the dark theme" width="{hd}" height="{hdh}"
        fetchpriority="high" decoding="async"></div></div>
      <div class="ph front device"><div class="screen"><img src="/shots/hero-light.jpg"
        alt="The Corlang Learn tab in the light theme" width="{hl}" height="{hlh}"
        fetchpriority="high" decoding="async"></div></div>
    </div>
  </section>

  <section class="section">
    <div class="section-head">
      <h2>Available now</h2>
    </div>
    <div class="langs">
{{LANG_CARDS}}
    </div>
  </section>

  <section class="section">
    <div class="section-head">
      <h2>Inside every lesson</h2>
      <p>Ten minutes, six steps, the same order every day.</p>
    </div>
    <div class="method">
      <div class="row">
        <div class="term"><span class="n">01</span><h3>New words</h3></div>
        <p>A small set of new words, introduced on their own before anything else uses them.
        Introducing them first means the rest of the lesson is recognition rather than decoding,
        and they enter your review schedule the same day.</p>
      </div>
      <div class="row">
        <div class="term"><span class="n">02</span><h3>Input</h3></div>
        <p>A short teaching block: the pattern of the day, shown in sentences you would really
        say. Deliberately small, because a page of new material is a page you will not
        remember tomorrow.</p>
      </div>
      <div class="row">
        <div class="term"><span class="n">03</span><h3>Practice</h3></div>
        <p>Exercises on exactly what was just taught: quick multiple choice to check you
        followed it, then drills where you type the form yourself. The typed ones are the ones
        that stick, because producing an answer is harder than recognising one, and that
        difficulty is what does the work.</p>
      </div>
      <div class="row">
        <div class="term"><span class="n">04</span><h3>Output</h3></div>
        <p>A dialogue where you take one side and say your lines out loud. This is the first
        point where you are making the language rather than answering questions about it, and
        it is the step most courses leave out.</p>
      </div>
      <div class="row">
        <div class="term"><span class="n">05</span><h3>Wrap-up</h3></div>
        <p>The lesson ends by asking for the day's phrases back from an empty page, with nothing
        to copy. It is the most valuable minute in the ten: retrieving something is the act that
        tells your memory to keep it.</p>
      </div>
      <div class="row">
        <div class="term"><span class="n">06</span><h3>Review</h3></div>
        <p>Last, a pass over words falling due from earlier lessons, scheduled one word at a
        time. Then the day is done and the streak moves, which is the part that gets you back
        tomorrow.</p>
      </div>
    </div>
  </section>

  <section class="section">
    <div class="section-head">
      <h2>How a word actually sticks</h2>
      <p>That is one lesson. This is what happens to a single word afterwards, in your daily
      flashcards, for as long as it takes.</p>
    </div>
    <div class="loop">
      <div class="step"><span class="n">01</span><h3>Learn it</h3>
        <p>A new word arrives inside a lesson, in a sentence you would really say.</p></div>
      <div class="step"><span class="n">02</span><h3>Use it</h3>
        <p>You write it, say it and practise it.</p></div>
      <div class="step"><span class="n">03</span><h3>Recall it</h3>
        <p>The lesson ends by asking for it again, from memory, with no prompt.</p></div>
      <div class="step"><span class="n">04</span><h3>Review it</h3>
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
    <div class="claims">
      <div class="claim"><h3>Nothing to sign up for</h3>
        <p>No account, no ads, no tracking. Your progress stays on your phone, and you can
        export it whenever you like.</p></div>
      <div class="claim"><h3>Ten minutes</h3>
        <p>One lesson and its reviews. On the bus, in a queue, before bed.</p></div>
      <div class="claim"><h3>A finish line</h3>
        <p>A fixed course from your first words to the official B1 exam, in its real format.
        Not an endless feed.</p></div>
    </div>
  </section>


  <section class="section">
    <div class="section-head">
      <h2>Questions</h2>
    </div>
    <div class="faq">
      <details name="faq">
        <summary>Which languages can I learn?</summary>
        <p>At the moment only Croatian and European Portuguese are available. More languages
        are planned for the future.</p>
      </details>
      <details name="faq">
        <summary>Is it free?</summary>
        <p>The first level of each course is free permanently, not as a trial that expires.
        After that each level from A1 to B1 is bought on its own, and every unlock includes the
        levels below it, so buying B1 unlocks the whole course at once. These are one-time
        purchases and what you buy stays bought. The AI tutor is the only subscription, and
        everything else works without it.</p>
      </details>
      <details name="faq">
        <summary>Do I need an account?</summary>
        <p>No. There is no sign-up and no password, and nothing about you reaches us. Your
        progress lives on your phone, and Android's own backup copies it to your Google Drive so
        a new phone picks up where the old one left off. There is a manual export in Profile
        too, if you would rather not rely on that.</p>
      </details>
      <details name="faq">
        <summary>Does it work offline?</summary>
        <p>The whole course does: lessons, reviews, quizzes and the mock exams. Only the AI tutor
        needs a connection, because it is talking to a language model.</p>
      </details>
      <details name="faq">
        <summary>How long is a lesson?</summary>
        <p>About ten minutes, and it is the same ten minutes every day. That is deliberate: a
        session you can finish anywhere is a lesson you will still be doing months from now.</p>
      </details>
      <details name="faq">
        <summary>Will it get me through the official exam?</summary>
        <p>It is built for it. Each course follows the syllabus the real exam is written from,
        and the mock exams copy its format and marking. What it cannot do is award anything: only
        the official examining body can do that.</p>
      </details>
      <details name="faq">
        <summary>What does the AI tutor do?</summary>
        <p>It is a patient conversation partner in the language you are learning: it corrects
        you, explains why, and keeps to your level. It has a daily message allowance, shown in
        the app, and it will decline anything that is not language learning.</p>
      </details>
      <details name="faq">
        <summary>Am I being tracked?</summary>
        <p>No. No analytics, no advertising, no third-party trackers, and nothing
        about you is ever sold. The
        <a href="/privacy/">privacy policy</a> lists the one case in which anything leaves your
        device at all.</p>
      </details>
    </div>
  </section>

"""

    # Flags: copied verbatim, so the only place they exist is tools/site/flags.
    flags_dst = os.path.join(OUT, 'flags')
    os.makedirs(flags_dst, exist_ok=True)
    for fn in sorted(os.listdir(os.path.join(HERE, 'flags'))):
        shutil.copyfile(os.path.join(HERE, 'flags', fn), os.path.join(flags_dst, fn))

    lang_cards = "\n".join(
        '      <div class="lang">'
        '<img src="/flags/{code}.svg" width="54" height="36" alt="" '
        'loading="lazy" decoding="async">'
        '<div><h3>{name}</h3><span class="native">{native}</span></div></div>'.format(**L)
        for L in live_languages())
    landing = landing.replace('{LANG_CARDS}', lang_cards)

    io.open(os.path.join(OUT, 'index.html'), 'w', encoding='utf-8', newline='\n').write(
        page("Corlang — learn a language, remember it",
             "Short daily lessons built on spaced repetition, from your first words to the "
             "official B1 exam. No account, no ads, no tracking.",
             landing, "https://corlang.app/"))

    os.makedirs(os.path.join(OUT, 'requests'), exist_ok=True)
    io.open(os.path.join(OUT, 'requests', 'index.html'), 'w', encoding='utf-8',
            newline='\n').write(
        page("Request a language — Corlang",
             "Tell us which language you want Corlang to build next, and how far you need to get.",
             REQUEST_BODY + REQUEST_SCRIPT, "https://corlang.app/requests/", noindex=True))

    os.makedirs(os.path.join(OUT, 'terms'), exist_ok=True)
    terms_md = io.open(os.path.join(ROOT, 'TERMS.md'), encoding='utf-8').read()
    io.open(os.path.join(OUT, 'terms', 'index.html'), 'w', encoding='utf-8',
            newline='\n').write(
        page("Terms of Service — Corlang",
             "What you are buying, who handles the payment, what the AI tutor is and is not, "
             "and what Corlang does not promise.",
             '<article>' + md_to_html(terms_md) + '</article>', "https://corlang.app/terms/"))

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
        "\n/fonts/*\n  Cache-Control: public, max-age=604800\n"
        "\n/flags/*\n  Cache-Control: public, max-age=604800\n")
    print('built site/index.html, /privacy/, /terms/, /requests/, favicon, _headers')


if __name__ == '__main__':
    build()
