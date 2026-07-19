package com.corlang.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.corlang.app.data.model.StudyPlan
import com.corlang.app.ui.components.SectionTitle
import com.corlang.app.ui.theme.rememberReducedMotion

/**
 * A scrollable "stepping stones" map of the plan, grouped by CEFR level. Shows the days of a
 * chosen level as a path, completed days filled, the current day highlighted, future days
 * locked. Completed/current days are tappable to jump the Lesson view there. Reached levels
 * (and only those) are selectable, so finishing a level unlocks the next while past levels
 * stay open for review.
 */
@Composable
fun LevelJourney(
    plan: StudyPlan,
    completed: List<Int>,
    targetDay: Int,
    viewedDay: Int,
    quizLevelIds: Set<String> = emptySet(),
    readinessLevelIds: Set<String> = emptySet(),
    examLevelIds: Set<String> = emptySet(),
    quizDoneLevelIds: Set<String> = emptySet(),
    onOpenQuiz: (String) -> Unit = {},
    onOpenReadiness: (String) -> Unit = {},
    onOpenExam: (String) -> Unit = {},
    onPickDay: (Int) -> Unit
) {
    val completedSet = remember(completed) { completed.toSet() }
    // Insertion-ordered groups: A0, A1, A2, B1.
    val levelGroups = remember(plan) { plan.days.groupBy { it.level }.entries.toList() }
    val currentLevel = remember(targetDay, plan) {
        plan.days.firstOrNull { it.day == targetDay }?.level ?: levelGroups.first().key
    }
    var selectedLevel by rememberSaveable(currentLevel) { mutableStateOf(currentLevel) }

    Column {
        SectionTitle("Your journey")

        // Level selector chips.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            levelGroups.forEach { entry ->
                val level = entry.key
                val days = entry.value
                val minDay = days.minOf { it.day }
                val reached = minDay <= targetDay
                val allDone = days.all { it.day in completedSet }
                val selected = level == selectedLevel
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = when {
                        selected -> MaterialTheme.colorScheme.primary
                        reached -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = when {
                        selected -> MaterialTheme.colorScheme.onPrimary
                        reached -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    // Every chip is tappable, reached or not: peeking at a future level shows
                    // its (locked) lessons and how many there are. Locked stones stay untappable.
                    modifier = Modifier.clickable { selectedLevel = level }
                ) {
                    Text(
                        level + when {
                            allDone -> " ✓"
                            !reached -> " 🔒"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Stones for the selected level.
        val stones = levelGroups.firstOrNull { it.key == selectedLevel }?.value ?: emptyList()
        val doneInLevel = stones.count { it.day in completedSet }

        // Soft breathing pulse on the current day node — draws the eye to "you are here".
        val reduced = rememberReducedMotion()
        val infinite = rememberInfiniteTransition(label = "journey")
        val pulseAnim by infinite.animateFloat(
            initialValue = 1f,
            targetValue = 1.10f,
            animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
            label = "journey-pulse"
        )
        val pulse = if (reduced) 1f else pulseAnim

        // Auto-centre the current day: earlier days stay anchored left (offset coerced ≥ 0), and
        // once the path grows the scroll slides so "you are here" sits mid-screen.
        val journeyScroll = rememberScrollState()
        val density = LocalDensity.current
        val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
        // Snap to centre the first time we land on a level (so a language switch fades in already
        // centred, no visible slide); animate only for later shifts, e.g. finishing a day.
        val positioned = remember(selectedLevel) { mutableStateOf(false) }
        LaunchedEffect(targetDay, selectedLevel, stones.size) {
            val idx = stones.indexOfFirst { it.day == targetDay }
            if (idx >= 0) {
                val stridePx = with(density) { 52.dp.toPx() }      // ~40dp node + 12dp connector
                val startPadPx = with(density) { 8.dp.toPx() }
                val nodeHalfPx = with(density) { 23.dp.toPx() }
                val viewportPx = with(density) { (screenWidthDp - 32).dp.toPx() }
                val nodeCentre = startPadPx + idx * stridePx + nodeHalfPx
                val target = (nodeCentre - viewportPx / 2f).coerceAtLeast(0f).toInt()
                if (positioned.value) journeyScroll.animateScrollTo(target)
                else { journeyScroll.scrollTo(target); positioned.value = true }
            } else {
                // Browsing a level that does NOT contain the current lesson: start at its
                // first stone. Without this the scroll kept the previous level's offset and
                // opened mid-path.
                journeyScroll.scrollTo(0)
                positioned.value = true
            }
        }

        Row(
            // Inner padding (child of the scroll) gives the pulsing current-day node room to
            // expand without being clipped by the scroll container's edges — otherwise day 1,
            // flush against the left edge, gets its left side cut off as it breathes.
            modifier = Modifier
                .horizontalScroll(journeyScroll)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            stones.forEachIndexed { i, d ->
                val done = d.day in completedSet
                val current = d.day == targetDay
                val viewed = d.day == viewedDay
                val locked = d.day > targetDay && !done
                val bg = when {
                    done -> MaterialTheme.colorScheme.primary
                    current -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val fg = when {
                    done -> MaterialTheme.colorScheme.onPrimary
                    current -> MaterialTheme.colorScheme.onTertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(if (current) 46.dp else 40.dp)
                        .then(
                            // zIndex lifts the pulsing node above the connector lines so it
                            // expands ON TOP of the path, not underneath it.
                            if (current) Modifier.zIndex(1f).graphicsLayer { scaleX = pulse; scaleY = pulse }
                            else Modifier
                        )
                        .background(bg, CircleShape)
                        .then(
                            if (viewed) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier
                        )
                        .then(
                            if (!locked) Modifier.clickable { onPickDay(d.day) } else Modifier
                        )
                ) {
                    Text(
                        if (done) "✓" else "${d.day}",
                        style = MaterialTheme.typography.labelMedium,
                        // Compensate the system font scale: a 3-digit day number must stay
                        // inside its fixed 40dp circle even at accessibility font sizes.
                        fontSize = (12f / LocalDensity.current.fontScale).sp,
                        maxLines = 1,
                        fontWeight = FontWeight.Bold,
                        color = fg
                    )
                }
                // Connector: a filled segment between two done days reads as a completed path;
                // otherwise a faint line still ties the stones into a single journey.
                if (i < stones.lastIndex) {
                    val nextDone = stones[i + 1].day in completedSet
                    val connector =
                        if (done && nextDone) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(2.dp)
                            .background(connector)
                    )
                }
            }

            // Checkpoints at the tail of the level's path, in the order you take them:
            // level quiz → exam readiness check → mock exam flag. All unlock together once
            // every lesson in the level is done; each taps through to its own screen.
            val levelDone = stones.isNotEmpty() && stones.all { it.day in completedSet }
            if (stones.isNotEmpty()) {
                if (selectedLevel in quizLevelIds) {
                    CheckpointConnector(levelDone)
                    CheckpointNode(
                        icon = Icons.Filled.QuestionMark,
                        contentDescription = "Level quiz checkpoint",
                        unlocked = levelDone,
                        done = selectedLevel in quizDoneLevelIds,
                        onClick = { onOpenQuiz(selectedLevel) }
                    )
                }
                if (selectedLevel in readinessLevelIds) {
                    CheckpointConnector(levelDone)
                    CheckpointNode(
                        icon = Icons.Filled.CheckBox,
                        contentDescription = "Exam readiness checkpoint",
                        unlocked = levelDone,
                        done = false,
                        onClick = { onOpenReadiness(selectedLevel) }
                    )
                }
                if (selectedLevel in examLevelIds) {
                    CheckpointConnector(levelDone)
                    CheckpointNode(
                        icon = Icons.Filled.Flag,
                        contentDescription = "Level exam checkpoint",
                        unlocked = levelDone,
                        done = false,
                        onClick = { onOpenExam(selectedLevel) }
                    )
                }
            }
        }
        // Locked-state hint only; once unlocked the stones speak for themselves. Worded around
        // the exam, so it only shows for levels that actually END in one (an A0 onramp has just
        // its quiz, and "unlock exam" there would promise something that does not exist).
        val lockedHint = stones.isNotEmpty() && selectedLevel in examLevelIds &&
            !stones.all { it.day in completedSet }
        Text(
            "$selectedLevel · $doneInLevel / ${stones.size} lessons done" +
                (if (lockedHint) "  ·  complete to unlock exam" else ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** Path segment leading into a checkpoint node, filled once the level's lessons are done. */
@Composable
private fun CheckpointConnector(levelDone: Boolean) {
    Box(
        modifier = Modifier
            .width(12.dp)
            .height(2.dp)
            .background(
                if (levelDone) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant
            )
    )
}

/**
 * One end-of-level checkpoint stone. Locked (grey) until the level's lessons are done,
 * unlocked (tertiary) and tappable after, and filled primary once completed at least once
 * (only the quiz tracks a done state for now).
 */
@Composable
private fun CheckpointNode(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    unlocked: Boolean,
    done: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .background(
                when {
                    done -> MaterialTheme.colorScheme.primary
                    unlocked -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                CircleShape
            )
            .then(if (unlocked) Modifier.clickable { onClick() } else Modifier)
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = when {
                done -> MaterialTheme.colorScheme.onPrimary
                unlocked -> MaterialTheme.colorScheme.onTertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(22.dp)
        )
    }
}
