import React, { useEffect, useRef } from "react";

/**
 * CorlangLoader — animated loading screen that resolves the "Orbit Core" mark
 * into the Corlang wordmark (the mark becomes the "o").
 *
 * Timeline (loops, DUR ms):
 *   0    -> 50%  : determinate ring FILLS 0->100% (stroke-dashoffset), % counter,
 *                  whole ring rotates one turn. Ring closes into a full "O" only at 100%.
 *   50%  -> 80%  : mark eases from big+centered down to letter size, translating
 *                  purely LEFT into its "o" slot (no reflow — letters hold position).
 *   58%  -> ~95% : the 6 letters (C r l a n g) fade + slide up in place, staggered.
 *   82%  -> ...  : tagline fades in.
 *   93%  -> 100% : whole stage fades out, then the loop restarts.
 *
 * Colors / type are props so you can wire them to your theme.
 */
export default function CorlangLoader({
  brand = "#2f7fae",       // ring color
  core = "#c8402c",        // center dot ("molten core")
  ink = "#f4efe6",         // letters / on-dark text
  bg = "#0f1620",          // splash background
  tagline = "Jezik u srži",
  fontFamily = "'Space Grotesk', system-ui, sans-serif",
  durationMs = 4400,
  onDone,                  // optional: called once when a cycle first reaches 100%
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
    const eo = (x) => 1 - Math.pow(1 - x, 3); // easeOutCubic
    const C = 2 * Math.PI * 30;               // ring circumference (r = 30)

    const measure = () => {
      const wm = q("[data-role='wordmark']");
      const mk = q("[data-role='mark']");
      const wr = wm.getBoundingClientRect();
      const mr = mk.getBoundingClientRect();
      // horizontal delta to push the mark to the wordmark's center during load
      markDX.current = wr.left + wr.width / 2 - (mr.left + mr.width / 2);
    };

    const frame = (now) => {
      const t = ((now - startRef.current) % durationMs) / durationMs;
      const loadEnd = 0.5;

      // stage fade for a clean loop
      let g = 1;
      if (t < 0.04) g = t / 0.04;
      else if (t > 0.93) g = cl(1 - (t - 0.93) / 0.07);
      stage.style.opacity = g;

      // determinate ring fill + one rotation
      const prog = cl(t / loadEnd);
      const arc = q("[data-role='ringArc']");
      const ring = q("[data-role='ring']");
      if (arc) arc.style.strokeDashoffset = String(C * (1 - prog));
      if (ring) ring.style.transform = `rotate(${prog * 360}deg)`;

      // mark: scale down + translate LEFT into slot
      const dx = markDX.current || 0;
      let s = 2.4, tx = dx;
      if (t >= loadEnd) {
        const sp = eo(cl((t - loadEnd) / 0.3));
        s = 2.4 - 1.4 * sp;
        tx = dx * (1 - sp);
      }
      const mark = q("[data-role='mark']");
      if (mark) mark.style.transform = `translateX(${tx}px) scale(${Math.max(1, s)})`;

      // letters fade in place, staggered
      letters.forEach((el, i) => {
        const st = 0.58 + i * 0.04;
        const lp = eo(cl((t - st) / 0.16));
        el.style.opacity = String(lp);
        el.style.transform = `translateY(${(1 - lp) * 8}px)`;
      });

      // % counter
      const pct = q("[data-role='pct']");
      if (pct) {
        if (t < loadEnd) { pct.style.opacity = "1"; pct.textContent = Math.round((t / loadEnd) * 100) + "%"; }
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
    // wait for the webfont so measurement is accurate
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(run);
    else run();

    return () => cancelAnimationFrame(rafRef.current);
  }, [durationMs, onDone]);

  const letterStyle = { display: "inline-block", opacity: 0, lineHeight: 1 };
  const L = (ch) => <span data-role="letter" style={letterStyle}>{ch}</span>;

  return (
    <div style={{ position: "absolute", inset: 0, background: bg, overflow: "hidden" }}>
      <div ref={stageRef} style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center" }}>
        <div data-role="wordmark" style={{ display: "inline-flex", alignItems: "center", fontFamily, fontWeight: 700, fontSize: 56, letterSpacing: "-0.02em", color: ink }}>
          {L("C")}
          <span data-role="mark" style={{ display: "inline-block", flex: "0 0 auto", width: 46, height: 46, margin: 0, transformOrigin: "50% 50%" }}>
            <svg viewBox="0 0 100 100" width="46" height="46" style={{ display: "block", overflow: "visible" }}>
              <g data-role="ring" style={{ transformBox: "fill-box", transformOrigin: "center" }}>
                <circle data-role="ringArc" cx="50" cy="50" r="30" fill="none" stroke={brand} strokeWidth="8" strokeLinecap="round" transform="rotate(-90 50 50)" strokeDasharray={2 * Math.PI * 30} strokeDashoffset={2 * Math.PI * 30} />
              </g>
              <circle cx="50" cy="50" r="12.5" fill={core} />
            </svg>
          </span>
          {L("r")}{L("l")}{L("a")}{L("n")}{L("g")}
        </div>
        <div data-role="pct" style={{ position: "absolute", top: "59%", left: 0, right: 0, textAlign: "center", fontFamily: "'Space Mono', monospace", fontSize: 13, letterSpacing: 3, color: "rgba(244,239,230,0.5)" }}>0%</div>
        <div data-role="tag" style={{ position: "absolute", top: "59%", left: 0, right: 0, textAlign: "center", fontFamily, fontSize: 15, color: "rgba(244,239,230,0.62)", opacity: 0 }}>{tagline}</div>
      </div>
    </div>
  );
}
