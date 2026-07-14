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
    val stroke = Stroke(width = 2.2f)
    val thin = Stroke(width = 1.3f)
    val soft = col.copy(alpha = col.alpha * 0.45f)
    fun polar(r: Float, deg: Float) = Offset(
        c.x + r * kotlin.math.cos(Math.toRadians(deg.toDouble())).toFloat(),
        c.y + r * kotlin.math.sin(Math.toRadians(deg.toDouble())).toFloat()
    )

    // ---- Frames ----
    drawRect(col, Offset(c.x - half, c.y - half), Size(half * 2, half * 2), style = stroke)
    val inset = half * 0.90f
    drawRect(col, Offset(c.x - inset, c.y - inset), Size(inset * 2, inset * 2), style = thin)

    // ---- Corner fans: two concentric quarter-arcs + radial ticks (a fan that four
    //      neighbouring tiles would complete into a full rosette) ----
    val fanR1 = half * 0.26f
    val fanR2 = half * 0.36f
    listOf(
        Offset(c.x - half, c.y - half) to 0f,
        Offset(c.x + half, c.y - half) to 90f,
        Offset(c.x + half, c.y + half) to 180f,
        Offset(c.x - half, c.y + half) to 270f
    ).forEach { (corner, start) ->
        for (r in listOf(fanR1, fanR2)) {
            drawArc(col, start, 90f, false, Offset(corner.x - r, corner.y - r), Size(r * 2, r * 2), style = thin)
        }
        for (k in 0..3) {
            val deg = Math.toRadians((start + 11.25f + k * 22.5f).toDouble())
            val dx = kotlin.math.cos(deg).toFloat(); val dy = kotlin.math.sin(deg).toFloat()
            drawLine(col, Offset(corner.x + dx * fanR1, corner.y + dy * fanR1),
                Offset(corner.x + dx * fanR2, corner.y + dy * fanR2), strokeWidth = 1.3f)
        }
    }

    // ---- Edge buds at each side's midpoint: nested half-circles + a dot; they join
    //      across the seam with the neighbouring tile ----
    val budR = half * 0.15f
    listOf(
        Offset(c.x, c.y - half) to 0f,      // top edge, bud opens downward
        Offset(c.x + half, c.y) to 90f,     // right edge, opens left
        Offset(c.x, c.y + half) to 180f,    // bottom edge, opens up
        Offset(c.x - half, c.y) to 270f     // left edge, opens right
    ).forEach { (m, start) ->
        for (r in listOf(budR, budR * 0.55f)) {
            drawArc(col, start, 180f, false, Offset(m.x - r, m.y - r), Size(r * 2, r * 2), style = thin)
        }
    }

    // ---- Foliage scrolls: S-curled spirals on the four diagonals (the arabesque touch) ----
    for (diag in listOf(45f, 135f, 225f, 315f)) {
        val base = polar(half * 0.72f, diag)
        val spiral = androidx.compose.ui.graphics.Path()
        var first = true
        val steps = 26
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val r = half * 0.14f * (1f - t * 0.78f)
            val ang = Math.toRadians((diag + 180f + t * 480f).toDouble())
            val p = Offset(base.x + r * kotlin.math.cos(ang).toFloat(),
                           base.y + r * kotlin.math.sin(ang).toFloat())
            if (first) { spiral.moveTo(p.x, p.y); first = false } else spiral.lineTo(p.x, p.y)
        }
        drawPath(spiral, col, style = thin)
    }

    // ---- Central medallion: scalloped ring around a circle ----
    val ringR = half * 0.56f
    drawCircle(col, ringR, c, style = thin)
    val lobes = 12
    for (i in 0 until lobes) {
        val a0 = i * (360f / lobes)
        val mid = polar(ringR * 1.13f, a0 + 360f / lobes / 2f)
        val p0 = polar(ringR, a0)
        val p1 = polar(ringR, a0 + 360f / lobes)
        val lobe = androidx.compose.ui.graphics.Path().apply {
            moveTo(p0.x, p0.y); quadraticTo(mid.x, mid.y, p1.x, p1.y)
        }
        drawPath(lobe, col, style = thin)
    }

    // ---- Eight-pointed star inside the medallion ----
    val a = half * 0.36f
    drawRect(col, Offset(c.x - a, c.y - a), Size(a * 2, a * 2), style = stroke)
    val v = a * 1.4142f
    drawPath(
        androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x, c.y - v); lineTo(c.x + v, c.y); lineTo(c.x, c.y + v); lineTo(c.x - v, c.y); close()
        },
        col, style = stroke
    )

    // ---- Eight-petal flower at the heart: leaves on axes AND diagonals, softly filled ----
    val petalLen = a * 0.86f
    val petalW = a * 0.30f
    for (deg in listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)) {
        val dirX = kotlin.math.cos(Math.toRadians(deg.toDouble())).toFloat()
        val dirY = kotlin.math.sin(Math.toRadians(deg.toDouble())).toFloat()
        val tip = Offset(c.x + dirX * petalLen, c.y + dirY * petalLen)
        val px = dirY * petalW; val py = dirX * petalW
        val midX = c.x + dirX * petalLen * 0.5f; val midY = c.y + dirY * petalLen * 0.5f
        val petal = androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x, c.y)
            quadraticTo(midX - px, midY - py, tip.x, tip.y)
            quadraticTo(midX + px, midY + py, c.x, c.y)
            close()
        }
        if (deg % 90f == 0f) drawPath(petal, soft)   // axis petals filled, diagonals outlined
        drawPath(petal, col, style = thin)
    }
    drawCircle(col, half * 0.06f, c)
    drawCircle(col, half * 0.11f, c, style = thin)
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
