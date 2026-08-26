package com.corlang.app.data

import com.corlang.app.data.db.CanDoCheck
import com.corlang.app.data.db.DayCompletion
import com.corlang.app.data.db.DayTaskCheck
import com.corlang.app.data.db.ExamSectionAttempt
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
    /** Completion rows with their timestamps, for the Progress calendar. */
    fun completions(lang: String): Flow<List<com.corlang.app.data.db.DayCompletion>> =
        dao.completions(lang)
    fun completionsSince(lang: String, sinceEpochMs: Long): Flow<Int> =
        dao.completionsSince(lang, sinceEpochMs)
    fun completedDayCount(lang: String): Flow<Int> = dao.completedDayCount(lang)
    fun quizAttempts(lang: String): Flow<List<QuizAttempt>> = dao.quizAttempts(lang)
    fun bestQuizScore(lang: String, quizId: String): Flow<Int?> = dao.bestQuizScore(lang, quizId)

    /**
     * Erases EVERY trace of one language's progress: lessons, streak, word memory, quiz, exam,
     * teach-back and can-do records. Transactional and irreversible; the caller owns the
     * confirmation dialog and the preference cleanup (placement offsets, session snapshots).
     */
    suspend fun resetLanguage(lang: String) = dao.resetLanguage(lang)

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
        /** Bank cap. Also the number of milestones, so a full run fills the bank exactly. */
        const val MAX_FREEZES = 4

        /**
         * Streak lengths that each bank one freeze, once per run. Front-loaded on purpose: the
         * days a habit is most likely to die are days 3 and 7, so that is where the safety net
         * is handed out, not at day 30 when the learner no longer needs it.
         */
        val FREEZE_MILESTONES = listOf(3, 7, 14, 30)

        /**
         * The streak and bank as they stand RIGHT NOW, given the last completed day. This is the
         * ONE piece of streak math: both the display and the next completion run through it, so
         * the number on screen can never disagree with the number that gets written.
         *
         * A missed day burns one banked freeze. Four banked freezes therefore cover four missed
         * days; the fifth ends the run, and a break wipes the bank along with the streak — you
         * rebuild protection as you rebuild the streak, rather than being permanently unprotected
         * after one bad week.
         *
         * Deliberately PURE and idempotent: nothing is persisted while a lapse is in progress, so
         * a lapse decays correctly whether the app is opened every day or not at all. Recomputing
         * it from the same [lastStudiedEpochDay] always gives the same answer.
         *
         * A negative gap (clock set back, westward timezone travel) is not a lapse.
         */
        fun settle(streak: Int, lastStudiedEpochDay: Long, freezes: Int, today: Long): Pair<Int, Int> {
            if (streak <= 0) return 0 to 0
            val missed = today - lastStudiedEpochDay - 1
            return when {
                missed <= 0L -> streak to freezes          // studied today or yesterday
                missed <= freezes -> streak to (freezes - missed).toInt()
                else -> 0 to 0                             // more missed than the bank could cover
            }
        }

        /** The streak as it should read right now, decayed for missed days. */
        fun displayStreak(streak: Int, lastStudiedEpochDay: Long, freezes: Int, today: Long): Int =
            settle(streak, lastStudiedEpochDay, freezes, today).first

        /** The bank as it should read right now, drained by missed days. */
        fun displayFreezes(streak: Int, lastStudiedEpochDay: Long, freezes: Int, today: Long): Int =
            settle(streak, lastStudiedEpochDay, freezes, today).second

        /**
         * Where the streak and bank land when a lesson is completed today. Settles the lapse
         * first (so the freezes a gap consumed are actually spent), then credits the day and
         * pays out any milestone the new streak just crossed.
         */
        fun advanceStreak(
            streak: Int, lastStudiedEpochDay: Long, freezes: Int, today: Long
        ): Pair<Int, Int> {
            val (aliveStreak, aliveFreezes) = settle(streak, lastStudiedEpochDay, freezes, today)
            // Already completed a day today: the streak is banked, nothing more to credit.
            if (aliveStreak > 0 && today <= lastStudiedEpochDay) return aliveStreak to aliveFreezes
            val newStreak = aliveStreak + 1
            // Only a streak that GREW past a milestone pays out, so replaying day 3 of a run
            // cannot mint a second freeze for the same milestone.
            val earned = if (newStreak in FREEZE_MILESTONES) 1 else 0
            return newStreak to minOf(MAX_FREEZES, aliveFreezes + earned)
        }

        /** True when completing today's lesson crosses a milestone and the bank actually grows. */
        fun freezeEarnedBy(newStreak: Int, freezesBefore: Int): Boolean =
            newStreak in FREEZE_MILESTONES && freezesBefore < MAX_FREEZES

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
            streak = existing.streak,
            lastStudiedEpochDay = existing.lastStudiedEpochDay,
            freezes = existing.streakFreezes,
            today = today
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
                // Never move the anchor BACKWARDS: writing a smaller epoch day (clock set
                // back) would make the next real day look like a 2+ day gap.
                lastStudiedEpochDay = maxOf(today, existing.lastStudiedEpochDay),
                streakFreezes = newFreezes,
                // The record only ever grows: a lost run costs momentum, never the trophy.
                longestStreak = maxOf(existing.longestStreak, newStreak),
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

    /**
     * Forget one lesson's step marks so it can be played again from the first step.
     *
     * Deliberately does NOT touch the completed-days table: replaying lesson 9 must not un-tick
     * it on the journey, break the streak, or move the learner backwards. `advancePosition`
     * already refuses to regress, so the only thing standing between a finished lesson and a
     * fresh run is these marks.
     */
    suspend fun resetDayTasks(lang: String, day: Int) = dao.clearDayTaskChecksForDay(lang, day)

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

    // ----- Mistake bank -----

    private val mistakeJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** A wrong answer banks the question (or bumps an existing entry back to due). */
    suspend fun recordMistake(lang: String, day: Int, q: com.corlang.app.data.model.Question) {
        val today = java.time.LocalDate.now().toEpochDay()
        val existing = dao.missedQuestion(lang, q.prompt)
        dao.upsertMissedQuestion(
            existing?.copy(
                timesMissed = existing.timesMissed + 1,
                lastMissedEpochDay = today,
                clearedEpochDay = null
            ) ?: com.corlang.app.data.db.MissedQuestion(
                langCode = lang,
                promptKey = q.prompt,
                questionJson = mistakeJson.encodeToString(
                    com.corlang.app.data.model.Question.serializer(), q
                ),
                day = day,
                timesMissed = 1,
                lastMissedEpochDay = today
            )
        )
    }

    /** A correct answer clears the banked entry, if one exists. */
    suspend fun clearMistake(lang: String, q: com.corlang.app.data.model.Question) {
        val existing = dao.missedQuestion(lang, q.prompt) ?: return
        if (existing.clearedEpochDay != null) return
        dao.upsertMissedQuestion(
            existing.copy(clearedEpochDay = java.time.LocalDate.now().toEpochDay())
        )
    }

    /** Up to [limit] questions missed on EARLIER days, oldest first, decoded and ready to run.
     *  A snapshot that no longer parses (model drift across an update) is dropped silently. */
    suspend fun dueMistakes(lang: String, limit: Int = 3): List<com.corlang.app.data.model.Question> {
        val today = java.time.LocalDate.now().toEpochDay()
        return dao.dueMistakes(lang, today, limit).mapNotNull { row ->
            runCatching {
                mistakeJson.decodeFromString(
                    com.corlang.app.data.model.Question.serializer(), row.questionJson
                )
            }.getOrNull()
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

}
