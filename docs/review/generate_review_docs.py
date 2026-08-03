#!/usr/bin/env python3
"""
Generate a printable, self-contained content-review document per language, for native speakers
to check correctness (translations, examples, grammar, quiz answers).

Reads the shipped JSON content in app/src/main/assets/content/<lang>/ and writes
docs/review/<lang>-content-review.html. Open in a browser and print (or print-to-PDF) to hand to
a reviewer. The target-language term is stored under the legacy "hr" key for every language.

Run from the repo root:  python docs/review/generate_review_docs.py
"""
import html
import json
import os
import glob

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CONTENT = os.path.join(REPO, "app", "src", "main", "assets", "content")
OUT_DIR = os.path.join(REPO, "docs", "review")
LANGS = ["hr", "fr", "pt", "de", "it"]


def load(lang, name):
    path = os.path.join(CONTENT, lang, name)
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def e(x):
    return html.escape(str(x)) if x is not None else ""


def vocab_section(lang):
    files = sorted(
        p for p in glob.glob(os.path.join(CONTENT, lang, "vocab", "*.json"))
        if not p.endswith("_index.json")
    )
    rows, total = [], 0
    for path in files:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        for pack in data.get("packs", []):
            words = pack.get("words", [])
            total += len(words)
            rows.append(f'<h3>{e(pack.get("title", pack.get("id", "")))} '
                        f'<span class="lvl">{e(pack.get("level", ""))}</span></h3>')
            rows.append('<table><thead><tr>'
                        '<th>Term</th><th>English</th><th>Part of speech</th>'
                        '<th>Note</th><th>Example</th><th class="corr">Correction / notes</th>'
                        '</tr></thead><tbody>')
            for w in words:
                ex = w.get("example") or {}
                example = ""
                if ex:
                    example = (f'<span class="tgt">{e(ex.get("target", ""))}</span><br>'
                               f'<span class="gloss">{e(ex.get("gloss", ""))}</span>')
                rows.append(
                    f'<tr><td class="tgt">{e(w.get("hr", ""))}</td>'
                    f'<td>{e(w.get("en", ""))}</td>'
                    f'<td class="pos">{e(w.get("pos", ""))}</td>'
                    f'<td class="note">{e(w.get("note", ""))}</td>'
                    f'<td>{example}</td>'
                    f'<td class="corr"></td></tr>'
                )
            rows.append('</tbody></table>')
    return total, "\n".join(rows)


def grammar_section(lang):
    data = load(lang, "grammar.json")
    if not data:
        return ""
    out = []
    for level in data.get("levels", []):
        out.append(f'<h3>{e(level.get("levelId", ""))}</h3>')
        if level.get("intro"):
            out.append(f'<p class="intro-sm">{e(level["intro"])}</p>')
        for topic in level.get("topics", []):
            out.append(f'<h4>{e(topic.get("title", ""))}</h4>')
            if topic.get("summary"):
                out.append(f'<p>{e(topic["summary"])}</p>')
            for table in topic.get("tables", []) or []:
                out.append(f'<pre class="tbl">{e(table)}</pre>')
            exs = topic.get("examples", []) or []
            if exs:
                out.append('<ul class="ex">')
                for ex in exs:
                    out.append(f'<li><span class="tgt">{e(ex.get("target", ""))}</span> '
                               f'— <span class="gloss">{e(ex.get("gloss", ""))}</span></li>')
                out.append('</ul>')
            out.append('<div class="corr-line">Corrections: _______________________________</div>')
    return "\n".join(out)


def cheatsheet_section(lang):
    data = load(lang, "cheatsheet.json")
    if not data:
        return ""
    out = []
    for s in data.get("sections", []):
        out.append(f'<h4>{e(s.get("title", ""))}</h4>')
        bullets = s.get("bullets", []) or []
        if bullets:
            out.append('<ul>')
            for b in bullets:
                out.append(f'<li>{e(b)}</li>')
            out.append('</ul>')
        if s.get("diagram"):
            out.append(f'<pre class="tbl">{e(s["diagram"])}</pre>')
        for ex in s.get("examples", []) or []:
            out.append(f'<p class="ex1"><span class="tgt">{e(ex.get("target", ""))}</span> '
                       f'— <span class="gloss">{e(ex.get("gloss", ""))}</span></p>')
    return "\n".join(out)


def quizzes_section(lang):
    data = load(lang, "quizzes.json")
    if not data:
        return ""
    out = []
    for q in data.get("quizzes", []):
        out.append(f'<h4>{e(q.get("levelId", ""))} — {e(q.get("title", q.get("id", "")))}</h4>')
        for i, qq in enumerate(q.get("questions", []), 1):
            out.append(f'<p class="q"><b>{i}.</b> {e(qq.get("prompt", ""))}</p>')
            qtype = qq.get("type", "")
            if qtype == "REORDER":
                answer = " ".join(qq.get("ordered", []) or [])
            elif qtype == "MATCH":
                answer = "; ".join(f'{e(p.get("left"))} → {e(p.get("right"))}'
                                   for p in qq.get("pairs", []) or [])
            else:
                answer = qq.get("answer", "")
            out.append(f'<p class="a">Answer: <span class="tgt">{e(answer)}</span></p>')
            if qq.get("explanation"):
                out.append(f'<p class="expl">{e(qq["explanation"])}</p>')
    return "\n".join(out)


def dialogues_section(lang):
    index_path = os.path.join(CONTENT, lang, "plan", "_index.json")
    if not os.path.exists(index_path):
        return "", 0
    with open(index_path, encoding="utf-8") as f:
        phase_files = json.load(f)
    out, total, cur_level = [], 0, None
    for pf in phase_files:
        phase = load(lang, os.path.join("plan", pf))
        if not phase:
            continue
        for day in phase.get("days", []):
            dialogues = [a for a in day.get("activities", []) if a.get("type") == "DIALOGUE"]
            if not dialogues:
                continue
            level = day.get("level", "")
            if level != cur_level:
                out.append(f'<h3>{e(level)}</h3>')
                cur_level = level
            out.append(f'<h4>Day {e(day.get("day", ""))} &mdash; {e(day.get("title", ""))}</h4>')
            for d in dialogues:
                out.append('<table><thead><tr><th>Speaker</th><th>Term</th><th>English</th>'
                            '<th class="corr">Correction / notes</th></tr></thead><tbody>')
                for line in d.get("lines", []):
                    total += 1
                    out.append(
                        f'<tr><td class="note">{e(line.get("speaker", ""))}</td>'
                        f'<td class="tgt">{e(line.get("hr", ""))}</td>'
                        f'<td>{e(line.get("en", ""))}</td>'
                        f'<td class="corr"></td></tr>'
                    )
                out.append('</tbody></table>')
    return "\n".join(out), total


CSS = """
* { box-sizing: border-box; }
body { font: 15px/1.5 -apple-system, Segoe UI, Roboto, sans-serif; color: #14181c; margin: 0; padding: 32px 40px; max-width: 1000px; }
h1 { font-size: 26px; margin: 0 0 4px; }
h2 { font-size: 20px; margin: 34px 0 10px; border-bottom: 2px solid #2c5d78; padding-bottom: 4px; color: #0b3a50; }
h3 { font-size: 16px; margin: 20px 0 6px; color: #2c5d78; }
h4 { font-size: 15px; margin: 16px 0 4px; }
.sub { color: #5a6b74; margin: 0 0 18px; }
.intro { background: #eef4f7; border-left: 3px solid #2c5d78; padding: 12px 16px; border-radius: 4px; margin: 0 0 8px; font-size: 14px; }
table { border-collapse: collapse; width: 100%; margin: 6px 0 14px; font-size: 13px; }
th, td { border: 1px solid #d5dce1; padding: 6px 8px; text-align: left; vertical-align: top; }
th { background: #f0f4f6; font-size: 12px; text-transform: uppercase; letter-spacing: .04em; color: #48545c; }
.tgt { font-weight: 600; }
.gloss { color: #5a6b74; }
.pos, .note { color: #5a6b74; font-size: 12px; }
.lvl { font-size: 12px; color: #b85e34; font-weight: 600; }
.corr { background: #fcfbf7; min-width: 150px; }
.corr-line { color: #9aa4ab; font-size: 13px; margin: 6px 0 4px; }
pre.tbl { background: #f6f8f9; border: 1px solid #e3e8ec; border-radius: 4px; padding: 10px; overflow-x: auto; font: 12px/1.4 ui-monospace, Consolas, monospace; }
ul.ex, .ex1 { color: #14181c; }
.q { margin: 12px 0 2px; }
.a { margin: 2px 0; color: #0b3a50; }
.expl { margin: 2px 0 10px; color: #5a6b74; font-size: 13px; }
@media print { body { padding: 0; } h2 { page-break-after: avoid; } tr { page-break-inside: avoid; } }
"""


def build(lang):
    meta = load(lang, "meta.json") or {}
    name = meta.get("name", lang)
    native = meta.get("nativeName", "")
    total_words, vocab_html = vocab_section(lang)
    parts = [
        f'<!doctype html><html lang="en"><head><meta charset="utf-8">',
        f'<meta name="viewport" content="width=device-width, initial-scale=1">',
        f'<title>{e(name)} — content review</title><style>{CSS}</style></head><body>',
        f'<h1>{e(name)} <span style="font-weight:400;color:#5a6b74">({e(native)})</span> — content review</h1>',
        f'<p class="sub">Corlang course content for native-speaker review. '
        f'Please mark any translation, spelling, grammar, or naturalness issues in the '
        f'&ldquo;Correction / notes&rdquo; spaces. The bold term is the {e(name)}; the grey text is the English.</p>',
        f'<h2>1. Vocabulary <span style="font-weight:400;font-size:14px;color:#5a6b74">({total_words} words)</span></h2>',
        vocab_html,
        '<h2>2. Grammar</h2>', grammar_section(lang),
        '<h2>3. Cheatsheet</h2>', cheatsheet_section(lang),
        '<h2>4. Quizzes (answers shown for review)</h2>', quizzes_section(lang),
    ]
    dialogues_html, total_lines = dialogues_section(lang)
    parts += [
        f'<h2>5. Dialogues <span style="font-weight:400;font-size:14px;color:#5a6b74">'
        f'({total_lines} lines, grouped by level then day)</span></h2>',
        '<p class="intro">These are the two-person practice conversations from the day-by-day '
        'lessons &mdash; the least-reviewed material, since none of it overlaps the vocabulary '
        'above. Skim for anything a real speaker would never say, wrong register (formal vs. '
        'informal), or an unnatural word order; you do not need to read every single line.</p>',
        dialogues_html,
        '</body></html>',
    ]
    out_path = os.path.join(OUT_DIR, f"{lang}-content-review.html")
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(parts))
    return out_path, total_words


if __name__ == "__main__":
    os.makedirs(OUT_DIR, exist_ok=True)
    for lang in LANGS:
        path, n = build(lang)
        print(f"{lang}: {n} words -> {os.path.relpath(path, REPO)}")
