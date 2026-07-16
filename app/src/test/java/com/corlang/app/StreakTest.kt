package com.corlang.app

import com.corlang.app.data.ProgressRepository.Companion.advancePosition
import com.corlang.app.data.ProgressRepository.Companion.advanceStreak
import com.corlang.app.data.ProgressRepository.Companion.displayStreak
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

    // ----- displayStreak: decays a broken streak for display (the "still 3 after a skip" bug) -----

    @Test
    fun `displayStreak stays alive if studied today or yesterday`() {
        assertEquals(3, displayStreak(streak = 3, lastStudiedEpochDay = 100, freezes = 0, today = 100)) // today
        assertEquals(3, displayStreak(streak = 3, lastStudiedEpochDay = 100, freezes = 0, today = 101)) // yesterday
    }

    @Test
    fun `displayStreak breaks to zero after a missed day with no freeze`() {
        assertEquals(0, displayStreak(streak = 3, lastStudiedEpochDay = 100, freezes = 0, today = 102)) // 1 missed
        assertEquals(0, displayStreak(streak = 3, lastStudiedEpochDay = 100, freezes = 1, today = 103)) // 2 missed
    }

    @Test
    fun `displayStreak a banked freeze bridges exactly one missed day`() {
        assertEquals(3, displayStreak(streak = 3, lastStudiedEpochDay = 100, freezes = 1, today = 102))
    }

    @Test
    fun `displayStreak of a zero streak is zero`() {
        assertEquals(0, displayStreak(streak = 0, lastStudiedEpochDay = 100, freezes = 2, today = 100))
    }

    // ----- advancePosition: currentDay/level never regress when replaying an earlier day -----

    @Test
    fun `advancePosition advances day and level on the frontier`() {
        assertEquals(6 to "A1", advancePosition(completedDay = 5, currentDay = 5, totalDays = 240,
            completedLevel = "A1", currentLevel = "A1"))
    }

    @Test
    fun `advancePosition never regresses when replaying an earlier day`() {
        // On day 15 (A1), replay an A0 day 1 → stays at day 15, level A1.
        assertEquals(15 to "A1", advancePosition(completedDay = 1, currentDay = 15, totalDays = 240,
            completedLevel = "A0", currentLevel = "A1"))
    }

    @Test
    fun `advancePosition clamps at the last day`() {
        assertEquals(240 to "B1", advancePosition(completedDay = 240, currentDay = 240, totalDays = 240,
            completedLevel = "B1", currentLevel = "B1"))
    }
}
