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

    @Test fun mcq_isExactMatch() {
        // Options are TAPPED, not typed — chosen is always a canonical option string. Exact
        // comparison is required: many MCQs teach diacritics/capitalization, so a distractor
        // that differs only by accent or case ("s" vs "š", "engleski" vs "Engleski") must
        // grade as wrong.
        val question = q(QuestionType.MCQ, answer = "š", options = listOf("š", "s", "č"))
        assertTrue(Grading.gradeMcq(question, "š"))
        assertFalse(Grading.gradeMcq(question, "s"))
        val caseQ = q(QuestionType.MCQ, answer = "engleski", options = listOf("engleski", "Engleski"))
        assertTrue(Grading.gradeMcq(caseQ, "engleski"))
        assertFalse(Grading.gradeMcq(caseQ, "Engleski"))
    }

    @Test fun normalize_trimsEdgeSpaceLeftByPunctuationStrip() {
        // French typography puts a space before ?/!/: — "ce que ?" must equal "ce que".
        assertTrue(Grading.gradeFill(q(QuestionType.FILL, answer = "ce que"), "ce que ?"))
        assertTrue(Grading.gradeRecall("ce que", "ce que ?"))
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

    @Test fun reorderToken_hidesSentencePositionButKeepsDiacritics() {
        // Sentence-case capital and trailing dot would betray the first/last token.
        assertEquals("zovem", Grading.reorderToken("Zovem"))
        assertEquals("ana", Grading.reorderToken("Ana."))
        assertEquals("kavu", Grading.reorderToken("kavu,"))
        assertEquals("živiš", Grading.reorderToken("živiš?"))   // diacritics preserved
        assertEquals("j'ai", Grading.reorderToken("J'ai"))       // internal apostrophe kept
        assertEquals("?", Grading.reorderToken("?"))             // never emit an empty chip
    }

    @Test fun reorder_gradesDisplayNormalizedTokensAgainstOriginalOrdered() {
        // The UI shows reorderToken() forms; grading must accept them against the raw ordered list.
        val question = q(
            QuestionType.REORDER,
            options = listOf("Zovem", "se", "Ana."),
            ordered = listOf("Zovem", "se", "Ana.")
        )
        val tapped = question.ordered.map { Grading.reorderToken(it) }
        assertTrue(Grading.gradeReorder(question, tapped))
        assertFalse(Grading.gradeReorder(question, tapped.reversed()))
    }

    @Test fun recall_sharedTailAlternatives() {
        // "on / ona je" (he / she is): each alternative borrows the shared tail.
        assertTrue(Grading.gradeRecall("on / ona je", "on je"))
        assertTrue(Grading.gradeRecall("on / ona je", "ona je"))
        assertTrue(Grading.gradeRecall("on / ona je", "on/ona je"))      // writing both, compact
        assertTrue(Grading.gradeRecall("on / ona je", "on / ona je"))    // writing both, as shown
        assertFalse(Grading.gradeRecall("on / ona je", "on"))            // drops the verb — incomplete
        assertTrue(Grading.gradeRecall("oni / one su", "oni su"))
        assertTrue(Grading.gradeRecall("oni / one su", "one su"))
        assertTrue(Grading.gradeRecall("oni / one su", "oni/one su"))
    }

    @Test fun recall_completeAlternativesAndPlainAnswers() {
        // No shared tail: either full form is a complete answer.
        assertTrue(Grading.gradeRecall("dobar dan / bok", "bok"))
        assertTrue(Grading.gradeRecall("dobar dan / bok", "dobar dan"))
        assertFalse(Grading.gradeRecall("dobar dan / bok", "dan"))
        // No slash: behaves like the old exact (strict-diacritics) match.
        assertTrue(Grading.gradeRecall("želim", "želim"))
        assertFalse(Grading.gradeRecall("želim", "zelim"))
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

    // ---- Pro-drop pronoun equivalence (hr/pt recall) ----

    @Test fun recall_hr_acceptsPronounForBareVerb() {
        // "I work" licenses ja, and only ja.
        assertTrue(Grading.gradeRecall("radim", "radim", en = "I work", lang = "hr"))
        assertTrue(Grading.gradeRecall("radim", "ja radim", en = "I work", lang = "hr"))
        assertFalse(Grading.gradeRecall("radim", "ti radim", en = "I work", lang = "hr"))
    }

    @Test fun recall_hr_youResolvesByVerbEnding() {
        assertTrue(Grading.gradeRecall("radiš", "ti radiš", en = "you work", lang = "hr"))
        assertFalse(Grading.gradeRecall("radiš", "vi radiš", en = "you work", lang = "hr"))
        assertTrue(Grading.gradeRecall("radite", "vi radite", en = "you work", lang = "hr"))
    }

    @Test fun recall_hr_pronounfulTargetAcceptsBareForm() {
        assertTrue(Grading.gradeRecall("ja radim", "radim", en = "I work", lang = "hr"))
    }

    @Test fun recall_hr_cliticsBlockNaiveVariants() {
        // "ja sam umoran" minus ja would start with the clitic "sam": not offered.
        assertFalse(Grading.gradeRecall("ja sam umoran", "sam umoran", en = "I am tired", lang = "hr"))
        // "šaljem ti poruku" plus ja would need clitic reordering: naive prepend not offered.
        assertFalse(
            Grading.gradeRecall("šaljem ti poruku", "ja šaljem ti poruku",
                en = "I am sending you a message", lang = "hr")
        )
    }

    @Test fun recall_pt_acceptsPronounForBareVerb() {
        assertTrue(Grading.gradeRecall("trabalho", "eu trabalho", en = "I work", lang = "pt"))
        assertFalse(Grading.gradeRecall("trabalho", "ele trabalho", en = "I work", lang = "pt"))
    }

    @Test fun recall_fr_noExpansion() {
        // French is not pro-drop: nothing is added or stripped.
        assertFalse(Grading.gradeRecall("travaille", "je travaille", en = "I work", lang = "fr"))
    }

    @Test fun recall_withoutContext_behavesAsBefore() {
        assertTrue(Grading.gradeRecall("radim", "radim"))
        assertFalse(Grading.gradeRecall("radim", "ja radim"))
    }
}
