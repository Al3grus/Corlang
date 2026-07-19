package com.corlang.app.data

import com.corlang.app.data.db.ProgressDao
import com.corlang.app.data.db.WordReview
import com.corlang.app.data.model.VocabWord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate

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
        val seen = dao.wordReviewsOnce(lang).map { it.wordId }.toSet()
        val words = allWords(lang).subList(from.coerceAtMost(allWords(lang).size),
                                           until.coerceAtMost(allWords(lang).size))
            .filter { it.id !in seen }
        words.forEach {
            // A fresh card due today: the first grading runs the normal first-review path, so a
            // half-remembered word gets a short interval and a solid one a long jump.
            dao.upsertWordReview(
                WordReview(
                    langCode = lang,
                    wordId = it.id,
                    introducedEpochDay = today,
                    dueEpochDay = today
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
         * How many lessons' worth of vocabulary a placement queues for review. 60 lessons is
         * about 600 words: enough to cover the run-up a short test cannot verify, small enough
         * that the daily review limit clears it in a couple of weeks.
         */
        const val REVIEW_SEED_LESSONS = 60

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
