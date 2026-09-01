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
import com.corlang.app.ui.theme.rememberReducedMotion
import androidx.compose.ui.draw.alpha

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
    /**
     * True when a level's assessments are behind the paywall. Separate from "not reached
     * yet" because the two have different answers and different remedies: one is fixed by
     * studying, the other by buying.
     */
    levelLocked: (String) -> Boolean = { false },
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
    // The chips follow the lesson being VIEWED, not the one you are up to. Browsing back to
    // lesson 9 from lesson 19 crosses a level boundary, and keying this on targetDay left the
    // A1 chip selected while the card showed an A0 lesson, so the stone for it was not even in
    // the row. Tapping a chip still overrides, until the viewed lesson changes level again.
    val viewedLevel = remember(viewedDay, plan) {
        plan.days.firstOrNull { it.day == viewedDay }?.level ?: currentLevel
    }
    var selectedLevel by rememberSaveable(viewedLevel) { mutableStateOf(viewedLevel) }

    Column {
        // Its own heading rather than the shared SectionTitle, which carries 8dp of top padding
        // for use INSIDE a card. Out here the page already supplies the gap above, and that
        // extra 8 made the space above "Your journey" visibly wider than every other gap on
        // the Learn tab.
        Text(
            "Your journey",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Level selector chips.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            levelGroups.forEach { entry ->
                val level = entry.key
                val days = entry.value
                val minDay = days.minOf { it.day }
                // "Reached" now means reachable: far enough along AND paid for. A chip the
                // learner cannot open should not look like one they can.
                val reached = minDay <= targetDay && !levelLocked(level)
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
        // Tapping another level chip swapped the whole path in one frame. The stones and the
        // caption under them fade in on every change of level instead; the chips themselves stay
        // put, so the row you tapped is the one fixed thing while its contents change.
        val levelAlpha = com.corlang.app.ui.theme.rememberAppearAlpha(selectedLevel)
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
        // Centre on what the card is showing. This used to centre on targetDay, so leaving a
        // revisited lesson scrolled the path back to today while the card stayed on the older
        // lesson, and the two disagreed about where the learner was.
        LaunchedEffect(viewedDay, selectedLevel, stones.size) {
            val idx = stones.indexOfFirst { it.day == viewedDay }
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
                .alpha(levelAlpha)
                .horizontalScroll(journeyScroll)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            stones.forEachIndexed { i, d ->
                val done = d.day in completedSet
                val current = d.day == targetDay
                val viewed = d.day == viewedDay
                val locked = d.day > targetDay && !done
                /*
                 * Filled means HERE, ringed means done, flat means not yet.
                 *
                 * The three states used to be told apart by fill alone, and the light palette
                 * cannot do that: done (primary walnut) and current (tertiary ochre) are 12
                 * degrees of hue and 1.17:1 apart, which is invisible as two circles side by
                 * side. Brightening one only moved the problem, because anything pale enough to
                 * separate from walnut lands on the pale sand the unstarted stones use.
                 *
                 * So the separation is shape now, not hue. It also puts the emphasis the right
                 * way round: a finished lesson should recede and the one you are on should
                 * dominate, where before the completed stones were the loudest thing on screen.
                 */
                val bg = when {
                    current -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val fg = when {
                    current -> MaterialTheme.colorScheme.onPrimary
                    done -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                // One ring at most. Browsing to a completed stone would otherwise draw the
                // viewed ring and the done ring on the same circle; viewed wins, because it
                // answers "which one am I looking at", which is the more urgent question.
                val ring = when {
                    viewed -> MaterialTheme.colorScheme.onSurface
                    done && !current -> MaterialTheme.colorScheme.primary
                    else -> null
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
                            if (ring != null) Modifier.border(2.dp, ring, CircleShape)
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
            // ...or once placement has put the learner PAST the level entirely. A learner placed
            // at B1 was told to skip A1 and A2, so gating those levels' quiz and mock exam behind
            // lessons the app itself said not to do locked them out of the very assessments they
            // were placed high enough to sit. Placing past a level counts as clearing it.
            //
            // But PROGRESS is not the only gate. `placedPast` made the free placement test a
            // paywall bypass: a learner placed at B1 was "past" A1 and A2, which handed them
            // both levels' quizzes, readiness checks and full mock exams without a purchase.
            // Payment is checked separately and last, because it is the one condition studying
            // cannot satisfy.
            //
            // The `>` is strict on purpose for every level but the last, where it cannot be
            // satisfied at all: nothing lies beyond the final lesson, so a learner whom the
            // placement test placed AT the end of the course was never "past" B1 and its mock
            // exam stayed shut. That is the one assessment a topped-out learner came for, and
            // the screen was telling them so while locking it. Standing on the course's final
            // lesson counts as past everything; the paywall check below is unaffected.
            val lastPlanDay = plan.days.maxOfOrNull { it.day } ?: 0
            val placedPast = stones.isNotEmpty() &&
                (targetDay > stones.maxOf { it.day } || targetDay >= lastPlanDay)
            val levelDone = stones.isNotEmpty() &&
                (stones.all { it.day in completedSet } || placedPast) &&
                !levelLocked(selectedLevel)
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
            modifier = Modifier.alpha(levelAlpha).padding(top = 6.dp)
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
 * One end-of-level checkpoint stone, speaking the same three states as the lesson stones:
 * filled means take this now, ringed means taken, flat means not yet.
 *
 * These were the last of the ochre. An unlocked checkpoint was tertiary, which is the yellowest
 * thing in the light palette and did not match anything left on the path after the stones were
 * reworked. It is filled walnut now, the same as the lesson you are on, which is what it is:
 * the next thing to do.
 *
 * Unlocked deliberately does NOT become the flat grey of a not-yet stone, even though that
 * would also kill the yellow. Locked is already that grey, and two states that look identical
 * would leave no way to see which checkpoints are open to you
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
                    unlocked && !done -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                CircleShape
            )
            .then(
                if (done) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                else Modifier
            )
            .then(if (unlocked) Modifier.clickable { onClick() } else Modifier)
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = when {
                unlocked && !done -> MaterialTheme.colorScheme.onPrimary
                done -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(22.dp)
        )
    }
}
