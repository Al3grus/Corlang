"""Render Corlang Play Store assets from the Orbit Core mark.

Geometry is taken verbatim from design_handoff_corlang_loader/logo-orbit-core.svg
(and matches app/src/main/res/drawable/ic_launcher_foreground.xml):
  outer ring r=33 stroke 6, dash 132/76 -> 63.66% of the circle, rotated -52
  inner ring r=21 stroke 6, dash  80/52 -> 60.63% of the circle, rotated 128
  core circle r=9
in a 100x100 space. SVG dashes start at 3 o'clock and run clockwise, which is
exactly how Pillow measures arc angles, so the rotation is the start angle.
"""
import math
import os
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

OUT = os.path.join(os.path.dirname(__file__), "out")
os.makedirs(OUT, exist_ok=True)

BG = (0x0F, 0x16, 0x20)
RING = (0x2F, 0x7F, 0xAE)
CORE = (0xC8, 0x40, 0x2C)
TEXT = (0xF2, 0xF6, 0xFA)
MUTED = (0x8F, 0xA3, 0xB8)

SS = 4  # supersample factor

FONT_BOLD = r"C:\Windows\Fonts\segoeuib.ttf"
FONT_REG = r"C:\Windows\Fonts\segoeui.ttf"
FONT_LIGHT = r"C:\Windows\Fonts\segoeuil.ttf"


def arc_round(draw, cx, cy, r, width, start_deg, sweep_deg, color):
    """Stroke an arc centred on radius r with round caps."""
    bb = [cx - r - width / 2, cy - r - width / 2, cx + r + width / 2, cy + r + width / 2]
    draw.arc(bb, start_deg, start_deg + sweep_deg, fill=color, width=int(round(width)))
    for a in (start_deg, start_deg + sweep_deg):
        px = cx + r * math.cos(math.radians(a))
        py = cy + r * math.sin(math.radians(a))
        draw.ellipse([px - width / 2, py - width / 2, px + width / 2, py + width / 2], fill=color)


def draw_mark(draw, cx, cy, unit):
    """Draw the Orbit Core mark; `unit` = one unit of the 100-wide source space."""
    arc_round(draw, cx, cy, 33 * unit, 6 * unit, -52, 360 * (132 / (2 * math.pi * 33)), RING)
    arc_round(draw, cx, cy, 21 * unit, 6 * unit, 128, 360 * (80 / (2 * math.pi * 21)), RING)
    r = 9 * unit
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=CORE)


def glow(size, cx, cy, radius, color, strength):
    """Soft radial glow layer."""
    layer = Image.new("RGB", size, (0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=color)
    layer = layer.filter(ImageFilter.GaussianBlur(radius * 0.55))
    return Image.blend(Image.new("RGB", size, (0, 0, 0)), layer, strength)


# ---------------------------------------------------------------- app icon
def icon(path, with_glow=True):
    S = 512 * SS
    img = Image.new("RGB", (S, S), BG)
    if with_glow:
        img = ImageChops.screen(img, glow((S, S), S / 2, S / 2, S * 0.42, RING, 0.18))
    d = ImageDraw.Draw(img)
    # mark occupies ~60% of the canvas (matches the adaptive-icon safe-zone framing)
    unit = S * 0.60 / 72.0
    draw_mark(d, S / 2, S / 2, unit)
    img.resize((512, 512), Image.LANCZOS).save(path)
    print("wrote", path)


def _mask_from(g):
    return g.convert("L").point(lambda v: min(255, int(v * 1.6)))


# --------------------------------------------------------- feature graphic
def feature(path):
    """
    The mark and the wordmark, centred, on the brand ground.

    It used to carry two strap lines: "Croatian, Portuguese, French" and "A0 to B2, one lesson a
    day, works offline". Both had gone stale. French is authored but hidden from
    content/_index.json, so the graphic advertised a course the store could not deliver, and
    neither shipped course runs past B1. A feature graphic is also the asset Play crops and
    overlays its own text on, so small type near an edge is the first thing lost. A centred
    lockup survives that; a strap line does not.

    Centred by MEASURING, not by predicting. The first attempt computed the position from
    `72 * unit` and a textbbox taken at one anchor while drawing at another, and produced a mark
    low-left with the word floating high-right. Drawing both onto a transparent layer and asking
    Pillow for the real bounding box removes every assumption about font metrics and mark
    geometry at once.
    """
    W, H = 1024 * SS, 500 * SS

    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)

    unit = (H * 0.52) / 72.0
    f_word = ImageFont.truetype(FONT_BOLD, int(132 * SS))

    # Somewhere roughly right; the exact spot does not matter, it gets re-centred below.
    probe_cx, probe_cy = W * 0.30, H * 0.5
    draw_mark(ld, probe_cx, probe_cy, unit)
    mark_box = layer.getbbox()

    gap = 54 * SS
    ld.text((mark_box[2] + gap, probe_cy), "Corlang", font=f_word, fill=TEXT, anchor="lm")

    box = layer.getbbox()
    dx = int(round((W - (box[2] - box[0])) / 2 - box[0]))
    dy = int(round((H - (box[3] - box[1])) / 2 - box[1]))

    img = Image.new("RGB", (W, H), BG)
    img = ImageChops.screen(
        img, glow((W, H), (mark_box[0] + mark_box[2]) / 2 + dx, H * 0.5, H * 0.44, RING, 0.20))
    img.paste(layer.transform((W, H), Image.AFFINE, (1, 0, -dx, 0, 1, -dy),
                              resample=Image.NEAREST),
              (0, 0), layer.transform((W, H), Image.AFFINE, (1, 0, -dx, 0, 1, -dy),
                                      resample=Image.NEAREST))

    img.resize((1024, 500), Image.LANCZOS).save(path)
    print("wrote", path)


icon(os.path.join(OUT, "play-icon-512.png"))
feature(os.path.join(OUT, "feature-graphic-1024x500.png"))
