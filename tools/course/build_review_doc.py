#!/usr/bin/env python3
"""
Build the native-speaker review workbook for one course.

    python tools/course/build_review_doc.py hr
    python tools/course/build_review_doc.py hr --out docs/review/croatian.html

Supersedes docs/review/generate_review_docs.py (Aug 2026), which produced a static
printable page covering vocabulary, grammar, cheatsheet, quizzes and dialogues but
neither the lesson learn-items nor the ~3,200 exercise questions, and had no way for
a reviewer to record a verdict that could be read back in.

The output is ONE self-contained HTML file. It embeds the course's JSON and renders
it in the browser, which is why a 5MB course does not become a 40MB document: the
markup is built on demand, a section at a time.

Design notes, because they are the difference between a workbook that gets finished
and one that does not:

* The Croatian course has ~14,000 reviewable items. A form that asks a reviewer to
  tick 14,000 boxes will never be completed, so NOTHING is ticked by default: silence
  means "fine", and the reviewer only ever touches an item that is wrong. Coverage is
  tracked one level up, with a "reviewed" mark per section.
* Three verdicts, not five. Wrong / Awkward / Unsure covers what a language reviewer
  actually reports, and each one opens a correction box, because "what it should say
  instead" is the only part of the answer that can be acted on.
* Every flaggable item carries a stable path id. That is what lets the returned file
  be applied back to the JSON without anyone re-deriving which word was meant.
* Progress lives in localStorage and survives closing the tab; the reviewer exports a
  file at the end, and can re-import it to carry on somewhere else.

Re-run this after applying a round of fixes to produce a fresh workbook.
"""
from __future__ import annotations

import argparse
import io
import json
import os
import sys
from datetime import date

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CONTENT = os.path.join(ROOT, "app", "src", "main", "assets", "content")

# Levels that carry taught content. B2/C1 exist in levels.json as a ladder the learner
# can see, but nothing is authored for them, so they get no review section.
LEVEL_ORDER = ["A0", "A1", "A2", "B1", "B2", "C1"]


def load(lang: str, *parts: str):
    path = os.path.join(CONTENT, lang, *parts)
    with io.open(path, encoding="utf-8") as fh:
        return json.load(fh)


def exists(lang: str, *parts: str) -> bool:
    return os.path.exists(os.path.join(CONTENT, lang, *parts))


# --------------------------------------------------------------------------- items


def word_item(pack_id: str, w: dict) -> dict:
    it = {"i": f"vocab/{pack_id}/{w['id']}", "t": "w", "hr": w.get("hr", ""), "en": w.get("en", "")}
    if w.get("pos"):
        it["p"] = w["pos"]
    if w.get("note"):
        it["n"] = w["note"]
    ex = w.get("example")
    if ex:
        it["eh"] = ex.get("target", "")
        it["eg"] = ex.get("gloss", "")
    return it


def question_item(path: str, q: dict) -> dict:
    it = {
        "i": path,
        "t": "q",
        "qt": q.get("type", "MCQ"),
        "pr": q.get("prompt", ""),
        "ex": q.get("explanation", ""),
    }
    if q.get("options"):
        it["o"] = q["options"]
    if q.get("answer"):
        it["a"] = q["answer"]
    if q.get("accepted"):
        it["ac"] = q["accepted"]
    if q.get("pairs"):
        it["pa"] = [[p.get("left", ""), p.get("right", "")] for p in q["pairs"]]
    if q.get("ordered"):
        it["or"] = q["ordered"]
    if q.get("audioText"):
        it["au"] = q["audioText"]
    if q.get("strictDiacritics"):
        it["sd"] = 1
    return it


def heading(text: str) -> dict:
    return {"t": "h", "v": text}


def text_item(path: str, label: str, value: str) -> dict:
    return {"i": path, "t": "t", "l": label, "v": value}


# ------------------------------------------------------------------------ sections


def vocab_sections(lang: str) -> dict:
    """Packs in _index order, which IS the order words reach the learner."""
    out: dict[str, list] = {}
    for fname in load(lang, "vocab", "_index.json"):
        for pack in load(lang, "vocab", fname)["packs"]:
            level = pack.get("level", "A1")
            items = [word_item(pack["id"], w) for w in pack["words"]]
            out.setdefault(level, []).append(
                {
                    "id": f"vocab.{pack['id']}",
                    "k": "vocab",
                    "ti": pack.get("title", pack["id"]),
                    "sub": f"{len(items)} words · flashcard deck · file {fname}",
                    "items": items,
                }
            )
    return out


def lesson_section(day: dict) -> dict:
    n = day["day"]
    items: list[dict] = []

    # The English framing gets ONE flag between the four fields. A reviewer auditing
    # Croatian should not have to pass judgement on four blurbs per day, 344 times,
    # but they must still be able to see and challenge them.
    framing = []
    if day.get("objective"):
        framing.append(("Objective", day["objective"]))
    if day.get("paretoFocus"):
        framing.append(("Why this matters", day["paretoFocus"]))
    if day.get("drills"):
        framing.append(("Drills", "\n".join("• " + d for d in day["drills"])))
    rb = day.get("reviewBlock") or {}
    if rb.get("items"):
        framing.append((f"Review block ({rb.get('minutes', 15)} min)", "\n".join("• " + x for x in rb["items"])))
    if framing:
        items.append({"i": f"day/{n}/framing", "t": "fr", "rows": framing})

    for ai, act in enumerate(day.get("activities", [])):
        kind = act.get("type", "LEARN")
        items.append(heading(f"{kind} · {act.get('title', '')}"))
        if act.get("intro"):
            items.append(text_item(f"day/{n}/act{ai}/intro", "Explanation", act["intro"]))
        for j, li in enumerate(act.get("items", [])):
            it = {"i": f"day/{n}/act{ai}/item{j}", "t": "l", "hr": li.get("hr", ""), "en": li.get("en", "")}
            if li.get("note"):
                it["n"] = li["note"]
            items.append(it)
        for j, ln in enumerate(act.get("lines", [])):
            items.append(
                {
                    "i": f"day/{n}/act{ai}/line{j}",
                    "t": "d",
                    "s": ln.get("speaker", ""),
                    "hr": ln.get("hr", ""),
                    "en": ln.get("en", ""),
                }
            )
        for j, q in enumerate(act.get("questions", [])):
            items.append(question_item(f"day/{n}/act{ai}/q{j}", q))

    return {
        "id": f"day.{n}",
        "k": "lesson",
        "n": n,
        "ti": f"Lesson {n}: {day.get('title', '')}",
        "sub": f"Week {day.get('week', '')} · {day.get('phase', '')}",
        "items": items,
    }


def grammar_sections(lang: str) -> dict:
    if not exists(lang, "grammar.json"):
        return {}
    out: dict[str, list] = {}
    for lvl in load(lang, "grammar.json")["levels"]:
        items: list[dict] = []
        if lvl.get("intro"):
            items.append(text_item(f"grammar/{lvl['levelId']}/intro", "Level intro", lvl["intro"]))
        for t in lvl["topics"]:
            items.append(
                {
                    "i": f"grammar/{lvl['levelId']}/{t['id']}",
                    "t": "g",
                    "ti": t.get("title", ""),
                    "su": t.get("summary", ""),
                    "tb": t.get("tables", []),
                    "ex": [[e.get("target", ""), e.get("gloss", "")] for e in t.get("examples", [])],
                }
            )
        out.setdefault(lvl["levelId"], []).append(
            {
                "id": f"grammar.{lvl['levelId']}",
                "k": "grammar",
                "ti": f"Grammar reference · {lvl['levelId']}",
                "sub": f"{len(lvl['topics'])} topics, with tables and examples",
                "items": items,
            }
        )
    return out


def quiz_sections(lang: str) -> dict:
    if not exists(lang, "quizzes.json"):
        return {}
    out: dict[str, list] = {}
    for qz in load(lang, "quizzes.json")["quizzes"]:
        items = [question_item(f"quiz/{qz['id']}/q{j}", q) for j, q in enumerate(qz["questions"])]
        out.setdefault(qz.get("levelId", "A1"), []).append(
            {
                "id": f"quiz.{qz['id']}",
                "k": "quiz",
                "ti": f"Level quiz · {qz.get('title', qz['id'])}",
                "sub": f"{len(items)} questions · gate at the end of the level",
                "items": items,
            }
        )
    return out


def exam_sections(lang: str) -> dict:
    if not exists(lang, "exams.json"):
        return {}
    out: dict[str, list] = {}
    for spec in load(lang, "exams.json"):
        items: list[dict] = []
        if spec.get("description"):
            items.append(text_item(f"exam/{spec['id']}/desc", "Description", spec["description"]))
        for sec in spec.get("sections", []):
            items.append(heading(f"{sec.get('kind', '')} · {sec.get('title', '')}"))
            if sec.get("instructions"):
                items.append(
                    text_item(f"exam/{spec['id']}/{sec['id']}/instr", "Instructions", sec["instructions"])
                )
            for j, p in enumerate(sec.get("passages", [])):
                items.append(
                    {
                        "i": f"exam/{spec['id']}/{sec['id']}/passage{j}",
                        "t": "p",
                        "ti": p.get("title", ""),
                        "tx": p.get("text", ""),
                        "ao": 1 if p.get("audioOnly") else 0,
                    }
                )
            for j, q in enumerate(sec.get("questions", [])):
                items.append(question_item(f"exam/{spec['id']}/{sec['id']}/q{j}", q))
            for j, pr in enumerate(sec.get("prompts", [])):
                items.append(
                    {
                        "i": f"exam/{spec['id']}/{sec['id']}/prompt{j}",
                        "t": "op",
                        "pr": pr.get("prompt", ""),
                        "ma": pr.get("modelAnswer", ""),
                        "ru": pr.get("rubric", []),
                    }
                )
        out.setdefault(spec.get("levelId", "B1"), []).append(
            {
                "id": f"exam.{spec['id']}",
                "k": "exam",
                "ti": f"Mock exam · {spec.get('title', spec['id'])}",
                "sub": spec.get("passRule", ""),
                "items": items,
            }
        )
    return out


def extra_sections(lang: str) -> list:
    """Cross-level material: it is not taught at one level, so it gets its own group."""
    out = []

    if exists(lang, "placement.json"):
        pl = load(lang, "placement.json")
        items = []
        if pl.get("intro"):
            items.append(text_item("placement/intro", "Intro", pl["intro"]))
        for j, q in enumerate(pl["questions"]):
            it = question_item(f"placement/q{j}", q)
            it["pr"] = f"[{q.get('level', '')}] {it['pr']}"
            items.append(it)
        out.append(
            {
                "id": "extra.placement",
                "k": "quiz",
                "ti": "Placement test",
                "sub": f"{len(pl['questions'])} questions · decides where a new learner starts",
                "items": items,
            }
        )

    if exists(lang, "cheatsheet.json"):
        ch = load(lang, "cheatsheet.json")
        items = []
        for si, s in enumerate(ch["sections"]):
            items.append(
                {
                    "i": f"cheatsheet/{si}",
                    "t": "c",
                    "ti": s.get("title", ""),
                    "bu": s.get("bullets", []),
                    "dg": s.get("diagram") or "",
                    "ex": [[e.get("target", ""), e.get("gloss", "")] for e in s.get("examples", [])],
                }
            )
        out.append(
            {
                "id": "extra.cheatsheet",
                "k": "grammar",
                "ti": f"Cheatsheet · {ch.get('title', '')}",
                "sub": f"{len(ch['sections'])} sections · the 5-minute review page",
                "items": items,
            }
        )

    if exists(lang, "feynman.json"):
        fe = load(lang, "feynman.json")
        items = []
        for c in fe["concepts"]:
            items.append(
                {
                    "i": f"feynman/{c['id']}",
                    "t": "f",
                    "ti": f"[{c.get('levelId', '')}] {c.get('title', '')}",
                    "se": c.get("simpleExplanation", ""),
                    "an": c.get("analogy", ""),
                    "rp": [[r.get("point", ""), r.get("reTeach", "")] for r in c.get("rubricPoints", [])],
                }
            )
        out.append(
            {
                "id": "extra.feynman",
                "k": "grammar",
                "ti": "Teach-back concepts",
                "sub": f"{len(fe['concepts'])} concepts the learner explains back in their own words",
                "items": items,
            }
        )

    if exists(lang, "resources.json"):
        rs = load(lang, "resources.json")
        items = [
            {
                "i": f"resource/{r['name']}",
                "t": "r",
                "n": r.get("name", ""),
                "ty": r.get("type", ""),
                "u": r.get("url") or "",
                "w": r.get("why", ""),
            }
            for r in rs["resources"]
        ]
        out.append(
            {
                "id": "extra.resources",
                "k": "other",
                "ti": "Recommended resources",
                "sub": f"{len(items)} books, courses and channels the app points learners at",
                "items": items,
            }
        )

    return out


# ---------------------------------------------------------------------------- build


def build_data(lang: str) -> dict:
    meta = load(lang, "meta.json")
    levels_raw = {l["id"]: l for l in load(lang, "levels.json")["levels"]}

    by_level: dict[str, list] = {}

    def merge(d: dict):
        for lvl, secs in d.items():
            by_level.setdefault(lvl, []).extend(secs)

    grammar = grammar_sections(lang)
    vocab = vocab_sections(lang)
    quizzes = quiz_sections(lang)
    exams = exam_sections(lang)

    # Order within a level follows how a teacher would want to read it: the rules
    # first, then the words those rules act on, then the lessons in plan order, then
    # whatever assesses the level.
    merge(grammar)
    merge(vocab)

    lessons: dict[str, list] = {}
    for fname in load(lang, "plan", "_index.json"):
        for day in load(lang, "plan", fname)["days"]:
            lessons.setdefault(day["level"], []).append((day["day"], lesson_section(day)))
    for lvl, pairs in lessons.items():
        pairs.sort(key=lambda p: p[0])
        by_level.setdefault(lvl, []).extend(s for _, s in pairs)

    merge(quizzes)
    merge(exams)

    levels = []
    for lid in LEVEL_ORDER:
        secs = by_level.get(lid)
        if not secs:
            continue
        raw = levels_raw.get(lid, {})
        levels.append(
            {
                "id": lid,
                "ti": raw.get("title", lid),
                "ms": raw.get("milestone", ""),
                "cd": raw.get("canDo", []),
                "secs": secs,
            }
        )

    extras = extra_sections(lang)
    if extras:
        levels.append({"id": "EXTRA", "ti": "Across the whole course", "ms": "", "cd": [], "secs": extras})

    flaggable = sum(1 for l in levels for s in l["secs"] for i in s["items"] if i.get("i"))
    return {
        "code": lang,
        "name": meta.get("name", lang),
        "native": meta.get("nativeName", ""),
        "flag": meta.get("flagEmoji", ""),
        "built": date.today().isoformat(),
        "total": flaggable,
        "levels": levels,
    }


def render(data: dict) -> str:
    payload = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
    payload = payload.replace("</", "<\\/")  # cannot close the host <script> early
    return TEMPLATE.replace("__TITLE__", f"{data['name']} course review").replace("__DATA__", payload)


TEMPLATE = r"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>__TITLE__</title>
<style>
:root{
  --bg:#f6f5f2; --card:#fff; --ink:#1a1a1a; --dim:#5f6368; --line:#dfdcd6;
  --hr:#0b4f8a; --ok:#0f7b3f; --okbg:#e6f4ec; --wrong:#b3261e; --wrongbg:#fdeceb;
  --awk:#a15c00; --awkbg:#fdf1e0; --uns:#5b4a9e; --unsbg:#efeafb; --accent:#0b4f8a;
}
*{box-sizing:border-box}
body{margin:0;font:16px/1.55 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;
  background:var(--bg);color:var(--ink)}
h1,h2,h3{line-height:1.25;margin:0}
a{color:var(--accent)}
button{font:inherit;cursor:pointer}

/* ---- top bar ---- */
header{position:sticky;top:0;z-index:20;background:var(--card);border-bottom:1px solid var(--line);
  padding:10px 16px;display:flex;gap:12px;align-items:center;flex-wrap:wrap}
header .brand{font-weight:700;font-size:17px;white-space:nowrap}
header .sp{flex:1}
.btn{border:1px solid var(--line);background:var(--card);border-radius:8px;padding:7px 12px;color:var(--ink)}
.btn:hover{border-color:var(--accent);color:var(--accent)}
.btn.primary{background:var(--accent);color:#fff;border-color:var(--accent)}
.btn.primary:hover{opacity:.9;color:#fff}
#q{border:1px solid var(--line);border-radius:8px;padding:7px 10px;min-width:190px}
.bar{height:6px;background:var(--line);border-radius:3px;overflow:hidden;width:150px}
.bar>i{display:block;height:100%;background:var(--ok);width:0}
.stat{font-size:13px;color:var(--dim);white-space:nowrap}
#saved{font-size:12px;color:var(--ok);opacity:0;transition:opacity .3s}

/* ---- layout ---- */
.wrap{display:flex;align-items:flex-start;gap:20px;max-width:1500px;margin:0 auto;padding:20px 16px 80px}
nav{position:sticky;top:64px;width:290px;flex:none;max-height:calc(100vh - 84px);overflow:auto;
  background:var(--card);border:1px solid var(--line);border-radius:12px;padding:12px}
main{flex:1;min-width:0}
@media(max-width:1000px){.wrap{flex-direction:column}nav{position:static;width:100%;max-height:none}}

nav .lvl{margin-bottom:14px}
nav .lvl>b{display:block;font-size:13px;letter-spacing:.06em;text-transform:uppercase;color:var(--dim);
  margin-bottom:6px}
nav a.sec{display:block;padding:5px 8px;border-radius:6px;text-decoration:none;color:var(--ink);font-size:14px}
nav a.sec:hover{background:var(--bg)}
nav a.sec.on{background:var(--accent);color:#fff}
nav a.sec.done::before{content:"✓ ";color:var(--ok);font-weight:700}
nav a.sec.on.done::before{color:#fff}
nav a.sec .fl{color:var(--wrong);font-weight:700}
.days{display:flex;flex-wrap:wrap;gap:4px;margin:4px 0 2px}
.days a{display:flex;align-items:center;justify-content:center;min-width:30px;height:26px;padding:0 5px;
  border:1px solid var(--line);border-radius:6px;font-size:12px;text-decoration:none;color:var(--ink);background:var(--card)}
.days a:hover{border-color:var(--accent)}
.days a.done{background:var(--okbg);border-color:var(--ok);color:var(--ok);font-weight:700}
.days a.flag{background:var(--wrongbg);border-color:var(--wrong);color:var(--wrong);font-weight:700}
.days a.on{outline:2px solid var(--accent)}

/* ---- sections ---- */
.sec-head{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:16px 18px;margin-bottom:14px}
.sec-head h2{font-size:20px}
.sec-head p{margin:4px 0 0;color:var(--dim);font-size:14px}
.sec-head .row{display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin-top:12px}
.chk{display:flex;align-items:center;gap:8px;font-size:14px;background:var(--bg);border:1px solid var(--line);
  border-radius:8px;padding:7px 12px}
.chk input{width:17px;height:17px}

.item{background:var(--card);border:1px solid var(--line);border-left:4px solid var(--line);
  border-radius:10px;padding:12px 14px;margin-bottom:9px}
.item.v-wrong{border-left-color:var(--wrong);background:var(--wrongbg)}
.item.v-awkward{border-left-color:var(--awk);background:var(--awkbg)}
.item.v-unsure{border-left-color:var(--uns);background:var(--unsbg)}
.item .hr{color:var(--hr);font-weight:600}
.item .en{color:var(--dim)}
.grid{display:grid;grid-template-columns:1fr 1fr;gap:4px 18px}
@media(max-width:700px){.grid{grid-template-columns:1fr}}
.kind{display:inline-block;font-size:11px;letter-spacing:.05em;text-transform:uppercase;color:var(--dim);
  border:1px solid var(--line);border-radius:4px;padding:1px 6px;margin-right:8px;vertical-align:2px}
.note{font-size:14px;color:var(--dim);margin-top:5px}
.ex{margin-top:7px;padding-left:11px;border-left:2px solid var(--line);font-size:15px}
.h{margin:22px 0 10px;font-size:13px;letter-spacing:.08em;text-transform:uppercase;color:var(--dim);
  border-bottom:1px solid var(--line);padding-bottom:5px}
pre{background:var(--bg);border:1px solid var(--line);border-radius:8px;padding:10px;overflow-x:auto;
  font-size:13.5px;margin:8px 0;white-space:pre}
.opt{padding:3px 9px;border:1px solid var(--line);border-radius:6px;margin:3px 5px 0 0;display:inline-block;font-size:15px}
.opt.right{background:var(--okbg);border-color:var(--ok);color:var(--ok);font-weight:700}
.ansbox{background:var(--okbg);border:1px solid var(--ok);border-radius:8px;padding:8px 11px;margin-top:8px;
  color:var(--ok);font-weight:600}
.ansbox small{display:block;font-weight:400;color:var(--dim);margin-top:3px}
.expl{font-size:14px;color:var(--dim);margin-top:7px;font-style:italic}

/* ---- verdict controls ---- */
.ctl{display:flex;gap:6px;align-items:center;margin-top:10px;flex-wrap:wrap}
.v{border:1px solid var(--line);background:var(--card);border-radius:999px;padding:3px 11px;font-size:13px;color:var(--dim)}
.v:hover{border-color:var(--ink);color:var(--ink)}
.v.on-wrong{background:var(--wrong);border-color:var(--wrong);color:#fff}
.v.on-awkward{background:var(--awk);border-color:var(--awk);color:#fff}
.v.on-unsure{background:var(--uns);border-color:var(--uns);color:#fff}
.ctl .id{font-size:11px;color:#9a9a9a;font-family:ui-monospace,Menlo,Consolas,monospace;margin-left:auto}
textarea{width:100%;margin-top:8px;border:1px solid var(--line);border-radius:8px;padding:9px;font:inherit;
  font-size:14.5px;resize:vertical;min-height:60px;background:var(--card)}
textarea:focus{outline:2px solid var(--accent);border-color:transparent}

/* ---- intro page ---- */
.doc{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:26px 30px;max-width:820px}
.doc h1{font-size:27px;margin-bottom:6px}
.doc h3{font-size:16px;margin:22px 0 7px}
.doc p,.doc li{font-size:15.5px}
.doc ul{padding-left:20px}
.doc .lead{font-size:17px;color:var(--dim)}
.doc table{border-collapse:collapse;width:100%;margin:10px 0;font-size:14.5px}
.doc th,.doc td{border:1px solid var(--line);padding:7px 10px;text-align:left;vertical-align:top}
.doc th{background:var(--bg)}
.callout{background:var(--bg);border-left:4px solid var(--accent);border-radius:0 8px 8px 0;padding:12px 16px;margin:14px 0}
.hrline{border:0;border-top:1px solid var(--line);margin:22px 0}
</style>
</head>
<body>
<header>
  <span class="brand" id="brand"></span>
  <input id="q" placeholder="Search Croatian or English…">
  <button class="btn" id="fFlag">Flagged only</button>
  <button class="btn" id="fTodo">Not yet reviewed</button>
  <span class="sp"></span>
  <span class="stat" id="counts"></span>
  <span class="bar"><i id="prog"></i></span>
  <span id="saved">saved</span>
  <button class="btn" id="imp">Import…</button>
  <button class="btn primary" id="exp">Download review</button>
</header>

<div class="wrap">
  <nav id="nav"></nav>
  <main id="main"></main>
</div>

<input type="file" id="file" accept=".json" hidden>
<script id="payload" type="application/json">__DATA__</script>
<script>
"use strict";
var D = JSON.parse(document.getElementById('payload').textContent);
var KEY = 'corlang-review-' + D.code + '-v1';

/* ---------- state ---------- */
var S = {reviewer:'', flags:{}, done:{}, levelNotes:{}};
try { var raw = localStorage.getItem(KEY); if (raw) S = Object.assign(S, JSON.parse(raw)); } catch(e){}
var saveTimer = null;
function save(){
  clearTimeout(saveTimer);
  saveTimer = setTimeout(function(){
    try {
      localStorage.setItem(KEY, JSON.stringify(S));
      var el = document.getElementById('saved');
      el.style.opacity = 1; setTimeout(function(){ el.style.opacity = 0; }, 900);
    } catch(e){ alert('Could not save progress in this browser. Use "Download review" often.'); }
  }, 250);
}

/* ---------- index ---------- */
var SECS = {}, ITEM_SEC = {}, SEARCH = [];
D.levels.forEach(function(L){
  L.secs.forEach(function(s){
    s.lvl = L.id; SECS[s.id] = s;
    s.items.forEach(function(it){
      if (!it.i) return;
      ITEM_SEC[it.i] = s.id;
      SEARCH.push({s:s.id, t:((it.hr||'')+' '+(it.en||'')+' '+(it.pr||'')+' '+(it.ti||'')+' '+(it.v||'')+' '+(it.a||'')).toLowerCase()});
    });
  });
});
function secFlags(id){
  var s = SECS[id], n = 0;
  if (!s) return 0;
  s.items.forEach(function(it){ if (it.i && S.flags[it.i]) n++; });
  return n;
}
function esc(t){ return String(t==null?'':t).replace(/[&<>"]/g, function(c){
  return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]; }); }
function nl(t){ return esc(t).replace(/\n/g,'<br>'); }

/* ---------- nav ---------- */
var current = null, filter = null;
function buildNav(){
  var h = ['<a class="sec" href="#intro" data-go="intro"><b>How to use this</b></a>'];
  D.levels.forEach(function(L){
    h.push('<div class="lvl"><b>'+esc(L.id)+' — '+esc(L.ti)+'</b>');
    var days = L.secs.filter(function(s){ return s.k === 'lesson'; });
    L.secs.filter(function(s){ return s.k !== 'lesson'; }).forEach(function(s){
      h.push(navLink(s));
    });
    if (days.length){
      h.push('<div class="days">');
      days.forEach(function(s){
        var cls = S.done[s.id] ? 'done' : (secFlags(s.id) ? 'flag' : '');
        if (current === s.id) cls += ' on';
        h.push('<a class="'+cls+'" href="#'+s.id+'" data-go="'+s.id+'" title="'+esc(s.ti)+'">'+s.n+'</a>');
      });
      h.push('</div>');
    }
    h.push('</div>');
  });
  document.getElementById('nav').innerHTML = h.join('');
}
function navLink(s){
  var f = secFlags(s.id), cls = 'sec' + (S.done[s.id] ? ' done' : '') + (current === s.id ? ' on' : '');
  return '<a class="'+cls+'" href="#'+s.id+'" data-go="'+s.id+'">'+esc(s.ti)+
         (f ? ' <span class="fl">('+f+')</span>' : '')+'</a>';
}

/* ---------- progress ---------- */
function refresh(){
  var total = 0, done = 0, flags = 0;
  D.levels.forEach(function(L){ L.secs.forEach(function(s){ total++; if (S.done[s.id]) done++; }); });
  for (var k in S.flags) flags++;
  document.getElementById('prog').style.width = (total ? done/total*100 : 0) + '%';
  document.getElementById('counts').textContent =
    done + ' / ' + total + ' sections reviewed · ' + flags + ' flagged';
  buildNav();
  save();
}

/* ---------- item rendering ---------- */
function verdictCtl(it){
  var f = S.flags[it.i] || {}, v = f.v || '';
  function b(k, label){
    return '<button class="v'+(v===k?' on-'+k:'')+'" data-v="'+k+'" data-i="'+esc(it.i)+'">'+label+'</button>';
  }
  var h = '<div class="ctl">' + b('wrong','✗ Wrong') + b('awkward','≈ Awkward') + b('unsure','? Unsure') +
          '<span class="id">'+esc(it.i)+'</span></div>';
  if (v) h += '<textarea data-n="'+esc(it.i)+'" placeholder="What should it say instead? Corrected spelling, better wording, why it is wrong…">'+esc(f.n||'')+'</textarea>';
  return h;
}
function q(it){
  var h = '<span class="kind">'+esc(it.qt)+(it.au?' · listening':'')+(it.sd?' · strict diacritics':'')+'</span>';
  h += '<div><b>'+nl(it.pr)+'</b></div>';
  if (it.au) h += '<div class="note">Spoken aloud, hidden from the learner: <span class="hr">'+esc(it.au)+'</span></div>';
  if (it.o && it.o.length){
    h += '<div style="margin-top:6px">';
    it.o.forEach(function(o){
      var right = (it.qt === 'REORDER') ? false : (o === it.a);
      h += '<span class="opt'+(right?' right':'')+'">'+esc(o)+(right?' ✓':'')+'</span>';
    });
    h += '</div>';
  }
  if (it.or && it.or.length) h += '<div class="ansbox">Correct order: '+esc(it.or.join(' '))+'</div>';
  else if (it.pa && it.pa.length){
    h += '<div class="ansbox">Correct pairs:<small>' +
         it.pa.map(function(p){ return esc(p[0])+' → '+esc(p[1]); }).join('<br>') + '</small></div>';
  } else if (it.a && !(it.o && it.o.length)){
    h += '<div class="ansbox">Answer: '+esc(it.a)+
         (it.ac && it.ac.length ? '<small>Also accepted: '+esc(it.ac.join(' · '))+'</small>' : '')+'</div>';
  } else if (it.a && it.o && it.o.length && it.o.indexOf(it.a) < 0){
    h += '<div class="ansbox">Answer: '+esc(it.a)+'</div>';
  }
  if (it.ex) h += '<div class="expl">Shown after answering: '+nl(it.ex)+'</div>';
  return h;
}
function render_item(it){
  if (it.t === 'h') return '<div class="h">'+esc(it.v)+'</div>';
  var body;
  switch(it.t){
    case 'w':
      body = '<div class="grid"><div class="hr">'+esc(it.hr)+(it.p?' <span class="kind">'+esc(it.p)+'</span>':'')+
             '</div><div class="en">'+esc(it.en)+'</div></div>';
      if (it.n) body += '<div class="note">'+nl(it.n)+'</div>';
      if (it.eh) body += '<div class="ex"><span class="hr">'+esc(it.eh)+'</span><br><span class="en">'+esc(it.eg)+'</span></div>';
      break;
    case 'l':
      body = '<div class="grid"><div class="hr">'+esc(it.hr)+'</div><div class="en">'+esc(it.en)+'</div></div>';
      if (it.n) body += '<div class="note">'+nl(it.n)+'</div>';
      break;
    case 'd':
      body = '<div class="grid"><div><b class="en">'+esc(it.s)+':</b> <span class="hr">'+esc(it.hr)+'</span></div>'+
             '<div class="en">'+esc(it.en)+'</div></div>';
      break;
    case 'q': body = q(it); break;
    case 't':
      body = '<span class="kind">'+esc(it.l)+'</span><div>'+nl(it.v)+'</div>';
      break;
    case 'fr':
      body = '<span class="kind">Lesson framing (English)</span>' +
        it.rows.map(function(r){ return '<div class="note"><b>'+esc(r[0])+':</b> '+nl(r[1])+'</div>'; }).join('');
      break;
    case 'g':
      body = '<h3>'+esc(it.ti)+'</h3><div class="note">'+nl(it.su)+'</div>';
      (it.tb||[]).forEach(function(t){ body += '<pre>'+esc(t)+'</pre>'; });
      (it.ex||[]).forEach(function(e){
        body += '<div class="ex"><span class="hr">'+esc(e[0])+'</span><br><span class="en">'+esc(e[1])+'</span></div>'; });
      break;
    case 'c':
      body = '<h3>'+esc(it.ti)+'</h3>';
      if (it.bu && it.bu.length) body += '<ul class="note">'+it.bu.map(function(b){ return '<li>'+nl(b)+'</li>'; }).join('')+'</ul>';
      if (it.dg) body += '<pre>'+esc(it.dg)+'</pre>';
      (it.ex||[]).forEach(function(e){
        body += '<div class="ex"><span class="hr">'+esc(e[0])+'</span><br><span class="en">'+esc(e[1])+'</span></div>'; });
      break;
    case 'f':
      body = '<h3>'+esc(it.ti)+'</h3><div class="note"><b>Explanation:</b> '+nl(it.se)+'</div>'+
             '<div class="note"><b>Analogy:</b> '+nl(it.an)+'</div>';
      if (it.rp && it.rp.length) body += '<div class="note"><b>Points the learner must cover:</b><ul>'+
        it.rp.map(function(r){ return '<li>'+esc(r[0])+'</li>'; }).join('')+'</ul></div>';
      break;
    case 'p':
      body = '<span class="kind">'+(it.ao?'listening passage (spoken, transcript hidden)':'reading passage')+'</span>'+
             (it.ti?'<h3>'+esc(it.ti)+'</h3>':'')+'<div class="hr" style="font-weight:400">'+nl(it.tx)+'</div>';
      break;
    case 'op':
      body = '<span class="kind">open task</span><div><b>'+nl(it.pr)+'</b></div>'+
             '<div class="ansbox">Model answer:<small>'+nl(it.ma)+'</small></div>';
      if (it.ru && it.ru.length) body += '<div class="expl">Marked on: '+esc(it.ru.join(' · '))+'</div>';
      break;
    case 'r':
      body = '<div><b>'+esc(it.n)+'</b> <span class="kind">'+esc(it.ty)+'</span></div>'+
             (it.u?'<div class="note">'+esc(it.u)+'</div>':'')+'<div class="note">'+nl(it.w)+'</div>';
      break;
    default: body = '<pre>'+esc(JSON.stringify(it))+'</pre>';
  }
  var v = (S.flags[it.i]||{}).v || '';
  return '<div class="item'+(v?' v-'+v:'')+'" data-item="'+esc(it.i)+'">'+body+verdictCtl(it)+'</div>';
}

/* ---------- section view ---------- */
function show(id){
  current = id;
  var main = document.getElementById('main');
  if (id === 'intro'){ main.innerHTML = INTRO(); buildNav(); window.scrollTo(0,0); return; }
  var s = SECS[id];
  if (!s){ main.innerHTML = '<div class="doc">Section not found.</div>'; return; }
  var items = s.items;
  if (filter === 'flag') items = items.filter(function(it){ return it.i && S.flags[it.i]; });
  var h = '<div class="sec-head"><h2>'+esc(s.ti)+'</h2><p>'+esc(s.sub||'')+'</p><div class="row">'+
    '<label class="chk"><input type="checkbox" id="secdone"'+(S.done[s.id]?' checked':'')+'> '+
    'I have reviewed this whole section</label>'+
    '<button class="btn" id="prev">← Previous</button><button class="btn" id="next">Next →</button>'+
    '</div></div>';
  h += items.map(render_item).join('');
  if (!items.length) h += '<div class="doc">Nothing flagged in this section.</div>';
  main.innerHTML = h;
  window.scrollTo(0,0);
  buildNav();
}
function flatSections(){
  var out = [];
  D.levels.forEach(function(L){ L.secs.forEach(function(s){ out.push(s.id); }); });
  return out;
}
function step(d){
  var all = flatSections(), i = all.indexOf(current);
  if (i < 0) i = 0;
  var j = Math.min(all.length-1, Math.max(0, i+d));
  location.hash = all[j];
}

/* ---------- events ---------- */
document.addEventListener('click', function(e){
  var go = e.target.closest('[data-go]');
  if (go){ e.preventDefault(); location.hash = go.getAttribute('data-go'); return; }
  var v = e.target.closest('[data-v]');
  if (v){
    var id = v.getAttribute('data-i'), k = v.getAttribute('data-v');
    var cur = S.flags[id];
    if (cur && cur.v === k){
      // Clicking the active verdict clears the flag. If a correction has been typed, that is
      // real work about to be thrown away by a mis-click, so it gets a confirm.
      if (cur.n && cur.n.trim() && !confirm('Remove this flag and delete the correction you wrote?')) return;
      delete S.flags[id];
    }
    else { S.flags[id] = {v:k, n:(cur&&cur.n)||''}; }
    var card = v.closest('.item');
    card.outerHTML = render_item(SECS[ITEM_SEC[id]].items.filter(function(x){ return x.i === id; })[0]);
    refresh();
    return;
  }
  if (e.target.id === 'next') step(1);
  if (e.target.id === 'prev') step(-1);
});
document.addEventListener('input', function(e){
  if (e.target.id === 'secdone'){ /* handled on change */ }
  var n = e.target.getAttribute && e.target.getAttribute('data-n');
  if (n && S.flags[n]){ S.flags[n].n = e.target.value; save(); }
  if (e.target.id === 'reviewer'){ S.reviewer = e.target.value; save(); }
  var ln = e.target.getAttribute && e.target.getAttribute('data-lvl');
  if (ln){ S.levelNotes[ln] = e.target.value; save(); }
});
document.addEventListener('change', function(e){
  if (e.target.id === 'secdone'){
    if (e.target.checked) S.done[current] = 1; else delete S.done[current];
    refresh();
  }
});
window.addEventListener('hashchange', function(){ show(location.hash.slice(1) || 'intro'); });

document.getElementById('fFlag').addEventListener('click', function(){
  filter = (filter === 'flag') ? null : 'flag';
  this.classList.toggle('primary', filter === 'flag');
  show(current);
});
document.getElementById('fTodo').addEventListener('click', function(){
  var all = flatSections().filter(function(id){ return !S.done[id]; });
  if (!all.length){ alert('Every section is marked reviewed. Thank you!'); return; }
  location.hash = all[0];
});
document.getElementById('q').addEventListener('keydown', function(e){
  if (e.key !== 'Enter') return;
  var t = this.value.trim().toLowerCase();
  if (!t) return;
  var hit = SEARCH.filter(function(x){ return x.t.indexOf(t) >= 0; });
  if (!hit.length){ alert('No match for "' + t + '".'); return; }
  var ids = [];
  hit.forEach(function(x){ if (ids.indexOf(x.s) < 0) ids.push(x.s); });
  location.hash = ids[0];
  if (ids.length > 1) setTimeout(function(){
    alert('Found in ' + ids.length + ' sections. Showing the first: ' + SECS[ids[0]].ti);
  }, 60);
});

/* ---------- export / import ---------- */
document.getElementById('exp').addEventListener('click', function(){
  var flags = [];
  for (var id in S.flags){
    var sec = SECS[ITEM_SEC[id]];
    var it = sec ? sec.items.filter(function(x){ return x.i === id; })[0] : null;
    flags.push({
      id: id,
      section: sec ? sec.id : '',
      level: sec ? sec.lvl : '',
      verdict: S.flags[id].v,
      correction: S.flags[id].n || '',
      hr: it ? (it.hr || it.pr || it.ti || it.a || '') : '',
      en: it ? (it.en || it.v || '') : ''
    });
  }
  flags.sort(function(a,b){ return a.id < b.id ? -1 : 1; });
  var out = {
    course: D.code, courseName: D.name, workbookBuilt: D.built,
    exported: new Date().toISOString(), reviewer: S.reviewer || '',
    sectionsReviewed: Object.keys(S.done),
    levelNotes: S.levelNotes, flags: flags,
    summary: {
      itemsInCourse: D.total,
      sectionsReviewed: Object.keys(S.done).length,
      flagged: flags.length,
      wrong: flags.filter(function(f){ return f.verdict==='wrong'; }).length,
      awkward: flags.filter(function(f){ return f.verdict==='awkward'; }).length,
      unsure: flags.filter(function(f){ return f.verdict==='unsure'; }).length
    }
  };
  var name = D.code + '-review-' + (S.reviewer||'reviewer').replace(/[^a-z0-9]+/gi,'-').toLowerCase() +
             '-' + new Date().toISOString().slice(0,10) + '.json';
  var blob = new Blob([JSON.stringify(out, null, 1)], {type:'application/json'});
  var a = document.createElement('a');
  a.href = URL.createObjectURL(blob); a.download = name; a.click();
  setTimeout(function(){ URL.revokeObjectURL(a.href); }, 2000);
});
document.getElementById('imp').addEventListener('click', function(){ document.getElementById('file').click(); });
document.getElementById('file').addEventListener('change', function(e){
  var f = e.target.files[0]; if (!f) return;
  var r = new FileReader();
  r.onload = function(){
    try {
      var d = JSON.parse(r.result);
      S.reviewer = d.reviewer || S.reviewer;
      S.levelNotes = d.levelNotes || {};
      S.done = {}; (d.sectionsReviewed||[]).forEach(function(x){ S.done[x] = 1; });
      S.flags = {}; (d.flags||[]).forEach(function(x){ S.flags[x.id] = {v:x.verdict, n:x.correction||''}; });
      refresh(); show(current || 'intro');
      alert('Loaded ' + (d.flags||[]).length + ' flags and ' + (d.sectionsReviewed||[]).length + ' reviewed sections.');
    } catch(err){ alert('That file could not be read: ' + err.message); }
  };
  r.readAsText(f);
});

/* ---------- intro ---------- */
function INTRO(){
  var perLevel = D.levels.map(function(L){
    var words = 0, qs = 0, lessons = 0, other = 0;
    L.secs.forEach(function(s){
      if (s.k === 'lesson') lessons++;
      s.items.forEach(function(it){
        if (it.t === 'w') words++;
        else if (it.t === 'q') qs++;
        else if (it.i) other++;
      });
    });
    return '<tr><td><b>'+esc(L.id)+'</b><br><span class="en">'+esc(L.ti)+'</span></td><td>'+lessons+
           '</td><td>'+words+'</td><td>'+qs+'</td><td>'+other+'</td></tr>';
  }).join('');
  return '<div class="doc">'+
  '<h1>'+esc(D.flag)+' '+esc(D.name)+' course — native-speaker review</h1>'+
  '<p class="lead">Everything the app teaches, in the order it teaches it, with every answer key shown. '+
  'Your job is to find what is wrong.</p>'+
  '<p style="font-size:15px" class="en"><i>Hvala što pregledavate ovaj tečaj. Ovdje je sav sadržaj naše aplikacije za '+
  'učenje hrvatskoga. Označite sve što je pogrešno ili neprirodno.</i></p>'+
  '<div class="callout"><b>Your name</b> (goes into the file you send back)<br>'+
  '<input id="reviewer" style="margin-top:6px;padding:8px 10px;border:1px solid var(--line);border-radius:8px;'+
  'min-width:260px;font:inherit" value="'+esc(S.reviewer)+'" placeholder="e.g. Ana Horvat"></div>'+

  '<h3>The one rule</h3>'+
  '<p><b>Do not mark what is correct.</b> There are '+D.total.toLocaleString()+' items here, and ticking them all '+
  'would take weeks. Leave anything that is fine completely untouched. Only flag what needs to change. '+
  'When you finish a section, tick <i>“I have reviewed this whole section”</i> at the top of it — that is how we '+
  'know the difference between “checked and fine” and “not looked at yet”.</p>'+

  '<h3>The three flags</h3>'+
  '<table><tr><th>Flag</th><th>Use it when</th></tr>'+
  '<tr><td><b>✗ Wrong</b></td><td>It is incorrect and would teach a learner something false. Bad spelling or '+
  'missing diacritic, wrong gender or aspect, wrong translation, a grammar rule stated incorrectly, an answer key '+
  'that is not actually the right answer, a “wrong” option that is in fact also correct.</td></tr>'+
  '<tr><td><b>≈ Awkward</b></td><td>Not wrong, but no Croatian speaker would say it that way. Unnatural word order, '+
  'a stilted or translated-sounding sentence, the wrong register (too formal, too slangy), a regionalism or a '+
  'Serbianism where standard Croatian is meant, a word that is technically right but not the one people use.</td></tr>'+
  '<tr><td><b>? Unsure</b></td><td>Something looks off but you would want to check it, or it depends on context. '+
  'Also for “this belongs at a different level”, or “this is fine but something important is missing here”.</td></tr>'+
  '</table>'+
  '<p>Whichever you pick, a box opens underneath. <b>Please write what it should say instead.</b> A correction we can '+
  'paste in is worth ten flags without one.</p>'+

  '<h3>What to look for</h3>'+
  '<ul>'+
  '<li><b>Words.</b> Spelling and diacritics (č ć dž đ š ž), the English meaning, the part of speech and gender or '+
  'aspect shown next to it, and the note. Then the example sentence: is it grammatical, natural, and does its '+
  'English match?</li>'+
  '<li><b>Grammar explanations and tables.</b> Endings in the tables are the highest-risk thing in the whole course — '+
  'one wrong cell teaches a wrong case for months.</li>'+
  '<li><b>Questions.</b> The correct answer is marked in green. Check that it really is correct, that none of the '+
  'other options is <i>also</i> correct, that the question can only be read one way, and that the explanation '+
  'underneath is true.</li>'+
  '<li><b>Dialogues.</b> Would people really say this? Is ti/vi consistent and appropriate? Is it the register a '+
  'learner should copy?</li>'+
  '<li><b>Standard Croatian.</b> This matters to us more than anything else. Anything that reads as Serbian, Bosnian, '+
  'or a strong regional form should be flagged, even when it is perfectly understandable.</li>'+
  '</ul>'+

  '<h3>Where to start</h3>'+
  '<p>The sidebar follows the course: each level holds its grammar, then its vocabulary packs, then its lessons in '+
  'order, then its quiz. If you have limited time, this is the order that protects a learner best:</p>'+
  '<ol><li><b>Grammar references</b> — smallest, and a wrong rule poisons everything built on it.</li>'+
  '<li><b>Vocabulary packs</b> — every word here goes into long-term memory drilling, so a wrong one is learned '+
  'permanently.</li>'+
  '<li><b>Lessons A0 → B1</b> in order.</li>'+
  '<li><b>Quizzes, placement test and mock exams</b>.</li></ol>'+

  '<h3>How much there is</h3>'+
  '<table><tr><th>Level</th><th>Lessons</th><th>Words</th><th>Questions</th><th>Other items</th></tr>'+perLevel+'</table>'+

  '<h3>Saving and sending it back</h3>'+
  '<p>Your work saves in this browser automatically as you go — you can close the tab and come back. Do keep the '+
  'same browser and the same file. When you are done, or at the end of each session, press '+
  '<b>Download review</b> in the top right; that gives you one small <code>.json</code> file to send back. '+
  '<b>Import…</b> loads such a file again, so you can carry on from another computer.</p>'+
  '<div class="callout">Nothing is uploaded anywhere. This page works offline and keeps everything on your machine '+
  'until you send us the file yourself.</div>'+

  '<hr class="hrline">'+
  '<h3>Overall notes, per level</h3>'+
  '<p>For anything that is not about one item: gaps, progression, a topic taught too early or too late, a whole '+
  'lesson that misses the point.</p>'+
  D.levels.map(function(L){
    return '<div style="margin-top:12px"><b>'+esc(L.id)+' — '+esc(L.ti)+'</b>'+
      (L.ms ? '<div class="note">Milestone: '+esc(L.ms)+'</div>' : '')+
      '<textarea data-lvl="'+esc(L.id)+'" placeholder="Anything about '+esc(L.id)+' as a whole…">'+
      esc(S.levelNotes[L.id]||'')+'</textarea></div>';
  }).join('')+
  '</div>';
}

/* ---------- boot ---------- */
document.getElementById('brand').textContent = D.flag + ' ' + D.name + ' review · built ' + D.built;
show(location.hash.slice(1) || 'intro');
refresh();
</script>
</body>
</html>
"""


def main() -> int:
    ap = argparse.ArgumentParser(description="Build a native-speaker review workbook for a course.")
    ap.add_argument("lang", help="language code under assets/content, e.g. hr")
    ap.add_argument("--out", default=None, help="output .html path")
    args = ap.parse_args()

    if not os.path.isdir(os.path.join(CONTENT, args.lang)):
        print(f"No content folder for '{args.lang}'", file=sys.stderr)
        return 1

    data = build_data(args.lang)
    out = args.out or os.path.join(ROOT, "docs", "review", f"{args.lang}-review-workbook.html")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    html = render(data)
    with io.open(out, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(html)

    print(f"{out}  ({len(html)/1024/1024:.1f} MB)")
    print(f"  {data['total']:,} flaggable items across {sum(len(l['secs']) for l in data['levels'])} sections")
    for l in data["levels"]:
        n = sum(1 for s in l["secs"] for i in s["items"] if i.get("i"))
        print(f"  {l['id']:<6} {len(l['secs']):>4} sections  {n:>6,} items")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
