import React from "react";

/**
 * CorlangLogo — the single source of truth for the Corlang mark/logo.
 * Use this everywhere the brand appears so color + geometry stay consistent.
 *
 * The mark is the two-ring "Orbit Core": an outer broken ring, an inner
 * broken arc, and a solid core — the same mark the loader resolves into,
 * and the "o" in the Corlang wordmark.
 *
 * variant:
 *   "orbit"    -> the mark alone (app icon / favicon / chrome)  [default]
 *   "lockup"   -> icon + "Corlang" wordmark, side by side
 *   "wordmark" -> "Corlang" with the mark AS the "o"
 */
export default function CorlangLogo({
  variant = "orbit",
  size = 40,               // mark height in px
  brand = "#2f7fae",       // rings
  core = "#c8402c",        // center dot
  ink = "#2b3038",         // wordmark text (use #f4efe6 on dark)
  fontFamily = "'Helvetica Neue', Helvetica, Arial, sans-serif",
  title = "Corlang",
  style,
  ...rest
}) {
  const Mark = (px) => (
    <svg viewBox="0 0 100 100" width={px} height={px} role="img" aria-label={title} style={{ display: "block" }}>
      {/* outer broken ring, final icon angle -52deg */}
      <circle cx="50" cy="50" r="33" fill="none" stroke={brand} strokeWidth="6" strokeLinecap="round" strokeDasharray="132 76" transform="rotate(-52 50 50)" />
      {/* inner broken arc, anchored right / opening lower-right */}
      <path d="M70.37 44.92 A21 21 0 1 0 37.07 66.55" fill="none" stroke={brand} strokeWidth="6" strokeLinecap="round" />
      <circle cx="50" cy="50" r="9" fill={core} />
    </svg>
  );

  if (variant === "orbit")
    return <span style={{ display: "inline-flex", ...style }} {...rest}>{Mark(size)}</span>;

  const fontSize = size * 1.22;

  if (variant === "wordmark") {
    const oSize = size * 0.9;
    return (
      <span style={{ display: "inline-flex", alignItems: "center", fontFamily, fontWeight: 700, fontSize, letterSpacing: "-0.02em", color: ink, lineHeight: 1, ...style }} aria-label={title} {...rest}>
        C
        <span style={{ display: "inline-block", width: oSize, height: oSize, margin: "0 0.02em" }}>{Mark(oSize)}</span>
        rlang
      </span>
    );
  }

  // lockup = icon + wordmark
  return (
    <span style={{ display: "inline-flex", alignItems: "center", gap: size * 0.3, ...style }} aria-label={title} {...rest}>
      {Mark(size)}
      <span style={{ fontFamily, fontWeight: 700, fontSize, letterSpacing: "-0.02em", color: ink, lineHeight: 1 }}>{title}</span>
    </span>
  );
}
