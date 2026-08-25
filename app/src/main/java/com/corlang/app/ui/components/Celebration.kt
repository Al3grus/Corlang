package com.corlang.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
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
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * Celebration vocabulary (chess.com-inspired): a streak flame that is grey until today's
 * lesson is banked and whose colors intensify as the streak grows, plus a one-shot confetti
 * burst with an encouraging message when a day is completed.
 */

/**
 * Flame colors for a streak length: outer body, inner core. Tiers escalate like chess.com.
 *
 * Two sets, because a flame is drawn shape-first and owns its own colors — it cannot borrow a
 * Material role. The dark tiers are bright, which is what makes them glow in the dark theme and exactly what
 * makes them vanish in the light theme; the light tiers are the same four steps (ember → orange
 * → blue → gold) pitched darker, so the escalation still reads and the flame still has an edge.
 */
private fun flameTier(streak: Int, dark: Boolean): Pair<Color, Color> = when {
    streak >= 100 ->
        if (dark) Color(0xFFFFD54F) to Color(0xFFFFF9C4)      // gold / white-hot
        else Color(0xFFC79213) to Color(0xFFF0C846)
    streak >= 30 ->
        if (dark) Color(0xFF64B5F6) to Color(0xFFE3F2FD)      // blue flame
        else Color(0xFF2A6FBE) to Color(0xFF7FB2E4)
    streak >= 7 ->
        if (dark) Color(0xFFFF8A50) to Color(0xFFFFE0B2)      // vivid orange
        else Color(0xFFD05A20) to Color(0xFFF0A257)
    else ->
        if (dark) Color(0xFFEF9A6A) to Color(0xFFFFCCBC)      // young ember
        else Color(0xFFB86A44) to Color(0xFFE0A183)
}

/** Not-today-yet grey: cool in the dark theme, warm stone in the light one. */
private fun unlitFlame(dark: Boolean): Pair<Color, Color> =
    if (dark) Color(0xFF5A646D) to Color(0xFF7A848D)
    else Color(0xFFAEA595) to Color(0xFFC7BFB0)

/**
 * The streak flame. [lit] = today's lesson is done (grey otherwise, like chess.com's
 * gray-until-you-play streak).
 *
 * Two motions, because the two places this appears want different things. On Today it is an ICON
 * sitting on screen all day, so it barely moves: one slow oscillator, and none at all until the
 * day is banked. In the completion celebration it IS the reward, so it gets [energetic].
 */
@Composable
fun StreakFlame(
    streak: Int,
    lit: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    energetic: Boolean = false
) {
    val reduced = rememberReducedMotion()
    val dark = com.corlang.app.ui.theme.CorlangColors.isDark
    val (body, core) = if (lit) flameTier(streak, dark) else unlitFlame(dark)
    // The branch is fixed per call site, so each one keeps a stable composition.
    if (energetic) EnergeticFlame(body, core, size, modifier, still = reduced)
    else CalmFlame(body, core, size, modifier, animate = lit && !reduced)
}

/** Today's icon: one slow flicker that narrows the flame and sways the tip. Symmetric on purpose. */
@Composable
private fun CalmFlame(body: Color, core: Color, size: Dp, modifier: Modifier, animate: Boolean) {
    val flicker = if (animate) {
        val transition = rememberInfiniteTransition(label = "flame")
        transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing), repeatMode = RepeatMode.Reverse
            ),
            label = "flicker"
        ).value
    } else 0f

    Box(
        modifier
            .size(size)
            .drawBehind {
                val w = this.size.width
                val h = this.size.height
                val half = w * 0.42f * (1f - flicker * 0.06f)
                val sway = (flicker - 0.5f) * w * 0.05f
                drawPath(
                    flamePath(w / 2f, h * 0.04f, h * 0.96f, half, half, sway, 0.62f, 0.62f),
                    body
                )
                drawPath(
                    flamePath(
                        w / 2f, h * 0.42f, h * 0.90f, half * 0.52f, half * 0.52f,
                        sway * 0.4f, 0.62f, 0.62f
                    ),
                    core
                )
            }
    )
}

/**
 * The celebration flame.
 *
 * The first version of this was a lean-and-squeeze on ONE oscillator, and rendering it frame by
 * frame showed why that never looks like fire: the silhouette is dominated by a fat symmetric
 * base that never changes, so only the tip appears to move and the whole thing reads as a static
 * teardrop being nudged. Fire is asymmetric. So the two sides of the flame are driven
 * INDEPENDENTLY here: left and right each have their own width and their own shoulder height, on
 * oscillators with no shared period, which makes the body writhe rather than lean. The tip
 * stretches on a fast one (flames lick), and the inner core carries a third set again, so it
 * moves inside the body instead of with it.
 *
 * On top of that it IGNITES rather than appearing: a spring from 55% with overshoot plus a
 * white-hot flare decaying over 700ms, so the flare-up is the first thing the eye catches.
 *
 * There are no sparks, and that is a decision rather than an omission. Three versions were
 * rendered frame by frame (rising above the tip, rising through the body, and tiny near-white
 * ones): at this size they read as grit inside the flame or as a wisp of smoke above it, never
 * as embers. The body motion is what makes it look alive; the specks only added noise.
 *
 * [still] is reduced-motion: the same flame, drawn once, at rest.
 */
@Composable
private fun EnergeticFlame(body: Color, core: Color, size: Dp, modifier: Modifier, still: Boolean) {
    val ignite = remember { Animatable(if (still) 1f else 0.55f) }
    val flare = remember { Animatable(if (still) 0f else 1f) }
    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        launch { ignite.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessLow)) }
        flare.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
    }

    val transition = rememberInfiniteTransition(label = "flame")

    @Composable
    fun wave(period: Int, label: String): Float =
        if (still) 0.5f else transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(period, easing = LinearEasing), repeatMode = RepeatMode.Reverse
            ),
            label = label
        ).value

    val widthR = wave(610, "widthR")
    val widthL = wave(970, "widthL")
    val shoulderR = wave(1270, "shoulderR")
    val shoulderL = wave(830, "shoulderL")
    val lick = wave(290, "lick")
    val lean = wave(730, "lean")
    val heart = wave(890, "heart")
    val heartWidth = wave(430, "heartWidth")
    val breath = wave(1470, "breath")
    val flareNow = flare.value
    // White-hot at the moment of ignition, settling into the streak tier colors.
    val hotBody = lerp(body, Color.White, 0.45f * flareNow)
    val hotCore = lerp(core, Color.White, 0.70f * flareNow)

    Box(
        modifier
            .size(size)
            .drawBehind {
                val w = this.size.width
                val h = this.size.height
                val cx = w / 2f
                val scale = ignite.value

                // Inset from the box so the glow has room and the flame can lick upward.
                val bottom = h * 0.90f
                val restTop = h * 0.20f
                val top = bottom - (bottom - restTop) * scale * (0.90f + lick * 0.16f)
                val base = w * 0.25f * scale
                val halfR = base * (0.86f + widthR * 0.30f)
                val halfL = base * (0.86f + widthL * 0.30f)
                val glowAt = Offset(cx, bottom - (bottom - top) * 0.38f)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            hotBody.copy(alpha = 0.34f + breath * 0.16f + flareNow * 0.34f),
                            Color.Transparent
                        ),
                        center = glowAt,
                        radius = w * 0.48f
                    ),
                    radius = w * 0.48f,
                    center = glowAt
                )
                drawPath(
                    flamePath(
                        cx = cx, top = top, bottom = bottom,
                        halfL = halfL, halfR = halfR,
                        tipSway = (lean - 0.5f) * w * 0.15f,
                        // One side bulges high while the other bulges low: that is the writhe.
                        shoulderL = 0.48f + shoulderL * 0.26f,
                        shoulderR = 0.48f + shoulderR * 0.26f
                    ),
                    hotBody
                )
                val span = bottom - top
                val coreHalf = base * 0.46f * (0.80f + heartWidth * 0.36f)
                drawPath(
                    flamePath(
                        cx = cx,
                        top = top + span * (0.30f + heart * 0.16f),
                        bottom = bottom - span * 0.07f,
                        halfL = coreHalf, halfR = coreHalf,
                        tipSway = (heart - 0.5f) * w * 0.09f,
                        shoulderL = 0.55f, shoulderR = 0.55f
                    ),
                    hotCore
                )
            }
    )
}

/**
 * One flame outline. The two sides take SEPARATE half-widths and shoulder heights so a caller
 * can make it asymmetric; passing the same value for both gives the old symmetric teardrop.
 * [shoulderL] / [shoulderR] are where each side reaches its widest, as a fraction of the height.
 */
private fun flamePath(
    cx: Float,
    top: Float,
    bottom: Float,
    halfL: Float,
    halfR: Float,
    tipSway: Float,
    shoulderL: Float,
    shoulderR: Float
) = Path().apply {
    val span = bottom - top
    moveTo(cx + tipSway, top)                                   // tip
    cubicTo(
        cx + halfR * 0.52f, top + span * shoulderR * 0.52f,
        cx + halfR, top + span * shoulderR,
        cx + halfR * 0.70f, bottom - span * 0.12f
    )
    quadraticTo(cx, bottom + span * 0.06f, cx - halfL * 0.70f, bottom - span * 0.12f)
    cubicTo(
        cx - halfL, top + span * shoulderL,
        cx - halfL * 0.52f, top + span * shoulderL * 0.52f,
        cx + tipSway, top
    )
    close()
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
    // The fourth confetti color was a hardcoded mint, invisible in the light theme. The feedback palette's
    // "correct" green is the same color in dark and a legible deep green in light — and it is the
    // right one to celebrate with anyway.
    val colors = listOf(
        scheme.primary,
        scheme.secondary,
        scheme.tertiary,
        com.corlang.app.ui.theme.CorlangColors.feedback.correct
    )

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
    14 -> "Two weeks straight. This is who you are now."
    30 -> "30 days. Your flame burns blue from here. 🔵"
    50 -> "Fifty days of showing up."
    100 -> "100 days, a golden flame for a golden habit. ✨"
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
    /** True when THIS completion grew the freeze bank (a 3/7/14/30 milestone below the cap). */
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
                // Bigger than the Today icon, and energetic: the flame is the reward here,
                // and the extra box is the headroom its sparks and glow are drawn into.
                StreakFlame(streak = streak, lit = true, size = 132.dp, energetic = true)
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
                // Deliberately quiet: one small line under the streak, not a second headline.
                // The day's lesson is the achievement; the freeze is a footnote to it, and the
                // streak sheet is where anyone curious can read what it actually does.
                if (freezeEarned) {
                    Text(
                        "❄️ +1 freeze earned" + if (freezes > 1) " · $freezes banked" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
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
