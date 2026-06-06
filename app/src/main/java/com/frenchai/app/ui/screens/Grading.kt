package com.frenchai.app.ui.screens

import com.frenchai.app.data.model.Question
import com.frenchai.app.data.model.QuestionType
import java.text.Normalizer

/** Offline answer-grading. Deterministic, accent- and case-insensitive where appropriate. */
object Grading {

    /** Lowercase, trim, collapse spaces, strip accents and most punctuation. */
    fun normalize(s: String): String {
        val stripped = Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")          // remove diacritics
            .replace("[.,!?;:\"'’]".toRegex(), "")       // remove common punctuation
            .replace("\\s+".toRegex(), " ")
        return stripped
    }

    /** True if the learner's free-text [input] matches the FILL answer or any accepted variant. */
    fun gradeFill(q: Question, input: String): Boolean {
        val target = (listOf(q.answer) + q.accepted).map { normalize(it) }
        return normalize(input) in target
    }

    /** True if [chosen] is the correct MCQ option. */
    fun gradeMcq(q: Question, chosen: String): Boolean =
        normalize(chosen) == normalize(q.answer)

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
