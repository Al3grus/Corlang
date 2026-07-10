package com.corlang.app.data

import com.corlang.app.data.db.CanDoCheck
import com.corlang.app.data.db.DayCompletion
import com.corlang.app.data.db.DayTaskCheck
import com.corlang.app.data.db.ExamSectionAttempt
import com.corlang.app.data.db.FeynmanAttempt
import com.corlang.app.data.db.LanguageProgress
import com.corlang.app.data.db.ProgressDao
import com.corlang.app.data.db.QuizAttempt
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
     * Credits today as a study day: bumps the streak if this is the first activity today
     * (consecutive-calendar-day logic) and stamps lastStudiedEpochDay. Any real practice —
     * plan day, quiz, teach-back, or a words session — counts toward the daily streak.
     */
    suspend fun recordStudyActivity(lang: String) {
        val today = LocalDate.now().toEpochDay()
        val existing = dao.progressOnce(lang) ?: LanguageProgress(langCode = lang)
        val (newStreak, newFreezes) = advanceStreak(
            gap = today - existing.lastStudiedEpochDay,
            streak = existing.streak,
            freezes = existing.streakFreezes
        )
        dao.upsertProgress(
            existing.copy(streak = newStreak, lastStudiedEpochDay = today, streakFreezes = newFreezes)
        )
    }

    companion object {
        const val MAX_FREEZES = 2
        const val FREEZE_EVERY_DAYS = 7

        /**
         * Pure streak/freeze math. gap = days since last study. A banked freeze bridges exactly
         * one missed day; a freeze is earned at every 7th consecutive day (banked, max 2).
         */
        fun advanceStreak(gap: Long, streak: Int, freezes: Int): Pair<Int, Int> {
            var f = freezes
            val newStreak = when {
                gap == 0L -> maxOf(streak, 1)                  // already studied today
                gap == 1L -> streak + 1                         // consecutive day
                gap == 2L && f > 0 && streak > 0 -> {           // one missed day: spend a freeze
                    f--
                    streak + 1
                }
                else -> 1                                       // streak reset / first ever
            }
            if (newStreak > streak && newStreak % FREEZE_EVERY_DAYS == 0) {
                f = minOf(MAX_FREEZES, f + 1)
            }
            return newStreak to f
        }
    }

    /**
     * Marks a study day complete: records the completion, advances currentDay if this was the
     * current one, and credits the streak.
     */
    suspend fun completeDay(lang: String, day: Int, totalDays: Int, currentLevel: String) {
        val now = System.currentTimeMillis()
        dao.insertCompletion(DayCompletion(langCode = lang, day = day, completedAtEpoch = now))
        recordStudyActivity(lang)

        val existing = dao.progressOnce(lang) ?: LanguageProgress(langCode = lang)
        val nextDay = if (day >= existing.currentDay) minOf(day + 1, totalDays) else existing.currentDay
        dao.upsertProgress(existing.copy(currentDay = nextDay, currentLevel = currentLevel))
    }

    suspend fun recordQuiz(lang: String, quizId: String, score: Int, total: Int) {
        dao.insertQuizAttempt(
            QuizAttempt(
                langCode = lang, quizId = quizId, score = score,
                total = total, takenAtEpoch = System.currentTimeMillis()
            )
        )
        recordStudyActivity(lang)
    }

    // ----- Mock exam -----

    fun examAttempts(lang: String, examId: String): Flow<List<ExamSectionAttempt>> =
        dao.examAttempts(lang, examId)

    fun latestExamAttempts(lang: String, examId: String): Flow<List<ExamSectionAttempt>> =
        dao.latestExamAttempts(lang, examId)

    suspend fun recordExamSection(
        lang: String, examId: String, sectionId: String,
        score: Int, total: Int, passed: Boolean
    ) {
        dao.insertExamSectionAttempt(
            ExamSectionAttempt(
                langCode = lang, examId = examId, sectionId = sectionId,
                score = score, total = total, passed = passed,
                takenAtEpoch = System.currentTimeMillis()
            )
        )
        recordStudyActivity(lang)
    }

    // ----- Plan-day task checklist -----

    fun dayTaskChecks(lang: String, day: Int): Flow<List<DayTaskCheck>> =
        dao.dayTaskChecks(lang, day)

    suspend fun setDayTask(lang: String, day: Int, itemId: String, checked: Boolean) {
        if (checked) {
            dao.upsertDayTask(
                DayTaskCheck(
                    langCode = lang, day = day, itemId = itemId,
                    checkedAtEpoch = System.currentTimeMillis()
                )
            )
        } else {
            dao.deleteDayTask(lang, day, itemId)
        }
    }

    // ----- Can-do checklist -----

    fun canDoChecks(lang: String, levelId: String): Flow<List<CanDoCheck>> =
        dao.canDoChecks(lang, levelId)

    suspend fun setCanDo(lang: String, levelId: String, itemId: String, checked: Boolean) {
        if (checked) {
            dao.upsertCanDo(
                CanDoCheck(
                    langCode = lang, levelId = levelId, itemId = itemId,
                    checkedAtEpoch = System.currentTimeMillis()
                )
            )
        } else {
            dao.deleteCanDo(lang, levelId, itemId)
        }
    }

    suspend fun recordFeynman(lang: String, conceptId: String, selfScore: Int, total: Int) {
        dao.insertFeynmanAttempt(
            FeynmanAttempt(
                langCode = lang, conceptId = conceptId, selfScore = selfScore,
                total = total, doneAtEpoch = System.currentTimeMillis()
            )
        )
        recordStudyActivity(lang)
    }
}
