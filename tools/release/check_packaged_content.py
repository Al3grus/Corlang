# -*- coding: utf-8 -*-
"""Does the artefact actually carry the content in the source tree?

On 2026-08-30 the answer was no, and nothing anywhere would have said so. `stageLiveAssets`
copies src/main/assets into build/generated/liveAssets minus the hidden courses, and
`assets.setSrcDirs(listOf(stageLiveAssets))` registers that directory. Registering a path is
not the same as depending on the task that fills it: stageLiveAssets never entered the task
graph, the asset merge found the directory unchanged and reported UP-TO-DATE, and every APK
built between 2026-08-25 and 2026-08-30 shipped a five-day-old copy of the courses.

Everything else stayed green throughout. The source tree was correct, all six offline checkers
passed, and 235 Kotlin tests passed, because every one of them reads src/main/assets. The only
place the defect was visible was inside the artefact, which nothing opened.

So this opens it. Compare what is packaged against what is in source, for the courses the app
offers, and fail on any difference.

    python tools/release/check_packaged_content.py releases/corlang.apk
    python tools/release/check_packaged_content.py app/build/outputs/bundle/playRelease/app-play-release.aab
"""
import hashlib
import io
import json
import os
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
SRC = os.path.join(ROOT, "app", "src", "main", "assets", "content")


def digest(data):
    return hashlib.sha256(data).hexdigest()[:16]


def live_courses():
    with io.open(os.path.join(SRC, "_index.json"), encoding="utf-8") as fh:
        return json.load(fh)


def packaged(path):
    """Every content file inside the artefact, keyed by its path under content/.

    An APK stores assets at assets/...; an AAB stores them at base/assets/...
    """
    out = {}
    with zipfile.ZipFile(path) as z:
        for name in z.namelist():
            for prefix in ("assets/content/", "base/assets/content/"):
                if name.startswith(prefix) and not name.endswith("/"):
                    out[name[len(prefix):]] = digest(z.read(name))
    return out


def on_disk(codes):
    out = {}
    for code in codes:
        root = os.path.join(SRC, code)
        for base, _dirs, names in os.walk(root):
            for n in names:
                full = os.path.join(base, n)
                rel = os.path.relpath(full, SRC).replace(os.sep, "/")
                with io.open(full, "rb") as fh:
                    out[rel] = digest(fh.read())
    with io.open(os.path.join(SRC, "_index.json"), "rb") as fh:
        out["_index.json"] = digest(fh.read())
    return out


def main(path):
    if not os.path.isfile(path):
        print("no artefact at %s" % path)
        return 2
    codes = live_courses()
    want = on_disk(codes)
    got = packaged(path)

    problems = []
    for rel, d in sorted(want.items()):
        if rel not in got:
            problems.append("MISSING from the artefact: %s" % rel)
        elif got[rel] != d:
            problems.append("STALE in the artefact: %s (source %s, packaged %s)" % (rel, d, got[rel]))
    hidden = sorted(r for r in got if r.split("/")[0] not in codes and r != "_index.json")
    for rel in hidden:
        problems.append("SHIPPED but not offered: %s" % rel)

    print("%s" % os.path.relpath(path, ROOT).replace(os.sep, "/"))
    print("  offers %s | %d content files in source, %d packaged"
          % (" ".join(codes), len(want), len(got)))
    for p in problems:
        print("  - %s" % p)
    print("  %d problems" % len(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if len(sys.argv) != 2:
        print(__doc__.strip().splitlines()[-2].strip())
        print("usage: python check_packaged_content.py <apk-or-aab>")
        sys.exit(2)
    sys.exit(main(sys.argv[1]))
