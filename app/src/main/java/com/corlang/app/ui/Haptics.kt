package com.corlang.app.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Explicit vibration for learning feedback. Compose's performHapticFeedback is gated by the
 * system "touch feedback" setting, which many users disable — these effects always fire.
 */
object Haptics {

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** Light tick — card flip, selection. */
    fun tick(context: Context) {
        vibrator(context)?.vibrate(VibrationEffect.createOneShot(20, 120))
    }

    /** Single firm buzz — correct answer / card graded. */
    fun confirm(context: Context) {
        vibrator(context)?.vibrate(VibrationEffect.createOneShot(40, 200))
    }

    /** Double buzz — wrong answer. */
    fun reject(context: Context) {
        vibrator(context)?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 45, 80, 45), intArrayOf(0, 220, 0, 220), -1)
        )
    }
}
