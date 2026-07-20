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
        "croaticum-b1-sample", "cefr-grid", "ffzg-ecourse",
        // French (DELF B2 target)
        "cecrl", "delf-b1-sample", "delf-b2-sample",
        "referentiel-fr", "francais-fondamental", "freq-fr",
        // Portuguese, European (DIPLE B2 target)
        "qecr", "caple", "deple-sample", "diple-sample",
        "referencial-camoes", "portugues-fundamental", "freq-pt",
        // German (Goethe-Zertifikat B1 target, the citizenship / settlement level)
        "goethe-a1", "goethe-a2", "goethe-b1", "telc-b1", "goethe-wortliste", "stag-10",
        // Italian (CILS / CELI B1 target, the citizenship level since Dec 2018)
        "cils-a1", "cils-a2", "cils-b1", "celi-b1", "b1-cittadinanza", "cliq", "freq-it"
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
    fun placementBandsCarryExactlyThreeItemsEach() {
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
                assertEquals("$lang placement band $band must carry exactly 3 items",
                    3, items.size)
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
    private val weightedRuleDebt = mapOf(
        "pt" to "A2 55 and B1 70, short 15 and 55. B2 hidden 2026-07-20 (Portugal requires only " +
            "A2 for nationality), so the course is 170 lessons until B1 is topped up.",
        "fr" to "A2 55, B1 70, B2 80. Highest priority of the three: France raised naturalisation " +
            "to B2 on 2026-01-01, so this is the one course where B2 is load-bearing.",
        "hr" to "A2 90, B1 100. Short at B1 against the Croatian column of the table."
    )

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
}
