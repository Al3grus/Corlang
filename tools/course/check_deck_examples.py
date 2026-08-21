# -*- coding: utf-8 -*-
"""Deck example sentences: is every flashcard's example present, usable and its own?

`VocabWord.example` is not decoration. Two features read it:

  * `WordsScreen` prints it under the word, and speaks it through TTS. A card without one is a
    bare headword with an English gloss and no evidence of how the word behaves in a sentence.
  * `DrillGen.clozeFor` BLANKS it. It looks for the sentence token sharing the longest prefix
    with the headword and replaces that token with "___". Nothing verifies it blanked the right
    word: an example for `riječ` that happens to contain `rijeka` produces a drill whose answer
    is the wrong noun, and the learner is marked wrong for reading it correctly.

Croatian shipped 303 A0 words with no example at all (the whole of `00-a0-core.json`, the first
303 slots of the deck, so the first thirty lessons of every learner's SRS queue) while every
other course was at 100%. Nothing caught it, because coverage was never checked anywhere.

What this checks, per language:
  1. COVERAGE  every deck word carries an example with a non-empty target and gloss.
  2. CLOZE     where DrillGen would build a cloze, exactly one token looks like the headword,
               so which word gets blanked is not decided by word order.
  3. TYPABLE   no pair symbol, arrow or bracket in the target (mirrors check_wrapup's rule:
               the target is spoken and read, and a cloze answer is typed).
  4. UNIQUE    no two cards share an example sentence. A card whose example is also a sentence
               the plan teaches is reported as INFO, not a problem: meeting a card's sentence
               again in the lesson that taught it is reinforcement, and every course does it a
               little. Only two CARDS carrying the same sentence is a real defect, since the
               second card then teaches nothing the first did not.
  5. SANE      the target is a sentence, not a restatement of the gloss, and stays inside
               RECALL_MAX_CHARS.

Usage:  python check_deck_examples.py hr [fr pt ...]
"""
import io
import json
import os
import re
import sys
import unicodedata

ROOT = os.path.join("app", "src", "main", "assets", "content")

# NOT the wrap-up's typed-recall cap, which is 80 because a learner has to TYPE that string.
# Nothing types a deck example: WordsScreen reads it aloud, and DrillGen's cloze answer is picked
# from options. So the limit here is only against a sentence so long it stops being an example,
# and trimming good B1/B2 sentences to satisfy a borrowed number would be the S17 mistake of
# deleting content to make a figure look right.
MAX_CHARS = 100
# Mirrors check_wrapup.PAIR_SYMBOLS plus brackets, which Grading.normalize does not strip.
BANNED = ["→", "←", "↔", "⇒", "=", "«", "»", "–", "—", "✓", "✗", "(", ")", "·", "…"]


def norm(s):
    s = unicodedata.normalize("NFD", s.strip().lower())
    s = "".join(c for c in s if unicodedata.category(c) != "Mn").replace("đ", "d")
    return re.sub(r"\s+", " ", re.sub(r"[^\w\s]", " ", s)).strip()


def target_strings(node, path=""):
    """Every target-language surface of a plan file (same key scoping as check_hr)."""
    if isinstance(node, dict):
        for k, v in node.items():
            if k in ("sources", "day", "week"):
                continue
            yield from target_strings(v, path + "." + k)
    elif isinstance(node, list):
        for v in node:
            yield from target_strings(v, path + "[]")
    elif isinstance(node, str):
        if path.endswith(".hr") or path.endswith(".target") or path.endswith(".answer"):
            yield node


LETTERS = re.compile(r"[^\W\d_]+", re.UNICODE)


def common_prefix(a, b):
    i = 0
    while i < len(a) and i < len(b) and a[i] == b[i]:
        i += 1
    return i


def cloze_answer(head, target):
    """Port of DrillGen.clozeFor's token selection. None where it declines to build a cloze."""
    head = head.lower()
    if " " in head or len(head) < 4:
        return None
    tokens = [t for t in LETTERS.findall(target) if len(t) >= 3]
    if not tokens:
        return None
    min_shared = max(3, len(head) - 2)
    best = max(tokens, key=lambda t: common_prefix(t.lower(), head))
    if common_prefix(best.lower(), head) < min_shared:
        return None
    return best


def lesson_corpus(lang):
    out = {}
    pdir = os.path.join(ROOT, lang, "plan")
    for name in json.load(io.open(os.path.join(pdir, "_index.json"), encoding="utf-8")):
        doc = json.load(io.open(os.path.join(pdir, name), encoding="utf-8"))
        for day in doc["days"]:
            for s in target_strings(day):
                out.setdefault(norm(s), day.get("day"))
    return out


def check(lang):
    errs, info = [], []
    vdir = os.path.join(ROOT, lang, "vocab")
    index = json.load(io.open(os.path.join(vdir, "_index.json"), encoding="utf-8"))
    lessons = lesson_corpus(lang)
    seen = {}
    total = 0
    for name in index:
        doc = json.load(io.open(os.path.join(vdir, name), encoding="utf-8"))
        for pack in doc["packs"]:
            for w in pack["words"]:
                total += 1
                wid, head = w["id"], (w.get("hr") or w["id"]).strip()
                tag = f"{name}/{pack['id']}/{wid}"
                ex = w.get("example")
                if not ex or not (ex.get("target") or "").strip():
                    errs.append(f"{tag}: no example sentence")
                    continue
                target, gloss = ex["target"].strip(), (ex.get("gloss") or "").strip()
                if not gloss:
                    errs.append(f"{tag}: example has no gloss")
                for sym in BANNED:
                    if sym in target:
                        errs.append(f"{tag}: untypable {sym!r} in target {target!r}")
                if len(target) > MAX_CHARS:
                    errs.append(f"{tag}: target is {len(target)} chars, cap is {MAX_CHARS}")
                if len(LETTERS.findall(target)) < 2:
                    errs.append(f"{tag}: target is not a sentence: {target!r}")
                if norm(target) == norm(head):
                    errs.append(f"{tag}: target just repeats the headword: {target!r}")
                if gloss and norm(gloss) == norm(target):
                    errs.append(f"{tag}: gloss is the target, untranslated: {target!r}")
                # 2. CLOZE: DrillGen blanks the token with the longest shared prefix and has
                # no tie-break, so when two tokens tie, the blank lands on whichever the author
                # happened to write first. "Racunalo racuna brze od covjeka." for `racunati`
                # blanks the NOUN and asks the learner to produce it as a verb form; the same
                # shape hid "Pili daske novom pilom." for the noun `pila`.
                #
                # Only the TIE is reported. An earlier version also flagged a blank that is
                # another deck word's headword, and it was wrong seven times out of eight: an
                # inflected form legitimately collides with a different lemma all the time
                # (`u ovoj cetvrti` is the locative of `cetvrt`, not the ordinal `cetvrti`),
                # and a checker that cries wolf is one nobody reruns. The residual gap is a
                # lookalike with NO tie, which nothing here can tell from a real inflection
                # without a morphological analyser.
                blanked = cloze_answer(head, target)
                if blanked is not None:
                    toks = [t for t in LETTERS.findall(target) if len(t) >= 3]
                    top = common_prefix(blanked.lower(), head.lower())
                    tied = {t.lower() for t in toks
                            if common_prefix(t.lower(), head.lower()) == top}
                    if len(tied) > 1:
                        errs.append(f"{tag}: cloze is ambiguous, {sorted(tied)} tie for "
                                    f"{head!r}: {target!r}")
                # 4. UNIQUE.
                key = norm(target)
                if key in seen:
                    errs.append(f"{tag}: example already used by {seen[key]}: {target!r}")
                else:
                    seen[key] = tag
                if key in lessons:
                    info.append(f"{tag}: also lesson {lessons[key]}'s own sentence: {target!r}")
    return total, errs, info


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    bad = 0
    verbose = "--info" in argv
    for lang in [a for a in argv if not a.startswith("--")]:
        total, errs, info = check(lang)
        print(f"{lang}: {total} deck words, "
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
