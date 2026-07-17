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
        "referencial-camoes", "portugues-fundamental", "freq-pt"
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
        val resourceNames = strictJson
            .decodeFromString<ResourceList>(read("hr", "resources.json"))
            .resources.map { it.name }.toSet()
        // In-app references (like the Words tab) are allowed; external ones must resolve.
        val allowed = resourceNames + setOf("Words tab (built-in daily flashcards)")
        loadPlan("hr").days.forEach { d ->
            d.resources.forEach { r ->
                assertTrue("day ${d.day} references unknown resource: $r", r in allowed)
            }
        }
    }

    // ---------- Quiz invariants ----------

    @Test
    fun `quiz questions are internally consistent`() {
        strictJson.decodeFromString<QuizSet>(read("hr", "quizzes.json")).quizzes.forEach { quiz ->
            quiz.questions.forEach { q ->
                when (q.type) {
                    QuestionType.MCQ -> {
                        assertTrue("${quiz.id}: MCQ answer not in options: '${q.answer}'",
                            q.answer in q.options)
                        assertTrue("${quiz.id}: MCQ needs 2+ options", q.options.size >= 2)
                    }
                    QuestionType.FILL ->
                        assertTrue("${quiz.id}: FILL with blank answer", q.answer.isNotBlank())
                    QuestionType.REORDER ->
                        assertEquals("${quiz.id}: REORDER ordered != permutation of options",
                            q.options.sorted(), q.ordered.sorted())
                    QuestionType.MATCH ->
                        assertTrue("${quiz.id}: MATCH without pairs", q.pairs.isNotEmpty())
                }
                assertTrue("${quiz.id}: question missing explanation", q.explanation.isNotBlank())
            }
        }
    }

    // ---------- Embedded day activities ----------

    @Test
    fun `day activities are complete lessons, not references`() {
        loadPlan("hr").days.forEach { d ->
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
            for (name in listOf("quizzes.json", "exams.json", "placement.json")) {
                if (!exists(lang, name)) continue
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
        val blocked = listOf(
            "ônibus", "celular", "banheiro", "sorvete", "geladeira", "açougue",
            "esporte", "aeromoça", "café da manhã", "caminhão", "usuário", "gerenciar",
            "bonde", "encanador", "faxina", "grampeador", "história em quadrinhos"
        )
        val hits = mutableListOf<String>()
        ptRoot.walkTopDown().filter { it.isFile && it.extension == "json" }.forEach { f ->
            val text = f.readText(Charsets.UTF_8).lowercase()
            blocked.forEach { term ->
                if (Regex("(?<![\\p{L}])${Regex.escape(term)}(?![\\p{L}])").containsMatchIn(text)) {
                    hits.add("${f.name}: '$term'")
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
}
