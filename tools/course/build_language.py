# -*- coding: utf-8 -*-
"""Assemble a NEW language course from authored batches into a ready-to-move content folder.

merge_plan.py and merge_vocab.py both append to a language that already lives in
assets/content. A brand new course has nothing to append to, so this builds the plan/ and
vocab/ directories from scratch inside the scratchpad build directory, in the exact layout the
app and the content gates expect:

  <build>/plan/_index.json      ordered list of phase files
  <build>/plan/phaseN-<lvl>.json  {"title": ..., "days": [...]}  one file per level
  <build>/vocab/_index.json     ordered list of pack files
  <build>/vocab/NN-<slug>.json  {"packs": [...]}                one file per pack

Invariants it enforces, because the gates downstream do:
  - days numbered contiguously 1..N in the order the batches are given
  - week = ceil(day / 7)
  - each level is one contiguous block, in ladder order
  - vocab ids globally unique; a duplicate is DROPPED, keeping the earliest introduction,
    since deck order is SRS introduction order and the first meeting is the real one
  - no em or en dashes anywhere

Usage:
  python build_language.py <build-dir> --title "<plan title>" \
      --lessons a.json b.json ... --vocab v1.json v2.json ...
"""
import argparse
import io
import json
import math
import os
import re
import sys
import unicodedata

LADDER = ["A0", "A1", "A2", "B1", "B2", "C1"]
PHASE_FILE = {"A0": "phase0-a0.json", "A1": "phase1-a1.json", "A2": "phase2-a2.json",
              "B1": "phase3-b1.json", "B2": "phase4-b2.json", "C1": "phase5-c1.json"}
DASH = re.compile(r"[–—]")


def die(msg):
    print(f"ERROR: {msg}")
    sys.exit(1)


def read_json(path):
    return json.load(io.open(path, encoding="utf-8"))


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    text = json.dumps(obj, ensure_ascii=False, indent=1) + "\n"
    if DASH.search(text):
        die(f"{path}: em or en dash in assembled output")
    io.open(path, "w", encoding="utf-8", newline="\n").write(text)


def load_retired(path):
    """Titles to skip, one per line, # comments ignored. See de_retired.txt for the rationale."""
    if not path:
        return set()
    out = set()
    for line in io.open(path, encoding="utf-8"):
        line = line.split("#", 1)[0].strip()
        if line:
            out.add(line)
    return out


def build_plan(build_dir, title, lesson_files, retired=frozenset()):
    days = []
    for f in lesson_files:
        batch = read_json(f)
        if not isinstance(batch, list):
            die(f"{f}: expected a JSON array of day objects")
        days.extend(batch)

    if retired:
        before = len(days)
        titles = {d["title"] for d in days}
        # A retire list that no longer matches is a silent no-op, and a silent no-op here means
        # shipping a course with the wrong level balance. Fail loudly instead.
        missing = retired - titles
        if missing:
            die("retire list names lessons that do not exist: " + ", ".join(sorted(missing)))
        days = [d for d in days if d["title"] not in retired]
        print(f"  retired {before - len(days)} lessons named in the retire list")

    # Levels must form contiguous blocks in ladder order. Batches are given in course order,
    # so this validates the caller's ordering rather than silently resorting it: a plan whose
    # levels interleave is an authoring mistake worth failing loudly on.
    seen_order = []
    for d in days:
        if not seen_order or seen_order[-1] != d["level"]:
            seen_order.append(d["level"])
    if len(seen_order) != len(set(seen_order)):
        die(f"levels are not contiguous, saw the sequence {seen_order}")
    ranked = [LADDER.index(l) for l in seen_order]
    if ranked != sorted(ranked):
        die(f"levels out of ladder order: {seen_order}")

    # Every lesson's `resources` string must name a real entry in resources.json. Lesson batches
    # and resources.json are authored in parallel, so the names drift whenever a URL fails live
    # verification and its entry is renamed or dropped. German shipped that mismatch all the way
    # to the real Kotlin gates; catching it here costs nothing.
    res_path = os.path.join(build_dir, "resources.json")
    if os.path.exists(res_path):
        valid = {x["name"] for x in read_json(res_path)["resources"]}
        unknown = {r for d in days for r in d.get("resources", [])} - valid
        if unknown:
            die("lessons reference resources absent from resources.json: "
                + ", ".join(sorted(unknown)))

    for i, d in enumerate(days, start=1):
        d["day"] = i
        d["week"] = math.ceil(i / 7)

    index = []
    for level in seen_order:
        block = [d for d in days if d["level"] == level]
        name = PHASE_FILE[level]
        write_json(os.path.join(build_dir, "plan", name), {"title": title, "days": block})
        index.append(name)
        print(f"  plan/{name:<16} {len(block):>3} lessons  "
              f"days {block[0]['day']} to {block[-1]['day']}")
    write_json(os.path.join(build_dir, "plan", "_index.json"), index)
    return len(days)


def slug(pack_id):
    return re.sub(r"[^a-z0-9-]+", "-", pack_id.lower()).strip("-")


def build_vocab(build_dir, vocab_files):
    # Deck order IS the SRS introduction order, so packs must run up the ladder regardless of
    # which file they were authored in. Top-up batches are written last but often fill gaps at
    # A2, and left in file order they would introduce A2 words after B1 ones. The sort is
    # stable, so packs stay in their authored order within a level.
    packs = [p for f in vocab_files for p in read_json(f)["packs"]]
    for p in packs:
        if p["level"] not in LADDER:
            die(f"pack {p['id']}: unknown level {p['level']!r}")
    packs.sort(key=lambda p: LADDER.index(p["level"]))

    seen, index, total, dropped = set(), [], 0, 0
    for pack in packs:
        words = []
        for w in pack["words"]:
            wid = unicodedata.normalize("NFC", w["hr"]).lower()
            if w["id"] != wid:
                die(f"pack {pack['id']}: id {w['id']!r} is not lower(NFC(hr)) {wid!r}")
            if wid in seen:
                dropped += 1
                continue
            seen.add(wid)
            words.append(w)
        if not words:
            print(f"  vocab: pack {pack['id']} emptied by dedup, skipped")
            continue
        pack = dict(pack, words=words)
        name = f"{slug(pack['id'])}.json"
        write_json(os.path.join(build_dir, "vocab", name), {"packs": [pack]})
        index.append(name)
        total += len(words)
        print(f"  vocab/{name:<28} {pack['level']:<3} {len(words):>3} words")
    write_json(os.path.join(build_dir, "vocab", "_index.json"), index)
    return total, dropped


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("build_dir")
    ap.add_argument("--title", required=True)
    ap.add_argument("--lessons", nargs="*", default=[])
    ap.add_argument("--vocab", nargs="*", default=[])
    ap.add_argument("--retire", default=None,
                    help="file of lesson titles to skip, see de_retired.txt")
    args = ap.parse_args()

    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    lessons = vocab_words = dropped = 0
    if args.lessons:
        print("PLAN")
        lessons = build_plan(args.build_dir, args.title, args.lessons,
                             load_retired(args.retire))
    if args.vocab:
        print("VOCAB")
        vocab_words, dropped = build_vocab(args.build_dir, args.vocab)

    print(f"\n{lessons} lessons, {vocab_words} unique words "
          f"({dropped} duplicates dropped) -> {args.build_dir}")


if __name__ == "__main__":
    main()
