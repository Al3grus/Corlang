# -*- coding: utf-8 -*-
"""List every proper noun in learner-visible content, for the real-names boundary review.

The standing rule (Gold Book, docs/language-standard.md §7) is: **no named real companies,
platforms, apps, institutions or real people anywhere in lesson text.** Invented proper nouns
are the correct way to satisfy a convention that needs a name; real geography is exempt;
`resources.json` is the one sanctioned place naming real external material.

That rule is the LARGEST defect class in this project's history. It had to be fixed in four of
the five shipped courses, and it was found each time by a human reading thousands of lines, not
by a tool. This script does not decide anything: no mechanical test can tell an invented firm
from a real one. It does the part that IS mechanical, which is finding every candidate and
grouping it, so the human pass reads a few hundred distinct names instead of the whole course.

What it reports:
  * every capitalised token that is not sentence-initial and not in the exempt lists,
  * grouped by name with a count and one example location each,
  * multi-word sequences of capitalised tokens kept together (Universidad Complutense),
  * a separate section for names appearing only ONCE, which is where a real name slipped in as
    local colour tends to hide.

Exemptions are deliberately narrow and are listed in code so a reader can audit them: real
geography is allowed by the rule itself, and the target language's own everyday capitalisation
(the language name, nationalities in some languages, the pronoun I in English glosses) is not a
proper-noun finding.

Usage:
  python scan_proper_nouns.py <dir-or-file> [...] [--lang es] [--min-count 1]
"""
import argparse
import io
import json
import os
import re
import sys
import unicodedata

import check_batch

# Real geography is explicitly permitted by the boundary rule ("Real geography (cities,
# countries, natural landmarks like Plitvice Lakes) is not covered by this rule"), so the
# common Spanish-course geography is exempted to keep the report readable. Anything NOT here
# still shows up, which is the safe direction for an exemption list to fail in.
GEOGRAPHY_ES = {
    "españa", "madrid", "barcelona", "sevilla", "valencia", "bilbao", "granada", "toledo",
    "málaga", "zaragoza", "salamanca", "córdoba", "santander", "murcia", "alicante", "vigo",
    "gijón", "oviedo", "pamplona", "cádiz", "almería", "burgos", "león", "segovia", "ávila",
    "cuenca", "girona", "lleida", "tarragona", "huelva", "jaén", "cáceres", "badajoz", "logroño",
    "andalucía", "cataluña", "galicia", "asturias", "cantabria", "aragón", "navarra", "extremadura",
    "castilla", "mancha", "rioja", "canarias", "baleares", "mallorca", "menorca", "ibiza",
    "tenerife", "pirineos", "ebro", "duero", "tajo", "guadalquivir", "mediterráneo", "atlántico",
    "cantábrico", "europa", "américa", "áfrica", "asia", "francia", "portugal", "italia",
    "alemania", "inglaterra", "reino", "unido", "irlanda", "bélgica", "holanda", "suiza",
    "austria", "grecia", "polonia", "marruecos", "méxico", "argentina", "chile", "colombia",
    "perú", "uruguay", "ecuador", "bolivia", "venezuela", "cuba", "brasil", "japón", "china",
    "lisboa", "parís", "londres", "roma", "berlín", "bruselas", "buenos", "aires", "lima",
    "bogotá", "montevideo", "quito", "caracas", "habana",
}
# Everyday capitalisation that is not a proper-noun finding.
COMMON_EXEMPT = {
    # Language and nationality words are lowercase in Spanish, but appear capitalised in the
    # English glosses, where they are ordinary words rather than named entities.
    "spanish", "english", "french", "german", "italian", "portuguese", "castilian",
    "spain", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
    "january", "february", "march", "april", "may", "june", "july", "august", "september",
    "october", "november", "december", "i", "im", "id", "ill", "ive",
    # Section labels that legitimately appear in exam-technique content.
    "grupo", "prueba", "tarea", "apto", "cefr", "mcer",
}

SENTENCE_END = re.compile(r"[.!?¡¿:;]\s*$")
TOKEN = re.compile(r"[A-ZÁÉÍÓÚÑÜ][\wÁÉÍÓÚÑÜáéíóúñü'’]*")


def strip_accents(s):
    return "".join(c for c in unicodedata.normalize("NFD", s)
                   if unicodedata.category(c) != "Mn")


def learner_visible(raw):
    """Every learner-visible string, minus provenance arrays. Mirrors the Kotlin gate's scope:
    resources.json is the one sanctioned home for real external names and is skipped by the
    caller, not here."""
    for path, s in check_batch.walk_strings(raw):
        if ".sources[" in path:
            continue
        yield path, s


def candidates(text):
    """Capitalised sequences that are NOT sentence-initial. A sequence of adjacent capitalised
    tokens is kept whole, so 'Universidad Complutense' reports as one name rather than two."""
    out = []
    # Split into rough sentences so "first word" can be recognised.
    # A NEWLINE is a sentence boundary too. Monospace `tables` diagrams are full of short lines
    # with no terminal punctuation, so splitting on [.!?] alone made each table one giant
    # "sentence" and every line-initial capital in it looked like a mid-sentence proper noun.
    # Found running this over grammar.json, whose conjugation tables produced a dozen false
    # hits (A, No, Quiero, Lo, Estoy) that would have been hundreds across 250 lessons.
    parts = re.split(r"(?<=[.!?])\s+|\n+", text)
    for part in parts:
        toks = list(TOKEN.finditer(part))
        if not toks:
            continue
        # Sentence-initial means "first WORD", and in Spanish the sentence opens with ¿ or ¡
        # before that word. Counting the opening mark as content made every question word
        # (¿Cuándo, ¿Quién, ¿Puedes) look like a mid-sentence proper noun, which buried the
        # real signal under dozens of false hits on the very first run.
        first_start = len(part) - len(part.lstrip(" \t¿¡\"'«("))
        run, run_start = [], None
        for m in toks:
            is_first = (m.start() <= first_start)
            if is_first and not run:
                continue                      # sentence-initial capital, not evidence
            if run and m.start() == run_start + len(" ".join(run)) + 1:
                run.append(m.group(0))
            else:
                if run:
                    out.append(" ".join(run))
                run, run_start = [m.group(0)], m.start()
        if run:
            out.append(" ".join(run))
    return out


def exempt(name, lang):
    words = [strip_accents(w.lower()) for w in name.split()]
    geo = {strip_accents(g) for g in GEOGRAPHY_ES} if lang == "es" else set()
    common = {strip_accents(c) for c in COMMON_EXEMPT}
    return all(w in geo or w in common for w in words)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("paths", nargs="+")
    ap.add_argument("--lang", default="es")
    ap.add_argument("--min-count", type=int, default=1)
    args = ap.parse_args()
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    files = []
    for p in args.paths:
        if os.path.isdir(p):
            for root, _, names in os.walk(p):
                files += [os.path.join(root, n) for n in names if n.endswith(".json")]
        else:
            files.append(p)
    # resources.json is the ONE sanctioned home for real external names.
    files = [f for f in files if os.path.basename(f) not in ("resources.json", "_index.json")]

    hits = {}
    for f in files:
        try:
            raw = json.load(io.open(f, encoding="utf-8"))
        except Exception:
            continue
        for path, s in learner_visible(raw):
            for name in candidates(s):
                if exempt(name, args.lang):
                    continue
                rec = hits.setdefault(name, {"count": 0, "where": None})
                rec["count"] += 1
                if rec["where"] is None:
                    rec["where"] = f"{os.path.basename(f)}:{path} {s[:70]!r}"

    once = {k: v for k, v in hits.items() if v["count"] == 1}
    many = {k: v for k, v in hits.items() if v["count"] > 1}

    print(f"{len(files)} files scanned, {len(hits)} distinct proper nouns found\n")
    print(f"--- appearing MORE THAN ONCE ({len(many)}) ---")
    for name, rec in sorted(many.items(), key=lambda kv: -kv[1]["count"]):
        if rec["count"] >= args.min_count:
            print(f"  {rec['count']:>4}x  {name}")
    print(f"\n--- appearing ONCE ({len(once)}), where a real name tends to hide ---")
    for name, rec in sorted(once.items()):
        print(f"        {name}    <- {rec['where']}")
    print("\nThis tool DECIDES NOTHING. Every name above must be confirmed invented, or")
    print("geography, or removed. No mechanical test can tell an invented firm from a real one.")


if __name__ == "__main__":
    main()
