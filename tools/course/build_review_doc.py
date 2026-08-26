#!/usr/bin/env python3
"""
Build the native-speaker review workbook for one course.

    python tools/course/build_review_doc.py hr
    python tools/course/build_review_doc.py hr --out docs/review/croatian.html

Replaced docs/review/generate_review_docs.py (Aug 2026, deleted), which produced a
static printable page covering vocabulary, grammar, cheatsheet, quizzes and dialogues
but neither the lesson learn-items nor the ~3,200 exercise questions, and had no way
for a reviewer to record a verdict that could be read back in.

Build a workbook when a reviewer is lined up, not before: it is a snapshot of content
that keeps moving, and a stale one in the repo eventually gets handed to somebody.

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


def word_item(pack_id: str, pack_title: str, w: dict) -> dict:
    # The id stays keyed on the pack even though the word is now SHOWN under a lesson: it is the
    # path back into vocab/*.json, and where a reviewer happened to read it changes nothing.
    it = {
        "i": f"vocab/{pack_id}/{w['id']}",
        "t": "w",
        "pk": pack_title,
        "hr": w.get("hr", ""),
        "en": w.get("en", ""),
    }
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


PER_LESSON = 10  # Fsrs.NEW_WORDS_PER_DAY — the deck is sized to the plan at this rate


def deck_order(lang: str) -> list:
    """
    The deck in introduction order, mirroring `data/DeckOrder.kt` exactly.

    A pack with `fromDay = F` cannot appear before slot `(F - 1) * PER_LESSON`, the first slot
    lesson F draws from. The walk is in AUTHORED order and a gate only ever defers: it is a
    floor, not a summons, so nothing gated appears early and nothing else is dragged forward.

    If this ever disagrees with the Kotlin, the workbook shows a word under the wrong lesson,
    so the two must be changed together.
    """
    authored = []
    for fname in load(lang, "vocab", "_index.json"):
        for pack in load(lang, "vocab", fname)["packs"]:
            title = pack.get("title", pack["id"])
            for w in pack["words"]:
                gate = w.get("fromDay") or pack.get("fromDay", 0)
                authored.append((w, pack["id"], title, gate))

    def slot(gate: int) -> int:
        return (gate - 1) * PER_LESSON if gate > 0 else 0

    out: list = []
    waiting: list = []

    def release():
        while True:
            i = next((k for k, e in enumerate(waiting) if slot(e[3]) <= len(out)), None)
            if i is None:
                return
            out.append(waiting.pop(i))

    for entry in authored:
        if slot(entry[3]) <= len(out):
            out.append(entry)
            release()
        else:
            waiting.append(entry)
    release()
    out.extend(waiting)  # deck ran out before a gate opened: early beats never seen
    return out


def words_by_lesson(lang: str) -> dict:
    """Deck slots [(n-1)*PER, n*PER) are the words lesson n introduces."""
    deck = deck_order(lang)
    by_day: dict[int, list] = {}
    for idx, (w, pack_id, pack_title, _gate) in enumerate(deck):
        by_day.setdefault(idx // PER_LESSON + 1, []).append(word_item(pack_id, pack_title, w))
    return by_day


def lesson_section(day: dict, words: list) -> dict:
    """
    One lesson, in the order the app plays it.

    Mirrors `buildSessionSteps` in ui/screens/SessionPlayer.kt, which is the only thing that
    decides what a learner actually meets:

        intro (title + objective + "Why this matters")
        1 Recall  — the new words this lesson introduces
        2 Input   — every LEARN activity
        3 Practice— every EXERCISE activity
        4 Output  — every DIALOGUE activity
        5 Wrap-up — recall of today's phrases, built from the LEARN items above

    Note the phase sort: the app groups ALL LEARN before ALL EXERCISE before ALL DIALOGUE,
    regardless of the order they sit in the JSON. Showing them in authored order would have the
    reviewer checking a sequence no learner ever sees.

    `drills` and `reviewBlock` are NOT here. buildSessionSteps only falls back to them when a day
    has no activities at all, and every day in every shipped course has activities, so they
    never reach a learner.
    """
    n = day["day"]
    items: list[dict] = []

    # The intro step, exactly as the app composes it.
    intro = day.get("objective", "")
    if day.get("paretoFocus"):
        intro += chr(10) + chr(10) + "Why this matters: " + day["paretoFocus"]
    if intro.strip():
        items.append(text_item(f"day/{n}/intro", "Lesson intro (shown first)", intro))

    if words:
        items.append(heading(f"1 · RECALL — the {len(words)} new words this lesson introduces"))
        items.extend(words)

    # Phase order, stable within a phase, matching the Kotlin sort.
    phases = [
        ("LEARN", "2 · INPUT"),
        ("EXERCISE", "3 · PRACTICE"),
        ("DIALOGUE", "4 · OUTPUT"),
    ]
    acts = list(enumerate(day.get("activities", [])))
    for kind, label in phases:
        for ai, act in [(i, a) for i, a in acts if a.get("type") == kind]:
            items.append(heading(f"{label} · {act.get('title', '')}"))
            if act.get("intro"):
                items.append(text_item(f"day/{n}/act{ai}/intro", "Explanation", act["intro"]))
            for j, li in enumerate(act.get("items", [])):
                it = {"i": f"day/{n}/act{ai}/item{j}", "t": "l", "hr": li.get("hr", ""), "en": li.get("en", "")}
                if li.get("note"):
                    it["n"] = li["note"]
                items.append(it)
            for j, q in enumerate(act.get("questions", [])):
                items.append(question_item(f"day/{n}/act{ai}/q{j}", q))
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

    n_q = sum(len(a.get("questions", [])) for a in day.get("activities", []))
    return {
        "id": f"day.{n}",
        "k": "lesson",
        "n": n,
        "ti": f"Lesson {n}: {day.get('title', '')}",
        "sub": f"Week {day.get('week', '')} · {len(words)} new words · {n_q} questions",
        "items": items,
    }


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


def placement_section(lang: str) -> list:
    """
    The placement test: the only thing a learner meets BEFORE lesson 1, so it goes first.
    """
    if not exists(lang, "placement.json"):
        return []
    pl = load(lang, "placement.json")
    items = []
    if pl.get("intro"):
        items.append(text_item("placement/intro", "Intro", pl["intro"]))
    for j, q in enumerate(pl["questions"]):
        it = question_item(f"placement/q{j}", q)
        it["pr"] = f"[{q.get('level', '')}] {it['pr']}"
        items.append(it)
    return [
        {
            "id": "placement",
            "k": "quiz",
            "ti": "Placement test",
            "sub": f"{len(pl['questions'])} questions · offered at sign-up, before Lesson 1, "
                   f"to decide where a learner starts",
            "items": items,
        }
    ]


# ---------------------------------------------------------------------------- build


def build_data(lang: str) -> dict:
    meta = load(lang, "meta.json")
    levels_raw = {l["id"]: l for l in load(lang, "levels.json")["levels"]}

    by_level: dict[str, list] = {}

    # Levels hold LESSONS and nothing else. A lesson is the unit a teacher actually audits:
    # its explanations, its dialogue, its exercises and the ten words it introduces, read in
    # one sitting, in the order a learner meets them. Splitting the words back out into
    # thematic packs meant reviewing a word in one place and the lesson that teaches it in
    # another, hundreds of screens apart.
    per_day_words = words_by_lesson(lang)
    lessons: dict[str, list] = {}
    for fname in load(lang, "plan", "_index.json"):
        for day in load(lang, "plan", fname)["days"]:
            lessons.setdefault(day["level"], []).append(
                (day["day"], lesson_section(day, per_day_words.get(day["day"], [])))
            )
    for lvl, pairs in lessons.items():
        pairs.sort(key=lambda p: p[0])
        by_level[lvl] = [s for _, s in pairs]

    # A level's quiz and then its mock exam are what the app serves AFTER its last lesson: the
    # journey puts both at the end of the level, and nobody starts a level by being tested on it.
    # The exam goes last because it is the final thing a level asks for.
    #
    # The grammar syllabus is NOT here. It is still in the app, as reference material behind a
    # Profile button, but it is not something the course serves in sequence, and this workbook
    # follows the sequence.
    quizzes = quiz_sections(lang)
    exams = exam_sections(lang)
    for lvl in list(by_level):
        by_level[lvl].extend(quizzes.get(lvl, []))
        by_level[lvl].extend(exams.get(lvl, []))

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

    pl = placement_section(lang)
    if pl:
        levels.insert(0, {"id": "START", "ti": "Before Lesson 1", "ms": "", "cd": [], "secs": pl})

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
/* Light is the default because this is a reading document and most people read long text in
   it. Dark is a real second palette, not an inversion: the flag colours are re-pitched so a
   flagged card still reads as flagged against a dark card, and the Croatian blue lifts to
   something legible on ink rather than staying a navy that disappears. */
:root{
  --bg:#f6f5f2; --card:#fff; --ink:#1a1a1a; --dim:#5f6368; --line:#dfdcd6;
  --hr:#0b4f8a; --ok:#0f7b3f; --okbg:#e6f4ec; --wrong:#b3261e; --wrongbg:#fdeceb;
  --awk:#a15c00; --awkbg:#fdf1e0; --uns:#5b4a9e; --unsbg:#efeafb; --accent:#0b4f8a;
  --shadow:rgba(0,0,0,.06);
}
@media (prefers-color-scheme: dark){
  :root:not([data-theme="light"]){
    --bg:#14161a; --card:#1c1f24; --ink:#e8e6e3; --dim:#9aa0a6; --line:#31363d;
    --hr:#7fb2e4; --ok:#5cc98c; --okbg:#16301f; --wrong:#ef8f88; --wrongbg:#3a1c1a;
    --awk:#e0a850; --awkbg:#33260f; --uns:#b0a2ee; --unsbg:#241f38; --accent:#7fb2e4;
    --shadow:rgba(0,0,0,.4);
  }
}
:root[data-theme="dark"]{
  --bg:#14161a; --card:#1c1f24; --ink:#e8e6e3; --dim:#9aa0a6; --line:#31363d;
  --hr:#7fb2e4; --ok:#5cc98c; --okbg:#16301f; --wrong:#ef8f88; --wrongbg:#3a1c1a;
  --awk:#e0a850; --awkbg:#33260f; --uns:#b0a2ee; --unsbg:#241f38; --accent:#7fb2e4;
  --shadow:rgba(0,0,0,.4);
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
.v.on-wrong,.v.on-awkward,.v.on-unsure{color:#14161a}

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
.pk{font-size:11.5px;color:var(--dim);opacity:.75;white-space:nowrap}
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
.ctl .id{font-size:11px;color:var(--dim);opacity:.7;font-family:ui-monospace,Menlo,Consolas,monospace;margin-left:auto}
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
.foot{display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin:26px 0 10px;padding:16px;
  background:var(--card);border:1px solid var(--line);border-radius:12px}
.foot .sp{flex:1}
.foot .btn{padding:10px 18px}
.doneChip{font-size:12px;letter-spacing:.04em;text-transform:uppercase;color:var(--ok);
  background:var(--okbg);border:1px solid var(--ok);border-radius:999px;padding:2px 10px;
  vertical-align:middle;margin-left:8px;font-weight:700}
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
  <button class="btn" id="theme" title="Switch between light and dark">Dark</button>
  <button class="btn primary" id="exp">Save my work</button>
</header>

<div class="wrap">
  <nav id="nav"></nav>
  <main id="main"></main>
</div>

<script id="payload" type="application/json">__DATA__</script>
<script>
"use strict";
var D = JSON.parse(document.getElementById('payload').textContent);
var KEY = 'corlang-review-' + D.code + '-v1';

/*
 * Server sync, when the page is served with a ?k=<token> link.
 *
 * The SAME file works both ways: opened from disk, or from any URL without the token, none of
 * this engages and the workbook behaves exactly as the local one does. That matters because the
 * fallback has to be a file someone can still open if the site is down mid-review.
 *
 * localStorage stays the primary write on every keystroke; the server is a debounced mirror. A
 * dropped connection therefore costs nothing, and the reviewer's own browser is always the
 * fastest source of truth.
 */
var SYNC = (function(){
  try { return new URLSearchParams(location.search).get('k') || null; } catch(e){ return null; }
})();
var pushTimer = null, pushing = false, pushFailed = false;

function serverPush(){
  if (!SYNC) return;
  clearTimeout(pushTimer);
  pushTimer = setTimeout(function(){
    if (pushing) { serverPush(); return; }
    pushing = true;
    fetch('/api/review?k=' + encodeURIComponent(SYNC), {
      method: 'PUT',
      headers: {'content-type': 'application/json'},
      body: JSON.stringify(S)
    }).then(function(r){
      pushing = false;
      pushFailed = !r.ok;
      syncBadge(r.ok ? 'saved to the server' : 'server refused the save');
    }).catch(function(){
      pushing = false; pushFailed = true;
      syncBadge('offline, saved in this browser');
    });
  }, 2500);
}
function syncBadge(text){
  var el = document.getElementById('saved');
  el.textContent = text;
  el.style.color = pushFailed ? 'var(--awk)' : 'var(--ok)';
  el.style.opacity = 1;
  setTimeout(function(){ el.style.opacity = 0; }, 1600);
}

/* ---------- state ---------- */
var S = {reviewer:'', flags:{}, done:{}, levelNotes:{}};

// Does this browser actually give a page opened from disk a working localStorage? Some do not
// (private windows, Safari, hardened settings), and the failure is silent. Probing ONCE at
// startup is the difference between a reviewer learning about it now and learning about it
// after an evening of work.
var CAN_STORE = (function(){
  try {
    localStorage.setItem(KEY + '-probe', '1');
    var ok = localStorage.getItem(KEY + '-probe') === '1';
    localStorage.removeItem(KEY + '-probe');
    return ok;
  } catch(e){ return false; }
})();
if (CAN_STORE){
  try { var raw = localStorage.getItem(KEY); if (raw) S = Object.assign(S, JSON.parse(raw)); } catch(e){}
}

var dirty = false;  // changed since the last file export
var saveTimer = null;
function save(){
  dirty = true;
  if (!CAN_STORE){ warnNoStore(); serverPush(); return; }
  clearTimeout(saveTimer);
  saveTimer = setTimeout(function(){
    try {
      localStorage.setItem(KEY, JSON.stringify(S));
      dirty = false;
      serverPush();
      var el = document.getElementById('saved');
      el.style.opacity = 1; setTimeout(function(){ el.style.opacity = 0; }, 900);
    } catch(e){ CAN_STORE = false; warnNoStore(); }
  }, 250);
}

// A banner, not an alert: an alert is dismissed once and forgotten, and this has to stay true
// for the rest of the session.
function warnNoStore(){
  if (document.getElementById('nostore')) return;
  var b = document.createElement('div');
  b.id = 'nostore';
  b.style.cssText = 'position:sticky;top:0;z-index:30;background:#b3261e;color:#fff;padding:10px 16px;' +
    'font-size:14.5px;line-height:1.4';
  b.innerHTML = '<b>This browser is not saving your progress automatically.</b> Your work is only ' +
    'in this tab, and closing it will lose it. Press <b>Save my work</b> before you close, and send ' +
    'us that file. (Usually a private/incognito window — an ordinary window normally saves fine.)';
  document.body.insertBefore(b, document.body.firstChild);
}

// Only when storage is broken. If it works, closing the tab is genuinely safe and prompting
// on every close would just train the reviewer to click through warnings.
window.addEventListener('beforeunload', function(e){
  if (CAN_STORE || !dirty) return;
  var n = Object.keys(S.flags).length;
  if (!n) return;
  e.preventDefault(); e.returnValue = '';
  return '';
});

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
  var h = ['<a class="sec" href="#intro" data-go="intro"><b>Instructions</b></a>',
           '<a class="sec' + (current === 'notes' ? ' on' : '') +
           '" href="#notes" data-go="notes"><b>Overall notes</b>' + notesCount() + '</a>'];
  D.levels.forEach(function(L){
    h.push('<div class="lvl"><b>'+esc(L.id)+' — '+esc(L.ti)+'</b>');
    // In the SECTIONS' OWN ORDER. Listing the non-lessons first was quicker to write and put
    // the level quiz and the mock exam above Lesson 1 in the sidebar, which reads as "start
    // here" — the exact opposite of when the course serves them. Consecutive lessons collapse
    // into a grid of numbered squares; anything else prints as a row where it actually falls.
    var run = [];
    function flushDays(){
      if (!run.length) return;
      h.push('<div class="days">');
      run.forEach(function(s){
        var cls = S.done[s.id] ? 'done' : (secFlags(s.id) ? 'flag' : '');
        if (current === s.id) cls += ' on';
        h.push('<a class="'+cls+'" href="#'+s.id+'" data-go="'+s.id+'" title="'+esc(s.ti)+'">'+s.n+'</a>');
      });
      h.push('</div>');
      run = [];
    }
    L.secs.forEach(function(s){
      if (s.k === 'lesson'){ run.push(s); return; }
      flushDays();
      h.push(navLink(s));
    });
    flushDays();
    h.push('</div>');
  });
  document.getElementById('nav').innerHTML = h.join('');
}
// A count beside the tab, so the reviewer can see at a glance which levels they have already
// written something about without opening it.
function notesCount(){
  var n = 0;
  for (var k in S.levelNotes) if ((S.levelNotes[k]||'').trim()) n++;
  return n ? ' <span class="fl" style="color:var(--ok)">('+n+')</span>' : '';
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
      // The deck pack is named because some defects are only visible across a SET -- one colour
      // glossed differently from the other nine, one month capitalised. The lesson shows ten
      // words at a time, so without this label a set is invisible.
      body = '<div class="grid"><div class="hr">'+esc(it.hr)+(it.p?' <span class="kind">'+esc(it.p)+'</span>':'')+
             '</div><div class="en">'+esc(it.en)+(it.pk?' <span class="pk">'+esc(it.pk)+'</span>':'')+'</div></div>';
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
  if (id === 'notes'){ main.innerHTML = NOTES(); buildNav(); window.scrollTo(0,0); return; }
  var s = SECS[id];
  if (!s){ main.innerHTML = '<div class="doc">Section not found.</div>'; return; }
  var items = s.items;
  if (filter === 'flag') items = items.filter(function(it){ return it.i && S.flags[it.i]; });
  var h = '<div class="sec-head"><h2>'+esc(s.ti)+(S.done[s.id]?' <span class="doneChip">Reviewed ✓</span>':'')+
    '</h2><p>'+esc(s.sub||'')+'</p></div>';
  h += items.map(render_item).join('');
  if (!items.length) h += '<div class="doc">Nothing flagged in this section.</div>';
  h += footer(s);
  main.innerHTML = h;
  window.scrollTo(0,0);
  buildNav();
}
/**
 * The controls live at the BOTTOM. Marking a section reviewed is something you do when you have
 * finished reading it, and a checkbox at the top asked for that verdict before the reviewer had
 * seen anything. The primary action also advances, because over 344 lessons "mark, then find
 * next" is two clicks repeated three hundred times.
 */
function footer(s){
  var all = flatSections(), i = all.indexOf(s.id);
  var last = i >= all.length - 1;
  var h = '<div class="foot">' +
    '<button class="btn" id="prev"'+(i <= 0 ? ' disabled' : '')+'>← Previous</button><span class="sp"></span>';
  if (S.done[s.id]){
    h += '<button class="btn" id="undone">Reviewed ✓ — undo</button>' +
         '<button class="btn primary" id="next"'+(last?' disabled':'')+'>Next →</button>';
  } else {
    h += '<button class="btn" id="next"'+(last?' disabled':'')+'>Skip for now →</button>' +
         '<button class="btn primary" id="donenext">✓ Mark reviewed'+(last?'':' and continue')+'</button>';
  }
  return h + '</div>';
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
  if (e.target.id === 'undone'){ delete S.done[current]; refresh(); show(current); }
  if (e.target.id === 'donenext'){
    S.done[current] = 1;
    var all = flatSections(), i = all.indexOf(current);
    refresh();
    if (i < all.length - 1) step(1); else show(current);
  }
});
document.addEventListener('input', function(e){
  if (e.target.id === 'secdone'){ /* handled on change */ }
  var n = e.target.getAttribute && e.target.getAttribute('data-n');
  if (n && S.flags[n]){ S.flags[n].n = e.target.value; save(); }
  if (e.target.id === 'reviewer'){ S.reviewer = e.target.value; save(); }
  var ln = e.target.getAttribute && e.target.getAttribute('data-lvl');
  if (ln){ S.levelNotes[ln] = e.target.value; save(); buildNav(); }
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

/* ---------- theme ---------- */
// Three states, like any well-behaved page: follow the system, or override it either way. The
// choice is remembered separately from the review data, so clearing one does not disturb the
// other, and a browser that refuses storage entirely still gets a working toggle for the session.
var THEME_KEY = 'corlang-review-theme';
function applyTheme(t){
  if (t === 'light' || t === 'dark') document.documentElement.setAttribute('data-theme', t);
  else document.documentElement.removeAttribute('data-theme');
  var dark = t === 'dark' || (t !== 'light' && matchMedia('(prefers-color-scheme: dark)').matches);
  document.getElementById('theme').textContent = dark ? 'Light' : 'Dark';
}
var theme = null;
try { theme = localStorage.getItem(THEME_KEY); } catch(e){}
applyTheme(theme);
document.getElementById('theme').addEventListener('click', function(){
  var dark = theme === 'dark' || (theme !== 'light' && matchMedia('(prefers-color-scheme: dark)').matches);
  theme = dark ? 'light' : 'dark';
  try { localStorage.setItem(THEME_KEY, theme); } catch(e){}
  applyTheme(theme);
});

/* ---------- export ---------- */
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
  dirty = false;
  setTimeout(function(){ URL.revokeObjectURL(a.href); }, 2000);
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

  '<h3>How it is organised</h3>'+
  '<p>By lesson, in the order a learner meets them. Each lesson is one self-contained job: read it as a learner '+
  'would and judge it as a whole. The words it introduces sit inside it rather than in a separate list, because '+
  'that is where the app introduces them.</p>'+
  '<p>Simply start at Lesson 1 and work down. The numbered squares in the sidebar turn green as you tick sections '+
  'off, so you can always find where you stopped, and <b>Not yet reviewed</b> in the toolbar jumps you there.</p>'+
  '<p>Nothing has been added and nothing left out: this is what the app serves, in the order it serves it. Each '+
  'lesson opens the way a learner sees it — the intro, then the ten new words, then the teaching, then the '+
  'exercises, then the dialogue. A level ends with its quiz and mock exam, and the grammar reference for that '+
  'level, because that is where the app puts them. The placement test comes first because it is offered before '+
  'Lesson 1.</p>'+

  '<h3>How much there is</h3>'+
  '<table><tr><th>Level</th><th>Lessons</th><th>Words</th><th>Questions</th><th>Other items</th></tr>'+perLevel+'</table>'+

  '<h3>Saving and sending it back</h3>'+
  '<p>Your work saves in this browser as you go, so you can close the tab and come back to it. That saving is '+
  'tied to <b>this browser on this computer</b>, and to this file staying where it is; if the browser refuses to '+
  'save at all, a red bar appears at the top of this page.</p>'+
  '<p>At the end of every session press <b>Save my work</b> in the top right. It gives you one small file — that '+
  'is what you send us, and it is your backup if anything happens to the browser.</p>'+
  '<div class="callout">Nothing is uploaded anywhere. This page works offline and keeps everything on your machine '+
  'until you send us the file yourself.</div>'+

  '<div style="margin-top:26px;text-align:center">'+
  '<button class="btn primary" data-go="'+esc(firstSection())+'" style="padding:11px 22px">Start at Lesson 1 →</button>'+
  '</div>'+
  '</div>';
}

/**
 * Overall notes, on their own page. They used to sit at the foot of the instructions, which
 * meant reaching them was: open the instructions, scroll past everything you have already read.
 * Anything not about one specific item goes here.
 */
function NOTES(){
  return '<div class="doc">'+
  '<h1>Overall notes</h1>'+
  '<p class="lead">For anything that is not about a single word or question: gaps, progression, '+
  'a topic taught too early or too late, a whole lesson that misses the point, a habit you keep '+
  'seeing across the course.</p>'+
  '<p>These are saved with everything else and come back to us in the same file. Write as much '+
  'or as little as you like, in English or in Croatian.</p>'+
  D.levels.map(function(L){
    return '<div style="margin-top:18px"><b>'+esc(L.id)+' — '+esc(L.ti)+'</b>'+
      (L.ms ? '<div class="note">Milestone: '+esc(L.ms)+'</div>' : '')+
      '<textarea data-lvl="'+esc(L.id)+'" style="min-height:110px" placeholder="Anything about '+
      esc(L.id)+' as a whole…">'+esc(S.levelNotes[L.id]||'')+'</textarea></div>';
  }).join('')+
  '<div style="margin-top:22px"><b>The course as a whole</b>'+
  '<textarea data-lvl="_course" style="min-height:130px" placeholder="Would you teach from this? '+
  'What is missing? What would you throw away?">'+esc(S.levelNotes['_course']||'')+'</textarea></div>'+
  '</div>';
}

/** The first real section, for the "Start at Lesson 1" button. */
function firstSection(){ var a = flatSections(); return a.length ? a[0] : 'intro'; }

/* ---------- boot ---------- */
document.getElementById('brand').textContent = D.flag + ' ' + D.name + ' review · built ' + D.built;
show(location.hash.slice(1) || 'intro');
refresh();

// With a token, whatever the server holds wins on open: it is the copy that survives this
// browser being cleared or swapped, and it is what got there from this reviewer anyway. Only
// adopted when it actually carries more work than the local copy, so a failed first save can
// never wipe an afternoon.
if (SYNC){
  fetch('/api/review?k=' + encodeURIComponent(SYNC))
    .then(function(r){ return r.ok ? r.json() : null; })
    .then(function(remote){
      if (!remote || !remote.flags) return;
      var mine = Object.keys(S.flags).length + Object.keys(S.done).length;
      var theirs = Object.keys(remote.flags).length + Object.keys(remote.done || {}).length;
      if (theirs <= mine) return;
      S = Object.assign(S, remote);
      refresh();
      show(current || 'intro');
    })
    .catch(function(){});
}
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
