package com.corlang.app

import com.corlang.app.data.Fsrs
import com.corlang.app.data.SrsGrade
import com.corlang.app.data.WordsRepository
import com.corlang.app.data.db.WordReview
import com.corlang.app.ui.screens.SessionStep
import com.corlang.app.ui.screens.StepKind
import com.corlang.app.ui.screens.revisitLabels
import com.corlang.app.ui.screens.revisitSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Revisiting a finished lesson in parts: which sections are offered, which words belong to a
 * lesson, and when a revisit's word pass is allowed to move the SRS schedule.
 *
 * The last of those is the one worth pinning down. A revisit an hour after the lesson is rehearsal,
 * not retrieval after a delay, and letting it grade would let a learner push their intervals out by
 * tapping rather than by remembering.
 */
class LessonRevisitTest {

    private fun step(kind: StepKind, id: String) =
        SessionStep(id = id, kind = kind, title = id)

    // ---------------- sections ----------------

    /** A typical lesson, in the order buildSessionSteps emits. */
    private val LESSON = listOf(
        step(StepKind.INFO, "intro"),
        step(StepKind.WORDS, "words"),
        step(StepKind.LEARN, "activity-0"),
        step(StepKind.EXERCISE, "activity-1"),
        step(StepKind.EXERCISE, "activity-2"),
        step(StepKind.DIALOGUE, "activity-3"),
        step(StepKind.WRAPUP, "wrapup"),
        step(StepKind.REVIEW, "review"),
        step(StepKind.COMPLETE, "complete")
    )

    @Test
    fun `sections are the teaching parts, not the scaffolding around them`() {
        // Indices into the ORIGINAL list: they are what the player is asked to open at, so a
        // filtered-and-renumbered list would jump to the wrong step. The intro, the finish, the
        // new-words step (its words are the flashcard button) and the due-words review are out.
        assertEquals(listOf(2, 3, 4, 5, 6), revisitSections(LESSON))
    }

    @Test
    fun `a lesson with nothing to redo offers no sections`() {
        val steps = listOf(
            step(StepKind.INFO, "intro"),
            step(StepKind.WORDS, "words"),
            step(StepKind.COMPLETE, "complete")
        )
        assertTrue(revisitSections(steps).isEmpty())
    }

    @Test
    fun `a repeated kind is numbered, a single one is not`() {
        val sections = revisitSections(LESSON)
        assertEquals(
            listOf("Learn", "Exercise 1", "Exercise 2", "Dialogue", "Wrap-Up"),
            revisitLabels(LESSON, sections)
        )
    }

    // ---------------- which words are the lesson's ----------------

    @Test
    fun `a lesson owns its own block of the deck`() {
        assertEquals(0 until 10, WordsRepository.lessonDeckRange(1, 10, deckStart = 0, deckSize = 500))
        assertEquals(40 until 50, WordsRepository.lessonDeckRange(5, 10, deckStart = 0, deckSize = 500))
        // The pace is the learner's own dial, and the block follows it.
        assertEquals(80 until 100, WordsRepository.lessonDeckRange(5, 20, deckStart = 0, deckSize = 500))
    }

    @Test
    fun `a placement start clips the lessons it skipped past`() {
        // Placed at deck index 600: lesson 5's block is entirely behind the learner and was never
        // introduced, so there is nothing of it to review.
        assertTrue(WordsRepository.lessonDeckRange(5, 10, deckStart = 600, deckSize = 3440).isEmpty())
        // The lesson the placement lands inside keeps only the part above the start.
        assertEquals(600 until 610, WordsRepository.lessonDeckRange(61, 10, deckStart = 600, deckSize = 3440))
    }

    @Test
    fun `the block never reaches past the end of the deck`() {
        assertEquals(40 until 45, WordsRepository.lessonDeckRange(5, 10, deckStart = 0, deckSize = 45))
        assertTrue(WordsRepository.lessonDeckRange(9, 10, deckStart = 0, deckSize = 45).isEmpty())
        assertTrue(WordsRepository.lessonDeckRange(0, 10, deckStart = 0, deckSize = 45).isEmpty())
    }

    // ---------------- the once-a-day rule ----------------

    private val today = 100L

    /** A word with real memory state, last reviewed [daysAgo] days ago. */
    private fun reviewed(daysAgo: Long) = WordReview(
        langCode = "hr",
        wordId = "w1",
        stability = 6.0,
        difficulty = 5.0,
        lastReviewEpochDay = today - daysAgo,
        dueEpochDay = today,
        reps = 3
    )

    @Test
    fun `a second pass on the same day changes nothing`() {
        assertNull(
            "redoing a lesson an hour later must not reschedule its words",
            WordsRepository.practiceReview(reviewed(0), SrsGrade.GOOD, today)
        )
        assertNull(
            "not even a miss: nothing was tested after a delay",
            WordsRepository.practiceReview(reviewed(0), SrsGrade.AGAIN, today)
        )
    }

    @Test
    fun `a pass on a later day is a real review and spaces the word further out`() {
        val before = reviewed(daysAgo = 3)
        val after = WordsRepository.practiceReview(before, SrsGrade.GOOD, today)
        assertNotNull(after)
        after!!
        assertEquals("it counts as a repetition", before.reps + 1, after.reps)
        assertEquals("reviewed today", today, after.lastReviewEpochDay)
        assertTrue("a right answer must push the next showing further out", after.stability > before.stability)
        assertTrue(after.dueEpochDay > today)
        // Same answer as the ordinary review path: a revisit is not a second scheduler.
        assertEquals(Fsrs.review(before, SrsGrade.GOOD, today), after)
    }

    @Test
    fun `a miss on a later day pulls the word back in`() {
        val before = reviewed(daysAgo = 3)
        val after = WordsRepository.practiceReview(before, SrsGrade.AGAIN, today)!!
        assertTrue(after.stability <= before.stability)
        assertEquals(before.lapses + 1, after.lapses)
    }

    @Test
    fun `a word introduced but never reviewed still grades`() {
        // Placement seeds cards with no history at all; they are due, not already done today.
        val seeded = WordReview(langCode = "hr", wordId = "w2", dueEpochDay = today)
        assertNotNull(WordsRepository.practiceReview(seeded, SrsGrade.GOOD, today))
    }
}
