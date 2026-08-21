package com.corlang.app

import com.corlang.app.reminder.ReminderCopy
import com.corlang.app.reminder.catchUpDelay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * The reminder's two decisions, both pure so they can be tested without a device or WorkManager.
 *
 * Field report 2026-08-21: "the reminder is not firing if I already opened the app that day".
 * It was not the opening as such: `schedule()` runs on every app start to fight WorkManager
 * drift, and it anchors to the NEXT occurrence of the reminder time. Open the app after that
 * time while the (Doze-deferred) run is still pending, and the pending run gets moved to
 * tomorrow. [catchUpDelay] is what hands that day back.
 */
class ReminderTest {

    private fun at(hour: Int, minute: Int) = LocalDateTime.of(2026, 8, 21, hour, minute)

    // ---------- catch-up scheduling ----------

    @Test
    fun `nothing is owed before the reminder time`() {
        assertNull(catchUpDelay(at(9, 0), 19, 0))
        assertNull(catchUpDelay(at(18, 59), 19, 0))
    }

    @Test
    fun `opening the app after the reminder time queues the nudge it just re-anchored away`() {
        // The exact reported sequence: 19:00 slot, app opened at 19:30, lesson not done.
        val delay = catchUpDelay(at(19, 30), 19, 0)
        assertNotNull("a nudge is still owed for today", delay)
        assertEquals(45L, delay!!.toMinutes())
    }

    @Test
    fun `the catch-up never lands past the window, even opened at its edge`() {
        // 21:40 with a 19:00 slot: 45 minutes would cross the 22:00 deadline, so it is clamped.
        val delay = catchUpDelay(at(21, 40), 19, 0)!!
        assertEquals(20L, delay.toMinutes())
        assertTrue(at(21, 40).plus(delay) <= at(22, 0))
    }

    @Test
    fun `a stale evening gets no nudge at all`() {
        // Three hours past the slot the nudge is stale, and the worst version of this feature
        // is one that wakes someone near midnight.
        assertNull(catchUpDelay(at(22, 0), 19, 0))
        assertNull(catchUpDelay(at(23, 55), 19, 0))
    }

    @Test
    fun `a late reminder time still gets its own window rather than a fixed cutoff`() {
        assertNotNull(catchUpDelay(at(23, 10), 23, 0))
    }

    // ---------- copy ----------

    @Test
    fun `an unfinished lesson is asked to continue, not to start`() {
        (0..3).forEach { d ->
            val text = ReminderCopy.body("Croatian", streak = 4, startedToday = true, proverb = "p", dayOfYear = d)
            assertTrue(
                "should ask to continue: $text",
                text.contains("started") || text.contains("left") || text.contains("waiting")
            )
            // Some variants name the streak, others print the count. Both say what is at stake.
            assertTrue(
                "should still put the streak at stake: $text",
                text.contains("streak") || text.contains("4 days")
            )
        }
    }

    @Test
    fun `an untouched day is asked to start`() {
        (0..3).forEach { d ->
            val text = ReminderCopy.body("Croatian", streak = 0, startedToday = false, proverb = "p", dayOfYear = d)
            assertTrue("should not claim it was started: $text", !text.contains("You started"))
        }
    }

    @Test
    fun `the copy rotates rather than repeating every evening`() {
        val week = (0..6).map {
            ReminderCopy.body("Croatian", streak = 9, startedToday = false, proverb = "p", dayOfYear = it)
        }
        assertTrue("a nudge that never varies stops being read", week.toSet().size > 1)
    }

    @Test
    fun `day of year never indexes out of range`() {
        listOf(0, 1, 200, 365, 366).forEach { d ->
            listOf(true, false).forEach { started ->
                listOf(0, 12).forEach { streak ->
                    assertTrue(ReminderCopy.body("Croatian", streak, started, "p", d).isNotEmpty())
                }
            }
        }
    }
}
