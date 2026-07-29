# The Error Registry

Every defect class ever found in a Corlang course, with its automation and its sweep status
across ALL languages. This is the self-improving loop made concrete: an error found once in
one language becomes a check run against every language, forever.

**The lifecycle (mandatory, same commit as the fix):**

1. **Register.** A new failure class gets a row here when it is found. An error without a
   registry row is not fixed, it is postponed.
2. **Scope.** Decide what it applies to: one language, all languages, decks, exams, Kotlin
   copy, or process.
3. **Sweep.** Run the check against every in-scope language NOW. A sweep that cannot happen
   now goes in **Open sweeps** below with what blocks it. The registry's founding example: the
   dash rule was "fixed" twice (content purge 2026-07-19, seed-greeting gate 2026-07-20) and a
   grep while writing this file still found ~40 dash-bearing strings in UI Kotlin. Fixed twice
   is not swept.
4. **Automate.** Encode it at the strongest layer that fits: Kotlin gate (`ContentValidationTest`)
   > assembler (`build_language.py`) > batch checker (`check_batch.py` / `check_<lang>.py`)
   > course audit (`proctor.py`). A rule that lives only in prose will be violated by the
   next parallel agent.
5. **Prevent.** Encode the upstream cause in the Gold Book / specs / agent prompts, so the
   error stops being produced, not just caught.

Checker bugs are errors too (§V): a validator that silently stops checking is worse than none.

Sweep key: ✅ swept clean · 🔧 swept, fixes pending · ▢ not yet swept · — not applicable.

---

## I. Content: language-independent

| ID | Failure class | Found | Automated by | hr | fr | pt | de | it | es |
|---|---|---|---|---|---|---|---|---|---|
| C1 | Em/en dashes in learner-visible content | 2026-07-19, all courses (3822 purged) | Kotlin gate `content_usesNoEmOrEnDashes`; DASH regex in every tool | ✅ | ✅ | ✅ | ✅ | ✅ | ▢ |
| C2 | Dashes in learner-visible KOTLIN copy (gate above only walks assets) | 2026-07-20, tutor seed greetings | Kotlin gate over ALL string literals (16 swept, Drills.kt delimiters allowlisted) | ✅ | ✅ | ✅ | ✅ | ✅ | ▢ |
| C3 | Sending learners outside the app (URLs, institutions, platforms) | 2026-07-18, hr Croaticum/FFZG refs | EXTERNAL regex in `check_batch.py`; Kotlin gate | ✅ | ✅ | ✅ | ✅ | ✅ | ▢ |
| C4 | Dead or unverified resource URLs | pre-2026-07 (dead YouTube link shipped) | Live-verify rule, Gold Book Phase 2; no automated gate possible | ▢ | ▢ | ✅ | 🔧 | ✅ | ▢ |
| C5 | FILL answer appearing in its own prompt | fr/pt expansion | `check_batch.py`; Kotlin gate | ✅ | ✅ | ✅ | ✅ | ✅ | ▢ |
| C6 | REORDER prompt leaking the answer tokens | fr/pt expansion | `check_batch.py` 70% token-overlap rule | ✅ | ✅ | ✅ | ✅ | ✅ | ▢ |
| C7 | REORDER with fewer than 3 tokens | 2026-07-20, it_a1a "il sole" | `check_batch.py` | ✅ | ✅ | ✅ | ✅ | ✅ | ▢ |
| C8 | Duplicate prompts within a container | fr/pt expansion | `check_batch.py`; Kotlin gate | ✅ | ✅ | ✅ | ✅ | ✅ | ▢ |
| C9 | "day N" phrasing in learner text | fr/pt expansion | `check_batch.py`; Kotlin gate | ✅ | ✅ | ✅ | ✅ | ✅ | ▢ |
| C10 | Objective restated verbatim in drills/intros ("In this lesson you will…") | 2026-07-20, pt lesson 1 (user report) | `proctor.py` check 1 | ✅ | ✅ | ✅ | ✅ | ✅ | ▢ |
| C11 | Stamped-out instructional boilerplate across lessons | 2026-07-20, de dialogues | `proctor.py` check 2 (ritual app nudges exempt) | ✅ | ✅ | ✅ | ✅ | ✅ | ▢ |
| C12 | Duplicate taught sentences across days | 2026-07-20 proctor sweep | `proctor.py` check 3 | ✅ | ✅ | ✅ | ✅ | ✅ | ▢ |
| C13 | Quiz/exam prompts colliding with lesson prompts (tests memory, not language) | 2026-07-20 proctor design | `proctor.py` check 4 | ✅ | ✅ | ✅ | ✅ | ✅ | ▢ |
| C14 | MCQ answer visible in its own prompt | 2026-07-20 proctor design | `proctor.py` check 4 | ✅ | ✅ | ✅ | ✅ | ✅ | ▢ |
| C15 | Longest-option-is-answer bias > 55% (guessable course) | 2026-07-20 proctor design | `proctor.py` check 5 | ✅ | ✅ | ✅ | ✅ | ✅ | ▢ |
| C16 | Sources keys citing documents never consulted (provenance overclaim) | 2026-07-20, de/it decks (user question) | Gold Book Phase 8b; no mechanical gate possible | ✅ | ✅ | ▢ | ✅ | ✅ | ▢ |
| C17 | MCQ explanation names a distractor by POSITION ("the second option...") which the app shuffles at render, so the explanation can land on and disparage the CORRECT answer after shuffling | 2026-07-27, hr A0 days 15/16 | no automated gate yet; a proctor regex for "the (first\|second\|third\|last) option" across every explanation would catch it | ✅ | ▢ | ▢ | ▢ | ▢ | ▢ |
| C18 | Perfective/imperfective headword's own `example.target` shows only the OTHER aspect partner's form, never the headword itself | 2026-07-27, hr deck (8+ real hits: shvatiti, krenuti, odrediti, dozvoliti, obuhvatiti, olakšati, posvetiti se, sjediti) | no automated gate yet; a check for "headword stem present in example.target" would catch it mechanically | ✅ | ▢ | ▢ | ▢ | ▢ | ▢ |
| C19 | Grammar-note commentary concatenated directly into the target-language field (`hr`) instead of a separate `note`/`en` field, so TTS and the learner see partly-English "Croatian" | 2026-07-27, hr B1 days 311/312/315/316 (13 items, one late authoring batch) | no automated gate yet; a per-language ASCII-density check on `.hr`-suffixed keys would catch it | ✅ | ▢ | ▢ | ▢ | ▢ | ▢ |

*C10–C14 sweep counts (2026-07-20): hr 118, fr 56, pt 45, de 80 problems. 🔧 clears to ✅
when `proctor.py` runs clean on that language. hr re-verified clean 2026-07-27 after the
full-course audit and fix pass below.*

## II. Content: language-specific variety and orthography

| ID | Failure class | Found | Automated by | Applies | Status |
|---|---|---|---|---|---|
| V1 | Serbian drift in hr (da-constructions, ekavian, Serbian lexis) | field report, Tutor | `varietyRules("hr")`; model eval; `check_hr.py` batch gate (built 2026-07-21, negative-tested). On its first assembly run it CAUGHT A SHIPPED DEFECT: the A0 day-12 lesson taught 'Da li govoris?' as the correct MCQ answer, now fixed to the standard 'Govoris li?' | hr | ✅ |
| V2 | Brazilianisms in pt without European counterpart in same activity | pt expansion | Kotlin gate (activity-scoped) | pt | ✅ |
| V3 | Austrian/Swiss forms in de without standard counterpart in same activity | de build | `check_de.py` REGIONAL (whole-word both sides) | de | ✅ |
| V4 | Southern perfect auxiliaries (ist gesessen/gestanden/gelegen) | de build | `check_de.py`, scoped to produced-German keys | de | ✅ |
| V5 | Pre-reform sharp s (daß, muß, läßt) | de build | `check_de.py` | de | ✅ |
| V6 | Missing Italian accents where the bare form is not a word (perche, piu, e') | it build; 1 real hit in it_a2a | `check_it.py`, casing-aware for Sara/fara | it | ✅ |
| V7 | Wrong Italian article form (il studente, un amico, un'cane) | it build | `check_it.py` | it | ✅ |
| V8 | Passato remoto taught below B1 | it build | `check_it.py`, level-scoped (B1 recognition lesson exempt) | it | ✅ |
| V9 | Deck nouns without their article (gender unlearnable) | de convention; 3 real hits in it_vocab_a | ad-hoc verification scripts per deck | de, it, es | ✅ de/it |
| V10 | Article/gender cross-mismatch in deck (der X tagged n. f.) | de deck verification | ad-hoc verification scripts | de, it, es | ✅ de/it |
| V11 | False "distinctly Croatian" vocab notes: a standard Croatian word framed as the non-Croatian alternative to a register/loanword variant that is ALSO standard (insekt, redovno, protest, geografija, šporet, mobilni telefon all wrongly cast as "Serbian"), the same overclaim class check_hr.py exists to prevent, running backwards | 2026-07-27, hr deck (7+ real hits) | no automated gate; requires a native-checked whitelist of genuine hr/sr lexical pairs vs. register pairs | hr | 🔧 (7 found and fixed 2026-07-27; no gate built yet, re-sweep needed if more vocab is authored) |

*New language rule: es (and every future language) gets its own `check_<code>.py` covering its
drift modes (for es: Latin American forms vs Castilian, per the pt/Brazilian precedent), plus
V9/V10 if the deck carries articles. Every V-row is a candidate check for every new language.*

| V12 | Belgian/Swiss numbers (septante/octante/nonante) and Quebecois lexis/meal-name shift (déjeuner=breakfast, dîner=lunch) taught as standard Metropolitan French, or without a contrastive counterpart in the same activity | 2026-07-27, fr full-course audit (built check_fr.py — fr had no per-language checker before this) | `check_fr.py`, built and negative-tested this session (activity-scoped exemption, same design as check_de.py's REGIONAL table); found only 1 real hit course-wide (day 185's dialogue line dropped its own lesson's contrastive pairing), now fixed | fr | ✅ |
| V14 | Latin American Spanish lexis or voseo taught as a peninsular production target with no contrastive counterpart in the same activity (carro/coche, computadora/ordenador, celular/móvil, jugo/zumo, vos sos/tú eres) | 2026-07-29, es build (Phase 3, before any lesson existed) | `check_es.py` AMERICAN and VOSEO tables, activity-scoped, same design as `check_de.py`'s REGIONAL. Note the deliberate asymmetry with V2/V3/V12/V13: the DELE explicitly accepts any Hispanic norm in candidate production and its B1 input texts span all varieties, so the rule is about PAIRING, never banning, and B1 content is REQUIRED to expose American forms. Forms whose peninsular sense is an ordinary different word (papa, plata, saco, chico, lentes, departamento, tomar, manejar) are deliberately excluded, as are the voseo `-ís` forms, which are identical to the peninsular vosotros forms the course teaches | es | ✅ (built + negative-tested; no content yet) |
| V15 | Missing written accent or missing ñ in Spanish, restricted to forms that are not words at all without them (aquí, también, estación, año, español). The Spanish analogue of V6, and of the systemic missing-`è` bug found in Italian (open sweep 9); load-bearing because exam FILL answers are graded strictly | 2026-07-29, es build | `check_es.py`, plus a missing `¿`/`¡` check on whole produced sentences. Pairs whose bare member is also a real word (esta/está, si/sí, tu/tú, el/él, que/qué, como/cómo, hacia/hacía, sabia/sabía, ingles/inglés) are deliberately NOT matched and are reviewer items instead | es | ✅ (built + negative-tested) |
| V16 | Grammar taught ABOVE the course's B1 ceiling: imperfecto de subjuntivo (PCIC 9.2.2 is B2), and with it the counterfactual `si tuviera dinero, viajaría`, plus futuro perfecto, condicional compuesto and pretérito anterior. The Spanish analogue of V8, and expected to be the most-violated rule in the build, because nearly every commercial Spanish syllabus puts the counterfactual at B1 | 2026-07-29, es build (found by the Phase 1 PCIC level-split, not by an audit) | `check_es.py` IMPERFECT_SUBJ over unambiguous strong stems only, plus a compound-tense check that fires only when a participle actually follows (`habrá`/`habría`/`hubo` alone are good B1 forms of haber). `fuera` is deliberately absent from the stem list: it is also the ordinary adverb "outside" | es | ✅ (built + negative-tested) |
| V17 | Grammar taught ABOVE the current level but below the course ceiling: the future in -ré and the conditional are B1 in the PCIC, so both are off-syllabus at A1 and A2. check_es.py had checks for material above B1 (V16) but NOTHING for B1 material appearing at A2, so the whole middle of the ladder was unguarded. Found because a batch-6 authoring agent reported catching a future-tense distractor BY HAND and noted the checker did not test for it | 2026-07-30, es Phase 5 | `check_es.py` FUTURE_ABOVE_A2 and CONDITIONAL_ABOVE_A2, level-gated exactly like check_it.py's passato remoto. Only unambiguous forms are matched, and the exclusions were MEASURED against the six authored A1/A2 batches rather than guessed: `-éis` is out because `queréis` is the present of an -er verb whose stem ends in -er (the future is `querréis`), all first-person plurals are out because `queremos` contains "eremos", a generic conditional `-aría|-ería|-iría` is out because it collides with a whole noun class (librería, panadería, peluquería, cafetería, categoría) and with the name María, and `-aré` is IN with a listed exemption for the preterites of -ar-stem verbs (preparé, paré, declaré) because excluding it missed two of the three real defects. The stem guard is `\w*` and not `\w{2,}`, or the short irregulars iré/será/veré/daré slip through. 12 fixture cases, both directions. Immediately found 5 real violations across the 120 authored A1/A2 lessons |
| V13 | Brazilian-Portuguese lexis/progressive-gerund drift into European Portuguese content | 2026-07-28, pt full-course audit (built check_pt.py — pt had no per-language checker before this) | `check_pt.py`, built and negative-tested this session (mirrors the Kotlin `ContentValidationTest` Brazilianism gate's 17-word blocklist + activity-scoped exemption, plus a closed-whitelist gerund check after a naive `\w+ndo` regex over-matched "quando"/"lindo"/"Fernando"); deliberately did NOT implement tu-vs-você or ênclise/próclise as regex — both proven too syntactically ambiguous by real, correctly-taught course content (a whole lesson teaches nuanced `você` use; genuine ênclise/próclise triggers routinely sit in the PROMPT, invisible to a KEY-scoped answer check) — found 0 hits course-wide, deck was already clean | pt | ✅ |

## III. Structure, assessment and app integration

| ID | Failure class | Found | Automated by | Status |
|---|---|---|---|---|
| S1 | Lenient diacritic grading (é accepted for è) | field report, fr lesson 1 | `gradeFill` always strict; exam FILL `strictDiacritics` Kotlin gate | ✅ all |
| S2 | Exam FILL missing `strictDiacritics: true` | de integration (6 questions) | Kotlin gate | ✅ all |
| S3 | Wrong `passPercent` semantics (modular needs N per section, global needs null) | de/it research | `ExamRules` + tests; Gold Book Phase 6 | ✅ all |
| S4 | Placement anchors stale after a plan rebalance (band points at wrong level) | de integration (every band past A1 wrong) | Kotlin gate asserts anchor level == plan level; Gold Book: placement authored LAST | ✅ all |
| S5 | Placement bands with <3 items (silent under-placement under 2-of-3) | v0.20.77 review | Kotlin gate `placementBandsCarryExactlyThreeItemsEach` | ✅ all |
| S6 | Lesson `resources` strings not matching `resources.json` names | de integration; it caught pre-integration | assembler gate + Kotlin gate; Gold Book: freeze names BEFORE lesson authoring | ✅ all |
| S7 | Vocab packs out of ladder order (top-ups introducing A2 words after B1) | de assembly | `build_language.py` stable level sort | ✅ all |
| S8 | Course top-light vs guided-hours weighting (volume rule hiding thin B1) | 2026-07-20 (user question) | Kotlin `levelFloor` per language + debt map | 🔧 pt +70 (fr and hr CLEARED 2026-07-21) |
| S18 | Hand-reassembling a shipped deck lost/duplicated words because shipped vocab FILES contain multiple packs each (fr 00-a1-core.json holds 10), but the assembler and my splice both assumed one pack per file | 2026-07-21, fr integration | never rebuild a shipped deck; restore its files from git untouched and only ADD new pack files, inserting them into _index at the right ladder position |
| S17 | Deck LARGER than lessons × 10, so the tail never unlocks and the words are dead weight (de ships 2913 words against a 2850 cap, 63 unreachable) | 2026-07-20, de gap-close planning | the deck is a FIXED-CAPACITY pipe: vocabulary work is a SWAP, not an add. Author missing core words by displacing the least valuable existing ones, or add lessons to widen the pipe |
| S9 | Deck smaller than lessons × 10 (last lessons teach no words) | fr/pt expansion | Kotlin gate `everyDeckCoversTheWholeCourse` | ✅ all |
| S10 | Level-tag deck seeding reaching past the placement point (1886 words unlearnable) | v0.20.73, reverted | deck-index windows only; `PlacementSeedingTest` | ✅ |
| S11 | Stale composition state across language switch (day-8 A0 learner shown A1) | field report | `key(lang)` rule on every per-language screen | ✅ |
| S12 | New language missing from `TUTOR_LANGS` despite authored tutor content | de wiring | debug `assertTutorLangRegistered` | ✅ |
| S13 | New language missing Reminder branches (German learner greeted in Croatian) | de wiring | none possible; Gold Book Phase 2 checklist (3 branches) | ✅ de/it wired |
| S14 | `grammar.json` optional in code but expected by every shipped language | de integration | Gold Book Phase 2 file list | ✅ |
| S15 | Stored `currentLevel` default "A0" shown raw in A1-start courses | field report (pt fresh profile) | floor-at-plan-level rule in Words/Progress | ✅ |

## IV. Process (how errors get produced)

| ID | Failure class | Found | Prevention |
|---|---|---|---|
| P1 | Parallel vocab authors duplicating each other (376 lost in de, 300 in it) | de/it decks | disjoint slices + exclusion-list file + overshoot ≥15%; Gold Book Phase 4 |
| P2 | Exclusion file never written because chained behind a failing validator with `&&` | it top-up | never chain file writes behind validators; verify the file exists before dispatch |
| P3 | Session limit killing agents mid-authoring | de B1 (7 of 9), it B1 (3 of 3) | agents write output files BEFORE reporting; re-validate from disk, never re-author |
| P4 | In-flight work invisible to later sessions (scratchpad not in git) | de build | build path recorded in `docs/new-languages-plan.md`; tools promoted to `tools/course/` |
| P5 | Python mirrors drifting from the real Kotlin gates | de integration (3 misses) | mirrors are pre-checks only; Phase 9 always runs the full suite `--rerun-tasks` |
| P6 | Content-only changes silently skipping gates (test task doesn't track assets) | fr/pt expansion | `--rerun-tasks` always, encoded in Gold Book Phase 8f/9 |
| P7 | Retiring lessons by deleting authored files | de rebalance (avoided) | title-based retire lists with reasons, loud-fail on mismatch |
| P8 | Agent self-validation accepted as verification | it vocab (agent's "validated" file had 3 article misses) | every delivered batch independently re-verified; Gold Book Phase 4/5 |
| P12 | A large single-agent batch (45 lessons, 750 words) is disproportionately likely to hit a mid-response connection drop, wasting the whole run; observed twice on 2026-07-20 | 2026-07-20, fr B2 | keep a batch to ~25 lessons or ~500 words; the write-before-report rule still saves completed chunks, but smaller batches lose less and finish more reliably |
| P11 | Re-running the assembler AFTER auditing the assembled build silently discards every audit fix, because plan files are regenerated from the source batches (Italian went 0 problems back to 48 on a reassembly to add vocabulary) | 2026-07-20, it | audit LAST, after the final assembly; if a reassembly is unavoidable, keep the fix script and re-apply it, and always re-run the proctor after ANY assembly |
| P10 | An authoring RULE worded identically in every agent prompt becomes boilerplate in the output (the spiral-review rule produced "with one review point from an earlier lesson" in 17 Italian lessons) | 2026-07-20, it proctor | state the rule as an INTENT plus an explicit instruction to vary the surface wording; proctor catches the residue |
| P13 | "Agents write output files BEFORE reporting" (P3) is satisfiable while still losing everything, because it permits writing ONCE at the very end, just before the report. Two B1 vocab agents dropped mid-response on the same wave; one had composed all 150 words in its reply and was one tool call away from writing. Nothing survived. The rule protected against session limits, which kill the agent AFTER a write, and not against connection drops, which kill it before one | 2026-07-29, es Phase 4 B1 wave 1 (2 of 5 agents lost) | Sharpen the instruction from "write before you report" to **"write early and often"**: write the output file as soon as the first ~25% exists, then rewrite it with more as authoring continues. A partial file on disk is recoverable and a later agent can finish it; a perfect answer in a dropped response is gone. Encoded in the es VOCAB-SPEC and in every relaunch brief. Note the relaunch itself is cheap ONLY because the slices are disjoint and the exclusion file is authoritative, so a lost slice can be re-authored without touching the ones that survived. **VALIDATED THE SAME DAY, within the hour**: the next wave of five agents hit a shared session limit simultaneously (the classic P3 pattern, as in de 7-of-9 and it 3-of-3), and because they had been given the new instruction, 240 words survived on disk that the old rule would have lost entirely: one slice complete at 150, two partial at 40 and 50, and only the two that had not yet reached their first write were total losses. A partial file is genuinely resumable, because a relaunched agent can be told to APPEND to it rather than start over |
| P14 | Trimming an over-authored deck to its capacity ceiling needs a value signal, and the two obvious ones are both WRONG. (a) Corpus frequency: the OpenSubtitles rank proposed dropping `español` and `España` from a Spanish course, the entire A2 reflexive daily routine (ducharse, acostarse) that the plan teaches in a named lesson, and sugerir/aconsejar/recomendar/prohibir, the exact verbs grammar.json names as subjunctive triggers; earlier it proposed dropping the tens (treinta...noventa) and three weekdays, because film dialogue rarely says "ochenta" or "miércoles". Subtitle rank measures what dialogue says, not what a syllabus needs. (b) Named in the course's own topic files: better, but topic TITLES are not lesson content, so it flagged el brazo, el dedo, buenas tardes and perdón as untaught | 2026-07-30, es Phase 4 | **Trim AFTER the lessons exist, not before.** Lesson usage is the only sound signal: a word used in a lesson is needed by construction, and a word appearing nowhere across 250 lessons is a genuine candidate. Nothing downstream blocks on it, since the assembler dedups vocab itself and the Kotlin deck-size gate only runs at integration. Two secondary preventions were also encoded: a PROTECTED closed-class set in trim_deck_es.py (numbers, days, months, colours, immediate family) that frequency may never outvote, because a closed class is complete or it is wrong; and a lemma-aware frequency lookup, since the list holds word FORMS and every verb INFINITIVE looked absent, which had `llamarse` proposed for deletion |
| P9 | Claiming feature gaps without auditing the implementation (TTS listening and AI writing feedback both existed while being reported as missing) | 2026-07-20 roadmap discussion | audit the code BEFORE writing any gap analysis; a roadmap claim about the app is a claim about code and gets verified like one |

## V. Checker bugs (the checks themselves are code and fail)

| ID | Failure class | Found | Prevention |
|---|---|---|---|
| K1 | Substring match letting the English gloss excuse the error ("January" excusing "Jänner") | check_de v1 | whole-word matching on BOTH sides of a counterpart rule |
| K2 | Scanning English commentary that names a wrong form in order to reject it (4 rounds of exemption-patching) | check_de v2-4 | scope by KEY: only `hr`/`target`/`answer`/`options`/`ordered`/`accepted` |
| K3 | Flagging correct words as unaccented (Sara the name, te the pronoun, meta the noun) | check_it v1 | only list forms that are not words without the accent; casing-aware for name collisions |
| K4 | Flagging deliberate ritual repetition as boilerplate (Words-tab nudge, 185×) | proctor v1 | exempt phrases naming app surfaces; repetition by design is not a defect |
| K5 | A loosened check silently becoming a no-op | risk, de | keep a planted-defect fixture per checker; re-run it after EVERY change |
| K7 | A stem-with-suffix regex over-matching a legitimate word family (check_hr's voz\w* flagged vozac/vozilo/voziti, all standard Croatian, when only the Serbian train noun voz is the target) | 2026-07-21, hr | match a Serbian noun by its exact case-forms, not a stem+\w*, when a legitimate same-stem family exists |
| K6 | Flagging a correct inflected form as the wrong variety (check_hr flagged "vremena", the correct Croatian genitive of vrijeme, as ekavian) | 2026-07-21, hr | list only forms that are the wrong variety in EVERY inflection; the ije-to-e alternation is regular in Croatian oblique cases, so match bare "vreme" not the "vremen-" stem |
| K8 | check_hr.py's Serbian-drift regexes were ASCII-only (no š/č/ć/ž/đ character classes), so they never matched real shipped Croatian, which always carries proper diacritics; the checker had been auditing nothing since the day it stopped seeing pre-diacritic planning drafts. Also could not parse the assembled `{title, days}` phase-file shape at all (expected a bare array), so it silently skipped the entire shipped course | 2026-07-27, hr full-course audit | rewrote the regexes with `[sš]`/`[cč]`/`[zž]`/`(?:đ\|dj)` character classes matching both spellings; added a `{title,days}` unwrap to both check_hr.py's CLI and check_batch.check_file (new check_batch.check_file_obj helper); negative-tested against both ASCII and diacritic-bearing planted defects post-fix |
| K9 | check_batch.py's CLI reported the wrong day count for assembled `{title, days}` files (`len()` on the top-level dict counted its 2 keys, not the days array), so every run of the script printed "2 days" regardless of the file's real size | 2026-07-28, hr structural sweep | count `len(obj["days"])` when the top level is a `{title, days}` dict, else `len(obj)` |
| K10 | check_batch.py's FILL answer-leak check (`norm()` strips diacritics from both sides before comparing) false-positived on diacritic-restoration drills, where the prompt intentionally shows the undiacriticized form of its own answer — the exercise's whole point looks like a "leak" once diacritics are stripped from both sides | 2026-07-28, hr structural sweep | exempt questions with `"strictDiacritics": true` from the answer-leak check |
| K11 | check_hr.py's `DA_PRESENT` modal list wrongly included `znam`; "znam da..." (I know that...) is a complementizer construction correct in both Croatian and Serbian, not the modal-plus-infinitive alternation the check targets (real Serbian drift is `moram da radim` for correct `moram raditi`) — flagged 4 correct sentences as Serbian drift | 2026-07-28, hr structural sweep | removed `znam` from the modal set; negative-tested against `Znam da dolazi vlak` (must not match) alongside `Moram da radim` (must still match) |
| K12 | check_hr.py's `EKAVIAN` list included the oblique forms `leta`/`letu` as ekavian reflexes of `leto` (summer, ijekavian `ljeto`), but these collide with the genitive/dative of the unrelated, jat-free noun `let` (flight) — flagged "odgoda leta" (flight delay), correct Croatian, as ekavian | 2026-07-28, hr structural sweep | removed `leta`/`letu` from the list, kept bare `leto` (no such collision: flight's nominative is `let`, never `leto`) |
| K13 | check_de.py's `check_german()` never unwrapped the assembled `{title, days}` shape (`if not isinstance(days, list): return []` silently exited on every real course file, only pre-merge bare-array batches ever got checked) — the exact same silent-no-op failure mode as K8, meaning the Austrian/Swiss-drift, southern-perfect-auxiliary, and Swiss-ß checks had never run against shipped German content | 2026-07-28, de audit kickoff | added the same `_unwrap()` helper used in check_hr.py/check_fr.py to both `check_german()` and the CLI's day-count; negative-tested against a planted `{title, days}`-wrapped Jänner/southern-perfect defect (found) and a contrastive same-activity pair (correctly not flagged) before trusting a real-content run |
| K14 | check_de.py/check_batch.py both hard-crash (`AttributeError`) on the vocab-pack `{"packs":[{"words":[...]}]}` shape and on the non-plan shapes of quizzes.json/placement.json/exams.json — neither ever validated the German deck or assessment content at all, a distinct gap from K13 (which only fixed the plan-file unwrap) | 2026-07-28, de audit | FIXED same day: ported check_pt.py's `_is_day_shaped()` / generic-file-fallback pattern into check_de.py (`check_german_generic()`, `_generic_distractors()` for the MCQ-distractor exemption on non-day shapes); the CLI now branches on shape instead of assuming one. Re-run against every previously-unreachable de file immediately caught one real pre-existing defect (`quizzes.json` had "der Fluß", pre-reform spelling, as the FILL answer, not just a distractor) |
| K15 | check_de.py's Swiss-spelling check (`SHARP_S_ERRORS`) only catches the pre-reform direction (ß written where ss belongs); it has no check for the opposite, actually-live drift direction — Swiss orthography eliminating ß entirely (ss for ß, e.g. "grosse" for "große") — found live in shipped content (a FILL question silently accepted "grosse" as correct with no contrastive note) | 2026-07-28, de audit | FIXED same day: added `gross/grosse/grosser/grosses/grossen/grossem`, `weiss`, `heissen`, `aussen`, `draussen`, `fuss`, `strasse(n)`, `gruss`, `grüsse` → their ß-forms, plus the Austrian-retention exception `erdgeschoß`→`erdgeschoss`, to the existing REGIONAL dict (same activity-scoped contrastive-teaching exemption as every other entry). This immediately surfaced a SECOND checker bug (see K16) before the fix could be trusted |
| K16 | The REGIONAL-form check (used by both check_german() and, transitively, check_german_generic()) scanned every string in a day/activity, including English `en`/`note`/commentary fields, not just the German-bearing keys — a scoping bug distinct from every other check in the file (which are correctly KEY-scoped to hr/target/answer/options/ordered/accepted). Latent since the checker was first written, but never triggered because no original REGIONAL entry (jänner, sackerl, semmel...) was also a common English word; the K15 fix's `gross`/`fuss` additions are, and immediately false-flagged "My salary is 2,800 euros **gross** per month" and "Do not make such a **fuss**" as Swiss regional drift | 2026-07-28, de audit (found while trusting the K15 fix) | FIXED: the REGIONAL loop in check_german() now scans `german_strings_of(a)` instead of the unscoped `strings_of(a)`; check_german_generic() was rewritten to use a new path-scoped `_german_strings()` helper (mirrors german_strings_of() but works on arbitrary JSON shapes via check_batch.walk_strings) for all three of its checks, not just REGIONAL. Negative-tested against both the planted English-collision case and a Croatian-audit-precedent real-drift case before trusting a final real-content run (0 problems, 285 days) |
| K17 | check_it.py's `check_italian()` never unwrapped the assembled `{title, days}` shape (`if not isinstance(days, list): return []`) — the third occurrence of this exact silent-no-op bug class (K8 in check_hr.py, K13 in check_de.py, now check_it.py), meaning the missing-accent/wrong-article/passato-remoto checks had never run against real shipped Italian content since the checker was written. Also crashed outright on vocab-pack/quizzes/placement/exams shapes (the same K14 class). Found at the start of Italian's first-ever audit, immediately before dispatching it, not after | 2026-07-28, it audit kickoff | FIXED proactively in one pass (all three fixes applied before the checker was ever trusted, rather than found incrementally the way de's were): added `_unwrap()`/`_is_day_shaped()`, ported check_de.py's generic-shape fallback (`check_italian_generic()`, `_generic_distractors()`), all correctly KEY-scoped from the start (no K16-equivalent needed: MISSING_ACCENT_LOWER's forms — sara/fara/andrà/verrà/potrà/dovrà/vorrà — are not common English words, so the English-collision class doesn't apply here, but the code is structured the same defensive way regardless). Negative-tested with 8 planted cases (missing accent, correct accent, passato remoto at A1 vs. B1, MCQ-distractor exemption, generic-shape parsing, no-crash-on-quiz-shape, wrong article) before trusting a real-content run — which immediately surfaced a large, previously-invisible finding: a systemic missing-"è" bug (the copula "è" written as bare "e", the conjunction) spanning at least 15 of 18 vocab packs, likely from an accent-stripping bug during authoring, now the subject of a full Phase 8 audit |

| K18 | A candidate regex colliding with ORDINARY WORDS OF OTHER LANGUAGES sitting in scoped keys. check_es.py's first draft matched `\w+[cs]ion\b` to catch Spanish nouns missing their accent (estacion for estación). Running that exact pattern over the scoped keys of all five shipped courses returned 40+ distinct `-sion` hits, every one an ordinary French or German word (conclusion, décision, télévision, pression, expression, version, discussion, profession, occasion) and not one a real error; `-cion` returned ZERO, because no English, French or German word ends in it. Same class as K1/K16, but caught BEFORE shipping | 2026-07-29, es build (Phase 3) | The technique, which generalises to every future checker: **measure a candidate regex against the scoped strings of the already-shipped courses before trusting it, and treat any hit there as a false-positive class by construction**, since none of that content is in the new language. Applied twice more in the same pass: over the whole accent/ñ/seseo/subjunctive list (0 genuine English collisions across 788 English-looking scoped strings), and to split name-colliding forms (dia, mia, tio, tia, leon) into a lowercase-only list, the K3 precedent. `-sión` is now a closed list of the nouns Spanish spells with a single s where English uses ss or cc (profesión, discusión, misión, ocasión), and the ones whose bare form IS an English word (decision, television, version, tension, division, revision, dimension, pension, confusion, conclusion) are deliberately unmatched: zero false positives is worth a few known misses |
| K19 | verify_deck_es.py's headword-in-example check (registry C18 in Spanish clothing) matched on a consonant skeleton, which is stable across the stem-VOWEL changes of radical verbs (poder/puedo, querer/quiero, pedir/pido) but NOT across Spanish's orthographic alternations, where the spelling changes precisely to keep the sound constant. The first real authored pack flagged `coger` against 'Cojo el paraguas' and `recoger` against 'Recojo mis libros', both perfectly correct Spanish. Two false positives on the first 130 real words, i.e. the K7 over/under-matching class rather than a content defect | 2026-07-29, es Phase 4, first delivered vocab pack | Collapse the alternating pairs onto one letter before comparing: j->g (coger/cojo, elegir/elijo), z->c and q->c (vencer/venzo, empezar/empiece, buscar/busque, conocer/conozco), and drop silent h (oler/huelo). Four cases added to the kept fixture, one per alternation, alongside the existing case proving a genuinely unrelated verb example still fires, so the fix is provably a narrowing of the false positives and not a loosening of the check. **The same heuristic then needed a SECOND narrowing on the very next pack**: adjectives inflect for gender at the END, so a prefix of the whole word fails, and `bajo` does not appear in 'Mi hermana es baja' nor `rubio` in 'Mi hija es rubia'. Strip the inflecting final vowel for `adj.` before matching. Three narrowings in total (stem vowel, orthographic alternation, adjective gender ending), every one found by real authored content within the first 390 words, which is the argument for verifying batch 1 before dispatching batch 2 rather than authoring the whole deck and auditing at the end |
| K20 | verify_deck_es.py required a definite article on every noun (the V9 rule, since gender is unlearnable without it) and therefore flagged every country name in the first people-and-places pack: España, Francia, Italia, Alemania, Inglaterra, Portugal. Spanish proper nouns take no article, so the rule is inapplicable to them rather than violated by them | 2026-07-29, es Phase 4, second delivered vocab pack | Exempt a single capitalised token from the article requirement, and narrowly: one token with an initial capital, so a multi-word name keeps its article (el Ebro) and a capitalised common noun cannot escape. The gender then has nowhere to live, so the exemption REPLACES the article requirement with a note requirement rather than dropping the check, matching the shipped German precedent (Deutschland, n. n., 'proper noun, used without an article'). Three fixture cases pin all three directions |
| K21 | verify_deck_es.py flagged all 7 days and all 12 months as nouns missing their definite article, and flagged the articles and object pronouns (el, la, los, las) as nouns carrying one. Both are the article rule applied where it does not apply: the Gold Book Phase 4 text NAMES days and months as the standing exception ("el enero" is unnatural Spanish, and the shipped fr/it/pt courses all list them bare), and a single-token headword that IS an article is not a noun carrying an article. The authoring agent flagged the days/months tension in its own report before the verifier ran, and was right | 2026-07-29, es Phase 4, fourth delivered vocab pack | Closed-set exemption for the 19 calendar words, and the article-carrying check restricted to multi-token headwords. Six fixture cases pin both directions: an ordinary noun still needs its article, and a real noun phrase with a non-noun pos still fires. Together with K19 and K20 this makes FOUR verifier narrowings across the first 520 authored words, every one a false positive rather than a content defect, which is the strongest argument in this build for the Gold Book's verify-each-batch-on-arrival rule over authoring the whole deck and auditing at the end |
| K22 | verify_deck_es.py rejected three classes of CORRECT Spanish across the A2 packs, and one of them was the check actively causing the defect it was meant to prevent. (a) EPICENE NOUNS: it required the article to agree with the declared pos, so `el turista` tagged `n. m./f.` failed. An epicene noun has one form for both genders and is distinguished ONLY by its article, so forcing agreement reproduces the exact defect the pt audit found, where epicene nouns were wrongly locked masculine. (b) PHRASE HEADWORDS: 'Cogemos el metro para ir al centro' is a perfect example of `coger el metro` and contains none of it verbatim, because a phrase's parts inflect and reorder independently. (c) IRREGULAR PARTICIPLES: romper/roto shares no usable stem with its infinitive. A fourth, `el mismo` tagged `adj.`, exposed a check that could only ever be right by accident | 2026-07-29, es Phase 4, A2 packs | (a) `n. m./f.` accepts either article, with a case proving a genuinely gendered noun must still agree. (b) a multi-word headword is satisfied by one substantial token, with the token also reduced as an infinitive so `vivir con` matches 'Vivo con mis padres'. (c) 21 irregular participles listed EXPLICITLY rather than exempting the whole verb, which would switch the check off for that verb's regular forms too. (d) the article-carrying check narrowed to pos values an article cannot attach to (v./adv./prep./conj.) and deliberately NOT applied to adj./pron., because Spanish cites real determiner and pronoun phrases with their article (el mismo, la mía, el primero) and no mechanical test separates those from a mis-tag without a lexicon. **Standing note: this whole heuristic has now needed seven narrowings and has caught zero real content defects across 1,370 authored words while producing twelve false positives.** It is kept because C18 was real and expensive in hr, and because every narrowing was a genuine fact about Spanish rather than a loosening, but it is the highest-maintenance check in the toolchain and that should be known before it is copied to a seventh language |
| C20 | An off-syllabus construction hiding in an MCQ DISTRACTOR rather than in taught content. The distractor exemption every per-language checker carries exists so a lesson can print a wrong form in order to reject it, and it is correct for the VARIETY and ORTHOGRAPHY checks. It is wrong for the LEVEL ceiling: a wrong option is still learner-visible, so a B1 tense sitting in an A2 distractor teaches that tense anyway, and a learner who cannot parse the option cannot use it to answer either. Three real instances in the es A1/A2 lessons, all of them CORRECT Spanish used as a register or meaning contrast the learner had no way to evaluate; one was worse, an MCQ whose ANSWER was "the option you have not been taught", which tests unfamiliarity rather than Spanish | 2026-07-30, es Phase 5 | `check_es.py` runs the level-gated checks over distractors too while still exempting them from the variety and orthography checks, and says so in the message. Also encoded upstream in the Gold Book Phase 5 and in every remaining authoring brief: a wrong option must be wrong for a reason the learner can ALREADY understand, meaning wrong person, wrong tense among tenses they know, or wrong auxiliary, never a tense they have not met |

---

## Open sweeps (the queue this registry exists to drain)

1. **C10–C14**: DRAINED for all four shipped languages 2026-07-20 (hr 118, de 80, fr 56, pt 45, all to zero, independently verified); it audits at assembly. The CI proctor step can flip to hard-fail.
1a. **hr full-course audit (2026-07-27)**: 19-agent audit (11 lesson-range reviewers, 4 deck
   reviewers, quizzes/placement, exams, reference, syllabus) plus `proctor.py` + full Kotlin
   gate + a diacritic-aware Serbian-drift sweep. Found and FIXED: 31 Critical (wrong clitic
   order stated in 5 places incl. `feynman.json`, 13 English-contaminated `hr` fields in one
   batch, a gender-agreement dialogue, a broken exam answer key, a broken quiz answer key, 3
   reversed/corrupted `grammar.json` table cells), 49 High (case-government errors, aspect-
   partner mismatches, false-Serbianism vocab notes = V11, real institutions/athletes named in
   lesson text, an MCQ-shuffle-position bug = C17), plus most Medium findings. Deck trimmed
   90→ back to exactly the 3,440-word cap by removing 76 off-level/redundant B1 words.
   `check_hr.py` itself was broken (K8: ASCII-only, couldn't parse the assembled format) and is
   now fixed and negative-tested. One judgment call surfaced a conflict with
   `[[corlang-session-resume]]`'s 2026-07-18 note "HRT immersion mentions kept deliberately" —
   this audit treated repeated HRT/Ruđer Bošković Institute/Dinamo/Hajduk naming as boundary-rule
   violations and genericized all of it, later confirmed by the user as the standing rule
   (real people/institutions banned everywhere in lesson text, `docs/course-gold-book.md`
   updated 2026-07-27).
   **CLOSED 2026-07-28**: the 314 `check_batch.py` structural findings (recounted precisely at
   308: 168 missing-DIALOGUE, 57 3-option MCQs, 55 over-length dialogues, 27 duplicate prompts)
   were fixed via 11 parallel batched-range authoring agents across all 4 phase files, followed
   by a consolidation pass closing 26 cross-range duplicate prompts the per-range agents
   correctly couldn't see (duplicate detection needs whole-file context) and 1 pre-existing
   answer-leak false positive (K10). Found and fixed 4 checker bugs in the same pass (K9-K12).
   `proctor.py` caught 3 new DIALOGUE-intro-repeats-objective problems introduced by the batch
   authoring; reworded and reverified to 0. The ASOO A1 directional-preposition unit (u/na/po/za
   + accusative, ići present) was authored into day 24 ("Prepositions: motion vs. location"),
   expanding an existing day rather than inserting a new one, so no day-numbering shift touched
   placement.json/exams.json/resources.json. Full re-verification: `check_batch.py` 344 days/0
   problems, `check_hr.py` 344/0, `proctor.py` 0 problems, Kotlin `ContentValidationTest` green.
2. **C16**: de CHECKED 2026-07-20 (DWDS mirrors + all three official PDFs, complete): deck
   covers 46.3% of the official A1..B1 inventory, 262 official A1 lemmas absent entirely, so
   the `goethe-wortliste` key was removed from all 22 packs, then EARNED BACK the same day for
   A1 and A2 by a 477-word gap-close and a 540-word swap that lifted coverage to 81.9% and
   89.2%; it stays off the B1 packs, whose 41% coverage cannot be fixed without more lessons. **it CHECKED and ACTED ON before shipping**: the cross-check found 5 required
   structures missing (demonstratives from A1 among them), all now authored, plus a 300-word
   fondamentale top-up against a measured 56.2% coverage gap. **fr CHECKED**: three keys were pure overclaims (referentiel-fr on 723 citations, plus
   francais-fondamental and freq-fr; none of those documents was ever fetchable). referentiel-fr
   re-pointed to the Eaquals/CIEP Inventaire which WAS fetched complete, the other two retired in
   favour of Lexique 3.83. hr and pt digests still unchecked.
4. **C4**: live re-verification of hr and fr resources.json URLs; de's DW link was confirmed
   by search only (fetcher blocks dw.com), noted in its digest.
5. **S8**: weighted-floor debt, fr +145 (legally B2, priority) → hr +90 → pt +70.
6. **fr full-course audit (2026-07-27)**: no per-language checker existed for fr before this
   session; wrote and negative-tested `check_fr.py` (V12), then ran a 24-agent audit (16 over
   all 418 lessons with extra rigor on B2, the legally load-bearing level, 5 over the 4,223-word
   deck, plus quizzes/placement/exams/reference/syllabus). Found and FIXED: 20 Critical (13
   real people/institutions incl. Camus, Victor Hugo and France Éducation international named
   in `grammar.json`/`levels.json` itself; `grammar.json` missing accents on every French word
   in the whole file, all 37 topics restored; a mock-exam elision bug; 5 self-contradicting
   grammar rules where the course's own other examples proved the stated rule wrong), 41 High
   (6 epicene nouns tagged masculine-only contradicting their own example; a DALF C1 task
   labelled "the DELF B2 synthesis task", a real risk given B2 is the legal citizenship target
   here; all 4 DELF mocks missing required document types, now authored). Also closed: 4
   placement bands under-diversified (same pattern as hr), all 4 quiz difficulty orderings,
   the recurring FILL-punctuation-artifact pattern (11 instances), the œ-ligature spelling gap.
   **CLOSED 2026-07-28**: `decret-2025-648` (the registered but previously-uncited naturalisation
   law) is now cited in the 3 activities that assert the fact (days 373/395/403 of
   `phase4-b2.json`), in the B2 `exam.sources` in `levels.json`, and in the `fr-b2-society`
   vocab pack's sources. `grammar.json`'s topic index was resynced with 6 real new topics found
   by cross-checking day titles against the existing 38: A2 exclamations (quel/que/comme), B1
   compound relative pronouns (lequel/auquel/duquel), B2 l'infinitif passé, B2 condition beyond
   si (à condition que/pourvu que/à moins que/au cas où), B2 interrogative lequel, B2 cause/
   consequence in formal register (en raison de.../si bien que...); the existing B2 "present
   participle" topic was expanded in place to add the adjectif verbal agreement contrast rather
   than duplicated, and 2 lessons (357 "se plaindre au téléphone", 358 "conditionnel passé:
   regret/reproche/conseil") were confirmed to be functional-language or already-covered content,
   not new grammar points, and correctly left out.
7. **pt full-course audit + fix DONE (2026-07-28)**: no per-language checker existed for pt
   before this session; wrote and negative-tested `check_pt.py` first (V13). Then a 7-agent
   audit (4 over all 170 lessons, 1 over the 2,568-word deck via 8 sub-reviewers, 1 over
   quizzes/placement/exams, 1 over reference content + Phase 8b CAPLE/Camões syllabus
   cross-check — the syllabus itself checked out clean, no curriculum gap found). Found and
   FIXED: 85 Critical (real people/institutions the largest single class by far — Amália
   Rodrigues, Camões, Fernando Pessoa, José Saramago, Gil Vicente, CAPLE, Universidade de
   Lisboa, Livraria Lello, Câmara Municipal de Braga with a realistic fabricated contact block,
   real CP train brands Intercidades/Alfa Pendular, Multibanco, Benfica, concentrated 12-deep in
   one vocab pack `24-b2-culture-arts.json`; also 34 3-option MCQs in one 10-day span, epicene
   nouns wrongly locked masculine, broken idiom headwords missing the word that makes them
   grammatical, a translation leak, missing-diacritic self-contradictions within the same
   lesson), 45 High (CIPLE mock's weighting silently wrong vs. the real 45/30/25 split, every
   mock's reading/listening sections single-document against the real plural-document format,
   3 placement-band items testing content taught days later, quizzes shipping 8 questions
   instead of 10, an untaught future-subjunctive construction, a reversed phone-greeting
   convention, an "acabar de" sense self-contradicted 6 days later, 17 consecutive B1 days on a
   visibly thinner authoring template than their neighbors). One agent was killed by a
   connection error near-zero-progress and cleanly relaunched (session-limit-kill resilience
   pattern holds for API errors too). One cross-agent conflict caught in re-verification: an A2
   fix agent, confused by an unrelated concurrent edit's side effect, reverted a systemic
   title fix ("rumo ao DIPLE" → "rumo ao DEPLE", pt's course targets the B1 diploma not B2) on
   its own file — reapplied directly. Verified: `check_batch.py`/`check_pt.py` 0 problems,
   `proctor.py` 0 problems (1 new finding from the batch fixes, fixed same pass), Kotlin
   `ContentValidationTest` gate green, 0 dashes, real-name sweep clean (distinguishing the
   sanctioned `resources.json` mechanism and legitimate exam-format-label/city-name usage from
   actual violations). `docs/sources/README.md` and `caple.md`'s stale "target DIPLE B2"
   headers corrected to the real target (DEPLE B1, DIPLE B2 legacy since 2026-07-20).
8. **de full-course audit + fix DONE (2026-07-28)**: check_de.py existed but its `check_german()`
   had the K13 silent-no-op bug (fixed first, negative-tested, before trusting any result).
   During the fix pass, two more checker bugs were found and fixed the same day: K14 (vocab/
   assessment file shapes crashed the checker outright — ported check_pt.py's generic-shape
   fallback pattern; immediately caught one real pre-existing defect, "der Fluß" as a quiz
   ANSWER rather than a distractor) and K15 (no check for the live Swiss ss-for-ß drift
   direction, only the reverse — added it, which surfaced K16, a latent English-collision
   scoping bug the new entries exposed, also fixed same day). Then a 13-agent audit (10 over all 285
   lessons, 1 over the 2,850-word deck, 1 over quizzes/placement/exams, 1 over reference content
   + Phase 8b Goethe/telc syllabus cross-check — clean, no curriculum gap found) — all 13 hit a
   shared session-limit wall simultaneously and were cleanly relaunched. Found and FIXED far
   fewer Critical findings than hr/fr/pt (de is a noticeably cleaner-authored course: **zero**
   real-people/institution violations anywhere in the 2,850-word deck, a first for this project):
   ~9 Critical (2 self-contradicting grammar explanations — a false stem-vowel claim, wrong
   ge-prefix reasoning; a wrong dative-plural rule; a `goethe-wortliste` source-key regression
   affecting 431 citations across `grammar.json`'s B1 section and every B1 lesson activity,
   re-introducing an overclaim a 2026-07-20 digest had explicitly closed; and the session's most
   significant single finding — B1 days 220-223 taught a dedicated civics mini-unit quoting
   Article 1/20 GG verbatim and naming the Bundesverfassungsgericht by name, reinstating the
   exact "institutions of state are exempt" pattern the Gold Book's carve-out removal was written
   to prevent; fixed by genericizing only day 222, the one day actually containing verbatim text/
   named-institution violations, while leaving days 220/221/223's generic constitutional-organ
   vocabulary — Bundestag, Bundeskanzler — untouched as legitimate, non-violating usage). ~21 High
   (5 placement bands across all 4 levels testing content taught days-to-weeks later, all 6 mock
   exam Lesen/Hören sections single-document against the real multi-Teil Goethe/telc format,
   B1 Schreiben missing its 3rd task, quiz difficulty ordering broken in 3 of 4 levels, 2 untaught-
   vocabulary-used-in-exercises gaps, Swiss "grosse" silently accepted as correct with no
   contrastive note — the exact K15 gap surfacing in real content, a repetitive boilerplate drill
   stamped into 6 lessons unrelated to its own grammar point, 8 duplicate reflexive-verb SRS
   entries). Removing the 8 duplicate vocab entries dropped the deck 8 words under the
   `everyDeckCoversTheWholeCourse` floor (2850 needed for 285 lessons x 10/day) — caught by the
   Kotlin gate, closed by authoring 8 new, genuinely non-duplicate B1 words rather than reverting
   the dedup fix. Verified: `check_batch.py`/`check_de.py` 285 days/0 problems, `proctor.py` 0
   problems (1 new objective-echo finding from the civics rewrite, fixed same pass), Kotlin
   `ContentValidationTest` gate green, 0 dashes, real-name sweep clean.
9. **it full-course audit + fix DONE (2026-07-29)**: `check_it.py` had the K17 silent-no-op bug
   (same class as K8/K13, fixed and negative-tested proactively at audit kickoff, with all of
   K13-K16's lessons baked in from the start — generic-shape fallback, KEY-scoping, no
   English-collision gap). A first-ever real check_it.py run on live content immediately
   surfaced a systemic missing-`è` bug (bare `e` standing in for the copula) concentrated
   entirely in `05-a2-descriptors.json` (70 instances) plus 10 missing elision apostrophes in
   the same file — 6/9 other 00-08 vocab files and all 9 of the 09-17 files were confirmed
   clean of this class by close read, refuting the task brief's wider claim. A 4-phase audit
   (plan days 1-245, vocab packs 00-17, quizzes/placement/exams, reference content + Phase 8b
   syllabus cross-check) found and FIXED, all directly (subagent spawn cap hit mid-session —
   confirmed cumulative not concurrent — remaining work done with Edit/Bash instead of agents):
   8 Critical + 11 High + 47 Medium/Low in `phase1-a1.json` (66 findings: untaught-grammar
   REORDER/FILL items before their lesson, self-contradicting stress/h-insertion/article rules,
   an MCQ answer-leak via article, missing FILL `accepted` case-variants, several incoherent or
   off-topic dialogue lines); 2 in `phase2-a2.json`; a civics mini-unit at B1 day 204 whose
   `paretoFocus` explicitly claimed to be "part of the citizenship syllabus" — the exact
   overclaim `docs/language-standard.md` bans, independent of and in addition to the generic
   institution-naming question (institution names — Parlamento, Camera dei deputati, Senato,
   Presidente della Repubblica — were correctly left alone as the Italian equivalent of
   "Congress"/"the Chancellor", mirroring the de day-222 precedent); 5 real-institution findings
   (Università per Stranieri di Siena x2, Ministero dell'Interno x2, plus a 5-institution
   concentration in one `feynman.json` teach-back concept) and 2 real-person findings (Italo
   Calvino, Dante Alighieri with real biography) in `levels.json`/`meta.json`/`feynman.json`/
   `grammar.json`; a missing 5th CILS `GRAMMAR`/`strutture` section across all 3 mock exams
   (added, 8 questions each, matching each level's already-taught grammar); all 6 receptive
   exam sections (ascolto/lettura x3 levels) single-document, matching the exact gap already
   found and fixed in pt/de this session (added a second passage + 3 questions to each); A1/A2
   exam `passPercent` at 55 instead of the sourced 58.3% (7/12); 3 Critical + 2 High + 1 Medium
   `placement.json` bands testing grammar taught after their own anchor day (2 bands moved
   anchor day forward to where their content is actually valid, 2 bands' items replaced with
   genuine day-1/day-12/day-23/day-34-taught content); 4 `grammar.json` topics the plan had
   taught since a 2026-07-20 digest fix but the standalone reference never mirrored
   (dimostrativi, indefiniti/esclamative, che relativo at A2, enclisi/interrogative indirette —
   authored and the now-stale digest updated to record all 8 of its 2026-07-20 follow-ups as
   closed, not open). Two self-inflicted regressions caught and fixed by the Kotlin gate itself
   before commit: `PlacementQuestion` has no `accepted` field and requires `options` even for
   fill-style items (converted 4 FILL replacements to MCQ), and every exam FILL must carry
   `strictDiacritics: true` unconditionally because the runner's field label promises it
   exam-wide, not per-question (an initial "meaningless flag" judgment on 2 numeric answers was
   wrong and reverted). `freq-it`'s pre-existing PARTIALLY EARNED status (56.2%/22.6% coverage)
   is unresolved by design — flagged for the user rather than unilaterally decided, matching the
   digest's own framing. Verified: `check_batch.py`/`check_it.py`/`proctor.py` 0 problems (245
   days), Kotlin `ContentValidationTest` gate green (39/39), 0 dashes, real-name sweep clean.
10. **es build IN PROGRESS (started 2026-07-29)**: the first Corlang language built AFTER the
   registry existed, so the registry was used as the pre-flight rather than the post-mortem.
   Every V-row was read as a candidate check and every P-row as a trap to avoid, before a line
   was authored. Phases 0 to 3 are committed; the `es` column above is `▢` throughout because
   no content exists yet, and it is swept at Phase 8, not before.
   What the pre-flight produced: `check_es.py` was written with K1/K2/K3/K5/K8/K13/K14/K16/K17
   all designed in from the first line (KEY scoping, assembled-shape unwrap, generic-shape
   fallback, whole-word both sides, unambiguous forms only) rather than discovered one silent
   no-op at a time the way check_hr/check_de/check_it each were. It ships with a kept fixture
   (`tools/course/fixtures/es_checker_fixture.py`, 33 cases, 18 planted defects and 15 correct
   cases that must stay quiet), and the fixture was itself verified by neutering each of the
   nine checks in turn and confirming it failed each time, so it is known not to be a no-op.
   K18 was found and closed during that work. New rows V14, V15, V16 registered.
   Still open for es and tracked here until Phase 9: the whole C1-C19 column, the C4 live
   resource re-verification (done once at Phase 2, to be redone before ship), C16 provenance
   (`pcic` is EARNED for grammar but NOT for vocabulary until the Phase 8b deck cross-check,
   and `freq-es` is ordering-only), and the S-row app-integration checks, none of which can run
   until the content folder exists.
