package com.corlang.app.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

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

    /** Point recognition at the active language (hr -> hr-HR, fr -> fr-FR). */
    fun setLanguage(code: String) {
        langTag = SpeechLocales.tagFor(code)
    }

    /**
     * Starts listening in the active language. [onListening] fires true when the mic opens and
     * false when it closes; [onResult] delivers the best transcript; [onError] a readable message.
     */
    fun listen(
        onListening: (Boolean) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        cancel()
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        activeOnListening = onListening
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
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
                    ?.firstOrNull { it.isNotBlank() }
                    .orEmpty()
                if (heard.isBlank()) onError("Didn't catch that. Try again.") else onResult(heard)
                cleanup()
            }

            override fun onError(error: Int) {
                onListening(false)
                onError(messageFor(error))
                cleanup()
            }
        })
        runCatching { sr.startListening(intent) }
            .onFailure { onError("Speech recognition unavailable on this device."); cleanup() }
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
