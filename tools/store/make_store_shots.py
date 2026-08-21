# -*- coding: utf-8 -*-
"""Turn raw phone screenshots into framed Play Store listing images.

    python tools/store/make_store_shots.py

Reads docs/store-assets/screenshots/*.jpeg and writes docs/store-assets/play/NN-name.png,
1080x1920 each, ready to upload.

What this does NOT do, on purpose: it never edits the screenshot itself. Play requires listing
images to show the actual app, so the phone content is the untouched capture, scaled. Everything
added lives OUTSIDE the phone frame: a background, a caption, and the device bezel.

The background is the app's own dark theme rather than the website's light one. These captures
are dark, and a dark UI floating on cool paper reads as a mistake.
"""
import io
import os
import math

from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, 'docs', 'store-assets', 'screenshots')
OUT = os.path.join(ROOT, 'docs', 'store-assets', 'play')
FONT = os.path.join(ROOT, 'tools', 'store', 'fonts', 'Manrope.ttf')

W, H = 1080, 1920                 # Play's standard phone portrait
BG_TOP = (14, 19, 24)
BG_BOTTOM = (20, 30, 40)
INK = (240, 244, 248)
MUTED = (150, 165, 180)
BLUE = (47, 127, 174)             # brand
CORE = (200, 64, 44)              # brand core

# Order matters: Play shows the first few largest, and these are ordered by what actually sells
# the product rather than by where they sit in the app.
SHOTS = [
    ('learn-tab.jpeg',   'One lesson a day.',        'Ten minutes, then you are done.'),
    ('lesson-4.jpeg',    'Write it from memory.',    'Every lesson ends with no prompts.'),
    ('flashcard.jpeg',   'Words come back.',         'Right before you would forget them.'),
    ('lesson-1.jpeg',    'Real sentences.',          'Every word with audio and its forms.'),
    ('lesson-2.jpeg',    'Type it, do not guess.',   'Production, not multiple choice.'),
    ('ai-tutor.jpeg',    'A tutor that corrects.',   'Chat in the language, get it fixed.'),
    ('progress-tab.jpeg', 'Watch it add up.',        'Streak, words learned, the whole path.'),
]


def font(size, weight=800):
    f = ImageFont.truetype(FONT, size)
    try:
        f.set_variation_by_axes([weight])
    except Exception:
        pass
    return f


def gradient(w, h, top, bottom):
    """A vertical gradient, built one row at a time then resized: cheap and smooth."""
    strip = Image.new('RGB', (1, h))
    px = strip.load()
    for y in range(h):
        t = y / max(1, h - 1)
        px[0, y] = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
    return strip.resize((w, h), Image.BILINEAR)


def glow(canvas, cx, cy, radius, color, strength=0.5):
    """A soft brand-coloured bloom behind the phone, so the frame is not floating on flat black."""
    layer = Image.new('L', (canvas.width, canvas.height), 0)
    ImageDraw.Draw(layer).ellipse(
        [cx - radius, cy - radius, cx + radius, cy + radius], fill=int(255 * strength))
    layer = layer.filter(ImageFilter.GaussianBlur(radius * 0.55))
    canvas.paste(Image.new('RGB', canvas.size, color), (0, 0), layer)


def rounded(im, r):
    mask = Image.new('L', im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, im.width - 1, im.height - 1], r, fill=255)
    out = im.copy()
    out.putalpha(mask)
    return out


def wrap(draw, text, f, max_w):
    words, lines, cur = text.split(), [], ''
    for w in words:
        trial = (cur + ' ' + w).strip()
        if draw.textlength(trial, font=f) <= max_w:
            cur = trial
        else:
            if cur:
                lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def compose(src_name, headline, sub, index):
    shot = Image.open(os.path.join(SRC, src_name)).convert('RGB')

    canvas = gradient(W, H, BG_TOP, BG_BOTTOM)

    # Geometry is driven by the HEIGHT available under the caption, not by a fixed width. Fixing
    # the width put the bottom of the device 150px past the canvas, which reads as a rendering
    # bug rather than a deliberate bleed, and it cut off the navigation bar that tells a viewer
    # this is a real app.
    top = 430                      # first pixel the device may occupy
    bottom_margin = 62
    bez = 14
    avail_h = H - top - bottom_margin - bez * 2
    phone_h = avail_h
    phone_w = round(shot.width * (phone_h / shot.height))
    px = (W - phone_w) // 2
    py = top + bez

    glow(canvas, W // 2, py + phone_h // 3, 520, (30, 92, 130), 0.30)
    glow(canvas, W // 2, py - 80, 300, (47, 127, 174), 0.12)

    d = ImageDraw.Draw(canvas)

    # caption
    f_head = font(62, 800)
    f_sub = font(30, 500)
    lines = wrap(d, headline, f_head, W - 160)
    y = 150
    for ln in lines:
        d.text((W // 2, y), ln, font=f_head, fill=INK, anchor='ma')
        y += 74
    y += 6
    for ln in wrap(d, sub, f_sub, W - 200):
        d.text((W // 2, y), ln, font=f_sub, fill=MUTED, anchor='ma')
        y += 40

    # a short brand rule under the caption, in the core red: the one warm mark on the image
    d.rounded_rectangle([W // 2 - 26, y + 22, W // 2 + 26, y + 27], 3, fill=CORE)

    # device: a bezel, then the untouched capture inside it
    radius = 46
    frame = [px - bez, py - bez, px + phone_w + bez, py + phone_h + bez]
    d.rounded_rectangle(frame, radius + bez, fill=(26, 33, 41), outline=(58, 72, 86), width=2)
    inner = rounded(shot.resize((phone_w, phone_h), Image.LANCZOS), radius)
    canvas.paste(inner, (px, py), inner)

    os.makedirs(OUT, exist_ok=True)
    name = f"{index:02d}-{os.path.splitext(src_name)[0]}.png"
    path = os.path.join(OUT, name)
    canvas.save(path, 'PNG', optimize=True)
    return path, canvas.size


def main():
    if not os.path.isfile(FONT):
        raise SystemExit('missing %s' % FONT)
    made = []
    for i, (src, head, sub) in enumerate(SHOTS, 1):
        p = os.path.join(SRC, src)
        if not os.path.isfile(p):
            print('  MISSING %s, skipped' % src)
            continue
        path, size = compose(src, head, sub, i)
        made.append(path)
        print(f"  {os.path.basename(path):<28} {size[0]}x{size[1]}  {head}")
    print(f"\n{len(made)} listing images in docs/store-assets/play/")


if __name__ == '__main__':
    main()
