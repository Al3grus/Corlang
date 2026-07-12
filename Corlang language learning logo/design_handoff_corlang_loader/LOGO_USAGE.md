# Using the Corlang logo across the app

Treat the logo as ONE shared component, not copy-pasted SVG. Import `CorlangLogo.jsx`
(or transliterate it to your platform) and use it in every location below, so a future
color/shape change happens in one file.

## Variants (prop `variant`)
- `orbit`    — the two-ring mark alone. **Default brand icon.** App/launcher icon, favicon, nav, tab bars, small chrome.
- `lockup`   — icon + "Corlang" wordmark side by side. Headers, marketing, auth screens, splash-after-load.
- `wordmark` — "Corlang" with the mark as the "o", no leading icon. Where space is tight but you still want the name.

## Font
Wordmark uses **Helvetica** (`'Helvetica Neue', Helvetica, Arial, sans-serif`). Match your app's brand font only if it differs intentionally.

## Colors
- On light backgrounds: defaults (`brand="#2f7fae"`, `core="#c8402c"`, `ink="#2b3038"`).
- On dark backgrounds: keep `brand`/`core`, set `ink="#f4efe6"`.
- Monochrome (watermarks, disabled, print): pass the SAME value to `brand` and `core`.

## Placement checklist (wire these in one pass)
- [ ] **App / launcher icon** — `orbit` mark on the app-icon squircle. iOS AppIcon set, Android adaptive icon foreground; web `favicon.svg` + PNG fallbacks + `apple-touch-icon`.
- [ ] **Splash / launch screen** — the `CorlangLoader` (animated), or a static `lockup` if the platform disallows JS on splash.
- [ ] **Top bar / header** — `orbit` ~28–32px, or `wordmark` if the header is text-led.
- [ ] **Bottom tab bar / side nav brand slot** — `orbit`, small.
- [ ] **Auth / onboarding screens** — `lockup`, centered.
- [ ] **Empty states & placeholders** — `orbit` in a muted/monochrome tone.
- [ ] **In-app loading spinners** — reuse the determinate ring fill for a consistent motion language (see loader spec in `README.md`).
- [ ] **About / settings / legal footer** — `lockup` or `wordmark` + version string.
- [ ] **Notifications / share cards / OG image** — `lockup` on brand background.
- [ ] **Documents / exports / email templates** — `wordmark`.

## Sizing guidance
- Keep the mark ≥ ~20px; below that the two broken rings get muddy.
- Minimum tap target for a logo that acts as a button: 44×44px (pad around the mark).
- In `lockup`/`wordmark` the component scales the wordmark type to the mark height automatically; just set `size`.

## Example usage
```jsx
import CorlangLogo from "./CorlangLogo";

<CorlangLogo variant="orbit" size={30} />                          // header
<CorlangLogo variant="lockup" size={44} />                         // auth screen
<CorlangLogo variant="wordmark" size={36} ink="#f4efe6" />         // on dark
<CorlangLogo variant="orbit" size={22} brand="#9aa2ad" core="#9aa2ad" /> // muted watermark
```

## Vanilla (no framework)
Raw mark is in `logo-orbit-core.svg`. Inline it (so `stroke`/`fill` theme via CSS) or reference via `<img>`.
Themeable inline version:

```html
<span class="corlang-mark" aria-label="Corlang">
  <svg viewBox="0 0 100 100" width="32" height="32">
    <circle cx="50" cy="50" r="33" fill="none" stroke="var(--brand,#2f7fae)" stroke-width="6" stroke-linecap="round" stroke-dasharray="132 76" transform="rotate(-52 50 50)"/>
    <path d="M70.37 44.92 A21 21 0 1 0 37.07 66.55" fill="none" stroke="var(--brand,#2f7fae)" stroke-width="6" stroke-linecap="round"/>
    <circle cx="50" cy="50" r="9" fill="var(--core,#c8402c)"/>
  </svg>
</span>
```
