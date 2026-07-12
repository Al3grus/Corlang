package com.corlang.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.data.WordsRepository
import com.corlang.app.ui.components.GoalRing
import com.corlang.app.ui.components.InfoCard
import com.corlang.app.ui.components.SectionTitle
import com.corlang.app.ui.navigation.Dest
import kotlinx.coroutines.launch

/**
 * The Lesson tab = one button. It lands on the day after your last completed one, shows the
 * streak and the next action, and "Start lesson" hands over to the guided SessionPlayer,
 * which walks through every task of the day step by step. No loose checklists, the app leads.
 */
@Composable
fun TodayScreen(
    container: AppContainer,
    lang: String,
    onNavigate: (String) -> Unit = {}
) {
    val plan = remember(lang) { container.content.plan(lang) }
    val progress by container.progress.progress(lang).collectAsState(initial = null)
    val completed by container.progress.completedDays(lang).collectAsState(initial = emptyList())

    val currentDay = progress?.currentDay ?: 1
    val streak = progress?.streak ?: 0
    val freezes = progress?.streakFreezes ?: 0

    // Live due count for the hero ('today' computed fresh, no stale midnight state).
    val reviews by container.words.reviews(lang).collectAsState(initial = emptyList())
    val today = WordsRepository.todayEpochDay()
    val dueNow = reviews.count { it.dueEpochDay <= today }

    // The lesson to land on = the day AFTER your last completed one (also covers doing several
    // days at once). Robust even if the stored currentDay lags behind completions.
    val lastCompleted = completed.maxOrNull() ?: 0
    val targetDay = maxOf(currentDay, lastCompleted + 1).coerceIn(1, plan.days.size)

    // Which day is being viewed (defaults to the target; user can browse away with ‹ ›).
    var viewedDay by remember(lang) { mutableStateOf(targetDay) }
    var userBrowsed by remember(lang) { mutableStateOf(false) }
    LaunchedEffect(targetDay) {
        if (!userBrowsed) viewedDay = targetDay
    }

    val day = plan.days.firstOrNull { it.day == viewedDay } ?: plan.days.first()
    val isDone = completed.contains(day.day)

    // Guided session mode.
    var inPlayer by rememberSaveable(lang) { mutableStateOf(false) }
    if (inPlayer) {
        SessionPlayer(
            container = container,
            lang = lang,
            day = day,
            totalDays = plan.days.size,
            onNavigate = onNavigate,
            onExit = { inPlayer = false }
        )
        return
    }

    // Session progress for the viewed day (steps ticked in the player).
    val resourceUrls = remember(lang) {
        container.content.resources(lang).resources.associate { it.name to it.url }
    }
    val steps = remember(day.day) { buildSessionSteps(day, resourceUrls) }
    val checks by container.progress.dayTaskChecks(lang, day.day)
        .collectAsState(initial = emptyList())
    val doneIds = checks.map { it.itemId }.toSet()
    val actionSteps = steps.filter { it.kind != StepKind.INFO && it.kind != StepKind.COMPLETE }
    val stepsDone = actionSteps.count {
        it.id in doneIds || (it.kind == StepKind.WORDS && dueNow == 0)
    }

    // Daily goal ring: today's guided session, measured on the day you're up to (not the one
    // being browsed). Closes fully once a lesson day has been completed today and stays closed.
    val startOfToday = remember {
        java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
    }
    val completedToday by container.progress.completionsSince(lang, startOfToday)
        .collectAsState(initial = 0)
    val targetDayObj = plan.days.firstOrNull { it.day == targetDay } ?: day
    val targetSteps = remember(targetDay) { buildSessionSteps(targetDayObj, resourceUrls) }
    val targetChecks by container.progress.dayTaskChecks(lang, targetDay)
        .collectAsState(initial = emptyList())
    val targetDoneIds = targetChecks.map { it.itemId }.toSet()
    val targetAction = targetSteps.filter { it.kind != StepKind.INFO && it.kind != StepKind.COMPLETE }
    val targetStepsDone = targetAction.count {
        it.id in targetDoneIds || (it.kind == StepKind.WORDS && dueNow == 0)
    }
    val ringProgress = when {
        completedToday > 0 -> 1f
        targetAction.isEmpty() -> 0f
        else -> targetStepsDone.toFloat() / targetAction.size
    }

    // One even rhythm between the page's blocks so nothing clusters at the top.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Hero: streak + daily goal ring + ONE unmissable next action.
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        buildString {
                            append(
                                if (streak > 0) "🔥 $streak-day streak"
                                else "🔥 Start your streak today"
                            )
                            if (freezes > 0) append("  ·  ❄️ $freezes freeze${if (freezes > 1) "s" else ""}")
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    // Only speak up when there's something worth saying; no filler line.
                    val heroSubtitle = when {
                        completedToday > 0 -> "Today's goal is done ✓. Anything more is bonus depth."
                        dueNow > 0 -> "Starts with your $dueNow due words."
                        else -> ""
                    }
                    if (heroSubtitle.isNotBlank()) {
                        Text(
                            heroSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    // The single next best action. The lesson itself opens with the due words,
                    // so before it's done we always just start the lesson, no words-first detour.
                    Button(
                        onClick = {
                            if (completedToday > 0) onNavigate(Dest.WORDS.route)
                            else { viewedDay = targetDay; userBrowsed = false; inPlayer = true }
                        },
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        Text(
                            when {
                                completedToday > 0 && dueNow > 0 -> "Review $dueNow words →"
                                completedToday > 0 -> "Learn extra words →"
                                targetStepsDone > 0 -> "Continue Day $targetDay →"
                                else -> "Start Day $targetDay →"
                            }
                        )
                    }
                }
                GoalRing(
                    progress = ringProgress,
                    label = if (ringProgress >= 1f) "✓"
                            else "${(ringProgress * 100).toInt()}%",
                    size = 64.dp,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }

        // Day navigator (browse the plan). Lands on your current lesson; ‹ › to look around.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { if (viewedDay > 1) { viewedDay--; userBrowsed = true } },
                    enabled = viewedDay > 1
                ) { Text("‹") }
                Text(
                    "Day ${day.day} / ${plan.days.size}" + if (isDone) "  ✓" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = { if (viewedDay < plan.days.size) { viewedDay++; userBrowsed = true } },
                    enabled = viewedDay < plan.days.size
                ) { Text("›") }
            }
            // If the learner browsed away, one tap returns to the day they're up to.
            if (viewedDay != targetDay) {
                Text(
                    "↩ Back to your current lesson (Day $targetDay)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewedDay = targetDay; userBrowsed = false }
                )
            }
        }

        // Lesson header + objective.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${day.phase} · Week ${day.week} · ${day.level}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                day.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            InfoCard {
                SectionTitle("🎯 In this lesson you will")
                Text(day.objective, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
        }

        // Days must be done in order: a future day is locked until you finish the current one.
        val locked = day.day > targetDay
        if (locked) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "🔒 Locked. Finish Day $targetDay first, no skipping ahead.",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Button(
                onClick = { inPlayer = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        isDone -> "Day done ✓, revisit the lesson"
                        stepsDone > 0 -> "Continue lesson ($stepsDone/${actionSteps.size} steps done)"
                        else -> "Start lesson →"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }

        // The stepping-stones map: scroll your level's lessons, jump to any you've reached,
        // switch between completed levels to review.
        LevelJourney(
            plan = plan,
            completed = completed,
            targetDay = targetDay,
            viewedDay = viewedDay,
            onPickDay = { d -> viewedDay = d; userBrowsed = true }
        )

        Spacer(Modifier.height(8.dp))
    }
}
