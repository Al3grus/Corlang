package com.corlang.app

import com.corlang.app.data.model.ExamSectionKind
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

    // ----- CAPLE rule (European Portuguese DEPLE/DIPLE): global average >= 55% (Suficiente) -----

    @Test
    fun `caple passes at 55 percent average with no per-section floor`() {
        // Equal weights: 60% + 50% + 55% + 55% = avg 55% exactly → Suficiente.
        assertTrue(ExamRules.caplePassed(listOf(6 to 10, 5 to 10, 11 to 20, 11 to 20)))
        // A weak section is compensable (unlike DELF): 30% + 80% + 60% + 60% = avg 57.5%.
        assertTrue(ExamRules.caplePassed(listOf(3 to 10, 8 to 10, 6 to 10, 6 to 10)))
    }

    @Test
    fun `caple fails below 55 percent average`() {
        // 50% across the board.
        assertFalse(ExamRules.caplePassed(listOf(5 to 10, 5 to 10, 10 to 20, 10 to 20)))
        assertFalse(ExamRules.caplePassed(emptyList()))
        // Zero-question section counts as 0% and drags the average.
        assertFalse(ExamRules.caplePassed(listOf(9 to 10, 9 to 10, 0 to 0, 0 to 10)))
    }

    // ----- Goethe rule (German): A1/A2 global 60%, B1 modular 60% in EVERY module -----

    @Test
    fun `goethe a1 and a2 pass at 60 percent overall`() {
        // Equal-weight parts, 60 of 100: exactly on the bar passes.
        assertTrue(ExamRules.goetheGlobalPassed(listOf(6 to 10, 6 to 10, 15 to 25, 15 to 25)))
        // A weak part is compensable at A1/A2, since the parts are graded together.
        assertTrue(ExamRules.goetheGlobalPassed(listOf(4 to 10, 9 to 10, 8 to 10, 7 to 10)))
    }

    @Test
    fun `goethe a1 and a2 fail below 60 percent overall`() {
        assertFalse(ExamRules.goetheGlobalPassed(listOf(5 to 10, 6 to 10, 6 to 10, 6 to 10)))
        assertFalse(ExamRules.goetheGlobalPassed(emptyList()))
        // Stricter than CAPLE's 55%: this average is 57.5%, enough for CAPLE, not for Goethe.
        assertTrue(ExamRules.caplePassed(listOf(3 to 10, 8 to 10, 6 to 10, 6 to 10)))
        assertFalse(ExamRules.goetheGlobalPassed(listOf(3 to 10, 8 to 10, 6 to 10, 6 to 10)))
    }

    @Test
    fun `goethe b1 is modular, so one failed module sinks the exam`() {
        // B1 uses the all-sections rule: compensation is NOT allowed, unlike A1/A2.
        val modules = listOf("lesen", "hoeren", "schreiben", "sprechen")
        assertTrue(ExamRules.examPassed(modules, modules.associateWith { true }))
        assertFalse(
            ExamRules.examPassed(modules, modules.associateWith { true } + ("schreiben" to false))
        )
        // A module at 59% fails its own 60% bar, which is what feeds the map above.
        assertFalse(ExamRules.sectionPassed(59, 100, 60))
        assertTrue(ExamRules.sectionPassed(60, 100, 60))
    }

    // ----- DELE rule (Spanish): two grading GROUPS of 50 points, 30 required in each -----
    // Source: the official Guia del examen for A1, A2 and B1 (docs/sources/es-exams.md §2.1).

    private fun dele(reading: Int, writing: Int, listening: Int, speaking: Int, total: Int = 25) =
        listOf(
            Triple(ExamSectionKind.READING, reading, total),
            Triple(ExamSectionKind.WRITING, writing, total),
            Triple(ExamSectionKind.LISTENING, listening, total),
            Triple(ExamSectionKind.SPEAKING, speaking, total)
        )

    @Test
    fun `dele passes at exactly 30 in both groups`() {
        // 15 + 15 in each pair: exactly on the bar in both groups.
        assertTrue(ExamRules.delePassed(dele(reading = 15, writing = 15, listening = 15, speaking = 15)))
        // One point short in Grupo 1 sinks it even though the other group is perfect.
        assertFalse(ExamRules.delePassed(dele(reading = 15, writing = 14, listening = 25, speaking = 25)))
    }

    @Test
    fun `dele compensates INSIDE a group but never between groups`() {
        // A disastrous writing score is carried by a perfect reading score: 25 + 5 = 30. This is
        // the case that proves DELE is not modular.
        assertTrue(ExamRules.delePassed(dele(reading = 25, writing = 5, listening = 20, speaking = 15)))
        // Perfect Grupo 1 and a weak Grupo 2 averages 60% overall and still fails, which is the
        // case that proves DELE is not global either.
        assertFalse(ExamRules.delePassed(dele(reading = 25, writing = 25, listening = 5, speaking = 5)))
    }

    @Test
    fun `dele has no per-section floor, unlike DELF`() {
        // Zero in one prueba is survivable if its partner carries the group. DELF would
        // disqualify this on its 5-per-25 floor; DELE has no such floor.
        assertTrue(ExamRules.delePassed(dele(reading = 25, writing = 5, listening = 25, speaking = 5)))
        assertFalse(ExamRules.delfPassed(listOf(25 to 25, 5 to 25, 25 to 25, 4 to 25)))
    }

    @Test
    fun `dele normalises any per-section scale to 25 points`() {
        // Sections scored out of 30 (the real B1 reading and listening item counts) must weigh
        // the same as sections scored out of 25.
        val mixed = listOf(
            Triple(ExamSectionKind.READING, 18, 30),     // 15.0 / 25
            Triple(ExamSectionKind.WRITING, 15, 25),     // 15.0 / 25  -> Grupo 1 = 30.0
            Triple(ExamSectionKind.LISTENING, 18, 30),   // 15.0 / 25
            Triple(ExamSectionKind.SPEAKING, 15, 25)     // 15.0 / 25  -> Grupo 2 = 30.0
        )
        assertTrue(ExamRules.delePassed(mixed))
    }

    @Test
    fun `dele groups by kind, not by position`() {
        // The same four results in a different array order must give the same verdict, or a
        // harmless reordering of exams.json would silently change whether a learner passed.
        val ordered = dele(reading = 25, writing = 5, listening = 5, speaking = 25)
        assertTrue(ExamRules.delePassed(ordered))
        assertTrue(ExamRules.delePassed(ordered.reversed()))
        assertTrue(ExamRules.delePassed(listOf(ordered[2], ordered[0], ordered[3], ordered[1])))
    }

    @Test
    fun `dele fails loudly on a malformed exam spec`() {
        assertFalse(ExamRules.delePassed(emptyList()))
        // Three pruebas: one group is incomplete, so there is no honest verdict to give.
        assertFalse(
            ExamRules.delePassed(
                listOf(
                    Triple(ExamSectionKind.READING, 25, 25),
                    Triple(ExamSectionKind.WRITING, 25, 25),
                    Triple(ExamSectionKind.LISTENING, 25, 25)
                )
            )
        )
        // A GRAMMAR section does not belong in a DELE mock (the real exam has four pruebas and
        // no grammar paper), and adding one must not be able to rescue a failing group.
        assertFalse(
            ExamRules.delePassed(
                dele(reading = 5, writing = 5, listening = 25, speaking = 25) +
                    Triple(ExamSectionKind.GRAMMAR, 25, 25)
            )
        )
    }

    @Test
    fun `dele treats an unattempted section as zero`() {
        assertFalse(
            ExamRules.delePassed(
                listOf(
                    Triple(ExamSectionKind.READING, 25, 25),
                    Triple(ExamSectionKind.WRITING, 0, 0),      // never attempted
                    Triple(ExamSectionKind.LISTENING, 25, 25),
                    Triple(ExamSectionKind.SPEAKING, 25, 25)
                )
            )
        )
    }
}
