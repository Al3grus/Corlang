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
    }
}
