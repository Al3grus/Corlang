package com.corlang.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The single source of truth for the Corlang mark, ported from the design handoff
 * (design_handoff_corlang_loader/CorlangLogo.jsx, rev 2). Use this everywhere the brand
 * appears so a future color/shape change happens in one place. The mark is always the
 * two-ring "Orbit Core" (outer r=33 broken ring at -52deg, inner r=21 arc anchored right,
 * r=9 core), the same mark the loader resolves into and the "o" in the wordmark.
 */
enum class LogoVariant {
    /** The two-ring mark alone. Default brand icon: chrome, nav, small marks. */
    ORBIT,
    /** Icon + "Corlang" wordmark side by side. Headers, About, marketing. */
    LOCKUP,
    /** "Corlang" with the mark as the "o", no leading icon. Tight spaces. */
    WORDMARK,
}

/** Brand ring blue and molten-core red, the two fixed brand colors. */
val CorlangBrand = Color(0xFF2F7FAE)
val CorlangCore = Color(0xFFC8402C)

@Composable
fun CorlangLogo(
    variant: LogoVariant = LogoVariant.ORBIT,
    size: Dp = 40.dp,
    brand: Color = CorlangBrand,
    core: Color = CorlangCore,
    ink: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    when (variant) {
        LogoVariant.ORBIT -> Canvas(modifier.size(size)) { drawOrbit(brand, core) }
        LogoVariant.WORDMARK -> Wordmark(size, brand, core, ink, modifier)
        LogoVariant.LOCKUP -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(size)) { drawOrbit(brand, core) }
            Spacer(Modifier.width(size * 0.3f))
            Text("Corlang", style = wordmarkTextStyle(size, ink))
        }
    }
}

/** "Corlang" with the Orbit Core mark standing in for the "o" (rev 2: no solid-O form). */
@Composable
private fun Wordmark(size: Dp, brand: Color, core: Color, ink: Color, modifier: Modifier) {
    val oSize = size * 0.9f
    val style = wordmarkTextStyle(size, ink)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("C", style = style)
        Canvas(Modifier.size(oSize)) { drawOrbit(brand, core) }
        Text("rlang", style = style)
    }
}

private fun wordmarkTextStyle(size: Dp, ink: Color): TextStyle {
    val fontSize = size.value * 1.22f
    return TextStyle(
        color = ink,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize.sp,
        letterSpacing = (fontSize * -0.02f).sp,
    )
}

/** Two broken rings (r=33 outer rotated -52, r=21 inner rotated 128) + core, in a 100 viewBox. */
private fun DrawScope.drawOrbit(brand: Color, core: Color) {
    val s = size.minDimension
    val c = center
    // Broken-ring gaps: outer shows 132/2pi.33 = 63.66% -> 229.2deg; inner 80/2pi.21 = 60.63% -> 218.3deg.
    drawRingArc(brand, c, radius = 0.33f * s, stroke = 0.06f * s, startDeg = -52f, sweepDeg = 229.2f)
    drawRingArc(brand, c, radius = 0.21f * s, stroke = 0.06f * s, startDeg = 128f, sweepDeg = 218.3f)
    drawCircle(core, radius = 0.09f * s, center = c)
}

private fun DrawScope.drawRingArc(
    color: Color, center: Offset, radius: Float, stroke: Float, startDeg: Float, sweepDeg: Float
) {
    drawArc(
        color = color,
        startAngle = startDeg,
        sweepAngle = sweepDeg,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}

/**
 * Branded in-app loading indicator: the mark's outer broken ring orbiting its core, so every
 * loading moment speaks the loader's motion language. Determinate progress uses the loader
 * (CorlangSplash); this is the indeterminate everyday spinner.
 */
@Composable
fun CorlangRingSpinner(
    size: Dp = 24.dp,
    brand: Color = CorlangBrand,
    core: Color = CorlangCore,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "corlangSpinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "angle",
    )
    Canvas(modifier.size(size)) {
        val s = size.toPx()
        val c = center
        drawCircle(core, radius = 0.09f * s, center = c)
        rotate(angle, c) {
            // The mark's own outer arc (229.2deg of r=33) doing one clean orbit.
            drawRingArc(brand, c, radius = 0.33f * s, stroke = 0.06f * s, startDeg = -52f, sweepDeg = 229.2f)
        }
    }
}
