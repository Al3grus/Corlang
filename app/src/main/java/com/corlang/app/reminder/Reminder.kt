package com.corlang.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.corlang.app.MainActivity
import com.corlang.app.data.db.AppDatabase
import com.corlang.app.data.prefs.LanguagePrefs
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Daily study reminder. A periodic worker fires around [REMINDER_HOUR]; if nothing
 * has been studied that day it posts a nudge with the current streak at stake.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val lang = LanguagePrefs(ctx).selectedLanguage.first()
        val progress = AppDatabase.get(ctx).progressDao().progressOnce(lang)
        val today = LocalDate.now().toEpochDay()

        // Today's lesson already completed (the only thing that banks the streak), no nag needed.
        if (progress?.lastStudiedEpochDay == today) return Result.success()

        val streak = progress?.streak ?: 0
        // Copy follows the ACTIVE language — a French learner must not be nagged about Croatian.
        val languageName = when (lang) {
            "fr" -> "French"
            else -> "Croatian"
        }
        val title = when (lang) {
            "fr" -> "C'est l'heure du français ! 🇫🇷"
            else -> "Vrijeme je za hrvatski! 🇭🇷"
        }
        val littleByLittle = when (lang) {
            "fr" -> "Petit à petit, a little today is all it takes."
            else -> "Malo po malo, a little today is all it takes."
        }
        // Rotate copy so the reminder doesn't become invisible through repetition.
        val variants = if (streak > 0) listOf(
            "Your $streak-day streak is on the line, today's lesson banks it.",
            "One guided lesson = streak safe. $streak days and counting.",
            "$streak days of $languageName so far. Don't let today be the gap.",
            "Finishing today's lesson beats starting over. Streak: $streak days."
        ) else listOf(
            "A few minutes of $languageName today beats an hour next week. Start today's lesson.",
            "Day 1 of a streak starts with one guided lesson.",
            littleByLittle
        )
        val text = variants[(LocalDate.now().dayOfYear % variants.size)]
        postNotification(ctx, title, text)
        return Result.success()
    }

    private fun postNotification(ctx: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Daily study reminder", NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Once a day, only if you haven't studied yet." }
            )
        }

        val intent = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(com.corlang.app.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "daily_reminder"
        const val NOTIFICATION_ID = 1001
    }
}

object ReminderScheduler {

    private const val WORK_NAME = "corlang-daily-reminder"

    /** Schedules (or reschedules) the daily reminder at the user's chosen time. */
    fun schedule(context: Context, hour: Int = 19, minute: Int = 0) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val initialDelay = Duration.between(now, next)

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay.toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
