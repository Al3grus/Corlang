package com.corlang.app.speech

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class TtsState { INITIALIZING, READY, LANGUAGE_MISSING, UNAVAILABLE }

/**
 * Croatian text-to-speech via the system engine. Lazy: the engine is created on first use.
 * Never blocks features, callers observe [state] and degrade gracefully (e.g. LISTEN
 * questions offer a "reveal transcript" fallback when Croatian isn't available).
 */
class TtsManager(private val context: Context) {

    private val _state = MutableStateFlow(TtsState.INITIALIZING)
    val state: StateFlow<TtsState> = _state

    private var tts: TextToSpeech? = null
    private var initStarted = false

    private val croatian = Locale("hr", "HR")

    /** Idempotent; safe to call from any screen that might speak. */
    fun ensureInit() {
        if (initStarted) return
        initStarted = true
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                _state.value = TtsState.UNAVAILABLE
                return@TextToSpeech
            }
            val result = tts?.setLanguage(croatian)
            _state.value = when (result) {
                TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED ->
                    TtsState.LANGUAGE_MISSING
                else -> TtsState.READY
            }
        }
    }

    /**
     * Speaks Croatian text, replacing anything currently being spoken.
     * [rate] < 1 slows speech down (useful for listening drills).
     */
    fun speak(text: String, rate: Float = 1.0f) {
        ensureInit()
        val engine = tts ?: return
        if (_state.value != TtsState.READY) return
        engine.setSpeechRate(rate)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "corlang-utterance")
    }

    fun stop() {
        tts?.stop()
    }

    /** Opens the system dialog to install missing TTS voice data. */
    fun promptInstallVoice() {
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        initStarted = false
        _state.value = TtsState.INITIALIZING
    }
}
