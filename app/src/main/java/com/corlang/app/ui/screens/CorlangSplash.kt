package com.corlang.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corlang.app.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * Corlang loading screen. The two-ring Orbit Core mark fills with REAL load progress and, at
 * 100%, matches the app icon exactly, then resolves into the wordmark (the mark becomes the
 * "o" in Corlang) before revealing the app.
 *
 * Ported from the design handoff rev 2 (design_handoff_corlang_loader/CorlangLoader.jsx):
 * the outer ring's arc grows while sweeping into its final -52deg angle (easeOutCubic), the
 * inner arc reveals leftward from its right anchor, and the core pops in near the end with an
 * ease-out-back overshoot. The web prototype loops on a timer; here `prog` is driven by the
 * actual content preload, and the settle -> letters -> tagline sequence (easeInOutCubic)
 * plays once, then onReady() hands over to the app.
 */

private val SplashBg = Color(0xFF0F1620)      // deep ink-navy
private val SplashBrand = Color(0xFF2F7FAE)   // ring
private val SplashCore = Color(0xFFC8402C)    // molten core
private val SplashInk = Color(0xFFF4EFE6)     // letters / on-dark text

private fun cl(x: Float): Float = x.coerceIn(0f, 1f)

/** easeOutCubic: 1 - (1-x)^3. */
private fun eo(x: Float): Float {
    val v = 1f - cl(x)
    return 1f - v * v * v
}

/** easeInOutCubic (the rev-2 settle/letter easing). */
private fun eio(x: Float): Float {
    val v = cl(x)
    return if (v < 0.5f) 4f * v * v * v else 1f - ((-2f * v + 2f) * (-2f * v + 2f) * (-2f * v + 2f)) / 2f
}

/** easeOutBack with overshoot c=2.2 (the core's pop). */
private fun eob(x: Float): Float {
    if (x <= 0f) return 0f
    val c = 2.2f
    val v = cl(x) - 1f
    return 1f + (c + 1f) * v * v * v + c * v * v
}

/**
 * The load work whose completion drives the ring. These warm the content caches the first screen
 * needs (plan and vocab are the heavy ones), so progress reflects genuine startup cost, and the
 * app is instant once the splash clears.
 */
private fun preloadSteps(container: AppContainer, lang: String): List<suspend () -> Unit> = listOf(
    { container.content.meta(lang) },
    { container.content.levels(lang) },
    { container.content.cheatsheet(lang) },
    { container.content.grammar(lang) },
    { container.content.feynman(lang) },
    { container.content.quizzes(lang) },
    { container.content.resources(lang) },
    { container.content.exams(lang) },
    { container.content.plan(lang) },
    { container.content.vocab(lang) },
)

@Composable
fun CorlangSplash(container: AppContainer, onReady: () -> Unit) {
    val progress = remember { Animatable(0f) }   // ring fill (follows real load, smoothed)
    val resolve = remember { Animatable(0f) }     // 0..1 settle -> letters -> tagline, plays once
    val stageAlpha = remember { Animatable(0f) }  // fade in when measured, fade out on handover
    val target = remember { mutableFloatStateOf(0f) }

    // Measured at mount: how far the mark must travel from its "o" slot to screen-center during load.
    var stageCx by remember { mutableStateOf(Float.NaN) }
    var markCx by remember { mutableStateOf(Float.NaN) }
    val measured = !stageCx.isNaN() && !markCx.isNaN()
    val dx = if (measured) stageCx - markCx else 0f

    val density = LocalDensity.current
    val rise = with(density) { 8.dp.toPx() }

    // Only reveal once we can place the mark correctly, so it never jumps on the first frame.
    LaunchedEffect(measured) { if (measured) stageAlpha.animateTo(1f, tween(280)) }

    // Smoothly ease the visible fill toward each real milestone.
    LaunchedEffect(Unit) {
        snapshotFlow { target.floatValue }.collect { tv ->
            progress.animateTo(tv, tween(420, easing = FastOutSlowInEasing))
        }
    }

    // Real preload -> 100% -> resolve the wordmark -> reveal the app.
    LaunchedEffect(Unit) {
        val lang = container.languagePrefs.selectedLanguage.first()
        container.progress.ensure(lang)
        val steps = preloadSteps(container, lang)
        steps.forEachIndexed { i, step ->
            withContext(Dispatchers.Default) { runCatching { step() } }
            target.floatValue = (i + 1f) / steps.size
        }
        // Let the mark visibly reach 100% (= the app icon) before resolving.
        snapshotFlow { progress.value }.first { it >= 0.999f }
        delay(100)
        // Linear driver: each sub-animation (settle/letters/tagline) applies its own easing,
        // mirroring the prototype's single time axis t 0.5 -> 1.0.
        resolve.animateTo(1f, tween(2200, easing = LinearEasing))
        // Let the finished wordmark + tagline hold for a beat before handing over,
        // so "Corlang" registers instead of vanishing the instant it assembles.
        delay(1000)
        stageAlpha.animateTo(0f, tween(450))
        onReady()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(SplashBg)) {
        val hPx = with(density) { maxHeight.toPx() }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = stageAlpha.value }
                .onGloballyPositioned { stageCx = it.boundsInRoot().center.x }
        ) {
            // Wordmark: C · mark · r l a n g. Letters always hold full width (alpha only), so the
            // mark's slot is fixed and its slide to center-and-back never wobbles.
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SplashLetter("C", 0, resolve, rise)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .onGloballyPositioned { markCx = it.boundsInRoot().center.x }
                        .graphicsLayer {
                            // Settle (rev 2): ease-in-out over t 0.5 -> 0.88, scale 2.4 -> 1.0,
                            // sliding left from screen-center into the "o" slot.
                            val tt = 0.5f + 0.5f * resolve.value
                            val sp = eio(cl((tt - 0.5f) / 0.38f))
                            val s = 2.4f - 1.4f * sp
                            scaleX = s
                            scaleY = s
                            translationX = dx * (1f - sp)
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val p = progress.value
                        val w = size.minDimension
                        val stroke = 6f / 100f * w
                        val c = Offset(size.width / 2f, size.height / 2f)

                        // Outer ring: visible arc grows to 229.2deg while the whole ring sweeps
                        // (counter-rotates) into the icon's final -52deg angle.
                        val sweepIn = -52f - (1f - eo(p)) * 210f
                        rotate(degrees = sweepIn + 52f, pivot = c) {
                            drawMarkArc(SplashBrand, c, radius = 33f / 100f * w, stroke = stroke,
                                startDeg = -52f, sweepDeg = 229.2f * p)
                        }

                        // Inner arc: anchored at its right end (-14deg), reveals leftward
                        // (counter-clockwise) onto the icon's inner arc.
                        drawMarkArc(SplashBrand, c, radius = 21f / 100f * w, stroke = stroke,
                            startDeg = -14f, sweepDeg = -218.3f * p)

                        // Core: hidden until ~80%, then pops in with an overshoot.
                        val cp = eob(cl((p - 0.8f) / 0.2f))
                        if (cp > 0f) drawCircle(SplashCore, radius = 9f / 100f * w * cp, center = c)
                    }
                }
                SplashLetter("r", 1, resolve, rise)
                SplashLetter("l", 2, resolve, rise)
                SplashLetter("a", 3, resolve, rise)
                SplashLetter("n", 4, resolve, rise)
                SplashLetter("g", 5, resolve, rise)
            }

            // % counter at ~59% height, visible during load.
            Text(
                text = "${(progress.value * 100f).roundToInt()}%",
                color = SplashInk.copy(alpha = 0.5f),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    letterSpacing = 3.sp
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = hPx * 0.59f
                        alpha = cl(1f - (0.5f * resolve.value) / 0.08f)
                    }
            )

            // Tagline at ~59% height, fades in after the word assembles.
            Text(
                text = "Jezik u srži",
                color = SplashInk.copy(alpha = 0.62f),
                style = TextStyle(
                    fontFamily = com.corlang.app.ui.components.CorlangWordmarkFont,
                    fontSize = 15.sp
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = hPx * 0.59f
                        alpha = cl(((0.5f + 0.5f * resolve.value) - 0.82f) / 0.08f)
                    }
            )
        }
    }
}

/** One wordmark letter: laid out at full width always, only fading + rising into place. */
@Composable
private fun SplashLetter(ch: String, index: Int, resolve: Animatable<Float, *>, rise: Float) {
    Text(
        text = ch,
        color = SplashInk,
        style = TextStyle(
            fontFamily = com.corlang.app.ui.components.CorlangWordmarkFont,
            fontWeight = FontWeight.Bold,
            fontSize = 56.sp,
            letterSpacing = (-1.1).sp
        ),
        modifier = Modifier.graphicsLayer {
            // Rev 2 stagger: letter i enters at t = 0.62 + i*0.04 over 0.18, ease-in-out.
            val tt = 0.5f + 0.5f * resolve.value
            val lp = eio(cl((tt - (0.62f + index * 0.04f)) / 0.18f))
            alpha = lp
            translationY = (1f - lp) * rise
        }
    )
}

/* Letter/tagline styles use the bundled wordmark font (see CorlangWordmarkFont). */

/** Round-capped arc of the mark, in mark-local pixels. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMarkArc(
    color: Color, center: Offset, radius: Float, stroke: Float, startDeg: Float, sweepDeg: Float
) {
    if (sweepDeg == 0f) return
    drawArc(
        color = color,
        startAngle = startDeg,
        sweepAngle = sweepDeg,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = stroke, cap = StrokeCap.Round)
    )
}
