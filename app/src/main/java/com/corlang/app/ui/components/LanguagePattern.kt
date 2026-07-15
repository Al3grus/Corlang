package com.corlang.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/*
 * Barely-there cultural texture, one motif per course, drawn in code (no assets):
 *   hr — šahovnica checkerboard, pt — a nau under sail, fr — Deco sunburst lines.
 * Same theme everywhere; the pattern is what quietly tells you which course you're in.
 * Keep alpha low: this is texture, not decoration.
 */

/** Path helpers so motif geometry can be written in Offsets instead of loose x/y floats. */
private fun Path.moveTo(o: Offset) = moveTo(o.x, o.y)
private fun Path.lineTo(o: Offset) = lineTo(o.x, o.y)
private fun Path.quadTo(control: Offset, end: Offset) = quadraticTo(control.x, control.y, end.x, end.y)

/**
 * Draws the language's motif behind the content. [tint] is usually the card's content color.
 * Stroke-drawn motifs (nau, Deco rays) get a higher base alpha than the solid chequer —
 * thin outlines read far fainter than filled squares at the same opacity.
 */
fun Modifier.languagePattern(lang: String, tint: Color): Modifier =
    drawBehind {
        when (lang) {
            "pt" -> drawNau(tint.copy(alpha = 0.14f))
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
 * pt: a nau under sail — the Portuguese carrack that carried the Age of Discovery across the
 * oceans. Drawn at a three-quarter bow angle (neither dead-on nor flat side): billowing square
 * sails, a high stern castle turned just enough to show its quarter, a bowsprit reaching toward
 * the viewer, and the Cross of the Order of Christ on the mainsail. Anchored to the right so it
 * sails out of the card while the text stays clear on the left.
 */
private fun DrawScope.drawNau(color: Color) {
    val s = size.height / 3.6f
    val c = Offset(size.width - s * 1.6f, size.height * 0.74f)
    fun pt(x: Float, y: Float) = Offset(c.x + x * s, c.y + y * s)

    val stroke = Stroke(width = 2.2f)
    val thin = Stroke(width = 1.3f)
    val hair = Stroke(width = 1.0f)
    val soft = color.copy(alpha = color.alpha * 0.5f)
    val faint = color.copy(alpha = color.alpha * 0.35f)
    val fill = color.copy(alpha = color.alpha * 0.20f)

    // A billowing square sail: a yard across the top, the canvas bulging down and to leeward
    // (right, toward the viewer) — the bulge is what sells the three-quarter angle.
    fun sail(xL: Float, yTL: Float, xR: Float, yTR: Float, drop: Float, belly: Float): Path {
        val blx = xL + belly * 0.20f; val bly = yTL + drop
        val brx = xR + belly * 0.20f; val bry = yTR + drop
        val midTop = (xL + xR) / 2f
        val midBot = (blx + brx) / 2f
        return Path().apply {
            moveTo(pt(xL, yTL))
            quadTo(pt(midTop, (yTL + yTR) / 2f + belly * 0.10f), pt(xR, yTR))        // head (slight sag)
            quadTo(pt(xR + belly, (yTR + bry) / 2f + belly * 0.15f), pt(brx, bry))   // leech bulging out
            quadTo(pt(midBot + belly * 0.25f, (bly + bry) / 2f + belly * 0.95f), pt(blx, bly)) // foot
            quadTo(pt(xL + belly * 0.45f, (yTL + bly) / 2f + belly * 0.15f), pt(xL, yTL))       // luff
            close()
        }
    }

    // ---- Sea: a few fading swells beneath the hull ----
    for (i in 0..2) {
        val wy = 0.46f + i * 0.14f
        val amp = 0.055f - i * 0.012f
        val wave = Path()
        var x = -1.5f
        var first = true
        while (x <= 1.7f) {
            val o = pt(x, wy + amp * kotlin.math.sin((x * 2.6f + i).toDouble()).toFloat())
            if (first) { wave.moveTo(o.x, o.y); first = false } else wave.lineTo(o.x, o.y)
            x += 0.18f
        }
        drawPath(wave, faint.copy(alpha = faint.alpha * (1f - i * 0.25f)), style = thin)
    }

    // ---- Hull: crescent carrack hull, soft-filled for body then outlined ----
    val hull = Path().apply {
        moveTo(pt(1.42f, -0.62f))                          // prow tip
        quadTo(pt(1.15f, -0.42f), pt(1.02f, -0.30f))       // down onto the fore deck
        quadTo(pt(0.55f, -0.22f), pt(-0.05f, -0.16f))      // sheer, amidships
        quadTo(pt(-0.70f, -0.20f), pt(-1.20f, -0.55f))     // up to the stern rail
        quadTo(pt(-1.34f, -0.18f), pt(-1.02f, 0.18f))      // stern post down to the water
        quadTo(pt(-0.55f, 0.55f), pt(0.05f, 0.62f))        // belly of the keel
        quadTo(pt(0.66f, 0.54f), pt(1.05f, 0.22f))         // up toward the bow
        quadTo(pt(1.34f, -0.02f), pt(1.42f, -0.62f))       // bow stem back to the prow
        close()
    }
    drawPath(hull, fill)
    drawPath(hull, color, style = stroke)

    // Wale line + a row of gunports along the flank.
    val wale = Path().apply {
        moveTo(pt(1.16f, -0.26f))
        quadTo(pt(0.5f, 0.0f), pt(-0.05f, 0.04f))
        quadTo(pt(-0.7f, 0.02f), pt(-1.04f, -0.18f))
    }
    drawPath(wale, soft, style = thin)
    for (gx in listOf(-0.75f, -0.4f, -0.05f, 0.3f, 0.65f)) {
        drawCircle(soft, s * 0.03f, pt(gx, -0.02f))
    }

    // Far gunwale + deck beams: the open deck seen from just above the near rail (depth cue).
    val farRail = Path().apply {
        moveTo(pt(-0.55f, -0.32f)); quadTo(pt(-0.05f, -0.28f), pt(0.6f, -0.34f))
    }
    drawPath(farRail, faint, style = thin)
    for (bx in listOf(-0.4f, 0.1f, 0.5f)) {
        drawLine(faint, pt(bx, -0.18f), pt(bx, -0.30f), strokeWidth = 1.0f)
    }

    // ---- Stern castle (raised aft) with its quarter turned toward us ----
    val sternCastle = Path().apply {
        moveTo(pt(-0.66f, -0.26f)); lineTo(pt(-0.72f, -0.82f))
        quadTo(pt(-0.96f, -0.94f), pt(-1.18f, -0.84f)); lineTo(pt(-1.22f, -0.52f))
    }
    drawPath(sternCastle, color, style = thin)
    val sternQuarter = Path().apply {
        moveTo(pt(-1.18f, -0.84f)); lineTo(pt(-1.32f, -0.66f))
        lineTo(pt(-1.34f, -0.36f)); lineTo(pt(-1.22f, -0.52f))
    }
    drawPath(sternQuarter, soft, style = thin)
    drawLine(soft, pt(-0.72f, -0.62f), pt(-1.20f, -0.64f), strokeWidth = 1.0f)

    // ---- Forecastle (raised bow) beneath the prow ----
    val foreCastle = Path().apply {
        moveTo(pt(0.98f, -0.28f)); lineTo(pt(1.0f, -0.58f))
        quadTo(pt(1.2f, -0.66f), pt(1.38f, -0.56f)); lineTo(pt(1.42f, -0.30f))
    }
    drawPath(foreCastle, color, style = thin)

    // ---- Spars: bowsprit and three masts ----
    drawLine(color, pt(1.30f, -0.46f), pt(1.95f, -0.86f), strokeWidth = 2.0f)   // bowsprit
    drawLine(color, pt(0.05f, -0.18f), pt(0.05f, -2.06f), strokeWidth = 2.2f)   // mainmast
    drawLine(color, pt(0.95f, -0.30f), pt(0.95f, -1.56f), strokeWidth = 2.0f)   // foremast
    drawLine(color, pt(-0.75f, -0.30f), pt(-0.60f, -1.34f), strokeWidth = 2.0f) // mizzenmast

    // ---- Standing rigging (drawn under the canvas) ----
    drawLine(faint, pt(0.05f, -2.02f), pt(1.92f, -0.84f), strokeWidth = 1.0f)   // fore-topstay to bowsprit
    drawLine(faint, pt(0.05f, -2.02f), pt(-1.18f, -0.80f), strokeWidth = 1.0f)  // main backstay to stern
    drawLine(faint, pt(0.95f, -1.52f), pt(1.9f, -0.84f), strokeWidth = 1.0f)    // foremast stay to bowsprit
    drawLine(faint, pt(-0.60f, -1.30f), pt(-1.18f, -0.62f), strokeWidth = 1.0f) // mizzen stay to stern
    // A few shrouds fanning down to the rails.
    for (d in listOf(-0.18f, 0.0f, 0.18f)) {
        drawLine(faint, pt(0.05f, -1.9f), pt(0.05f + d, -0.2f), strokeWidth = 0.9f)
    }

    // ---- Yards ----
    drawLine(color, pt(-0.58f, -0.92f), pt(0.68f, -0.98f), strokeWidth = 1.6f)  // main course yard
    drawLine(color, pt(-0.37f, -1.52f), pt(0.47f, -1.57f), strokeWidth = 1.4f)  // main topsail yard
    drawLine(color, pt(0.53f, -0.75f), pt(1.37f, -0.80f), strokeWidth = 1.4f)   // fore course yard

    // ---- Canvas ----
    val mainCourse = sail(-0.58f, -0.92f, 0.68f, -0.98f, 0.64f, 0.20f)
    val mainTop = sail(-0.37f, -1.52f, 0.47f, -1.57f, 0.42f, 0.13f)
    val foreCourse = sail(0.53f, -0.75f, 1.37f, -0.80f, 0.52f, 0.16f)
    drawPath(mainCourse, fill); drawPath(foreCourse, fill); drawPath(mainTop, fill)

    // Cross of the Order of Christ on the mainsail — the emblem the discovery fleets carried.
    val cx = 0.05f; val cy = -0.62f; val k = 0.22f
    val crossPts = listOf(
        -0.16f to -0.42f, -0.42f to -1.0f, 0.42f to -1.0f, 0.16f to -0.42f,
        0.42f to -0.16f, 1.0f to -0.42f, 1.0f to 0.42f, 0.42f to 0.16f,
        0.16f to 0.42f, 0.42f to 1.0f, -0.42f to 1.0f, -0.16f to 0.42f,
        -0.42f to 0.16f, -1.0f to 0.42f, -1.0f to -0.42f, -0.42f to -0.16f
    )
    val cross = Path().apply {
        crossPts.forEachIndexed { i, (px, py) ->
            val o = pt(cx + px * k, cy + py * k)
            if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
        }
        close()
    }
    drawPath(cross, soft)
    drawPath(cross, color, style = thin)

    drawPath(mainCourse, color, style = thin)
    drawPath(mainTop, color, style = thin)
    drawPath(foreCourse, color, style = thin)

    // Mizzen: a lateen (triangular) sail on the slanted after yard.
    drawLine(color, pt(-0.90f, -0.34f), pt(-0.42f, -1.30f), strokeWidth = 1.4f)  // lateen yard
    val lateen = Path().apply {
        moveTo(pt(-0.42f, -1.30f))
        quadTo(pt(-0.56f, -0.78f), pt(-0.88f, -0.34f))     // luff, down to the tack
        quadTo(pt(-1.04f, -0.44f), pt(-1.14f, -0.56f))     // foot
        quadTo(pt(-0.78f, -0.88f), pt(-0.42f, -1.30f))     // leech billowing back to the peak
        close()
    }
    drawPath(lateen, fill); drawPath(lateen, color, style = thin)

    // Spritsail slung beneath the bowsprit.
    val sprit = sail(1.44f, -0.58f, 1.82f, -0.74f, 0.30f, 0.10f)
    drawPath(sprit, fill); drawPath(sprit, color, style = hair)

    // ---- Pennants streaming forward from each masthead ----
    fun pennant(x: Float, y: Float, len: Float): Path = Path().apply {
        moveTo(pt(x, y))
        quadTo(pt(x + len * 0.5f, y - 0.06f), pt(x + len, y - 0.02f))
        quadTo(pt(x + len * 0.5f, y + 0.03f), pt(x, y + 0.05f))
        close()
    }
    drawPath(pennant(0.05f, -2.06f, 0.5f), soft)
    drawPath(pennant(0.95f, -1.56f, 0.38f), soft)
    drawPath(pennant(-0.42f, -1.30f, 0.32f), soft)
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
