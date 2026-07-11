package com.corlang.app.ui.screens

import com.corlang.app.data.model.Question
import com.corlang.app.data.model.QuestionType
import java.text.Normalizer

/** Offline answer-grading. Deterministic, accent- and case-insensitive where appropriate. */
object Grading {

    /**
     * Lowercase, trim, collapse spaces, strip punctuation. By default also strips diacritics
     * (lenient legacy behaviour); with [strict] = true diacritics are preserved so that
     * exam-grade answers require correct Croatian spelling (želim ≠ zelim).
     */
    fun normalize(s: String, strict: Boolean = false): String {
        val base = s.trim().lowercase()
        val deAccented = if (strict) {
            Normalizer.normalize(base, Normalizer.Form.NFC)
        } else {
            Normalizer.normalize(base, Normalizer.Form.NFD)
                .replace("\\p{Mn}+".toRegex(), "")      // remove diacritics
                // đ does not decompose under NFD, map it explicitly so leniency is consistent.
                .replace('đ', 'd')
        }
        return deAccented
            .replace("[.,!?;:\"'’]".toRegex(), "")       // remove common punctuation
            .replace("\\s+".toRegex(), " ")
    }

    /** True if the learner's free-text [input] matches the FILL answer or any accepted variant. */
    fun gradeFill(q: Question, input: String): Boolean {
        val strict = q.strictDiacritics
        val target = (listOf(q.answer) + q.accepted).map { normalize(it, strict) }
        return normalize(input, strict) in target
    }

    /** True if [chosen] is the correct MCQ option. */
    fun gradeMcq(q: Question, chosen: String): Boolean =
        normalize(chosen, q.strictDiacritics) == normalize(q.answer, q.strictDiacritics)

    /** True if the assembled token sequence equals the correct order. */
    fun gradeReorder(q: Question, assembled: List<String>): Boolean =
        assembled.map { normalize(it) } == q.ordered.map { normalize(it) }

    /** True if every left item was matched to its correct right item. */
    fun gradeMatch(q: Question, mapping: Map<String, String>): Boolean =
        q.pairs.all { normalize(mapping[it.left] ?: "") == normalize(it.right) }

    /** Convenience for simple MCQ/FILL grading used by the quiz runner. */
    fun isCorrect(q: Question, response: String): Boolean = when (q.type) {
        QuestionType.MCQ -> gradeMcq(q, response)
        QuestionType.FILL -> gradeFill(q, response)
        else -> false // MATCH/REORDER use their dedicated graders
    }
}
