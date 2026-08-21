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
BLUE = (47, 127, 174)             # brand
CORE = (200, 64, 44)              # brand core

# Two backgrounds, chosen per screenshot by measuring the capture rather than by a flag that has
# to be kept in sync. A light phone on a dark ground (or the reverse) reads as a mistake, and the
# app genuinely ships both themes, so the listing alternates and shows that.
THEMES = {
    'dark':  dict(top=(14, 19, 24), bottom=(20, 30, 40), ink=(240, 244, 248),
                  muted=(150, 165, 180), bezel=(26, 33, 41), edge=(58, 72, 86),
                  glow=(30, 92, 130), glow2=(47, 127, 174), g1=0.30, g2=0.12),
    # the APP's light theme, warm paper, not the website's cool one: this frames the app
    'light': dict(top=(253, 250, 245), bottom=(240, 232, 220), ink=(43, 33, 24),
                  muted=(122, 105, 86), bezel=(255, 253, 249), edge=(214, 202, 186),
                  glow=(226, 212, 194), glow2=(214, 196, 172), g1=0.55, g2=0.30),
}

# Order matters: Play shows the first few largest, and these are ordered by what sells rather
# than by where the screen sits in the app. Themes alternate on purpose.
SHOTS = [
    ('learn-tab.jpeg',                'One lesson a day.',      'Ten minutes, then you are done.'),
    ('lesson-4.jpeg',                 'Write it from memory.',  'Every lesson ends with no prompts.'),
    ('light-theme-review-tab.jpeg',   'Every word has a date.', 'Reviews arrive the day you need them.'),
    ('flashcard.jpeg',                'One card at a time.',    'Say how it went. That sets the next date.'),
    ('light-theme-mock-exam-2.jpeg',  'Built for the real exam.', 'Mock papers in the official format.'),
    ('ai-tutor.jpeg',                 'A tutor that corrects.', 'Chat in the language, get it fixed.'),
    ('light-theme-languages.jpeg',    'Two languages.',         'Croatian and Portuguese, kept separate.'),
    ('progress-tab.jpeg',             'Watch it add up.',       'Streak, words learned, the whole path.'),
]


def theme_for(im):
    """Pick the palette from the capture itself: mean luma over a small sample."""
    small = im.convert('RGB').resize((16, 32))
    px = list(small.get_flattened_data()) if hasattr(small, 'get_flattened_data') else list(small.getdata())
    luma = sum(sum(p) / 3 for p in px) / len(px)
    return THEMES['light'] if luma > 128 else THEMES['dark']


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
    th = theme_for(shot)

    canvas = gradient(W, H, th['top'], th['bottom'])

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

    glow(canvas, W // 2, py + phone_h // 3, 520, th['glow'], th['g1'])
    glow(canvas, W // 2, py - 80, 300, th['glow2'], th['g2'])

    d = ImageDraw.Draw(canvas)

    # caption
    f_head = font(62, 800)
    f_sub = font(30, 500)
    lines = wrap(d, headline, f_head, W - 160)
    y = 150
    for ln in lines:
        d.text((W // 2, y), ln, font=f_head, fill=th['ink'], anchor='ma')
        y += 74
    y += 6
    for ln in wrap(d, sub, f_sub, W - 200):
        d.text((W // 2, y), ln, font=f_sub, fill=th['muted'], anchor='ma')
        y += 40

    # a short brand rule under the caption, in the core red: the one warm mark on the image
    d.rounded_rectangle([W // 2 - 26, y + 22, W // 2 + 26, y + 27], 3, fill=CORE)

    # device: a bezel, then the untouched capture inside it
    radius = 46
    frame = [px - bez, py - bez, px + phone_w + bez, py + phone_h + bez]
    d.rounded_rectangle(frame, radius + bez, fill=th['bezel'], outline=th['edge'], width=2)
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
    # Wipe first. Renaming or reordering SHOTS leaves the previous run's files behind, and a
    # stale 07-progress-tab.png sitting beside the new 08 is exactly the sort of thing that gets
    # uploaded by accident.
    if os.path.isdir(OUT):
        for f in os.listdir(OUT):
            if f.endswith('.png'):
                os.remove(os.path.join(OUT, f))
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
