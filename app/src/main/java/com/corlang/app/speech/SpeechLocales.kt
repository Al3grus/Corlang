package com.corlang.app.speech

import java.util.Locale

/**
 * Turns a BCP-47 speech tag into a [Locale] for TTS and speech recognition. The tag itself is
 * DATA: it comes from each language's `meta.json` `speechTag` (e.g. "pt-PT", "de-DE") so adding a
 * language's voice needs no code change here. The region matters — the default pt voice on many
 * devices is Brazilian and the default de could be Austrian/Swiss — which is exactly why the tag
 * is authored per language rather than derived from the bare code.
 */
object SpeechLocales {
    /** Parse a BCP-47 tag like "pt-PT" into a Locale; blank/malformed falls back to Croatian. */
    fun localeFromTag(tag: String?): Locale {
        val parts = tag?.split('-', '_')?.filter { it.isNotBlank() }.orEmpty()
        return when (parts.size) {
            0 -> Locale("hr", "HR")
            1 -> Locale(parts[0])
            else -> Locale(parts[0], parts[1])
        }
    }

    /**
     * Last-resort tag when a language's meta.json omits `speechTag`. Derives "<code>-<CODE>",
     * which is correct for every current course; meta.json's speechTag overrides it when a
     * language needs a region that isn't just the uppercased code.
     */
    fun fallbackTag(code: String): String =
        if (code.isBlank()) "hr-HR" else "$code-${code.uppercase()}"
}
