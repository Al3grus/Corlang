# French referentiel and frequency cross-check (Phase 8b)

**Date: 2026-07-20.** This is the syllabus cross-check that the six French source keys
(`cecrl`, `referentiel-fr`, `francais-fondamental`, `freq-fr`, `delf-b1-sample`,
`delf-b2-sample`) had claimed without ever being performed. Same shape and same honesty
standard as the German Wortliste cross-check (docs/sources/goethe-exams.md) and the Italian
sillabo cross-check (docs/sources/italian-exams.md). French is the highest-stakes course in
the app: France raised naturalisation to B2 on 2026-01-01, so B2 is legally load-bearing here
and nowhere else. Checked content: the 36 deck files in `app/src/main/assets/content/fr/vocab/`
(2,886 entries: A1 818, A2 831, B1 639, B2 598; 2,879 unique headwords) and the 250 day titles
in `content/fr/plan/` (A1 45, A2 55, B1 70, B2 80). This check also defines the content of the
planned 145-lesson expansion (to 395: A1 50, A2 80, B1 140, B2 125).

## The legal driver, re-verified live

- **Naturalisation now requires B2, written and oral.** Decret n. 2025-648 of 15 July 2025,
  modifying decret n. 93-1362, in force for all applications filed from **1 January 2026**,
  for naturalisation by decree AND by declaration through marriage. Verified 2026-07-20 on
  service-public.gouv.fr (fiche F11926): "connaissance de la langue francaise a l'oral et a
  l'ecrit au moins egale au niveau B2". Accepted proof: a French diploma, any diploma attesting
  French at B2 (the DELF B2 is a national diploma and qualifies), or a TCF / TEF attestation
  valid 2 years (arrete du 22 decembre 2025). Exemptions only for certified medical
  impossibility, and for the decree route for refugees aged 70 plus with 15 years residence.
  A separate 40-question civics exam (examen civique) also applies from the same date; it is
  out of Corlang scope per the gold book.
- **Trap found in an official document:** the FEI Manuel du candidat DELF B2 (December 2021),
  still the current published manual, says "Le DELF B1 permet d'obtenir la nationalite
  francaise". That statement predates decret 2025-648 and is now wrong. Nothing in Corlang
  copy may state B1 for naturalisation.
- The residence ladder below naturalisation is unchanged in what Corlang targets: DELF A2 for
  the carte de resident de longue duree (stated in the same FEI manual).

## Fetch provenance and completeness

- **DELF B2 exam format: COMPLETE, two official documents.** (1) FEI *Manuel du candidat
  DELF B2* (Sevres, December 2021, 20 pages), fetched complete as PDF from delfdalf.jp (the
  DELF DALF Japan national centre, an official FEI exam-centre network site; text extracted in
  full). (2) The official demonstration paper **Sujet_demo_B2TP**, candidate booklet, 13 pages,
  fetched complete from delfdalf.fr. Both agree exactly, see the format section below.
- **DELF B1 exam format: COMPLETE via the official manual.** FEI *Manuel du candidat DELF B1*
  (same series, 20 pages) fetched complete from delfdalf.jp. The B1 sujet demo PDF itself
  could NOT be retrieved today (delfdalf.fr media links for B1 are dead); the manual documents
  every exercise type, so the format is verified, the sample paper is not on disk.
- **FEI website: BLOCKED.** france-education-international.fr returned an access-denied page
  on every attempt (diploma pages and document pages). All FEI facts above therefore come from
  the two FEI-authored PDFs obtained through official exam-centre hosts. Re-try FEI directly
  before launch.
- **Beacco et al. referentiels (Didier): NOT FETCHED, and not fetchable.** *Niveau A1 / A2 /
  B1 / B2 pour le francais* are commercial print/ebook volumes; no official free copy of the
  inventories exists online. Only publisher and scholarly descriptions were reachable. The
  existing digest referentiel-fr.md was written from memory, not from the books; nothing may
  cite `referentiel-fr` as a checked source (see verdicts).
- **Substitute per-level inventory: COMPLETE.** *Inventaire linguistique des contenus cles des
  niveaux du CECRL* (Eaquals and CIEP, Paris 2015, coordinated by Brian North; revision and
  quality control by two CIEP staff, and CIEP is today France Education international), 112-page
  PDF fetched complete from eaquals.org and text-extracted in full, including Annexe D (the
  master table of functions, discourse, sociocultural content, grammar and vocabulary themes
  per level A1 to C1) and Annexe E (the per-level overviews). Its taxonomy is built on the
  CEFR and on the Beacco referentiels. This is the grammar and function authority the plan was
  diffed against, and it is the closest FEI-adjacent document that is actually publishable
  proof. Registered as new key `inventaire-cecrl`.
- **Coe.int: BLOCKED** (HTTP 403 on the RLD page). The CEFR descriptor grid itself was not
  re-fetched today; the cecrl-grid.md digest stands on its earlier verification.
- **Francais fondamental: PARTIAL.** The official 1,475-word 1er degre list was not found in
  any fetchable official form. What WAS fetched complete: the **Gougenheim frequency base**
  (lexique.org/databases/Gougenheim100/gougenheim.tsv, 1,063 rows, 1,023 unique words), which
  is the published frequency table from *L'elaboration du francais fondamental* (Gougenheim,
  Rivenc, Michea, Sauvageot), the exact corpus the FF list was built from (163 spoken texts,
  312,135 words; words with frequency 20 plus). The FF 1er degre additionally contains
  availability words (mots disponibles) that are NOT in this base; coverage against the full
  official list can therefore not be computed today and is not claimed.
- **Frequency authority: COMPLETE.** **Lexique 3.83** (New, Pallier et al.), the standard
  French lexical database, fetched complete from lexique.org (25.8 MB TSV, 46,947 unique
  lemmas with film-subtitle and book corpus frequencies). Used as the ranked frequency
  authority: rank = lemma ordered by freqlemfilms2 plus freqlemlivres. Registered as new key
  `lexique383`. *A Frequency Dictionary of French* (Lonsdale and Le Bras), the source the
  `freq-fr` key names, is a commercial Routledge book and was NOT fetched; no content may
  claim it (see verdicts).
- **Naturalisation law: COMPLETE** via service-public.gouv.fr fiche F11926 (fetched 2026-07-20),
  corroborated by diplomatie.gouv.fr and immigration.interieur.gouv.fr search results.
  Registered as new key `decret-2025-648`.

## DELF exam formats as currently administered (re-verified)

Both manuals and the sujet demo confirm the pass rule the digest recorded: **each of the four
epreuves is out of 25; pass = at least 50/100 total AND at least 5/25 in every epreuve; below
5/25 anywhere is eliminatory.** Still current as of 2026-07-20. The 2026 law change altered the
required LEVEL, not the DELF format or pass rule. Note: FEI states two paper formats (format 1
and format 2 actualise) are in circulation side by side "pendant quelques annees"; the
difference is exercise count in the comprehension papers, not sections, timing, points or pass
rule.

**DELF B2** (Manuel du candidat B2 + Sujet_demo_B2TP, verbatim figures):
- Comprehension de l'oral: 30 minutes, 25 points, 2 or 3 exercises; two recorded documents,
  one heard twice (expose, conference, discours, documentaire, emission), one heard once
  (interview, bulletin d'informations); recordings max 8 minutes total.
- Comprehension des ecrits: 60 minutes, 25 points; informative text on France or la
  francophonie plus an argumentative text.
- Production ecrite: 60 minutes, 25 points, one task, **250 words minimum**, prise de position
  argumentee (contribution to a debate, formal letter, or critical article).
- Production orale: 20 minutes plus **30 minutes preparation**, 25 points; present and defend
  a viewpoint from a short trigger document, then debate with the examiners.
- Collective papers total 2h30.

**DELF B1** (Manuel du candidat B1): CO 25 min (3 exercises), CE 45 min, PE 45 min (one text,
**160 words minimum**), PO 15 min plus 10 min preparation (entretien dirige, expression d'un
point de vue, exercice en interaction). Same 50/100 and 5/25 rule.

The exam objects in `content/fr/levels.json` (A1, A2, B1, B2) match these formats and pass
rules exactly, including the 160 and 250 word minima and the B2 trigger-document oral. No fix
needed there.

## Grammar and function coverage: 250 day titles vs the Inventaire

Method: the taught-structure inventory was built from the 250 day titles and diffed both ways
against Annexe D and Annexe E of the Inventaire (Eaquals and CIEP 2015). B2 was checked
exhaustively, row by row, because it is the legal target.

**Covered, with margin.** The course names every core item of the A1 and A2 grammar tables
(articles and gender, present of the three groups, negation, questions, futur proche, passe
compose with both auxiliaries, imparfait and the alternance, futur simple, COD/COI/y/en,
comparison, relatives qui/que, imperative with pronouns, partitives, full negation set,
ne...que, indefinites, depuis/pendant/il y a) and every core item of B1 (subjonctif present
and triggers, conditionnel, si + imparfait, dont/ou/ce qui/ce que, plus-que-parfait, discours
indirect, gerondif, passive, nominalisation, cause/consequence/but/opposition/concession
connective days, doubles pronoms, relatifs composes). At B2 the argumentation, discourse and
sociocultural inventories (articulateurs, registres, mise en relief, implicite, ironie,
idioms, thesis/antithesis essay craft, note-taking, formal letter, article d'opinion, debat)
are covered with unusual depth by days 177 to 207 and 236 to 250, and the B2 theme list of
the Inventaire (societe, environnement, economie, TIC, politique, valeurs) maps onto days
208 to 235 completely.

**Missing or misplaced against the official inventory** (authoring tasks, priority order):

1. **L'infinitif passe** (apres avoir termine, merci d'etre venu): required at B2 in Annexe D.
   No named topic anywhere in the 250 and no plausible incidental home. Blocking for B2
   narration and formal writing. Highest-priority grammar gap.
2. **L'expression de la condition** (a condition que + subj, pourvu que + subj, au cas ou +
   cond, plus avec/sans + noun as hypothesis): required at B2. The course has days for cause,
   consequence, but, opposition and concession (129 to 132) but condition is never a named
   topic; day 104 covers only si + imparfait and day 133 only si + plus-que-parfait. Gap.
3. **L'exclamation** (Quel ! Que ! Comme !): required at A2 (Annexe D and E). Absent from all
   250 titles at every level. Same gap Italian had (frasi esclamative); it recurs because no
   topic list ever names it. Needed for DELF PO/PE naturalness.
4. **Les hypotheses certaines, si + present** (S'il fait beau, je vais a la plage): the A2/B1
   entry point of the hypothesis ladder is never named; the course starts the ladder at si +
   imparfait (104). A DELF A2 or B1 candidate must produce type-0/1 conditions. Gap, cheap fix.
5. **On: les trois valeurs** (nous / ils / quelqu'un): A2 requirement, never named. On is
   unavoidable in listening papers and in spoken French; one lesson.
6. **La mise en relief** (ce qui... c'est; ce que je veux dire, c'est que): B1 requirement in
   the Inventaire; the course first names it at B2 (day 178), with partial cover at day 105.
   Pull the introduction into B1, keep the B2 day for register depth.
7. **Le participe present vs l'adjectif verbal** (fatigant / fatiguant, different spelling and
   agreement): B2 row names both; day 176 names only the participe present. Content-note fix
   inside a new or existing B2 lesson.
8. **L'interrogation avec lequel**: B2 row in Annexe D; day 125 teaches the contracted
   relative forms (auquel, duquel) but interrogative lequel is never named. Content-note fix.
9. **B2-register connective sets for cause and consequence** (en raison de, du fait de, faute
   de, a force de, sous pretexte de; de ce fait, en consequence, de telle sorte que): days 129
   and 130 carry the B1 sets; day 197 upgrades opposition (or, neanmoins, en revanche) but
   cause/consequence never get their B2 upgrade. Fold into the new B2 connective lessons.
10. **Se plaindre par telephone** (B2 function): complaint is covered in writing (242); the
    oral service-recovery scenario is not named. One interaction lesson.
11. **Presenter un pays, une ville / faire une biographie, le portrait** (A2 functions): only
    partially housed (day 95 decrire un lieu, day 29 decrire une personne). Name biography
    and place-presentation in A2 expansion content.
12. **Les rituels de la lettre formelle** at B1 (Inventaire places the sociocultural ritual at
    B1; the course teaches the formal letter only at B2 day 242, while the B1 letter day 115
    is personal). Add a B1 formal-register letter lesson (also serves DELF B1 PE variants).

**Off-level, deliberate and acceptable, flag only:** futur anterieur (124), discours indirect
au passe with concordance (128), hypothese irreelle du passe (133) and relatifs composes (125)
sit in B1 but are B2 rows in the Inventaire; that is safety margin exactly like the Italian
course, their difficulty bands should be at the top of the B1 range. Day 78 (accord du
participe passe with preposed COD) is a B1 row taught at A2, same class. Passe simple (194)
and subjonctif imparfait (195) are C1/literary items correctly framed as recognition only;
keep the "reconnaitre" framing. **La synthese de documents (186, 238) is a DALF C1 task, not
a DELF B2 task**: the B2 PE is a single argued text. Keep the lessons as stretch training but
they must not be presented as B2 exam format, and the two examen-blanc days (192, 193) must
not include a synthese section. Verified against the Sujet_demo_B2TP paper: they do not.

## Vocabulary coverage

Matching: lemma = headword after stripping articles and reflexive se, NFC casefold, oe
ligature normalized; multiword entries credit their content tokens; plural forms credit the
singular. Function words and grammatical paradigm items (pronouns, determiners, possessives)
are excluded from the "content" counts because the course teaches them through grammar
lessons, not vocab cards; both raw and content numbers are given.

**Against the Gougenheim base (1,023 unique words, the francais fondamental corpus core):**

| Deck slice | Covered | Coverage |
|---|---|---|
| A1 packs only | 383 of 1,023 | 37.4% |
| Through A2 | 621 of 1,023 | 60.7% |
| Whole deck (through B2) | 712 of 1,023 | 69.6% |

311 words missing, of which **243 are content-class**. This is better than pre-swap German
(46%) but the same failure mode, and the identity of the absentees is damning for a
frequency-first deck.

**Against Lexique 3.83 (ranked lemmas, films plus books frequency):**

| Rank band | Overall coverage | Content-class coverage |
|---|---|---|
| 1 to 1,000 | 72.3% | 76.9% (659 of 857) |
| 1,001 to 2,000 | 50.0% | 51.0% |
| 2,001 to 3,000 | 36.3% | 37.3% |
| 3,001 to 4,000 | 25.6% | 26.1% |
| 4,001 to 5,000 | 18.9% | 19.1% |
| Top 5,000 total | 40.6% (2,031) | |

676 content-class lemmas of the top 2,000 are absent; 2,814 of the top 5,000.

**Most important absent lemmas** (frequency order, spot-checked individually against the raw
`hr` fields): la chose, la fois, un an, maintenant, sortir, le travail (travailler is in,
the noun is not), demander, peut-etre, quelque chose, quelqu'un, tout le monde, d'accord,
premier / deuxieme / troisieme (no ordinal exists in the deck at all), le nom, le besoin,
la minute, la seconde, le cas, vite, la voix, l'age, l'endroit, le lieu, mourir, naitre,
meilleur, pire, aucun, tel, pareil, sinon, le milieu, le fond, le bord, le debut, l'image,
l'aide, la parole, le reve, exister, suffire, manquer, agir, ressembler, quitter, retrouver,
reprendre, la dame, le copain, maman, papa, le village, midi, l'apres-midi, le lendemain,
le francais / l'anglais (no language or nationality word exists in the deck), and the tens
numerals quinze, quarante, cinquante, soixante. Numbers and ordinals may be practised inside
lesson 4 activities, but per the German precedent the deck convention counts them, and a
learner's SRS never sees them.

**Level fit, the healthy direction:** 97% of A1 headwords, 93% of A2, 93% of B1 and 87% of B2
are real Lexique lemmas; 90% of A1 headwords sit inside the top 5,000 (78% A2, 61% B1, 32%
B2). The B1/B2 tail is deliberate exam and civic realia, thematically motivated as in Italian.
The deck is high-precision and low-recall: what it teaches is well chosen, what it omits is
the top of the frequency list.

**Capacity note.** 250 lessons unlock deck[0..2500], so 386 of the current 2,886 words are
already unreachable. The expansion to 395 lessons raises capacity to 3,950: room for 1,064
net additions. The missing frequency core above (243 Gougenheim content absences, 676 top-2,000
content absences, heavily overlapping: about 700 unique lemmas) fits inside that headroom
with about 350 slots to spare for B2 argumentation collocations and band 2,000 to 3,000
fill. Unlike German, NO swap is required; this is an append done alongside the new lessons,
inserted at the correct level position (A1/A2 core absences must enter in A1/A2 pack order,
not appended at the end, which the assembler's ladder sort supports).

## The 145-slot content plan (direct input to the authoring pass)

Priority order within each level; grammar gaps first because a missing structure is blocking,
then exam craft, then theme days that host the missing vocabulary. Every new thematic day
must carry 10 deck words drawn from the missing-lemma lists (gg_missing / lexique top-2,000
first), which is what closes the vocabulary verdicts.

**A1, 5 new slots (45 to 50):**
1. Les nombres de 20 a 100 et les ordinaux (premier, deuxieme, troisieme; prices, floors,
   dates). Hosts: quinze, quarante, cinquante, soixante, ordinals.
2. Les nationalites et les langues (etre + nationality, parler + language, venir de + pays).
   Hosts: francais, anglais, espagnol, allemand, etranger.
3. Les mots du temps qui passe (an vs annee, fois, minute, moment, maintenant, tot, tard,
   midi, l'apres-midi, le matin meme). Hosts that exact list.
4. Les indispensables de la conversation (chose, quelque chose, quelqu'un, tout le monde,
   d'accord, peut-etre, vite, ensemble). Function: exprimer l'accord simple.
5. On et nous au present; premiere rencontre avec etre en train de et venir de (the Inventaire
   places both at A1; the course currently waits until day 60).

**A2, 25 new slots (55 to 80):**
Grammar and function gaps (7): 1. L'exclamation: Quel ! Que ! Comme ! 2. Si + present: les
hypotheses certaines. 3. On: les trois valeurs. 4. La nominalisation: decouverte (la visite,
la reponse, le depart). 5. Feliciter, souhaiter, remercier: les formules (fete, anniversaire,
reussite). 6. Avertir et mettre en garde (attention a, il ne faut pas, sinon). 7. Faire une
biographie simple: raconter la vie de quelqu'un (naitre, mourir, se marier, la jeunesse).
DELF A2 exam craft (5), the level currently has zero named exam-training days: 8. CO A2:
comprendre des annonces et messages. 9. CE A2: comprendre des ecrits du quotidien. 10. PE A2:
ecrire un message de 60 mots (invitation, excuse, recit simple). 11. PO A2: entretien,
monologue et jeu de role. 12. Examen blanc DELF A2.
Theme days hosting missing core vocab (13): 13. Presenter un pays et une ville. 14. Un fait
divers simple (rater, tomber, voler, le voleur). 15. La famille elargie et les etapes de la
vie. 16. Les mesures et les quantites (le metre, le kilometre, le kilo, la moitie, le quart,
le double). 17. Chez le voisin: la vie de quartier (le copain, la dame, le voisin, le village).
18. L'ecole d'hier et d'aujourd'hui (imparfait practice). 19. Le corps en mouvement (bouger,
s'asseoir, se lever, tenir, jeter). 20. Parler de son etat (avoir besoin de, se sentir, la
forme, fatigue). 21. Les objets de tous les jours 2 (le truc, le machin, servir a, utile).
22. Avant et apres (le lendemain, la veille, d'abord, ensuite, enfin, narration chain).
23. Donner son avis simplement (avoir raison, avoir tort, etre d'accord, meilleur, pire).
24. Le monde du travail 2 (le patron, l'equipe, la reunion, embaucher). 25. Revision A2:
raconter, decrire, comparer (spiral day).

**B1, 70 new slots (70 to 140):**
Named-gap fixes and grammar depth (12): 1. La mise en relief: ce qui... c'est (pulled from
B2 per the Inventaire). 2. La lettre formelle: rituels et formules (vous de politesse,
formules d'appel et de conge). 3. Si + present vs si + imparfait: la ligne des hypotheses.
4. Le subjonctif present: verbes irreguliers et les 10 declencheurs les plus frequents.
5. Indicatif ou subjonctif: esperer vs souhaiter, penser que vs ne pas penser que.
6. Les adverbes en -ment et la place de l'adverbe. 7. Les pronoms en et y avec verbes a
preposition (penser a, se souvenir de). 8. Le passif: temps composes et complement d'agent.
9. Les temps du recit: passe compose, imparfait, plus-que-parfait ensemble (spiral).
10. Le gerondif: maniere, condition, simultaneite. 11. La comparaison avancee (de plus en
plus, autant de, le meme que). 12. Les verbes a double construction (demander a quelqu'un de,
permettre a, interdire a).
DELF B1 exam craft (12): 13 to 16. One full CO cycle: annonces, radio, conversation,
strategies des 3 exercices. 17 to 18. CE: le texte informatif; degager la position de
l'auteur. 19 to 21. PE: l'essai 160 mots x 2 ateliers (plan, connecteurs, relecture), la
lettre au courrier des lecteurs. 22 to 24. PO: entretien dirige, expression du point de vue
x 2 (3-minute expose from a topic slip). 25. Examen blanc DELF B1 complet (the current day
170 bilan stays as the second).
Inventaire B1 functions not yet themed (10): 26. Reformuler pour expliquer (c'est-a-dire,
autrement dit). 27. Synthetiser un court article et donner son avis. 28. Realiser une
interview. 29. Formuler des hypotheses sur le present. 30. Exprimer la satisfaction et le
mecontentement: reclamer poliment. 31. Rassurer et encourager quelqu'un. 32. Exprimer
l'interet et l'indifference. 33. Prendre la parole et garder son tour dans une discussion.
34. Rapporter une conversation (discours indirect spiral). 35. Decrire une evolution (de
plus en plus, augmenter, diminuer).
Theme days aligned to Inventaire B1 themes and the missing 1,000 to 3,000 band (35):
36 to 70: la mode et l'apparence; les sentiments en nuance (la honte, la fierte, l'espoir);
les relations amoureuses et l'amitie; la sante mentale et le stress; le sommeil et le rythme
de vie; les generations et la transmission; la cuisine et les recettes (imperative spiral);
les courses et la consommation locale; le logement partage; les animaux et la campagne;
la meteo et le climat au quotidien; la montagne et la mer (vacances); les musees et le
patrimoine local; la lecture et la presse people vs serieuse; la radio et les podcasts;
la television et les series; les jeux et les loisirs numeriques; le telephone et la
messagerie; l'administration en ligne; la banque au quotidien (le compte, la carte, retirer);
les impots et les papiers (vocabulaire des demarches); la voiture et la conduite (le permis,
freiner, doubler); le velo en ville; les urgences et les secours (composer un numero,
au secours); la justice au quotidien (le droit, la loi, porter plainte); le bricolage et
les reparations; le jardin; les fetes de famille; les voyages en francophonie (le Quebec,
l'Afrique francophone, la Belgique et la Suisse); l'accent et les varietes du francais;
la politesse et les malentendus culturels; l'humour au quotidien; les souvenirs d'enfance
(imparfait spiral); les projets d'avenir (futur spiral); revision B1 grand spiral. Each of
these 35 carries its 10 words from the missing-lemma manifest, A2/B1 band first.

**B2, 45 new slots (80 to 125):**
Grammar gaps, blocking first (10): 1. L'infinitif passe (apres avoir + participe, merci
d'avoir/d'etre, avant de vs apres + inf passe). 2. La condition: a condition que, pourvu
que, au cas ou, a moins que, faute de quoi. 3. Le conditionnel passe en fonction: conseil
retrospectif, regret, reproche (tu aurais du, j'aurais aime). 4. La cause soutenue: en
raison de, du fait de, faute de, a force de, sous pretexte de. 5. La consequence soutenue:
de ce fait, en consequence, de telle sorte que, au point de. 6. Participe present, adjectif
verbal et orthographe (fatigant / fatiguant, provocant / provoquant). 7. Lequel dans toutes
ses fonctions: interrogatif, relatif compose, reprise. 8. Le ne expletif et les nuances de
la phrase soutenue (avant qu'il ne parte), reception first. 9. Le passif complet: par vs de,
se faire + infinitif, la forme impersonnelle passive. 10. Subjonctif bilan: les declencheurs
de jugement et de concession (spiral over 171/172).
Functions and orality (5): 11. Se plaindre et negocier au telephone (service recovery).
12. Nuancer en direct: concessives orales (certes... mais, il n'empeche). 13. Interagir en
reunion professionnelle (rituels, tours de parole, compte rendu oral). 14. L'entretien
d'embauche niveau B2 (parcours, motivation, questions pieges). 15. Debattre des chiffres:
commenter des donnees et un graphique (DELF trigger documents are often charts).
DELF B2 exam craft (12): 16 to 18. CO B2: l'exposé long (5 minutes, une seule prise de
notes), l'interview a une ecoute, strategies des deux formats. 19 to 20. CE B2: le texte
informatif sur la francophonie; reperer these et concessions dans un texte polemique.
21 to 24. PE B2: la contribution a un debat en ligne; la lettre formelle au maire ou au
directeur; l'article critique; atelier relecture et autocorrection (self-correction is a
named B2 descriptor). 25 to 26. PO B2: construire le plan en 30 minutes x 2 (trigger
document to defended outline). 27. Examen blanc DELF B2 n. 2 complet (the existing 192/193
becomes n. 1).
Theme and argument days hosting B2 vocab (18): 28 to 45: le monde associatif; la
recherche et l'innovation; la francophonie institutionnelle; le systeme de sante compare;
le logement et la crise urbaine; la culture subventionnee; le service public et l'usager;
la presse d'opinion et le dessin de presse; l'entreprise et la RSE; les syndicats et la
greve (institutions of state allowed as subject matter); la demographie; la ruralite et
les territoires; le numerique a l'ecole; la propriete intellectuelle; le mecenat et le
financement participatif; la gastronomie comme patrimoine; l'exception culturelle;
revision B2 grand spiral. Each carries 10 words from the 2,000 to 5,000 missing band plus
argumentative collocations (remettre en cause, tirer parti de, faire face a).

Deck directive for the same pass: append about 1,060 words in level-correct pack positions:
(1) all 243 Gougenheim content absences at A1/A2, (2) the remaining top-2,000 Lexique content
absences at A2/B1, (3) fill to capacity from the 2,000 to 3,000 band and the B2 collocation
list. Target after the pass: Gougenheim 95 percent plus, Lexique top-1,000 at 95 percent plus,
top-2,000 at 90 percent plus. Re-run this check's scripts to confirm before restoring keys.

## Verdicts per source key

- **`cecrl`: PARTIALLY EARNED.** The can-do descriptors in levels.json match the published
  CEFR self-assessment grid (digested in cecrl-grid.md), and every exam fact citing it was
  re-verified today through FEI documents. But coe.int itself returned 403 today, so the grid
  was not re-fetched; the key stands on the earlier digest, not on a same-day fetch. Keep, and
  re-verify coe.int before launch.
- **`referentiel-fr`: UNEARNED.** The Beacco Didier volumes were never opened; they are
  commercial books with no fetchable inventory, and the referentiel-fr.md digest was written
  from memory. Per the provenance rule the 31 pack citations of `referentiel-fr` are an
  overclaim. Remove the key from content, or re-point those citations to `inventaire-cecrl`
  once follow-ups 1 to 12 above are authored; the digest file must be marked as
  reconstructed-from-memory until then.
- **`inventaire-cecrl` (NEW): EARNED for the topic sequence, with the 12 listed follow-ups.**
  The Eaquals and CIEP 2015 Inventaire was fetched complete and the 250 titles were diffed
  against Annexe D and E both ways. The course covers the A1 to B2 inventories with margin
  except the items listed; items 1 and 2 (infinitif passe, condition) are the only B2
  blockers. Key registered in README.md; must also be added to
  ContentValidationTest.knownSourceKeys before any content cites it.
- **`francais-fondamental`: UNEARNED for the deck as it stands.** Two independent reasons:
  the official 1,475-word list itself could not be fetched (only the Gougenheim corpus base
  could, complete), and the diff against that base fails the coverage direction (69.6 percent
  whole-deck, 60.7 by end of A2, with chose, fois, maintenant and the tens numerals absent).
  Do not ship the key on the packs; restore it only if the expansion's deck pass closes the
  243 content absences AND the official list (or a verified faithful copy) is obtained.
- **`freq-fr`: UNEARNED as defined; superseded.** The key names Lonsdale and Le Bras, which
  was never fetched and is not fetchable. The frequency function is now served by Lexique
  3.83, fetched complete and diffed (numbers above). Re-point the 45 pack citations from
  `freq-fr` to `lexique383` when the deck pass lands; coverage today (top-1,000 at 72.3
  percent) does not yet justify the key on the A1/A2 packs either.
- **`lexique383` (NEW): PARTIALLY EARNED.** The source is fetched complete and the diff was
  performed (this document); the deck fails the coverage direction, so packs may not cite it
  until the expansion closes the top-2,000 gap. Registered in README.md; add to
  ContentValidationTest.knownSourceKeys.
- **`delf-b1-sample`: PARTIALLY EARNED.** The B1 format, timing, word minimum and pass rule
  were verified today from the official FEI Manuel du candidat B1, and the levels.json B1 exam
  object matches exactly. The sujet demo paper itself could not be retrieved (dead media
  links), so mock-exam fidelity at item level (Phase 8e) still lacks its official template on
  disk. Note also this key is currently cited by the A1 and A2 exam objects, which is
  imprecise: their formats were verified via the same manual series, but an `delf-a1/a2`
  split or a rename to a generic delf-manuals key would be more honest.
- **`delf-b2-sample`: EARNED.** The official Sujet_demo_B2TP candidate booklet (13 pages) and
  the Manuel du candidat B2 were both fetched complete today; sections, timings, points, the
  50/100 with 5/25 rule, the 250-word minimum, the 30-minute oral preparation and the task
  natures all match the levels.json B2 exam object and days 187 to 193. This is the exam the
  naturalisation target hangs on, and its format claim is now proven.
- **`decret-2025-648` (NEW): EARNED.** The B2 naturalisation requirement, its date, scope
  (decree and marriage routes), proof documents and exemptions were verified on
  service-public.gouv.fr today. Registered in README.md; add to
  ContentValidationTest.knownSourceKeys. Any app copy about naturalisation must cite it.

## Reproducibility

Scripts and manifests live in the session scratchpad (fr_deck.tsv, gg_missing.txt,
lex_missing_content.txt, inventaire.txt, delf-b2-sujet.pdf, Lexique383.tsv, gougenheim.tsv);
the matching rules are stated in the vocabulary section. The next session can re-run the
whole check from the URLs in the provenance section.
