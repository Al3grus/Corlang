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

    // ----- DELF rule (French): total >= 50/100 AND >= 5/25 per section -----

    @Test
    fun `delf passes at 50 total with every section above the floor`() {
        // Four sections each scored out of 25: 13+12+13+12 = 50, all >= 5.
        assertTrue(ExamRules.delfPassed(listOf(13 to 25, 12 to 25, 13 to 25, 12 to 25)))
    }

    @Test
    fun `delf fails below 50 total even with no floored section`() {
        // 12+12+12+12 = 48 < 50.
        assertFalse(ExamRules.delfPassed(listOf(12 to 25, 12 to 25, 12 to 25, 12 to 25)))
    }

    @Test
    fun `delf fails when any section is below 5-25 even if total is high`() {
        // 24+24+24+4 = 76 total but the last section is below the 5/25 floor.
        assertFalse(ExamRules.delfPassed(listOf(24 to 25, 24 to 25, 24 to 25, 4 to 25)))
    }

    @Test
    fun `delf normalizes sections scored on other scales`() {
        // Section totals differ from 25; normalize each to /25 first.
        // 8/10 -> 20, 6/10 -> 15, 5/10 -> 12.5, 3/10 -> 7.5 ; sum 55 >= 50, all >= 5.
        assertTrue(ExamRules.delfPassed(listOf(8 to 10, 6 to 10, 5 to 10, 3 to 10)))
        // A zero-question section can't clear the floor.
        assertFalse(ExamRules.delfPassed(listOf(8 to 10, 6 to 10, 5 to 10, 0 to 0)))
    }

    @Test
    fun `delf requires four sections`() {
        assertFalse(ExamRules.delfPassed(listOf(20 to 25, 20 to 25, 20 to 25)))
    }
}
