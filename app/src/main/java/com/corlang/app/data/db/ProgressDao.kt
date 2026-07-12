package com.corlang.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {

    @Query("SELECT * FROM language_progress WHERE langCode = :lang LIMIT 1")
    fun progress(lang: String): Flow<LanguageProgress?>

    @Query("SELECT * FROM language_progress WHERE langCode = :lang LIMIT 1")
    suspend fun progressOnce(lang: String): LanguageProgress?

    @Upsert
    suspend fun upsertProgress(p: LanguageProgress)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletion(c: DayCompletion)

    @Query("SELECT day FROM day_completion WHERE langCode = :lang")
    fun completedDays(lang: String): Flow<List<Int>>

    @Query("SELECT COUNT(DISTINCT day) FROM day_completion WHERE langCode = :lang")
    fun completedDayCount(lang: String): Flow<Int>

    /** Days completed at/after a moment (epoch ms) — powers the "goal done today" ring state. */
    @Query("SELECT COUNT(*) FROM day_completion WHERE langCode = :lang AND completedAtEpoch >= :sinceEpochMs")
    fun completionsSince(lang: String, sinceEpochMs: Long): Flow<Int>

    @Insert
    suspend fun insertQuizAttempt(a: QuizAttempt)

    @Query("SELECT * FROM quiz_attempt WHERE langCode = :lang ORDER BY takenAtEpoch DESC")
    fun quizAttempts(lang: String): Flow<List<QuizAttempt>>

    @Query("SELECT MAX(score) FROM quiz_attempt WHERE langCode = :lang AND quizId = :quizId")
    fun bestQuizScore(lang: String, quizId: String): Flow<Int?>

    // ----- Word reviews (SRS) -----

    @Query("SELECT * FROM word_review WHERE langCode = :lang")
    fun wordReviews(lang: String): Flow<List<WordReview>>

    @Query("SELECT * FROM word_review WHERE langCode = :lang")
    suspend fun wordReviewsOnce(lang: String): List<WordReview>

    @Query("SELECT * FROM word_review WHERE langCode = :lang AND wordId = :wordId LIMIT 1")
    suspend fun wordReviewOnce(lang: String, wordId: String): WordReview?

    @Query("SELECT * FROM word_review WHERE langCode = :lang AND dueEpochDay <= :today")
    suspend fun dueWordReviews(lang: String, today: Long): List<WordReview>

    @Query("SELECT COUNT(*) FROM word_review WHERE langCode = :lang AND introducedEpochDay = :today")
    suspend fun introducedTodayCount(lang: String, today: Long): Int

    @Upsert
    suspend fun upsertWordReview(r: WordReview)

    @Insert
    suspend fun insertFeynmanAttempt(a: FeynmanAttempt)

    @Query("SELECT * FROM feynman_attempt WHERE langCode = :lang ORDER BY doneAtEpoch DESC")
    fun feynmanAttempts(lang: String): Flow<List<FeynmanAttempt>>

    // ----- Mock exam attempts -----

    @Insert
    suspend fun insertExamSectionAttempt(a: ExamSectionAttempt)

    @Query("SELECT * FROM exam_section_attempt WHERE langCode = :lang AND examId = :examId ORDER BY takenAtEpoch DESC")
    fun examAttempts(lang: String, examId: String): Flow<List<ExamSectionAttempt>>

    @Query(
        // Aliases matter: without them the correlated sectionId comparison resolves to the
        // inner table (a tautology) and only the single globally-latest attempt is returned.
        "SELECT * FROM exam_section_attempt AS a WHERE a.langCode = :lang AND a.examId = :examId " +
        "AND a.takenAtEpoch = (SELECT MAX(b.takenAtEpoch) FROM exam_section_attempt AS b " +
        "WHERE b.langCode = :lang AND b.examId = :examId AND b.sectionId = a.sectionId)"
    )
    fun latestExamAttempts(lang: String, examId: String): Flow<List<ExamSectionAttempt>>

    // ----- Plan-day task checklist -----

    @Query("SELECT * FROM day_task_check WHERE langCode = :lang AND day = :day")
    fun dayTaskChecks(lang: String, day: Int): Flow<List<DayTaskCheck>>

    @Upsert
    suspend fun upsertDayTask(c: DayTaskCheck)

    @Query("DELETE FROM day_task_check WHERE langCode = :lang AND day = :day AND itemId = :itemId")
    suspend fun deleteDayTask(lang: String, day: Int, itemId: String)

    // ----- Can-do self-checklist -----

    @Query("SELECT * FROM can_do_check WHERE langCode = :lang AND levelId = :levelId")
    fun canDoChecks(lang: String, levelId: String): Flow<List<CanDoCheck>>

    @Upsert
    suspend fun upsertCanDo(c: CanDoCheck)

    @Query("DELETE FROM can_do_check WHERE langCode = :lang AND levelId = :levelId AND itemId = :itemId")
    suspend fun deleteCanDo(lang: String, levelId: String, itemId: String)

    // ----- Backup / restore (whole database, all languages) -----

    @Query("SELECT * FROM language_progress") suspend fun allProgress(): List<LanguageProgress>
    @Query("SELECT * FROM day_completion") suspend fun allCompletions(): List<DayCompletion>
    @Query("SELECT * FROM quiz_attempt") suspend fun allQuizAttempts(): List<QuizAttempt>
    @Query("SELECT * FROM word_review") suspend fun allWordReviews(): List<WordReview>
    @Query("SELECT * FROM feynman_attempt") suspend fun allFeynmanAttempts(): List<FeynmanAttempt>
    @Query("SELECT * FROM exam_section_attempt") suspend fun allExamAttempts(): List<ExamSectionAttempt>
    @Query("SELECT * FROM can_do_check") suspend fun allCanDoChecks(): List<CanDoCheck>
    @Query("SELECT * FROM day_task_check") suspend fun allDayTaskChecks(): List<DayTaskCheck>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllProgress(x: List<LanguageProgress>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllCompletions(x: List<DayCompletion>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllQuizAttempts(x: List<QuizAttempt>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllWordReviews(x: List<WordReview>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllFeynmanAttempts(x: List<FeynmanAttempt>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllExamAttempts(x: List<ExamSectionAttempt>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllCanDoChecks(x: List<CanDoCheck>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllDayTaskChecks(x: List<DayTaskCheck>)

    @Query("DELETE FROM language_progress") suspend fun clearProgress()
    @Query("DELETE FROM day_completion") suspend fun clearCompletions()
    @Query("DELETE FROM quiz_attempt") suspend fun clearQuizAttempts()
    @Query("DELETE FROM word_review") suspend fun clearWordReviews()
    @Query("DELETE FROM feynman_attempt") suspend fun clearFeynmanAttempts()
    @Query("DELETE FROM exam_section_attempt") suspend fun clearExamAttempts()
    @Query("DELETE FROM can_do_check") suspend fun clearCanDoChecks()
    @Query("DELETE FROM day_task_check") suspend fun clearDayTaskChecks()

    /** Atomic restore: wipes every table and repopulates from a backup. All-or-nothing. */
    @Transaction
    suspend fun replaceAll(
        progress: List<LanguageProgress>,
        completions: List<DayCompletion>,
        quizAttempts: List<QuizAttempt>,
        wordReviews: List<WordReview>,
        feynmanAttempts: List<FeynmanAttempt>,
        examAttempts: List<ExamSectionAttempt>,
        canDoChecks: List<CanDoCheck>,
        dayTaskChecks: List<DayTaskCheck>
    ) {
        clearProgress(); clearCompletions(); clearQuizAttempts(); clearWordReviews()
        clearFeynmanAttempts(); clearExamAttempts(); clearCanDoChecks(); clearDayTaskChecks()
        insertAllProgress(progress); insertAllCompletions(completions)
        insertAllQuizAttempts(quizAttempts); insertAllWordReviews(wordReviews)
        insertAllFeynmanAttempts(feynmanAttempts); insertAllExamAttempts(examAttempts)
        insertAllCanDoChecks(canDoChecks); insertAllDayTaskChecks(dayTaskChecks)
    }
}
