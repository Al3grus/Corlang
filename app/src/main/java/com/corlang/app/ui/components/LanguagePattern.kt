package com.corlang.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/*
 * Barely-there cultural texture, one motif per course, drawn in code (no assets):
 *   hr — šahovnica checkerboard, pt — azulejo diamond lattice, fr — Deco sunburst lines.
 * Same theme everywhere; the pattern is what quietly tells you which course you're in.
 * Keep alpha low: this is texture, not decoration.
 */

/**
 * Draws the language's motif behind the content. [tint] is usually the card's content color.
 * Stroke-drawn motifs (azulejo, Deco rays) get a higher base alpha than the solid chequer —
 * thin outlines read far fainter than filled squares at the same opacity.
 */
fun Modifier.languagePattern(lang: String, tint: Color): Modifier =
    drawBehind {
        when (lang) {
            "pt" -> drawAzulejo(tint.copy(alpha = 0.14f))
            "fr" -> drawDecoRays(tint.copy(alpha = 0.14f))
            else -> drawChequer(tint.copy(alpha = 0.05f))
        }
    }

/** hr: šahovnica — a sparse checkerboard fading in from the right edge. */
private fun DrawScope.drawChequer(color: Color) {
    val cell = 26f
    val cols = (size.width / cell).toInt() + 1
    val rows = (size.height / cell).toInt() + 1
    for (r in 0..rows) {
        for (col in 0..cols) {
            if ((r + col) % 2 == 0) continue
            // Fade toward the left so text areas stay clean.
            val x = col * cell
            val fade = (x / size.width).coerceIn(0f, 1f)
            if (fade < 0.45f) continue
            drawRect(
                color = color.copy(alpha = color.alpha * fade),
                topLeft = Offset(x, r * cell),
                size = Size(cell, cell)
            )
        }
    }
}

/** pt: azulejo — a diamond lattice, denser toward the right edge. */
private fun DrawScope.drawAzulejo(color: Color) {
    val step = 34f
    val half = step / 2f
    val stroke = Stroke(width = 2.2f)
    var y = 0f
    while (y < size.height + step) {
        var x = 0f
        var odd = ((y / step).toInt() % 2 == 1)
        while (x < size.width + step) {
            val cx = x + if (odd) half else 0f
            val fade = (cx / size.width).coerceIn(0f, 1f)
            if (fade >= 0.4f) {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx, y - half)
                    lineTo(cx + half, y)
                    lineTo(cx, y + half)
                    lineTo(cx - half, y)
                    close()
                }
                drawPath(p, color.copy(alpha = color.alpha * fade), style = stroke)
            }
            x += step
        }
        y += half
        odd = !odd
    }
}

/** fr: Art-Deco rays fanning from the bottom-right corner. */
private fun DrawScope.drawDecoRays(color: Color) {
    val origin = Offset(size.width * 1.05f, size.height * 1.2f)
    val rays = 9
    for (i in 0 until rays) {
        val angle = Math.toRadians(180.0 + i * (75.0 / (rays - 1)) + 15.0)
        val dirX = kotlin.math.cos(angle).toFloat()
        val dirY = kotlin.math.sin(angle).toFloat()
        val reach = size.width * 0.75f
        drawLine(
            color = color,
            start = origin + Offset(dirX * size.width * 0.12f, dirY * size.width * 0.12f),
            end = origin + Offset(dirX * reach, dirY * reach),
            strokeWidth = if (i % 2 == 0) 3.5f else 1.8f
        )
    }
}
