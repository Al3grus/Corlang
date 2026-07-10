package com.corlang.app.data

import com.corlang.app.data.db.WordReview

/** Grade the learner gives themselves on one flashcard. */
enum class SrsGrade { AGAIN, GOOD, EASY }

/**
 * Minimal Leitner-style spaced-repetition scheduler. Pure and deterministic so it
 * can be unit-tested; all persistence lives in [WordsRepository].
 *
 * Boxes 0..6 with growing intervals. AGAIN drops the word back to box 1 and keeps
 * it due today (it is re-served within the session); GOOD moves up one box; EASY
 * jumps two. New words (box 0) enter at box 1 on their first GOOD.
 */
object Srs {

    const val MAX_BOX = 6
    const val NEW_WORDS_PER_DAY = 10

    /** Days until the next review for a word that has just landed in [box]. */
    fun intervalDays(box: Int): Long = when (box.coerceIn(0, MAX_BOX)) {
        0 -> 0L
        1 -> 1L
        2 -> 2L
        3 -> 5L
        4 -> 10L
        5 -> 21L
        else -> 45L
    }

    /** Applies a grade and returns the updated review state. */
    fun grade(review: WordReview, grade: SrsGrade, todayEpochDay: Long): WordReview {
        val newBox = when (grade) {
            SrsGrade.AGAIN -> 1
            SrsGrade.GOOD -> (review.box + 1).coerceAtMost(MAX_BOX)
            SrsGrade.EASY -> (review.box + 2).coerceAtMost(MAX_BOX)
        }
        return review.copy(
            box = newBox,
            dueEpochDay = if (grade == SrsGrade.AGAIN) todayEpochDay
                          else todayEpochDay + intervalDays(newBox),
            lapses = if (grade == SrsGrade.AGAIN) review.lapses + 1 else review.lapses
        )
    }
}
