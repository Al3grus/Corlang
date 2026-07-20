package com.corlang.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** One row per language: the learner's current position and streak. */
@Serializable
@Entity(tableName = "language_progress")
data class LanguageProgress(
    @PrimaryKey val langCode: String,
    val currentLevel: String = "A0",
    val currentDay: Int = 1,
    val streak: Int = 0,
    val lastStudiedEpochDay: Long = 0L, // epoch-day a lesson day was last COMPLETED, for streak math
    /** Banked streak freezes (earned every 7 consecutive days, max 2, auto-spent on a missed day). */
    val streakFreezes: Int = 0
)

@Serializable
// Unique (langCode, day): a day can only ever be completed once. Without this the IGNORE
// strategy on insertCompletion had nothing to conflict on (the PK is autoincrement), so a
// racing double-insert produced duplicate rows that inflated completionsSince (the goal ring).
@Entity(
    tableName = "day_completion",
    indices = [androidx.room.Index(value = ["langCode", "day"], unique = true)]
)
data class DayCompletion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val langCode: String,
    val day: Int,
    val completedAtEpoch: Long
)

@Serializable
@Entity(tableName = "quiz_attempt")
data class QuizAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val langCode: String,
    val quizId: String,
    val score: Int,
    val total: Int,
    val takenAtEpoch: Long
)

/**
 * Spaced-repetition state for one vocabulary word (Leitner boxes).
 * box 0 = brand new; each correct answer moves the word up a box and pushes
 * its due date further out. A row exists only once a word has been introduced.
 */
@Serializable
@Entity(tableName = "word_review", primaryKeys = ["langCode", "wordId"])
data class WordReview(
    val langCode: String,
    val wordId: String,
    val box: Int = 0,                     // legacy Leitner box; kept for migration backfill, unused by FSRS
    val dueEpochDay: Long = 0L,
    val introducedEpochDay: Long = 0L,    // day first introduced (lesson gating)
    val lapses: Int = 0,
    // FSRS-6 memory state. stability = days for recall probability to fall to 90%;
    // difficulty in 1..10; lastReviewEpochDay drives the retrievability decay.
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val lastReviewEpochDay: Long = 0L,
    val reps: Int = 0
)

/**
 * The mistake bank: an exercise question answered wrong, kept until answered right. The SRS
 * recycles WORDS, but a failed exercise was previously never seen again, which discards the
 * single strongest learning signal a lesson produces. Keyed on the prompt text, which the
 * duplicate-prompt gate makes unique per course and which survives day renumbering across
 * content updates; the full question is snapshotted as JSON so it can be re-run even if the
 * lesson that carried it changes.
 */
@Serializable
@Entity(tableName = "missed_question", primaryKeys = ["langCode", "promptKey"])
data class MissedQuestion(
    val langCode: String,
    val promptKey: String,
    val questionJson: String,
    val day: Int,                       // where it was first missed, for display only
    val timesMissed: Int = 1,
    val lastMissedEpochDay: Long = 0L,
    val clearedEpochDay: Long? = null
)

@Serializable
@Entity(tableName = "feynman_attempt")
data class FeynmanAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val langCode: String,
    val conceptId: String,
    val selfScore: Int,   // rubric points the learner marked as covered
    val total: Int,
    val doneAtEpoch: Long
)

/**
 * One attempt at a mock-exam section. Scored sections (listening/reading/grammar) store
 * score/total and pass = score/total >= the section threshold; writing/speaking are
 * self-assessed pass/fail with score = total = 0.
 */
@Serializable
@Entity(tableName = "exam_section_attempt")
data class ExamSectionAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val langCode: String,
    val examId: String,
    val sectionId: String,
    val score: Int,
    val total: Int,
    val passed: Boolean,
    val takenAtEpoch: Long
)

/** A ticked item on a plan day's checklist (drill / resource / review item). */
@Serializable
@Entity(tableName = "day_task_check", primaryKeys = ["langCode", "day", "itemId"])
data class DayTaskCheck(
    val langCode: String,
    val day: Int,
    val itemId: String,      // "drill-0", "res-1", "review-2", stable per plan JSON order
    val checkedAtEpoch: Long
)

/** A ticked CEFR can-do statement (exam-readiness self-checklist). */
@Serializable
@Entity(tableName = "can_do_check", primaryKeys = ["langCode", "levelId", "itemId"])
data class CanDoCheck(
    val langCode: String,
    val levelId: String,
    val itemId: String,      // "<skill-index>-<descriptor-index>", stable per levels.json
    val checkedAtEpoch: Long
)
