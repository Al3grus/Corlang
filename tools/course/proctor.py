# -*- coding: utf-8 -*-
"""Course-wide proctoring audit. Runs on an assembled build directory, BEFORE integration.

The per-batch checkers (check_batch.py, check_<lang>.py) validate one file at a time, so a
whole class of defects is invisible to them: repetition ACROSS lessons, quiz items leaking
lesson prompts, instructional boilerplate that reads machine-written, and answer patterns that
make exercises guessable. Every check here exists because a real instance shipped or nearly
shipped (the Portuguese lesson-1 "In this lesson you will / You will recognize" duplication is
the founding example).

Usage:  python proctor.py <build-dir>
  where <build-dir> contains plan/, and optionally vocab/, quizzes.json, exams.json.

Exit non-zero on PROBLEMS. INFO lines are advisory and do not fail the run.
"""
import collections
import io
import json
import os
import re
import sys
import unicodedata

def norm_words(s):
    s = unicodedata.normalize("NFD", s.lower())
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"[^\w\s]", " ", s).split()


def ngrams(words, n):
    return {" ".join(words[i:i + n]) for i in range(len(words) - n + 1)}


def load_days(build):
    days = []
    index = json.load(io.open(f"{build}/plan/_index.json", encoding="utf-8"))
    for name in index:
        days.extend(json.load(io.open(f"{build}/plan/{name}", encoding="utf-8"))["days"])
    return days


def instructional_fields(day):
    """Every English instructional string of a day, labelled."""
    out = [("objective", day.get("objective", "")), ("paretoFocus", day.get("paretoFocus", ""))]
    out += [("drill", d) for d in day.get("drills", [])]
    out += [("review", r) for r in day.get("reviewBlock", {}).get("items", [])]
    for a in day.get("activities", []):
        if a.get("intro"):
            out.append((f"{a['type']}-intro", a["intro"]))
    return out


def mcq_iter(container):
    """All MCQ questions in a list of question dicts."""
    for q in container:
        if q.get("type") == "MCQ":
            yield q


def main(build):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    problems, info = [], []
    days = load_days(build)

    # 1. SELF-REPETITION inside a day: the objective restated nearly verbatim in a drill,
    #    review item or intro reads as filler ("In this lesson you will X" / "You will X").
    for i, day in enumerate(days):
        fields = instructional_fields(day)
        obj = ngrams(norm_words(day.get("objective", "")), 5)
        for label, text in fields[1:]:
            if obj and obj & ngrams(norm_words(text), 5):
                problems.append(f"day {i+1} '{day['title'][:35]}': {label} repeats the "
                                f"objective nearly verbatim")

    # 2. BOILERPLATE across the course: an instructional 6-gram appearing many times means
    #    the lessons read stamped-out. Threshold is generous; real courses vary phrasing.
    #
    #    Exempt: RITUAL instructions that point at an app feature. The daily "review your due
    #    words in the Words tab" nudge and the checkpoint pointers repeat BY DESIGN, as habit
    #    loops; flagging them 185 times in Croatian taught us the difference. A phrase is
    #    ritual if it names an app surface, not if it merely recurs.
    RITUAL = re.compile(r"words tab|quiz tab|journey|streak")
    counter = collections.Counter()
    for day in days:
        seen_in_day = set()
        for _, text in instructional_fields(day):
            if RITUAL.search(text.lower()):
                continue
            for g in ngrams(norm_words(text), 6):
                if g not in seen_in_day:
                    counter[g] += 1
                    seen_in_day.add(g)
    for g, c in counter.most_common(12):
        if c >= 10:
            problems.append(f"boilerplate: the phrase '{g}' opens instructional text in {c} lessons")
        elif c >= 6:
            info.append(f"phrase used {c}x across lessons: '{g}'")

    # 3. DUPLICATE TARGET SENTENCES across days: the same taught sentence in two lessons is
    #    wasted teaching; in a quiz it is memorable rather than testing.
    hr_seen = {}
    for i, day in enumerate(days):
        for a in day.get("activities", []):
            for it in a.get("items", []) or []:
                s = it.get("hr", "")
                if len(s) > 14:
                    if s in hr_seen and hr_seen[s] != i:
                        problems.append(f"duplicate taught sentence in days {hr_seen[s]+1} "
                                        f"and {i+1}: '{s[:50]}'")
                    hr_seen[s] = i

    # 4. QUIZ / EXAM prompts colliding with lesson prompts: the learner has literally seen the
    #    question before, so it tests memory of the lesson, not the language.
    lesson_prompts = {q.get("prompt", "") for day in days
                      for a in day.get("activities", []) if a.get("type") == "EXERCISE"
                      for q in a.get("questions", [])}
    for fname in ("quizzes.json", "exams.json"):
        path = os.path.join(build, fname)
        if not os.path.exists(path):
            continue
        data = json.load(io.open(path, encoding="utf-8"))
        qlists = []
        if fname == "quizzes.json":
            qlists = [(f"quiz {z['id']}", z["questions"]) for z in data["quizzes"]]
        else:
            for e in data:
                for s in e["sections"]:
                    if s.get("questions"):
                        qlists.append((f"exam {e['id']}/{s['id']}", s["questions"]))
        for label, qs in qlists:
            for q in qs:
                if q.get("prompt", "") in lesson_prompts:
                    problems.append(f"{label}: prompt duplicated from a lesson: "
                                    f"'{q['prompt'][:50]}'")
                # MCQ answer appearing verbatim inside its own prompt is a giveaway.
                if q.get("type") == "MCQ":
                    ans = q.get("answer", "")
                    if len(ans) >= 4 and re.search(
                            rf"\b{re.escape(ans.lower())}\b", q.get("prompt", "").lower()):
                        problems.append(f"{label}: MCQ answer '{ans}' appears in its own prompt")

    # 5. MCQ LONGEST-ANSWER BIAS: if the correct option is disproportionately the longest,
    #    the course is guessable without knowing any of the language.
    total = longest = 0
    all_mcq = [q for day in days for a in day.get("activities", [])
               if a.get("type") == "EXERCISE" for q in mcq_iter(a.get("questions", []))]
    for q in all_mcq:
        opts = q.get("options", [])
        if len(opts) == 4:
            total += 1
            if max(opts, key=len) == q.get("answer"):
                longest += 1
    if total:
        rate = longest * 100 // total
        line = f"MCQ longest-option-is-answer rate: {rate}% over {total} items"
        (problems if rate > 55 else info).append(
            line + (" (guessable, rewrite distractors)" if rate > 55 else ""))

    # 6. DIFFICULTY sanity per level, advisory.
    by_level = collections.defaultdict(list)
    for day in days:
        for a in day.get("activities", []):
            for q in a.get("questions", []) or []:
                if isinstance(q.get("difficulty"), int):
                    by_level[day["level"]].append(q["difficulty"])
    for lvl, ds in sorted(by_level.items()):
        info.append(f"difficulty {lvl}: min {min(ds)} max {max(ds)} "
                    f"mean {sum(ds)/len(ds):.1f} over {len(ds)} questions")

    print(f"proctor: {len(days)} lessons audited")
    for p in problems:
        print(f"  PROBLEM  {p}")
    for x in info:
        print(f"  info     {x}")
    print(f"\n{len(problems)} problems, {len(info)} advisories")
    return 1 if problems else 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("usage: python proctor.py <build-dir>")
        sys.exit(2)
    sys.exit(main(sys.argv[1]))
