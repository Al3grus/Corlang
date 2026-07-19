package com.corlang.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.corlang.app.ui.theme.rememberReducedMotion
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * Celebration vocabulary (chess.com-inspired): a streak flame that is grey until today's
 * lesson is banked and whose colors intensify as the streak grows, plus a one-shot confetti
 * burst with an encouraging message when a day is completed.
 */

/** Flame colors for a streak length: outer body, inner core. Tiers escalate like chess.com. */
private fun flameTier(streak: Int): Pair<Color, Color> = when {
    streak >= 100 -> Color(0xFFFFD54F) to Color(0xFFFFF9C4)   // gold / white-hot
    streak >= 30 -> Color(0xFF64B5F6) to Color(0xFFE3F2FD)    // blue flame
    streak >= 7 -> Color(0xFFFF8A50) to Color(0xFFFFE0B2)     // vivid orange
    else -> Color(0xFFEF9A6A) to Color(0xFFFFCCBC)            // young ember
}

private val UNLIT_BODY = Color(0xFF5A646D)
private val UNLIT_CORE = Color(0xFF7A848D)

/**
 * The streak flame. [lit] = today's lesson is done (grey otherwise, like chess.com's
 * gray-until-you-play streak). Gently flickers when lit, unless animations are disabled.
 */
@Composable
fun StreakFlame(streak: Int, lit: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val flicker = if (lit && !reduced) {
        val transition = rememberInfiniteTransition(label = "flame")
        transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing), repeatMode = RepeatMode.Reverse
            ),
            label = "flicker"
        ).value
    } else 0f

    val (body, core) = if (lit) flameTier(streak) else UNLIT_BODY to UNLIT_CORE
    Box(
        modifier
            .size(size)
            .drawBehind {
                // Flicker narrows the flame slightly and sways the tip.
                val squeeze = 1f - flicker * 0.06f
                val sway = (flicker - 0.5f) * this.size.width * 0.05f
                drawFlame(body, core, squeeze, sway)
            }
    )
}

/** Teardrop flame with an inner core, drawn relative to the DrawScope size. */
private fun DrawScope.drawFlame(body: Color, core: Color, squeeze: Float, sway: Float) {
    val w = size.width
    val h = size.height

    fun flamePath(cx: Float, top: Float, bottom: Float, halfWidth: Float, tipSway: Float) =
        Path().apply {
            moveTo(cx + tipSway, top)                       // tip
            cubicTo(
                cx + halfWidth * 0.55f, top + (bottom - top) * 0.32f,
                cx + halfWidth, top + (bottom - top) * 0.62f,
                cx + halfWidth * 0.72f, bottom - (bottom - top) * 0.12f
            )
            quadraticTo(cx, bottom + (bottom - top) * 0.06f, cx - halfWidth * 0.72f, bottom - (bottom - top) * 0.12f)
            cubicTo(
                cx - halfWidth, top + (bottom - top) * 0.62f,
                cx - halfWidth * 0.55f, top + (bottom - top) * 0.32f,
                cx + tipSway, top
            )
            close()
        }

    drawPath(flamePath(w / 2f, h * 0.04f, h * 0.96f, w * 0.42f * squeeze, sway), body)
    drawPath(flamePath(w / 2f, h * 0.42f, h * 0.90f, w * 0.22f * squeeze, sway * 0.4f), core)
}

private data class Particle(
    val angleDeg: Float, val speed: Float, val hue: Int,
    val spin: Float, val sizeFactor: Float, val startXFactor: Float
)

/**
 * One-shot confetti burst filling its bounds; plays once on first composition (~1.6s).
 * Under reduced motion it renders nothing — the celebration text carries the moment.
 */
@Composable
fun ConfettiBurst(modifier: Modifier = Modifier) {
    if (rememberReducedMotion()) return
    val particles = remember {
        List(90) {
            Particle(
                angleDeg = Random.nextFloat() * 140f + 20f,      // fan upward
                speed = Random.nextFloat() * 0.9f + 0.55f,
                hue = it % 4,
                spin = Random.nextFloat() * 720f - 360f,
                sizeFactor = Random.nextFloat() * 0.6f + 0.6f,
                startXFactor = Random.nextFloat() * 0.5f + 0.25f
            )
        }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(1600, easing = LinearEasing)) }

    val scheme = MaterialTheme.colorScheme
    val colors = listOf(scheme.primary, scheme.secondary, scheme.tertiary, Color(0xFF8FD694))

    Box(
        modifier.drawBehind {
            val progress = t.value
            if (progress >= 1f) return@drawBehind
            val alpha = if (progress < 0.7f) 1f else 1f - (progress - 0.7f) / 0.3f
            particles.forEach { p ->
                val rad = Math.toRadians(p.angleDeg.toDouble())
                val dist = p.speed * progress * size.height
                val x = p.startXFactor * size.width + (cos(rad) * dist * 0.6f).toFloat()
                val y = size.height * 0.35f - (sin(rad) * dist).toFloat() +
                    progress * progress * size.height * 0.9f   // gravity
                val side = 10f * p.sizeFactor
                rotate(p.spin * progress, pivot = Offset(x, y)) {
                    drawRect(
                        color = colors[p.hue].copy(alpha = alpha),
                        topLeft = Offset(x - side / 2, y - side / 2),
                        size = androidx.compose.ui.geometry.Size(side, side * 0.6f)
                    )
                }
            }
        }
    )
}

/**
 * Words are reserved for moments that earn them: the milestones only. Ordinary days celebrate
 * visually (confetti + flame + count) — a line that appears every day stops being read by day
 * five, and streak 1 recurs on every restart, so it gets no line either.
 */
private fun milestoneLine(streak: Int): String? = when (streak) {
    7 -> "A full week. The habit is forming. 🔥"
    14 -> "Two weeks straight — this is who you are now."
    30 -> "30 days. Your flame burns blue from here. 🔵"
    50 -> "Fifty days of showing up."
    100 -> "100 days — a golden flame for a golden habit. ✨"
    365 -> "A full year. Extraordinary."
    else -> null
}

/**
 * Full-screen day-complete celebration: confetti, the (lit) streak flame, and a message.
 * [streak] should be the post-completion streak value.
 */
@Composable
fun CelebrationOverlay(
    dayNumber: Int,
    streak: Int,
    /** True when THIS completion grew the freeze bank (7-day milestone below the cap). */
    freezeEarned: Boolean = false,
    freezes: Int = 0,
    onDone: () -> Unit
) {
    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
        ) {
            ConfettiBurst(Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StreakFlame(streak = streak, lit = true, size = 96.dp)
                Spacer(Modifier.height(20.dp))
                Text(
                    "Lesson $dayNumber complete!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                milestoneLine(streak)?.let { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (streak > 0) {
                    Text(
                        "🔥 $streak-day streak",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
                if (freezeEarned) {
                    Text(
                        "❄️ Streak freeze earned!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Text(
                        "It will cover one missed day automatically." +
                            if (freezes > 1) " You have $freezes banked." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp)
                ) { Text("Continue →") }
            }
        }
    }
}
