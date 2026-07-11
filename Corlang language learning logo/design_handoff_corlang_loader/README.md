# Handoff: Corlang loading screen + logo

## Overview
A splash / loading screen for **Corlang** (a language-learning app; "core language", tagline *"Jezik u srži"*). A determinate progress ring fills as the app loads and, at 100%, closes into a complete circular **"O"** with a molten-red core. The mark then shrinks and slides left into place while the letters draw in to spell **Corlang** — the mark IS the "o".

## About the design files
The files in this bundle are **design references created in HTML/JS** — a working prototype of the intended look and motion, **not production code to ship as-is**. The task is to **recreate this loader and logo inside the app's existing environment** (React Native, SwiftUI, Flutter, a React/Vue web app, etc.) using its established patterns. `CorlangLoader.jsx` and `corlang-loader-vanilla.html` are portable starting points you can adapt or transliterate to the target platform. If there's no front-end environment yet, pick the framework that fits the app and implement there.

## Fidelity
**High-fidelity.** Final colors, typography, geometry, timing, and easing are all specified below and are authoritative.

## Files in this bundle
- `CorlangLogo.jsx` — **shared logo component** (variants: `orbit`, `o`, `lockup`, `wordmark`; color + size props). Use this everywhere the brand appears.
- `LOGO_USAGE.md` — where/how to place the logo across the app, with a wiring checklist and sizing rules.
- `CorlangLoader.jsx` — self-contained React component (props for colors/tagline/duration; `onDone` callback fires when a cycle first hits 100%). Uses `requestAnimationFrame`, no dependencies.
- `corlang-loader-vanilla.html` — the same loader as framework-free HTML/CSS/JS. Open it in a browser to see the motion; copy the `#corlang-loader` markup + script into any page.
- `logo-orbit-core.svg` — the app-icon mark ("Orbit Core": two broken rings + core). Use for launcher/favicon.
- `logo-o-mark.svg` — the resolved solid-ring "O" mark (how it looks at 100% and as the "o" in the wordmark).
- `reference-full-design.dc.html` — the complete original exploration (loader + three static icon directions + on-device mockups). Reference only.

## The loader — screen spec
- **Container**: fills the viewport/parent. Background `#0f1620` (deep ink-navy).
- **Centerpiece**: the wordmark, an inline-flex row, vertically + horizontally centered. Order: `C` · **mark** · `r` `l` `a` `n` `g`. The mark is a 46×46 slot acting as the lowercase "o".
- **% counter**: monospace, centered at ~59% height, below the mark. Visible during load only.
- **Tagline**: "Jezik u srži", centered at ~59% height, fades in after the word assembles.

## The mark (SVG, 100×100 viewBox)
- Ring: `<circle r="30">`, `stroke-width="8"`, `stroke-linecap="round"`, color = brand. Rendered as a **determinate arc** via `stroke-dasharray = 2πr (≈188.5)` and animating `stroke-dashoffset` from `188.5` (empty) to `0` (full circle). Base rotation `rotate(-90)` so the fill starts at 12 o'clock.
- Core: `<circle r="12.5">`, fill = core color.
- The **static logo** for icons uses the "Orbit Core" version (`logo-orbit-core.svg`): two broken rings (r=33 and r=21, `stroke-width` 6, round caps, offset rotations) around an `r=9` core.

## Animation timeline (loops; `DUR = 4400ms`)
`t` = normalized loop progress 0→1. Easing = **easeOutCubic**: `eo(x) = 1 - (1-x)^3`. `cl` = clamp 0..1.
1. **Load — `t` 0 → 0.5 (0–2200ms):**
   - Ring fills: `strokeDashoffset = C * (1 - t/0.5)` → complete circle exactly at `t=0.5` (100%).
   - Whole ring rotates one turn: `rotate((t/0.5) * 360deg)`.
   - `%` counter = `round((t/0.5) * 100)`.
2. **Settle — `t` 0.5 → 0.8:** `sp = eo((t-0.5)/0.3)`
   - Mark scale `2.4 → 1.0`: `scale = 2.4 - 1.4*sp`.
   - Mark translates **left only** into its slot: `translateX = dx * (1 - sp)`, where `dx` is measured at mount as `wordmarkCenterX - markCenterX` (positive; moves the mark to screen-center during load, back to 0 at rest).
   - Combined transform: `translateX(tx) scale(s)`, `transform-origin: center`.
   - `%` counter fades out over `t` 0.5 → 0.58.
3. **Letters — `t` 0.58 → ~0.94:** each letter `i` (0..5) reveals with `lp = eo((t - (0.58 + i*0.04)) / 0.16)`; set `opacity = lp` and `translateY = (1-lp)*8px`. **Letters keep their final width the whole time (no reflow)** — they only fade + rise in place, which is why the mark can slide cleanly left without wobble.
4. **Tagline — `t` ≥ 0.82:** `opacity = cl((t-0.82)/0.08)`.
5. **Loop fade — `t` 0.93 → 1.0:** stage `opacity` ramps to 0, then the loop restarts (fade back in over `t` 0 → 0.04).

**Critical layout note:** the reason the mark moves smoothly is that the letters never change width — they're laid out at full size and only animated via opacity/translateY. The mark's horizontal travel is a single monotonic `translateX` from center → slot. Do not animate letter width / use a collapsing layout, or the mark will wobble side-to-side.

## In a real app (not a forever loop)
The prototype loops for display. In production, drive it once by real load progress:
- Map your actual progress (0..1) to the **ring fill** (`strokeDashoffset = C*(1-progress)`) and the `%` counter, instead of time.
- When progress hits 1, run the **settle → letters → tagline** sequence once (t 0.5 → ~0.95 of the timeline), then fade out and unmount, revealing the app. `CorlangLoader.jsx` exposes an `onDone` hook at the 100% moment to start your transition.

## Design tokens
- **Brand (ring)**: `#2f7fae`
- **Core (center dot)**: `#c8402c`
- **Splash background**: `#0f1620`
- **On-dark text / letters**: `#f4efe6`
- **Muted on-dark** (%, tagline): `rgba(244,239,230,0.5)` / `rgba(244,239,230,0.62)`
- **Wordmark type**: Space Grotesk, 700, 56px, letter-spacing `-0.02em`
- **Counter type**: Space Mono, 13px, letter-spacing `3px`
- **Ring**: r=30, stroke-width 8, round caps; core r=12.5 (all in a 100×100 viewBox)
- **Mark final size**: 46×46px; **load scale**: 2.4×
- **Loop duration**: 4400ms; **easing**: easeOutCubic

## Assets
- Fonts: **Space Grotesk** (wordmark) and **Space Mono** (counter) — Google Fonts. Swap for the app's brand font if different; re-check the measured `dx` after any font/size change.
- Logos: `logo-orbit-core.svg`, `logo-o-mark.svg` (both inline SVG, recolorable via `stroke`/`fill`).
- No raster images.
