package com.corlang.app

import com.corlang.app.data.backup.BackupData
import com.corlang.app.data.backup.BackupPrefs
import com.corlang.app.data.db.CanDoCheck
import com.corlang.app.data.db.DayCompletion
import com.corlang.app.data.db.DayTaskCheck
import com.corlang.app.data.db.ExamSectionAttempt
import com.corlang.app.data.db.FeynmanAttempt
import com.corlang.app.data.db.LanguageProgress
import com.corlang.app.data.db.QuizAttempt
import com.corlang.app.data.db.WordReview
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** A backup must survive a full serialize -> deserialize round trip with no data loss. */
class BackupSerializationTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Test
    fun `backup round-trips losslessly`() {
        val original = BackupData(
            exportedAtEpoch = 1_700_000_000_000L,
            progress = listOf(
                LanguageProgress("hr", currentLevel = "A2", currentDay = 63, streak = 12,
                    lastStudiedEpochDay = 19_800L, streakFreezes = 1)
            ),
            completions = listOf(DayCompletion(1, "hr", 1, 100L), DayCompletion(2, "hr", 2, 200L)),
            quizAttempts = listOf(QuizAttempt(1, "hr", "a1-q1", 8, 10, 300L)),
            wordReviews = listOf(WordReview("hr", "kava-coffee", box = 3, dueEpochDay = 19_810L,
                introducedEpochDay = 19_700L, lapses = 1)),
            feynmanAttempts = listOf(FeynmanAttempt(1, "hr", "cases", 3, 4, 400L)),
            examAttempts = listOf(ExamSectionAttempt(1, "hr", "hr-b1-mock", "citanje", 12, 15, true, 500L)),
            canDoChecks = listOf(CanDoCheck("hr", "A2", "0-1", 600L)),
            dayTaskChecks = listOf(DayTaskCheck("hr", 5, "drill-0", 700L)),
            prefs = BackupPrefs(
                reminderEnabled = true, reminderHour = 8, reminderMinute = 30,
                newWordsPerDay = 15, selectedLanguage = "hr"
            )
        )

        val restored = json.decodeFromString<BackupData>(json.encodeToString(original))
        assertEquals(original, restored)
    }

    @Test
    fun `unknown future fields are ignored on restore`() {
        // Forward compatibility: a newer export with extra keys must still restore on an older build.
        val withExtra = """
            {
              "format": 1, "app": "corlang", "exportedAtEpoch": 1,
              "prefs": {"reminderEnabled": false, "reminderHour": 19, "reminderMinute": 0,
                        "newWordsPerDay": 10, "selectedLanguage": "hr"},
              "someFutureField": {"nested": true}
            }
        """.trimIndent()
        val data = json.decodeFromString<BackupData>(withExtra)
        assertEquals("corlang", data.app)
        assertEquals(10, data.prefs.newWordsPerDay)
    }
}
