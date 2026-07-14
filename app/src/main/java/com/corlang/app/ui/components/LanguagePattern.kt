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

/**
 * pt: azulejo — two large traditional tiles anchored right. Each tile is the classic
 * Hispano-Moorish composition: a framed square, an eight-pointed star (two overlapping
 * squares, one rotated 45°), a four-petal rosette at the heart, and quarter-circle corner
 * arcs that hint at the infinite repeating pattern azulejo walls are famous for.
 */
private fun DrawScope.drawAzulejo(color: Color) {
    val half = size.height * 0.40f
    val cy = size.height / 2f
    val tiles = listOf(
        Offset(size.width - half * 1.25f, cy) to 1f,
        Offset(size.width - half * 3.65f, cy) to 0.5f   // second tile sits back, fainter
    )
    tiles.forEach { (c, fade) -> drawAzulejoTile(c, half, color.copy(alpha = color.alpha * fade)) }
}

private fun DrawScope.drawAzulejoTile(c: Offset, half: Float, col: Color) {
    val stroke = Stroke(width = 2.5f)
    val thin = Stroke(width = 1.4f)

    // Tile frame + inner frame.
    drawRect(col, Offset(c.x - half, c.y - half), Size(half * 2, half * 2), style = stroke)
    val inset = half * 0.86f
    drawRect(col, Offset(c.x - inset, c.y - inset), Size(inset * 2, inset * 2), style = thin)

    // Eight-pointed star: an axis-aligned square and the same square rotated 45°.
    val a = half * 0.46f
    drawRect(col, Offset(c.x - a, c.y - a), Size(a * 2, a * 2), style = stroke)
    val v = a * 1.4142f
    drawPath(
        androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x, c.y - v); lineTo(c.x + v, c.y); lineTo(c.x, c.y + v); lineTo(c.x - v, c.y); close()
        },
        col, style = stroke
    )

    // Four-petal rosette at the heart: leaf-shaped petals along the axes, softly filled.
    val petalLen = a * 0.85f
    val petalW = a * 0.34f
    val petalFill = col.copy(alpha = col.alpha * 0.55f)
    listOf(0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f).forEach { (dx, dy) ->
        val tip = Offset(c.x + dx * petalLen, c.y + dy * petalLen)
        // Perpendicular direction for the petal's waist.
        val px = dy * petalW
        val py = dx * petalW
        val petal = androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x, c.y)
            quadraticTo(c.x + dx * petalLen * 0.5f - px, c.y + dy * petalLen * 0.5f - py, tip.x, tip.y)
            quadraticTo(c.x + dx * petalLen * 0.5f + px, c.y + dy * petalLen * 0.5f + py, c.x, c.y)
            close()
        }
        drawPath(petal, petalFill)
        drawPath(petal, col, style = thin)
    }
    drawCircle(col, radius = half * 0.07f, center = c)

    // Corner quarter-arcs: each tile corner carries a quarter circle facing inward —
    // on a real wall four neighbouring tiles complete these into full rosettes.
    val arcR = half * 0.34f
    listOf(
        Offset(c.x - half, c.y - half) to 0f,     // top-left corner: sweep 0..90
        Offset(c.x + half, c.y - half) to 90f,    // top-right: 90..180
        Offset(c.x + half, c.y + half) to 180f,   // bottom-right: 180..270
        Offset(c.x - half, c.y + half) to 270f    // bottom-left: 270..360
    ).forEach { (corner, start) ->
        drawArc(
            color = col,
            startAngle = start,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(corner.x - arcR, corner.y - arcR),
            size = Size(arcR * 2, arcR * 2),
            style = thin
        )
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
