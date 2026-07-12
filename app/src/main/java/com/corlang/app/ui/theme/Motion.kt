package com.corlang.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The app's shared motion vocabulary. One place so every screen animates with the same physics —
 * motion here is functional (things respond to touch, transitions carry direction), never
 * decorative. All specs are spring-based so interrupted animations retarget naturally.
 */
object Motion {
    /** Settling spring for objects that follow the finger then come to rest (cards, pulses). */
    fun <T> settle(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow)

    /** Crisp, bounce-free spring for UI transitions (steps, tabs). */
    fun <T> snappy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
}

/**
 * True when the user has disabled animations system-wide (Developer options / accessibility →
 * animator duration scale 0). Callers collapse their transitions to instant so we respect the
 * OS-level preference and never induce motion sickness. Read once — the scale rarely changes
 * mid-session and reading it live would need a settings observer for no real benefit.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}
