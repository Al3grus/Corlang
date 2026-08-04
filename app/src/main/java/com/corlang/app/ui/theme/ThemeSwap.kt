package com.corlang.app.ui.theme

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/*
 * Switching theme, done as one movement.
 *
 * The obvious implementation — animate every Material role from its old value to its new one —
 * looks wrong in practice, and the reason is worth recording. A colorScheme is read by nearly
 * every composable on screen, so easing 35 roles means invalidating and RECOMPOSING THE WHOLE
 * TREE on every frame of the animation. On a dense screen that drops frames, and dropped frames
 * during a color sweep read as the app repainting itself in pieces: exactly the "quick, but it
 * feels like it's loading" complaint. The animation was fighting the work it caused.
 *
 * So the colors do not animate at all. Instead:
 *   1. freeze the screen as it is (a single bitmap of the window),
 *   2. swap the scheme underneath in ONE recomposition, hidden behind that frozen frame,
 *   3. wait until the new theme has actually drawn,
 *   4. fade the frozen frame out.
 *
 * The visible transition is a pure alpha crossfade of two finished images, which costs the GPU
 * one blend and costs the CPU nothing — no recomposition happens while it runs. The learner
 * never sees a half-painted screen, because the new theme is complete before it is revealed.
 */

/**
 * Wraps [CorlangTheme] with the freeze-swap-fade transition above. [dark] is the REQUESTED theme;
 * what is on screen follows a frame or two later, deliberately.
 *
 * A capture can fail (no window surface yet, a device that refuses PixelCopy). That is not worth
 * an error path: the theme simply changes instantly, which is what the app did before.
 */
@Composable
fun CorlangThemeSwap(dark: Boolean, content: @Composable () -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current

    // The theme actually being rendered. Lags `dark` by the length of the capture.
    var applied by remember { mutableStateOf(dark) }
    // Which theme the system bar ICONS should suit. Flipped mid-fade rather than at the swap:
    // the bars sit above the frozen frame, so switching them at step 2 would put dark icons on
    // a still-visible dark screen for the whole transition.
    var barsDark by remember { mutableStateOf(dark) }
    var frozen by remember { mutableStateOf<ImageBitmap?>(null) }
    val fade = remember { Animatable(0f) }

    LaunchedEffect(dark) {
        if (dark == applied) return@LaunchedEffect
        val shot = if (view.isInEditMode) null else captureWindow(context as? Activity)
        if (shot != null) {
            // Both states land in the same recomposition, so the new theme is never visible
            // before the frozen frame covers it.
            frozen = shot
            fade.snapTo(1f)
        }
        applied = dark
        if (shot == null) {
            barsDark = dark
            return@LaunchedEffect
        }
        // Two frames: one for the swapped composition to be drawn, one for it to reach the
        // screen. Revealing earlier is what would show a half-drawn theme.
        withFrameNanos {}
        withFrameNanos {}
        launch {
            delay(FADE_MS / 2)
            barsDark = dark
        }
        fade.animateTo(0f, tween(durationMillis = FADE_MS.toInt(), easing = FastOutSlowInEasing))
        frozen = null
    }

    Box(Modifier.fillMaxSize()) {
        CorlangTheme(dark = applied, systemBarsDark = barsDark, content = content)

        val shot = frozen
        if (shot != null && fade.value > 0f) {
            Image(
                bitmap = shot,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(fade.value)
                    // Swallow taps for the length of the fade. The real UI underneath is live
                    // and invisible; a tap landing there would feel like a misfire.
                    .pointerInput(Unit) {
                        awaitPointerEventScope { while (true) awaitPointerEvent() }
                    }
            )
        }
    }
}

private const val FADE_MS = 420L

/**
 * The window exactly as the user is seeing it, as a bitmap. PixelCopy reads the real surface,
 * so this includes everything the app drew — no re-rendering, and nothing to keep in memory
 * while idle (a persistent capture layer would cost a screen-sized buffer for the whole session
 * to serve a transition that happens once in a while).
 */
private suspend fun captureWindow(activity: Activity?): ImageBitmap? {
    val window = activity?.window ?: return null
    val decor = window.decorView
    val w = decor.width
    val h = decor.height
    if (w <= 0 || h <= 0) return null
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    return suspendCancellableCoroutine { cont ->
        try {
            PixelCopy.request(
                window,
                bitmap,
                { result ->
                    if (cont.isActive) {
                        cont.resume(
                            if (result == PixelCopy.SUCCESS) bitmap.asImageBitmap() else null
                        )
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (e: Throwable) {
            // IllegalArgumentException when the window has no surface yet, and whatever a given
            // OEM decides to throw. Either way: no freeze frame, so no fade.
            if (cont.isActive) cont.resume(null)
        }
    }
}
