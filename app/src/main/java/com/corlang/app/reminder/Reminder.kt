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
 * Per-language reminder copy, as MAPS rather than `when` branches so a test can assert the
 * keys cover every available language (registry S13: German shipped its first wiring pass
 * with no Reminder branch, and a `when` cannot be enumerated by a test). The hr fallback is
 * deliberate for unknown codes, but a KNOWN language falling back is a wiring bug the
 * coverage gate now catches.
 */
internal object ReminderCopy {
    internal val names = mapOf(
        "hr" to "Croatian", "fr" to "French", "pt" to "Portuguese",
        "de" to "German", "it" to "Italian"
    )
    // title(lang, "") gives the bare greeting; with a name, the addressed one.
    internal val titles: Map<String, (String) -> String> = mapOf(
        "hr" to { who -> if (who.isEmpty()) "Vrijeme je za hrvatski! 🇭🇷" else "Vrijeme je za hrvatski, $who! 🇭🇷" },
        "fr" to { who -> if (who.isEmpty()) "C'est l'heure du français ! 🇫🇷" else "C'est l'heure du français, $who ! 🇫🇷" },
        "pt" to { who -> if (who.isEmpty()) "Está na hora do português! 🇵🇹" else "Está na hora do português, $who! 🇵🇹" },
        "de" to { who -> if (who.isEmpty()) "Zeit für Deutsch! 🇩🇪" else "Zeit für Deutsch, $who! 🇩🇪" },
        "it" to { who -> if (who.isEmpty()) "È ora di italiano! 🇮🇹" else "È ora di italiano, $who! 🇮🇹" }
    )
    internal val proverbs = mapOf(
        "hr" to "Malo po malo, a little today is all it takes.",
        "fr" to "Petit à petit, a little today is all it takes.",
        "pt" to "Devagar se vai ao longe, a little today is all it takes.",
        "de" to "Steter Tropfen höhlt den Stein, a little today is all it takes.",
        "it" to "Goccia a goccia si scava la pietra, a little today is all it takes."
    )

    fun name(lang: String) = names[lang] ?: names.getValue("hr")
    fun title(lang: String, who: String) = (titles[lang] ?: titles.getValue("hr"))(who)
    fun proverb(lang: String) = proverbs[lang] ?: proverbs.getValue("hr")
}

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
        val prefs = LanguagePrefs(ctx)
        val selected = prefs.selectedLanguage.first()
        // Only nag about languages the user opted into (Settings → Study reminder).
        // No explicit choice yet = follow the selected language, the pre-existing behavior.
        val chosen = prefs.reminderLanguages.first() ?: setOf(selected)
        val dao = AppDatabase.get(ctx).progressDao()
        val today = LocalDate.now().toEpochDay()

        // Selected language first so the nudge matches what the app opens to; then the rest.
        val candidates = (listOf(selected).filter { it in chosen } + (chosen - selected).sorted())
        // A day's lesson banks the streak; languages already studied today need no nag.
        val lang = candidates.firstOrNull {
            dao.progressOnce(it)?.lastStudiedEpochDay != today
        } ?: return Result.success()

        val progress = dao.progressOnce(lang)
        // Decayed to right-now, same as the UI: the STORED streak only updates on the next
        // completion, so after 2+ missed days the raw value still read "12-day streak on the
        // line" when the streak was already gone.
        val streak = com.corlang.app.data.ProgressRepository.displayStreak(
            streak = progress?.streak ?: 0,
            lastStudiedEpochDay = progress?.lastStudiedEpochDay ?: 0L,
            freezes = progress?.streakFreezes ?: 0,
            today = today
        )
        val languageName = ReminderCopy.name(lang)
        // The learner's name, when they gave one, is what makes the nudge feel addressed to a
        // person rather than broadcast. Appended to the in-language title so the greeting still
        // opens in the language being learned: "Vrijeme je za hrvatski, Ricardo! 🇭🇷".
        val who = prefs.profile.first().name.trim()
        val title = ReminderCopy.title(lang, who)
        val littleByLittle = ReminderCopy.proverb(lang)
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
