package com.corlang.app.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.corlang.app.data.ContentRepository

/**
 * One-shot speech recognition wrapper for the active language (hr/fr). Lets a learner say a phrase
 * and get back what the recognizer heard, so pronunciation can be checked against a target. Uses
 * the system recognizer (Google), which needs the RECORD_AUDIO permission and, usually, a network.
 *
 * All calls must run on the main thread (SpeechRecognizer requirement).
 */
class SpeechInput(private val context: Context) {

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private var recognizer: SpeechRecognizer? = null
    // The active session's listening callback: a new listen() (or cancel/dispose) must tell the
    // PREEMPTED UI it stopped, or that mic button stays stuck on "Listening…" forever — the
    // destroyed recognizer never fires its own callbacks.
    private var activeOnListening: ((Boolean) -> Unit)? = null
    private var langTag: String = "hr-HR"

    /** Point recognition at the active language; BCP-47 tag comes from the language's meta.json. */
    fun setLanguage(code: String) {
        langTag = runCatching { ContentRepository(context).meta(code).speechTag }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: SpeechLocales.fallbackTag(code)
    }

    /**
     * Starts listening in the active language.
     *
     * [onListening] fires true when the mic opens and false when it closes; [onResult] delivers
     * EVERY hypothesis the recogniser offers, best first (it is asked for three, and for a
     * non-native speaker the second is often the closer match, so the caller scores them all);
     * [onError] a readable message, with [unusableLanguage] true when the failure is the language
     * itself rather than this attempt. That distinction matters: a caller can retry a misheard
     * phrase, but it must stop offering recognition altogether for a language the device cannot
     * transcribe, instead of blaming the learner's pronunciation for it.
     *
     * Offline first. The rest of the app works without a connection, so recognition tries the
     * on-device path and only falls back to the networked recogniser if the device says it
     * cannot serve the language locally.
     */
    fun listen(
        onListening: (Boolean) -> Unit,
        onResult: (List<String>) -> Unit,
        onError: (message: String, unusableLanguage: Boolean) -> Unit
    ) {
        start(preferOffline = true, onListening, onResult, onError)
    }

    private fun start(
        preferOffline: Boolean,
        onListening: (Boolean) -> Unit,
        onResult: (List<String>) -> Unit,
        onError: (String, Boolean) -> Unit
    ) {
        cancel()
        if (!isAvailable()) {
            onError("Speech recognition isn't available on this device.", true)
            return
        }
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        activeOnListening = onListening
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(langTag))
        }
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onListening(true)
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() = onListening(false)
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onResults(results: Bundle?) {
                onListening(false)
                val heard = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
                if (heard.isEmpty()) onError("Didn't catch that. Try again.", false)
                else onResult(heard)
                cleanup()
            }

            override fun onError(error: Int) {
                cleanup()
                // The offline attempt failing on the language or the network is not a verdict on
                // the learner: retry once online before deciding the language is unusable.
                if (preferOffline && error in RETRY_ONLINE) {
                    start(preferOffline = false, onListening, onResult, onError)
                    return
                }
                onListening(false)
                onError(messageFor(error), error in LANGUAGE_UNUSABLE)
            }
        })
        runCatching { sr.startListening(intent) }
            .onFailure {
                onError("Speech recognition isn't available on this device.", true)
                cleanup()
            }
    }

    private companion object {
        /** Offline could not serve this; the networked recogniser still might. */
        val RETRY_ONLINE = setOf(
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT,
        )

        /** Nothing the learner does will make this attempt work: stop offering recognition. */
        val LANGUAGE_UNUSABLE = setOf(
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        )
    }

    fun cancel() {
        activeOnListening?.invoke(false)   // release the preempted UI before destroying
        runCatching { recognizer?.cancel() }
        cleanup()
    }

    private fun cleanup() {
        activeOnListening = null
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun messageFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Try again, a bit slower."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't hear anything. Tap and speak."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Needs a connection for speech recognition."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "Speech recognition for this language isn't installed on this device."
        else -> "Couldn't recognise speech. Try again."
    }
}
