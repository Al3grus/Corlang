package com.corlang.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.data.isLearned
import com.corlang.app.data.isMastered
import com.corlang.app.ui.components.Bullet
import com.corlang.app.ui.components.SectionTitle
import com.corlang.app.ui.components.StatTile
import com.corlang.app.ui.navigation.Dest
import com.corlang.app.ui.theme.Radius
import kotlinx.coroutines.launch

/**
 * Profile, organised into bands so it leads with identity and progress instead of a wall:
 *   You (name), Progress (streak/days/level + vocab stats), Assessment (practice, exam
 *   readiness, can-do), and Reference (CEFR ladder, the Pareto insight, resources) — the last
 *   group collapsed by default since it's browse-when-you-want material.
 */
@Composable
fun ProgressScreen(
    container: AppContainer,
    lang: String,
    onNavigate: (String) -> Unit = {}
) {
    val meta = remember(lang) { container.content.meta(lang) }
    val levels = remember(lang) { container.content.levels(lang).levels }

    val progress by container.progress.progress(lang).collectAsState(initial = null)
    val rawDaysDone by container.progress.completedDayCount(lang)
        .collectAsState(initial = null)
    val rawReviews by container.words.reviews(lang).collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    // Load-then-show (same rule as Today): without the gate every tab entry painted one frame
    // of "0 day streak / 0 days done / A0" stat tiles before Room emitted.
    if (progress == null || rawDaysDone == null || rawReviews == null) {
        Column(Modifier.fillMaxSize()) {}
        return
    }
    val daysDone = rawDaysDone ?: 0
    val reviews = rawReviews.orEmpty()

    val currentLevel = progress?.currentLevel ?: "A0"
    val streak = com.corlang.app.data.ProgressRepository.displayStreak(
        streak = progress?.streak ?: 0,
        lastStudiedEpochDay = progress?.lastStudiedEpochDay ?: 0L,
        freezes = progress?.streakFreezes ?: 0,
        today = com.corlang.app.data.WordsRepository.todayEpochDay()
    )
    // "Started" = introduced at all; "learned" = memory durable (stability ≥ 7d); "mastered" =
    // long interval (stability ≥ 21d). Thresholds live in Fsrs.
    val wordsStarted = reviews.size
    val wordsLearned = reviews.count { it.isLearned }
    val wordsMastered = reviews.count { it.isMastered }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        // ---- You ----
        Text("${meta.flagEmoji} ${meta.name}",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Your progress — kept on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp))

        // ---- Progress ----
        Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("$streak", "day streak", Modifier.weight(1f))
            StatTile("$daysDone", "days done", Modifier.weight(1f))
            StatTile(currentLevel, "current level", Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("$wordsStarted", "words started", Modifier.weight(1f))
            StatTile("$wordsLearned", "words learned", Modifier.weight(1f))
            StatTile("$wordsMastered", "words mastered", Modifier.weight(1f))
        }

        // ---- Assessment ----
        Spacer(Modifier.height(22.dp))
        SectionTitle("Assessment")
        OutlinedButton(
            onClick = { onNavigate(Dest.PRACTICE.route) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)
        ) { Text("Practice: quizzes & mock exam →") }

        // Exam readiness + can-do, folded into ONE collapsible. The Practice button above already
        // opens the mock exam, so this is reference detail: per-section pass status and the CEFR
        // self-check — no need for a second always-open box repeating the same call to action.
        val examLevel = levels.firstOrNull { it.exam != null }
        val examSpec = remember(lang, examLevel?.id) {
            container.content.exams(lang).firstOrNull { it.levelId == examLevel?.id }
        }
        if (examLevel?.exam != null) {
            val exam = examLevel.exam!!
            CollapsibleCard("Exam readiness · ${examLevel.id}") {
                Text(exam.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
                            s.title + ", " + when {
                                a == null -> "not attempted"
                                a.passed -> "passed ✓"
                                s.passPercent != null && a.total > 0 ->
                                    "${a.score * 100 / a.total}% (need ${s.passPercent}%)"
                                else -> "not passed yet"
                            }
                        )
                    }
                }
                if (examLevel.skills.isNotEmpty()) {
                    val checks by container.progress.canDoChecks(lang, examLevel.id)
                        .collectAsState(initial = emptyList())
                    val checkedIds = checks.map { it.itemId }.toSet()
                    Text("Can-do self-check (CEFR descriptors)",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp))
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

        // ---- Level map ---- (where you are on the CEFR ladder; reference library is in Profile)
        Spacer(Modifier.height(22.dp))
        SectionTitle("Your level")

        CollapsibleCard("CEFR ladder & milestones") {
            levels.forEach { level ->
                val isCurrent = level.id == currentLevel
                Surface(
                    shape = RoundedCornerShape(Radius.md),
                    color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
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
                        Text("Milestone: ${level.milestone}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp))
                        level.canDo.forEach { Bullet(it) }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * A section that opens on tap — used for the long "Reference" material so Profile leads with
 * identity and progress instead of a wall. Bordered surface to match the app's card style;
 * collapsed by default.
 */
@Composable
private fun CollapsibleCard(title: String, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

