package com.corlang.app.data

import android.content.Context
import com.corlang.app.data.model.Cheatsheet
import com.corlang.app.data.model.ExamSpec
import com.corlang.app.data.model.FeynmanSet
import com.corlang.app.data.model.GrammarSyllabus
import com.corlang.app.data.model.LanguageMeta
import com.corlang.app.data.model.Levels
import com.corlang.app.data.model.QuizSet
import com.corlang.app.data.model.ResourceList
import com.corlang.app.data.model.StudyPlan
import com.corlang.app.data.model.VocabSet
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Cache sentinel meaning "the optional placement.json is absent for this language". */
private val PlacementNone = Any()

/**
 * Loads and caches the bundled JSON content for a language from `assets/content/<lang>/`.
 * Content is read-only and immutable at runtime, so simple in-memory caching is enough.
 *
 * Each file is parsed lazily and cached independently, so e.g. building the language switcher
 * (which only needs every language's `meta.json`) doesn't force-parse the 60-day plans or quizzes.
 */
class ContentRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Cache keyed by asset path, so each JSON file is read and decoded at most once.
     * Concurrent: read from the main thread by composition AND written from background warm-up
     * (a plain HashMap here would be a data race). getOrPut may rarely parse twice under
     * contention — harmless, parsing is idempotent.
     */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Any>()

    /** Language codes that ship with the app, in display order. All are live. */
    val availableLanguages: List<String> = listOf("hr", "fr", "pt", "de")

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    private fun assetExists(path: String): Boolean = try {
        context.assets.open(path).close(); true
    } catch (e: Exception) {
        false
    }

    private inline fun <reified T : Any> load(lang: String, file: String): T {
        val path = "content/$lang/$file"
        @Suppress("UNCHECKED_CAST")
        return cache.getOrPut(path) {
            json.decodeFromString<T>(readAsset(path))
        } as T
    }

    /**
     * Reads the explicit ordering manifest of a split content directory. The manifest, not
     * filesystem order, defines concatenation order, because for vocab that order IS the
     * SRS introduction order.
     */
    private fun indexOf(lang: String, dir: String): List<String> =
        json.decodeFromString(
            ListSerializer(String.serializer()),
            readAsset("content/$lang/$dir/_index.json")
        )

    fun meta(lang: String): LanguageMeta = load(lang, "meta.json")
    fun allMeta(): List<LanguageMeta> = availableLanguages.map { meta(it) }
    fun cheatsheet(lang: String): Cheatsheet = load(lang, "cheatsheet.json")
    fun levels(lang: String): Levels = load(lang, "levels.json")
    fun quizzes(lang: String): QuizSet = load(lang, "quizzes.json")
    fun feynman(lang: String): FeynmanSet = load(lang, "feynman.json")
    fun resources(lang: String): ResourceList = load(lang, "resources.json")

    /** Plan: merged from content/<lang>/plan/ (phase files) or the single plan.json. */
    fun plan(lang: String): StudyPlan {
        val key = "content/$lang/plan(merged)"
        @Suppress("UNCHECKED_CAST")
        return cache.getOrPut(key) {
            if (assetExists("content/$lang/plan/_index.json")) {
                val parts = indexOf(lang, "plan").map {
                    json.decodeFromString<StudyPlan>(readAsset("content/$lang/plan/$it"))
                }
                StudyPlan(title = parts.first().title, days = parts.flatMap { it.days })
            } else {
                json.decodeFromString<StudyPlan>(readAsset("content/$lang/plan.json"))
            }
        } as StudyPlan
    }

    /** Vocab: merged from content/<lang>/vocab/ (level batches) or the single vocab.json. */
    fun vocab(lang: String): VocabSet {
        val key = "content/$lang/vocab(merged)"
        @Suppress("UNCHECKED_CAST")
        return cache.getOrPut(key) {
            if (assetExists("content/$lang/vocab/_index.json")) {
                val packs = indexOf(lang, "vocab").flatMap {
                    json.decodeFromString<VocabSet>(readAsset("content/$lang/vocab/$it")).packs
                }
                VocabSet(packs)
            } else {
                json.decodeFromString<VocabSet>(readAsset("content/$lang/vocab.json"))
            }
        } as VocabSet
    }

    /** Grammar syllabus, optional file; empty when a language hasn't shipped one. */
    fun grammar(lang: String): GrammarSyllabus {
        val key = "content/$lang/grammar(opt)"
        @Suppress("UNCHECKED_CAST")
        return cache.getOrPut(key) {
            if (assetExists("content/$lang/grammar.json"))
                json.decodeFromString<GrammarSyllabus>(readAsset("content/$lang/grammar.json"))
            else GrammarSyllabus(levels = emptyList())
        } as GrammarSyllabus
    }

    /** Placement test, optional file; null when a language hasn't shipped one. */
    fun placement(lang: String): com.corlang.app.data.model.PlacementTest? {
        val key = "content/$lang/placement(opt)"
        return cache.getOrPut(key) {
            if (assetExists("content/$lang/placement.json"))
                json.decodeFromString<com.corlang.app.data.model.PlacementTest>(readAsset("content/$lang/placement.json"))
            else PlacementNone
        }.let { if (it === PlacementNone) null else it as com.corlang.app.data.model.PlacementTest }
    }

    /** Exam specs, optional file; empty list when a language hasn't shipped one. */
    fun exams(lang: String): List<ExamSpec> {
        val key = "content/$lang/exams(opt)"
        @Suppress("UNCHECKED_CAST")
        return cache.getOrPut(key) {
            if (assetExists("content/$lang/exams.json"))
                json.decodeFromString<List<ExamSpec>>(readAsset("content/$lang/exams.json"))
            else emptyList<ExamSpec>()
        } as List<ExamSpec>
    }
}
