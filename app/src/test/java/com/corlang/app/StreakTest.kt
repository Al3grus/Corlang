package com.corlang.app

import com.corlang.app.data.ProgressRepository.Companion.advanceStreak
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakTest {

    @Test
    fun `same day keeps streak, consecutive day increments`() {
        assertEquals(5 to 0, advanceStreak(gap = 0, streak = 5, freezes = 0))
        assertEquals(6 to 0, advanceStreak(gap = 1, streak = 5, freezes = 0))
        assertEquals(1 to 0, advanceStreak(gap = 0, streak = 0, freezes = 0)) // first ever today
    }

    @Test
    fun `one missed day spends a freeze instead of resetting`() {
        assertEquals(11 to 0, advanceStreak(gap = 2, streak = 10, freezes = 1))
        // No freeze banked → reset.
        assertEquals(1 to 0, advanceStreak(gap = 2, streak = 10, freezes = 0))
        // Two missed days can't be bridged.
        assertEquals(1 to 1, advanceStreak(gap = 3, streak = 10, freezes = 1))
    }

    @Test
    fun `a freeze is earned at every 7th day, capped at 2`() {
        assertEquals(7 to 1, advanceStreak(gap = 1, streak = 6, freezes = 0))
        assertEquals(14 to 2, advanceStreak(gap = 1, streak = 13, freezes = 1))
        assertEquals(21 to 2, advanceStreak(gap = 1, streak = 20, freezes = 2)) // cap
        // Studying twice on day 7 doesn't double-earn.
        assertEquals(7 to 1, advanceStreak(gap = 0, streak = 7, freezes = 1))
    }

    @Test
    fun `freeze spend and earn can combine on the 7th day`() {
        // Day 6 streak, one day missed, freeze bridges to 7 → earns one back.
        assertEquals(7 to 1, advanceStreak(gap = 2, streak = 6, freezes = 1))
    }
}
