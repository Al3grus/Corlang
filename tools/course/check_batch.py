# -*- coding: utf-8 -*-
"""Pre-merge validator for authored lesson batches.

Runs the same invariants the Kotlin content gates enforce, but against the raw batch files, so
a subagent's mistake is caught before it ever reaches the plan. Usage:
    python check_batch.py <file.json> [<file.json> ...]
Exits non-zero and prints every violation grouped by file.
"""
import io
import json
import os
import re
import sys
import unicodedata

DASHES = re.compile(r"[–—]")
# Mirrors the Kotlin gate: it bans "sign up AT <place>", not the ordinary English verb
# ("sign up for a class" is a perfectly good gloss and must not be rewritten).
EXTERNAL = re.compile(
    r"https?://|www\.|ffzg|unizg|e-tecaj|\be-course|croaticum|cehas|"
    r"sign in at|sign up at|log in at|\bduolingo\b|\bmemrise\b|\banki\b|"
    r"youtube|netflix|\btv5\b|instagram|facebook|tiktok",
    re.IGNORECASE)
DAY_N = re.compile(r"\b[Dd]ays?\s+\d")
REQUIRED = {"title", "objective", "paretoFocus", "drills", "reviewBlock", "activities",
            "day", "week", "phase", "level", "resources"}


def norm(s):
    s = unicodedata.normalize("NFD", s.lower())
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"[^\w\s]", " ", s).split()


def walk_strings(node, path=""):
    """Every learner-visible string, skipping provenance keys."""
    if isinstance(node, dict):
        for k, v in node.items():
            if k in ("sources", "day", "week"):
                continue
            yield from walk_strings(v, f"{path}.{k}")
    elif isinstance(node, list):
        for i, v in enumerate(node):
            yield from walk_strings(v, f"{path}[{i}]")
    elif isinstance(node, str):
        yield path, node


def check_file(path):
    errs = []
    raw = io.open(path, encoding="utf-8").read()
    try:
        days = json.loads(raw)
    except Exception as e:
        return [f"INVALID JSON: {e}"]
    if not isinstance(days, list):
        return ["top level must be a JSON array of day objects"]

    # day/week are 0 in a PRE-MERGE batch (the merge tool assigns them) but populated in an
    # assembled build. Detect which we are looking at, so the language checkers can also be run
    # against a finished course; flagging 245 populated days as errors made that impossible.
    nums = [d.get("day") for d in days]
    merged = (len(days) > 1 and all(isinstance(n, int) and n > 0 for n in nums)
              and nums == list(range(nums[0], nums[0] + len(nums))))

    prompts_seen = {}
    for di, day in enumerate(days):
        tag = f"[{di}] {day.get('title', '?')[:40]}"
        missing = REQUIRED - set(day)
        if missing:
            errs.append(f"{tag}: missing keys {sorted(missing)}")
            continue
        if not merged and (day["day"] != 0 or day["week"] != 0):
            errs.append(f"{tag}: day/week must be 0 (merge tool assigns them)")
        if len(day["drills"]) < 2:
            errs.append(f"{tag}: needs at least 2 drills")
        if len(day["reviewBlock"].get("items", [])) < 2:
            errs.append(f"{tag}: reviewBlock needs at least 2 items")

        kinds = [a["type"] for a in day["activities"]]
        for want in ("LEARN", "EXERCISE", "DIALOGUE"):
            if want not in kinds:
                errs.append(f"{tag}: no {want} activity")
        for a in day["activities"]:
            if a["type"] == "LEARN":
                if len(a.get("items", [])) < 4:
                    errs.append(f"{tag}/LEARN: needs at least 4 items")
                for it in a.get("items", []):
                    if not it.get("hr") or not it.get("en"):
                        errs.append(f"{tag}/LEARN: item missing hr/en (target text key is 'hr')")
            elif a["type"] == "DIALOGUE":
                lines = a.get("lines", [])
                if not 5 <= len(lines) <= 10:
                    errs.append(f"{tag}/DIALOGUE: {len(lines)} lines, want 6 to 8")
                for ln in lines:
                    if ln.get("speaker") not in ("Me", "Partner"):
                        errs.append(f"{tag}/DIALOGUE: bad speaker {ln.get('speaker')!r}")
                    if not ln.get("hr") or not ln.get("en"):
                        errs.append(f"{tag}/DIALOGUE: line missing hr/en")
            elif a["type"] == "EXERCISE":
                qs = a.get("questions", [])
                if len(qs) < 4:
                    errs.append(f"{tag}/EXERCISE: only {len(qs)} questions, want 5")
                for q in qs:
                    p = q.get("prompt", "")
                    if p in prompts_seen and prompts_seen[p] != tag:
                        errs.append(f"{tag}: duplicate prompt (also in {prompts_seen[p]}): {p[:60]}")
                    prompts_seen[p] = tag
                    qt = q.get("type")
                    if qt == "MCQ":
                        opts = q.get("options", [])
                        if len(opts) != 4:
                            errs.append(f"{tag}/MCQ: {len(opts)} options, want 4: {p[:50]}")
                        if q.get("answer") not in opts:
                            errs.append(f"{tag}/MCQ: answer not among options: {p[:50]}")
                    elif qt == "FILL":
                        ans = norm(q.get("answer", ""))
                        # Gate: a multi-word answer must not appear verbatim in its own prompt.
                        if len(ans) > 1 and " ".join(ans) in " ".join(norm(p)):
                            errs.append(f"{tag}/FILL: answer leaks into its prompt: {p[:60]}")
                    elif qt == "REORDER":
                        opts, ordered = q.get("options", []), q.get("ordered", [])
                        if sorted(opts) != sorted(ordered):
                            errs.append(f"{tag}/REORDER: options != ordered tokens: {p[:50]}")
                        if len(ordered) < 3:
                            errs.append(f"{tag}/REORDER: too few tokens: {p[:50]}")
                        # Gate: the prompt is the English gloss, never the target sentence.
                        ptok, otok = set(norm(p)), set(norm(" ".join(ordered)))
                        if otok and len(ptok & otok) >= 0.7 * len(otok):
                            errs.append(f"{tag}/REORDER: prompt leaks the answer: {p[:60]}")
                    else:
                        errs.append(f"{tag}: bad question type {qt!r}")

        for where, s in walk_strings(day):
            if DASHES.search(s):
                errs.append(f"{tag}{where}: em/en dash in {s[:60]!r}")
            if EXTERNAL.search(s):
                errs.append(f"{tag}{where}: external reference in {s[:60]!r}")
            if DAY_N.search(s):
                errs.append(f"{tag}{where}: 'day N' phrasing in {s[:60]!r}")
    return errs


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    total, bad = 0, 0
    for path in sys.argv[1:]:
        if not os.path.exists(path):
            print(f"MISSING {path}")
            bad += 1
            continue
        errs = check_file(path)
        n = len(json.loads(io.open(path, encoding='utf-8').read())) if not errs or True else 0
        try:
            n = len(json.load(io.open(path, encoding="utf-8")))
        except Exception:
            n = 0
        total += n
        status = "OK " if not errs else f"{len(errs)} PROBLEMS"
        print(f"{os.path.basename(path):<16} {n:>3} days  {status}")
        for e in errs[:25]:
            print(f"    - {e}")
        if len(errs) > 25:
            print(f"    ... and {len(errs) - 25} more")
        bad += len(errs)
    print(f"\n{total} days total, {bad} problems")
    sys.exit(1 if bad else 0)
