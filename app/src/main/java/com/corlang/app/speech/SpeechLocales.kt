package com.corlang.app.speech

import java.util.Locale

/**
 * Maps a Corlang language code to its speech locale, so TTS and speech recognition follow the
 * active language (hr -> Croatian, fr -> French). One place to add a language's voice.
 */
object SpeechLocales {
    fun localeFor(code: String): Locale = when (code) {
        "fr" -> Locale("fr", "FR")
        // pt-PT explicitly: the default pt voice on many devices is BRAZILIAN — the whole point
        // of the course is European Portuguese, so the region must never be left to chance.
        "pt" -> Locale("pt", "PT")
        else -> Locale("hr", "HR")
    }

    /** BCP-47 tag for the speech-recognizer intent extras. */
    fun tagFor(code: String): String = when (code) {
        "fr" -> "fr-FR"
        "pt" -> "pt-PT"
        else -> "hr-HR"
    }
}
