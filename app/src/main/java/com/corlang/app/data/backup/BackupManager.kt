package com.corlang.app.data.backup

import com.corlang.app.data.db.CanDoCheck
import com.corlang.app.data.db.DayCompletion
import com.corlang.app.data.db.DayTaskCheck
import com.corlang.app.data.db.ExamSectionAttempt
import com.corlang.app.data.db.FeynmanAttempt
import com.corlang.app.data.db.LanguageProgress
import com.corlang.app.data.db.ProgressDao
import com.corlang.app.data.db.QuizAttempt
import com.corlang.app.data.db.WordReview
import com.corlang.app.data.prefs.LanguagePrefs
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The small subset of settings worth carrying across a reinstall. Secrets are never included. */
@Serializable
data class BackupPrefs(
    val reminderEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
    val newWordsPerDay: Int,
    val selectedLanguage: String,
    // Learner profile (added in format 1 with defaults, so older backups still parse).
    val onboardingDone: Boolean = false,
    val profileName: String = "",
    val profileGender: String = "m",
    val profileFrom: String = "",
    val profileLivesIn: String = "",
    val profileReason: String = ""
)

/** A complete, portable snapshot of the learner's progress across every language. */
@Serializable
data class BackupData(
    val format: Int = 1,
    val app: String = "corlang",
    val exportedAtEpoch: Long,
    val progress: List<LanguageProgress> = emptyList(),
    val completions: List<DayCompletion> = emptyList(),
    val quizAttempts: List<QuizAttempt> = emptyList(),
    val wordReviews: List<WordReview> = emptyList(),
    val feynmanAttempts: List<FeynmanAttempt> = emptyList(),
    val examAttempts: List<ExamSectionAttempt> = emptyList(),
    val canDoChecks: List<CanDoCheck> = emptyList(),
    val dayTaskChecks: List<DayTaskCheck> = emptyList(),
    val prefs: BackupPrefs
)

/**
 * Exports and restores all learner progress as a single JSON document. Content (words, plans,
 * quizzes) is bundled with the app, so a backup carries only what the learner produced: SRS
 * state, streaks, completions, quiz/exam attempts, checklists, and a few settings.
 *
 * The caller owns file access (via the Storage Access Framework); this class only turns the
 * database and prefs into a string and back, so it stays testable and free of Android IO.
 */
class BackupManager(
    private val dao: ProgressDao,
    private val prefs: LanguagePrefs
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /** Serializes everything to a pretty JSON string. [nowEpoch] stamps the export time. */
    suspend fun export(nowEpoch: Long): String {
        val (h, m) = prefs.reminderTime.first()
        val profile = prefs.profile.first()
        val data = BackupData(
            exportedAtEpoch = nowEpoch,
            progress = dao.allProgress(),
            completions = dao.allCompletions(),
            quizAttempts = dao.allQuizAttempts(),
            wordReviews = dao.allWordReviews(),
            feynmanAttempts = dao.allFeynmanAttempts(),
            examAttempts = dao.allExamAttempts(),
            canDoChecks = dao.allCanDoChecks(),
            dayTaskChecks = dao.allDayTaskChecks(),
            prefs = BackupPrefs(
                reminderEnabled = prefs.reminderEnabled.first(),
                reminderHour = h,
                reminderMinute = m,
                newWordsPerDay = prefs.newWordsPerDay.first(),
                selectedLanguage = prefs.selectedLanguage.first(),
                onboardingDone = prefs.onboardingDone.first(),
                profileName = profile.name,
                profileGender = profile.gender,
                profileFrom = profile.from,
                profileLivesIn = profile.livesIn,
                profileReason = profile.reason
            )
        )
        return json.encodeToString(data)
    }

    /**
     * Replaces all local progress with the contents of [text]. Returns the number of restored
     * rows on success, or a failure carrying a human-readable reason (bad file, wrong format).
     * The database swap is atomic; a malformed file leaves existing data untouched.
     */
    suspend fun import(text: String): Result<Int> = try {
        val data = json.decodeFromString<BackupData>(text)
        require(data.app == "corlang") { "This file isn't a Corlang backup." }
        dao.replaceAll(
            progress = data.progress,
            completions = data.completions,
            quizAttempts = data.quizAttempts,
            wordReviews = data.wordReviews,
            feynmanAttempts = data.feynmanAttempts,
            examAttempts = data.examAttempts,
            canDoChecks = data.canDoChecks,
            dayTaskChecks = data.dayTaskChecks
        )
        prefs.setReminderEnabled(data.prefs.reminderEnabled)
        prefs.setReminderTime(data.prefs.reminderHour, data.prefs.reminderMinute)
        prefs.setNewWordsPerDay(data.prefs.newWordsPerDay)
        prefs.setLanguage(data.prefs.selectedLanguage)
        prefs.setOnboardingDone(data.prefs.onboardingDone)
        prefs.setProfile(
            com.corlang.app.data.prefs.LearnerProfile(
                name = data.prefs.profileName,
                gender = data.prefs.profileGender,
                from = data.prefs.profileFrom,
                livesIn = data.prefs.profileLivesIn,
                reason = data.prefs.profileReason
            )
        )
        val rows = data.progress.size + data.completions.size + data.quizAttempts.size +
            data.wordReviews.size + data.feynmanAttempts.size + data.examAttempts.size +
            data.canDoChecks.size + data.dayTaskChecks.size
        Result.success(rows)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
