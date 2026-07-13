package com.corlang.app.data

import com.corlang.app.data.db.WordReview
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToLong

/** Grade the learner gives themselves on one flashcard. */
enum class SrsGrade { AGAIN, GOOD, EASY }

/**
 * FSRS (Free Spaced Repetition Scheduler). Each word carries Difficulty and Stability; the next
 * interval is derived so recall probability lands at the requested retention. This makes the
 * schedule adaptive per word — easy words stretch out fast, hard words return quickly — needing
 * ~20-30% fewer reviews than a fixed-interval box system for the same retention.
 *
 * Pure and deterministic (unit-tested); persistence lives in [WordsRepository]. Day-granular,
 * requested retention 0.9, shipped with FSRS's published default parameters (no per-user
 * optimizer — a future enhancement). Our three grades map to FSRS ratings: AGAIN→1, GOOD→3,
 * EASY→4 (the Hard=2 rating is unused; we have no Hard button).
 */
object Fsrs {

    const val NEW_WORDS_PER_DAY = 10

    /** Max due cards the in-lesson review step surfaces (~last two lessons) — overflow waits / Words tab. */
    const val REVIEW_CAP = 20

    /** Stability (days-to-90%) at which a word reads as "learned" / flips to production recall. */
    const val LEARNED_STABILITY = 7.0

    /** Stability at which a word reads as "mastered". */
    const val MASTERED_STABILITY = 21.0

    /**
     * Longest a word can be scheduled out. Uncapped FSRS growth reaches decades after a dozen
     * good reviews — pointless for exam prep; every word gets touched at least yearly.
     */
    const val MAX_INTERVAL_DAYS = 365L

    private const val RETENTION = 0.9
    private const val DECAY = -0.5
    private val FACTOR = 0.9.pow(1.0 / DECAY) - 1.0   // = 19/81 ≈ 0.2346

    // FSRS default parameters (trained on hundreds of millions of real reviews).
    private val W = doubleArrayOf(
        0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046,
        1.54575, 0.1192, 1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315,
        2.9898, 0.51655, 0.6621
    )

    /** FSRS rating 1..4 for our three-button grade. */
    private fun rating(g: SrsGrade): Int = when (g) {
        SrsGrade.AGAIN -> 1
        SrsGrade.GOOD -> 3
        SrsGrade.EASY -> 4
    }

    private fun clampD(d: Double) = d.coerceIn(1.0, 10.0)
    private fun clampS(s: Double) = s.coerceAtLeast(0.01)

    /** Recall probability after [elapsedDays] for a memory with the given [stability]. */
    fun retrievability(elapsedDays: Long, stability: Double): Double {
        if (stability <= 0.0) return 0.0
        val t = elapsedDays.coerceAtLeast(0).toDouble()
        return (1.0 + FACTOR * t / stability).pow(DECAY)
    }

    /** Current recall probability of a stored review as of [todayEpochDay] (0 for never-reviewed). */
    fun retrievabilityOf(r: WordReview, todayEpochDay: Long): Double =
        if (r.reps == 0 || r.stability <= 0.0) 0.0
        else retrievability(todayEpochDay - r.lastReviewEpochDay, r.stability)

    private fun initialStability(g: SrsGrade) = W[rating(g) - 1]

    private fun initialDifficulty(g: SrsGrade): Double =
        clampD(W[4] - exp(W[5] * (rating(g) - 1)) + 1.0)

    private fun nextDifficulty(d: Double, g: SrsGrade): Double {
        val deltaD = -W[6] * (rating(g) - 3)
        val dp = d + deltaD * ((10.0 - d) / 9.0)                 // linear damping toward 10
        return clampD(W[7] * initialDifficulty(SrsGrade.EASY) + (1.0 - W[7]) * dp)  // mean reversion
    }

    private fun stabilityOnSuccess(d: Double, s: Double, r: Double, g: SrsGrade): Double {
        val tD = 11.0 - d
        val tS = s.pow(-W[9])
        val tR = exp(W[10] * (1.0 - r)) - 1.0
        val easy = if (g == SrsGrade.EASY) W[16] else 1.0
        return s * (1.0 + tD * tS * tR * easy * exp(W[8]))
    }

    private fun stabilityOnFail(d: Double, s: Double, r: Double): Double {
        val dF = d.pow(-W[12])
        val sF = (s + 1.0).pow(W[13]) - 1.0
        val rF = exp(W[14] * (1.0 - r))
        return minOf(W[11] * dF * sF * rF, s)   // post-lapse stability never exceeds the old value
    }

    /** Days until the next review for a memory with the given [stability], at retention 0.9. */
    fun intervalDays(stability: Double): Long =
        ((stability / FACTOR) * (RETENTION.pow(1.0 / DECAY) - 1.0)).roundToLong()
            .coerceIn(1, MAX_INTERVAL_DAYS)

    /** Applies a grade and returns the updated FSRS review state. */
    fun review(review: WordReview, grade: SrsGrade, todayEpochDay: Long): WordReview {
        val firstReview = review.reps == 0 || review.stability <= 0.0
        val newD: Double
        val newS: Double
        if (firstReview) {
            newD = initialDifficulty(grade)
            newS = clampS(initialStability(grade))
        } else {
            val r = retrievability(todayEpochDay - review.lastReviewEpochDay, review.stability)
            newD = nextDifficulty(review.difficulty, grade)
            newS = clampS(
                if (grade == SrsGrade.AGAIN) stabilityOnFail(review.difficulty, review.stability, r)
                else stabilityOnSuccess(review.difficulty, review.stability, r, grade)
            )
        }
        return review.copy(
            stability = newS,
            difficulty = newD,
            dueEpochDay = todayEpochDay + intervalDays(newS),
            lastReviewEpochDay = todayEpochDay,
            reps = review.reps + 1,
            lapses = if (grade == SrsGrade.AGAIN) review.lapses + 1 else review.lapses
        )
    }
}

/** A word is "learned" (and flips to EN→HR production recall) once its memory is durable. */
val WordReview.isLearned: Boolean get() = stability >= Fsrs.LEARNED_STABILITY

/** A word is "mastered" once its interval is long. */
val WordReview.isMastered: Boolean get() = stability >= Fsrs.MASTERED_STABILITY
