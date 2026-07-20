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
| `inventaire-cecrl` | Eaquals & CIEP (now France Éducation international), *Inventaire linguistique des contenus clés des niveaux du CECRL* (2015): per-level A1–C1 inventories of functions, discourse, sociocultural content, grammar and vocabulary themes for French; fetched complete 2026-07-20 | [french-referentiel.md](french-referentiel.md) |
| `lexique383` | *Lexique 3.83* (New, Pallier et al., lexique.org): 46,947-lemma French frequency database (film + book corpora), the ranked frequency authority for the deck; fetched complete 2026-07-20 | [french-referentiel.md](french-referentiel.md) |
| `decret-2025-648` | Décret n° 2025-648 (15 July 2025) + arrêté du 22 décembre 2025: French at **B2** (written and oral) required for naturalisation by decree and by marriage from 1 January 2026; verified via service-public.gouv.fr F11926 | [french-referentiel.md](french-referentiel.md) |

Phase 8b cross-check for French performed 2026-07-20: see
[french-referentiel.md](french-referentiel.md) for per-key verdicts (`referentiel-fr`,
`francais-fondamental` and `freq-fr` are currently UNEARNED on content; `delf-b2-sample` and
the three new keys above are earned). The three new keys must also be added to
`ContentValidationTest.knownSourceKeys` before any content cites them.

Keys may be added by adding a row here and a digest file, and extending the known-keys set in
`ContentValidationTest`.

### German (de) — target Goethe-Zertifikat B1 (citizenship / settlement level)

German at **B1** is the legal bar for naturalisation (§ 10 StAG) and the settlement permit, and
is why the Corlang German course ends there. Standard German is taught; Austrian and Swiss
divergences are noted contrastively, never mixed in.

| Key | Source | Digest |
|-----|--------|--------|
| `goethe-a1` | Goethe-Institut, *Goethe-Zertifikat A1: Start Deutsch 1*, Prüfungsziele/Testbeschreibung (4-part exam, 60% overall) | [goethe-exams.md](goethe-exams.md) |
| `goethe-a2` | Goethe-Institut, *Goethe-Zertifikat A2*, exam description (4 parts, 25 points each, 60/100 to pass) | [goethe-exams.md](goethe-exams.md) |
| `goethe-b1` | Goethe-Institut, *Goethe-Zertifikat B1*, exam description (modular; 60% required in EVERY module) | [goethe-exams.md](goethe-exams.md) |
| `telc-b1` | telc, *Zertifikat Deutsch / telc Deutsch B1* exam description (non-modular alternative accepted for citizenship) | [goethe-exams.md](goethe-exams.md) |
| `goethe-wortliste` | Goethe-Institut official *Wortliste* A1/A2/B1, the per-level lexical inventory anchoring the deck's level banding | [goethe-exams.md](goethe-exams.md) |
| `stag-10` | Staatsangehörigkeitsgesetz § 10, the statutory B1 language requirement for naturalisation | [goethe-exams.md](goethe-exams.md) |

### Portuguese, European (pt) — target DIPLE B2 (CAPLE), milestone DEPLE B1

Corlang teaches **Português europeu (pt-PT)** exclusively — most platforms only offer Brazilian
Portuguese. European lexis/grammar (tu, ênclise, estar a + infinitivo, pequeno-almoço…) is an
authoring REQUIREMENT enforced by a Brazilianism blocklist in the content pipeline.

| Key | Source | Digest |
|-----|--------|--------|
| `qecr` | Council of Europe, *Quadro Europeu Comum de Referência para as Línguas* (the CEFR in Portuguese); official can-do descriptors | [cefr-grid.md](cefr-grid.md) |
| `caple` | CAPLE (Centro de Avaliação e Certificação de Português Língua Estrangeira, Univ. Lisbon / Instituto Camões): exam ladder CIPLE A2 → DEPLE B1 → DIPLE B2 → DAPLE C1, component structure and the ≥55% (Suficiente) pass rule | [caple.md](caple.md) |
| `deple-sample` | CAPLE, DEPLE (B1) *modelo de exame* — 4-component template for the B1 mock | [caple.md](caple.md) |
| `diple-sample` | CAPLE, DIPLE (B2) *modelo de exame* — 4-component template for the B2 mock | [caple.md](caple.md) |
| `referencial-camoes` | Instituto Camões, *Referencial Camões PLE* (per-level content inventories A1–C2 for Portuguese as a foreign language) | [referencial-camoes.md](referencial-camoes.md) |
| `portugues-fundamental` | CLUL (Centro de Linguística da Univ. de Lisboa), *Português Fundamental* (1984): the official 2,217-word basic vocabulary from a 700k-word SPOKEN European-Portuguese corpus + 30-theme availability vocabulary | [portugues-fundamental.md](portugues-fundamental.md) |
| `freq-pt` | *A Frequency Dictionary of Portuguese* (Davies & Preto-Bay, Routledge), corpus frequency cross-check (European forms preferred where variants differ) | [portugues-fundamental.md](portugues-fundamental.md) |

### Italian (it) — target B1 (citizenship level)

Italian at **B1** is the legal bar for citizenship by residence (art. 9, L. 91/1992) and by
marriage (art. 5), introduced by L. 132/2018 in force from 4 December 2018, and is why the Corlang
Italian course ends there. Two bodies dominate and their pass rules DIVERGE: CILS is modular per
skill, CELI sums parts into a written block plus an oral block. Corlang's B1 mock applies the
stricter CILS rule.

| Key | Source | Digest |
|-----|--------|--------|
| `cils-a1` | Università per Stranieri di Siena, CILS A1 exam (5 abilities, 12 points each, 60 total; 7 required in EVERY ability, 35 total) | [italian-exams.md](italian-exams.md) |
| `cils-a2` | Università per Stranieri di Siena, CILS A2 exam, plus the shortened A2 Integrazione variant (4 abilities, 48 total, 7 each) | [italian-exams.md](italian-exams.md) |
| `cils-b1` | Università per Stranieri di Siena, CILS UNO-B1, *Criteri di attribuzione dei punteggi* (modular; 11 of 20 required in EVERY one of 5 abilities, 55 of 100; capitalizzazione 18 months) | [italian-exams.md](italian-exams.md) |
| `celi-b1` | Università per Stranieri di Perugia (CVCL), CELI 2 = B1, *Criteri di valutazione e punteggi* (global within blocks: 72/120 written AND 22/40 oral, 94/160 overall) + CELI level-to-CEFR mapping | [italian-exams.md](italian-exams.md) |
| `b1-cittadinanza` | The shortened citizenship-only B1: CILS B1 Cittadinanza (4 sections x 12 = 48, 7 each, 28 total, NO capitalizzazione) and CELI 2 i (B1) Cittadinanza; **not valid for work or study** | [italian-exams.md](italian-exams.md) |
| `cliq` | Associazione CLIQ (Certificazione Lingua Italiana di Qualità), the four bodies accepted by the Ministero dell'Interno (CILS, CELI, cert.it, PLIDA) per D.M. 7 December 2021, plus art. 9.1 L. 91/1992 and Corte cost. sent. 25/2025 | [italian-exams.md](italian-exams.md) |
| `freq-it` | *Nuovo vocabolario di base della lingua italiana* (De Mauro & Chiari, 2016): ~7,000 entries in fondamentale / alto uso / alta disponibilità bands, plus *Profilo della lingua italiana* (Spinelli & Parizzi, Council of Europe RLD for Italian) | [italian-exams.md](italian-exams.md) |
