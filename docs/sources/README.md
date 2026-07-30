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
| `hrlex` | hrLex (Ljubešić), inflectional lexicon of Croatian with hrWaC v2.2 lemma frequencies (CC BY-SA 4.0); v1.2 fetched complete 2026-07-21 via the public megahr/lexicon mirror (v1.3 on CLARIN.SI was unreachable that day); the ranked frequency authority for the hr deck, with an OpenSubtitles 2018 spoken cross-check | [croatian-curriculum.md](croatian-curriculum.md) |

Phase 8b cross-check for Croatian performed 2026-07-21: see
[croatian-curriculum.md](croatian-curriculum.md) for per-key verdicts (all seven existing
Croatian keys are earned; `hrlex` is new and PARTIALLY earned, deck coverage must close
before packs may cite it). `hrlex` must also be added to
`ContentValidationTest.knownSourceKeys` before any content cites it.

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

### Portuguese, European (pt) — target DEPLE B1 (CAPLE); DIPLE B2 hidden 2026-07-20 (Portugal
requires only A2 for nationality, so B2 is legacy scope, not the live course target)

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

### Spanish (es) — target DELE B1; A2 is the legal bar

Spain requires **DELE A2** plus the CCSE civics test for nationality by residence (Orden
JUS/1625/2016 art. 10.2, re-verified live 2026-07-29; no 2026 reform raises it). A2 alone is too
thin to be a product, so the course runs to **B1**, the same reasoning already applied to
Portuguese. **The CCSE is out of scope**: it is sat in Spanish but tests Spain's institutions and
society, not language. Corlang teaches **Castilian/peninsular** Spanish, with Latin American
forms noted contrastively and never called wrong — which is exactly the DELE's own policy (A1/A2
input texts are peninsular, B1 input texts span all varieties, and any coherently-followed
Hispanic norm is valid in candidate production).

| Key | Source | Digest |
|-----|--------|--------|
| `jus-1625-2016` | Orden JUS/1625/2016 art. 10, the statutory DELE A2 + CCSE requirement for Spanish nationality by residence (BOE consolidated text) | [es-exams.md](es-exams.md) |
| `dele-a1` | Instituto Cervantes, *Guía del examen DELE A1*: 4 pruebas, 25 points each, grouped pass rule (30/50 in each of Grupo 1 and Grupo 2) | [es-exams.md](es-exams.md) |
| `dele-a2` | Instituto Cervantes, *Guía del examen DELE A2*: 4 pruebas, task inventory and the same grouped pass rule; A2 is the level the nationality procedure requires | [es-exams.md](es-exams.md) |
| `dele-b1` | Instituto Cervantes, *Guía del examen DELE B1*: 5+5 tareas receptive (30 items each), 2 writing tasks, 4 speaking tasks, grouped pass rule; the course's finish line | [es-exams.md](es-exams.md) |
| `pcic` | Instituto Cervantes, *Plan curricular / Niveles de referencia para el español* (NRE): the 13 per-level inventories (grammar, functions, nociones generales/específicas...) that the DELE guides themselves name as the exam's content repertoire | [es-exams.md](es-exams.md) |
| `freq-es` | OpenSubtitles-2018 Spanish frequency list (Hermit Dave, *FrequencyWords*), 50,000 ranked forms, fetched complete 2026-07-29; ordering authority only (subtitle register, mixed varieties) | [es-exams.md](es-exams.md) |

Provenance status (2026-07-29, Phases 0 and 1): `jus-1625-2016`, `dele-a1`, `dele-a2` and
`dele-b1` are EARNED (all four documents fetched complete, raw extracts in `raw/`). **`pcic` is
EARNED for grammar and the topic sequence** (both `gramatica` inventory pages fetched,
machine-split by level into `raw/pcic-gramatica-*-split.txt`, and the 250-lesson sequence derived
from that split) but **NOT EARNED for vocabulary banding** (updated 2026-07-30, Phase 8b complete). The
cross-check has now been RUN, so the "records a coverage figure" condition is discharged: all four
`nociones` inventory pages were fetched live on 2026-07-30, parsed per level (each noción is a
two-column A1/A2 or B1/B2 table, so a true per-level split is available), and 7,093 inventory items
were diffed against the deck in both directions. Coverage after the Phase 8b remediation:
específicas A1 60.4% exact / 78.5% partial, A2 46.8% / 67.1%, B1 27.7% / 46.7%; generales A1 51.7%,
A2 35.8%, B1 27.0%. Every A1 and A2 noción with extractable content is now populated; the four
remaining A1 zeros are collocation-only entries with no headword to author.
**The citation is nevertheless DECLINED, on provenance rather than coverage grounds.** A
`sources: ["pcic"]` on a vocab pack claims the words were selected from that inventory. They were
not: the deck was built from `freq-es` plus thematic need, and 124 words were retrofitted into it
*because* the cross-check found them missing. Citing the syllabus we failed against, on the
strength of having then patched it, is the same overclaim that had to be stripped from 431 German
citations (`goethe-wortliste`). The cross-check's value was finding the gap (registry S20), not
licensing a key. All 20 vocab packs therefore keep `sources: ["freq-es"]`. Re-measure any time with
`pcic_crosscheck.py` (offline, one command). **`freq-es` is PARTIALLY EARNED**:
usable for deck ordering, never for level banding or for deciding which of two variant forms the
course teaches. Phase 8b tested even the ordering claim and it does not fully hold: the words the
cross-check found MISSING included `freq-es` ranks 50, 90, 95, 122, 129, 134, 136, 166, 184, 206,
243, 244 and 325, so the deck was plainly not assembled by walking the frequency list, and the 124
remediation words were appended to their packs rather than inserted at their rank. `freq-es`
therefore stays PARTIALLY EARNED and must not be upgraded: it is the ordering *authority* the deck
was built with reference to, not a description of the deck's actual order. RAE CREA (403) and
SUBTLEX-ESP (404) were both unfetchable on 2026-07-29 and are deliberately NOT registered.
