# Handoff: Corlang loading screen + logo

## Overview
A splash / loading screen for **Corlang** (language-learning app; "core language", tagline *"Jezik u srži"*). Two rings fill as the app loads and the mark resolves into the **two-ring "Orbit Core"** icon with a molten-red core — then it shrinks and slides left into place as the **"o"** in the **Corlang** wordmark. At 100% the mark matches the app icon exactly.

## About the design files
These files are **design references created in HTML/JS** — a working prototype of the intended look and motion, **not production code to ship as-is**. The task is to **recreate this loader and logo inside the app's existing environment** (React Native, SwiftUI, Flutter, a React/Vue web app, etc.) using its established patterns. `CorlangLoader.jsx` and `corlang-loader-vanilla.html` are portable starting points to adapt or transliterate.

## Fidelity
**High-fidelity.** Colors, typography, geometry, timing, and easing below are authoritative.

## Font
**Helvetica** for the wordmark (`'Helvetica Neue', Helvetica, Arial, sans-serif`). Swap for the app's brand font if different; re-check the measured `dx` (below) after any font/size change. The % counter uses **Space Mono** (or any monospace).

## Files in this bundle
- `CorlangLogo.jsx` — **shared logo component** (variants `orbit`, `lockup`, `wordmark`; color/size props). Use everywhere the brand appears.
- `LOGO_USAGE.md` — where/how to place the logo across the app, with a wiring checklist.
- `CorlangLoader.jsx` — self-contained React loader (props for colors/tagline/duration; `onDone` fires at 100%). `requestAnimationFrame`, no dependencies.
- `corlang-loader-vanilla.html` — the same loader as framework-free HTML/CSS/JS. Open in a browser to see the motion.
- `logo-orbit-core.svg` — the mark as a static SVG (launcher/favicon).
- `reference-full-design.dc.html` — the full original exploration. Reference only.

## The mark (SVG, 100×100 viewBox)
Two rings + a core, all recolorable via `stroke` / `fill`:
- **Outer ring**: `<circle r="33">`, `stroke-width="6"`, round caps, `stroke-dasharray="132 76"`, `transform="rotate(-52 50 50)"`. (Broken ring, ~132 units visible.)
- **Inner arc**: `<path d="M70.37 44.92 A21 21 0 1 0 37.07 66.55">`, `stroke-width="6"`, round caps. An ~80-unit arc on a radius-21 circle, anchored at the right (≈346°) and opening toward the lower-right.
- **Core**: `<circle r="9">`, fill = core color.

## The loader — screen spec
- **Container**: fills the viewport/parent. Background `#0f1620` (deep ink-navy).
- **Centerpiece**: the wordmark row, centered. Order: `C` · **mark** · `r` `l` `a` `n` `g`. The mark is a 46×46 slot acting as the lowercase "o".
- **% counter**: monospace, centered ~59% height. Load phase only.
- **Tagline**: "Jezik u srži", same position, fades in after the word assembles.

## Animation timeline (loops; `DUR = 4400ms`)
`t` = normalized loop progress 0→1. `prog = clamp(t / 0.5)` (load fraction).
Easings: `eo` easeOutCubic `1-(1-x)^3`; `eio` easeInOutCubic; `eob` easeOutBack (overshoot ~2.2). `cl` = clamp 0..1.

1. **Load — `t` 0 → 0.5 (0–2200ms):**
   - **Outer ring** visible arc grows: `stroke-dasharray = (132*prog) + " 207.3"`; ring sweeps into place: `rotate(-52 - (1 - eo(prog)) * 210)` → lands at `-52deg` at 100%.
   - **Inner arc** reveals from its right anchor leftward: `stroke-dashoffset = 80 * (1 - prog)` (dasharray `80 80`) → fully drawn at 100%.
   - **Core** pops in only near the end: `cp = clamp((prog - 0.8) / 0.2)`, `scale = eob(cp)` (overshoots then settles). Hidden until ~80%, full at 100%. `transform-box: fill-box; transform-origin: center`.
   - `%` counter = `round(prog * 100)`.
2. **Settle — `t` 0.5 → ~0.88:** `sp = eio((t - 0.5) / 0.38)`
   - Mark scale `2.4 → 1.0`: `scale = 2.4 - 1.4*sp`.
   - Mark translates **left only**: `translateX = dx * (1 - sp)`, `dx` measured at mount = `wordmarkCenterX - markCenterX`.
   - Transform: `translateX(tx) scale(s)`, `transform-origin: center`.
   - `%` fades out over `t` 0.5 → 0.58.
3. **Letters — `t` 0.62 → ~0.95:** letter `i` (0..5): `lp = eio((t - (0.62 + i*0.04)) / 0.18)`; `opacity = lp`, `translateY = (1-lp)*8px`. **Letters keep their final width the whole time (no reflow)** — they only fade + rise. This is what lets the mark slide cleanly left with no wobble.
4. **Tagline — `t` ≥ 0.82:** `opacity = clamp((t-0.82)/0.08)`.
5. **Loop fade — `t` 0.93 → 1.0:** stage `opacity` → 0, then restart (fade in `t` 0 → 0.04).

**Critical layout note:** the mark moves smoothly only because the letters never change width. Do NOT animate letter width / collapse layout, or the mark wobbles.

## In a real app (not a forever loop)
The prototype loops for display. In production drive it by real load progress:
- Map actual progress (0..1) to `prog` — the ring fill, inner reveal, core pop, and `%` counter all key off `prog`.
- When progress hits 1, run **settle → letters → tagline** once, then fade out and unmount. `CorlangLoader.jsx` exposes `onDone` at the 100% moment to start your transition.

## Design tokens
- **Brand (rings)**: `#2f7fae`
- **Core (center dot)**: `#c8402c`
- **Splash background**: `#0f1620`
- **On-dark text / letters**: `#f4efe6`
- **Muted on-dark** (%, tagline): `rgba(244,239,230,0.5)` / `rgba(244,239,230,0.62)`
- **Wordmark type**: Helvetica / Helvetica Neue, 700, 56px (loader), letter-spacing `-0.02em`
- **Counter type**: Space Mono, 13px, letter-spacing `3px`
- **Mark geometry**: outer r=33 sw6 dash `132 76` rotate −52; inner arc path (r=21, ~80 units) opening lower-right; core r=9 — all in a 100×100 viewBox
- **Mark final size (loader)**: 46×46px; **load scale**: 2.4×
- **Loop duration**: 4400ms

## Assets
- Font: **Helvetica / Helvetica Neue** (system on Apple platforms; use the app's licensed copy elsewhere). Counter: any monospace (Space Mono shown).
- Logo: `logo-orbit-core.svg` (inline SVG, recolorable). No raster images.
