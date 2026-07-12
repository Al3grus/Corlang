import React, { useEffect, useRef } from "react";

/**
 * CorlangLoader — animated loading screen that resolves the two-ring
 * "Orbit Core" mark into the Corlang wordmark (the mark becomes the "o").
 * At 100% the mark matches the app icon exactly.
 *
 * Timeline (loops, DUR ms):
 *   0    -> 50%  : LOAD. Both rings fill and the % counter climbs.
 *                  - Outer ring: its visible arc grows AND the ring sweeps
 *                    (counter-rotates) into the icon's final -52deg angle.
 *                  - Inner arc: anchored at its right end, REVEALS leftward
 *                    (stroke-dashoffset), landing on the icon's inner arc.
 *                  - Core (red dot): stays hidden until ~80%, then POPS in
 *                    with an ease-out-back overshoot, finishing at 100%.
 *   50%  -> ~88% : SETTLE. Mark eases (ease-in-out) from big+centered down to
 *                  letter size, translating purely LEFT into its "o" slot.
 *                  Letters hold their final positions — no reflow.
 *   62%  -> ~95% : Letters (C r l a n g) fade + rise into place, staggered.
 *   82%  -> ...  : Tagline fades in.
 *   93%  -> 100% : Stage fades out, loop restarts.
 *
 * In production, drive `prog` from real load progress instead of the timer
 * (see README.md) and use onDone to trigger your app-ready transition.
 */
export default function CorlangLoader({
  brand = "#2f7fae",       // ring color
  core = "#c8402c",        // center dot ("molten core")
  ink = "#f4efe6",         // letters
  bg = "#0f1620",          // splash background
  tagline = "Jezik u srži",
  fontFamily = "'Helvetica Neue', Helvetica, Arial, sans-serif",
  durationMs = 4400,
  onDone,                  // called once when a cycle first reaches 100%
}) {
  const stageRef = useRef(null);
  const rafRef = useRef(0);
  const startRef = useRef(0);
  const markDX = useRef(0);
  const firedDone = useRef(false);

  useEffect(() => {
    const stage = stageRef.current;
    if (!stage) return;

    const letters = [...stage.querySelectorAll("[data-role='letter']")];
    const q = (s) => stage.querySelector(s);
    const cl = (x) => Math.max(0, Math.min(1, x));
    const eo = (x) => 1 - Math.pow(1 - x, 3);                         // easeOutCubic
    const eio = (x) => x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2; // easeInOutCubic
    const easeOutBack = (x) => {
      const c = 2.2;
      return x <= 0 ? 0 : 1 + (c + 1) * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2);
    };

    const measure = () => {
      const wm = q("[data-role='wordmark']");
      const mk = q("[data-role='mark']");
      const wr = wm.getBoundingClientRect();
      const mr = mk.getBoundingClientRect();
      markDX.current = wr.left + wr.width / 2 - (mr.left + mr.width / 2);
    };

    const frame = (now) => {
      const t = ((now - startRef.current) % durationMs) / durationMs;
      const loadEnd = 0.5;
      const prog = cl(t / loadEnd);

      // stage fade for a clean loop
      let g = 1;
      if (t < 0.04) g = t / 0.04;
      else if (t > 0.93) g = cl(1 - (t - 0.93) / 0.07);
      stage.style.opacity = g;

      // outer ring: grow visible arc + sweep into final -52deg
      const arcO = q("[data-role='arcOuter']");
      const ringO = q("[data-role='ringOuter']");
      if (arcO) arcO.style.strokeDasharray = 132 * prog + " 207.3";
      const ep = eo(prog);
      if (ringO) ringO.style.transform = `rotate(${-52 - (1 - ep) * 210}deg)`;

      // inner arc: anchored at right, reveals leftward
      const arcI = q("[data-role='arcInner']");
      if (arcI) arcI.style.strokeDashoffset = String(80 * (1 - prog));

      // core: pops in near the end with overshoot
      const coreEl = q("[data-role='core']");
      if (coreEl) {
        const cp = cl((prog - 0.8) / 0.2);
        coreEl.style.transform = `scale(${easeOutBack(cp)})`;
        coreEl.style.transformBox = "fill-box";
        coreEl.style.transformOrigin = "center";
      }

      // mark: scale down + translate LEFT into slot (ease-in-out)
      const dx = markDX.current || 0;
      let s = 2.4, tx = dx;
      if (t >= loadEnd) {
        const sp = eio(cl((t - loadEnd) / 0.38));
        s = 2.4 - 1.4 * sp;
        tx = dx * (1 - sp);
      }
      const mark = q("[data-role='mark']");
      if (mark) mark.style.transform = `translateX(${tx}px) scale(${Math.max(1, s)})`;

      // letters fade + rise in place, staggered
      letters.forEach((el, i) => {
        const st = 0.62 + i * 0.04;
        const lp = eio(cl((t - st) / 0.18));
        el.style.opacity = String(lp);
        el.style.transform = `translateY(${(1 - lp) * 8}px)`;
      });

      // % counter
      const pct = q("[data-role='pct']");
      if (pct) {
        if (t < loadEnd) { pct.style.opacity = "1"; pct.textContent = Math.round(prog * 100) + "%"; }
        else pct.style.opacity = String(cl(1 - (t - loadEnd) / 0.08));
      }

      // tagline
      const tag = q("[data-role='tag']");
      if (tag) tag.style.opacity = String(cl((t - 0.82) / 0.08));

      if (!firedDone.current && t >= loadEnd) { firedDone.current = true; onDone && onDone(); }

      rafRef.current = requestAnimationFrame(frame);
    };

    const run = () => {
      measure();
      startRef.current = performance.now();
      rafRef.current = requestAnimationFrame(frame);
    };
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(run);
    else run();

    return () => cancelAnimationFrame(rafRef.current);
  }, [durationMs, onDone]);

  const letterStyle = { display: "inline-block", opacity: 0, lineHeight: 1 };
  const L = (ch, key) => <span key={key} data-role="letter" style={letterStyle}>{ch}</span>;

  return (
    <div style={{ position: "absolute", inset: 0, background: bg, overflow: "hidden" }}>
      <div ref={stageRef} style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center" }}>
        <div data-role="wordmark" style={{ display: "inline-flex", alignItems: "center", fontFamily, fontWeight: 700, fontSize: 56, letterSpacing: "-0.02em", color: ink }}>
          {L("C", "c")}
          <span data-role="mark" style={{ display: "inline-block", flex: "0 0 auto", width: 46, height: 46, transformOrigin: "50% 50%" }}>
            <svg viewBox="0 0 100 100" width="46" height="46" style={{ display: "block", overflow: "visible" }}>
              <g data-role="ringOuter" style={{ transformBox: "fill-box", transformOrigin: "center" }}>
                <circle data-role="arcOuter" cx="50" cy="50" r="33" fill="none" stroke={brand} strokeWidth="6" strokeLinecap="round" strokeDasharray="0 207.3" />
              </g>
              <g data-role="ringInner">
                <path data-role="arcInner" d="M70.37 44.92 A21 21 0 1 0 37.07 66.55" fill="none" stroke={brand} strokeWidth="6" strokeLinecap="round" strokeDasharray="80 80" strokeDashoffset="80" />
              </g>
              <circle data-role="core" cx="50" cy="50" r="9" fill={core} />
            </svg>
          </span>
          {L("r", "r")}{L("l", "l")}{L("a", "a")}{L("n", "n")}{L("g", "g")}
        </div>
        <div data-role="pct" style={{ position: "absolute", top: "59%", left: 0, right: 0, textAlign: "center", fontFamily: "'Space Mono', monospace", fontSize: 13, letterSpacing: 3, color: "rgba(244,239,230,0.5)" }}>0%</div>
        <div data-role="tag" style={{ position: "absolute", top: "59%", left: 0, right: 0, textAlign: "center", fontFamily, fontSize: 15, color: "rgba(244,239,230,0.62)", opacity: 0 }}>{tagline}</div>
      </div>
    </div>
  );
}
