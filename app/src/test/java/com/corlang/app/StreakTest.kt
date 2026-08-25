package com.corlang.app

import com.corlang.app.data.MonthHistory
import com.corlang.app.data.ProgressRepository.Companion.MAX_FREEZES
import com.corlang.app.data.ProgressRepository.Companion.advanceStreak
import com.corlang.app.data.ProgressRepository.Companion.displayFreezes
import com.corlang.app.data.ProgressRepository.Companion.displayStreak
import com.corlang.app.data.ProgressRepository.Companion.freezeEarnedBy
import com.corlang.app.data.ProgressRepository.Companion.settle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The streak/freeze contract. Freezes are earned at streak 3, 7, 14 and 30 (four per run, which
 * is also the bank cap), one is burned per missed day, and a break wipes streak and bank together.
 */
class StreakTest {

    // ----- advanceStreak: crediting a completed day -----

    @Test
    fun `consecutive days grow the streak`() {
        assertEquals(6 to 0, advanceStreak(streak = 5, lastStudiedEpochDay = 99, freezes = 0, today = 100))
    }

    @Test
    fun `first ever completion starts at one`() {
        assertEquals(1 to 0, advanceStreak(streak = 0, lastStudiedEpochDay = 0, freezes = 0, today = 100))
    }

    @Test
    fun `a second lesson the same day does not re-credit the streak`() {
        assertEquals(5 to 1, advanceStreak(streak = 5, lastStudiedEpochDay = 100, freezes = 1, today = 100))
    }

    @Test
    fun `a clock set back is not a broken streak`() {
        // Stored anchor is AHEAD of today (westward travel): the streak survives untouched.
        assertEquals(9 to 2, advanceStreak(streak = 9, lastStudiedEpochDay = 103, freezes = 2, today = 100))
    }

    // ----- Milestones: 3 / 7 / 14 / 30, one each, four per run -----

    @Test
    fun `each milestone banks exactly one freeze`() {
        assertEquals(3 to 1, advanceStreak(streak = 2, lastStudiedEpochDay = 99, freezes = 0, today = 100))
        assertEquals(7 to 2, advanceStreak(streak = 6, lastStudiedEpochDay = 99, freezes = 1, today = 100))
        assertEquals(14 to 3, advanceStreak(streak = 13, lastStudiedEpochDay = 99, freezes = 2, today = 100))
        assertEquals(30 to 4, advanceStreak(streak = 29, lastStudiedEpochDay = 99, freezes = 3, today = 100))
    }

    @Test
    fun `non-milestone days bank nothing`() {
        for (streak in listOf(1, 4, 8, 15, 31, 99)) {
            val (newStreak, freezes) = advanceStreak(streak, lastStudiedEpochDay = 99, freezes = 2, today = 100)
            assertEquals(streak + 1, newStreak)
            assertEquals("streak $streak should bank nothing", 2, freezes)
        }
    }

    @Test
    fun `the bank never exceeds four`() {
        // Day 30 with a full bank already (only reachable via odd data) must not overflow.
        assertEquals(30 to MAX_FREEZES, advanceStreak(streak = 29, lastStudiedEpochDay = 99, freezes = 4, today = 100))
    }

    @Test
    fun `past day 30 no further freezes are ever earned`() {
        for (streak in 30..80) {
            val (_, freezes) = advanceStreak(streak, lastStudiedEpochDay = 99, freezes = 4, today = 100)
            assertEquals("streak $streak", 4, freezes)
        }
    }

    // ----- The lapse: one freeze burned per missed day -----

    @Test
    fun `a missed day burns one freeze and keeps the streak`() {
        // Last studied day 100, returning on 102 = one missed day (101): the bank pays for it
        // and the returning lesson still counts, so 12 becomes 13 with 3 freezes left.
        assertEquals(13 to 3, advanceStreak(streak = 12, lastStudiedEpochDay = 100, freezes = 4, today = 102))
    }

    @Test
    fun `four banked freezes cover exactly four missed days`() {
        // Streak 31, bank 4, last studied day 100. Missing days 101-104 drains the bank to 0.
        assertEquals(31 to 3, settle(31, lastStudiedEpochDay = 100, freezes = 4, today = 102)) // 1 missed
        assertEquals(31 to 2, settle(31, lastStudiedEpochDay = 100, freezes = 4, today = 103)) // 2 missed
        assertEquals(31 to 1, settle(31, lastStudiedEpochDay = 100, freezes = 4, today = 104)) // 3 missed
        assertEquals(31 to 0, settle(31, lastStudiedEpochDay = 100, freezes = 4, today = 105)) // 4 missed
        assertEquals(0 to 0, settle(31, lastStudiedEpochDay = 100, freezes = 4, today = 106))  // 5th: gone
    }

    @Test
    fun `a break wipes the bank along with the streak`() {
        assertEquals(0 to 0, settle(20, lastStudiedEpochDay = 100, freezes = 2, today = 104))
    }

    @Test
    fun `with an empty bank a single missed day ends the run`() {
        assertEquals(0 to 0, settle(9, lastStudiedEpochDay = 100, freezes = 0, today = 102))
    }

    @Test
    fun `returning after a covered lapse spends the freezes and continues the streak`() {
        // Two missed days, bank of 4: come back and the streak continues at 32 with 2 left.
        assertEquals(32 to 2, advanceStreak(streak = 31, lastStudiedEpochDay = 100, freezes = 4, today = 103))
    }

    @Test
    fun `returning after the streak died starts a fresh run with an empty bank`() {
        assertEquals(1 to 0, advanceStreak(streak = 31, lastStudiedEpochDay = 100, freezes = 4, today = 110))
    }

    @Test
    fun `a covered lapse can still cross a milestone on return`() {
        // Streak 2, one freeze banked (from day 3 of a previous... no: bank 1, one missed day).
        assertEquals(3 to 1, advanceStreak(streak = 2, lastStudiedEpochDay = 100, freezes = 1, today = 102))
    }

    // ----- settle is idempotent: it is computed, never persisted mid-lapse -----

    @Test
    fun `settle gives the same answer however often it is recomputed`() {
        val first = settle(31, lastStudiedEpochDay = 100, freezes = 4, today = 103)
        repeat(5) { assertEquals(first, settle(31, lastStudiedEpochDay = 100, freezes = 4, today = 103)) }
    }

    @Test
    fun `settle of a zero streak is zero`() {
        assertEquals(0 to 0, settle(0, lastStudiedEpochDay = 100, freezes = 4, today = 100))
    }

    // ----- display helpers agree with settle -----

    @Test
    fun `display helpers read the settled values`() {
        assertEquals(31, displayStreak(31, lastStudiedEpochDay = 100, freezes = 4, today = 103))
        assertEquals(2, displayFreezes(31, lastStudiedEpochDay = 100, freezes = 4, today = 103))
        assertEquals(0, displayStreak(31, lastStudiedEpochDay = 100, freezes = 4, today = 106))
        assertEquals(0, displayFreezes(31, lastStudiedEpochDay = 100, freezes = 4, today = 106))
    }

    @Test
    fun `studied today or yesterday reads the full streak and bank`() {
        assertEquals(3, displayStreak(3, lastStudiedEpochDay = 100, freezes = 1, today = 100))
        assertEquals(3, displayStreak(3, lastStudiedEpochDay = 100, freezes = 1, today = 101))
        assertEquals(1, displayFreezes(3, lastStudiedEpochDay = 100, freezes = 1, today = 101))
    }

    // ----- a covered day is a day OFF, never a day done -----

    /**
     * The freeze protects the streak NUMBER and nothing else. It writes no completion, so the
     * month calendar on Progress and the week strip in the streak sheet — both built purely from
     * completed days — must still show the covered day as missed. The streak may be generous;
     * the record of what was actually studied may not.
     */
    @Test
    fun `a frozen day is still missing from the record of completed days`() {
        val today = LocalDate.of(2026, 8, 25)      // Tuesday
        val studied = setOf(today.minusDays(2))    // Sunday: the last lesson actually done
        // Monday was covered by a freeze, so the streak is alive...
        val (streak, freezesLeft) = settle(
            streak = 6,
            lastStudiedEpochDay = today.minusDays(2).toEpochDay(),
            freezes = 2,
            today = today.toEpochDay()
        )
        assertEquals(6, streak)
        assertEquals(1, freezesLeft)
        // ...but Monday is not, and must not become, a completed day.
        val month = MonthHistory.build(today, monthOffset = 0, bankedDates = studied)
        val monday = month.cells.first { it.date == today.minusDays(1) }
        assertEquals(MonthHistory.DayState.Missed, monday.state)
        assertEquals("only the day actually studied is banked", 1, month.banked)
    }

    // ----- the celebration's "+1 freeze earned" line -----

    @Test
    fun `freezeEarnedBy is true only on a milestone below the cap`() {
        assertTrue(freezeEarnedBy(newStreak = 3, freezesBefore = 0))
        assertTrue(freezeEarnedBy(newStreak = 30, freezesBefore = 3))
        assertFalse(freezeEarnedBy(newStreak = 4, freezesBefore = 0))
        assertFalse("at the cap a milestone must not claim a payout",
            freezeEarnedBy(newStreak = 30, freezesBefore = MAX_FREEZES))
    }
}
