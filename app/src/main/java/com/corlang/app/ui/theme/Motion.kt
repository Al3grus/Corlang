package com.corlang.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The app's shared motion vocabulary. One place so every screen animates with the same physics -
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

    /**
     * Fade-through: the app's one answer to "the thing in this frame was replaced".
     *
     * The two halves are SEQUENTIAL, not a cross-dissolve - the outgoing content is gone before
     * the incoming content starts. That ordering is the whole trick. A simultaneous crossfade
     * has to be kept very short (both versions are painted at once, so the old one visibly
     * lingers over a screen that has already moved on), and anything short enough to avoid that
     * is too short to read as motion: it lands as a cut. Nothing overlaps here, so the fade can
     * be long enough to actually see.
     */
    const val FADE_OUT_MS = 90
    const val FADE_IN_MS = 210

    /**
     * The same fade-through at WHOLE-SCREEN scale: a tab replacing a tab, and anything that opens
     * over one (Settings, the paywall, a Profile sub-page). Twice the in-place figures above,
     * because it is twice the journey. A verdict opening under a question is one detail changing
     * and wants to be over quickly; a whole screen being replaced is the app going somewhere, and
     * at the shorter timing that read as a blink between two states rather than as a move between
     * them. Every screen change in the app is spent from these two, so they all move alike.
     */
    const val SCREEN_FADE_OUT_MS = FADE_OUT_MS * 2
    const val SCREEN_FADE_IN_MS = FADE_IN_MS * 2

    /**
     * A RUN of things that arrive together - a column of choices, a list that has just loaded.
     * Each one fades for [CASCADE_FADE_MS], starting [CASCADE_STEP_MS] after the one above it.
     *
     * These two are separate knobs and the mistake is to tie them together. Shortening the fade
     * to shorten the run is what makes a cascade look cheap: a hundred milliseconds is six frames,
     * which the eye reads as things appearing one after another rather than as things fading in.
     * The fade stays the app's ordinary fade, long enough to actually see, and the RUN is kept
     * short by the step instead - a quarter of a fade, so four of them are on their way at any
     * moment and the column arrives as one wave rather than as a queue of separate arrivals.
     */
    const val CASCADE_FADE_MS = FADE_IN_MS
    const val CASCADE_STEP_MS = FADE_IN_MS / 4

    fun enter(reduced: Boolean): EnterTransition =
        if (reduced) EnterTransition.None
        else fadeIn(tween(SCREEN_FADE_IN_MS, delayMillis = SCREEN_FADE_OUT_MS))

    fun exit(reduced: Boolean): ExitTransition =
        if (reduced) ExitTransition.None else fadeOut(tween(SCREEN_FADE_OUT_MS))

    /**
     * For something that OPENS UNDER what is already on screen and pushes it down: an answer
     * verdict under a question, a hint under a field. It grows into place rather than shoving
     * the button below it a card's height down between one frame and the next.
     */
    fun revealEnter(reduced: Boolean): EnterTransition =
        if (reduced) EnterTransition.None
        else fadeIn(tween(FADE_IN_MS)) + expandVertically(tween(FADE_IN_MS))

    fun revealExit(reduced: Boolean): ExitTransition =
        if (reduced) ExitTransition.None
        else fadeOut(tween(FADE_OUT_MS)) + shrinkVertically(tween(FADE_OUT_MS))
}

/**
 * Alpha for content that appears where there was nothing: a screen coming out of its load gate,
 * the next question, the next word. Starts transparent and settles opaque the first time each
 * [key] is seen, so replaced content reads as a fade rather than a hard swap.
 *
 * Deliberately NOT an AnimatedContent. The outgoing content here is never worth keeping alive
 * through a transition - by the time the key changes it has already been reset (a checked
 * question whose verdict was just cleared), so a crossfade would dissolve to a half-blanked
 * copy of itself - and keeping a text field alive across a content change costs the keyboard.
 */
@Composable
fun rememberAppearAlpha(
    key: Any? = Unit,
    durationMillis: Int = Motion.FADE_IN_MS,
    /** Held transparent this long first - a run's place in its own cascade. See CASCADE_STEP_MS. */
    delayMillis: Int = 0
): Float {
    val reduced = rememberReducedMotion()
    val alpha = remember(key, reduced, delayMillis) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(key, reduced, delayMillis) {
        if (!reduced) alpha.animateTo(1f, tween(durationMillis, delayMillis = delayMillis))
    }
    return alpha.value
}

/**
 * True when the user has disabled animations system-wide (Developer options / accessibility ->
 * animator duration scale 0). Callers collapse their transitions to instant so we respect the
 * OS-level preference and never induce motion sickness. Read once - the scale rarely changes
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
