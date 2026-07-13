# CAPLE — the official European-Portuguese exam system (digest)

**Source:** CAPLE — Centro de Avaliação e Certificação de Português Língua Estrangeira,
Faculdade de Letras, Universidade de Lisboa, under the Instituto Camões umbrella.
Verified live 2026-07 at caple.letras.ulisboa.pt (exam pages /exame/2/ciple, /exame/3/deple,
/exame/4/diple; FAQ /pagina/2/faq; modelos de exame PDFs linked from each exam page).

## The exam ladder

| Exam | CEFR | Role in Corlang |
|------|------|-----------------|
| ACESSO | A1 | on-ramp reference |
| **CIPLE** | **A2** | first certificate; A2 checkpoint (also the level accepted for PT nationality) |
| **DEPLE** | **B1** | the B1 milestone mock |
| **DIPLE** | **B2** | the target: job/academic proficiency mock |
| DAPLE / DUPLE | C1 / C2 | future continuation |

## Component structure

- **CIPLE (A2), 3 components:** Compreensão da Leitura e Produção e Interação Escritas (45%),
  Compreensão do Oral (30%), Produção e Interação Orais (25%).
- **DEPLE (B1), 4 components:** Compreensão da Leitura, Produção e Interação Escritas,
  Compreensão do Oral, Produção e Interação Orais.
- **DIPLE (B2), 4 components:** same four; the oral component is 25% of the total — the four
  components are weighted equally (25% each) in the Corlang mocks.

## Pass rule (verified)

Final classification is a single global percentage across components (no per-component floor
is published):

| Classification | Range |
|---|---|
| Muito Bom | 85–100% |
| Bom | 70–84% |
| **Suficiente (PASS)** | **55–69%** |
| Insuficiente / Mau | < 55% (fail) |

**Corlang implementation:** `ExamRules.caplePassed` = equal-weight average across the four
sections ≥ 55%. Differs from both the Croatian NN rule (per-section floors + pass/fail
sections) and DELF (≥50/100 with a 5/25 floor), so it gets its own function, wired for exam
ids containing `deple`/`diple`.

## Mock templates (from the modelos de exame)

- **Compreensão da Leitura**: authentic short texts (announcements, articles, letters) with
  MCQ / matching items.
- **Produção e Interação Escritas**: guided writing tasks (informal/formal letter or message,
  opinion text at B2) — OpenPrompt with model answer + rubric in Corlang.
- **Compreensão do Oral**: recorded dialogues/announcements → MCQ (TTS `audioText` in Corlang,
  transcripts hidden).
- **Produção e Interação Orais**: monologue + interaction prompts — OpenPrompt self-check.
