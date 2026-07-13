package com.corlang.app

import com.corlang.app.data.Fsrs
import com.corlang.app.data.SrsGrade
import com.corlang.app.data.db.WordReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsTest {

    private val today = 20_000L
    private fun fresh() = WordReview(langCode = "hr", wordId = "kava")

    @Test
    fun `new word GOOD schedules days out, EASY schedules further`() {
        val good = Fsrs.review(fresh(), SrsGrade.GOOD, today)
        val easy = Fsrs.review(fresh(), SrsGrade.EASY, today)
        assertTrue("GOOD interval should be at least a day", good.dueEpochDay > today)
        assertTrue("EASY should schedule further out than GOOD", easy.dueEpochDay > good.dueEpochDay)
        assertEquals(1, good.reps)
        assertEquals(0, good.lapses)
        assertEquals(today, good.lastReviewEpochDay)
        assertTrue("stability positive", good.stability > 0.0)
        assertTrue("difficulty in range", good.difficulty in 1.0..10.0)
    }

    @Test
    fun `AGAIN lowers stability, sharply shortens the interval, and counts a lapse`() {
        // Establish a well-learned card first, then fail it.
        val learned = Fsrs.review(Fsrs.review(fresh(), SrsGrade.GOOD, today), SrsGrade.GOOD, today + 5)
        val failedOn = learned.dueEpochDay
        val lapsed = Fsrs.review(learned, SrsGrade.AGAIN, failedOn)
        val preLapseInterval = learned.dueEpochDay - learned.lastReviewEpochDay
        val postLapseInterval = lapsed.dueEpochDay - failedOn
        assertEquals(learned.lapses + 1, lapsed.lapses)
        assertTrue("stability must drop on a lapse", lapsed.stability < learned.stability)
        assertTrue("a lapse shortens the interval sharply", postLapseInterval < preLapseInterval)
        assertTrue("failed card returns soon (a few days)", postLapseInterval in 1..7)
    }

    @Test
    fun `repeated GOOD grows stability monotonically`() {
        var r = Fsrs.review(fresh(), SrsGrade.GOOD, today)
        var day = r.dueEpochDay
        var prev = r.stability
        repeat(5) {
            r = Fsrs.review(r, SrsGrade.GOOD, day)
            assertTrue("stability should grow on a successful, spaced review", r.stability > prev)
            prev = r.stability
            day = r.dueEpochDay
        }
    }

    @Test
    fun `retrievability decays from 1 as time passes and hits ~0_9 at one stability-worth of days`() {
        val s = 10.0
        assertEquals(1.0, Fsrs.retrievability(0, s), 1e-9)
        val rHalf = Fsrs.retrievability(5, s)
        val rFull = Fsrs.retrievability(10, s)
        assertTrue("recall decreases with elapsed time", rFull < rHalf && rHalf < 1.0)
        // Stability is defined as the time for recall to fall to 0.9.
        assertEquals(0.9, Fsrs.retrievability(10, s), 0.02)
    }

    @Test
    fun `interval grows with stability and is at least one day`() {
        assertTrue(Fsrs.intervalDays(0.1) >= 1)
        assertTrue(Fsrs.intervalDays(20.0) > Fsrs.intervalDays(5.0))
    }

    @Test
    fun `long GOOD streak grows intervals into months, capped at a year`() {
        var r = Fsrs.review(fresh(), SrsGrade.GOOD, today)
        var day = r.dueEpochDay
        var prevInterval = day - today
        repeat(10) {
            r = Fsrs.review(r, SrsGrade.GOOD, day)
            val interval = r.dueEpochDay - day
            assertTrue("intervals never shrink on GOOD (got $interval after $prevInterval)", interval >= prevInterval)
            assertTrue("never scheduled past the yearly cap, was $interval", interval <= Fsrs.MAX_INTERVAL_DAYS)
            assertTrue("difficulty stays in 1..10", r.difficulty in 1.0..10.0)
            prevInterval = interval
            day = r.dueEpochDay
        }
        assertTrue("a long streak should reach months, was $prevInterval", prevInterval >= 60)
        assertEquals("and plateau at the yearly cap", Fsrs.MAX_INTERVAL_DAYS, prevInterval)
    }

    @Test
    fun `lapse mid-streak recovers gradually with sane intervals`() {
        var r = Fsrs.review(fresh(), SrsGrade.GOOD, today)
        var day = r.dueEpochDay
        repeat(4) { r = Fsrs.review(r, SrsGrade.GOOD, day); day = r.dueEpochDay }
        val beforeLapse = r.stability
        r = Fsrs.review(r, SrsGrade.AGAIN, day)
        assertTrue("lapse must reduce stability", r.stability < beforeLapse)
        assertTrue("lapsed card returns, never due in the past", r.dueEpochDay > day)
        // Relearning: GOODs after the lapse rebuild stability monotonically, no dead intervals.
        var prev = r.stability
        repeat(3) {
            day = r.dueEpochDay
            r = Fsrs.review(r, SrsGrade.GOOD, day)
            assertTrue("relearning grows stability", r.stability > prev)
            assertTrue("interval at least a day", r.dueEpochDay - day >= 1)
            prev = r.stability
        }
    }

    @Test
    fun `hammering AGAIN repeatedly never underflows or gets stuck`() {
        var r = fresh()
        var day = today
        repeat(8) {
            r = Fsrs.review(r, SrsGrade.AGAIN, day)
            assertTrue("stability stays positive", r.stability > 0.0)
            assertTrue("difficulty clamped", r.difficulty in 1.0..10.0)
            assertTrue("due strictly after review day", r.dueEpochDay > day)
            day = r.dueEpochDay
        }
        assertEquals(8, r.lapses)
        assertEquals(8, r.reps)
    }
}
