package com.corlang.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.corlang.app.data.prefs.LanguagePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Where the exact alarm lands, and where the schedule is rebuilt when the system throws it away.
 *
 * Two jobs, both of them the reason the reminder is now punctual:
 *  - an alarm fires: post the nudge if one is owed, then re-arm for tomorrow. Exact alarms are
 *    one-shot by nature, so the daily cadence is this re-arm rather than a repeating alarm
 *    (a repeating alarm is inexact on every Android since KitKat).
 *  - the system dropped our alarms: after a reboot, after the app is replaced by an update, or
 *    after the clock or timezone moved. An alarm is a fixed instant, so a timezone change would
 *    otherwise fire "10:00" at the old zone's 10:00.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val fires = intent.action == ACTION_REMINDER_DAILY ||
            intent.action == ACTION_REMINDER_CATCH_UP
        // goAsync: the work reads DataStore and Room, which a receiver's synchronous window
        // cannot do. finish() must run on every path or the process is held open.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                if (fires) ReminderNotifier.postIfDue(ctx)
                rearm(ctx, afterFiring = fires)
            } finally {
                pending.finish()
            }
        }
    }

    /** Re-arms from the stored preference, which is the only authority on when and whether. */
    private suspend fun rearm(ctx: Context, afterFiring: Boolean) {
        val prefs = LanguagePrefs(ctx)
        if (!prefs.reminderEnabled.first()) return
        val (hour, minute) = prefs.reminderTime.first()
        ReminderScheduler.schedule(ctx, hour, minute, withCatchUp = !afterFiring)
    }
}
