# Using the Corlang logo across the app

Treat the logo as ONE shared component, not copy-pasted SVG. Import `CorlangLogo.jsx`
(or transliterate it to your platform) and use it in every location below. That way a
future color/shape change happens in one file.

## Variants (prop `variant`)
- `orbit`    — the two-broken-ring mark. **Default brand icon.** Use for app/launcher icon, favicon, nav, tab bars, small chrome.
- `o`        — solid-ring "O" with core. Matches the loader's end state; use where a simpler, denser mark reads better at tiny sizes.
- `lockup`   — icon + "Corlang" wordmark side by side. Use for headers, marketing, auth screens, splash-after-load.
- `wordmark` — "Corlang" with the mark as the "o", no leading icon. Use where space is tight but you still want the name.

## Colors
- On light backgrounds: defaults (`brand="#2f7fae"`, `core="#c8402c"`, `ink="#2b3038"`).
- On dark backgrounds: keep `brand`/`core`, set `ink="#f4efe6"`.
- Monochrome (single ink, e.g. disabled states, watermarks, print): pass the SAME value to `brand` and `core` (e.g. both `#2b3038` on light, both `#f4efe6` on dark).

## Placement checklist (wire these in one pass)
- [ ] **App / launcher icon** — export `orbit` mark onto the app-icon squircle. Native: generate the required icon sizes (iOS AppIcon set, Android adaptive icon foreground); web: `favicon.svg` + PNG fallbacks + `apple-touch-icon`.
- [ ] **Splash / launch screen** — the `CorlangLoader` (animated), or a static `lockup` if the platform disallows JS on splash.
- [ ] **Top bar / header** — `orbit` at ~28–32px, or `wordmark` if the header is text-led.
- [ ] **Bottom tab bar / side nav brand slot** — `orbit`, small.
- [ ] **Auth screens** (sign in / sign up / onboarding) — `lockup`, centered.
- [ ] **Empty states & placeholders** — `orbit` in a muted/monochrome tone.
- [ ] **Loading spinners inside the app** — reuse the determinate ring (`o` mark filling) for a consistent motion language; see the loader spec in `README.md`.
- [ ] **About / settings / legal footer** — `lockup` or `wordmark` + version string.
- [ ] **Notifications / share cards / OG image** — `lockup` on brand background.
- [ ] **Documents / exports / email templates** — `wordmark`.

## Sizing guidance
- Never render the mark below ~18px — below that, prefer the `o` variant over `orbit` (the broken rings get muddy).
- Minimum tap target for a logo that acts as a button: 44×44px (pad around the mark).
- In a lockup, the component scales the wordmark type to the mark height automatically; just set `size`.

## Example usage
```jsx
import CorlangLogo from "./CorlangLogo";

// header
<CorlangLogo variant="orbit" size={30} />

// auth screen (centered lockup)
<CorlangLogo variant="lockup" size={44} />

// on a dark surface
<CorlangLogo variant="wordmark" size={36} ink="#f4efe6" />

// tiny monochrome watermark
<CorlangLogo variant="o" size={20} brand="#9aa2ad" core="#9aa2ad" />
```

## Vanilla (no framework)
If you're not on React, the raw SVGs are in `logo-orbit-core.svg` and `logo-o-mark.svg`.
Inline them (so `stroke`/`fill` can be themed via CSS `currentColor`) or reference via `<img>`.
For a themeable inline version, replace the hardcoded colors with `currentColor` and set
`color:` in CSS, or expose CSS variables:

```html
<span class="corlang-mark" aria-label="Corlang">
  <svg viewBox="0 0 100 100" width="32" height="32">
    <circle cx="50" cy="50" r="33" fill="none" stroke="var(--brand,#2f7fae)" stroke-width="6" stroke-linecap="round" stroke-dasharray="132 76" transform="rotate(-52 50 50)"/>
    <circle cx="50" cy="50" r="21" fill="none" stroke="var(--brand,#2f7fae)" stroke-width="6" stroke-linecap="round" stroke-dasharray="80 52" transform="rotate(128 50 50)"/>
    <circle cx="50" cy="50" r="9" fill="var(--core,#c8402c)"/>
  </svg>
</span>
```
