package com.corlang.app

import com.corlang.app.data.model.Cheatsheet
import com.corlang.app.data.model.FeynmanSet
import com.corlang.app.data.model.LanguageMeta
import com.corlang.app.data.model.Levels
import com.corlang.app.data.model.QuestionType
import com.corlang.app.data.model.QuizSet
import com.corlang.app.data.model.ResourceList
import com.corlang.app.data.model.StudyPlan
import com.corlang.app.data.model.VocabPack
import com.corlang.app.data.model.VocabSet
import com.corlang.app.ui.screens.Grading
import com.corlang.app.ui.screens.PAIR_SYMBOLS
import com.corlang.app.ui.screens.recallCandidates
import com.corlang.app.ui.screens.wrapupRecallPhrases
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.Normalizer

/**
 * The permanent content quality gate. Reads the REAL asset JSON from src/main/assets and
 * enforces structural + provenance invariants, so unvalidated or corrupted content can never
 * ship silently. See docs/sources/README.md for the provenance rule.
 */
class ContentValidationTest {

    /** Strict: unknown/typo'd field names fail the parse instead of being ignored. */
    private val strictJson = Json { isLenient = false }

    private val contentRoot: File by lazy {
        // Unit tests usually run with the module dir as CWD; fall back to repo root.
        listOf("src/main/assets/content", "app/src/main/assets/content")
            .map { File(it) }
            .firstOrNull { it.isDirectory }
            ?: error("content assets directory not found from ${File(".").absolutePath}")
    }

    /**
     * Every language DISCOVERED from the content directory — never a hardcoded list. A new
     * language enters every automated gate the moment its folder lands in assets/content/,
     * with no test edits required (docs/language-standard.md is the companion checklist).
     */
    private val allLangs: List<String> by lazy {
        contentRoot.listFiles()!!.filter { it.isDirectory }.map { it.name }.sorted()
            .also { require(it.isNotEmpty()) { "no language directories under $contentRoot" } }
    }

    /** Source keys registered in docs/sources/README.md. */
    private val knownSourceKeys = setOf(
        // Croatian
        "asoo", "nn-6-2021", "nn-100-2021", "croaticum-syllabus",
        "croaticum-b1-sample", "cefr-grid", "ffzg-ecourse", "hrlex",
        // French (DELF B2 target, the level naturalisation requires since 2026-01-01).
        // referentiel-fr / francais-fondamental / freq-fr were retired on 2026-07-20: the
        // Beacco volumes and the official Fondamental list were never fetchable, so citing
        // them was an overclaim (registry C16). The Eaquals/CIEP Inventaire and Lexique 3.83
        // are the fetched, verified replacements.
        "cecrl", "delf-b1-sample", "delf-b2-sample",
        "inventaire-cecrl", "lexique383", "decret-2025-648",
        // Portuguese, European (DIPLE B2 target)
        // freq-pt retired 2026-08-04: *A Frequency Dictionary of Portuguese* (Davies &
        // Preto-Bay, Routledge) is a commercial book, never fetched and not fetchable, the
        // exact freq-fr/freq-es overclaim class (registry C16). Cited on 12 packs with no
        // coverage check ever performed; removed from all of them.
        "qecr", "caple", "deple-sample", "diple-sample",
        "referencial-camoes", "portugues-fundamental",
        // German (Goethe-Zertifikat B1 target, the citizenship / settlement level)
        "goethe-a1", "goethe-a2", "goethe-b1", "telc-b1", "goethe-wortliste", "stag-10",
        // Italian (CILS / CELI B1 target, the citizenship level since Dec 2018)
        "cils-a1", "cils-a2", "cils-b1", "celi-b1", "b1-cittadinanza", "cliq", "freq-it",
        // Spanish (DELE B1 target; Spain's legal bar is DELE A2 + the CCSE civics test, and
        // civics is out of scope). `pcic` is EARNED for grammar and the topic sequence but the
        // vocabulary citation was DECLINED after the Phase 8b cross-check ran (2026-07-30): the
        // deck is freq-es plus thematic need, so claiming the syllabus as a vocab source would be
        // the goethe-wortliste overclaim. No vocab pack cites it; all 20 carry ["freq-es"], which
        // is itself ordering-only. See docs/sources/es-exams.md §6c.
        "jus-1625-2016", "dele-a1", "dele-a2", "dele-b1", "pcic", "freq-es"
    )

    private fun read(lang: String, file: String): String =
        File(contentRoot, "$lang/$file").readText(Charsets.UTF_8)

    private fun exists(lang: String, file: String): Boolean =
        File(contentRoot, "$lang/$file").exists()

    /** Loads all vocab packs for a language: merged vocab/ dir (via _index.json) or vocab.json. */
    private fun loadVocabPacks(lang: String): List<VocabPack> {
        val dir = File(contentRoot, "$lang/vocab")
        return if (dir.isDirectory) {
            val index = strictJson.decodeFromString<List<String>>(
                File(dir, "_index.json").readText(Charsets.UTF_8)
            )
            // Every listed file must exist, and every vocab file must be listed.
            val actual = dir.listFiles()!!.map { it.name }.filter { it != "_index.json" }.toSet()
            assertEquals("vocab/_index.json out of sync with directory", actual, index.toSet())
            index.flatMap {
                strictJson.decodeFromString<VocabSet>(File(dir, it).readText(Charsets.UTF_8)).packs
            }
        } else {
            strictJson.decodeFromString<VocabSet>(read(lang, "vocab.json")).packs
        }
    }

    private fun loadPlan(lang: String): StudyPlan {
        val dir = File(contentRoot, "$lang/plan")
        return if (dir.isDirectory) {
            val index = strictJson.decodeFromString<List<String>>(
                File(dir, "_index.json").readText(Charsets.UTF_8)
            )
            // Same sync rule as vocab: a phase file on disk but missing from _index.json is
            // silently dropped by the app's loader — and if it's the FINAL phase, the
            // contiguity test still passes with a shorter course. Fail loudly instead.
            val actual = dir.listFiles()!!.map { it.name }.filter { it != "_index.json" }.toSet()
            assertEquals("$lang plan/_index.json out of sync with directory", actual, index.toSet())
            val plans = index.map {
                strictJson.decodeFromString<StudyPlan>(File(dir, it).readText(Charsets.UTF_8))
            }
            StudyPlan(title = plans.first().title, days = plans.flatMap { it.days })
        } else {
            strictJson.decodeFromString<StudyPlan>(read(lang, "plan.json"))
        }
    }

    // ---------- Parse gate ----------

    @Test
    fun `all languages parse strictly`() {
        for (lang in allLangs) {
            strictJson.decodeFromString<LanguageMeta>(read(lang, "meta.json"))
            strictJson.decodeFromString<Cheatsheet>(read(lang, "cheatsheet.json"))
            strictJson.decodeFromString<Levels>(read(lang, "levels.json"))
            strictJson.decodeFromString<QuizSet>(read(lang, "quizzes.json"))
            strictJson.decodeFromString<FeynmanSet>(read(lang, "feynman.json"))
            strictJson.decodeFromString<ResourceList>(read(lang, "resources.json"))
            // Optional files, but when present they must strict-parse too — a wrong question key
            // in exams.json (e.g. "kind" vs "type") must fail the build, not crash at runtime.
            if (exists(lang, "exams.json"))
                strictJson.decodeFromString<List<com.corlang.app.data.model.ExamSpec>>(read(lang, "exams.json"))
            if (exists(lang, "placement.json"))
                strictJson.decodeFromString<com.corlang.app.data.model.PlacementTest>(read(lang, "placement.json"))
            loadVocabPacks(lang)
            loadPlan(lang)
        }
    }

    // ---------- Vocabulary invariants ----------

    @Test
    fun `word ids are globally unique and NFC normalized`() {
        val ids = loadVocabPacks("hr").flatMap { it.words }.map { it.id }
        assertEquals("duplicate word ids", ids.size, ids.toSet().size)
        ids.forEach { id ->
            assertEquals("word id not NFC-normalized: $id",
                Normalizer.normalize(id, Normalizer.Form.NFC), id)
        }
    }

    @Test
    fun `frozen word ids are never renamed or removed`() {
        // WordReview rows key on wordId — renaming an id orphans the user's SRS progress.
        val frozen = javaClass.getResourceAsStream("/frozen-word-ids.txt")!!
            .bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
        val current = loadVocabPacks("hr").flatMap { it.words }.map { it.id }.toSet()
        val missing = frozen.filterNot { it in current }
        assertTrue("frozen word ids missing (SRS progress would be orphaned): $missing",
            missing.isEmpty())
    }

    @Test
    fun `croatian words contain no mojibake`() {
        loadVocabPacks("hr").flatMap { it.words }.forEach { w ->
            assertTrue("suspicious character in '${w.hr}'", !w.hr.contains('�'))
            assertTrue("empty gloss for '${w.id}'", w.en.isNotBlank())
        }
    }

    // ---------- Plan invariants ----------

    @Test
    fun `plan days are contiguous and well-formed`() {
        val plan = loadPlan("hr")
        val days = plan.days.map { it.day }
        assertEquals("plan days not contiguous 1..N", (1..plan.days.size).toList(), days)
        plan.days.forEach { d ->
            assertTrue("day ${d.day}: blank level/phase/title",
                d.level.isNotBlank() && d.phase.isNotBlank() && d.title.isNotBlank())
            assertTrue("day ${d.day}: week must be positive", d.week >= 1)
        }
    }

    @Test
    fun `plan resource references exist in resources json`() {
        for (lang in allLangs) {
            val resourceNames = strictJson
                .decodeFromString<ResourceList>(read(lang, "resources.json"))
                .resources.map { it.name }.toSet()
            // In-app references (like the Words tab) are allowed; external ones must resolve.
            val allowed = resourceNames + setOf("Words tab (built-in daily flashcards)")
            loadPlan(lang).days.forEach { d ->
                d.resources.forEach { r ->
                    assertTrue("$lang day ${d.day} references unknown resource: $r", r in allowed)
                }
            }
        }
    }

    // ---------- Quiz invariants ----------

    @Test
    fun `quiz questions are internally consistent`() {
        // Every discovered language — this ran hr-only for months while pt/fr quizzes shipped
        // ungated (clean by luck, not by gate).
        for (lang in allLangs) {
            strictJson.decodeFromString<QuizSet>(read(lang, "quizzes.json")).quizzes.forEach { quiz ->
                quiz.questions.forEach { q ->
                    when (q.type) {
                        QuestionType.MCQ -> {
                            assertTrue("$lang/${quiz.id}: MCQ answer not in options: '${q.answer}'",
                                q.answer in q.options)
                            assertTrue("$lang/${quiz.id}: MCQ needs 2+ options", q.options.size >= 2)
                            assertEquals("$lang/${quiz.id}: duplicate MCQ options: ${q.options}",
                                q.options.size, q.options.toSet().size)
                        }
                        QuestionType.FILL ->
                            assertTrue("$lang/${quiz.id}: FILL with blank answer", q.answer.isNotBlank())
                        QuestionType.REORDER ->
                            assertEquals("$lang/${quiz.id}: REORDER ordered != permutation of options",
                                q.options.sorted(), q.ordered.sorted())
                        QuestionType.MATCH ->
                            assertTrue("$lang/${quiz.id}: MATCH without pairs", q.pairs.isNotEmpty())
                        QuestionType.TRANSLATE ->
                            assertTrue("$lang/${quiz.id}: TRANSLATE answer must be a sentence",
                                q.answer.trim().split(" ").size >= 2)
                    }
                    assertTrue("$lang/${quiz.id}: question missing explanation", q.explanation.isNotBlank())
                }
            }
        }
    }

    // ---------- Embedded day activities ----------

    @Test
    fun `day activities are complete lessons, not references`() {
        // All discovered languages (was hr-only; the fr/pt copies below remain as belt-and-
        // suspenders, and a 4th language is gated here automatically).
        for (lang in allLangs) {
        loadPlan(lang).days.forEach { d ->
            d.activities.forEachIndexed { i, a ->
                when (a.type) {
                    com.corlang.app.data.model.ActivityKind.LEARN ->
                        assertTrue("day ${d.day} activity $i: LEARN needs >=3 items with content",
                            a.items.size >= 3 && a.items.all { it.hr.isNotBlank() && it.en.isNotBlank() })
                    com.corlang.app.data.model.ActivityKind.EXERCISE -> {
                        assertTrue("day ${d.day} activity $i: EXERCISE needs >=4 questions",
                            a.questions.size >= 4)
                        a.questions.forEach { q ->
                            when (q.type) {
                                QuestionType.MCQ -> assertTrue(
                                    "day ${d.day}: MCQ answer not in options: '${q.answer}'",
                                    q.answer in q.options && q.options.size >= 2)
                                QuestionType.FILL -> assertTrue(
                                    "day ${d.day}: FILL blank answer", q.answer.isNotBlank())
                                QuestionType.REORDER -> assertEquals(
                                    "day ${d.day}: REORDER not a permutation",
                                    q.options.sorted(), q.ordered.sorted())
                                else -> {}
                            }
                        }
                    }
                    com.corlang.app.data.model.ActivityKind.DIALOGUE ->
                        assertTrue("day ${d.day} activity $i: DIALOGUE needs >=4 lines",
                            a.lines.size >= 4 && a.lines.all { it.hr.isNotBlank() })
                }
                assertTrue("day ${d.day} activity $i: missing sources", a.sources.isNotEmpty())
            }
        }
        }
    }

    // ---------- French (fr), guarded so they no-op on the old seed and enforce as content lands ----------
    //
    // French is being rebuilt to the hr standard (docs/french-plan.md). Each check below activates
    // only once the corresponding new-format file/dir exists, so the gate stays green today while
    // guaranteeing every landed French piece meets the same bar as Croatian.

    private fun frDir(name: String) = File(contentRoot, "fr/$name")

    @Test
    fun `french split vocab has unique NFC ids and frozen ids hold`() {
        if (!frDir("vocab").isDirectory) return   // old-format seed: nothing to enforce yet
        val ids = loadVocabPacks("fr").flatMap { it.words }.map { it.id }
        assertEquals("duplicate fr word ids", ids.size, ids.toSet().size)
        ids.forEach { id ->
            assertEquals("fr word id not NFC-normalized: $id",
                Normalizer.normalize(id, Normalizer.Form.NFC), id)
        }
        // Frozen ids only enforced once the snapshot file exists (created when A1 lands).
        val frozenStream = javaClass.getResourceAsStream("/frozen-word-ids-fr.txt") ?: return
        val frozen = frozenStream.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
        val missing = frozen.filterNot { it in ids.toSet() }
        assertTrue("frozen fr word ids missing (SRS progress would orphan): $missing", missing.isEmpty())
    }

    @Test
    fun `french new-format plan is contiguous with complete embedded activities`() {
        if (!frDir("plan").isDirectory) return   // still the single-file seed
        val plan = loadPlan("fr")
        assertEquals("fr plan days not contiguous 1..N",
            (1..plan.days.size).toList(), plan.days.map { it.day })
        plan.days.forEach { d ->
            assertTrue("fr day ${d.day}: blank level/phase/title",
                d.level.isNotBlank() && d.phase.isNotBlank() && d.title.isNotBlank())
            d.activities.forEachIndexed { i, a ->
                when (a.type) {
                    com.corlang.app.data.model.ActivityKind.LEARN ->
                        assertTrue("fr day ${d.day} act $i: LEARN needs >=3 items with content",
                            a.items.size >= 3 && a.items.all { it.hr.isNotBlank() && it.en.isNotBlank() })
                    com.corlang.app.data.model.ActivityKind.EXERCISE -> {
                        assertTrue("fr day ${d.day} act $i: EXERCISE needs >=4 questions",
                            a.questions.size >= 4)
                        a.questions.forEach { q ->
                            when (q.type) {
                                QuestionType.MCQ -> assertTrue(
                                    "fr day ${d.day}: MCQ answer not in options: '${q.answer}'",
                                    q.answer in q.options && q.options.size >= 2)
                                QuestionType.FILL -> assertTrue(
                                    "fr day ${d.day}: FILL blank answer", q.answer.isNotBlank())
                                QuestionType.REORDER -> assertEquals(
                                    "fr day ${d.day}: REORDER not a permutation",
                                    q.options.sorted(), q.ordered.sorted())
                                else -> {}
                            }
                        }
                    }
                    com.corlang.app.data.model.ActivityKind.DIALOGUE ->
                        assertTrue("fr day ${d.day} act $i: DIALOGUE needs >=4 lines",
                            a.lines.size >= 4 && a.lines.all { it.hr.isNotBlank() })
                }
                assertTrue("fr day ${d.day} act $i: missing sources", a.sources.isNotEmpty())
            }
        }
    }

    @Test
    fun `wrapup recall items never leak or mangle their answer`() {
        // Guards the field-reported classes: "he / she is" graded against a truncated "on"
        // (hr day 6) and "no, bread, hand" demanding the "ão — …" demo string (pt day 1).
        for (lang in allLangs) {
            val plan = loadPlan(lang)
            plan.days.forEach { day ->
                com.corlang.app.ui.screens.wrapupRecallPhrases(day).forEach { item ->
                    assertTrue(
                        "$lang day ${day.day}: demo dash survived in recall target '${item.hr}'",
                        " — " !in item.hr
                    )
                    val gloss = com.corlang.app.ui.screens.Grading.normalize(item.en, strict = true)
                    val answer = com.corlang.app.ui.screens.Grading.normalize(item.hr, strict = true)
                    assertTrue(
                        "$lang day ${day.day}: gloss '${item.en}' contains its own answer '${item.hr}'",
                        answer !in gloss
                    )
                }
            }
        }
    }

    @Test
    fun `reorder prompts never contain their own sentence`() {
        // Guards the field-found class: "Put in order: 'Il faut que je finisse mon travail.'"
        // handed the learner the full answer. Prompts must carry the MEANING (English), never
        // the target sentence — neither verbatim nor most of its words.
        val lenient = Json { ignoreUnknownKeys = true }
        fun norm(s: String) = com.corlang.app.ui.screens.Grading.normalize(s, strict = true)
        fun collect(
            e: kotlinx.serialization.json.JsonElement,
            out: MutableList<Pair<String, List<String>>>
        ) {
            when (e) {
                is kotlinx.serialization.json.JsonObject -> {
                    val prompt = (e["prompt"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    val ordered = (e["ordered"] as? kotlinx.serialization.json.JsonArray)
                        ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                    if (prompt != null && !ordered.isNullOrEmpty()) out += prompt to ordered
                    e.values.forEach { collect(it, out) }
                }
                is kotlinx.serialization.json.JsonArray -> e.forEach { collect(it, out) }
                else -> {}
            }
        }
        for (lang in allLangs) {
            val reorders = mutableListOf<Pair<String, List<String>>>()
            val planDir = File(contentRoot, "$lang/plan")
            val files = (planDir.listFiles()?.map { "plan/${it.name}" } ?: emptyList()) +
                listOf("quizzes.json", "exams.json", "placement.json")
            files.filter { exists(lang, it) && it.endsWith(".json") && "_index" !in it }
                .forEach { collect(lenient.parseToJsonElement(read(lang, it)), reorders) }
            reorders.forEach { (prompt, ordered) ->
                val p = norm(prompt)
                val sentence = norm(ordered.joinToString(" "))
                assertTrue(
                    "$lang: REORDER prompt contains its own sentence: $prompt",
                    sentence.isBlank() || sentence !in p
                )
                val tokens = ordered.map { norm(it) }.filter { it.isNotBlank() }.distinct()
                if (tokens.size >= 3) {
                    val present = tokens.count { Regex("(^| )${Regex.escape(it)}( |$)").containsMatchIn(p) }
                    assertTrue(
                        "$lang: REORDER prompt reveals most of its words ($present/${tokens.size}): $prompt",
                        present.toDouble() / tokens.size < 0.7
                    )
                }
            }
        }
    }

    @Test
    fun `typed fill answers never appear verbatim in their own prompt`() {
        // Guards the field-found class: a FILL prompt whose format example IS the answer
        // ("Kada je sljedeći polazak? (npr. 'sutra u 7')" with answer "sutra u 7").
        // Single-word coincidences are legitimate declension tasks (a lemma hint whose asked
        // form is unchanged: "(godina)" → gen.pl "godina"), so only multi-word answers count.
        val lenient = Json { ignoreUnknownKeys = true }
        fun norm(s: String) = com.corlang.app.ui.screens.Grading.normalize(s, strict = true)
        fun collectFills(
            e: kotlinx.serialization.json.JsonElement,
            out: MutableList<Pair<String, List<String>>>
        ) {
            when (e) {
                is kotlinx.serialization.json.JsonObject -> {
                    val type = (e["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    val prompt = (e["prompt"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    val answer = (e["answer"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    if (type == "FILL" && prompt != null && !answer.isNullOrBlank()) {
                        val accepted = (e["accepted"] as? kotlinx.serialization.json.JsonArray)
                            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                            ?: emptyList()
                        out += prompt to (listOf(answer) + accepted)
                    }
                    e.values.forEach { collectFills(it, out) }
                }
                is kotlinx.serialization.json.JsonArray -> e.forEach { collectFills(it, out) }
                else -> {}
            }
        }
        for (lang in allLangs) {
            // plan/ INCLUDED — this test originally scanned only quizzes/exams/placement while
            // the REORDER-leak test scanned plan/ too; a fr lesson FILL quoting the sentence
            // containing its own answer ("ce que") slipped through that gap.
            val planDir = File(contentRoot, "$lang/plan")
            val names = (planDir.listFiles()?.map { "plan/${it.name}" } ?: emptyList()) +
                listOf("quizzes.json", "exams.json", "placement.json")
            for (name in names) {
                if (!exists(lang, name) || !name.endsWith(".json") || "_index" in name) continue
                val fills = mutableListOf<Pair<String, List<String>>>()
                collectFills(lenient.parseToJsonElement(read(lang, name)), fills)
                fills.forEach { (prompt, answers) ->
                    val p = norm(prompt)
                    answers.map { norm(it) }.filter { " " in it }.forEach { a ->
                        assertTrue("$lang/$name: FILL prompt leaks its answer '$a': $prompt", a !in p)
                    }
                }
            }
        }
    }

    // ---------- Exam structure (all languages) ----------

    @Test
    fun `exam sections are structurally sound`() {
        // Exams were never structurally validated for ANY language: an empty prompts list
        // would crash the runner (prompts[promptIndex]); a REORDER in a scored section would
        // render as a bare text field and be unanswerable.
        val scoredKinds = setOf(
            com.corlang.app.data.model.ExamSectionKind.LISTENING,
            com.corlang.app.data.model.ExamSectionKind.READING,
            com.corlang.app.data.model.ExamSectionKind.GRAMMAR
        )
        for (lang in allLangs) {
            if (!exists(lang, "exams.json")) continue
            strictJson.decodeFromString<List<com.corlang.app.data.model.ExamSpec>>(
                read(lang, "exams.json")
            ).forEach { exam ->
                assertTrue("$lang/${exam.id}: exam without sections", exam.sections.isNotEmpty())
                exam.sections.forEach { s ->
                    if (s.kind in scoredKinds) {
                        assertTrue("$lang/${exam.id}/${s.id}: scored section without questions",
                            s.questions.isNotEmpty())
                        s.questions.forEach { q ->
                            assertTrue(
                                "$lang/${exam.id}/${s.id}: scored sections support only MCQ/FILL, got ${q.type}",
                                q.type == QuestionType.MCQ || q.type == QuestionType.FILL
                            )
                            if (q.type == QuestionType.MCQ) {
                                assertTrue("$lang/${exam.id}/${s.id}: MCQ answer not in options: '${q.answer}'",
                                    q.answer in q.options && q.options.size >= 2)
                                assertEquals("$lang/${exam.id}/${s.id}: duplicate MCQ options",
                                    q.options.size, q.options.toSet().size)
                            } else {
                                assertTrue("$lang/${exam.id}/${s.id}: FILL blank answer",
                                    q.answer.isNotBlank())
                                // The runner's field label promises "diacritics count!" — the
                                // grading must actually be strict for every exam FILL.
                                assertTrue("$lang/${exam.id}/${s.id}: exam FILL not strictDiacritics: '${q.answer}'",
                                    q.strictDiacritics)
                            }
                        }
                    } else {
                        assertTrue("$lang/${exam.id}/${s.id}: ${s.kind} section needs >=1 prompt",
                            s.prompts.isNotEmpty())
                        s.prompts.forEach { p ->
                            assertTrue("$lang/${exam.id}/${s.id}: prompt missing model answer or rubric",
                                p.modelAnswer.isNotBlank() && p.rubric.isNotEmpty())
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `day exercises never use MATCH`() {
        // ExerciseActivity has no MATCH renderer: a MATCH question in a lesson would grade
        // false forever and re-queue infinitely. Ban it at the gate instead of skipping it.
        for (lang in allLangs) {
            loadPlan(lang).days.forEach { d ->
                d.activities.filter { it.type == com.corlang.app.data.model.ActivityKind.EXERCISE }
                    .forEach { a ->
                        a.questions.forEach { q ->
                            assertTrue(
                                "$lang day ${d.day}: MATCH in a day exercise is unplayable (no renderer)",
                                q.type != QuestionType.MATCH
                            )
                        }
                    }
            }
        }
    }

    @Test
    fun `no duplicate prompts within a question container`() {
        // Question UI state was historically keyed on prompt TEXT; it's index-keyed now, but
        // duplicate prompts inside one activity/quiz/section are an authoring error regardless
        // (the learner sees the same question twice and the intent is ambiguous).
        for (lang in allLangs) {
            loadPlan(lang).days.forEach { d ->
                d.activities.forEachIndexed { i, a ->
                    val prompts = a.questions.map { it.prompt }
                    assertEquals("$lang day ${d.day} activity $i: duplicate question prompts",
                        prompts.size, prompts.toSet().size)
                }
            }
            strictJson.decodeFromString<QuizSet>(read(lang, "quizzes.json")).quizzes.forEach { quiz ->
                val prompts = quiz.questions.map { it.prompt }
                assertEquals("$lang/${quiz.id}: duplicate question prompts",
                    prompts.size, prompts.toSet().size)
            }
            if (exists(lang, "exams.json")) {
                strictJson.decodeFromString<List<com.corlang.app.data.model.ExamSpec>>(
                    read(lang, "exams.json")
                ).forEach { exam ->
                    exam.sections.forEach { s ->
                        val prompts = s.questions.map { it.prompt } + s.prompts.map { it.prompt }
                        assertEquals("$lang/${exam.id}/${s.id}: duplicate prompts",
                            prompts.size, prompts.toSet().size)
                    }
                }
            }
        }
    }

    @Test
    fun `french split vocab carries provenance from known keys`() {
        if (!frDir("vocab").isDirectory) return
        loadVocabPacks("fr").forEach { pack ->
            assertTrue("fr pack ${pack.id} missing sources (provenance rule)", pack.sources.isNotEmpty())
            pack.sources.forEach {
                assertTrue("fr pack ${pack.id}: unknown source key '$it'", it in knownSourceKeys)
            }
        }
    }

    @Test
    fun `french placement and exams are consistent when present`() {
        if (exists("fr", "placement.json")) {
            val test = strictJson.decodeFromString<com.corlang.app.data.model.PlacementTest>(
                read("fr", "placement.json")
            )
            val planSize = loadPlan("fr").days.size
            assertTrue("fr placement has no questions", test.questions.isNotEmpty())
            test.questions.forEach { q ->
                assertTrue("fr placement: answer not in options: '${q.answer}'", q.answer in q.options)
                assertTrue("fr placement: startDay ${q.startDay} outside 1..$planSize",
                    q.startDay in 1..planSize)
            }
        }
        if (exists("fr", "exams.json")) {
            assertTrue("fr exams.json must cite sources", read("fr", "exams.json").contains("\"sources\""))
        }
        if (exists("fr", "grammar.json")) {
            assertTrue("fr grammar.json must cite sources", read("fr", "grammar.json").contains("\"sources\""))
        }
    }

    // ---------- Portuguese (pt), guarded so each check activates as content lands ----------
    //
    // European Portuguese (pt-PT) built to the hr/fr standard (docs/portuguese-plan.md). The
    // Brazilianism test is the pt-specific guarantee: Corlang teaches EUROPEAN Portuguese, so
    // unambiguous Brazilian lexis is a build failure anywhere in the pt content.

    private fun ptDir(name: String) = File(contentRoot, "pt/$name")

    @Test
    fun `portuguese split vocab has unique NFC ids, frozen ids hold, provenance known`() {
        if (!ptDir("vocab").isDirectory) return
        val packs = loadVocabPacks("pt")
        val ids = packs.flatMap { it.words }.map { it.id }
        assertEquals("duplicate pt word ids", ids.size, ids.toSet().size)
        ids.forEach { id ->
            assertEquals("pt word id not NFC-normalized: $id",
                Normalizer.normalize(id, Normalizer.Form.NFC), id)
        }
        packs.forEach { pack ->
            assertTrue("pt pack ${pack.id} missing sources", pack.sources.isNotEmpty())
            pack.sources.forEach {
                assertTrue("pt pack ${pack.id}: unknown source key '$it'", it in knownSourceKeys)
            }
        }
        val frozenStream = javaClass.getResourceAsStream("/frozen-word-ids-pt.txt") ?: return
        val frozen = frozenStream.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
        val missing = frozen.filterNot { it in ids.toSet() }
        assertTrue("frozen pt word ids missing (SRS progress would orphan): $missing", missing.isEmpty())
    }

    @Test
    fun `portuguese plan is contiguous with complete embedded activities`() {
        if (!ptDir("plan").isDirectory) return
        val plan = loadPlan("pt")
        assertEquals("pt plan days not contiguous 1..N",
            (1..plan.days.size).toList(), plan.days.map { it.day })
        plan.days.forEach { d ->
            assertTrue("pt day ${d.day}: blank level/phase/title",
                d.level.isNotBlank() && d.phase.isNotBlank() && d.title.isNotBlank())
            d.activities.forEachIndexed { i, a ->
                when (a.type) {
                    com.corlang.app.data.model.ActivityKind.LEARN ->
                        assertTrue("pt day ${d.day} act $i: LEARN needs >=3 items with content",
                            a.items.size >= 3 && a.items.all { it.hr.isNotBlank() && it.en.isNotBlank() })
                    com.corlang.app.data.model.ActivityKind.EXERCISE -> {
                        assertTrue("pt day ${d.day} act $i: EXERCISE needs >=4 questions",
                            a.questions.size >= 4)
                        a.questions.forEach { q ->
                            when (q.type) {
                                QuestionType.MCQ -> assertTrue(
                                    "pt day ${d.day}: MCQ answer not in options: '${q.answer}'",
                                    q.answer in q.options && q.options.size >= 2)
                                QuestionType.FILL -> assertTrue(
                                    "pt day ${d.day}: FILL blank answer", q.answer.isNotBlank())
                                QuestionType.REORDER -> assertEquals(
                                    "pt day ${d.day}: REORDER not a permutation",
                                    q.options.sorted(), q.ordered.sorted())
                                else -> {}
                            }
                        }
                    }
                    com.corlang.app.data.model.ActivityKind.DIALOGUE ->
                        assertTrue("pt day ${d.day} act $i: DIALOGUE needs >=4 lines",
                            a.lines.size >= 4 && a.lines.all { it.hr.isNotBlank() })
                }
                assertTrue("pt day ${d.day} act $i: missing sources", a.sources.isNotEmpty())
            }
        }
    }

    @Test
    fun `portuguese placement and exams are consistent when present`() {
        if (exists("pt", "placement.json") && ptDir("plan").isDirectory) {
            val test = strictJson.decodeFromString<com.corlang.app.data.model.PlacementTest>(
                read("pt", "placement.json")
            )
            val planSize = loadPlan("pt").days.size
            assertTrue("pt placement has no questions", test.questions.isNotEmpty())
            test.questions.forEach { q ->
                assertTrue("pt placement: answer not in options: '${q.answer}'", q.answer in q.options)
                assertTrue("pt placement: startDay ${q.startDay} outside 1..$planSize",
                    q.startDay in 1..planSize)
            }
        }
        if (exists("pt", "exams.json")) {
            assertTrue("pt exams.json must cite sources", read("pt", "exams.json").contains("\"sources\""))
        }
        if (exists("pt", "grammar.json")) {
            assertTrue("pt grammar.json must cite sources", read("pt", "grammar.json").contains("\"sources\""))
        }
    }

    @Test
    fun `portuguese content contains no Brazilianisms`() {
        val ptRoot = File(contentRoot, "pt")
        if (!ptRoot.isDirectory) return
        // Unambiguous Brazilian lexis only (words that are simply not European Portuguese);
        // ambiguous/shared items are left to human review.
        // Each Brazilian form maps to the European word that must accompany it. A bleed form is
        // allowed ONLY as a CONTRASTIVE example: it has to sit in the same activity as its
        // European counterpart (an MCQ distractor beside the correct answer, or a dialogue line
        // the learner corrects). docs/language-standard.md asks for exactly that teaching, so a
        // whole-file ban would forbid it; a bleed form with no correction nearby is drift, fails.
        val blocked = mapOf(
            "ônibus" to "autocarro", "celular" to "telemóvel", "banheiro" to "casa de banho",
            "sorvete" to "gelado", "geladeira" to "frigorífico", "açougue" to "talho",
            "esporte" to "desporto", "aeromoça" to "hospedeira", "café da manhã" to "pequeno-almoço",
            "caminhão" to "camião", "usuário" to "utilizador", "gerenciar" to "gerir",
            "bonde" to "elétrico", "encanador" to "canalizador", "faxina" to "limpeza",
            "grampeador" to "agrafador", "história em quadrinhos" to "banda desenhada"
        )
        val lenient = Json { ignoreUnknownKeys = true }

        /** All strings under [e], flattened. */
        fun flatten(e: kotlinx.serialization.json.JsonElement): String = buildString {
            fun rec(x: kotlinx.serialization.json.JsonElement) {
                when (x) {
                    is kotlinx.serialization.json.JsonObject -> x.values.forEach { rec(it) }
                    is kotlinx.serialization.json.JsonArray -> x.forEach { rec(it) }
                    is kotlinx.serialization.json.JsonPrimitive ->
                        if (x.isString) append(x.content).append(' ')
                    else -> {}
                }
            }
            rec(e)
        }

        /** One scope per activity, plus one scope for everything outside any activity. */
        fun scopes(root: kotlinx.serialization.json.JsonElement): List<String> {
            val activities = mutableListOf<String>()
            val outside = StringBuilder()
            fun rec(e: kotlinx.serialization.json.JsonElement) {
                when (e) {
                    is kotlinx.serialization.json.JsonObject -> {
                        val kind = (e["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                        if (kind in setOf("LEARN", "EXERCISE", "DIALOGUE")) activities.add(flatten(e))
                        else e.values.forEach { rec(it) }
                    }
                    is kotlinx.serialization.json.JsonArray -> e.forEach { rec(it) }
                    is kotlinx.serialization.json.JsonPrimitive ->
                        if (e.isString) outside.append(e.content).append(' ')
                    else -> {}
                }
            }
            rec(root)
            return activities + outside.toString()
        }

        val hits = mutableListOf<String>()
        ptRoot.walkTopDown().filter { it.isFile && it.extension == "json" }.forEach { f ->
            scopes(lenient.parseToJsonElement(f.readText(Charsets.UTF_8))).forEach { scope ->
                val text = scope.lowercase()
                blocked.forEach { (term, european) ->
                    val present = Regex("(?<![\\p{L}])${Regex.escape(term)}(?![\\p{L}])")
                        .containsMatchIn(text)
                    if (present && !text.contains(european)) {
                        hits.add("${f.name}: '$term' without '$european' in the same activity")
                    }
                }
            }
        }
        assertTrue("Brazilianisms found in EUROPEAN Portuguese content: $hits", hits.isEmpty())
    }

    // ---------- Placement test ----------

    @Test
    fun `placement questions are consistent and map to real plan days`() {
        if (!exists("hr", "placement.json")) return
        val test = strictJson.decodeFromString<com.corlang.app.data.model.PlacementTest>(
            read("hr", "placement.json")
        )
        val planSize = loadPlan("hr").days.size
        assertTrue("placement.json has no questions", test.questions.isNotEmpty())
        test.questions.forEach { q ->
            assertTrue("placement: answer not in options: '${q.answer}'", q.answer in q.options)
            assertTrue("placement: needs 2+ options", q.options.size >= 2)
            assertTrue("placement: startDay ${q.startDay} outside 1..$planSize",
                q.startDay in 1..planSize)
            assertTrue("placement: blank level", q.level.isNotBlank())
        }
    }

    // ---------- Provenance ----------

    @Test
    fun `declared source keys are registered`() {
        // Any sources declared anywhere must come from docs/sources/README.md registry.
        loadVocabPacks("hr").forEach { pack ->
            pack.sources.forEach {
                assertTrue("pack ${pack.id}: unknown source key '$it'", it in knownSourceKeys)
            }
        }
    }

    @Test
    fun `new content files carry provenance`() {
        // grammar.json / exams.json and split vocab dirs are the "validated era" formats:
        // once they exist they must cite sources.
        if (exists("hr", "grammar.json")) {
            val text = read("hr", "grammar.json")
            assertTrue("grammar.json must cite sources", text.contains("\"sources\""))
        }
        if (exists("hr", "exams.json")) {
            val text = read("hr", "exams.json")
            assertTrue("exams.json must cite sources", text.contains("\"sources\""))
        }
        val vocabDir = File(contentRoot, "hr/vocab")
        if (vocabDir.isDirectory) {
            loadVocabPacks("hr").forEach { pack ->
                assertTrue("pack ${pack.id} missing sources (provenance rule)",
                    pack.sources.isNotEmpty())
            }
        }
    }

    // ---- App-only content + wording invariants (docs/language-standard.md section 7) ----

    /** Every learner-visible string in every content file, minus provenance `sources` arrays
     *  and resources.json (the ONE sanctioned home for external material, on Profile). */
    private fun learnerStrings(lang: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        fun walk(el: kotlinx.serialization.json.JsonElement, file: String) {
            when (el) {
                is kotlinx.serialization.json.JsonObject ->
                    el.forEach { (k, v) -> if (k != "sources") walk(v, file) }
                is kotlinx.serialization.json.JsonArray -> el.forEach { walk(it, file) }
                is kotlinx.serialization.json.JsonPrimitive ->
                    if (el.isString) out += file to el.content
                else -> {}
            }
        }
        File(contentRoot, lang).walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .filter { it.name != "resources.json" && it.name != "_index.json" }
            .forEach { f ->
                walk(Json.parseToJsonElement(f.readText(Charsets.UTF_8)), "$lang/${f.name}")
            }
        return out
    }

    /**
     * Lessons NEVER send the learner to study elsewhere: no URLs, no course sites, no named
     * institutions, no sign-in instructions, no competitor apps. External material lives only
     * in resources.json (Profile > References). Mirrors SessionPlayer's runtime isExternal
     * filter, but at the content gate so it can't even be authored. (Named-media immersion
     * habits like watching the news are allowed; course/site/app references are not.)
     */
    @Test
    fun content_neverSendsLearnersElsewhere() {
        val banned = Regex(
            // \be-course: without the boundary, "three-course interaction" matched.
            "https?://|www\\.|ffzg|unizg|a1\\.hr|a2\\.hr|e-tečaj|\\be-course|" +
                "croaticum|cehas|rijeka school|sign in at|sign up at|log in at|" +
                "\\bduolingo\\b|\\bmemrise\\b|\\banki\\b",
            RegexOption.IGNORE_CASE
        )
        allLangs.forEach { lang ->
            val hits = learnerStrings(lang).filter { (_, str) -> banned.containsMatchIn(str) }
            assertTrue(
                "external-study references in learner-visible content:\n" +
                    hits.joinToString("\n") { (f, str) -> "  $f: ${str.take(120)}" },
                hits.isEmpty()
            )
        }
    }

    /**
     * Course positions are "lesson N", never "day N": learners do not necessarily study daily,
     * and the app's UI says Lesson everywhere. Calendar durations ("30 days", "7-day streak")
     * put the number first and never match; target languages say dan/dia/jour.
     */
    @Test
    fun content_saysLessonNotDayForPositions() {
        val dayRef = Regex("\\b[Dd]ays?\\s+\\d")
        allLangs.forEach { lang ->
            val hits = learnerStrings(lang).filter { (_, str) -> dayRef.containsMatchIn(str) }
            assertTrue(
                "'day N' position references (should be 'lesson N'):\n" +
                    hits.joinToString("\n") { (f, str) -> "  $f: ${str.take(120)}" },
                hits.isEmpty()
            )
        }
    }

    /**
     * No em or en dashes in learner-visible content (docs/language-standard.md §7). The rule
     * was stated from the start but never enforced, so 3822 strings had accumulated across all
     * three languages before this gate existed. Commas, parentheses or a split sentence say the
     * same thing; a hyphen inside a compound ("spaced-repetition") is fine and not matched here.
     */
    @Test
    fun content_usesNoEmOrEnDashes() {
        val dash = Regex("[–—]")
        allLangs.forEach { lang ->
            val hits = learnerStrings(lang).filter { (_, str) -> dash.containsMatchIn(str) }
            assertTrue(
                "em/en dashes in learner-visible content (use commas):\n" +
                    hits.take(20).joinToString("\n") { (f, str) -> "  $f: ${str.take(110)}" },
                hits.isEmpty()
            )
        }
    }

    /**
     * Placement band shape (docs/language-standard.md §1). The adaptive scorer clears a band on
     * 2 of 3 items, so a band with fewer than two items can NEVER be cleared: the learner would
     * silently always fail it and place below their level. Exactly three keeps every band's
     * evidence equal; uniform difficulty keeps the ladder honest.
     */
    @Test
    fun placementBandsCarryExactlyFourItemsEach() {
        allLangs.forEach { lang ->
            if (!exists(lang, "placement.json")) return@forEach
            val test = strictJson.decodeFromString<com.corlang.app.data.model.PlacementTest>(
                read(lang, "placement.json")
            )
            val bands = test.questions.groupBy { it.level to it.startDay }
            assertTrue("$lang placement has fewer than 2 bands", bands.size >= 2)
            // Every anchor must carry the level the plan actually teaches on that day: the
            // result screen shows "LEVEL · Lesson N" and writes both into progress, so a
            // mislabeled anchor tells the learner they are at a level the course never visits
            // (field: pt/fr bottom bands said A0 while both courses start at A1).
            val levelByDay = loadPlan(lang).days.associate { it.day to it.level }
            bands.keys.forEach { (level, startDay) ->
                assertEquals(
                    "$lang placement anchor at lesson $startDay is labeled $level but the " +
                        "plan teaches ${levelByDay[startDay]} there",
                    levelByDay[startDay], level
                )
            }
            bands.forEach { (band, items) ->
                // Four, since the pass rule became 3 of 4: a band of three could only be
                // cleared by a clean sweep under that rule, which fails a learner who knows
                // the material and mis-taps once. See Placement.neededToPass.
                assertEquals("$lang placement band $band must carry exactly 4 items",
                    com.corlang.app.data.Placement.ITEMS_PER_BAND, items.size)
                assertEquals("$lang placement band $band mixes difficulties",
                    1, items.map { it.difficulty }.distinct().size)
                items.forEach { q ->
                    assertEquals("$lang $band: MCQ needs 4 options: ${q.prompt.take(50)}",
                        4, q.options.size)
                    assertTrue("$lang $band: answer not among options: ${q.prompt.take(50)}",
                        q.answer in q.options)
                }
            }
        }
    }

    /**
     * Deck-size floor (docs/language-standard.md §1). The SRS unlocks
     * `deck[0 .. lesson * NEW_WORDS_PER_DAY]`, so a 250-lesson course at the fixed pace of 10 a
     * lesson consumes 2500 words. A shorter deck means the last lessons introduce nothing, which
     * is exactly what Portuguese (2300) and Croatian (2412) did before this floor.
     */
    @Test
    fun everyDeckCoversTheWholeCourse() {
        allLangs.forEach { lang ->
            val words = loadVocabPacks(lang).sumOf { it.words.size }
            val needed = loadPlan(lang).days.size * com.corlang.app.data.Fsrs.NEW_WORDS_PER_DAY
            assertTrue(
                "$lang deck has $words words but the course needs $needed " +
                    "(${loadPlan(lang).days.size} lessons x ${com.corlang.app.data.Fsrs.NEW_WORDS_PER_DAY})",
                words >= needed
            )
        }
    }

    /**
     * Per-level lesson floor (docs/language-standard.md §1). The old rule was a flat 250-lesson
     * total, which is a volume rule: two courses can both clear it while covering very different
     * ground. Guided-hours research weights the levels A1 1.0 : A2 1.6 : B1 2.8, so B1 alone
     * costs about 2.8x what A1 costs, and every course built before this rule was top-light.
     */
    private val levelFloor = mapOf(
        // Baseline is the closest-to-English group (es, it, pt). Harder languages scale up by
        // the square root of their FSI hour ratio, rounded to 5: see the table in the standard.
        "es" to mapOf("A1" to 45, "A2" to 70, "B1" to 125),
        "it" to mapOf("A1" to 45, "A2" to 70, "B1" to 125),
        "pt" to mapOf("A1" to 45, "A2" to 70, "B1" to 125),
        "de" to mapOf("A1" to 50, "A2" to 80, "B1" to 140),
        "fr" to mapOf("A1" to 50, "A2" to 80, "B1" to 140, "B2" to 125),
        "hr" to mapOf("A1" to 60, "A2" to 95, "B1" to 170)
    )

    /**
     * Courses authored before the weighted rule and not yet rebalanced. Each entry is a DEBT,
     * not a dispensation: delete the line when the course is topped up, and the gate starts
     * enforcing. Listing them here keeps the shortfall visible in code rather than letting a
     * lowered bar hide it.
     */
    private val weightedRuleDebt = emptyMap<String, String>()

    @Test
    fun everyCourseMeetsTheWeightedLessonFloor() {
        allLangs.forEach { lang ->
            val debt = weightedRuleDebt[lang]
            if (debt != null) {
                println("SKIP $lang, known weighted-rule debt: $debt")
                return@forEach
            }
            val floors = levelFloor[lang] ?: emptyMap()
            assertTrue(
                "$lang has no row in levelFloor, add it to the table in " +
                    "docs/language-standard.md before shipping the language",
                floors.isNotEmpty()
            )
            val byLevel = loadPlan(lang).days.groupingBy { it.level }.eachCount()
            byLevel.forEach { (level, n) ->
                val floor = floors[level] ?: return@forEach   // A0 onramp carries no floor
                assertTrue(
                    "$lang $level has $n lessons, the weighted floor is $floor " +
                        "(see docs/language-standard.md)",
                    n >= floor
                )
            }
        }
    }

    /**
     * The every-level checkpoint rule (docs/language-standard.md §1): every CEFR level the
     * plan actually teaches (A1 and up) must end in all three journey checkpoints — a level
     * quiz, an exam readiness milestone (levels.json `exam`), and a mock exam. A missing
     * entry silently drops the stone from the journey, so the gate fails loudly instead.
     * A0 onramps are quiz-only (no official A0 exam exists to mirror).
     */
    @Test
    fun everyPlanLevelEndsInQuizReadinessAndMockExam() {
        allLangs.forEach { lang ->
            val planLevels = loadPlan(lang).days.map { it.level }.distinct()
            val quizLevels = strictJson.decodeFromString<QuizSet>(read(lang, "quizzes.json"))
                .quizzes.map { it.levelId }.toSet()
            val readinessLevels = strictJson.decodeFromString<Levels>(read(lang, "levels.json"))
                .levels.filter { it.exam != null }.map { it.id }.toSet()
            val examLevels =
                if (exists(lang, "exams.json"))
                    strictJson.decodeFromString<List<com.corlang.app.data.model.ExamSpec>>(
                        read(lang, "exams.json")
                    ).map { it.levelId }.toSet()
                else emptySet()
            planLevels.forEach { level ->
                assertTrue("$lang $level: no level quiz in quizzes.json", level in quizLevels)
                if (level != "A0") {
                    assertTrue("$lang $level: no exam readiness milestone in levels.json",
                        level in readinessLevels)
                    assertTrue("$lang $level: no mock exam in exams.json", level in examLevels)
                }
            }
        }
    }

    /**
     * IMPLEMENTATION x CONTENT: every authored answer must grade CORRECT through the real
     * graders. The structural gates prove an answer exists; only the grader itself proves it
     * is reachable by a learner who types or taps it. A FILL whose accepted variant normalizes
     * to empty, or a REORDER whose ordered tokens disagree with the grader's normalization,
     * is content that no correct learner can ever pass, which is the worst defect a course can
     * ship. Grading is pure Kotlin, so the whole corpus cross-checks in one test.
     */
    @Test
    fun `every authored answer grades correct through the real graders`() {
        val grading = com.corlang.app.ui.screens.Grading
        fun checkQuestions(where: String, qs: List<com.corlang.app.data.model.Question>) {
            qs.forEach { q ->
                when (q.type) {
                    QuestionType.MCQ ->
                        assertTrue("$where: MCQ answer fails its own grader: '${q.answer}'",
                            grading.gradeMcq(q, q.answer))
                    QuestionType.FILL -> {
                        assertTrue("$where: FILL answer fails its own grader: '${q.answer}'",
                            grading.gradeFill(q, q.answer))
                        q.accepted.forEach {
                            assertTrue("$where: accepted variant fails: '$it' (answer '${q.answer}')",
                                grading.gradeFill(q, it))
                        }
                    }
                    QuestionType.REORDER ->
                        assertTrue("$where: ordered tokens fail gradeReorder: ${q.ordered}",
                            grading.gradeReorder(q, q.ordered))
                    else -> Unit
                }
            }
        }
        allLangs.forEach { lang ->
            loadPlan(lang).days.forEach { day ->
                day.activities.forEach { a ->
                    checkQuestions("$lang day ${day.day} '${day.title.take(30)}'", a.questions)
                }
            }
            strictJson.decodeFromString<QuizSet>(read(lang, "quizzes.json")).quizzes.forEach {
                checkQuestions("$lang quiz ${it.id}", it.questions)
            }
            if (exists(lang, "exams.json")) {
                strictJson.decodeFromString<List<com.corlang.app.data.model.ExamSpec>>(
                    read(lang, "exams.json")
                ).forEach { e ->
                    e.sections.forEach { s ->
                        checkQuestions("$lang exam ${e.id}/${s.id}", s.questions)
                    }
                }
            }
            // Placement questions are a separate model (PlacementQuestion, MCQ-only). MCQ
            // grading is exact string equality, and the placement gates already assert every
            // answer appears among its options, so the grader cross-check is subsumed there.
        }
    }

    /**
     * WIRING COVERAGE, discovered not hardcoded: every language whose content folder exists
     * must have a real speech locale. German shipped its first wiring pass with the tutor
     * table missing from TUTOR_LANGS; this is the same class of miss for the voice. The hr
     * fallback makes a forgotten language SPEAK CROATIAN rather than fail, which no test
     * exercised until now.
     */
    /**
     * Same wiring class as the speech gate, now DATA-driven: reminder copy lives in each
     * language's meta.json (not a Kotlin map), so a known language must carry the fields rather
     * than fall back to hr copy at runtime.
     */
    @Test
    fun `reminder copy present in every language meta`() {
        allLangs.forEach { lang ->
            val meta = strictJson.decodeFromString<LanguageMeta>(read(lang, "meta.json"))
            assertTrue("$lang meta.json missing reminderTitle", !meta.reminderTitle.isNullOrBlank())
            assertTrue("$lang meta.json missing reminderProverb", !meta.reminderProverb.isNullOrBlank())
            val named = meta.reminderTitleNamed
            assertTrue("$lang meta.json missing reminderTitleNamed", !named.isNullOrBlank())
            assertTrue("$lang reminderTitleNamed must contain the {name} placeholder",
                named!!.contains("{name}"))
        }
    }

    /**
     * The free window is what a learner gets before any payment, and it is the only trial this
     * app has: there is no account, so nothing else can hand out a sample. Three ways it can be
     * authored wrong, all of which reach the store silently:
     *
     *  - **Too short to sell anything.** Spaced repetition only proves itself once reviews start
     *    coming back, which takes about a week and a half of daily lessons. A window under 10 is
     *    a paywall pretending to be a trial.
     *  - **An orphan remainder.** If the window ends one lesson short of a level boundary, that
     *    level's product sells a single lesson. Croatian is exactly this shape (A0 is 16), which
     *    is why the window is per-language data: it can land ON the boundary. A remainder must
     *    be nothing at all, or a real product.
     *  - **The whole course free.** A window at or past the last day leaves nothing to buy.
     */
    @Test
    fun `free lesson window leaves a sellable course`() {
        allLangs.forEach { lang ->
            val meta = strictJson.decodeFromString<LanguageMeta>(read(lang, "meta.json"))
            val free = meta.freeLessons
            val days = loadPlan(lang).days.sortedBy { it.day }
            assertTrue("$lang freeLessons=$free is too short to show the method works (min 10)",
                free >= 10)
            assertTrue("$lang freeLessons=$free leaves no lesson to sell (course is ${days.size})",
                free < days.size)

            // The level the window ends inside, and what is left of it to charge for.
            val straddled = days.firstOrNull { it.day == free }?.level
            if (straddled != null) {
                val remaining = days.count { it.level == straddled && it.day > free }
                assertTrue(
                    "$lang freeLessons=$free orphans $remaining lesson(s) of $straddled: land the " +
                        "window on the level boundary, or leave at least 10 lessons to sell",
                    remaining == 0 || remaining >= 10
                )
            }
        }
    }

    @Test
    fun `speech tag present in every language meta`() {
        allLangs.forEach { lang ->
            val meta = strictJson.decodeFromString<LanguageMeta>(read(lang, "meta.json"))
            val tag = meta.speechTag
            assertTrue("$lang meta.json missing speechTag (e.g. \"$lang-XX\")", !tag.isNullOrBlank())
            assertTrue("$lang speechTag '$tag' must start with the language code",
                tag!!.startsWith(lang))
        }
    }

    /**
     * The no-dashes rule covers everything the learner reads, but the existing gate only walks
     * assets/content, so learner-facing strings that live in Kotlin were never checked. The
     * tutor's seed greeting is the clearest example: it is the first sentence of every Tutor
     * conversation, and three of the four shipped greetings carried an em dash unnoticed.
     */
    @Test
    fun `tutor seed greetings carry no em or en dashes`() {
        (allLangs + "unknown-fallback").forEach { lang ->
            val greeting = com.corlang.app.ui.screens.seedGreeting(lang)
            assertFalse(
                "$lang tutor seed greeting contains an em or en dash: $greeting",
                greeting.contains('—') || greeting.contains('–')
            )
        }
    }

    /**
     * Registry C2, closed: the seed-greeting gate above covered one string; this walks EVERY
     * Kotlin string literal in the app source. A sweep on 2026-07-20 found 16 dash-bearing
     * strings that had survived two earlier "fixes" of the dash rule. Drills.kt is allowlisted:
     * its " — " literals are data delimiters in legacy content parsing, not copy.
     */
    @Test
    fun `kotlin string literals carry no em or en dashes`() {
        val srcRoot = listOf("src/main/java", "app/src/main/java")
            .map { File(it) }.firstOrNull { it.isDirectory }
            ?: error("source root not found from ${File(".").absolutePath}")
        val literal = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
        val offenders = mutableListOf<String>()
        srcRoot.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            if (file.name == "Drills.kt") return@forEach
            file.readLines(Charsets.UTF_8).forEachIndexed { i, line ->
                val t = line.trim()
                if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) return@forEachIndexed
                literal.findAll(line).forEach { m ->
                    if ('—' in m.groupValues[1] || '–' in m.groupValues[1]) {
                        offenders += "${file.name}:${i + 1}: ${m.groupValues[1].take(60)}"
                    }
                }
            }
        }
        assertTrue("em/en dashes in Kotlin string literals:\n" + offenders.joinToString("\n"),
            offenders.isEmpty())
    }

    /**
     * Every deck word carries an example sentence. Two features read it: WordsScreen prints it
     * under the word and speaks it, and DrillGen.clozeFor blanks it to build a drill. A word
     * without one is a bare headword and an English gloss, and it silently generates no drill.
     *
     * Croatian shipped 303 A0 words with no example at all, the whole of 00-a0-core.json, which
     * is the first 303 slots of the deck and therefore the first weeks of every learner's queue,
     * while all five other courses were already at 100%. Nothing caught it because nothing ever
     * looked. tools/course/check_deck_examples.py is the fast offline version of this gate and
     * carries the rest of the rules (cloze ambiguity, duplicate sentences); this is the one that
     * fails the build.
     */
    @Test
    fun `every deck word carries an example sentence`() {
        allLangs.forEach { lang ->
            val bare = loadVocabPacks(lang).flatMap { it.words }.filter { w ->
                val ex = w.example
                ex == null || ex.target.isBlank() || ex.gloss.isBlank()
            }
            assertTrue(
                "$lang: ${bare.size} deck words have no example sentence, " +
                    "so their card shows nothing and DrillGen builds no cloze: " +
                    bare.take(10).joinToString { it.id },
                bare.isEmpty()
            )
        }
    }

    /**
     * A gated vocab pack must be held back WITHOUT costing the deck a word. The SRS deck is
     * sized against the course (10 a lesson, and a floor test depends on it), so if gating ever
     * dropped or duplicated a word the course would quietly run short of vocabulary near the end,
     * which is the least visible place for it to happen.
     */
    @Test
    fun `pack gating reorders the deck without losing a word`() {
        allLangs.forEach { lang ->
            val packs = loadVocabPacks(lang)
            val authored = packs.flatMap { it.words }
            val ordered = com.corlang.app.data.DeckOrder.ordered(
                packs, com.corlang.app.data.Fsrs.NEW_WORDS_PER_DAY
            )
            assertEquals("$lang: gating changed the deck size", authored.size, ordered.size)
            assertEquals("$lang: gating dropped or duplicated a word",
                authored.map { it.id }.toSet(), ordered.map { it.id }.toSet())

            packs.filter { it.fromDay > 0 }.forEach { pack ->
                assertTrue("$lang: pack '${pack.title}' has a negative fromDay", pack.fromDay > 0)
                val firstSlot = (pack.fromDay - 1) * com.corlang.app.data.Fsrs.NEW_WORDS_PER_DAY
                val ids = pack.words.map { it.id }.toSet()
                val earliest = ordered.indexOfFirst { it.id in ids }
                assertTrue(
                    "$lang: '${pack.title}' is gated to lesson ${pack.fromDay} but its first " +
                        "word sits at slot $earliest, before lesson ${pack.fromDay} starts at " +
                        "$firstSlot",
                    earliest >= firstSlot
                )
            }
        }
    }

    /**
     * Compose's padding modifier REJECTS a negative value, and it does so at layout time, not at
     * compile time: the screen builds, ships, and crashes the moment it is opened. That is
     * exactly what happened to the Progress tab, from a design handoff asking for a "negative
     * 14dp vertical margin" so 48dp arrows could overlap a card's padding. There is no such
     * thing in Compose (Modifier.offset moves the drawing but not the layout), so a negative dp
     * inside padding() is always a crash waiting for the user to find it.
     *
     * A compiler cannot catch this and neither can a unit test that never lays anything out, so
     * it is caught here, in the source, the same way the dash rule is.
     */
    @Test
    fun `no negative padding anywhere in the ui`() {
        val srcRoot = listOf("src/main/java", "app/src/main/java")
            .map { File(it) }.firstOrNull { it.isDirectory }
            ?: error("source root not found from ${File(".").absolutePath}")
        // padding( ... (-8).dp ... ) in any argument position, across a single line.
        val negativePadding = Regex("""padding\s*\([^)]*\(\s*-\s*\d+(?:\.\d+)?\s*\)\s*\.dp""")
        val offenders = mutableListOf<String>()
        srcRoot.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            file.readLines(Charsets.UTF_8).forEachIndexed { i, line ->
                val t = line.trim()
                if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) return@forEachIndexed
                if (negativePadding.containsMatchIn(line)) {
                    offenders += "${file.name}:${i + 1}: ${t.take(80)}"
                }
            }
        }
        assertTrue(
            "negative dp passed to padding() crashes at layout time:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    /**
     * The release flow keeps three version declarations in sync by hand (build.gradle.kts
     * versionCode/versionName and releases/version.json); a desync ships an update banner
     * that points at the wrong build. This pins them together.
     */
    @Test
    fun `release version json matches the gradle version`() {
        val gradle = listOf("build.gradle.kts", "app/build.gradle.kts")
            .map { File(it) }.firstOrNull { it.isFile }?.readText(Charsets.UTF_8)
            ?: error("app/build.gradle.kts not found from ${File(".").absolutePath}")
        val versionJson = listOf("../releases/version.json", "releases/version.json")
            .map { File(it) }.firstOrNull { it.isFile }?.readText(Charsets.UTF_8)
            ?: error("releases/version.json not found")
        val code = Regex("versionCode\\s*=\\s*(\\d+)").find(gradle)!!.groupValues[1]
        val name = Regex("versionName\\s*=\\s*\"([^\"]+)\"").find(gradle)!!.groupValues[1]
        assertTrue("version.json versionCode != gradle versionCode $code",
            Regex("\"versionCode\"\\s*:\\s*$code\\b").containsMatchIn(versionJson))
        assertTrue("version.json versionName != gradle versionName $name",
            "\"$name\"" in versionJson)
    }

    /**
     * A themed block of vocabulary may not reach the learner long before the lesson that teaches
     * it. Croatian taught all twelve months as flashcards at lesson 8, sixteen lessons before
     * "Days, months & schedules" at lesson 25: nobody learns siječanj cold from a card, they meet
     * it, fail it, and it returns until the lesson finally explains what it was.
     *
     * `VocabPack.fromDay` is the fix and `DeckOrder.ordered` applies it. This gate exists because
     * the mechanism was built, used on two packs, and then drifted: twenty more packs had grown
     * the same gap unnoticed, the worst by 125 lessons.
     *
     * Measured through DeckOrder itself, never through raw authored position — an already-gated
     * pack is already correct, and measuring it raw reports it as broken.
     *
     * A gate never pushes a pack past its own level: an A1 pack whose theme lesson lands in B1
     * would starve A1 of common words, which is worse than the drift. Those cases are a PLAN
     * observation (no lesson at that level covers the theme) and are recorded in the registry,
     * not failed here.
     */
    @Test
    fun `themed vocabulary packs are not introduced long before their lesson`() {
        val offenders = mutableListOf<String>()
        wrapupLangs.forEach { lang ->
            // Derived from THIS course, not hardcoded. The map here was Croatian's
            // (A0 16, A1 77, A2 173, B1 344) while the loop ran over hr AND pt, so every
            // Portuguese pack was measured against a ceiling roughly twice its real one:
            // pt A1 ends at day 45, not 77. It never produced a false failure, which is exactly
            // why it survived, but a per-language number written once and reused for all
            // languages is the same skeleton violation in a test that it would be in the app.
            val levelEnd = loadPlan(lang).days
                .groupBy { it.level }.mapValues { e -> e.value.maxOf { it.day } }
            val packs = loadVocabPacks(lang)
            val deck = com.corlang.app.data.DeckOrder.ordered(packs, WORDS_PER_LESSON)
            val wordToPack = packs.flatMap { p -> p.words.map { it.id to p.id } }.toMap()
            val introOf = mutableMapOf<String, Int>()
            deck.forEachIndexed { i, w ->
                wordToPack[w.id]?.let { introOf.putIfAbsent(it, i / WORDS_PER_LESSON + 1) }
            }
            val lessonWords = loadPlan(lang).days.associate { day ->
                day.day to buildSet {
                    day.activities.forEach { a ->
                        a.items.forEach { addAll(tokens(it.hr)) }
                        a.lines.forEach { addAll(tokens(it.hr)) }
                        addAll(tokens(a.title))
                    }
                }
            }
            packs.forEach { p ->
                val words = p.words.map { it.hr.lowercase() }
                val theme = lessonWords.keys.sorted().firstOrNull { d ->
                    words.count { w -> w.split(" ").all { it in lessonWords.getValue(d) } } >= 3
                } ?: return@forEach
                val intro = introOf[p.id] ?: return@forEach
                val target = minOf(theme, levelEnd[p.level] ?: theme)
                if (target - intro >= MAX_PACK_EARLY) {
                    offenders += "$lang pack '${p.id}' (${p.level}) introduced at lesson $intro, " +
                        "taught at lesson $theme (+${theme - intro}); set fromDay = $target"
                }
            }
        }
        assertTrue(
            "themed packs arriving before their lesson (${offenders.size}):\n" +
                offenders.joinToString("\n").take(4000),
            offenders.isEmpty()
        )
    }

    private fun tokens(s: String): List<String> =
        Regex("[\\p{L}]+").findAll(s.lowercase()).map { it.value }.toList()

    // ---------- The wrap-up: the day's closing from-memory recall ----------
    //
    // Registry C21-C24. The wrap-up is GENERATED from the day's LEARN items
    // (Drills.kt:wrapupRecallPhrases), so a table row authored for the teaching screen becomes a
    // typing test unless the content forbids the shape. These four gates are the forbidding.
    //
    // SCOPE: the courses that have HAD the pass, not every course on disk. hr closed 2026-08-20,
    // pt closed 2026-08-21 (115 problems over 33 of its 240 days: paradigms in one row, table
    // rows written with arrows, rule notation like "do = de + o" inside the taught text, and six
    // B1 days whose own teaching sentences all sat past the recall cap). fr, de, it and es carry
    // the same classes untouched and are hidden from content/_index.json; widening to them is
    // tracked as an open sweep in docs/error-registry.md. Do not "helpfully" run these over
    // allLangs before the authoring pass for that course has actually landed.

    private val wrapupLangs = listOf("hr", "pt")

    /**
     * The CEFR levels declared clean. Widened one level at a time as the authoring pass lands,
     * so the gates hold every day already fixed without the whole 344-day course having to be
     * finished in one go. The target state is all four; docs/error-registry.md carries the
     * remaining levels as an open sweep.
     */
    private val gatedLevels = setOf("A0", "A1", "A2", "B1")

    private fun gatedDays(lang: String) = loadPlan(lang).days.filter { it.level in gatedLevels }

    /**
     * A typed answer must be typable. An arrow or an equation in the expected text is a RELATION
     * between two forms, not something a learner can produce: Grading.normalize strips only
     * ordinary punctuation, so the symbol survives into the comparison and the answer can never
     * match. Field report: a day 11 wrap-up scored 0/8 against linguistically perfect answers
     * because every expected string looked like "kava → kavu".
     *
     * Covers both surfaces the same class hides in: the typed question types, and the LEARN
     * items the wrap-up recruits.
     */
    @Test
    fun `typed answers are typable`() {
        val offenders = mutableListOf<String>()
        wrapupLangs.forEach { lang ->
            gatedDays(lang).forEach { day ->
                day.activities.forEach { act ->
                    act.questions
                        .filter { it.type == QuestionType.FILL || it.type == QuestionType.TRANSLATE }
                        .forEach { q ->
                            (listOf(q.answer) + q.accepted).forEach { a ->
                                PAIR_SYMBOLS.filter { it in a }.forEach {
                                    offenders += "$lang day ${day.day} ${q.type} answer '$a' contains '$it'"
                                }
                            }
                        }
                }
                // Candidates, not the filtered list: the app drops these, the content must not
                // contain them. Measured after the filter this assertion could never fail.
                recallCandidates(day).forEach { item ->
                    PAIR_SYMBOLS.filter { it in item.hr || it in item.en }.forEach {
                        offenders += "$lang day ${day.day} wrap-up item '${item.hr}' / '${item.en}' contains '$it'"
                    }
                    // Brackets belong on the English side, where they disambiguate. Inside the
                    // target they are part of the expected string, and normalize does not strip
                    // them: "Radila sam. (zena)" could only be matched by typing the brackets.
                    if (item.hr.any { it in "()·" }) {
                        offenders += "$lang day ${day.day} wrap-up answer '${item.hr}' carries a gloss"
                    }
                }
            }
        }
        assertTrue(
            "untypable symbols in typed answers (${offenders.size}):\n" +
                offenders.joinToString("\n").take(4000),
            offenders.isEmpty()
        )
    }

    /**
     * Two wrap-up rows glossed identically ask one English question with two different right
     * answers. Whichever the learner writes, one of them is marked wrong, and nothing on screen
     * says which was wanted.
     */
    @Test
    fun `wrap-up prompts are unambiguous`() {
        val offenders = mutableListOf<String>()
        wrapupLangs.forEach { lang ->
            gatedDays(lang).forEach { day ->
                recallCandidates(day).take(WRAPUP_ASKED)
                    .groupBy { Grading.normalize(it.en, strict = true) }
                    .filter { it.value.size > 1 }
                    .forEach { (prompt, items) ->
                        offenders += "$lang day ${day.day}: '$prompt' -> " +
                            items.joinToString(" | ") { it.hr }
                    }
            }
        }
        assertTrue("ambiguous wrap-up prompts:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    /**
     * One ask, one answer. "dvjesto, tristo, petsto" glossed "two hundred, three hundred, five
     * hundred" is three answers wearing one prompt, and the grader accepts only all three typed
     * in order. A real sentence carries commas too, and is told apart by ending like one.
     */
    @Test
    fun `wrap-up asks are single answers`() {
        val offenders = mutableListOf<String>()
        wrapupLangs.forEach { lang ->
            gatedDays(lang).forEach { day ->
                recallCandidates(day).forEach { item ->
                    val parts = item.hr.split(",").map { it.trim() }
                    if (parts.size > 1 && parts.all { it.isNotEmpty() } &&
                        item.hr.trimEnd().last() !in ".?!".toSet()
                    ) {
                        offenders += "$lang day ${day.day}: '${item.hr}' / '${item.en}'"
                    }
                }
            }
        }
        assertTrue(
            "wrap-up asks with several answers (${offenders.size}):\n" +
                offenders.joinToString("\n").take(4000),
            offenders.isEmpty()
        )
    }

    /**
     * Every lesson ends with a real from-memory recall of what it just taught. Below the
     * [WRAPUP_MIN] the session builder silently replays the day's exercise instead, which
     * retests recognition the learner just did rather than production they have not.
     */
    @Test
    fun `every day has a real wrap-up`() {
        val offenders = mutableListOf<String>()
        wrapupLangs.forEach { lang ->
            gatedDays(lang).forEach { day ->
                val n = wrapupRecallPhrases(day).size
                if (n < WRAPUP_MIN) offenders += "$lang day ${day.day} (${day.level}): $n recallable"
            }
        }
        assertTrue(
            "days with no real wrap-up (${offenders.size}):\n" +
                offenders.joinToString("\n").take(4000),
            offenders.isEmpty()
        )
    }

    /**
     * Learner-facing titles are English. The target language is welcome INSIDE one, as the
     * example it names ("Big numbers: sto, tisuca, milijun", "At the market (Na trznici)"), but
     * the head of the title, the part that tells the learner what this lesson is, must be
     * readable by someone who does not speak the language yet. Day 16 shipped as
     * "Veliki brojevi: sto, tisuca, milijun".
     *
     * Partial automation, and honestly so: this catches the diacritic and function-word classes,
     * not every Croatian phrase ("Veliki brojevi" carries neither). The authoring pass is the
     * real check; this holds the line against regression. See registry C23.
     */
    @Test
    fun `learner-facing titles are english`() {
        val offenders = mutableListOf<String>()
        wrapupLangs.forEach { lang ->
            fun check(where: String, title: String) {
                // The head: everything before the first ':' or '(' introduces an example.
                val head = title.substringBefore(':').substringBefore('(').trim()
                val words = head.lowercase().split(Regex("[^\\p{L}]+"))
                    .filter { it.isNotBlank() && it !in PT_TITLE_ALLOW }
                val bare = words.joinToString(" ")
                val (markers, letters) = when (lang) {
                    "pt" -> PT_TITLE_MARKERS to PT_LETTERS
                    else -> HR_TITLE_MARKERS to HR_LETTERS
                }
                val hits = markers.filter { it in words } +
                    letters.filter { it in bare }.map { "'$it'" }
                if (hits.isNotEmpty()) offenders += "$lang $where: '$title' -> ${hits.joinToString()}"
            }
            gatedDays(lang).forEach { day ->
                check("day ${day.day} title", day.title)
                day.activities.forEach { check("day ${day.day} ${it.type}", it.title) }
            }
        }
        assertTrue(
            "target-language titles (${offenders.size}):\n" +
                offenders.joinToString("\n").take(4000),
            offenders.isEmpty()
        )
    }

    private companion object {
        /** Mirrors Fsrs.NEW_WORDS_PER_DAY: the deck slots one lesson consumes. */
        const val WORDS_PER_LESSON = 10

        /** A themed pack this many lessons ahead of its own lesson is a scheduling accident. */
        const val MAX_PACK_EARLY = 15

        /** Mirrors WrapupRecall's .take(8): the items a learner is actually asked. */
        const val WRAPUP_ASKED = 8

        /** Mirrors SessionPlayer's threshold for building a recall wrap-up at all. */
        const val WRAPUP_MIN = 4

        /** Letters no other language in the course uses, so their presence is decisive. */
        val HR_LETTERS = listOf("č", "ć", "đ", "š", "ž")

        /**
         * Croatian function words frequent enough in titles to be a reliable tell, and absent
         * (or harmless) as English words. "i" and "u" are excluded: too collidey with English.
         */
        val HR_TITLE_MARKERS = listOf(
            "je", "su", "se", "sam", "si", "smo", "ste", "na", "za", "od", "iz",
            "sto", "kako", "koliko", "kada", "gdje", "tko", "zasto", "moj", "tvoj", "nas",
            "vas", "njegov", "brojevi", "glagoli", "rijeci", "vjezba", "ponavljanje"
        )

        /**
         * Portuguese function words that are NOT also English or a common English borrowing.
         * "do", "as", "no", "se", "para", "eu", "os" and "um" are deliberately absent: they are
         * ordinary English words ("Do you agree?", "As soon as I arrived", "Saying no"), and a
         * marker that fires on those turns the gate into noise nobody reruns.
         */
        val PT_TITLE_MARKERS = listOf(
            "uma", "dos", "das", "nas", "aos", "pelo", "pela", "nosso", "nossa", "meu", "minha",
            "teu", "tua", "seu", "sua", "quem", "qual", "quais", "onde", "quando", "porque",
            "porquê", "sobre", "entre", "sem", "depois", "antes", "hoje", "ontem", "amanhã",
            "muito", "mais", "menos", "tudo", "nada", "isto", "isso", "aquilo", "vamos", "falar",
            "fazer", "dizer", "pedir", "escrever", "verbos", "frases", "palavras", "revisão"
        )

        /**
         * Portuguese-only letters. The accented vowels alone are not decisive, because English
         * borrows several words whole: café is the one this course actually uses, and it is a
         * dictionary English word, not a lapse.
         */
        val PT_LETTERS = listOf("ã", "õ", "ç")

        val PT_TITLE_ALLOW = listOf("café", "cafés")
    }
}
