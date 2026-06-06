package com.frenchai.app.data

import com.frenchai.app.data.db.DayCompletion
import com.frenchai.app.data.db.FeynmanAttempt
import com.frenchai.app.data.db.LanguageProgress
import com.frenchai.app.data.db.ProgressDao
import com.frenchai.app.data.db.QuizAttempt
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Owns all progress mutations. Progress is fully independent per language code, so every
 * method takes the active language. Switching languages in the UI simply changes which rows
 * these flows read/write.
 */
class ProgressRepository(private val dao: ProgressDao) {

    fun progress(lang: String): Flow<LanguageProgress?> = dao.progress(lang)
    fun completedDays(lang: String): Flow<List<Int>> = dao.completedDays(lang)
    fun completedDayCount(lang: String): Flow<Int> = dao.completedDayCount(lang)
    fun quizAttempts(lang: String): Flow<List<QuizAttempt>> = dao.quizAttempts(lang)
    fun bestQuizScore(lang: String, quizId: String): Flow<Int?> = dao.bestQuizScore(lang, quizId)
    fun feynmanAttempts(lang: String): Flow<List<FeynmanAttempt>> = dao.feynmanAttempts(lang)

    /** Ensures a progress row exists for a language (called on first open of that language). */
    suspend fun ensure(lang: String) {
        if (dao.progressOnce(lang) == null) {
            dao.upsertProgress(LanguageProgress(langCode = lang))
        }
    }

    /**
     * Marks a study day complete: records the completion, advances currentDay if this was the
     * current one, and updates the streak (consecutive calendar days).
     */
    suspend fun completeDay(lang: String, day: Int, totalDays: Int, currentLevel: String) {
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()
        dao.insertCompletion(DayCompletion(langCode = lang, day = day, completedAtEpoch = now))

        val existing = dao.progressOnce(lang) ?: LanguageProgress(langCode = lang)
        val newStreak = when (today - existing.lastStudiedEpochDay) {
            0L -> maxOf(existing.streak, 1)   // already studied today
            1L -> existing.streak + 1          // consecutive day
            else -> 1                          // streak reset / first ever
        }
        val nextDay = if (day >= existing.currentDay) minOf(day + 1, totalDays) else existing.currentDay
        dao.upsertProgress(
            existing.copy(
                currentDay = nextDay,
                currentLevel = currentLevel,
                streak = newStreak,
                lastStudiedEpochDay = today
            )
        )
    }

    suspend fun recordQuiz(lang: String, quizId: String, score: Int, total: Int) {
        dao.insertQuizAttempt(
            QuizAttempt(
                langCode = lang, quizId = quizId, score = score,
                total = total, takenAtEpoch = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordFeynman(lang: String, conceptId: String, selfScore: Int, total: Int) {
        dao.insertFeynmanAttempt(
            FeynmanAttempt(
                langCode = lang, conceptId = conceptId, selfScore = selfScore,
                total = total, doneAtEpoch = System.currentTimeMillis()
            )
        )
    }
}
