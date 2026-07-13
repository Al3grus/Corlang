package com.corlang.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.corlang.app.data.model.GrammarTopic
import com.corlang.app.ui.components.DiagramBox
import com.corlang.app.ui.components.ExampleLine
import com.corlang.app.ui.components.InfoCard

/**
 * The source-anchored grammar reference: per-level expandable topics with full declension/
 * conjugation tables and spoken examples. This is the "no shortcuts" correctness layer,
 * mapped from the official ASOO curriculum (see docs/sources/asoo-curriculum.md).
 */
@Composable
fun GrammarScreen(container: AppContainer, lang: String) {
    val syllabus = remember(lang) { container.content.grammar(lang) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Grammar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "The complete syllabus per CEFR level, from the official curriculum. " +
                "Tables are reference-grade, no shortcuts.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
        )
        if (syllabus.levels.isEmpty()) {
            Text("No grammar syllabus for this language yet.")
        }
        syllabus.levels.forEach { level ->
            Text(
                "${level.levelId}, ${level.topics.size} topics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp)
            )
            if (level.intro.isNotBlank()) {
                Text(
                    level.intro,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            level.topics.forEach { topic -> GrammarTopicCard(container, topic) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GrammarTopicCard(container: AppContainer, topic: GrammarTopic) {
    var expanded by rememberSaveable(topic.id) { mutableStateOf(false) }
    InfoCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
        ) {
            Text(
                topic.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand"
            )
        }
        if (expanded) {
            Text(
                topic.summary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp)
            )
            topic.tables.forEach { DiagramBox(it) }
            if (topic.examples.isNotEmpty()) {
                Text(
                    "Examples",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
                topic.examples.forEach { ExampleLine(it.target, it.gloss, container.tts) }
            }
        }
    }
}
