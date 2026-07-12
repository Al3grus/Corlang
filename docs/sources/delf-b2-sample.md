# DELF B2 tout public — official exam structure (mock-exam template, job-proficiency target)

- **Source:** France Éducation international (FEI), the state body that designs and awards the
  DELF. Official *exemples de sujets*, niveau B2. B2 is the job/professional-proficiency bar.
- **URL:** https://www.france-education-international.fr/diplome/delf-tout-public/niveau-b2/exemples-sujets
  (site blocks automated fetches; structure below cross-verified from FEI descriptions and
  delfdalf.fr, 2026-07)
- **Role:** the authoritative template for the app's DELF B2 mock, the course's finish line.

## Structure (4 sections, each /25, total /100)

### 1. Compréhension de l'oral (listening) — ~30 min, /25
Two recorded documents: (a) a short informative extract (interview, bulletin, message) heard
**once**; (b) a longer document (radio broadcast, conference, debate) heard **twice**. QCM +
short answers. Register and speed are authentic/native.

### 2. Compréhension des écrits (reading) — 1 h, /25
Two texts of ~1 page: (a) an **informative** text about France/Francophonie, (b) an
**argumentative** text. Comprehension questions test main idea, author's stance, implied meaning,
and precise detail (MCQ + short answer + justification).

### 3. Production écrite (writing) — 1 h, /25
**Argue a personal position** in a formal register: a contribution to a debate (forum post),
formal/protest letter, or article, **~250 words**. Marked on: respecting the genre & register,
developing a coherent argument, nuance, and grammatical/lexical range.

### 4. Production orale (speaking) — ~20 min + 30 min prep, /25
From a short trigger document, **defend a viewpoint**: (a) a sustained monologue (5–7 min)
presenting and defending an opinion, then (b) an interactive debate with the examiner (10–13 min).

## Pass rule (identical across DELF levels)
Total **≥ 50/100**, with **≥ 5/25 in every section** — below 5 in any section is disqualifying.

## App mock mapping (reuses the existing ExamSpec model)
| DELF B2 section | App implementation |
|---|---|
| Compréhension de l'oral | LISTENING: TTS passages (one heard once, one twice), MCQ + FILL, /25 |
| Compréhension des écrits | READING: informative + argumentative passages, MCQ/FILL with justification |
| Production écrite | WRITING: `OpenPrompt` (argued ~250-word forum post / formal letter) + model + rubric |
| Production orale | SPEAKING: `OpenPrompt` (viewpoint monologue + debate) + rubric |

Scoring uses `ExamRules.delfPassed`: total ≥ 50/100 AND ≥ 5/25 per section. The B2 writing rubric
adds register/argumentation criteria the B1 one doesn't, matching the level jump.
