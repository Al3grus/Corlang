package com.corlang.app.data

import com.corlang.app.data.db.ProgressDao
import com.corlang.app.data.db.WordReview
import com.corlang.app.data.model.VocabWord
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** A word queued for today's session, with its persisted SRS state (null = brand new). */
data class SessionCard(
    val word: VocabWord,
    val review: WordReview?
)

/**
 * Builds daily review sessions and persists grading results.
 * Session = every word due today + up to [Srs.NEW_WORDS_PER_DAY] not-yet-seen words
 * (in deck order, so packs introduce themselves front to back).
 */
class WordsRepository(
    private val dao: ProgressDao,
    private val content: ContentRepository
) {

    fun reviews(lang: String): Flow<List<WordReview>> = dao.wordReviews(lang)

    fun allWords(lang: String): List<VocabWord> =
        content.vocab(lang).packs.flatMap { it.words }

    suspend fun buildSession(
        lang: String,
        today: Long = todayEpochDay(),
        newPerDay: Int = Srs.NEW_WORDS_PER_DAY
    ): List<SessionCard> {
        val wordsById = allWords(lang).associateBy { it.id }

        // Due first, oldest box first, so the shakiest words lead the session.
        val due = dao.dueWordReviews(lang, today)
            .sortedWith(compareBy({ it.box }, { it.dueEpochDay }))
            .mapNotNull { r -> wordsById[r.wordId]?.let { SessionCard(it, r) } }

        // Then fresh words, respecting the daily introduction cap.
        val introducedToday = dao.introducedTodayCount(lang, today)
        val seenIds = dao.wordReviewsOnce(lang).map { it.wordId }.toSet()
        val newBudget = (newPerDay - introducedToday).coerceAtLeast(0)
        val fresh = allWords(lang)
            .filter { it.id !in seenIds }
            .take(newBudget)
            .map { SessionCard(it, null) }

        return due + fresh
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
        val updated = Srs.grade(existing, grade, today)
        dao.upsertWordReview(updated)
        return updated
    }

    companion object {
        fun todayEpochDay(): Long = LocalDate.now().toEpochDay()
    }
}
