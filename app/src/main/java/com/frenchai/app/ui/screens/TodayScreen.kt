package com.frenchai.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frenchai.app.AppContainer
import com.frenchai.app.ui.components.Bullet
import com.frenchai.app.ui.components.InfoCard
import com.frenchai.app.ui.components.SectionTitle
import kotlinx.coroutines.launch

/**
 * Today's study session, drawn from the language's day-by-day plan. The learner can browse
 * days, and "Mark complete" records the completion, advances the plan, and updates the streak.
 */
@Composable
fun TodayScreen(container: AppContainer, lang: String) {
    val plan = remember(lang) { container.content.plan(lang) }
    val progress by container.progress.progress(lang).collectAsState(initial = null)
    val completed by container.progress.completedDays(lang).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val currentDay = progress?.currentDay ?: 1
    // Which day is being viewed (defaults to current; user can browse).
    var viewedDay by remember(lang) { mutableStateOf(currentDay) }
    var userBrowsed by remember(lang) { mutableStateOf(false) }
    // Snap to the current day when progress first loads, unless the user has browsed away.
    LaunchedEffect(currentDay) {
        if (!userBrowsed) viewedDay = currentDay
    }

    val day = plan.days.firstOrNull { it.day == viewedDay } ?: plan.days.first()
    val isDone = completed.contains(day.day)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Day navigator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { if (viewedDay > 1) { viewedDay--; userBrowsed = true } },
                enabled = viewedDay > 1
            ) { Text("‹ Prev") }
            Text(
                "Day ${day.day} / ${plan.days.size}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(
                onClick = { if (viewedDay < plan.days.size) { viewedDay++; userBrowsed = true } },
                enabled = viewedDay < plan.days.size
            ) { Text("Next ›") }
        }

        Text(
            "${day.phase} · Week ${day.week} · Level ${day.level}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            day.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        InfoCard {
            SectionTitle("🎯 Objective")
            Text(day.objective, style = MaterialTheme.typography.bodyMedium)
            SectionTitle("⚡ Why this is high-leverage (the 20%)")
            Text(day.paretoFocus, style = MaterialTheme.typography.bodyMedium)
        }

        if (day.drills.isNotEmpty()) {
            InfoCard {
                SectionTitle("✍️ Drills")
                day.drills.forEach { Bullet(it) }
            }
        }

        if (day.resources.isNotEmpty()) {
            InfoCard {
                SectionTitle("📚 Use these resources")
                day.resources.forEach { Bullet(it) }
            }
        }

        // The 15-minute review block, visually highlighted.
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Timer, contentDescription = null)
                    Text(
                        "  ${day.reviewBlock.minutes}-minute review",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "Close every session with this spaced review.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                day.reviewBlock.items.forEach { Bullet(it) }
            }
        }

        Button(
            onClick = {
                scope.launch {
                    container.progress.completeDay(
                        lang = lang,
                        day = day.day,
                        totalDays = plan.days.size,
                        currentLevel = day.level
                    )
                }
            },
            enabled = !isDone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 24.dp)
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null)
            Text(if (isDone) "  Completed ✓" else "  Mark day complete")
        }
    }
}
