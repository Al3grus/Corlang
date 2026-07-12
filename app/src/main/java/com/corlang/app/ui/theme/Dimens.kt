package com.corlang.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * One deliberate scale for corner radius and spacing, so surfaces of the same kind look the same
 * everywhere. Before this, radii of 8/10/12/16 all coexisted for identical elements — the kind of
 * drift that makes a minimalist UI read as unfinished rather than considered. Prefer these over
 * inline `.dp` literals for card/row shapes and section spacing.
 */
object Radius {
    val sm = 8.dp    // chips, tokens, inline diagram boxes
    val md = 12.dp   // option rows, stat tiles, list cards
    val lg = 16.dp   // hero/step cards, flashcards
}

object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}
