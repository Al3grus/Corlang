# -*- coding: utf-8 -*-
"""Does the native-speaker review workbook still match the course?

A reviewer auditing a stale workbook wastes their time twice: they read lessons that no longer
exist, and they flag defects that were already fixed. On 2026-08-30 the live site was four days
behind and showed a lesson titled "Full cheatsheet review", named for a feature deleted at
v0.75.0, plus a duplicated gender question that the content had already stopped carrying.

The rule this enforces: content changes and the review artefacts move together, in the same
commit. Only for languages that HAVE a review; the rest are silently skipped, because a
workbook nobody has asked for is not a thing to keep in sync.

Compares each existing artefact against a freshly generated one, ignoring the payload's "built"
date, which changes on every run by design.

    python tools/course/check_review_sync.py           # every language with a review
    python tools/course/check_review_sync.py hr        # just this one
"""
import io
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
BUILDER = os.path.join(HERE, "build_review_doc.py")
BUILT = re.compile(r'"built"\s*:\s*"[0-9-]{10}"')


def artefacts(code):
    """Where a review for this language lives, if it lives anywhere."""
    return [p for p in (
        os.path.join(ROOT, "docs", "review", "%s-review-workbook.html" % code),
        os.path.join(ROOT, "server", "review-site", "public", code, "index.html"),
    ) if os.path.isfile(p)]


def reviewed_languages():
    out = []
    content = os.path.join(ROOT, "app", "src", "main", "assets", "content")
    for code in sorted(os.listdir(content)):
        if os.path.isdir(os.path.join(content, code)) and artefacts(code):
            out.append(code)
    return out


def normalise(path):
    with io.open(path, encoding="utf-8") as fh:
        return BUILT.sub('"built":"-"', fh.read())


def check(code):
    have = artefacts(code)
    if not have:
        print("  %-4s no review exists, nothing to keep in sync" % (code + ":"))
        return []
    fresh = os.path.join(tempfile.gettempdir(), "review-sync-%s.html" % code)
    r = subprocess.run([sys.executable, BUILDER, code, "--out", fresh],
                       capture_output=True, text=True, encoding="utf-8", errors="replace",
                       cwd=ROOT)
    if r.returncode != 0:
        return ["%s: could not rebuild the workbook: %s" % (code, (r.stderr or "").strip()[:200])]
    want = normalise(fresh)
    problems = []
    for path in have:
        rel = os.path.relpath(path, ROOT).replace(os.sep, "/")
        if normalise(path) == want:
            print("  %-4s %s is current" % (code + ":", rel))
        else:
            problems.append("%s is STALE: rebuild it, the course has moved on since" % rel)
    try:
        os.remove(fresh)
    except OSError:
        pass
    return problems


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    codes = sys.argv[1:] or reviewed_languages()
    if not codes:
        print("no language has a review workbook; nothing to check")
        sys.exit(0)
    found = []
    for code in codes:
        found += check(code)
    print("")
    for p in found:
        print("  - %s" % p)
    print("%d problems" % len(found))
    if found:
        print("")
        print("  Rebuild and redeploy, then commit the workbook with the content change:")
        for code in codes:
            print("    python tools/course/build_review_doc.py %s" % code)
            print("    python tools/course/build_review_doc.py %s --out server/review-site/public/%s/index.html"
                  % (code, code))
        print("    cd server/review-site && npx wrangler pages deploy public "
              "--project-name corlang-review --branch main --commit-dirty=true")
    sys.exit(1 if found else 0)
