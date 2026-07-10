package com.corlang.app

import com.corlang.app.data.Srs
import com.corlang.app.data.SrsGrade
import com.corlang.app.data.db.WordReview
import org.junit.Assert.assertEquals
import org.junit.Test

class SrsTest {

    private val today = 20_000L
    private fun fresh() = WordReview(langCode = "hr", wordId = "kava", box = 0, dueEpochDay = 0L)

    @Test
    fun `new word graded GOOD lands in box 1 due tomorrow`() {
        val r = Srs.grade(fresh(), SrsGrade.GOOD, today)
        assertEquals(1, r.box)
        assertEquals(today + 1, r.dueEpochDay)
        assertEquals(0, r.lapses)
    }

    @Test
    fun `EASY jumps two boxes`() {
        val r = Srs.grade(fresh().copy(box = 2), SrsGrade.EASY, today)
        assertEquals(4, r.box)
        assertEquals(today + Srs.intervalDays(4), r.dueEpochDay)
    }

    @Test
    fun `AGAIN drops to box 1, stays due today, counts a lapse`() {
        val r = Srs.grade(fresh().copy(box = 5, lapses = 1), SrsGrade.AGAIN, today)
        assertEquals(1, r.box)
        assertEquals(today, r.dueEpochDay)
        assertEquals(2, r.lapses)
    }

    @Test
    fun `box never exceeds MAX_BOX and intervals grow monotonically`() {
        val r = Srs.grade(fresh().copy(box = Srs.MAX_BOX), SrsGrade.EASY, today)
        assertEquals(Srs.MAX_BOX, r.box)
        for (b in 1 until Srs.MAX_BOX) {
            assert(Srs.intervalDays(b) < Srs.intervalDays(b + 1))
        }
    }
}
