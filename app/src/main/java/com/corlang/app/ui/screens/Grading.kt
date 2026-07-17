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
            // Final trim: stripping punctuation can leave an edge space ("ce que ?" → "ce que ").
            // Space before ?/!/: is CORRECT French typography — it must not fail the grade.
            .trim()
    }

    /** True if the learner's free-text [input] matches the FILL answer or any accepted variant. */
    fun gradeFill(q: Question, input: String): Boolean {
        val strict = q.strictDiacritics
        val target = (listOf(q.answer) + q.accepted).map { normalize(it, strict) }
        return normalize(input, strict) in target
    }

    /**
     * True if [chosen] is the correct MCQ option. EXACT comparison, deliberately: options are
     * tapped, not typed, so [chosen] is always a canonical option string — and many MCQs exist
     * precisely to teach diacritics or capitalization ("š" vs "s", "engleski" vs "Engleski").
     * Lenient normalization here graded those distractors as correct while the highlight
     * (exact match) showed them red.
     */
    fun gradeMcq(q: Question, chosen: String): Boolean = chosen == q.answer

    /** True if the assembled token sequence equals the correct order. */
    fun gradeReorder(q: Question, assembled: List<String>): Boolean =
        assembled.map { normalize(it) } == q.ordered.map { normalize(it) }

    /**
     * Display form of a REORDER token: lowercased, edge punctuation stripped. Content stores
     * the tokens as they appear in the sentence ("Zovem", "se.") — shown verbatim, the capital
     * letter betrays the first token and the trailing dot the last. Diacritics stay (this is
     * display, not lenient matching); [gradeReorder] normalizes both sides, so tokens shaped
     * here still grade correctly against q.ordered.
     */
    fun reorderToken(s: String): String =
        s.trim().lowercase()
            .trim('.', ',', '!', '?', ';', ':', '"', '\'', '’', '…', '(', ')')
            .ifEmpty { s }

    /** True if every left item was matched to its correct right item. */
    fun gradeMatch(q: Question, mapping: Map<String, String>): Boolean =
        q.pairs.all { normalize(mapping[it.left] ?: "") == normalize(it.right) }

    /**
     * All typed answers accepted for a recall target with " / " alternatives.
     *
     * Two content shapes exist and need different expansions:
     *  - Shared tail — "on / ona je" teaches "on je" OR "ona je": each leading alternative
     *    borrows the last alternative's tail. A bare "on" is NOT accepted (it drops the verb
     *    the gloss "he / she is" asks for).
     *  - Complete alternatives — "dobar dan / bok": either full form is right.
     * Writing both (e.g. "on/ona je") is always accepted via slash-insensitive comparison
     * in [gradeRecall].
     */
    fun recallVariants(answer: String): List<String> {
        val parts = answer.split(" / ").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return listOf(answer)
        val tail = parts.last().split(" ").drop(1).joinToString(" ")
        return buildList {
            add(answer)   // both alternatives, as written
            if (tail.isBlank()) {
                addAll(parts)                                     // complete alternatives
            } else {
                parts.dropLast(1).forEach { add("$it $tail") }    // "on" -> "on je"
                add(parts.last())                                 // "ona je"
            }
        }
    }

    /** True if the typed [input] matches any accepted variant of the recall [answer].
     *  Strict diacritics (exam-grade), slash-insensitive ("on/ona je" == "on / ona je"). */
    fun gradeRecall(answer: String, input: String): Boolean {
        fun canon(s: String) = normalize(s.replace("/", " "), strict = true)
        val c = canon(input)
        return recallVariants(answer).any { canon(it) == c }
    }

    /** Convenience for simple MCQ/FILL grading used by the quiz runner. */
    fun isCorrect(q: Question, response: String): Boolean = when (q.type) {
        QuestionType.MCQ -> gradeMcq(q, response)
        QuestionType.FILL -> gradeFill(q, response)
        else -> false // MATCH/REORDER use their dedicated graders
    }
}
