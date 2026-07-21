# Croatian curriculum and frequency cross-check (Phase 8b)

**Date: 2026-07-21.** This is the syllabus cross-check that the seven Croatian source keys
(`asoo`, `croaticum-syllabus`, `croaticum-b1-sample`, `nn-6-2021`, `nn-100-2021`, `cefr-grid`,
`ffzg-ecourse`) had been carrying since launch without a recorded 8b diff. Same shape and same
honesty standard as the German Wortliste check (docs/sources/goethe-exams.md), the Italian
sillabo check (docs/sources/italian-exams.md) and the French referentiel check
(docs/sources/french-referentiel.md). Croatian targets B1, the level required for citizenship
and for long-term residence, examined by an institutional commission with a 60 percent rule.
Checked content: the 13 deck files in `app/src/main/assets/content/hr/vocab/` (2,566 entries:
A0 106, A1 682, A2 852, B1 926; 4 duplicate headwords) and the 250 day titles plus objectives
in `content/hr/plan/` (A0 14, A1 46, A2 90, B1 100). This check also defines the content of
the planned 90-lesson expansion (to 340: A0 15, A1 60, A2 95, B1 170).

Unlike French, the pre-existing Croatian digests turn out to be honest: every raw file cited
on disk is real, was verified byte-identical or content-identical against the live source
today, and the digest inventories match the raw extractions verbatim. What had never been done
is the diff itself. It is below, and it found real gaps.

## The legal driver, re-verified live

- **Citizenship requires B1.** NN 6/2021 (Pravilnik o načinu provjere poznavanja hrvatskog
  jezika i latiničnog pisma, hrvatske kulture i društvenog uređenja u postupcima stjecanja
  hrvatskog državljanstva), Article 2, re-fetched 2026-07-21 from narodne-novine.nn.hr:
  proof of "najmanje B1 stupnja znanja hrvatskog jezika" via a certificate from an authorized
  institution or completed schooling in Croatia. The separate culture and society
  questionnaire (Article 6): 15 questions from a published bank of 110, 60 minutes, pass is
  at least 10 correct, administered at the police administration or a diplomatic mission.
  Exemptions (Article 3) for long-term refugees and pre-1991 residents in return programmes.
  The consolidated view at zakon.hr (database current through NN 53/26) shows NO amendment to
  this pravilnik since 2021.
- **The exam behind the certificate is the NN 100/2021 exam.** Re-fetched 2026-07-21: five
  components (razumijevanje slušanoga teksta, razumijevanje čitanoga teksta, poznavanje
  jezičnih struktura, pisano sporazumijevanje, usmeno sporazumijevanje), the first four
  written, the fifth oral, conducted by a Povjerenstvo (commission) of the authorized
  institution. Pass rule verbatim: "najmanje 60% bodova" in each of listening, reading and
  grammar structures; writing and speaking graded zadovoljio / nije zadovoljio; the exam is
  passed only by passing every section. Formally NN 100/2021 governs the long-term residence
  procedure; the same institutions issue the B1 certificates that NN 6/2021 accepts for
  citizenship. No amendment found through 2026-07-21, and exam centres are actively running
  it in 2026 (a pou.hr enrolment page verified live shows a June 2026 intake at B1).
- The `levels.json` B1 exam object matches these facts exactly (five sections, the 60 percent
  rule, pass in writing and speaking, every section required). No fix needed.
- **One newer adjacent fact, out of current scope but worth recording:** 2025 amendments to
  the Zakon o strancima introduced an A1.1 Croatian requirement in certain foreigner-status
  procedures (secondary sources dated March 2025). This does not touch the B1 citizenship or
  long-term residence bar. Verify directly before any app copy mentions it.

## Fetch provenance and completeness

- **ASOO curriculum: COMPLETE, on disk and live.** The official state curriculum *Hrvatski
  jezik za strance, opći jezik, stupnjevi A1 do C2* was fetched 2026-07-09 as
  `raw/asoo-hrvatski-za-strance.docx` (165,885 bytes) with full text extraction
  `raw/asoo-extracted.txt` (73,694 chars). Re-verified 2026-07-21: the asoo.hr URL returns
  HTTP 200 with Content-Length 165,885, byte-identical to the raw file. The digest's verbatim
  A1, A2 and B1 grammar inventories were spot-checked against the raw extraction today and
  match word for word (the full B1 "glagoli u kontekstu" series was re-read in the raw text).
- **Croaticum syllabus: COMPLETE, re-fetched live 2026-07-21.** The semester-course page
  (?page_id=1106) and the testing page (?page_id=1062) were both fetched today. Today's fetch
  contains MORE per-level grammar detail than the 2026-07-09 digest recorded, and the new
  detail matters: level 2 (B1.1) explicitly lists genitive with numbers, complex numbers,
  cardinal AND ordinal numbers, genitive with dates, i-declension, imperative, vocative,
  declension of adjectives and pronouns, comparison, aspect, first conditional, futur II;
  level 3 (B1.2) lists case syntax, aspect, interrogative and relative pronouns, word order,
  passive participle. The digest should absorb this detail on its next edit.
- **Croaticum B1 sample exam: COMPLETE, on disk and live.** `raw/croaticum-primjeri-zadataka.pdf`
  (392,877 bytes, fetched 2026-07-09 with full text extraction and answer key). Re-verified
  2026-07-21: the live PDF returns HTTP 200 with Content-Length 392,877, byte-identical.
- **NN 6/2021 and NN 100/2021: COMPLETE, re-fetched live 2026-07-21** from
  narodne-novine.nn.hr, key phrases quoted above; amendment search via zakon.hr and web
  search found none for either.
- **CEFR grid: COMPLETE via Europass, coe.int still blocked.** The Europass republication of
  the self-assessment grid was fetched 2026-07-21 and the A1 and B1 listening descriptors
  match cefr-grid.md and the hr levels.json verbatim. coe.int itself returned HTTP 403 today,
  the same block the French check hit on 2026-07-20.
- **FFZG e-courses: LIVE.** a1.ffzg.unizg.hr and a2.ffzg.unizg.hr both returned HTTP 200 on
  2026-07-21. Their 80-unit syllabi were NOT diffed unit by unit; they are used only as
  external resources in resources.json, not as pedagogy provenance.
- **Frequency authority: hrLex, PARTIAL in version, COMPLETE in substance.** The plan was the
  HNK (Hrvatski nacionalni korpus) frequency lists; the HNK čestotni rječnik (Moguš, Bratanić,
  Tadić 1999) turns out to be a print book, and the corpus itself sits behind a NoSketch
  Engine query interface with no downloadable lemma list, so HNK could NOT be used and is not
  claimed. The substitute is **hrLex** (Ljubešić, inflectional lexicon of Croatian, CC BY-SA
  4.0), whose entries carry lemma frequencies from the **hrWaC v2.2** web corpus (about 1.4
  billion tokens), the standard Croatian frequency resource. The current version hrLex 1.3
  could NOT be fetched today: the CLARIN.SI bitstream endpoint accepted connections but
  returned zero bytes on every attempt over roughly 30 minutes (metadata endpoints responded
  normally, so the item exists; the REST API confirms hrLex_v1.3.gz, 54,477,922 bytes, MD5
  e55a21f10bbb4f6c22afe31a65803649). **hrLex 1.2 was fetched COMPLETE** (30,172,873 bytes,
  5.9 million wordform rows, 186,450 lemma and part-of-speech pairs) from the public GitHub
  mirror in the megahr/lexicon repository (the MEGAHR psycholinguistic project, which
  redistributes it under the same CC BY-SA licence). Registered as new key `hrlex`. Re-try
  the CLARIN.SI 1.3 download before launch and re-run the numbers; differences between 1.2
  and 1.3 are incremental corrections, not a re-ranking.
- **Spoken cross-check: COMPLETE.** The OpenSubtitles 2018 Croatian wordform frequency list
  (hermitdave FrequencyWords, 50,000 rows) was fetched complete from GitHub and mapped to
  lemmas through hrLex, giving a spoken-register lemma ranking to balance hrWaC's web and
  news skew. Reported as part of the `hrlex` digest, not a separate key.

## Grammar and function coverage: 250 day titles vs ASOO and Croaticum

Method: the taught-structure inventory was built from the 250 day titles and objectives and
diffed both ways against the ASOO verbatim grammar and topic inventories (A1, A2, B1) and
the Croaticum per-level focus lists fetched today. Suspected gaps were re-checked against day
objectives, not titles alone, before being listed.

**Covered, with margin.** The A2 phase is structurally an implementation of the ASOO A2
curriculum: all ten ASOO A2 topics appear as named days (Zemlje i jezici 61, Kad će taj
vikend 68, Kvaliteta života 75, Planovi, želje, mogućnosti 84, Transport i komunikacija 93,
Odnosi, izgled, budućnost 106, Tko radi, ne boji se gladi 115, Sjećanja 124, Ime je znak 131,
Hrvati u svijetu 138), and every row of the ASOO A2 grammar inventory has a named day: -ski
adjectives 62, possessive adjectives 63, accusative phrase 64, svoj 65, clitic pronouns and
order 69 to 70, (n)uti and doći presents 71, locative phrase 76, aspect 78 to 83, conditional
85 to 87, dative phrase 89 to 90, impersonals 139, -ati to -jem 94, comparison 107 to 110,
futur II with kad/ako/dok 112 to 113, instrumental phrase 128, čuti-type and genitive phrase
118, temporal and possessive and quantity genitive 119 to 121, proširak nouns 127, peći-type
134, foreign nouns 132 to 133, -vati and dio 135. The ASOO B1 inventory is likewise nearly
complete: 11 of the 12 verb families are named days (pisati 152, gledati 158, misliti 165,
raditi 172, zvati 179, stati 186, držati 193, živjeti 200, govoriti 202, pustiti 207,
staviti 209), and dative experiencer 187, instrumental of consonant-final feminines 177,
collective nouns 201, tricky present classes 159 and 161, dob and doba 177, verbal adverb
210, passive participle 166 to 167, verbal nouns 173, subjectless sentences 183, and
conditional sentences with da and kad 194 to 195 are all named. Exam craft (days 226 to 250)
mirrors the Croaticum sample section by section.

**The hard cores, checked to depth:**

- **Seven cases: covered.** N 10, A 11 and 64, G 33 and 118 to 121, D 34 and 89 to 90, L 22
  and 76, I 42 and 128, V 46, each with a full-phrase declension day at A2 and a case-system
  sweep at 145. Depth is right. The one thin spot is genitive plural formation as such
  (žena, godina, mjeseci types), which the exam gap-fill actively tests; it lives inside
  days 118 and 121 but is never a named focus.
- **Aspect: covered with unusual depth.** Big idea 32, A2 series 78 to 83, B1 opening 151 to
  153, eleven verb-family days, exam drill 231. This matches the ASOO B1 inventory, which is
  mostly aspect, family by family.
- **Aorist and imperfect: NOT required, correctly absent.** The ASOO curriculum does not
  contain the strings aorist or imperfekt at ANY level A1 to C2 (checked in the raw
  extraction today), and Croaticum's ladder does not name them through C1. The premise that
  reception of aorist/imperfect is a B1 hard core is not supported by either authority; the
  course is right not to teach them. At most, a single reception note inside a B1 reading
  day (literary and biblical texts, ajme/reče flavour) is a nice-to-have.
- **Verbs of motion: covered** (ići 22, doći 71, day 100 verbs of motion and aspect,
  prefixed motion pairs inside the B1 families). ASOO has no dedicated motion row; no gap.
- **Vocative: covered ahead of both authorities** (day 46 at A1; ASOO never names it,
  Croaticum places it at B1.1). Keep a formal-address spiral at B1 (see plan).

**Missing or misplaced against the official inventories** (authoring tasks, priority order):

1. **The imperative is never taught.** Zero hits for imperativ in all 250 titles AND all 250
   objectives. ASOO places "imperativ glagola na -iti, -ati" at A1; Croaticum at B1.1; the
   exam's oral and written tasks assume it; day 222 even teaches REPORTING commands
   (prenošenje zapovijedi) without the course ever having taught the commands themselves.
   Highest-priority gap in the whole course.
2. **Ordinal numbers and dates are never taught.** No day names ordinals (prvi, drugi,
   treći), reading years, or the genitive date construction (prvoga svibnja). Croaticum
   B1.1 lists ordinal numbers and genitive with dates explicitly; the exam's biography text
   and životopis writing task are date-dense. The deck contains NO ordinal at all.
3. **Numbers above 100 are never named.** ASOO A1 says "brojevi do bilijun"; the course
   stops at a named 20 to 100 (day 19). sto, tisuća, milijun are absent from the deck.
   Prices, salaries and years cannot be produced. Cheap fix, one day plus deck cards.
4. **Demonstratives (ovaj, taj, onaj) are never a named topic** and are absent from the
   deck. ASOO A1 row (pokazne zamjenice, with plural exceptions). They are unavoidable in
   every exam listening task.
5. **Reflexive verbs as a system are never named** (prezent, perfekt, futur povratnih
   glagola are three separate ASOO A1 rows). zvati se (day 3) and svoj (65) exist, but the
   se-verb paradigm with clitic placement is taught nowhere before clitic order (70)
   presupposes it.
6. **Noun plural formation is never a named topic**: duga množina (-ovi/-evi),
   sibilarizacija (k/g/h to c/z/s), nepostojani a, all ASOO A1 rows, all absent from titles
   and objectives. Almost certainly taught incidentally; the exam gap-fill tests these forms
   (očiju, for instance), so they need a named home.
7. **Relative pronoun koji is never taught.** Zero hits for koji/odnosne in titles and
   objectives (the one "relative" hit is English family relatives). Croaticum B1.2 names
   interrogative and relative pronouns; B1 reading (columns, interviews) is impossible
   without declined koji. Two days minimum: N/A forms, then oblique cases.
8. **Indefinite and negative pronouns (netko, nešto, nitko, ništa, sve, svi, svatko) are
   never named** and every one of them is absent from the deck despite all being inside the
   spoken top 600.
9. **The igrati (se) verb family is missing**: ASOO B1 lists 12 glagoli-u-kontekstu
   families; the course names 11; igrati/zaigrati/odigrati/razigrati se/poigrati se/izigrati
   is the omitted one. One day, same format as the other eleven.
10. **Two ASOO B1 topics have no named day**: *Želite li promijeniti svijet?* (20 hours in
    ASOO, the volunteering and social-change block) and *Vrijednosti, sposobnosti, interesi*
    (10 hours). Both are also natural hosts for missing abstract vocabulary.
11. **The ASOO A1 clothes topic (Odijelo (ne) čini čovjeka, 7 hours) has no day.** The deck
    already contains the clothes vocabulary (odjeća, hlače, košulja, haljina, kaput,
    cipele), so this is purely a lesson-slot gap.
12. **Forms and postcards (obrasci, razglednice)**: the ASOO A1 writing outcomes are form
    filling and a simple razglednica; levels.json promises exactly this at A1; no named day
    delivers it (day 56 "Writing day" is generic).

**Off-level, deliberate and acceptable, flag only:** neupravni govor (221 to 222) and
se-constructions in depth (208) are ASOO B2 rows taught at B1, the same safety margin the
Italian and French courses carry; their difficulty bands should sit at the top of B1.
Sklonidba brojeva jedan to četiri (223) matches Croaticum B1.1 (genitive with numbers), fine.
Glagolski prilog prošli (224) is in no ASOO inventory at any level; it is correctly framed as
recognition only, keep that framing. Vocative at A1 (46) is ahead of Croaticum's B1.1
placement but justified by greetings. i-declension is ASOO A1 but taught at B1 (177), which
matches Croaticum's B1.1 placement; acceptable as is.

## Vocabulary coverage

Matching: each deck `hr` field was tokenized (multiword entries credit their content tokens),
lowercased, and credited against hrLex both as lemma and as wordform mapped to all candidate
lemmas (a deliberately generous rule; the true coverage is if anything lower). Ranking:
hrLex lemma frequencies (hrWaC v2.2), punctuation, residuals, abbreviations, digit tokens
and majority-proper-noun lemmas excluded. Content class = nouns, verbs, adjectives, adverbs;
pronouns, prepositions, conjunctions, particles and numerals are counted separately because
the course teaches them through grammar days, not vocab cards (the French and German
convention), except ordinals and cardinal numbers, which per the German precedent the deck
convention counts.

**Against the hrWaC lemma ranking (hrLex 1.2), whole deck (2,566 entries, 2,553 credited
lemmas):**

| Rank band | Overall coverage | Content-class coverage |
|---|---|---|
| Top 1,000 | 65.8% | 67.7% (594 of 877) |
| Top 2,000 | 56.9% | 57.4% (1,067 of 1,860) |
| Top 3,000 | 47.9% | |
| Top 5,000 | 37.2% | 37.1% (1,787 of 4,822) |

By level slice: A0 plus A1 covers 31.4% of the top 1,000; through A2 covers 47.9%; the whole
deck 65.8%. 793 content-class lemmas of the top 2,000 are absent (manifest:
missing_top2000.txt in the session scratchpad).

**Against the spoken ranking (OpenSubtitles 2018 mapped to hrLex lemmas):** top 500 covered
68.8%, top 1,000 covered 62.1% (content class 64.3%, 563 of 876), top 2,000 covered 54.2%.
379 of the spoken top 1,000 are absent, of which 313 are content-class.

**The verdict this implies:** the same failure mode as pre-swap German and pre-expansion
French, high precision, low recall. What the deck teaches is well chosen and thematically
motivated; what it omits is the top of the frequency list. The absentee identity is damning:
**mama, tata, dečko, djevojka, momak, gospodin** (no address noun in the deck at all),
**čuti** (day 118 teaches the čuti-type present class while the deck lacks čuti itself),
**umrijeti, mrtav, smrt, roditi se** (no life-and-death vocabulary), **opet, jednom, nikada,
toliko, nekoliko, isti, jedini, zadnji, važan, spreman, sjajan, čudan, lud**, the verbs
**učiniti, dogoditi se, značiti, pokazati, pronaći, shvatiti, krenuti, ući, izaći, smjeti,
stajati, davati**, the nouns **igra, ideja, tijelo, duša, policija, plan, tip, stanje,
situacija, bog**, plus **žao** (as in žao mi je), **večeras, ovamo, unutra**. Function-class
absences that grammar days must own explicitly since no card will: ovaj/taj/onaj, netko/
nešto/nitko/ništa, sve/svi, sav, kakav/takav, sebe, čiji, prema, kod, kroz, poput, zar, baš,
evo, hajde. Web-register absences to host in civic and news theme days: sustav, područje,
član, vlast, sud, županija, ministarstvo, organizacija, skupina, sredstvo, mjera, proces,
odnosno, posto, milijun. Note on kuna: it ranks high in hrWaC because the corpus predates
the 2023 euro changeover; teach euro as current currency and kuna as a reception-only
history note, never as live pricing vocabulary.

**Capacity note.** 250 lessons unlock deck[0..2500], so 66 of the current 2,566 words are
already unreachable. The expansion to 340 lessons raises capacity to 3,400: room for about
834 net additions. The union of the spoken top-1,000 content absences (313) and the hrWaC
top-2,000 content absences (793) is roughly 850 unique lemmas; the pass therefore fills
essentially all headroom: spoken and core absences first, in level-correct pack positions
(A1/A2 core absences enter in A1/A2 pack order, which the assembler's ladder sort supports),
web-register civic vocabulary into the new B1 theme days. Targets after the pass: spoken
top-1,000 at 90 percent plus, hrWaC top-1,000 at 85 percent plus, hrWaC top-2,000 content at
75 percent plus. No swap is required, this is an append, like French and unlike German.

## The 90-slot content plan (direct input to the authoring pass)

Priority order within each level; grammar gaps first because a missing structure is
blocking, then exam craft, then theme days that host the missing vocabulary. Every new
thematic day carries 10 deck words drawn from the missing-lemma manifests (spoken list
first, then hrWaC top 2,000), which is what closes the vocabulary verdict.

**A0, 1 new slot (14 to 15):**
1. Ovo, to, ono: pointing and asking what things are (demonstratives intro, Što je ovo?,
   hosts ovaj/taj/onaj/kakav before the A1 consolidation).

**A1, 14 new slots (46 to 60):**
1. The imperative I: dođi, izvoli, oprostite, recite (affirmative -aj/-i forms, polite
   requests; ASOO A1 row).
2. Reflexive verbs as a system: zvati se, osjećati se, veseliti se in present, perfekt and
   futur, with clitic se placement (three ASOO A1 rows).
3. Plurals in full: duga množina (-ovi/-evi), sibilarizacija, nepostojani a (ASOO A1 rows;
   feeds the exam gap-fill).
4. Demonstratives across the phrase: ovaj/taj/onaj declined, with plural exceptions
   (ASOO A1 row).
5. Numbers above 100: sto, tisuća, milijun; prices and years (ASOO A1 brojevi do bilijun).
   Hosts: sto, tisuća, milijun, euro, cijena words.
6. Ordinals and dates: prvi, drugi, treći; Koji je danas datum?; genitive dates (Croaticum
   B1.1 rows; exam biography realia). Deck gets the ordinal series.
7. Indefinite and negative pronouns: netko, nešto, nitko, ništa, sve, svi, svatko (deck and
   grammar gap; spoken top 600).
8. Odijelo (ne) čini čovjeka: clothes and appearance (ASOO A1 topic; deck words exist,
   lesson does not).
9. Pluralia tantum and i-nouns starter: hlače, vrata, novine, stvar, noć (ASOO A1 rows).
10. Forms, personal data and the razglednica (ASOO A1 writing outcomes; delivers the
    levels.json A1 writing promise).
11. People words and address: mama, tata, dečko, djevojka, momak, gospodin, gospođa (hosts
    the worst spoken absentees).
12. Conversation essentials: baš, zar ne, evo, hajde, žao mi je, super, odmah (spoken
    particles and reactions).
13. Time adverbs that carry a story: opet, jednom, nikada, uskoro, upravo, već, još, tek.
14. Verbs of perception and communication: čuti, slušati, vidjeti, pokazati, značiti,
    razumjeti (hosts čuti, pokazati, značiti).

**A2, 5 new slots (90 to 95):**
1. The imperative II: negative nemoj plus infinitive, instructions and recipes (depth day;
   Croaticum B1.1).
2. Relative pronoun koji I: nominative and accusative, describing people and things
   (Croaticum B1.2 pulled down to its natural entry point).
3. Quantities and approximation: nekoliko, toliko, mnogo, posto, otprilike, plus the
   genitive plural formation intensive (žena, godina, mjeseci; exam gap-fill core).
4. City services and emergencies: pošta, banka, policija, hitna pomoć, prijaviti (hosts
   policija and institution vocabulary; institutions of state as subject matter).
5. Prefixed motion and result: ući, izaći, krenuti, pronaći, dogoditi se, učiniti (aspect
   spiral hosting six missing core verbs).

**B1, 70 new slots (100 to 170):**

Named-gap grammar and depth (12):
1. Verb family igrati (se): zaigrati, odigrati, razigrati se, poigrati se, izigrati (the
   12th ASOO family, same format as the other eleven).
2. Relative pronoun koji II: oblique cases, s kojim, o kojem, čiji (Croaticum B1.2).
3. The imperative III: idemo and hajdemo, reported commands bridge (closes the loop that
   day 222 currently presupposes).
4. Dates in biography: ordinals, years, rođen je 1873. godine (feeds the exam životopis and
   biography reading directly).
5. Genitive plural mastery: quantities, numbers 5 plus, brojevne imenice recognition
   (dvoje, troje).
6. Se-pasiv vs trpni pridjev: kaže se, izgrađen je (extends 166/167/183/208; ASOO B2
   pulled down as reception-plus, top of B1 band).
7. Word formation I: agent nouns (pisac, čitatelj, voditelj, vršitelj radnje), reception.
8. Word formation II: diminutives and augmentatives, reception (Croaticum B2.1 preview,
   reception only, top of B1 band).
9. Aspect in narration: the triplet chains (pričati, ispričati, prepričati) across a whole
   story.
10. Iterativni kondicional and habitual past: kad bih došao, on bi rekao (reception; ASOO
    B2, framed as recognition).
11. Formal address spiral: vocative in letters and emails, Poštovani gospodine Horvat,
    opening and closing formulas (exam written communication).
12. Red riječi under focus: enclitic chains after conjunctions (extends 225; ASOO B2
    coordinate-clause clitic rows, reception-plus).

Functions from the ASOO and CEFR B1 outcome lists (8):
13. At the travel agency, and when the trip goes wrong: booking, complaint, refund.
14. Expressing feelings and reacting: žao mi je, drago mi je, bojim se da, ponosan sam
    (hosts feeling vocabulary).
15. Polite agreement and disagreement: slažem se, ne bih se složio, imate pravo, ali.
16. Dreams, hopes and ambitions: nadati se, sanjati, planirati (the B1 CEFR spoken
    production descriptor, currently unthemed).
17. Retelling with reactions II: a book or series, opinions and recommendation (extends
    169).
18. Explaining how something works: instructions, se-constructions and imperative together.
19. Realizirati intervju: conducting and reporting an interview (ASOO B1 outcome).
20. Responding to ads: apartment, job, second-hand (ASOO reading/writing outcome, hosts
    oglas vocabulary).

ASOO B1 topics without a day (6):
21. Želite li promijeniti svijet? I: volunteering and civic action (volontirati, udruga,
    pomoć).
22. Želite li promijeniti svijet? II: social change, petitions, environment activism
    (spiral with 182).
23. Vrijednosti, sposobnosti, interesi: values, abilities, interests (hosts vrijednost,
    sposobnost, interes, vlastit).
24. Baščanska ploča and the first printed book: Croatian cultural heritage reading
    (ASOO culture insert; historical artefacts as subject matter).
25. Starohrvatska mitologija and customs (ASOO culture insert).
26. Croatian inventors, artists and athletes: biography reading pack (extends 160; keep to
    historical figures, respect the no-named-politicians rule).

Exam craft, second full cycle (12):
27. Listening A II: short announcements, traffic and service messages (twice-heard drill).
28. Listening B II: DA/NE on a longer text, trap patterns (almost-true statements).
29. Reading A II: biography with open questions, timed.
30. Reading B II: word-bank gap-fill, case-form strategy (travnju, očiju class).
31. Grammar A II: form choice under time (the eight sampled point types).
32. Grammar B II: aspect and prefix choice under time.
33. Writing atelier: describe a picture, structure and self-check rubric.
34. Writing atelier: životopis, dates and register (with day 4 of this block).
35. Speaking simulation III: the oral commission format end to end.
36. FULL TIMED MOCK EXAM #3.
37. Mock #3 error-log review and section triage.
38. Strategy day: the 60 percent rule per section, time budgets, what zadovoljio means in
    writing and speaking.

Theme days hosting the missing frequency core (24, each carries its 10 words):
39. Život i smrt: roditi se, umrijeti, smrt, grob, naslijediti (life-events vocabulary the
    deck entirely lacks).
40. Tijelo i zdravlje II: tijelo, bol, ozljeda, pregled (extends 40/188).
41. Grad, županija i država: uprava, županija, ministarstvo, građanin (civic structures as
    subject matter; hrWaC civic band).
42. Novac danas: euro, plaća, račun, trošak, štednja (kuna as history note).
43. Mediji i program: emisija, vijest, program, informacija.
44. Sport i igra: igra, klub, utakmica, ekipa, pobijediti.
45. Posao II: plan, projekt, sastanak, uspjeh, organizacija.
46. Osjećaji u nijansama: duša, sreća, strah, ponos, sram.
47. Pravila i pravo: zakon, sud, pravo, pravilo, dozvola (institutions of state as subject
    matter).
48. Društvo i zajednica: zajednica, skupina, član, odnos, susjed.
49. Znanost i tehnologija II: sustav, model, proces, istraživanje.
50. Umjetnost II: glazba, kazalište, izložba, umjetnik.
51. Povijest i sjećanje: povijest, stoljeće, spomenik, sjećanje.
52. Vjera i tradicija: bog, crkva, blagdan, običaj (cultural reception register).
53. Kuhinja II: recepti with imperative spiral (hosts cooking verbs).
54. Putovanja II: granica, putovnica, carina, odgoda.
55. Obrazovanje II: ispit, znanje, ocjena, vještina.
56. Stanovanje II: najam, ugovor, račun, kvar, popravak.
57. Odnosi II: brak, vjenčanje, povjerenje, svađa, pomirenje.
58. Svakodnevni mali razgovori: small talk, vrijeme, red, čekanje (hosts spoken particles
    in context).
59. Hrvatska po regijama: geography and regional identity (subject-matter geography).
60. Standard i dijalekti: što, kaj, ča awareness, reception only (sociolinguistic note the
    exam listening's standard-language framing makes safe).
61. Čitam hrvatski: books, papers and columns (reading-habit day, hosts čitatelj, izdanje).
62. B1 grand spiral revision I: cases, aspect, clitics in one narrative.

Buffer and consolidation (8):
63. to 66. Four review-and-speaking days interleaved through the new B1 block (the shipped
    course's weekly rhythm requires one per new week).
67. Listening endurance II: full bulletin plus conversation, twice-heard discipline.
68. Reading speed II: two texts against the clock.
69. Writing clean-copy discipline: error hunt in your own texts (self-correction).
70. B1 grand spiral revision II: the final pre-mock consolidation.

Deck directive for the same pass: append about 830 words in level-correct pack positions:
(1) all 313 spoken top-1,000 content absences at A1/A2 positions (respecting their level),
(2) the remaining hrWaC top-2,000 content absences at A2/B1, (3) fill remaining capacity
from the hrWaC 2,000 to 3,000 band and the civic/exam realia the new theme days name.
Ordinals, cardinals above 100, demonstratives and indefinite pronouns DO enter the deck (the
German precedent). Re-run this check's scripts after the pass; the `hrlex` key may go onto
packs only if spoken top-1,000 reaches 90 percent and hrWaC top-1,000 reaches 85 percent.

## Verdicts per source key

- **`asoo`: EARNED.** The raw docx is on disk, byte-identical to the live asoo.hr file
  re-verified today; the digest inventories match the raw extraction verbatim; and the diff
  now exists (this document): the A2 phase implements the ASOO A2 curriculum topic for topic
  and row for row, B1 covers 11 of 12 verb families and all other B1 rows, and the named
  gaps (imperative, plurals, demonstratives, reflexives, ordinals, igrati, two B1 topics)
  are listed as authoring tasks in the plan above. The 692 citations are honest at the level
  they claim (theme and grammar-sequence anchoring); close the listed gaps in the expansion
  to keep it that way.
- **`croaticum-syllabus`: EARNED, digest update owed.** Both pages re-fetched live today and
  the ladder, levels and test structure match the digest. Today's fetch exposes fuller
  grammar detail per level (ordinals, dates, i-declension, vocative, imperative at B1.1;
  relative pronouns and passive participle at B1.2) which the digest must absorb; two of
  those items (imperative, relative pronouns) are course gaps the plan above closes. The 677
  citations stand as progression-pacing provenance.
- **`croaticum-b1-sample`: EARNED.** The official sample-task PDF is on disk, byte-identical
  to the live file re-verified today, with full answer key extracted; exam-craft days 226 to
  235 and the mock-exam mapping mirror its sections, task types and point weights exactly.
- **`nn-6-2021`: EARNED.** Article 2 (B1 for citizenship), Article 3 (exemptions) and
  Article 6 (culture questionnaire, 15 of 110, at least 10, 60 minutes) re-verified today on
  narodne-novine.nn.hr; no amendments found in the zakon.hr consolidated view. The 3
  citations (citizenship pathway copy) are accurate.
- **`nn-100-2021`: EARNED.** The five-section structure, the commission, the verbatim
  60 percent rule, the zadovoljio grading of writing and speaking, and the
  every-section-must-pass rule re-verified today; the levels.json B1 exam object matches
  exactly; the exam is being administered at B1 in 2026 by licensed institutions. The 48
  citations are accurate.
- **`cefr-grid`: EARNED, via Europass.** The self-assessment grid was re-fetched today from
  the Europass republication and the descriptors quoted in levels.json match verbatim.
  coe.int itself returned 403 today (same block as the French check); the digest already
  lists Europass as a co-URL, so the key stands on a live official source, not on memory.
- **`ffzg-ecourse`: EARNED for its actual use, and only that.** Both e-course sites
  returned HTTP 200 today. The key is cited by ZERO content units as pedagogy provenance;
  it appears only in resources.json as external material, which is exactly what the gold
  book's resource rule allows. It must NOT be added to any pack, grammar topic or exam
  object unless someone diffs the 80-unit syllabi, which has not been done.
- **`hrlex` (NEW): PARTIALLY EARNED.** The source is fetched complete (hrLex 1.2 via the
  public megahr mirror, 5.9 million rows; version 1.3 exists but the CLARIN.SI bitstream
  returned zero bytes on every attempt today) and the diff was performed (this document).
  The deck fails the coverage direction (spoken top-1,000 at 62.1 percent, hrWaC top-1,000
  at 65.8 percent), so packs may NOT cite it until the expansion's deck pass closes the gap
  to the targets above. Registered in README.md; must also be added to
  ContentValidationTest.knownSourceKeys before any content cites it. Re-try hrLex 1.3
  (MD5 e55a21f10bbb4f6c22afe31a65803649) before launch.
- **HNK / Moguš čestotni rječnik: NOT a key, and must not become one** unless the print
  dictionary is obtained: the corpus has no downloadable frequency list and the 1999
  dictionary is a book. Nothing may cite HNK.

## Reproducibility

Scripts and manifests live in the session scratchpad (hr_deck.tsv, hr_day_titles.txt,
hrLex_v1.2.gz, hr_50k.txt, hrwac_rank.tsv, spoken_rank.tsv, missing_top2000.txt,
missing_spoken1000.txt); matching rules are stated in the vocabulary section. The raw ASOO
and Croaticum files in docs/sources/raw/ were re-verified byte-identical against their live
URLs on 2026-07-21, so the whole check can be re-run from the URLs in the provenance
section.
