package com.frenchai.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per language: the learner's current position and streak. */
@Entity(tableName = "language_progress")
data class LanguageProgress(
    @PrimaryKey val langCode: String,
    val currentLevel: String = "A0",
    val currentDay: Int = 1,
    val streak: Int = 0,
    val lastStudiedEpochDay: Long = 0L  // epoch-day of last completed session, for streak math
)

@Entity(tableName = "day_completion")
data class DayCompletion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val langCode: String,
    val day: Int,
    val completedAtEpoch: Long
)

@Entity(tableName = "quiz_attempt")
data class QuizAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val langCode: String,
    val quizId: String,
    val score: Int,
    val total: Int,
    val takenAtEpoch: Long
)

@Entity(tableName = "feynman_attempt")
data class FeynmanAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val langCode: String,
    val conceptId: String,
    val selfScore: Int,   // rubric points the learner marked as covered
    val total: Int,
    val doneAtEpoch: Long
)
