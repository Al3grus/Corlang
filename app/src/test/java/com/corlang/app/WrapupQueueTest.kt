package com.corlang.app

import com.corlang.app.ui.screens.RECALL_MAX_MISSES
import com.corlang.app.ui.screens.RecallResume
import com.corlang.app.ui.screens.nextRecallQueue
import com.corlang.app.ui.screens.recallResumeFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wrap-up queue.
 *
 * A wrongly answered phrase used to be gone for good: the runner walked its eight items once, in
 * order, and a miss cost you the point and nothing else. That is the opposite of what the step is
 * for — the phrases you could not produce are precisely the ones worth asking again. Now a miss
 * returns at the back of the queue and keeps returning until you get it, with one ceiling: after
 * [RECALL_MAX_MISSES] tries the item is set down, because a typed answer graded on spelling and
 * diacritics has no "eventually you tap the right one" and one impossible item must never hold a
 * lesson hostage.
 */
class WrapupQueueTest {

    @Test
    fun `a cleared item leaves the queue`() {
        assertEquals(listOf(1, 2), nextRecallQueue(listOf(0, 1, 2), correct = true, missesForItem = 0))
    }

    @Test
    fun `a missed item comes back at the END, not as an immediate retry`() {
        // Immediate re-ask would only test copying the answer still on screen.
        assertEquals(listOf(1, 2, 0), nextRecallQueue(listOf(0, 1, 2), correct = false, missesForItem = 1))
    }

    @Test
    fun `the last item missed comes back alone rather than ending the step`() {
        assertEquals(listOf(7), nextRecallQueue(listOf(7), correct = false, missesForItem = 2))
    }

    @Test
    fun `three misses and the item is set down`() {
        assertEquals(
            "a fourth ask must never happen",
            listOf(1, 2),
            nextRecallQueue(listOf(0, 1, 2), correct = false, missesForItem = RECALL_MAX_MISSES)
        )
    }

    @Test
    fun `an empty queue stays empty`() {
        assertEquals(emptyList<Int>(), nextRecallQueue(emptyList(), correct = false, missesForItem = 0))
    }

    @Test
    fun `resume owes every item that is neither cleared nor set down, in order`() {
        val r = RecallResume(cleared = setOf(1, 4), misses = mapOf(0 to 1, 2 to RECALL_MAX_MISSES))
        assertEquals(listOf(0, 3, 5), r.queue(6))
        assertEquals(setOf(2), r.dropped)
        assertTrue(r.missedAny)
    }

    @Test
    fun `an item cleared after two misses is not counted as set down`() {
        // It was answered right on the third try: cleared wins over its miss history.
        val r = RecallResume(cleared = setOf(3), misses = mapOf(3 to RECALL_MAX_MISSES))
        assertEquals(emptySet<Int>(), r.dropped)
        assertEquals(listOf(0, 1, 2), r.queue(4))
    }

    @Test
    fun `a fresh wrap-up owes all of its items`() {
        val r = RecallResume()
        assertEquals(listOf(0, 1, 2, 3), r.queue(4))
        assertFalse(r.missedAny)
    }

    @Test
    fun `checks read back as cleared items and per-item miss counts`() {
        val ids = listOf(
            "wrapup::q0", "wrapup::q5",
            "wrapup::w2#1", "wrapup::w2#2",
            "wrapup::w3#1",
            "words", "review", "wrapup::q"   // other steps and junk are ignored
        )
        val r = recallResumeFrom(ids, "wrapup")
        assertEquals(setOf(0, 5), r.cleared)
        assertEquals(mapOf(2 to 2, 3 to 1), r.misses)
    }

    @Test
    fun `a miss written by an older build counts as one try, not zero`() {
        // Pre-0.86.4 wrote a bare "::w<i>". A learner who upgrades mid-lesson keeps their history.
        val r = recallResumeFrom(listOf("wrapup::w4", "wrapup::w4#2"), "wrapup")
        assertEquals(mapOf(4 to 2), r.misses)
    }

    @Test
    fun `one step never reads another steps checks`() {
        val r = recallResumeFrom(listOf("wrapup::q1", "mistakes::q2", "drill-0::w1#1"), "wrapup")
        assertEquals(setOf(1), r.cleared)
        assertTrue(r.misses.isEmpty())
    }
}
