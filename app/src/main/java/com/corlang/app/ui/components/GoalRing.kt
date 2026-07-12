package com.corlang.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.corlang.app.ui.Haptics
import com.corlang.app.ui.theme.Motion
import com.corlang.app.ui.theme.rememberReducedMotion

/**
 * The daily goal ring: closes as today's due words get cleared. [progress] in 0..1.
 * Center shows a label (e.g. "12 left" or "✓"). When it reaches 1.0 during use — the moment
 * the daily goal closes — it gives a single scale-pulse + haptic to mark the win. Entering a
 * screen already-complete does not pulse (only the crossing does).
 */
@Composable
fun GoalRing(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    stroke: Dp = 8.dp
) {
    val reduced = rememberReducedMotion()
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (reduced) snap() else tween(durationMillis = 500),
        label = "goal-ring"
    )
    val ringColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    // One-time completion celebration: pulse + haptic on the <1 → 1 transition only.
    val context = LocalContext.current
    val pulse = remember { Animatable(1f) }
    var wasComplete by remember { mutableStateOf(progress >= 1f) }
    LaunchedEffect(progress) {
        val nowComplete = progress >= 1f
        if (nowComplete && !wasComplete) {
            Haptics.confirm(context)
            if (!reduced) {
                pulse.animateTo(1.08f, Motion.settle())
                pulse.animateTo(1f, Motion.settle())
            }
        }
        wasComplete = nowComplete
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(strokePx / 2, strokePx / 2)
            drawArc(
                color = trackColor,
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            if (animated > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f, sweepAngle = 360f * animated, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
