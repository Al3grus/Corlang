package com.corlang.app.ui.screens

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.ui.components.Bullet
import com.corlang.app.ui.components.InfoCard
import kotlinx.coroutines.launch

/**
 * Exam readiness checkpoint, opened from the journey between the level quiz and the mock exam
 * flag: the official exam's pass rule, your latest per-section results, and the CEFR can-do
 * self-check — everything to judge whether you're ready before sitting the mock.
 */
@Composable
fun ReadinessScreen(container: AppContainer, lang: String, levelId: String, onExit: () -> Unit) {
    val examLevel = remember(lang, levelId) {
        container.content.levels(lang).levels.firstOrNull { it.id == levelId && it.exam != null }
    }
    val examSpec = remember(lang, levelId) {
        container.content.exams(lang).firstOrNull { it.levelId == levelId }
    }
    val scope = rememberCoroutineScope()

    androidx.activity.compose.BackHandler { onExit() }

    Column(Modifier.fillMaxSize()) {
        // Header matches the Profile sub-pages: bare back arrow + bold title, 16dp bands.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 16.dp, end = 16.dp)
        ) {
            IconButton(onClick = onExit) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Exam readiness · $levelId",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))

        Box(Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                val exam = examLevel?.exam
                if (exam == null) {
                    Text("No readiness data for this level yet.")
                    OutlinedButton(
                        onClick = onExit,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    ) { Text("Back") }
                    return@Column
                }

                InfoCard {
                    Text(exam.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text(exam.passRule, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp))
                }

                if (examSpec != null) {
                    Text("Mock exam sections, latest attempts",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
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
                    Text(
                        "Attempt the sections from the mock exam, the flag right after this " +
                            "checkpoint on your journey.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                if (examLevel.skills.isNotEmpty()) {
                    Text("Can-do self-check (CEFR descriptors)",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp))
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

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
