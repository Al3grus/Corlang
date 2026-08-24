# -*- coding: utf-8 -*-
"""Placement tests: does every question ask something the learner can actually answer?

The placement test is the highest-stakes content in a course and the least looked at. It runs
ONCE, it decides where a learner starts, and there is no retake: a question that gives its answer
away promotes somebody a whole band, and a question that never says what it is testing demotes
somebody who knows the material perfectly well. Both mistakes are permanent for that learner.

Two real defects prompted this, both shipped and both invisible to every other validator:

  * LEAKAGE. Croatian A2 asked "'Moram ___ ranije.' (I have to get up earlier: ustati)" with
    `ustati` as the answer and three conjugated forms as distractors. The hint printed the answer.
    Anyone reaching that band cleared it without knowing any Croatian.
  * NO TASK. Portuguese had thirteen bare-blank prompts with nothing saying what was being
    tested, eight of them genuinely ambiguous: "'Ela insistiu ___ pagar a conta.'" with four
    prepositions does not tell a B1 learner whether the question is about the preposition, the
    verb, or the register.

`proctor.py` audits lessons and quizzes and never opened placement.json.

What this checks, per language:
  1. LEAKAGE   no PARENTHETICAL HINT names the answer. Scoped to the hint rather than the whole
               prompt on purpose: the target sentence may legitimately contain the answer word
               (a German relative pronoun repeats the article, "which form of X" questions name
               X), and flagging those buried the real defect in twenty false positives. A gloss
               is the one place a word can only be a giveaway.
  2. ANSWERED  the answer is one of the options, exactly once, and the options are distinct.
  3. TASK      a prompt containing a blank says what is being asked: a parenthetical hint, or an
               instructional lead-in ("Choose the article:", "Present tense:", "O plural de ...").
               Reported as INFO, not a problem - a sentence can carry its own instruction.
  4. UNIQUE    no two questions share a prompt. The bands are probed independently, so a repeat
               is a free second chance at one band and a wasted question in another.
  5. BANDS     every (level, startDay) band has at least three items, which is the smallest the
               2-of-3 pass rule is defined for (see Placement.neededToPass).
"""
import io
import json
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
CONTENT = os.path.join(ROOT, "app", "src", "main", "assets", "content")

# Prompts whose instruction is the sentence itself rather than a parenthetical.
LEAD_IN = re.compile(
    r"(?i)^\s*(choose|pick|which|complete|present|past|future|imperative|plural|report"
    r"|o plural|o adv|the plural|the adverb|o documento|o superlativo)"
)


def check(lang):
    path = os.path.join(CONTENT, lang, "placement.json")
    if not os.path.exists(path):
        return None, ["no placement.json"], []
    data = json.load(io.open(path, encoding="utf-8"))
    qs = data.get("questions", [])
    errs, info = [], []

    seen_prompts = {}
    bands = {}
    for i, q in enumerate(qs):
        prompt = q.get("prompt", "")
        answer = q.get("answer", "")
        options = q.get("options", [])
        where = f"[{q.get('level')}/{q.get('startDay')}] {prompt[:60]}"

        # 1. LEAKAGE, in the hints only. Word-bounded so a one-letter answer like "A" does not
        #    fire on every word containing an a.
        for hint in re.findall(r"\(([^)]*)\)", prompt):
            if answer and re.search(r"(?<!\w)" + re.escape(answer) + r"(?!\w)", hint, re.I):
                errs.append(f"LEAKAGE: the hint ({hint}) names the answer {answer!r} - {where}")
                break

        # 2. ANSWERED.
        if answer not in options:
            errs.append(f"ANSWER not among options ({answer!r}) - {where}")
        elif options.count(answer) != 1:
            errs.append(f"ANSWER appears {options.count(answer)} times in options - {where}")
        if len(set(options)) != len(options):
            errs.append(f"DUPLICATE options - {where}")
        if len(options) < 2:
            errs.append(f"only {len(options)} option(s) - {where}")

        # 3. TASK.
        if "___" in prompt and "(" not in prompt and not LEAD_IN.match(prompt):
            info.append(f"blank with no stated task - {where}")

        # 4. UNIQUE.
        if prompt in seen_prompts:
            errs.append(f"DUPLICATE prompt, also question {seen_prompts[prompt]} - {where}")
        else:
            seen_prompts[prompt] = i

        bands.setdefault((q.get("level"), q.get("startDay")), 0)
        bands[(q.get("level"), q.get("startDay"))] += 1

    # 5. BANDS.
    for (level, day), n in sorted(bands.items(), key=lambda kv: (kv[0][1] or 0, kv[0][0] or "")):
        if n < 3:
            errs.append(f"BAND {level}/{day} has {n} item(s); 3 is the minimum the pass rule needs")

    return len(qs), errs, info


def main(argv):
    langs = [a for a in argv if not a.startswith("--")]
    verbose = "--info" in argv
    if not langs:
        # The LIVE courses by default, straight from the manifest that decides what ships.
        # Hidden courses are checked only when named, so an unfinished one cannot fail a gate
        # it was never meant to pass - and is checked the moment it is unhidden.
        langs = json.load(io.open(os.path.join(CONTENT, "_index.json"), encoding="utf-8"))
        langs = [c for c in (langs if isinstance(langs, list) else langs.get("languages", []))
                 if os.path.exists(os.path.join(CONTENT, c, "placement.json"))]
    bad = 0
    for lang in langs:
        total, errs, info = check(lang)
        print(f"{lang}: {total} questions, "
              f"{'OK' if not errs else str(len(errs)) + ' PROBLEMS'}"
              f"{f', {len(info)} INFO' if info else ''}")
        for e in errs[:40]:
            print(f"    - {e}")
        if len(errs) > 40:
            print(f"    ... and {len(errs) - 40} more")
        if verbose:
            for i in info:
                print(f"    i {i}")
        bad += len(errs)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    sys.exit(main(sys.argv[1:]))
