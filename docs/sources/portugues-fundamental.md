# Português Fundamental + frequency cross-check (digest)

## Português Fundamental (CLUL, 1984) — the core-vocabulary anchor

**Source:** Centro de Linguística da Universidade de Lisboa (CLUL). Verified live 2026-07 at
clul.ulisboa.pt/en/recurso/corpus-portugues-fundamental.

The national core-vocabulary project for **European Portuguese** (directly analogous to
*Le Français fondamental*):

- **Frequency corpus:** 1,800 recordings (~500 hours, ~700k words) of spontaneous SPOKEN
  Portuguese collected 1970–74 across continental Portugal and the islands, all ages and
  social/professional backgrounds → 25,107 distinct word forms, lemmatized; lemmas with
  frequency ≥ 40 form the **Vocabulário de Frequência**.
- **Availability vocabulary:** thematic surveys in every district capital across **30 themes**
  (human body, health, travel, professions, art, animals, plants, politics, work…) capturing
  essential low-frequency words.
- **Result: the Vocabulário Básico do Português — 2,217 words** (published 1984).

**Use in Corlang:** the deck-size and theme ledger anchor. The pt deck (~2,500–2,900 words
A1→B2) covers the Básico core plus exam-topic vocabulary from the Referencial Camões themes;
the 30 availability themes seed the pack structure.

## Frequency cross-check

`freq-pt` (*A Frequency Dictionary of Portuguese*, Davies & Preto-Bay, Routledge, 5,000 lemmas,
corpus-based) was **RETIRED 2026-08-04**: a commercial book, never fetched and not fetchable,
the exact overclaim class already retired for French (`freq-fr`) and refused for Spanish
(`pcic` vocabulary). It was cited on 12 packs with no coverage check ever actually performed;
removed from all of them and from `ContentValidationTest.knownSourceKeys`. No live, fetchable
European-Portuguese frequency corpus with an open licence was found to replace it (unlike
French, which had Lexique 3.83) — restore only if one is found and a real coverage diff is run,
per the `freq-fr` → `lexique383` precedent.

Where European and Brazilian variants differ, the **European form is authoritative** for
Corlang (autocarro not ônibus, telemóvel not celular, pequeno-almoço not café da manhã,
comboio not trem, casa de banho not banheiro…). A Brazilianism blocklist is enforced
mechanically in the content pipeline, independent of this citation.
