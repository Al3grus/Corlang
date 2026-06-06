package com.frenchai.app.data

import android.content.Context
import com.frenchai.app.data.model.Cheatsheet
import com.frenchai.app.data.model.FeynmanSet
import com.frenchai.app.data.model.LanguageMeta
import com.frenchai.app.data.model.Levels
import com.frenchai.app.data.model.QuizSet
import com.frenchai.app.data.model.ResourceList
import com.frenchai.app.data.model.StudyPlan
import kotlinx.serialization.json.Json

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

    /** Cache keyed by asset path, so each JSON file is read and decoded at most once. */
    private val cache = mutableMapOf<String, Any>()

    /** Language codes that ship with the app, in display order. */
    val availableLanguages: List<String> = listOf("fr", "hr")

    private inline fun <reified T : Any> load(lang: String, file: String): T {
        val path = "content/$lang/$file"
        @Suppress("UNCHECKED_CAST")
        return cache.getOrPut(path) {
            val text = context.assets.open(path).bufferedReader().use { it.readText() }
            json.decodeFromString<T>(text)
        } as T
    }

    fun meta(lang: String): LanguageMeta = load(lang, "meta.json")
    fun allMeta(): List<LanguageMeta> = availableLanguages.map { meta(it) }
    fun cheatsheet(lang: String): Cheatsheet = load(lang, "cheatsheet.json")
    fun levels(lang: String): Levels = load(lang, "levels.json")
    fun plan(lang: String): StudyPlan = load(lang, "plan.json")
    fun quizzes(lang: String): QuizSet = load(lang, "quizzes.json")
    fun feynman(lang: String): FeynmanSet = load(lang, "feynman.json")
    fun resources(lang: String): ResourceList = load(lang, "resources.json")
}
