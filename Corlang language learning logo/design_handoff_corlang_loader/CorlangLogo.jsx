import React from "react";

/**
 * CorlangLogo — the single source of truth for the Corlang mark/logo.
 * Use this everywhere the brand appears so color + geometry stay consistent.
 *
 * variant:
 *   "orbit"    -> app-icon mark (two broken rings + core)   [default]
 *   "o"        -> solid-ring "O" mark (matches the loader end state / the "o" in the wordmark)
 *   "lockup"   -> icon + "Corlang" wordmark, laid out horizontally
 *   "wordmark" -> "Corlang" with the "o" mark, no leading icon
 *
 * Colors are props so you can invert on dark backgrounds or go monochrome
 * (pass the same value for brand + core).
 */
export default function CorlangLogo({
  variant = "orbit",
  size = 40,               // mark height in px (lockup/wordmark scale type to match)
  brand = "#2f7fae",       // rings
  core = "#c8402c",        // center dot
  ink = "#2b3038",         // wordmark text
  fontFamily = "'Space Grotesk', system-ui, sans-serif",
  title = "Corlang",
  style,
  ...rest
}) {
  const Orbit = (
    <svg viewBox="0 0 100 100" width={size} height={size} role="img" aria-label={title} style={{ display: "block" }}>
      <circle cx="50" cy="50" r="33" fill="none" stroke={brand} strokeWidth="6" strokeLinecap="round" strokeDasharray="132 76" transform="rotate(-52 50 50)" />
      <circle cx="50" cy="50" r="21" fill="none" stroke={brand} strokeWidth="6" strokeLinecap="round" strokeDasharray="80 52" transform="rotate(128 50 50)" />
      <circle cx="50" cy="50" r="9" fill={core} />
    </svg>
  );

  const OMark = (
    <svg viewBox="0 0 100 100" width={size} height={size} role="img" aria-label={title} style={{ display: "block" }}>
      <circle cx="50" cy="50" r="30" fill="none" stroke={brand} strokeWidth="8" />
      <circle cx="50" cy="50" r="12.5" fill={core} />
    </svg>
  );

  if (variant === "orbit") return <span style={{ display: "inline-flex", ...style }} {...rest}>{Orbit}</span>;
  if (variant === "o") return <span style={{ display: "inline-flex", ...style }} {...rest}>{OMark}</span>;

  // lockup / wordmark: use the solid "O" mark as the letter "o"
  const fontSize = size * 1.22; // wordmark cap height relative to mark
  const oSize = size * 0.82;
  const wordmark = (
    <span style={{ display: "inline-flex", alignItems: "center", fontFamily, fontWeight: 700, fontSize, letterSpacing: "-0.02em", color: ink, lineHeight: 1 }}>
      C
      <span style={{ display: "inline-block", width: oSize, height: oSize, margin: "0 0.02em" }}>
        <svg viewBox="0 0 100 100" width={oSize} height={oSize} style={{ display: "block" }}>
          <circle cx="50" cy="50" r="30" fill="none" stroke={brand} strokeWidth="8" />
          <circle cx="50" cy="50" r="12.5" fill={core} />
        </svg>
      </span>
      rlang
    </span>
  );

  if (variant === "wordmark") return <span style={{ display: "inline-flex", ...style }} aria-label={title} {...rest}>{wordmark}</span>;

  // lockup = icon + wordmark
  return (
    <span style={{ display: "inline-flex", alignItems: "center", gap: size * 0.3, ...style }} aria-label={title} {...rest}>
      {Orbit}
      <span style={{ fontFamily, fontWeight: 700, fontSize, letterSpacing: "-0.02em", color: ink, lineHeight: 1 }}>{title}</span>
    </span>
  );
}
