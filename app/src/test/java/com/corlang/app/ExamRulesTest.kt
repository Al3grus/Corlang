package com.corlang.app

import com.corlang.app.ui.screens.ExamRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamRulesTest {

    @Test
    fun `scored section passes at exactly 60 percent`() {
        assertTrue(ExamRules.sectionPassed(score = 6, total = 10, passPercent = 60))
        assertTrue(ExamRules.sectionPassed(score = 5, total = 8, passPercent = 60))   // 62.5%
        assertFalse(ExamRules.sectionPassed(score = 4, total = 8, passPercent = 60))  // 50%
        assertFalse(ExamRules.sectionPassed(score = 0, total = 0, passPercent = 60))  // no questions
    }

    @Test
    fun `pass-fail sections rely on their own verdict`() {
        assertTrue(ExamRules.sectionPassed(score = 0, total = 0, passPercent = null))
    }

    @Test
    fun `exam passes only when every section passed`() {
        val ids = listOf("slusanje", "citanje", "gramatika", "pisanje", "govorenje")
        val allPass = ids.associateWith { true }
        assertTrue(ExamRules.examPassed(ids, allPass))
        assertFalse(ExamRules.examPassed(ids, allPass - "pisanje"))                 // unattempted
        assertFalse(ExamRules.examPassed(ids, allPass + ("gramatika" to false)))    // failed section
        assertFalse(ExamRules.examPassed(emptyList(), emptyMap()))
    }
}
