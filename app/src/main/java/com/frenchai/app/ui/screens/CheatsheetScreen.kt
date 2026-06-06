package com.frenchai.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frenchai.app.AppContainer
import com.frenchai.app.ui.components.Bullet
import com.frenchai.app.ui.components.DiagramBox
import com.frenchai.app.ui.components.ExampleLine
import com.frenchai.app.ui.components.InfoCard

/**
 * The single-page key-concepts cheatsheet — designed to be reviewed in ~5 minutes.
 * Renders bullets, monospace diagrams, and worked examples per section.
 */
@Composable
fun CheatsheetScreen(container: AppContainer, lang: String) {
    val sheet = remember(lang) { container.content.cheatsheet(lang) }
    val meta = remember(lang) { container.content.meta(lang) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(sheet.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "5-minute review · ${meta.flagEmoji} ${meta.name}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        sheet.sections.forEach { section ->
            InfoCard {
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                section.bullets.forEach { Bullet(it) }
                section.diagram?.let { DiagramBox(it) }
                if (section.examples.isNotEmpty()) {
                    Text(
                        "Examples",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                    section.examples.forEach { ExampleLine(it.target, it.gloss) }
                }
            }
        }
    }
}
