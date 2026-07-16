package com.corlang.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.corlang.app.ai.AiClient
import com.corlang.app.billing.PremiumManager
import com.corlang.app.data.ContentRepository
import com.corlang.app.data.ProgressRepository
import com.corlang.app.data.WordsRepository
import com.corlang.app.data.backup.BackupManager
import com.corlang.app.data.db.AppDatabase
import com.corlang.app.data.prefs.LanguagePrefs
import com.corlang.app.speech.SpeechInput
import com.corlang.app.speech.TtsManager
import com.corlang.app.update.Updater

/**
 * Tiny manual dependency container. Keeps construction in one place without pulling in a DI
 * framework for v1; can be swapped for Hilt later without touching call sites that read these.
 */
class AppContainer(context: Context) {
    /**
     * Application-lifetime scope for persistence that must SURVIVE screen disposal. A
     * rememberCoroutineScope launch followed by navigation/unmount gets cancelled mid-write
     * (this silently lost day completions, exam sections, and teach-back scores). Main.immediate
     * so Compose snapshot state may still be touched safely after the write.
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val content: ContentRepository = ContentRepository(context)
    val languagePrefs: LanguagePrefs = LanguagePrefs(context)
    val progress: ProgressRepository =
        ProgressRepository(AppDatabase.get(context).progressDao())
    val words: WordsRepository =
        WordsRepository(AppDatabase.get(context).progressDao(), content)
    val backup: BackupManager =
        BackupManager(AppDatabase.get(context).progressDao(), languagePrefs)
    val ai: AiClient = AiClient()
    val premium: PremiumManager = PremiumManager(languagePrefs)
    val tts: TtsManager = TtsManager(context)
    val speech: SpeechInput = SpeechInput(context)
    val updater: Updater = Updater(context)
}

class CorlangApp : android.app.Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
