package com.corlang.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Insert
    suspend fun insertQuizAttempt(a: QuizAttempt)

    @Query("SELECT * FROM quiz_attempt WHERE langCode = :lang ORDER BY takenAtEpoch DESC")
    fun quizAttempts(lang: String): Flow<List<QuizAttempt>>

    @Query("SELECT MAX(score) FROM quiz_attempt WHERE langCode = :lang AND quizId = :quizId")
    fun bestQuizScore(lang: String, quizId: String): Flow<Int?>

    @Insert
    suspend fun insertFeynmanAttempt(a: FeynmanAttempt)

    @Query("SELECT * FROM feynman_attempt WHERE langCode = :lang ORDER BY doneAtEpoch DESC")
    fun feynmanAttempts(lang: String): Flow<List<FeynmanAttempt>>
}
