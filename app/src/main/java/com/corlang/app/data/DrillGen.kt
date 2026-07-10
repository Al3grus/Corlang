package com.corlang.app.data

import com.corlang.app.data.model.VocabWord

/**
 * Auto-generated drill items from the validated vocabulary deck. Pure and unit-tested.
 *
 * Safety principle: the app never *generates* Croatian forms as correct answers — the correct
 * answer is always lifted verbatim from a human-authored, QA'd example sentence. Distractors
 * are ending-mutations that are wrong IN THAT SENTENCE even if they exist as forms elsewhere.
 */
object DrillGen {

    /** One cloze item: the example sentence with the target form blanked out. */
    data class Cloze(
        val sentence: String,      // "Pijem ___ s mlijekom."
        val gloss: String,         // English translation of the full sentence
        val answer: String,        // the exact form from the sentence, e.g. "kavu"
        val options: List<String>  // shuffled: answer + ending-mutated distractors
    )

    private val letters = Regex("[\\p{L}]+")

    /**
     * Builds a cloze from a word's example sentence by locating the inflected form of the
     * headword. Returns null when the form can't be located confidently (e.g. suppletive
     * verb stems like pisati→pišem) — skipping is always safe.
     */
    fun clozeFor(word: VocabWord, shuffle: Boolean = true): Cloze? {
        val ex = word.example ?: return null
        val head = word.hr.lowercase()
        if (' ' in head || head.length < 4) return null   // single-word heads only

        // Find the sentence token sharing the longest prefix with the headword.
        // Noun declension only changes the ending (kava→kavu, grad→gradovima), so all but
        // the last 2 chars of the headword must match; stem-mutating verbs (pisati→pišem)
        // are rejected on purpose — never risk blanking the wrong word.
        val tokens = letters.findAll(ex.target).map { it.value }.toList()
        val minShared = maxOf(3, head.length - 2)
        val match = tokens
            .filter { it.length >= 3 }
            .maxByOrNull { commonPrefix(it.lowercase(), head) }
            ?.takeIf { commonPrefix(it.lowercase(), head) >= minShared }
            ?: return null

        val sentence = ex.target.replaceFirst(match, "___")
        if ("___" !in sentence) return null

        val distractors = endingMutations(match)
            .filter { !it.equals(match, ignoreCase = true) }
            .distinct()
            .take(3)
        if (distractors.size < 2) return null

        val options = (listOf(match) + distractors).let { if (shuffle) it.shuffled() else it }
        return Cloze(sentence = sentence, gloss = ex.gloss, answer = match, options = options)
    }

    /** Builds up to [n] cloze items, preferring the given word order (caller sorts by due/seen). */
    fun buildClozeItems(words: List<VocabWord>, n: Int): List<Cloze> =
        words.asSequence().mapNotNull { clozeFor(it) }.take(n).toList()

    private fun commonPrefix(a: String, b: String): Int {
        var i = 0
        while (i < a.length && i < b.length && a[i] == b[i]) i++
        return i
    }

    /** Plausible wrong endings: strip the case/number ending, re-attach common ones. */
    internal fun endingMutations(form: String): List<String> {
        val stem = when {
            form.length > 4 && (form.endsWith("ima") || form.endsWith("ama")) ->
                form.dropLast(3)
            form.length > 3 && (form.endsWith("om") || form.endsWith("em") ||
                form.endsWith("ju") || form.endsWith("oj")) -> form.dropLast(2)
            form.last().lowercaseChar() in "aeiou" -> form.dropLast(1)
            else -> form
        }
        return listOf("a", "u", "e", "i", "om", "ama").map { stem + it }
    }

    /** One typed-recall item: produce the Croatian for an English gloss. */
    data class Recall(
        val en: String,
        val answerHr: String,
        val posHint: String?
    )

    /** Builds up to [n] typed-recall items; caller supplies words ordered by priority (due first). */
    fun buildRecallItems(words: List<VocabWord>, n: Int): List<Recall> =
        words.asSequence()
            .filter { it.hr.length >= 2 }
            .map { Recall(en = it.en, answerHr = it.hr, posHint = it.pos) }
            .take(n)
            .toList()
}
