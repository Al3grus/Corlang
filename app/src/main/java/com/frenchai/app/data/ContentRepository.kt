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
 */
class ContentRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private data class Bundle(
        val meta: LanguageMeta,
        val cheatsheet: Cheatsheet,
        val levels: Levels,
        val plan: StudyPlan,
        val quizzes: QuizSet,
        val feynman: FeynmanSet,
        val resources: ResourceList
    )

    private val cache = mutableMapOf<String, Bundle>()

    /** Language codes that ship with the app, in display order. */
    val availableLanguages: List<String> = listOf("fr", "hr")

    private fun bundle(lang: String): Bundle = cache.getOrPut(lang) {
        Bundle(
            meta = read("content/$lang/meta.json"),
            cheatsheet = read("content/$lang/cheatsheet.json"),
            levels = read("content/$lang/levels.json"),
            plan = read("content/$lang/plan.json"),
            quizzes = read("content/$lang/quizzes.json"),
            feynman = read("content/$lang/feynman.json"),
            resources = read("content/$lang/resources.json")
        )
    }

    private inline fun <reified T> read(path: String): T {
        val text = context.assets.open(path).bufferedReader().use { it.readText() }
        return json.decodeFromString(text)
    }

    fun meta(lang: String): LanguageMeta = bundle(lang).meta
    fun allMeta(): List<LanguageMeta> = availableLanguages.map { meta(it) }
    fun cheatsheet(lang: String): Cheatsheet = bundle(lang).cheatsheet
    fun levels(lang: String): Levels = bundle(lang).levels
    fun plan(lang: String): StudyPlan = bundle(lang).plan
    fun quizzes(lang: String): QuizSet = bundle(lang).quizzes
    fun feynman(lang: String): FeynmanSet = bundle(lang).feynman
    fun resources(lang: String): ResourceList = bundle(lang).resources
}
