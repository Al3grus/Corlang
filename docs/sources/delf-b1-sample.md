# DELF B1 tout public — official exam structure (mock-exam template)

- **Source:** France Éducation international (FEI, formerly CIEP), the state body that designs and
  awards the DELF. Official *exemples de sujets*, niveau B1.
- **URL:** https://www.france-education-international.fr/diplome/delf-tout-public/niveau-b1/exemples-sujets
  (the site blocks automated fetches; structure below cross-verified from FEI descriptions and
  delfdalf.fr, 2026-07)
- **Role:** the authoritative template for the app's `hr`-style DELF B1 mock: section order,
  task types, timings, and the pass rule.

## Structure (4 sections, each /25, total /100)

### 1. Compréhension de l'oral (listening) — ~25 min, /25
Several short recorded documents (announcements, interviews, radio extracts), each heard once or
twice, answered by **QCM** (multiple choice) and short answers. Difficulty rises across documents.

### 2. Compréhension des écrits (reading) — ~45 min, /25
Two exercises: (a) reading a document to accomplish a task (selecting from options against
criteria), (b) reading an informative/argumentative text (~300–400 words) and answering
comprehension questions (MCQ + short answer).

### 3. Production écrite (writing) — 45 min, /25
One essay expressing a **personal viewpoint** on a general theme (experience, opinion, feelings),
**minimum 160 words**. Marked on a rubric: task fit, coherence, range/accuracy of grammar & lexis.

### 4. Production orale (speaking) — ~15 min + 10 min prep, /25
Three parts in sequence: (1) guided interview (self-presentation), (2) interaction / role-play
(solve a situation with the examiner), (3) short monologue expressing a viewpoint prompted by a
short trigger document.

## Pass rule (identical across DELF levels)
Total **≥ 50/100**, with **≥ 5/25 in every section** — a score below 5 in any single section is
disqualifying. Certificate is for life.

## App mock mapping (reuses the existing ExamSpec model)
| DELF B1 section | App implementation |
|---|---|
| Compréhension de l'oral | LISTENING: TTS passages (`audioOnly` / `audioText`), MCQ + FILL, passPercent scored /25 |
| Compréhension des écrits | READING: passages + MCQ/FILL |
| Production écrite | WRITING: `OpenPrompt` (personal-viewpoint essay ≥160 words) + model answer + rubric |
| Production orale | SPEAKING: `OpenPrompt` prompts (interview / role-play / viewpoint monologue) + rubric |

Scoring uses the new `ExamRules.delfPassed`: total ≥ 50/100 AND ≥ 5/25 per section.
