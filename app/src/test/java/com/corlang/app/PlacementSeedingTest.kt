package com.corlang.app

import com.corlang.app.data.WordsRepository.Companion.REVIEW_SEED_LESSONS
import com.corlang.app.data.WordsRepository.Companion.prePlacementRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Placement queues the run-up to the placement point for review.
 *
 * The test is a dozen questions and its result is only as fine-grained as the day anchors
 * authored on them, so it cannot prove a learner knows the hundreds of words it places over.
 * The window is measured in DECK INDEX and anchored at the placement point, which is the whole
 * point: an earlier attempt seeded by CEFR level instead, and because pack levels do not map to
 * contiguous deck ranges it reached PAST the placement point, marking words the learner had
 * never seen as already known so they could never be taught (up to 1886 words in French).
 * These cases pin the arithmetic that makes that impossible.
 */
class PlacementSeedingTest {

    @Test fun `the window is the lessons immediately before the placement`() {
        // Placed at lesson 101: deck index 1000 is the first word still to be taught, so the
        // window is the 600 words before it.
        assertEquals(400 to 1000, prePlacementRange(101, lessons = 60, perLesson = 10))
    }

    @Test fun `the window never reaches past the placement point`() {
        listOf(1, 2, 15, 46, 61, 101, 151, 171, 207, 250).forEach { day ->
            val (from, until) = prePlacementRange(day)
            val deckStart = (day - 1) * 10
            assertEquals("window must end exactly at the placement point", deckStart, until)
            assertTrue("window must not start after it ends", from <= until)
        }
    }

    @Test fun `placing near the start clamps to the beginning of the deck`() {
        assertEquals(0 to 0, prePlacementRange(1))          // nothing skipped, nothing to check
        assertEquals(0 to 140, prePlacementRange(15))       // only 14 lessons exist behind
        assertEquals(0 to 590, prePlacementRange(60))       // still inside the window
    }

    @Test fun `a deep placement queues exactly the window, not everything behind it`() {
        val (from, until) = prePlacementRange(207)
        assertEquals(2060, until)
        assertEquals(2060 - REVIEW_SEED_LESSONS * 10, from)
        assertEquals(REVIEW_SEED_LESSONS * 10, until - from)
    }

    @Test fun `the window size is bounded by the seed constant`() {
        listOf(1, 46, 101, 151, 250).forEach { day ->
            val (from, until) = prePlacementRange(day)
            assertTrue(
                "queued $day: ${until - from} words exceeds the cap",
                until - from <= REVIEW_SEED_LESSONS * 10
            )
        }
    }

    @Test fun `a nonsensical placement day cannot produce a negative window`() {
        assertEquals(0 to 0, prePlacementRange(0))
        assertEquals(0 to 0, prePlacementRange(-5))
    }
}
