# Croaticum: Primjeri zadataka za provjeru znanja (official sample exam tasks)

- **Source:** Croaticum, Centar za hrvatski kao drugi i strani jezik, FFZG, University of
  Zagreb. Official sample tasks for the language proficiency test ("Testiranja").
- **URL:** https://croaticum.ffzg.unizg.hr/wp-content/uploads/2022/06/Primjeri-zadataka-za-provjeru-znanja.pdf
  (linked from https://croaticum.ffzg.unizg.hr/?page_id=1062)
- **Fetched:** 2026-07-09 (raw: `raw/croaticum-primjeri-zadataka.pdf`, text:
  `raw/croaticum-primjeri-extracted.txt`, includes full answer key)
- **Role:** the authoritative template for the app's mock exam: exact section order, task
  types, question counts, and point weights.

## Structure of the sample (with points)

Written total: **42 points** (6 + 6 + 7 + 12 + 8 + 3) + writing tasks + oral part.

### I. RAZUMIJEVANJE SLUŠANIH TEKSTOVA (listening), 12 pts
- **A) 6 pts:** six short spoken sentences (traffic-report style), each heard **twice**; open
  short-answer questions ("Koliko je dugačka kolona…?" → "Oko 3 kilometra.").
- **B) 6 pts:** one longer spoken text (event/festival announcement), heard **twice**; six
  DA/NE true-false statements.

### II. RAZUMIJEVANJE ČITANIH TEKSTOVA (reading), 19 pts
- **A) 7 pts:** biography-style text (~150 words, e.g. Marija Jurić Zagorka); seven open
  questions answered from the text.
- **B) 12 pts:** gap-fill text (~150 words, e.g. health/allergy article); 12 words provided in
  a scrambled bank, each inserted in the correct slot (tests vocabulary + case forms in
  context: e.g. *travnju* locative, *očiju* genitive plural, superlatives).

### III. POZNAVANJE GRAMATIČKIH STRUKTURA (grammar), 11 pts
- **A) 8 pts:** choose the correct form from four options and write it in. Sampled points:
  genitive plural after quantities (*4 i pol milijuna stanovnika*), instrumental of time
  (*četvrtkom*), futur I orthography (*ići ćemo*), conditional 1st person plural (*bismo*),
  preposition choice (*među* + instrumental), causal conjunction (*jer*), stressed pronoun
  after preposition (*o njemu*), comparative adverb (*ljepše*).
- **B) 3 pts:** choose the correct **prefixed/aspect verb** in context (*napisala* vs
  zapisala/prepisala/opisala; *zahvalit ćemo*; *pogledali*), the ASOO B1 "glagoli u
  kontekstu" material exactly.

### IV. PISANJE TEKSTA (writing), pass/fail per NN 100/2021
- Two tasks, **7–8 sentences each**: (1) describe a picture (people, actions, appearance,
  setting, what they discuss…), (2) write your own CV/biography (životopis).

### V. USMENO SPORAZUMIJEVANJE (speaking), oral, pass/fail
- Not included in the written sample; conducted live per NN 100/2021 Article 4(4).

## App mock-exam mapping

| Sample section | App implementation |
|---|---|
| I.A short sentences ×2 | TTS-read sentences (`audioText`), FILL short answers |
| I.B longer text, DA/NE | TTS passage (`audioOnly`), MCQ DA/NE |
| II.A text + open questions | reading passage + FILL/MCQ |
| II.B word-bank gap fill | passage + per-gap MCQ from the word bank (strict diacritics) |
| III.A form choice | MCQ, `strictDiacritics: true` |
| III.B aspect/prefix verbs | MCQ from verb series |
| IV writing | `OpenPrompt` with model answer + self-check rubric → pass/fail |
| V speaking | `OpenPrompt` speaking prompts + rubric → self-graded pass/fail |

Scoring: apply NN 100/2021, ≥60% on sections I–III separately; IV and V pass/fail.
