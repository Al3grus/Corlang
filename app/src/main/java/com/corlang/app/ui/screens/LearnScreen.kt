package com.corlang.app.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.ui.theme.rememberReducedMotion

/**
 * Learn = the reference half of the app: the one-page Cheatsheet, the full source-anchored
 * Grammar syllabus, and the Feynman Teach-back loop, switched with a segmented control.
 */
@Composable
fun LearnScreen(container: AppContainer, lang: String) {
    var tab by rememberSaveable(lang) { mutableIntStateOf(0) }
    val labels = listOf("Cheatsheet", "Grammar", "Teach", "Talk")

    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            labels.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = tab == i,
                    onClick = { tab = i },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = labels.size),
                    icon = {}   // the fill color marks the selection; no checkmark
                ) {
                    // Never wrap: four labels share the row; "Cheatsheet" would fold to two
                    // lines on narrow phones / large font scale and make the row uneven.
                    Text(label, maxLines = 1, softWrap = false)
                }
            }
        }
        // Peers, not a sequence → a fade (no directional slide) as you switch reference views.
        val reduced = rememberReducedMotion()
        Crossfade(
            targetState = tab,
            animationSpec = if (reduced) snap() else tween(durationMillis = 220),
            label = "learn-tab"
        ) { t ->
            when (t) {
                0 -> CheatsheetScreen(container, lang)
                1 -> GrammarScreen(container, lang)
                2 -> TeachScreen(container, lang)
                else -> TalkScreen(container, lang)
            }
        }
    }
}
