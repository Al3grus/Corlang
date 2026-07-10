package com.corlang.app

import android.content.Context
import com.corlang.app.data.ContentRepository
import com.corlang.app.data.ProgressRepository
import com.corlang.app.data.WordsRepository
import com.corlang.app.data.db.AppDatabase
import com.corlang.app.data.prefs.LanguagePrefs
import com.corlang.app.speech.TtsManager

/**
 * Tiny manual dependency container. Keeps construction in one place without pulling in a DI
 * framework for v1; can be swapped for Hilt later without touching call sites that read these.
 */
class AppContainer(context: Context) {
    val content: ContentRepository = ContentRepository(context)
    val languagePrefs: LanguagePrefs = LanguagePrefs(context)
    val progress: ProgressRepository =
        ProgressRepository(AppDatabase.get(context).progressDao())
    val words: WordsRepository =
        WordsRepository(AppDatabase.get(context).progressDao(), content)
    val tts: TtsManager = TtsManager(context)
}

class CorlangApp : android.app.Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
