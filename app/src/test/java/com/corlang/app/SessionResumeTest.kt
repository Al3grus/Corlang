package com.corlang.app

import com.corlang.app.ui.screens.StepKind
import com.corlang.app.ui.screens.sessionOpensAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which step a lesson opens at.
 *
 * Two field reports live here. A learner on lesson 19 who tapped lesson 9 on the journey was
 * dropped straight onto the congratulations screen with nothing to do but leave, because the
 * resume jump looked for the first step that was not done and on a finished lesson that is the
 * COMPLETE step. And an earlier one landed a learner past a review step that still had cards
 * due. Neither is visible in a build log, and both are one boolean away from returning.
 */
class SessionResumeTest {

    /** A typical lesson: intro, teach, practise, dialogue, wrap up, done. */
    private val KINDS = listOf(
        StepKind.INFO, StepKind.LEARN, StepKind.WORDS, StepKind.REVIEW,
        StepKind.EXERCISE, StepKind.DIALOGUE, StepKind.WRAPUP, StepKind.COMPLETE
    )

    private fun done(vararg trueAt: Int) = KINDS.indices.map { it in trueAt.toSet() }

    @Test
    fun `a finished lesson replays from the first step`() {
        val (index, replay) = sessionOpensAt(KINDS, KINDS.indices.map { true }, hasChecks = true)
        assertEquals("must open at step one, not on the congratulations screen", 0, index)
        assertTrue("its marks must be cleared so every step is live again", replay)
    }

    /**
     * The exact shape of the bug: everything done EXCEPT the COMPLETE step, which is what a
     * lesson looks like the moment after the learner walks away from the last screen.
     */
    @Test
    fun `a lesson done bar its completion screen still replays`() {
        val allButComplete = KINDS.indices.map { KINDS[it] != StepKind.COMPLETE }
        val (index, replay) = sessionOpensAt(KINDS, allButComplete, hasChecks = true)
        assertEquals(0, index)
        assertTrue(replay)
    }

    @Test
    fun `a half-done lesson resumes at the first unfinished step`() {
        // intro, teach and words done; the review step is where they stopped.
        val (index, replay) = sessionOpensAt(KINDS, done(0, 1, 2), hasChecks = true)
        assertEquals(3, index)
        assertFalse("a half-done lesson must keep its progress", replay)
    }

    @Test
    fun `an untouched lesson opens at the start and is not a replay`() {
        val (index, replay) = sessionOpensAt(KINDS, done(), hasChecks = false)
        assertEquals(0, index)
        assertFalse(replay)
    }

    /**
     * No marks written yet means no jump, whatever the flows currently say. A REVIEW step reads
     * as done when nothing is due, so without this guard a fresh lesson with an empty review
     * queue would skip its first steps.
     */
    @Test
    fun `without any marks the session never jumps forward`() {
        val (index, replay) = sessionOpensAt(KINDS, done(3), hasChecks = false)
        assertEquals(0, index)
        assertFalse(replay)
    }

    @Test
    fun `an INFO step is never something to stop on`() {
        // Only the intro is outstanding, everything real is done: that is a finished lesson.
        val allButInfo = KINDS.indices.map { KINDS[it] != StepKind.INFO }
        val (index, replay) = sessionOpensAt(KINDS, allButInfo, hasChecks = true)
        assertEquals(0, index)
        assertTrue(replay)
    }

    @Test
    fun `a lesson with nothing actionable is not treated as a replay`() {
        val onlyFrame = listOf(StepKind.INFO, StepKind.COMPLETE)
        val (index, replay) = sessionOpensAt(onlyFrame, listOf(true, true), hasChecks = true)
        assertEquals(0, index)
        assertFalse("no steps means nothing was ever done, so nothing to wipe", replay)
    }
}
