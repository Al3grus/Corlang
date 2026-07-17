package com.corlang.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The daily goal ring: closes as today's work is cleared. [progress] in 0..1; the fill SNAPS
 * to its value. Center shows a label (e.g. "12 left" or "✓").
 *
 * No fill tween: the flows driving [progress] emit null/0 → real on every fresh composition, so
 * a tween replayed the fill on every return to the tab (a distracting entrance-animation loop).
 * Progress only genuinely changes INSIDE a lesson, not while the dashboard is on screen, so the
 * dashboard ring settling instantly reads as calm and static; the real "goal complete" delight
 * is the celebration overlay. No completion haptic here for the same "can't tell settle from
 * completion" reason — the buzz happens on "Mark day complete".
 */
@Composable
fun GoalRing(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    stroke: Dp = 8.dp
) {
    val animated = progress.coerceIn(0f, 1f)
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
            // Sized from the ring and COMPENSATED for the system font scale: "100%" must stay
            // inside the ring's hole at accessibility font sizes, not paint over the arcs.
            fontSize = (size.value * 0.20f / LocalDensity.current.fontScale).sp,
            maxLines = 1,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
