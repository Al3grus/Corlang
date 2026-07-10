package com.corlang.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.ui.components.Bullet
import com.corlang.app.ui.components.InfoCard
import com.corlang.app.ui.components.SectionTitle
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

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

        // Exam readiness: the B1 milestone with official sections + can-do self-checklist.
        val examLevel = levels.firstOrNull { it.exam != null }
        val examSpec = remember(lang, examLevel?.id) {
            container.content.exams(lang).firstOrNull { it.levelId == examLevel?.id }
        }
        if (examLevel?.exam != null) {
            val exam = examLevel.exam!!
            SectionTitle("🎓 Exam readiness — ${examLevel.id}")
            InfoCard {
                Text(exam.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(exam.passRule, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
                if (examSpec != null) {
                    val latest by container.progress.latestExamAttempts(lang, examSpec.id)
                        .collectAsState(initial = emptyList())
                    val bySection = latest.associateBy { it.sectionId }
                    examSpec.sections.forEach { s ->
                        val a = bySection[s.id]
                        Bullet(
                            s.title + " — " + when {
                                a == null -> "not attempted"
                                a.passed -> "passed ✓"
                                s.passPercent != null && a.total > 0 ->
                                    "${a.score * 100 / a.total}% (need ${s.passPercent}%)"
                                else -> "not passed yet"
                            }
                        )
                    }
                    Text("Take the mock exam in the Quiz tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (examLevel.skills.isNotEmpty()) {
                InfoCard {
                    Text("Can-do self-check (official CEFR descriptors)",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    val checks by container.progress.canDoChecks(lang, examLevel.id)
                        .collectAsState(initial = emptyList())
                    val checkedIds = checks.map { it.itemId }.toSet()
                    examLevel.skills.forEachIndexed { si, skill ->
                        Text(skill.skill, style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 6.dp))
                        skill.descriptors.forEachIndexed { di, d ->
                            val itemId = "$si-$di"
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = itemId in checkedIds,
                                    onCheckedChange = { on ->
                                        scope.launch {
                                            container.progress.setCanDo(lang, examLevel.id, itemId, on)
                                        }
                                    }
                                )
                                Text(d, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }

        // Resources
        SectionTitle("⭐ The best resources to learn ${meta.name} fast")
        val uriHandler = LocalUriHandler.current
        resources.forEach { r ->
            // Whole card is the tap target (48dp+); show the domain, not a wrapping raw URL.
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable(enabled = r.url != null) { r.url?.let { uriHandler.openUri(it) } }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("${r.rank}. ${r.name}",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        r.type.replaceFirstChar { it.uppercase() } +
                            (r.url?.let { url ->
                                val domain = url.removePrefix("https://").removePrefix("http://")
                                    .substringBefore('/')
                                " · $domain ↗"
                            } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(r.why, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp))
                }
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
            // Single line: "🔥 365" must not wrap and make the 3 tiles uneven on narrow screens.
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
