# CECRL — CEFR self-assessment grid & level descriptors (French)

- **Source:** Council of Europe, *Cadre européen commun de référence pour les langues* (CECRL) —
  the CEFR in French. The self-assessment grid (5 skills × 6 levels) and the level descriptors.
  Official French descriptors are published by France Éducation international.
- **URLs:** coe.int/fr/web/common-european-framework-reference-languages ·
  FEI B2 descriptors: https://www.france-education-international.fr/document/cecrldescripteursb2 ·
  éduscol: eduscol.education.gouv.fr/6762/cadre-europeen-commun-de-reference-pour-les-langues-cecrl
- **Role:** the verbatim can-do descriptors per skill per level for `content/fr/levels.json`
  (`skills[].descriptors`), the same framework already used for Croatian (see `cefr-grid.md`).

## The five skills (grid rows)
Écouter (listening), Lire (reading), Prendre part à une conversation (spoken interaction),
S'exprimer oralement en continu (spoken production), Écrire (writing).

## Level thresholds relevant here
- **A1** Introductif / découverte · **A2** Intermédiaire / de survie · **B1** Niveau seuil
  (independent user threshold — the certified milestone) · **B2** Avancé / indépendant
  (the job/professional-proficiency target: can interact with fluency and spontaneity, argue a
  viewpoint, understand complex/technical text in one's field).

`levels.json` carries the official French descriptor text per skill per level; the digest is the
pointer, the descriptors themselves are lifted verbatim from the CECRL/FEI source into content.
