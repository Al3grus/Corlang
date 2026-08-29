# -*- coding: utf-8 -*-
"""The check that checks the checkers.

Two defects got in here on 2026-08-30, and the first hid the second for weeks.

  1. Every check_<code>.py read "no arguments" as "no files". It looped over sys.argv[1:],
     found nothing, printed "0 days total, 0 problems" and exited 0. The runbook documents
     exactly that no-argument command, so following the runbook produced a pass that had
     examined nothing. check_batch.py had the same shape.

  2. Behind that green light, check_batch.REQUIRED still demanded a "resources" key on every
     day. The resources feature was removed at v0.75.0 and no day carries one any more, so
     every day in every course was failing - 594 problems across the two live courses,
     invisible because nobody could get the checker to look.

Per docs/error-registry.md: found once, checked forever. This asserts the two invariants that
would have caught them, over whatever tools exist now rather than a list written today.

    python tools/course/check_tools.py
"""
import io
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
CONTENT = os.path.normpath(os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "content"))
DAYS_TOTAL = re.compile(r"(\d+) days total")

import check_batch  # noqa: E402  (needs HERE on sys.path, which running the script gives us)


def live_courses():
    """The codes the app actually offers. _index.json is authoritative."""
    with io.open(os.path.join(CONTENT, "_index.json"), encoding="utf-8") as fh:
        return json.load(fh)


def tools():
    return sorted(n for n in os.listdir(HERE)
                  if n.startswith("check_") and n.endswith(".py") and n != os.path.basename(__file__))


def a_pass_that_checked_nothing():
    """No tool may exit 0 without having examined anything.

    A tool that cannot work out what to look at must refuse (a non-zero exit), because an
    exit of 0 is read by a human, and by every script that chains these, as "this content is
    clean".
    """
    problems = []
    for name in tools():
        # encoding is explicit: these tools print Croatian and Portuguese, and Windows would
        # otherwise decode their output as cp1252 and throw inside subprocess' reader thread.
        r = subprocess.run([sys.executable, os.path.join(HERE, name)],
                           capture_output=True, text=True, timeout=600,
                           encoding="utf-8", errors="replace")
        out = (r.stdout or "") + (r.stderr or "")
        if r.returncode != 0:
            print("  %-24s non-zero (exit %s)" % (name, r.returncode))
            continue
        m = DAYS_TOTAL.search(out)
        if m and int(m.group(1)) == 0:
            problems.append("%s exits 0 after examining 0 days" % name)
        elif not out.strip():
            problems.append("%s exits 0 and says nothing at all" % name)
        else:
            print("  %-24s examined %s" % (name, (m.group(0) if m else "something and said so")))
    return problems


def required_keys_are_satisfiable():
    """Every key check_batch demands must exist on real shipped days.

    A key that the schema has dropped fails every day of every course while looking like a
    content problem. If REQUIRED and the content disagree, one of them is wrong and this says
    so rather than reporting hundreds of identical failures.
    """
    problems = []
    for code in live_courses():
        plan = os.path.join(CONTENT, code, "plan")
        if not os.path.isdir(plan):
            problems.append("%s has no plan/ directory" % code)
            continue
        seen, days = set(), 0
        for name in sorted(os.listdir(plan)):
            if not name.endswith(".json"):
                continue
            with io.open(os.path.join(plan, name), encoding="utf-8") as fh:
                raw = json.load(fh)
            block = raw.get("days", raw) if isinstance(raw, dict) else raw
            if not isinstance(block, list):
                continue
            for day in block:
                if isinstance(day, dict):
                    seen |= set(day.keys())
                    days += 1
        if not days:
            problems.append("%s: no days found under plan/" % code)
            continue
        missing = sorted(check_batch.REQUIRED - seen)
        if missing:
            problems.append("%s: check_batch.REQUIRED demands %s, which no day of %d carries"
                            % (code, missing, days))
        else:
            print("  %-24s %d days carry every required key" % (code + ":", days))
    return problems


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    found = []

    print("A tool that examines nothing must not exit 0:")
    found += a_pass_that_checked_nothing()

    print("\nEvery key check_batch requires must exist on real days:")
    found += required_keys_are_satisfiable()

    print("")
    for p in found:
        print("  - %s" % p)
    print("%d problems" % len(found))
    sys.exit(1 if found else 0)
