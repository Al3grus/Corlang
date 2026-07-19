package com.corlang.app

import com.corlang.app.data.WordsRepository.Companion.levelBelow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Placement seeds the level BELOW the placed level for review.
 *
 * The placement test is a dozen questions and its result is only as fine-grained as the day
 * anchors authored on them, so it cannot prove a learner knows the hundreds of words it skips
 * past. Rather than reteach those words (patronising) or drop them (silent, permanent gaps),
 * the level immediately below the placement is queued for REVIEW, where anything genuinely
 * forgotten fails its first card and rejoins normal scheduling.
 */
class PlacementSeedingTest {

    @Test fun `each placement queues the level directly below it`() {
        assertEquals("B1", levelBelow("B2"))
        assertEquals("A2", levelBelow("B1"))
        assertEquals("A1", levelBelow("A2"))
        assertEquals("A0", levelBelow("A1"))
    }

    @Test fun `placing at the bottom of the ladder queues nothing`() {
        assertNull(levelBelow("A0"))
    }

    @Test fun `level matching ignores case`() {
        assertEquals("A2", levelBelow("b1"))
    }

    @Test fun `an unrecognised level queues nothing rather than guessing`() {
        assertNull(levelBelow("C2"))
        assertNull(levelBelow(""))
        assertNull(levelBelow("intermediate"))
    }

    /**
     * Only ONE level is queued, never the whole history: a B1 placement checks A2 and trusts
     * A1. Seeding everything below would bury a new learner under a four-figure backlog.
     */
    @Test fun `the rule is one level down, not everything below`() {
        val below = levelBelow("B2")
        assertEquals("B1", below)
        assertEquals("A2", levelBelow(below!!))   // reachable only by placing lower, not at once
    }
}
