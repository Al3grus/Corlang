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
import androidx.compose.ui.unit.sp
import com.corlang.app.data.model.LanguageMeta

/**
 * Top bar: brand mark on the left, the current language's flag (static) on the right. Switching
 * languages and Settings both live on the Profile tab now — the bar is a quiet header, not a
 * control surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageTopBar(
    languages: List<LanguageMeta>,
    selected: String
) {
    val current = languages.firstOrNull { it.code == selected } ?: languages.firstOrNull()

    // Dark-only app: the bar sits on surface so it stays quiet against the ink background.
    val barColor = MaterialTheme.colorScheme.surface
    val onBar = MaterialTheme.colorScheme.onSurface

    TopAppBar(
        title = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                CorlangLogo(variant = LogoVariant.ORBIT, size = 26.dp, brand = onBar)
                // Past ~1.2× font scale the wordmark wrapped and broke the bar — logo alone then.
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
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = barColor,
            titleContentColor = onBar,
            actionIconContentColor = onBar
        ),
        actions = {
            // Static indicator of the current language (switching is on the Profile tab).
            Text(
                current?.flagEmoji ?: "",
                fontSize = 22.sp,
                modifier = Modifier.padding(end = 20.dp)
            )
        }
    )
}
