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
import com.corlang.app.data.Fsrs
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
    val newPerDay by container.languagePrefs.newWordsPerDay.collectAsState(initial = 10)
    val today = WordsRepository.todayEpochDay()
    val dueNow = reviews.count { it.dueEpochDay <= today }

    // The lesson to land on = the day AFTER your last completed one (also covers doing several
    // days at once). Robust even if the stored currentDay lags behind completions.
    val lastCompleted = completed.maxOrNull() ?: 0
    val targetDay = maxOf(currentDay, lastCompleted + 1).coerceIn(1, plan.days.size)

    // The current lesson's word load, split like the lesson's two steps: NEW words this lesson
    // unlocks (deck order, first targetDay * perLesson, not yet introduced), and the REVIEW load
    // capped at REVIEW_CAP. Lesson-scoped, so an earlier day never marks a later day done, and a
    // fresh lesson reads 0% until you actually do its words.
    val allWords = remember(lang) { container.words.allWords(lang) }
    val seenIds = remember(reviews) { reviews.map { it.wordId }.toSet() }
    val unlockedNew = allWords.take(targetDay * newPerDay).count { it.id !in seenIds }
    val reviewPending = minOf(dueNow, Fsrs.REVIEW_CAP)

    // Which day is being viewed (defaults to the target; user can browse away with ‹ ›).
    // Saveable alongside inPlayer: after process death mid-"revisit an old day", the restored
    // player must show the same day, not silently swap to the current one.
    var viewedDay by rememberSaveable(lang) { mutableStateOf(targetDay) }
    var userBrowsed by rememberSaveable(lang) { mutableStateOf(false) }
    LaunchedEffect(targetDay) {
        if (!userBrowsed) viewedDay = targetDay
    }

    val day = plan.days.firstOrNull { it.day == viewedDay } ?: plan.days.first()
    val isDone = completed.contains(day.day)

    // Guided session mode.
    var inPlayer by rememberSaveable(lang) { mutableStateOf(false) }
    if (inPlayer) {
        // System back leaves the player (progress is persisted per step), not the app.
        androidx.activity.compose.BackHandler { inPlayer = false }
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
    // Keyed on lang TOO: both languages can sit on the same day number, and the cached step
    // list must not survive a language switch.
    val steps = remember(lang, day.day) {
        buildSessionSteps(day, resourceUrls, container.content.meta(lang).name)
    }
    val checks by container.progress.dayTaskChecks(lang, day.day)
        .collectAsState(initial = emptyList())
    val doneIds = checks.map { it.itemId }.toSet()
    val actionSteps = steps.filter { it.kind != StepKind.INFO && it.kind != StepKind.COMPLETE }
    // "Started" = you actually completed a step of THIS lesson (a persisted check), so opening the
    // lesson or a shared-word state never turns "Start" into "Continue".
    val lessonStarted = doneIds.isNotEmpty()
    val stepsDone = actionSteps.count { s ->
        // Words/review count when this day's block was completed, OR — only once the lesson has
        // been started — when the block is already cleared (capped blocks can leave a backlog
        // behind). Without the started gate a fresh course showed phantom progress: zero due
        // reviews auto-ticked the review step before the learner did anything.
        when (s.kind) {
            StepKind.WORDS -> s.id in doneIds || (lessonStarted && unlockedNew == 0)
            StepKind.REVIEW -> s.id in doneIds || (lessonStarted && reviewPending == 0)
            else -> s.id in doneIds
        }
    }

    // Daily goal ring: today's guided session, measured on the day you're up to (not the one
    // being browsed). Closes fully once a lesson day has been completed today and stays closed.
    // Keyed on the epoch day: an unkeyed remember froze "today" — a process alive across
    // midnight kept counting yesterday's completion as today's and showed the ring done.
    val startOfToday = remember(today) {
        java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
    }
    val completedToday by container.progress.completionsSince(lang, startOfToday)
        .collectAsState(initial = 0)
    val targetDayObj = plan.days.firstOrNull { it.day == targetDay } ?: day
    val targetSteps = remember(lang, targetDay) {
        buildSessionSteps(targetDayObj, resourceUrls, container.content.meta(lang).name)
    }
    val targetChecks by container.progress.dayTaskChecks(lang, targetDay)
        .collectAsState(initial = emptyList())
    val targetDoneIds = targetChecks.map { it.itemId }.toSet()
    val targetAction = targetSteps.filter { it.kind != StepKind.INFO && it.kind != StepKind.COMPLETE }
    val targetStarted = targetDoneIds.isNotEmpty()
    val targetStepsDone = targetAction.count { s ->
        // Same started-gate as above: an untouched lesson must show a 0% ring.
        when (s.kind) {
            StepKind.WORDS -> s.id in targetDoneIds || (targetStarted && unlockedNew == 0)
            StepKind.REVIEW -> s.id in targetDoneIds || (targetStarted && reviewPending == 0)
            else -> s.id in targetDoneIds
        }
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
        // Hero: streak + daily goal ring + ONE unmissable next action. A quiet bordered surface
        // rather than a loud coloured banner — the streak is a calm signal, not a trophy.
        Surface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Grey until today's lesson banks the streak, then lit — and its
                        // colors escalate with streak length (7/30/100 tiers).
                        com.corlang.app.ui.components.StreakFlame(
                            streak = streak,
                            lit = completedToday > 0,
                            size = 20.dp
                        )
                        Text(
                            buildString {
                                append(
                                    if (streak > 0) " $streak-day streak"
                                    else " Start your streak today"
                                )
                                if (freezes > 0) append("  ·  $freezes freeze${if (freezes > 1) "s" else ""}")
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
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
                                targetStarted -> "Continue Day $targetDay →"
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
                SectionTitle("In this lesson you will")
                Text(day.objective, style = com.corlang.app.ui.theme.CorlangType.reading,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
        }

        // The hero already starts today's lesson — so a start button here would just repeat it.
        // Only when you've browsed to a DIFFERENT day (via the map below) do we surface an action
        // for that day; days ahead of the one you're up to stay locked.
        if (day.day != targetDay) {
            val locked = day.day > targetDay
            if (locked) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Locked — finish Day $targetDay first. No skipping ahead.",
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
                            isDone -> "Revisit Day ${day.day} ✓"
                            lessonStarted -> "Continue Day ${day.day} ($stepsDone/${actionSteps.size} steps)"
                            else -> "Open Day ${day.day} →"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
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
