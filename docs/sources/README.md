# Content source registry

Every piece of curriculum content in `app/src/main/assets/content/` must be anchored to a
published official source. This directory holds one **digest** per source: what the source is,
where it lives, when it was fetched, and the extracted specification the app content is built
from. Raw downloads live in `raw/`.

**Provenance rule:** every content unit that carries pedagogy (vocab pack, grammar topic,
quiz, exam spec, level definition) has a non-empty `sources` array whose entries are keys from
the table below. `ContentValidationTest` enforces this mechanically. If content can't cite a
source key, it doesn't ship.

| Key | Source | Digest |
|-----|--------|--------|
| `asoo` | ASOO (Agencija za strukovno obrazovanje i obrazovanje odraslih), *Nastavni plan i program (kurikul): Hrvatski jezik za strance, opći jezik, stupnjevi A1–C2*, the official Croatian state curriculum for Croatian as a foreign language | [asoo-curriculum.md](asoo-curriculum.md) |
| `nn-6-2021` | Narodne novine 6/2021, Pravilnik on proving Croatian language & Latin script knowledge, culture and social order in **citizenship** procedures | [nn-exam-regulations.md](nn-exam-regulations.md) |
| `nn-100-2021` | Narodne novine 100/2021, Pravilnik on the Croatian language & Latin script exam for **long-term residence** (defines the 5-section B1 exam and its pass rule) | [nn-exam-regulations.md](nn-exam-regulations.md) |
| `croaticum-syllabus` | Croaticum (FFZG, University of Zagreb), course ladder and per-level syllabi; administers official language tests | [croaticum-syllabus.md](croaticum-syllabus.md) |
| `croaticum-b1-sample` | Croaticum, *Primjeri zadataka za provjeru znanja* (official sample exam tasks; template for the mock exam) | [croaticum-b1-sample.md](croaticum-b1-sample.md) |
| `cefr-grid` | Council of Europe, CEFR self-assessment grid (Table 2), official can-do descriptors per skill per level | [cefr-grid.md](cefr-grid.md) |
| `ffzg-ecourse` | University of Zagreb free e-courses a1.ffzg.unizg.hr and a2.ffzg.unizg.hr (80 units each) | referenced in plan content; overview in [croaticum-syllabus.md](croaticum-syllabus.md) |

### French (fr) — target DELF B2, milestone DELF B1

| Key | Source | Digest |
|-----|--------|--------|
| `cecrl` | Council of Europe, *Cadre européen commun de référence pour les langues*; official French can-do descriptors (FEI) | [cecrl-grid.md](cecrl-grid.md) |
| `delf-b1-sample` | France Éducation international, DELF B1 *exemples de sujets* (4-section exam template + pass rule) | [delf-b1-sample.md](delf-b1-sample.md) |
| `delf-b2-sample` | France Éducation international, DELF B2 *exemples de sujets* (the job-proficiency exam) | [delf-b2-sample.md](delf-b2-sample.md) |
| `referentiel-fr` | Beacco et al., *Référentiels : Niveau A1/A2/B1/B2 pour le français* (Didier + Council of Europe), the per-level content inventory | [referentiel-fr.md](referentiel-fr.md) |
| `francais-fondamental` | *Le Français fondamental* (Ministry of Education core-French inventory) | [francais-fondamental.md](francais-fondamental.md) |
| `freq-fr` | *A Frequency Dictionary of French* (Lonsdale & Le Bras, Routledge), corpus frequency cross-check | [francais-fondamental.md](francais-fondamental.md) |

Keys may be added by adding a row here and a digest file, and extending the known-keys set in
`ContentValidationTest`.
