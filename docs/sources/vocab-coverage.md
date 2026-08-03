# Vocabulary coverage ledger (target ~2,500 words through B1)

The contract for Phase D vocabulary batches. Themes derive from the ASOO curriculum topic
lists (`asoo-curriculum.md`) and the Croaticum textbook progression (`croaticum-syllabus.md`).
Every batch file cites `sources`; every batch passes independent QA + `ContentValidationTest`
before it counts as done. **Word ids are frozen once shipped** (SRS keys on them).

| File | Level | Themes | Target | Status |
|------|-------|--------|-------:|--------|
| `00-a0-core.json` | A0/A1 | survival, people, numbers, time, calendar, verbs I–II, food, places, adjectives, questions, travel, home, weather, body, work-life, phrases | 303 | ✅ shipped (ids frozen) |
| `01-a1-people-daily.json` | A1 | professions (ASOO: Ljudi i zanimanja), personality & emotions, daily activities verbs III, education & classroom | 176 | ✅ shipped (ids frozen) |
| `02-a1-things-world.json` | A1 | clothing & colors (Odijelo ne čini čovjeka), house & furniture (Gdje živimo), food & drink expanded (Dobar tek), nature & animals | 173 | ✅ shipped (ids frozen) |
| `03-a1-society.json` | A1 | hobbies & sport (Zdravlje i sport), Croatia & nationalities (Informacije o Hrvatskoj), time & frequency expressions, quantities & measures, communication & tech basics | 154 | ✅ shipped (ids frozen) |
| `04-a2-life.json` | A2 | work & professions detail (Tko radi…), health & medicine, services (bank/post/administration), shopping & consumption (Kvaliteta života) | 200 | ✅ shipped (ids frozen) |
| `05-a2-world.json` | A2 | travel & tourism deep (Transport i komunikacija), city & country life, weather & environment, celebrations & customs (Sjećanja, Ime je znak) | 197 | ✅ shipped (ids frozen) |
| `06-a2-verbs-connectors.json` | A2 | verb expansion IV with aspect pairs, reflexives, adverbs, connectors & sentence glue (futur II / kondicional context), relationships & feelings | 183 | ✅ shipped (ids frozen) |
| `07-b1-society.json` | B1 | society & current world (Suvremeni svijet), media & news (Sport, mediji, čitanje), work market & CV (Poželjna zanimanja), values & opinions (Vrijednosti…) | 233 | ✅ shipped (ids frozen) |
| `08-b1-knowledge.json` | B1 | science & inventions (Znanost, otkrića, izumi), art & culture (Umjetnost), ecology (Eko-Hrvatska), food & health discourse (Ono smo što jedemo) | 226 | ✅ shipped (ids frozen) |
| `09-b1-verbs-aspect.json` | B1 | the ASOO B1 "glagoli u kontekstu" series: pisati/gledati/misliti/raditi/zvati/stati/držati/igrati/živjeti/govoriti/pustiti/staviti families with aspect pairs; abstract nouns & word formation; opinion/argument phrases | 146 | ✅ shipped (ids frozen) |
| `10-b1-topup.json` | A2/B1 | extended family & in-laws, children & parenting, character/emotions depth, daily-life gaps, official exam-text lemmas | 421 | ✅ shipped (ids frozen) |
| **Subtotal above** | | | **2,412** | (this table's original 11-file scope) |

**2026-08-04: this table is stale below this line** — the deck grew past it across several later
sessions and the per-file theme descriptions were never backfilled here. Current file list and
counts (all ✅ shipped, ids frozen), verified live against the repo: `11-b1-topup-2.json` (34),
`12-b1-topup-3.json` (120), `hr-a1-core-extra.json` (110), `hr-a2-core-extra.json` (110),
`hr-a2-mid-freq.json` (150), `hr-b1-abstract-extra.json` (100), `hr-b1-core-extra.json` (104),
`hr-b1-mid-freq.json` (150), `hr-b1-precise.json` (150). **Real total: 3,440 words across 19
files**, still inside the research band for B1 (~2,400-3,000 was the original target; the course
grew past its own floor once the 250+ lesson expansions needed more SRS capacity). Themes for the
newer files follow their filenames (mid-frequency top-up, precision/abstract B1 vocabulary,
core-extra top-up per level) rather than the ASOO-topic-per-file granularity of the original 11.

## Batch rules

1. Fields per word: `id` (NFC, lowercase, unique across the whole merged deck; homographs
   disambiguated `grad-city` style), `hr`, `en`, `pos` (e.g. "n. m.", "n. f.", "v. impf.",
   "adj."), `note` (gender surprises, aspect partner, usage), `example` (mandatory A1/A2,
   strongly encouraged B1) with English gloss.
2. Cross-check every batch against the ASOO topic list for its theme and general frequency
   (no rare word before its level; no high-frequency everyday word left behind).
3. Independent QA by a verifier who did not author the batch: diacritics, gloss accuracy,
   example grammar (case endings!), duplicates vs the entire merged deck.
4. `ContentValidationTest` green before a batch is marked shipped here.
