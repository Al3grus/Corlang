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
        DeckOrder.ordered(content.vocab(lang).packs, Fsrs.NEW_WORDS_PER_DAY)

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

    /**
     * The words one lesson introduced, for a revisit's "review this lesson's words" pass.
     *
     * A lesson's own block of the deck is `[(day-1) * perLesson, day * perLesson)` — the slice
     * lesson [day] is the first to unlock — clipped to the placement offset, exactly like
     * [unlockedNewWords] measures its window. Only words the learner has ACTUALLY met are
     * returned (a stored review exists): revisiting an old lesson is practice, and practice must
     * never introduce vocabulary. That also keeps the deck's introduction order intact, which is
     * the SRS's spine.
     *
     * Empty is a normal answer (a lesson entirely below a placement start, or one whose block was
     * never introduced); the caller hides the button rather than offering an empty pass.
     */
    suspend fun lessonWords(
        lang: String,
        day: Int,
        perLesson: Int = Fsrs.NEW_WORDS_PER_DAY
    ): List<SessionCard> {
        val deck = allWords(lang)
        val range = lessonDeckRange(day, perLesson, prefs.wordDeckStart(lang).first(), deck.size)
        if (range.isEmpty()) return emptyList()
        val reviewsById = dao.wordReviewsOnce(lang).associateBy { it.wordId }
        return deck.slice(range).mapNotNull { w -> reviewsById[w.id]?.let { SessionCard(w, it) } }
    }

    /**
     * Persists one grading from a REVISIT practice pass. Returns the updated state, or null when
     * nothing was written (see [practiceReview]).
     */
    suspend fun gradePractice(
        lang: String,
        wordId: String,
        grade: SrsGrade,
        today: Long = todayEpochDay()
    ): WordReview? {
        val existing = dao.wordReviewOnce(lang, wordId) ?: return null
        val updated = practiceReview(existing, grade, today) ?: return null
        dao.upsertWordReview(updated)
        return updated
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
        today: Long = todayEpochDay(),
        /**
         * Hard ceiling on the deck index this may seed, so the run-up to a placement can never
         * hand over vocabulary the learner has not paid for.
         *
         * Without it, placement was a paywall bypass: the test is free and offered at
         * onboarding, the window is 60 lessons wide, and at 10 new words a lesson that is 600
         * deck words dropped straight into Review for anyone who answered well enough to be
         * placed deep. Callers pass `accessibleThroughDay * NEW_WORDS_PER_DAY`, the same
         * expression [unlockedNewWords] uses, so the two agree by construction.
         *
         * Only the UPPER bound moves. Sliding `from` as well would leave gaps: a learner seeded
         * once while free and again after buying would end up with an unseeded band in the
         * middle, since the window's start is measured back from its end.
         */
        maxDeckIndex: Int = Int.MAX_VALUE,
    ): Int {
        val (from, rawUntil) = prePlacementRange(placedDay, lessons)
        val until = rawUntil.coerceAtMost(maxDeckIndex.coerceAtLeast(0))
        if (until <= from) return 0
        val deck = allWords(lang)
        val seen = dao.wordReviewsOnce(lang).map { it.wordId }.toSet()
        val words = deck.subList(from.coerceAtMost(deck.size), until.coerceAtMost(deck.size))
            .filter { it.id !in seen }
        if (words.isEmpty()) return 0

        // Hardest first: the words nearest the placement point sit at the edge of the learner's
        // ability, so they are the most likely gaps and the most worth checking early. Then
        // spread the rest so no single day is buried.
        //
        // The daily share adapts to the learner's OWN review limit and never claims more than
        // half of it. Seeded cards have no review history, so they sort as maximally urgent and
        // fill the lesson's review step first; a fixed 21-day spread put ~29 a day against the
        // default limit of 20, which meant the seeded pile grew faster than the lesson step
        // could drain it and real reviews were crowded out for weeks (session-review finding).
        val cap = prefs.maxReviewsPerDay.first()
        val perDay = (cap / 2).coerceAtLeast(REVIEW_SEED_MIN_PER_DAY)
        /*
         * Built as a list and written ONCE.
         *
         * This used to call upsertWordReview per word, and each Room upsert outside a
         * transaction is its own transaction, which on SQLite means its own disk sync. The
         * window is 60 lessons wide, so placing at lesson 55 with the course owned wrote 540 of
         * them back to back while the confirm button waited: a few seconds on a mid-range phone,
         * with nothing on screen to say why. One transaction for the whole batch instead.
         *
         * REPLACE rather than upsert is safe here and identical in effect: the primary key is
         * (langCode, wordId), a natural key with no autoincrement to churn, and `words` is
         * already filtered to ids with no existing review.
         */
        val rows = words.reversed().mapIndexed { i, w ->
            // A fresh card: the first grading runs the normal first-review path, so a
            // half-remembered word gets a short interval and a solid one a long jump.
            WordReview(
                langCode = lang,
                wordId = w.id,
                introducedEpochDay = today,
                dueEpochDay = today + (i / perDay)
            )
        }
        dao.insertAllWordReviews(rows)
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
         * The deck slice lesson [day] introduces: `[(day-1) * perLesson, day * perLesson)`, never
         * reaching below a placement's [deckStart] or past the end of the deck. Pure, so the rule
         * a revisit reviews by is unit-testable.
         */
        fun lessonDeckRange(day: Int, perLesson: Int, deckStart: Int, deckSize: Int): IntRange {
            if (day < 1 || perLesson < 1 || deckSize <= 0) return IntRange.EMPTY
            val from = maxOf((day - 1) * perLesson, deckStart.coerceAtLeast(0))
                .coerceAtMost(deckSize)
            val until = (day * perLesson).coerceAtMost(deckSize)
            return from until until
        }

        /**
         * One grading of a revisited lesson's words. Returns the new state, or null when the pass
         * must not touch the schedule.
         *
         * A card counts ONCE A DAY. Redoing lesson 9 an hour after finishing it is rehearsal, not
         * retrieval after a delay, and feeding it to FSRS would push the interval out on a memory
         * that was never tested — the learner would be scheduling by how often they tapped, not by
         * what they remember. Come back on a later day and the same pass is a real review: it
         * grades normally, stretching a right answer further out and pulling a wrong one back in.
         */
        fun practiceReview(review: WordReview, grade: SrsGrade, today: Long): WordReview? =
            if (review.reps > 0 && review.lastReviewEpochDay >= today) null
            else Fsrs.review(review, grade, today)

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
         * spreading (half the learner's review limit a day, see [REVIEW_SEED_MIN_PER_DAY])
         * rather than by shrinking the window: coverage and daily load are separate problems
         * and should not be traded against each other.
         */
        const val REVIEW_SEED_LESSONS = 60

        /**
         * Floor for the daily share of seeded placement cards. The real share is half the
         * learner's own review limit (so seeded checks never crowd real reviews out of the
         * lesson step), but never below this, so a tiny limit still drains the seed in
         * reasonable time.
         */
        const val REVIEW_SEED_MIN_PER_DAY = 5

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
