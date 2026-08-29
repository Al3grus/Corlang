package com.corlang.app.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
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
import java.time.ZoneId

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
 * Posts the daily nudge, when one is actually owed.
 *
 * Pulled out of the old worker so the exact alarm ([ReminderScheduler]) and the legacy
 * [ReminderWorker] shim share one implementation and one dedupe guard.
 */
internal object ReminderNotifier {

    const val CHANNEL_ID = "daily_reminder"
    const val NOTIFICATION_ID = 1001

    suspend fun postIfDue(ctx: Context) {
        val prefs = LanguagePrefs(ctx)
        // An alarm armed before the learner turned the reminder off (or a stale one that
        // survived an upgrade) must not post. The switch is the authority, not the alarm.
        if (!prefs.reminderEnabled.first()) return
        val selected = prefs.selectedLanguage.first()
        val content = ContentRepository(ctx)
        // Only nag about languages the user opted into (Settings -> Study reminder).
        // No explicit choice yet = follow the selected language, the pre-existing behavior.
        // Intersected with the SHIPPED languages: a course that has since been hidden from
        // content/_index.json is still in this stored set, and would otherwise keep sending
        // daily nudges for a course the app no longer opens.
        val chosen = (prefs.reminderLanguages.first() ?: setOf(selected))
            .filter { it in content.availableLanguages }.toSet()
        if (chosen.isEmpty()) return
        val dao = AppDatabase.get(ctx).progressDao()
        val today = LocalDate.now().toEpochDay()
        // The daily alarm and the catch-up can both come due on the same evening.
        if (prefs.lastReminderDay.first() == today) return

        // Selected language first so the nudge matches what the app opens to; then the rest.
        val candidates = (listOf(selected).filter { it in chosen } + (chosen - selected).sorted())
        // A day's lesson banks the streak; languages already studied today need no nag.
        val lang = candidates.firstOrNull {
            dao.progressOnce(it)?.lastStudiedEpochDay != today
        } ?: return

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
        // opens in the language being learned: "Vrijeme je za hrvatski, Ricardo!".
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
}

/**
 * Legacy entry point, kept only so periodic work enqueued by builds up to v0.85.0 still does
 * the right thing on the one occasion it runs before [ReminderScheduler] cancels it. New
 * scheduling never goes through WorkManager: it is deferrable by design, and Doze batched the
 * daily run more than an hour past the learner's chosen time.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ReminderNotifier.postIfDue(applicationContext)
        return Result.success()
    }
}

/**
 * The wall-clock instant the daily alarm should next fire: today at the chosen time if that is
 * still ahead of us, otherwise tomorrow. Pure, so the arithmetic is testable without a device.
 */
internal fun nextTrigger(now: LocalDateTime, hour: Int, minute: Int): LocalDateTime {
    val today = now.toLocalDate().atTime(LocalTime.of(hour, minute))
    return if (today.isAfter(now)) today else today.plusDays(1)
}

/**
 * How long to wait before a CATCH-UP nudge, or null when none is owed.
 *
 * An exact alarm is punctual, but it is not indestructible: alarms are dropped when the device
 * is off (the boot receiver can only re-arm the NEXT one), a learner who declined the
 * "Alarms & reminders" permission gets an inexact alarm that Doze may slide past, and a
 * force-stop clears every alarm the app holds. So opening the app after the chosen time, with
 * today's nudge never posted, still queues one.
 *
 * It waits [CATCH_UP_DELAY_MIN] minutes rather than firing at once, because the learner is
 * holding the phone right now and a notification for the app they are looking at is noise; the
 * check re-runs when it fires, so studying in the meantime makes it a silent no-op.
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
    if (now.isBefore(slot)) return null              // the daily alarm is still ahead of us
    val deadline = slot.plusHours(windowHours)
    if (!now.isBefore(deadline)) return null         // too late to be worth posting
    val fire = minOf(now.plusMinutes(delayMinutes), deadline)
    return Duration.between(now, fire)
}

/** The alarm intent actions, shared with [ReminderReceiver], which is their only other reader. */
internal const val ACTION_REMINDER_DAILY = "com.corlang.app.reminder.DAILY"
internal const val ACTION_REMINDER_CATCH_UP = "com.corlang.app.reminder.CATCH_UP"

internal const val CATCH_UP_DELAY_MIN = 45L
internal const val CATCH_UP_WINDOW_H = 3L

/**
 * Arms the daily reminder as an EXACT alarm.
 *
 * Field report 2026-08-29: a reminder set for 10:00 arrived at 11:38. It was periodic
 * WorkManager work, which the platform is free to batch and defer - "roughly daily" is the
 * strongest promise that API makes, and Doze on a phone in a pocket routinely turns that into
 * an hour and a half. A time the learner typed is not a hint, so the schedule moved to
 * AlarmManager's exact, allow-while-idle alarms, which fire at the minute even in Doze.
 *
 * Exactness needs the user's consent from API 31 (and is NOT pre-granted from API 34), so the
 * scheduler degrades rather than fails: without the permission the same alarm is armed
 * inexactly, which is late but never silent. Settings asks for it at the moment the learner
 * turns the reminder on ([canBeExact] / [exactAlarmSettingsIntent]).
 */
object ReminderScheduler {

    private const val REQ_DAILY = 4101
    private const val REQ_CATCH_UP = 4102
    // Unique names of the periodic work older builds enqueued. Cancelled on every schedule so
    // an upgraded install stops getting the drifting nudge as well as the punctual one.
    private const val LEGACY_WORK = "corlang-daily-reminder"
    private const val LEGACY_CATCH_UP_WORK = "corlang-reminder-catch-up"

    /**
     * Schedules (or reschedules) the daily reminder at the user's chosen time.
     *
     * [withCatchUp] is false only when the alarm itself has just fired: re-arming for tomorrow
     * at 10:00:00 would otherwise look exactly like the app being opened at 10:00:00, and queue
     * a catch-up for a nudge that was posted a second ago.
     */
    fun schedule(context: Context, hour: Int = 19, minute: Int = 0, withCatchUp: Boolean = true) {
        val ctx = context.applicationContext
        cancelLegacyWork(ctx)
        val now = LocalDateTime.now()
        armAt(ctx, REQ_DAILY, ACTION_REMINDER_DAILY, epochMillis(nextTrigger(now, hour, minute)))

        val catchUp = if (withCatchUp) catchUpDelay(now, hour, minute) else null
        if (catchUp != null) {
            armAt(ctx, REQ_CATCH_UP, ACTION_REMINDER_CATCH_UP, epochMillis(now.plus(catchUp)))
        } else {
            disarm(ctx, REQ_CATCH_UP, ACTION_REMINDER_CATCH_UP)
        }
    }

    fun cancel(context: Context) {
        val ctx = context.applicationContext
        disarm(ctx, REQ_DAILY, ACTION_REMINDER_DAILY)
        disarm(ctx, REQ_CATCH_UP, ACTION_REMINDER_CATCH_UP)
        cancelLegacyWork(ctx)
    }

    /**
     * Whether the platform will let us fire at the exact minute. True below API 31, where every
     * alarm was exact; from API 31 it is a user-grantable permission, and from API 34 apps that
     * are not clocks or calendars start without it.
     */
    fun canBeExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    /** The system screen where "Alarms & reminders" is granted, or null below API 31. */
    fun exactAlarmSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < 31) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.fromParts("package", context.packageName, null))
    }

    private fun armAt(ctx: Context, req: Int, action: String, atMillis: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(ctx, req, action, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            if (canBeExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } catch (e: SecurityException) {
            // The permission can be revoked between the check and the call. Late beats absent.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private fun disarm(ctx: Context, req: Int, action: String) {
        val pi = pending(ctx, req, action, PendingIntent.FLAG_NO_CREATE) ?: return
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pi)
        pi.cancel()
    }

    private fun pending(ctx: Context, req: Int, action: String, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            ctx, req,
            Intent(ctx, ReminderReceiver::class.java).setAction(action),
            flags or PendingIntent.FLAG_IMMUTABLE
        )

    private fun cancelLegacyWork(ctx: Context) {
        runCatching {
            val wm = WorkManager.getInstance(ctx)
            wm.cancelUniqueWork(LEGACY_WORK)
            wm.cancelUniqueWork(LEGACY_CATCH_UP_WORK)
        }
    }

    private fun epochMillis(at: LocalDateTime): Long =
        at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
