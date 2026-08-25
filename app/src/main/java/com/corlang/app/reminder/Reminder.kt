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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.corlang.app.MainActivity
import com.corlang.app.data.ContentRepository
import com.corlang.app.data.db.AppDatabase
import com.corlang.app.data.prefs.LanguagePrefs
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Per-language reminder copy is DATA, read from each language's `meta.json` (reminderTitle,
 * reminderTitleNamed, reminderProverb), so adding a language needs no code change here. Reads go
 * through [ContentRepository]; the content gate (ContentValidationTest) requires every shipped
 * language to carry these fields, which replaces the old in-code coverage map.
 */
internal object ReminderCopy {
    /** Bare or addressed title: named form fills the {name} placeholder from meta.json. */
    fun title(meta: com.corlang.app.data.model.LanguageMeta, who: String): String =
        if (who.isEmpty())
            meta.reminderTitle ?: "Time to study ${meta.name}!"
        else
            meta.reminderTitleNamed?.replace("{name}", who)
                ?: meta.reminderTitle
                ?: "Time to study ${meta.name}, $who!"

    fun proverb(meta: com.corlang.app.data.model.LanguageMeta): String =
        meta.reminderProverb ?: "A little today is all it takes."

    /**
     * The notification body. Pure, so the wording is unit-testable without a device.
     *
     * ONE reminder, whose wording reads the state — not two settings. A separate "remind me about
     * my streak" switch would be a second toggle able to fire a second notification on the same
     * evening, and the learner already said when they want to hear from us, once, in Settings.
     *
     * Three states, in order of how much they actually matter to the learner:
     *  - [freezesLeft] > 0 with a lapse in progress: the bank is paying for missed days RIGHT NOW
     *    and is finite, which is the only genuinely time-sensitive thing this app can say.
     *  - a streak running: mentioned, lightly, as momentum rather than as a debt.
     *  - no streak: a plain invitation, with no streak language at all. Someone who bought the
     *    course can work through it whenever they like, and a reminder that manufactures urgency
     *    out of nothing is how an app earns itself a long-press and "turn off notifications".
     *
     * Crossed with whether today's lesson was already STARTED. Variants rotate by day of year so
     * a reminder that arrives every evening does not go invisible through repetition.
     */
    fun body(
        languageName: String,
        streak: Int,
        startedToday: Boolean,
        proverb: String,
        dayOfYear: Int,
        freezesLeft: Int = 0,
        onFreeze: Boolean = false
    ): String {
        val freezeWord = if (freezesLeft == 1) "freeze" else "freezes"
        val variants = when {
            // A lapse the bank is currently covering. Says what is true and what runs out.
            onFreeze && streak > 0 -> listOf(
                "Your $streak-day streak is on a freeze, $freezesLeft $freezeWord left. " +
                    "Today's lesson puts it back on track.",
                "A freeze is holding your $streak days ($freezesLeft left). One lesson clears it.",
                "$freezesLeft streak $freezeWord between you and starting over. Today's lesson is enough."
            )
            startedToday && streak > 0 -> listOf(
                "You started today's lesson. Finish it and day ${streak + 1} is yours.",
                "Today's lesson is open and waiting, and it is the only thing left today.",
                "A few minutes left on today's $languageName lesson."
            )
            startedToday -> listOf(
                "You started today's lesson. Pick it up where you left off.",
                "Today's $languageName lesson is half done. Finish it and day 1 is on the board.",
                "You are already in today's lesson, there is not much left of it."
            )
            // Momentum, not a threat: what today ADDS, never what tonight would cost.
            streak > 0 -> listOf(
                "$streak days of $languageName so far. Today's lesson makes it ${streak + 1}.",
                "One guided lesson, and the run reaches ${streak + 1} days.",
                "$streak days in. Today's lesson is waiting whenever you are.",
                proverb
            )
            else -> listOf(
                "A few minutes of $languageName today beats an hour next week.",
                "Today's lesson is ready when you are.",
                proverb
            )
        }
        return variants[dayOfYear.mod(variants.size)]
    }
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
        val content = ContentRepository(ctx)
        // Only nag about languages the user opted into (Settings → Study reminder).
        // No explicit choice yet = follow the selected language, the pre-existing behavior.
        // Intersected with the SHIPPED languages: a course that has since been hidden from
        // content/_index.json is still in this stored set, and would otherwise keep sending
        // daily nudges for a course the app no longer opens.
        val chosen = (prefs.reminderLanguages.first() ?: setOf(selected))
            .filter { it in content.availableLanguages }.toSet()
        if (chosen.isEmpty()) return Result.success()
        val dao = AppDatabase.get(ctx).progressDao()
        val today = LocalDate.now().toEpochDay()
        // The periodic worker and the catch-up can both come due on the same evening.
        if (prefs.lastReminderDay.first() == today) return Result.success()

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
        val (streak, freezesLeft) = com.corlang.app.data.ProgressRepository.settle(
            streak = progress?.streak ?: 0,
            lastStudiedEpochDay = progress?.lastStudiedEpochDay ?: 0L,
            freezes = progress?.streakFreezes ?: 0,
            today = today
        )
        // A lapse is being covered right now: the last completed day is further back than
        // yesterday, and only the bank is keeping the run alive.
        val onFreeze = streak > 0 && (today - (progress?.lastStudiedEpochDay ?: 0L)) > 1L
        val meta = content.meta(lang)
        val languageName = meta.name
        // The learner's name, when they gave one, is what makes the nudge feel addressed to a
        // person rather than broadcast. Appended to the in-language title so the greeting still
        // opens in the language being learned: "Vrijeme je za hrvatski, Ricardo! 🇭🇷".
        val who = prefs.profile.first().name.trim()
        val title = ReminderCopy.title(meta, who)
        // Started today but did not finish: the lesson introduces its new words early and the
        // word step cannot be skipped, so a word introduced today is proof the lesson was
        // opened. That turns the nudge from "start" into "finish what you started", which is a
        // much smaller thing to ask of someone at the end of a day.
        val startedToday = dao.introducedTodayCount(lang, today) > 0
        val text = ReminderCopy.body(
            languageName = languageName,
            streak = streak,
            startedToday = startedToday,
            proverb = ReminderCopy.proverb(meta),
            dayOfYear = LocalDate.now().dayOfYear,
            freezesLeft = freezesLeft,
            onFreeze = onFreeze
        )
        postNotification(ctx, title, text)
        prefs.setLastReminderDay(today)
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

/**
 * How long to wait before a CATCH-UP nudge, or null when none is owed.
 *
 * The bug this exists for: [ReminderScheduler.schedule] is called on every app start to fight
 * WorkManager drift, and it always anchors to the NEXT occurrence of the reminder time. Periodic
 * work is not punctual (Doze batches it), so opening the app at 19:30 while the 19:00 run was
 * still pending re-anchored that pending run to tomorrow and the nudge was never posted. Opening
 * the app was silently cancelling the reminder for the day, which is the opposite of what opening
 * the app should mean when the lesson is not done.
 *
 * So when the slot has already passed, a one-shot catch-up is queued instead. It waits
 * [CATCH_UP_DELAY_MIN] minutes rather than firing at once, because the learner is holding the
 * phone right now and a notification for the app they are looking at is noise; the worker
 * re-checks completion when it runs, so studying in the meantime makes it a silent no-op.
 *
 * Nothing is queued more than [CATCH_UP_WINDOW_H] hours past the chosen time: a nudge that late
 * is stale, and the worst version of this feature is one that wakes someone near midnight.
 */
internal fun catchUpDelay(
    now: LocalDateTime,
    hour: Int,
    minute: Int,
    delayMinutes: Long = CATCH_UP_DELAY_MIN,
    windowHours: Long = CATCH_UP_WINDOW_H
): Duration? {
    val slot = now.toLocalDate().atTime(LocalTime.of(hour, minute))
    if (now.isBefore(slot)) return null              // the periodic run is still ahead of us
    val deadline = slot.plusHours(windowHours)
    if (!now.isBefore(deadline)) return null         // too late to be worth posting
    val fire = minOf(now.plusMinutes(delayMinutes), deadline)
    return Duration.between(now, fire)
}

internal const val CATCH_UP_DELAY_MIN = 45L
internal const val CATCH_UP_WINDOW_H = 3L

object ReminderScheduler {

    private const val WORK_NAME = "corlang-daily-reminder"
    private const val CATCH_UP_NAME = "corlang-reminder-catch-up"

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

        // Re-anchoring above moved any run still owed for today onto tomorrow. Hand it back.
        catchUpDelay(now, hour, minute)?.let { delay ->
            WorkManager.getInstance(context).enqueueUniqueWork(
                CATCH_UP_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
                    .build()
            )
        }
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(CATCH_UP_NAME)
    }
}
