package com.corlang.app.data

import com.corlang.app.data.db.ProgressDao
import com.corlang.app.data.db.WordReview
import com.corlang.app.data.model.VocabWord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.ceil

/** A word queued for today's session, with its persisted SRS state (null = brand new). */
data class SessionCard(
    val word: VocabWord,
    val review: WordReview?
)

/**
 * Builds word sessions and persists grading results. New words are gated by lesson progress
 * ([unlockedNewWords]) and introduced through lessons; the Words tab is review-only
 * ([buildReviewSession]). Deck order is the SRS introduction order.
 */
class WordsRepository(
    private val dao: ProgressDao,
    private val content: ContentRepository,
    private val prefs: com.corlang.app.data.prefs.LanguagePrefs
) {

    fun reviews(lang: String): Flow<List<WordReview>> = dao.wordReviews(lang)

    fun allWords(lang: String): List<VocabWord> =
        content.vocab(lang).packs.flatMap { it.words }

    /** The due reviews for today (no new words), oldest box first — the Words tab is review-only. */
    suspend fun buildReviewSession(
        lang: String,
        today: Long = todayEpochDay()
    ): List<SessionCard> {
        val wordsById = allWords(lang).associateBy { it.id }
        return dao.dueWordReviews(lang, today)
            // Most-forgotten first: lowest recall probability leads, so a capped review is urgent.
            .sortedBy { Fsrs.retrievabilityOf(it, today) }
            .mapNotNull { r -> wordsById[r.wordId]?.let { SessionCard(it, r) } }
    }

    /**
     * New words a learner is allowed to introduce, gated by lesson progress: deck words in
     * [deckStart, uptoDay * perLesson) that haven't been introduced yet (deck order = SRS
     * introduction order). You can never run ahead of the lessons you've reached, and the
     * placement test's deckStart keeps a Day-61 learner from being served day-1 words.
     */
    suspend fun unlockedNewWords(
        lang: String,
        uptoDay: Int,
        perLesson: Int = Fsrs.NEW_WORDS_PER_DAY
    ): List<SessionCard> {
        val seenIds = dao.wordReviewsOnce(lang).map { it.wordId }.toSet()
        val deckStart = prefs.wordDeckStart(lang).first()
        return allWords(lang)
            .take((uptoDay * perLesson).coerceAtLeast(0))
            .drop(deckStart)
            .filter { it.id !in seenIds }
            .map { SessionCard(it, null) }
    }

    /** Rebuilds session cards from a persisted list of word ids (gym-proof resume). */
    suspend fun sessionFromIds(lang: String, ids: List<String>): List<SessionCard> {
        val wordsById = allWords(lang).associateBy { it.id }
        val reviewsById = dao.wordReviewsOnce(lang).associateBy { it.wordId }
        return ids.mapNotNull { id ->
            wordsById[id]?.let { SessionCard(it, reviewsById[id]) }
        }
    }

    /**
     * Queues the vocabulary from the lessons immediately BEFORE a placement for review.
     *
     * Placement is a dozen questions and cannot verify the hundreds of words it skips past, so
     * the run-up to the placement point is checked rather than assumed: seeded as due-today
     * review cards, never as new words. A word the learner really knows passes and gets a long
     * interval; one they have lost fails its first card and rejoins normal FSRS scheduling. The
     * daily review limit bounds the backlog.
     *
     * The window is measured in DECK INDEX, anchored at the placement point, which is what makes
     * it safe. Seeding by CEFR level instead looks equivalent but is not: pack levels do not map
     * to contiguous deck ranges (Croatian A0 spans 0..207 while A1 starts at 68), so a level
     * window reached PAST the placement point and marked words the learner had never seen as
     * already known, which permanently stopped them ever being taught. Anchoring at deckStart
     * makes that impossible.
     *
     * Returns how many cards were queued.
     */
    suspend fun seedPrePlacementForReview(
        lang: String,
        placedDay: Int,
        lessons: Int = REVIEW_SEED_LESSONS,
        today: Long = todayEpochDay()
    ): Int {
        val (from, until) = prePlacementRange(placedDay, lessons)
        if (until <= from) return 0
        val deck = allWords(lang)
        val seen = dao.wordReviewsOnce(lang).map { it.wordId }.toSet()
        val words = deck.subList(from.coerceAtMost(deck.size), until.coerceAtMost(deck.size))
            .filter { it.id !in seen }
        if (words.isEmpty()) return 0

        // Hardest first: the words nearest the placement point sit at the edge of the learner's
        // ability, so they are the most likely gaps and the most worth checking early. Then
        // spread the rest across the spread window so no single day is buried.
        val perDay = ceil(words.size.toDouble() / REVIEW_SEED_SPREAD_DAYS).toInt().coerceAtLeast(1)
        words.reversed().forEachIndexed { i, w ->
            // A fresh card: the first grading runs the normal first-review path, so a
            // half-remembered word gets a short interval and a solid one a long jump.
            dao.upsertWordReview(
                WordReview(
                    langCode = lang,
                    wordId = w.id,
                    introducedEpochDay = today,
                    dueEpochDay = today + (i / perDay)
                )
            )
        }
        return words.size
    }

    /** Persists one grading. Returns the updated review state. */
    suspend fun grade(
        lang: String,
        wordId: String,
        grade: SrsGrade,
        today: Long = todayEpochDay()
    ): WordReview {
        val existing = dao.wordReviewOnce(lang, wordId)
            ?: WordReview(langCode = lang, wordId = wordId, introducedEpochDay = today)
        val updated = Fsrs.review(existing, grade, today)
        dao.upsertWordReview(updated)
        return updated
    }

    companion object {
        fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

        /**
         * How many lessons' worth of vocabulary a placement queues for review.
         *
         * Sized from this course's own placement anchors, not a round number. Placement is
         * "the last question answered correctly before the first miss", so one lucky guess on a
         * four-option question promotes the learner by a whole anchor, and the gap between
         * adjacent anchors reaches 60 lessons in Croatian, 51 in Portuguese and 37 in French.
         * A 30-lesson window would leave that single-guess overshoot uncovered, which matters
         * because the placement-testing literature finds short tests misplace learners UPWARD
         * far more often than downward. 60 covers the worst gap in every course.
         *
         * The usual objection to a window this size, a large due-today backlog, is answered by
         * [REVIEW_SEED_SPREAD_DAYS] rather than by shrinking the window: coverage and daily load
         * are separate problems and should not be traded against each other.
         */
        const val REVIEW_SEED_LESSONS = 60

        /**
         * Seeded cards are spread across this many days instead of all falling due at once.
         * Standard spaced-repetition guidance is that a large overdue pile should be drained
         * gradually and never allowed to crowd out new material; staggering keeps the daily
         * share small enough to sit alongside real reviews, and the learner's own review limit
         * caps whatever is left.
         */
        const val REVIEW_SEED_SPREAD_DAYS = 21

        /**
         * The deck slice `[from, until)` a placement at [placedDay] should queue for review:
         * the last [lessons] lessons before the placement point. Never reaches past the
         * placement point, and never below zero. Pure, so the rule is unit-testable.
         */
        fun prePlacementRange(
            placedDay: Int,
            lessons: Int = REVIEW_SEED_LESSONS,
            perLesson: Int = Fsrs.NEW_WORDS_PER_DAY
        ): Pair<Int, Int> {
            val until = ((placedDay - 1) * perLesson).coerceAtLeast(0)
            val from = (until - lessons * perLesson).coerceAtLeast(0)
            return from to until
        }
    }
}
