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
     * Queues one CEFR level's vocabulary for REVIEW after a placement test.
     *
     * Placement is a dozen questions and cannot verify the thousand-odd words it skips past, so
     * a learner placed into B1 is assumed to know A2 rather than proven to. These words are
     * therefore seeded as due-today review cards, never as new words: the learner is checked on
     * them instead of being taught them, anything they have actually forgotten fails its first
     * card and re-enters normal FSRS scheduling, and the daily review limit keeps the resulting
     * backlog bounded. Words already introduced are left untouched.
     *
     * Returns how many cards were queued, so the caller can tell the learner.
     */
    suspend fun seedLevelForReview(
        lang: String,
        level: String,
        today: Long = todayEpochDay()
    ): Int {
        val seen = dao.wordReviewsOnce(lang).map { it.wordId }.toSet()
        val words = content.vocab(lang).packs
            .filter { it.level.equals(level, ignoreCase = true) }
            .flatMap { it.words }
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

        /** The CEFR ladder, low to high. A0 is an optional onramp some courses provide. */
        private val LADDER = listOf("A0", "A1", "A2", "B1", "B2", "C1")

        /**
         * The CEFR level immediately below [level], or null at the bottom of the ladder or for
         * an unrecognised level. Pure, so the placement seeding rule is unit-testable.
         */
        fun levelBelow(level: String): String? {
            val i = LADDER.indexOfFirst { it.equals(level, ignoreCase = true) }
            return if (i > 0) LADDER[i - 1] else null
        }
    }
}
