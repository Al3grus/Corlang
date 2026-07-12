package com.corlang.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.data.model.CheatSection
import com.corlang.app.speech.TtsManager
import com.corlang.app.ui.components.Bullet
import com.corlang.app.ui.components.DiagramBox
import com.corlang.app.ui.components.ExampleLine
import com.corlang.app.ui.components.InfoCard

/**
 * The single-page key-concepts cheatsheet. Each section is a collapsible card (like Grammar) so the
 * page opens as a scannable table of contents instead of one long scroll; tap a section to expand.
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
            "5-minute review · ${meta.flagEmoji} ${meta.name} · tap a section to open",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // First section starts open so the page isn't a wall of collapsed headers.
        sheet.sections.forEachIndexed { i, section ->
            CheatsheetSectionCard(section, container.tts, initiallyExpanded = i == 0)
        }
    }
}

@Composable
private fun CheatsheetSectionCard(
    section: CheatSection,
    tts: TtsManager,
    initiallyExpanded: Boolean
) {
    var expanded by rememberSaveable(section.title) { mutableStateOf(initiallyExpanded) }
    InfoCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
        ) {
            Text(
                section.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand"
            )
        }
        if (expanded) {
            section.bullets.forEach { Bullet(it) }
            section.diagram?.let { DiagramBox(it) }
            if (section.examples.isNotEmpty()) {
                Text(
                    "Examples",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
                section.examples.forEach { ExampleLine(it.target, it.gloss, tts) }
            }
        }
    }
}
