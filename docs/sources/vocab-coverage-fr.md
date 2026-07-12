# French vocabulary coverage ledger (the contract against drift)

Themes × level with target word counts, derived from `referentiel-fr.md` and
`francais-fondamental.md`. Batches are authored against this ledger and cross-checked against a
named frequency list; the ledger is what keeps the deck complete and duplicate-free. Order in
`content/fr/vocab/_index.json` IS the SRS introduction order (A1 → B2).

## Targets (words introduced, cumulative ≈ 3,200 through B2)

| Level | Target | Themes |
|-------|--------|--------|
| **A1** | ≈ 600 | greetings & politeness, people & family, numbers & time, food & drink, home & everyday objects, places & directions, body & health basics, common verbs, common adjectives, colours, weather, question/function words |
| **A2** | ≈ 800 | daily routine & work, shopping & money, travel & transport, town & services, leisure & hobbies, past-narration verbs, feelings, clothing, nature, more adjectives & adverbs, connectors |
| **B1** | ≈ 900 | opinion & debate, media & news, society & environment, education & study, health & body (extended), technology, abstract-but-common nouns, opinion/reporting verbs, richer adverbs & connectors |
| **B2** | ≈ 900 | work & economy, politics & society, argumentation lexis, culture & arts, science & environment (extended), register-marked synonyms, nuance verbs, formal connectors & idiom |

## Authoring rules (enforced by QA + ContentValidationTest)
- Target French text in the `hr` field (the schema's target-language slot); id = the word,
  NFC-normalized and unique (disambiguate homographs with a `-hint` suffix, e.g. `livre-book`).
- `en` gloss; `pos` with **gender on every noun** (`n. m.` / `n. f.`) and verb group where useful.
- `example` sentence (target + gloss) mandatory A1/A2, encouraged B1/B2.
- Cross-check each batch against a named frequency list; ≥10% random spot-check.
- Freeze ids as each batch lands (`src/test/resources/frozen-word-ids-fr.txt`).
- `sources`: `francais-fondamental` + `freq-fr` (core), `referentiel-fr` (theme provenance).

## Batch plan (files in _index.json order)
`00-a1-core` (survival core, this batch) → `01-a1-*` … → `NN-b2-*`. Each ~100–150 words.
