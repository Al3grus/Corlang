package com.corlang.app.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Explicit vibration for learning feedback. Compose's performHapticFeedback is gated by the
 * system "touch feedback" setting, which many users disable, these effects always fire.
 *
 * Strength is a user setting (Settings > Haptic feedback), held here as process-wide state so
 * every call site stays a one-liner; MainActivity keeps it in sync with the DataStore pref.
 * A grade tick right after a swipe needs to be considerably stronger than after a stationary
 * tap, moving fingers mask short vibrations, which is why STRONG exists and MEDIUM is firmer
 * than the old defaults.
 */
object Haptics {

    enum class Strength(val time: Float, val amp: Float) {
        OFF(0f, 0f),
        LIGHT(0.7f, 0.6f),
        MEDIUM(1f, 1f),
        STRONG(1.6f, 1.3f),
    }

    @Volatile
    var strength: Strength = Strength.MEDIUM

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun ms(base: Long): Long = (base * strength.time).toLong().coerceAtLeast(1L)
    private fun amp(base: Int): Int = (base * strength.amp).toInt().coerceIn(1, 255)

    /** Light tick, card flip, selection. */
    fun tick(context: Context) {
        if (strength == Strength.OFF) return
        vibrator(context)?.vibrate(VibrationEffect.createOneShot(ms(20), amp(120)))
    }

    /** Single firm buzz, correct answer / card graded. */
    fun confirm(context: Context) {
        if (strength == Strength.OFF) return
        vibrator(context)?.vibrate(VibrationEffect.createOneShot(ms(55), amp(220)))
    }

    /** Double buzz, wrong answer. */
    fun reject(context: Context) {
        if (strength == Strength.OFF) return
        vibrator(context)?.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, ms(50), ms(70), ms(50)),
                intArrayOf(0, amp(230), 0, amp(230)),
                -1
            )
        )
    }
}
