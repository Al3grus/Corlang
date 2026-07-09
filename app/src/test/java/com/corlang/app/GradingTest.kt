package com.corlang.app

import com.corlang.app.data.model.Pair2
import com.corlang.app.data.model.Question
import com.corlang.app.data.model.QuestionType
import com.corlang.app.ui.screens.Grading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM unit tests for the offline grading logic (no Android dependencies). */
class GradingTest {

    private fun q(
        type: QuestionType,
        answer: String = "",
        accepted: List<String> = emptyList(),
        options: List<String> = emptyList(),
        ordered: List<String> = emptyList(),
        pairs: List<Pair2> = emptyList()
    ) = Question(
        type = type, prompt = "p", difficulty = 1,
        options = options, answer = answer, accepted = accepted,
        pairs = pairs, ordered = ordered, explanation = "e"
    )

    @Test fun normalize_stripsAccentsCaseAndPunctuation() {
        assertEquals("jai vingt ans", Grading.normalize("  J'ai  VINGT ans. "))
        assertEquals("francais", Grading.normalize("Français"))
        assertEquals("ecole", Grading.normalize("école"))
    }

    @Test fun fill_acceptsAccentInsensitiveAndVariants() {
        val question = q(QuestionType.FILL, answer = "allée", accepted = listOf("allee"))
        assertTrue(Grading.gradeFill(question, "allee"))
        assertTrue(Grading.gradeFill(question, "Allée"))
        assertTrue(Grading.gradeFill(question, "  allée  "))
        assertFalse(Grading.gradeFill(question, "allé"))
    }

    @Test fun fill_matchesPrimaryAnswer() {
        val question = q(QuestionType.FILL, answer = "radim")
        assertTrue(Grading.gradeFill(question, "RADIM"))
        assertFalse(Grading.gradeFill(question, "radiš"))
    }

    @Test fun mcq_isCaseAndAccentInsensitive() {
        val question = q(QuestionType.MCQ, answer = "Bonjour", options = listOf("Bonjour", "Merci"))
        assertTrue(Grading.gradeMcq(question, "bonjour"))
        assertFalse(Grading.gradeMcq(question, "Merci"))
    }

    @Test fun reorder_requiresExactOrder() {
        val question = q(
            QuestionType.REORDER,
            options = listOf("Je", "parle", "français"),
            ordered = listOf("Je", "parle", "français")
        )
        assertTrue(Grading.gradeReorder(question, listOf("Je", "parle", "français")))
        assertFalse(Grading.gradeReorder(question, listOf("parle", "Je", "français")))
    }

    @Test fun match_requiresAllPairsCorrect() {
        val question = q(
            QuestionType.MATCH,
            pairs = listOf(Pair2("je", "I"), Pair2("tu", "you"))
        )
        assertTrue(Grading.gradeMatch(question, mapOf("je" to "I", "tu" to "you")))
        assertFalse(Grading.gradeMatch(question, mapOf("je" to "you", "tu" to "I")))
        assertFalse(Grading.gradeMatch(question, mapOf("je" to "I")))
    }

    @Test fun isCorrect_dispatchesByType() {
        assertTrue(Grading.isCorrect(q(QuestionType.MCQ, answer = "a", options = listOf("a", "b")), "a"))
        assertTrue(Grading.isCorrect(q(QuestionType.FILL, answer = "x"), "x"))
        // MATCH/REORDER are not handled by the simple isCorrect helper.
        assertFalse(Grading.isCorrect(q(QuestionType.REORDER, ordered = listOf("a")), "a"))
    }
}
