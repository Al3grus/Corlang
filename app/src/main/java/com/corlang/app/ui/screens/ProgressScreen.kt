package com.corlang.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.ui.components.Bullet
import com.corlang.app.ui.components.InfoCard
import com.corlang.app.ui.components.SectionTitle

/**
 * Progress hub: streak + completion stats, the CEFR ladder A0→C1 with the milestone for each
 * level (current level highlighted), the top-5 resources, and recent quiz history.
 */
@Composable
fun ProgressScreen(container: AppContainer, lang: String) {
    val meta = remember(lang) { container.content.meta(lang) }
    val levels = remember(lang) { container.content.levels(lang).levels }
    val plan = remember(lang) { container.content.plan(lang) }
    val resources = remember(lang) { container.content.resources(lang).resources.sortedBy { it.rank } }

    val progress by container.progress.progress(lang).collectAsState(initial = null)
    val daysDone by container.progress.completedDayCount(lang).collectAsState(initial = 0)
    val quizAttempts by container.progress.quizAttempts(lang).collectAsState(initial = emptyList())

    val currentLevel = progress?.currentLevel ?: "A0"
    val streak = progress?.streak ?: 0
    val currentDay = progress?.currentDay ?: 1

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("${meta.flagEmoji} ${meta.name} progress",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // Stats row
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("🔥 $streak", "day streak", Modifier.weight(1f))
            StatTile("📅 $daysDone", "days done", Modifier.weight(1f))
            StatTile(currentLevel, "current level", Modifier.weight(1f))
        }
        Text(
            "On day $currentDay of ${plan.days.size} in the plan.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Pareto summary
        InfoCard {
            SectionTitle("⚡ The 20% that drives 80%")
            Text(meta.paretoSummary, style = MaterialTheme.typography.bodyMedium)
        }

        // CEFR ladder
        SectionTitle("🪜 CEFR ladder & milestones")
        Text(
            "C2 is intentionally excluded — it's effectively unreachable for most non-native learners.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        levels.forEach { level ->
            val isCurrent = level.id == currentLevel
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(level.id, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium)
                        Text(level.title, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium)
                        if (isCurrent) Text("• you are here",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium)
                    }
                    Text("🏁 Milestone: ${level.milestone}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp))
                    level.canDo.forEach { Bullet(it) }
                }
            }
        }

        // Resources
        SectionTitle("⭐ Top 5 resources to learn ${meta.name} fast")
        resources.forEach { r ->
            InfoCard {
                Text("${r.rank}. ${r.name}",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(r.type.replaceFirstChar { it.uppercase() } +
                    (r.url?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text(r.why, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }

        // Recent quiz history
        if (quizAttempts.isNotEmpty()) {
            SectionTitle("📝 Recent quiz attempts")
            quizAttempts.take(8).forEach { a ->
                Text("• ${a.quizId}: ${a.score}/${a.total}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp))
            }
        }

        Text("", modifier = Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
