package com.corlang.app.speech

import com.corlang.app.ui.screens.Grading

/**
 * How close a recognised utterance is to the line the learner was asked to say.
 *
 * The original rule was word-set overlap needing 80% of the target's words to appear verbatim.
 * That is far too brittle for what a recogniser actually returns: it hears "e" for "è", merges
 * "de l'eau" into "deleau", drops an article, punctuates, and inflects. Every one of those is a
 * correct utterance scored as a miss, and a pronunciation check that fails correct speech is
 * worse than no check at all, because the learner cannot tell the two apart.
 *
 * So this scores on CHARACTERS, not words: normalised edit distance, which degrades smoothly
 * with a wrong ending or a missing article instead of falling off a cliff. Word overlap is kept
 * as a second opinion and the better of the two wins, because it is the more forgiving measure
 * when the recogniser splits or reorders words.
 *
 * Every hypothesis the recogniser offers is scored, not just the top one. It returns up to three
 * ranked guesses and the second is very often the better match for a non-native speaker; asking
 * for them and then ignoring them was pure waste.
 *
 * Pure, so the thresholds can be tested without a microphone.
 */
object PronunciationScore {

    enum class Verdict { PASS, CLOSE, MISS }

    /** Similarity at or above this is treated as "said it right". */
    const val PASS_AT = 0.75
    /** Below [PASS_AT] but at or above this is "close, go again". */
    const val CLOSE_AT = 0.45

    /** The best verdict across every hypothesis the recogniser returned. */
    fun verdict(target: String, heard: List<String>): Verdict {
        val best = heard.maxOfOrNull { similarity(target, it) } ?: 0.0
        return when {
            best >= PASS_AT -> Verdict.PASS
            best >= CLOSE_AT -> Verdict.CLOSE
            else -> Verdict.MISS
        }
    }

    /** The hypothesis that matched best, for showing the learner what was heard. */
    fun bestHypothesis(target: String, heard: List<String>): String? =
        heard.maxByOrNull { similarity(target, it) }

    /** 0.0 (nothing in common) to 1.0 (identical after normalisation). */
    fun similarity(target: String, heard: String): Double {
        val t = Grading.normalize(target)
        val h = Grading.normalize(heard)
        if (t.isBlank()) return 0.0
        return maxOf(charSimilarity(t, h), wordOverlap(t, h))
    }

    private fun charSimilarity(t: String, h: String): Double {
        // Spaces removed: a recogniser that hears "delo" for "de l'eau" has heard it correctly,
        // and word boundaries are the least reliable thing it reports.
        val a = t.replace(" ", "")
        val b = h.replace(" ", "")
        if (a.isEmpty()) return 0.0
        val distance = levenshtein(a, b)
        return (1.0 - distance.toDouble() / maxOf(a.length, b.length)).coerceIn(0.0, 1.0)
    }

    private fun wordOverlap(t: String, h: String): Double {
        val want = t.split(" ").filter { it.isNotBlank() }
        if (want.isEmpty()) return 0.0
        val got = h.split(" ").filter { it.isNotBlank() }.toSet()
        return want.count { it in got }.toDouble() / want.size
    }

    /** Two-row edit distance: the whole string pair is short, so this stays trivial. */
    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val swap = prev; prev = cur; cur = swap
        }
        return prev[b.length]
    }
}
