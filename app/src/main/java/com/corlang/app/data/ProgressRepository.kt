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
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Owns all progress mutations. Progress is fully independent per language code, so every
 * method takes the active language. Switching languages in the UI simply changes which rows
 * these flows read/write.
 */
class ProgressRepository(private val dao: ProgressDao) {

    fun progress(lang: String): Flow<LanguageProgress?> = dao.progress(lang)
    fun completedDays(lang: String): Flow<List<Int>> = dao.completedDays(lang)
    fun completionsSince(lang: String, sinceEpochMs: Long): Flow<Int> =
        dao.completionsSince(lang, sinceEpochMs)
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

    // NOTE: streak credit happens ONLY inside completeDay — the streak counts completed lesson
    // days, not partial practice. That keeps the streak, the goal ring, and the reminder telling
    // the same story: done = today's lesson done.

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

        /**
         * The streak as it should read RIGHT NOW, decayed for missed days. The stored streak is
         * only recomputed on the next completion, so without this a broken streak keeps showing
         * its last value (the "still 3 after skipping a day" bug). A banked freeze bridges exactly
         * one missed day, matching [advanceStreak]. [today] and [lastStudiedEpochDay] are epoch days.
         */
        fun displayStreak(streak: Int, lastStudiedEpochDay: Long, freezes: Int, today: Long): Int {
            if (streak <= 0) return 0
            return when (today - lastStudiedEpochDay) {
                in Long.MIN_VALUE..1L -> streak       // studied today or yesterday: still alive
                2L -> if (freezes > 0) streak else 0  // one missed day: a freeze can bridge it
                else -> 0                             // more missed than a freeze can cover
            }
        }

        /**
         * Where the learner sits after completing [completedDay]. Never regresses: replaying an
         * earlier day keeps the current day and level (only a day at/after the frontier advances
         * them). Pulled out of completeDay so it's unit-testable without a database.
         */
        fun advancePosition(
            completedDay: Int, currentDay: Int, totalDays: Int,
            completedLevel: String, currentLevel: String
        ): Pair<Int, String> {
            val advancing = completedDay >= currentDay
            val nextDay = if (advancing) minOf(completedDay + 1, totalDays) else currentDay
            val nextLevel = if (advancing) completedLevel else currentLevel
            return nextDay to nextLevel
        }
    }

    /**
     * Heals a stale currentDay: it should never sit behind the furthest completed day. Legacy data
     * (or completions recorded by older builds) could leave currentDay lagging — e.g. "day 1" with
     * five days done. Only ever bumps forward. Called per language on app start.
     */
    suspend fun reconcileCurrentDay(lang: String) {
        val p = dao.progressOnce(lang) ?: return
        val maxCompleted = dao.completedDays(lang).first().maxOrNull() ?: 0
        if (p.currentDay < maxCompleted + 1) {
            dao.upsertProgress(p.copy(currentDay = maxCompleted + 1))
        }
    }

    /**
     * Marks a study day complete: records the completion, credits the streak (consecutive-day
     * logic with freezes), and advances currentDay if this was the current one — all as ONE
     * atomic transaction, so a crash mid-way can't leave partial state.
     */
    suspend fun completeDay(lang: String, day: Int, totalDays: Int, currentLevel: String) {
        // Idempotent: re-completing an already-completed day (a revisit) must NOT re-credit the
        // streak, count as today's goal, or touch the plan position. The UI hides the button on
        // revisits; this guards the data layer against any other path.
        if (dao.isDayCompleted(lang, day)) return
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()
        val existing = dao.progressOnce(lang) ?: LanguageProgress(langCode = lang)
        val (newStreak, newFreezes) = advanceStreak(
            gap = today - existing.lastStudiedEpochDay,
            streak = existing.streak,
            freezes = existing.streakFreezes
        )
        // Reviewing an EARLIER day must never drag your position backwards (the "stuck back in A0,
        // can't reach A1" bug). advancePosition guards both currentDay and currentLevel.
        val (nextDay, nextLevel) = advancePosition(
            completedDay = day, currentDay = existing.currentDay, totalDays = totalDays,
            completedLevel = currentLevel, currentLevel = existing.currentLevel
        )
        dao.completeDayTxn(
            DayCompletion(langCode = lang, day = day, completedAtEpoch = now),
            existing.copy(
                streak = newStreak,
                lastStudiedEpochDay = today,
                streakFreezes = newFreezes,
                currentDay = nextDay,
                currentLevel = nextLevel
            )
        )
    }

    /** Moves the learner's start point (placement test result). Does not mark days complete. */
    suspend fun setPlacement(lang: String, day: Int, level: String) {
        val existing = dao.progressOnce(lang) ?: LanguageProgress(langCode = lang)
        dao.upsertProgress(existing.copy(currentDay = day, currentLevel = level))
    }

    suspend fun recordQuiz(lang: String, quizId: String, score: Int, total: Int) {
        dao.insertQuizAttempt(
            QuizAttempt(
                langCode = lang, quizId = quizId, score = score,
                total = total, takenAtEpoch = System.currentTimeMillis()
            )
        )
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
    }
}
