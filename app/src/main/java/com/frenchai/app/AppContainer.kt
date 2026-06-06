package com.frenchai.app

import android.content.Context
import com.frenchai.app.data.ContentRepository
import com.frenchai.app.data.ProgressRepository
import com.frenchai.app.data.db.AppDatabase
import com.frenchai.app.data.prefs.LanguagePrefs

/**
 * Tiny manual dependency container. Keeps construction in one place without pulling in a DI
 * framework for v1; can be swapped for Hilt later without touching call sites that read these.
 */
class AppContainer(context: Context) {
    val content: ContentRepository = ContentRepository(context)
    val languagePrefs: LanguagePrefs = LanguagePrefs(context)
    val progress: ProgressRepository =
        ProgressRepository(AppDatabase.get(context).progressDao())
}

class FrenchaiApp : android.app.Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
