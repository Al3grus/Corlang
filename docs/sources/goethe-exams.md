# Goethe-Institut German exams (A1, A2, B1)

**Verified live: 2026-07-20.** Exam formats and citizenship requirements change; re-verify
before authoring any further German level or before launch.

Source: Goethe-Institut official exam pages and the published *Prüfungsziele,
Testbeschreibung* and *Durchführungsbestimmungen* documents at `goethe.de`, plus telc's
official exam description for its German certificates.

Corlang references these formats; it never reproduces exam material. Mock exams mirror the
STRUCTURE and pass rules only, with all texts, items and prompts written fresh.

---

## Why learners sit these exams

German citizenship (§ 10 StAG) and the settlement permit (Niederlassungserlaubnis) require
German at **B1**. Both the Goethe-Zertifikat and telc certificates are accepted by German
authorities for these purposes; Austria's ÖSD is a third accepted family. This is the legal
driver that puts B1 at the end of the Corlang German course.

---

## Goethe-Zertifikat A1: Start Deutsch 1

Four parts, taken together (not modular): **Hören, Lesen, Schreiben, Sprechen**.

Tests the ability to ask for and give simple information on familiar everyday topics (food and
drink, family, shopping), to introduce oneself and answer basic personal questions, and to
follow short simple exchanges when spoken slowly and clearly.

The speaking part is conducted in a group: candidates draw topic cards and take turns asking
and answering one question each, over two topic rounds.

**Pass: 60% overall.**

## Goethe-Zertifikat A2

Four parts: **Lesen (~30 min), Hören (~30 min), Schreiben (30 min), Sprechen (~15 min)**.
Not modular; the parts are taken as one exam and graded together.

Marked out of **100 points, 25 per part**. **Pass: 60 of 100 overall.**

## Goethe-Zertifikat B1

Four parts: **Lesen (65 min), Hören (40 min), Schreiben (60 min), Sprechen (~15 min, in
pairs)**. Around 180 minutes in total.

**Modular**: each part can be sat separately, on different days, and only a failed module needs
retaking. **Pass: 60% in EVERY module** (not an average), which is the pass rule Corlang's
B1 mock applies.

## telc Deutsch B1 (Zertifikat Deutsch)

The main alternative at B1, equally accepted for citizenship. **Not modular**: a written test
of about 150 minutes (reading, language structures, listening, writing) followed by an oral
exam of about 15 minutes, in pairs or small groups. **Pass: at least 60% in the written part
and 60% in the oral part independently**, the two are not averaged together.

Corlang's B1 mock follows the Goethe modular rule, since it is the stricter of the two and a
learner who clears it also clears telc's threshold.

---

## Vocabulary reference

The Goethe-Institut publishes official word lists (*Wortliste*) for A1, A2 and B1, with example
sentences showing typical usage. Approximate sizes: **A1 ~650 entries, A2 ~800 to 1000**, with
B1 substantially larger. These define the lexical expectation at each level and are the anchor
for the Corlang German deck's level banding. The lists are reference inventories, not teaching
material; Corlang authors its own sentences and never copies entries.

---

## Wortliste cross-check (Phase 8b)

**Date: 2026-07-20.** This is the check that the `goethe-wortliste` source key (cited by all
22 packs in `app/src/main/assets/content/de/vocab/`) had claimed without ever being performed
(error registry row C16).

### Fetch provenance

- **DWDS mirrors** (BBAW, state academy) of the official Goethe inventories, fetched complete
  on 2026-07-20: `dwds.de/lemma/wortschatz-goethe-zertifikat/A1`, `/A2`, `/B1`. Each is a
  single page, no pagination; full A to Z letter coverage confirmed, and the parsed letter M
  section was cross-verified item by item against the live render. Level-new lemma counts:
  **A1 833, A2 611, B1 1840** (cumulative inventory 3284).
- **Official Goethe PDFs**, all fetched complete and text-extracted the same day:
  `A1_SD1_Wortliste_02.pdf` (29 pages), `Goethe-Zertifikat_A2_Wortliste.pdf` (32 pages),
  `Goethe-Zertifikat_B1_Wortliste.pdf` (104 pages), from `goethe.de/pro/relaunch/prf/de/`.
- **DWDS vs PDF validation**: 97.2 percent of DWDS A1 lemmas appear verbatim in the A1 PDF
  text (23 exceptions, all spelling variants, symbols such as `m²`, or DWDS expansions of PDF
  stem entries like `jed-`); A2 cumulative 95.1 percent (71 of 1443, includes older A1 items
  the 2016 A2 PDF dropped); B1 cumulative 96.8 percent (103 of 3268, mostly Austrian and
  Swiss variants). Known mirror quirks: DWDS lists article and pronoun paradigm forms as
  lemmas, and places a few PDF A1 items at A2 (etwas, einmal, Rad, hin). None of this
  materially moves the numbers below.

### Coverage: official lemmas absent from the deck (cumulative deck levels)

Matching is strict on the lemma after stripping articles, reflexive `sich`, and parentheses,
case-insensitive; multiword deck entries also credit their content tokens.

| Official list | Lemmas | Covered by deck | Missing | Of missing: in deck at higher level | Absent from deck entirely |
|---|---|---|---|---|---|
| A1 (vs our A0+A1) | 833 | 458 (55.0%) | 375 (45.0%) | 113 | 262 |
| A2 new (vs our A0..A2) | 611 | 315 (51.6%) | 296 (48.4%) | 50 | 246 |
| B1 new (vs our A0..B1) | 1840 | 749 (40.7%) | 1091 (59.3%) | n/a | 1091 |
| Whole A1..B1 inventory | 3284 | 1522 (46.3%) | 1762 (53.7%) | | |

The missing sets include some DWDS paradigm noise (der, die, das, dich, ihm; unit
abbreviations kg, km, m²) that the deck reasonably teaches through grammar rather than vocab
cards, but the large majority are genuine content words. Spot checks confirmed the deck has
no entry at any level for, among others: Frage, Antwort, Problem, gern, Hilfe, Farbe, and
every basic color adjective.

**Top missing, A1** (frequency-first judgement): die Frage, die Antwort, das Problem, gern,
nur, alles, gleich, gerade, später, zurück, beide, ein bisschen, bekommen, beginnen,
aussehen, danken, fehlen, geboren, der Anfang, das Ende, das Wort, der Brief, das Bild, die
Zeitung, das Telefon, die Nummer, die Hilfe, die Farbe plus all color adjectives (rot, blau,
grün, gelb, braun, grau, schwarz, weiß), der Herr, Deutschland. Also absent: the tens
sechzig through neunzig and zweihundert/einhundert.

**Top missing, A2**: eigentlich, doch (particle), einmal, ganz, egal, erst, genug, selbst,
sonst, dabei, dafür, gegenüber, verschieden, die Meinung, die Idee, das Interesse, die
Geschichte, die Sache, das Ding, die Seite, die Zahl, das Ziel, der Wunsch, die Lust, toll,
echt, reden, nennen, sterben, erreichen.

**Top missing, B1**: schauen, geschehen, bieten, reichen, heben, werfen, fangen, brechen,
schweigen, erleben, akzeptieren, lösen, retten, kämpfen, gemeinsam, bestimmt, eher, nun, je,
mehrere, gar, längst, insgesamt, persönlich, allgemein, die Luft, die Erde, die Kraft, der
Tod, die Gefahr. Honorable mentions: die Stimme, die Form, der Krieg, der Mut.

### Level fit: deck words not in the official list for their level

| Deck level | Words | Within official list for level (cumulative) | Beyond it | Of beyond: official at higher level | Not in any Goethe A1..B1 list |
|---|---|---|---|---|---|
| A0 (vs A1 list) | 120 | 113 (94.2%) | 7 | 0 | 7 (greeting phrases, du, null, die Nacht) |
| A1 (vs A1 list) | 540 | 353 (65.4%) | 187 (34.6%) | 170 | 17 |
| A2 (vs A1+A2) | 1023 | 290 (28.3%) | 733 (71.7%) | 356 | 377 |
| B1 (vs full inventory) | 1230 | 524 (42.6%) | 706 (57.4%) | n/a | 706 |

The A1 overreach is mostly benign (feminine profession forms, body parts, months: items the
official lists place later or fold into stems). The A2 and B1 packs are a different story:
over a third of A2 content and a majority of B1 content (systematic workplace, civic,
climate, and abstract vocabulary) is outside the official Goethe inventory entirely. That may
be defensible pedagogy for the near-native goal, but it is not "anchored on the Goethe
Wortliste".

### Verdict

**The `goethe-wortliste` source key remains UNEARNED for the deck's level banding.** The deck
covers only about half of each official list (55% of A1, 52% of new A2, 41% of new B1, 46%
of the full B1 inventory needed for the citizenship-driven exam), and most A2/B1-tagged
content is outside the official inventory. The A0 pack alone is consistent with the A1 list
(94% fit). Until a follow-up authoring pass closes the missing-lemma gaps (priority: the 262
A1 absences), the key should either be removed from the packs or downgraded to a
"level-inspired-by" note. The full missing and beyond-list word lists are reproducible from
the sources and matching rules described above.

### Rebalance and re-check (2026-07-20, same day)

The gap found above was closed by a SWAP, not an append, because the deck is capacity-bound:
285 lessons unlock `deck[0 .. 2850]`, and the deck already held 2913 words, so 63 were already
unreachable and any addition would have stranded more.

477 missing official lemmas were authored (232 from the A1 list, 245 from the A2 list) and 540
words were removed, every one of them B1-tagged and on NO official Goethe list. Nouns took the
brunt (360), since a narrow topic noun carries far less weight per card than a verb or a
connective; no adverb, connective or discourse phrase was removed at all. The removal manifest
is kept in the build scratchpad.

Coverage of the official inventory, before and after:

| Level | Before | After |
|---|---|---|
| A1 | 55.0% | **81.9%** (682 of 833) |
| A2 | 51.6% | **89.2%** (545 of 611) |
| B1 | 40.7% | 41.0% (755 of 1840) |

Deck size is now exactly 2850, so every entry is reachable within the course.

**Verdict, revised.** `goethe-wortliste` is EARNED for the A1 and A2 packs and the key has been
restored to them: those levels were diffed against the official lists and the gap was closed to
82% and 89%, with the residue being spelling variants, symbols and abbreviations. It remains
UNEARNED for the B1 packs and is deliberately absent there, since B1 coverage is 41% and the
official B1 inventory (1840 lemmas) is larger than this course's entire remaining capacity.
Raising B1 coverage would require widening the pipe with more lessons, not more words.
