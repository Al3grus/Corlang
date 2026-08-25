package com.corlang.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Top bar: brand mark, wordmark, and the streak chip. Switching languages and Settings both live
 * on the Profile tab.
 *
 * The current language's flag used to sit on the right as a static indicator. It was removed
 * because it read as a button: a lone icon in the top-right corner is where every app puts an
 * action, so people tapped it and nothing happened. The streak chip now holds that corner and it
 * IS an action — it opens the streak sheet — so the corner finally does what it looks like it does.
 * The language is still named (next to its flag) on Profile, Progress and the cheatsheet.
 *
 * [streak] < 0 hides the chip entirely (progress not loaded yet), so the bar never flashes a
 * placeholder "0" before the real number arrives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorlangTopBar(
    streak: Int = -1,
    streakLit: Boolean = false,
    onStreakClick: () -> Unit = {}
) {
    // Dark-only app: the bar sits on surface so it stays quiet against the ink background.
    val barColor = MaterialTheme.colorScheme.surface
    val onBar = MaterialTheme.colorScheme.onSurface

    TopAppBar(
        title = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                CorlangLogo(variant = LogoVariant.ORBIT, size = 26.dp, brand = onBar)
                // Past ~1.2x font scale the wordmark wrapped and broke the bar, logo alone then.
                if (androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.2f) {
                    Text(
                        "Corlang",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        actions = {
            if (streak >= 0) {
                StreakChip(streak = streak, lit = streakLit, onClick = onStreakClick)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = barColor,
            titleContentColor = onBar
        )
    )
}
