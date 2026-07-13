package com.corlang.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corlang.app.ui.theme.rememberReducedMotion

/**
 * The daily goal ring: closes as today's work is cleared. [progress] in 0..1; the fill tweens
 * smoothly. Center shows a label (e.g. "12 left" or "✓").
 *
 * Note: no completion haptic/pulse here. The flows that drive [progress] start below 1 and emit
 * their real value on every recomposition, so a "just completed" pulse couldn't be told apart
 * from simply opening an already-complete day — it fired on every tab switch. The genuine
 * day-completion buzz already happens when you tap "Mark day complete".
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

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
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
            // Sized from the ring, not the system font scale alone: "100%" at accessibility
            // font sizes must stay inside the ring's hole instead of painting over the arcs.
            fontSize = (size.value * 0.20f).sp,
            maxLines = 1,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
