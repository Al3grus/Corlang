# -*- coding: utf-8 -*-
"""Rewrite lesson `resources` strings to names that exist in a build's resources.json.

Lesson batches are authored in parallel with resources.json, so the resource NAMES are agreed
up front and then drift when a URL fails live verification and its entry is dropped or renamed.
That is what happened to German ("Goethe-Institut Deutsch üben" against "Goethe-Institut,
Deutsch üben") and again to Italian, where RAI could not be verified and was replaced.

Usage: python fix_resources.py <build-dir> <batch.json> [...] --map "old=new" ["old=new" ...]
"""
import io, json, sys

build, files, mapping = sys.argv[1], [], {}
args = sys.argv[2:]
if "--map" in args:
    i = args.index("--map")
    files, pairs = args[:i], args[i + 1:]
    for p in pairs:
        k, v = p.split("=", 1)
        mapping[k] = v
else:
    files = args

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
valid = {x["name"] for x in json.load(io.open(f"{build}/resources.json", encoding="utf-8"))["resources"]}

for f in files:
    days = json.load(io.open(f, encoding="utf-8"))
    changed = 0
    for d in days:
        new = [mapping.get(r, r) for r in d["resources"]]
        if new != d["resources"]:
            d["resources"] = new
            changed += 1
    unknown = {r for d in days for r in d["resources"]} - valid
    io.open(f, "w", encoding="utf-8", newline="\n").write(
        json.dumps(days, ensure_ascii=False, indent=1) + "\n")
    status = "OK" if not unknown else f"STILL UNKNOWN: {sorted(unknown)}"
    print(f"{f:<18} {len(days):>3} days, {changed} rewritten  {status}")
