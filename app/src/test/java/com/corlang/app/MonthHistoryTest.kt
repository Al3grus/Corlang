package com.corlang.app

import com.corlang.app.data.MonthHistory
import com.corlang.app.data.MonthHistory.DayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MonthHistoryTest {

    private val today = LocalDate.of(2026, 8, 6)        // a Thursday

    @Test fun `the grid is padded so the first lands on its weekday`() {
        // 1 August 2026 is a Saturday: Monday-first means five leading blanks.
        val m = MonthHistory.build(today, 0, emptySet())
        assertEquals(5, m.cells.takeWhile { it.state == DayState.Blank }.size)
        assertEquals(5 + 31, m.cells.size)
        assertEquals(1, m.cells[5].day)
    }

    @Test fun `today is pending, and counts on neither side`() {
        val m = MonthHistory.build(today, 0, emptySet())
        val cell = m.cells.first { it.day == 6 }
        assertEquals(DayState.Today, cell.state)
        // Days 1..5 are over and unbanked; today is not among them.
        assertEquals(5, m.settled)
        assertEquals(0, m.banked)
    }

    @Test fun `banking today moves it into both sides of the count`() {
        val m = MonthHistory.build(today, 0, setOf(today))
        // Still drawn as Today: the cell state is about the date, not the tally.
        assertEquals(DayState.Today, m.cells.first { it.day == 6 }.state)
        val withHistory = MonthHistory.build(today.plusDays(1), 0, setOf(today))
        assertEquals(DayState.Banked, withHistory.cells.first { it.day == 6 }.state)
        assertEquals(1, withHistory.banked)
    }

    @Test fun `the denominator is settled days, not the length of the month`() {
        val m = MonthHistory.build(today, 0, setOf(LocalDate.of(2026, 8, 3)))
        assertEquals("a month in progress must not read 'of 31'", 5, m.settled)
        assertEquals(1, m.banked)
        assertTrue(m.settled < 31)
    }

    @Test fun `future days are future, never missed`() {
        val m = MonthHistory.build(today, 0, emptySet())
        assertTrue(m.cells.filter { (it.day ?: 0) > 6 }.all { it.state == DayState.Future })
    }

    @Test fun `a past month is fully settled`() {
        val m = MonthHistory.build(today, -1, setOf(LocalDate.of(2026, 7, 4)))
        assertEquals(31, m.settled)     // July
        assertEquals(1, m.banked)
        assertTrue(m.cells.none { it.state == DayState.Today || it.state == DayState.Future })
    }

    @Test fun `stepping back stops at the first month with history`() {
        assertEquals(0, MonthHistory.earliestOffset(today, emptySet()))
        assertEquals(-1, MonthHistory.earliestOffset(today, setOf(LocalDate.of(2026, 7, 30))))
        assertEquals(-8, MonthHistory.earliestOffset(today, setOf(LocalDate.of(2025, 12, 1))))
    }

    @Test fun `history in the future cannot pull the range forward`() {
        assertEquals(0, MonthHistory.earliestOffset(today, setOf(LocalDate.of(2027, 1, 1))))
    }
}
