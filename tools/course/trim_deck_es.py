# -*- coding: utf-8 -*-
"""Propose (and, once reviewed, apply) the trim that brings the deck to its exact capacity.

The deck is a FIXED-CAPACITY PIPE. The SRS unlocks `deck[0 .. lesson * 10]`, so a 250-lesson
course consumes exactly 2,500 words and every word past that index is unreachable and dead
(registry S17). Italian ships 2,836 words against 245 lessons and therefore carries 386 words no
learner can ever see. This script exists so Spanish does not repeat that.

**It proposes; it does not decide.** Trimming by frequency rank alone is a fair proxy at A1 and
A2, where the vocabulary is core, and it would gut B1, whose vocabulary is deliberately thematic
and low-frequency: `la hipoteca` and `el expediente` are rare words a B1 learner genuinely needs.
So the report ranks candidates by three signals in order of confidence, and a human picks:

  1. DUPLICATE GLOSS. Two entries whose English gloss is the same, or nearly, are near-synonyms
     competing for one slot. This is the strongest signal and the safest cut, because dropping
     one loses no meaning.
  2. ABSENT FROM THE FREQUENCY LIST at all, i.e. outside the top 2,500 forms. Weak on its own at
     B1, informative in combination with (1).
  3. WORST FREQUENCY RANK among what remains.

Usage:
  python trim_deck_es.py <vocab-src-dir> --report
  python trim_deck_es.py <vocab-src-dir> --apply drop-ids.txt
"""
import argparse
import collections
import io
import json
import os
import re
import sys
import unicodedata

LADDER = ["A1", "A2", "B1"]
# Phase 1 allocation: 250 lessons x 10 words, split A1 45 / A2 75 / B1 130 lessons.
TARGET = {"A1": 450, "A2": 750, "B1": 1300}
STOP = {"the", "a", "an", "to", "of", "for", "in", "on", "at", "with", "and", "or", "be",
        "it", "that", "this", "his", "her", "your", "my", "someone", "something"}
# Glosses that are grammatical LABELS rather than meanings. Two entries sharing one of these
# are not synonyms; the gloss simply carries no semantic content to compare. Found on the
# first real run, which reported un/este and una/esta as droppable because both are glossed
# with a bare gender label.
TRIVIAL_GLOSSES = {"masculine", "feminine", "neuter", "plural", "singular",
                   "feminine masculine", "masculine plural", "feminine plural"}

# CLOSED-CLASS CORE VOCABULARY, never a trim candidate at any frequency.
#
# This exists because the frequency signal's first real run proposed dropping treinta, cuarenta,
# cincuenta, sesenta, setenta, ochenta, noventa, martes, miércoles and jueves from A1. All ten
# are absent from the top-2,500 of the OpenSubtitles list, for the obvious reason that film
# dialogue rarely says "ochenta" or "miércoles" and constantly says "sí" and "quiero". An A1
# learner needs the tens and the weekdays absolutely: a course that teaches Monday and Friday
# but not Wednesday is broken in a way no gate would catch.
#
# This is the `freq-es` register caveat from docs/sources/es-exams.md §4 demonstrated rather
# than asserted, and it is the concrete reason this tool proposes instead of deciding. A closed
# class is complete or it is wrong; frequency has no vote.
PROTECTED = {
    # numbers
    "cero", "uno", "una", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
    "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete",
    "dieciocho", "diecinueve", "veinte", "treinta", "cuarenta", "cincuenta", "sesenta",
    "setenta", "ochenta", "noventa", "cien", "ciento", "mil", "millón", "primero", "segundo",
    "tercero", "último",
    # days and months
    "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo",
    "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre",
    "octubre", "noviembre", "diciembre",
    # colours
    "blanco", "negro", "rojo", "azul", "verde", "amarillo", "gris", "marrón", "rosa",
    "naranja", "morado",
    # the immediate family, which a beginner course cannot have holes in
    "padre", "madre", "hijo", "hija", "hermano", "hermana", "abuelo", "abuela", "tío", "tía",
    "primo", "prima", "marido", "esposa", "padres",
}


def strip_accents(s):
    return "".join(c for c in unicodedata.normalize("NFD", s)
                   if unicodedata.category(c) != "Mn")


def slice_order(names):
    """Ladder order, then slice number, which is the SRS introduction order."""
    def key(n):
        m = re.search(r"(a1|a2|b1)-slice(\d+)", n)
        if not m:
            return (99, 99, n)
        return (LADDER.index(m.group(1).upper()), int(m.group(2)), n)
    return sorted(names, key=key)


def load(dirpath):
    files = slice_order([f for f in os.listdir(dirpath) if f.endswith(".json")])
    deck = []
    for f in files:
        d = json.load(io.open(os.path.join(dirpath, f), encoding="utf-8"))
        for pack in d.get("packs", []):
            for w in pack["words"]:
                deck.append({"file": f, "pack": pack["id"], "level": pack["level"], "w": w})
    return files, deck


def freq_ranks(dirpath):
    path = os.path.join(dirpath, "FREQ-TOP2500.txt")
    ranks = {}
    if os.path.exists(path):
        for line in io.open(path, encoding="utf-8"):
            if line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) >= 2:
                ranks.setdefault(parts[1].strip(), int(parts[0]))
    return ranks


def best_rank(hw, ranks):
    """Frequency of a LEMMA, approximated from a list of word FORMS.

    The list is forms, not lemmas, which broke the naive lookup badly: `llamarse` is absent from
    the top 2,500 while `llama`, `llamo` and `llamas` all rank high, so the signal reported one
    of the most essential verbs in the language as droppable. Every verb infinitive was being
    penalised the same way, because subtitle dialogue uses conjugated forms and rarely the bare
    infinitive.

    So: exact match first, then the best rank among forms sharing a stem prefix. Pronominal -se
    is stripped, and the stem is capped at 5 characters, enough to be discriminating without
    demanding that a radical-changing verb keep its stem vowel."""
    if hw in ranks:
        return ranks[hw]
    stem = re.sub(r"se$", "", hw) if hw.endswith("se") and len(hw) > 4 else hw
    stem = re.sub(r"(ar|er|ir)$", "", stem)
    stem = strip_accents(stem)[:5]
    if len(stem) < 3:
        return None
    best = None
    for form, r in ranks.items():
        if strip_accents(form).startswith(stem):
            best = r if best is None else min(best, r)
    return best


def headword(hr):
    toks = hr.split()
    if toks and toks[0].lower() in ("el", "la", "los", "las"):
        toks = toks[1:]
    return toks[0].lower() if toks else hr.lower()


def gloss_key(en):
    """A normalised gloss, for spotting two entries that mean the same thing."""
    words = [w for w in re.findall(r"[a-z]+", en.lower()) if w not in STOP]
    return " ".join(sorted(set(words)))


def report(dirpath):
    files, deck = load(dirpath)
    ranks = freq_ranks(dirpath)
    by_level = collections.Counter(e["level"] for e in deck)

    print(f"{len(files)} slice files, {len(deck)} words\n")
    print(f"{'level':<6}{'have':>7}{'target':>8}{'trim':>7}")
    over = {}
    for lv in LADDER:
        have, want = by_level.get(lv, 0), TARGET[lv]
        over[lv] = max(0, have - want)
        print(f"{lv:<6}{have:>7}{want:>8}{over[lv]:>7}")
    total_have, total_want = len(deck), sum(TARGET.values())
    print(f"{'TOTAL':<6}{total_have:>7}{total_want:>8}{max(0, total_have - total_want):>7}")
    if total_have < total_want:
        print(f"\nUNDER CAPACITY by {total_want - total_have}. Author more before trimming: a "
              f"deck shorter than lessons x 10 means the last lessons introduce nothing, which "
              f"is the defect the Kotlin gate everyDeckCoversTheWholeCourse exists to catch.")

    # Signal 0: the same id in two slices. Not a judgement call at all, just work the dedup
    # pass has not caught up with yet, so it is reported separately and never mixed into the
    # trim proposals.
    seen = {}
    true_dups = []
    for e in deck:
        k = unicodedata.normalize("NFC", e["w"]["id"])
        if k in seen:
            true_dups.append((k, seen[k], e["file"]))
        else:
            seen[k] = e["file"]
    print(f"\n=== SIGNAL 0: EXACT duplicate ids ({len(true_dups)}) ===")
    if true_dups:
        print("  These are not trim candidates. Run the dedup pass, keeping first introduction.")
        for k, a, b in true_dups:
            print(f"      {k!r}: {a} and {b}")
    else:
        print("  none")

    # Signal 1: duplicate / near-duplicate gloss, within a level AND within a part of speech.
    #
    # The pos constraint is load-bearing. Without it the first run reported la tos/toser,
    # el freno/frenar, el empate/empatar, el voto/votar, comentar/el comentario, mentir/la
    # mentira and saltar/el salto as droppable near-synonyms. Every one of those is a noun and
    # its own verb, which a learner needs BOTH of: they are not competing for one slot, they are
    # a derivational pair. Only same-pos pairs are genuine synonyms (todavía/aún,
    # de repente/de pronto, otra vez/de nuevo).
    print("\n=== SIGNAL 1: near-synonyms, same gloss AND same part of speech (safest cuts) ===")
    for lv in LADDER:
        buckets = collections.defaultdict(list)
        for e in deck:
            if e["level"] == lv:
                key = (e["w"].get("pos", "?"), gloss_key(e["w"].get("en", "")))
                buckets[key].append(e)
        dups = {k: v for k, v in buckets.items()
                if len(v) > 1 and k[1] and k[1] not in TRIVIAL_GLOSSES}
        if not dups:
            print(f"  {lv}: none")
            continue
        print(f"  {lv}: {len(dups)} clusters, {sum(len(v) - 1 for v in dups.values())} droppable")
        for k, v in sorted(dups.items(), key=lambda kv: -len(kv[1]))[:25]:
            ids = ", ".join(f"{e['w']['id']} [{e['file'].split('-')[1].split('.')[0]}]" for e in v)
            print(f"      {k[0]:<10} {k[1]!r}: {ids}")

    # Signals 2 and 3: frequency.
    print("\n=== SIGNALS 2 and 3: lowest frequency value, per level ===")
    for lv in LADDER:
        if not over[lv]:
            print(f"  {lv}: nothing to trim")
            continue
        scored, protected = [], 0
        for e in deck:
            if e["level"] != lv:
                continue
            hw = headword(e["w"]["hr"])
            if hw in PROTECTED:
                protected += 1
                continue          # closed-class core: frequency has no vote, see PROTECTED
            r = best_rank(hw, ranks)
            scored.append((r if r is not None else 10 ** 6, e))
        scored.sort(key=lambda t: -t[0])
        print(f"  {lv}: {over[lv]} to trim, {protected} protected as closed-class core, "
              f"worst {min(over[lv] + 10, len(scored))} of the rest by frequency:")
        for r, e in scored[:over[lv] + 10]:
            rank = "absent" if r >= 10 ** 6 else f"#{r}"
            print(f"      {rank:<8} {e['w']['id']:<28} {e['w'].get('en','')[:40]}")

    print("\nWrite the ids you decide to drop, one per line, into a file and re-run with")
    print("--apply. Nothing is changed until you do.")


def apply_drops(dirpath, listfile):
    drop = set()
    for line in io.open(listfile, encoding="utf-8"):
        line = line.split("#")[0].strip()
        if line:
            drop.add(unicodedata.normalize("NFC", line))
    files, _ = load(dirpath)
    removed, missing = 0, set(drop)
    for f in files:
        p = os.path.join(dirpath, f)
        d = json.load(io.open(p, encoding="utf-8"))
        changed = False
        for pack in d.get("packs", []):
            keep = []
            for w in pack["words"]:
                if unicodedata.normalize("NFC", w["id"]) in drop:
                    removed += 1
                    missing.discard(unicodedata.normalize("NFC", w["id"]))
                    changed = True
                else:
                    keep.append(w)
            pack["words"] = keep
        if changed:
            json.dump(d, io.open(p, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(f"removed {removed} words")
    if missing:
        print(f"WARNING: {len(missing)} listed ids were not found: {sorted(missing)[:20]}")
    _, deck = load(dirpath)
    by_level = collections.Counter(e["level"] for e in deck)
    for lv in LADDER:
        print(f"  {lv}: {by_level.get(lv, 0)} (target {TARGET[lv]})")
    print(f"  TOTAL: {len(deck)} (target {sum(TARGET.values())})")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dirpath")
    ap.add_argument("--report", action="store_true")
    ap.add_argument("--apply")
    args = ap.parse_args()
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if args.apply:
        apply_drops(args.dirpath, args.apply)
    else:
        report(args.dirpath)


if __name__ == "__main__":
    main()
