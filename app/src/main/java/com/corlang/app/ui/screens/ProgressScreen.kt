package com.corlang.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.data.MonthHistory.DayState
import com.corlang.app.data.isLearned
import com.corlang.app.data.isMastered
import com.corlang.app.ui.components.SectionTitle
import com.corlang.app.ui.components.StatTile
import com.corlang.app.ui.theme.Radius
import androidx.compose.ui.draw.alpha

/**
 * Progress, organised into bands so it leads with identity and progress instead of a wall:
 *   You (name), Progress (streak/days/level + vocab stats), Course milestones and a month of
 *   history. The CEFR ladder used to close this screen and no longer does: it is reference
 *   material, and the level you are on is already the third stat at the top. Quizzes, exam
 *   readiness, and mock exams live on the journey as end-of-level checkpoints.
 */
@Composable
fun ProgressScreen(
    container: AppContainer,
    lang: String
) {
    val meta = remember(lang) { container.content.meta(lang) }

    val progress by container.progress.progress(lang).collectAsState(initial = null)
    val rawDaysDone by container.progress.completedDayCount(lang)
        .collectAsState(initial = null)
    // For the course milestone bar: lessons per level (plan order) and which are done.
    val plan = remember(lang) { container.content.plan(lang) }
    val completedSet by container.progress.completedDays(lang)
        .collectAsState(initial = emptyList())
    val rawReviews by container.words.reviews(lang).collectAsState(initial = null)

    // Load-then-show (same rule as Today): without the gate every tab entry painted one frame
    // of "0 day streak / 0 days done / A0" stat tiles before Room emitted.
    if (progress == null || rawDaysDone == null || rawReviews == null) {
        Column(Modifier.fillMaxSize()) {}
        return
    }
    val daysDone = rawDaysDone ?: 0
    val reviews = rawReviews.orEmpty()

    // Same floor as the Review tab: the row's default "A0" is not a level pt/fr ever teach,
    // so a fresh learner's tile must show the course's real first level, not the default.
    val planLevels = remember(lang) { plan.days.map { it.level }.distinct() }
    val storedLevel = progress?.currentLevel ?: planLevels.first()
    val currentLevel = if (storedLevel in planLevels) storedLevel else planLevels.first()
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

    // Same as Today: the load gate above blanks the tab until Room emits, so the content has to
    // fade in when it lands or the tab transition is spent on an empty screen.
    val pageAlpha = com.corlang.app.ui.theme.rememberAppearAlpha()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .alpha(pageAlpha)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // The headline carries the bottom padding the removed subtitle used to provide, so the
        // course card does not ride up against it.
        Text("Progress",
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 14.dp))

        // ---- 1. Course card: where you are in this course, in one line ----
        ProgressCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${meta.flagEmoji} ${meta.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                Text(currentLevel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val totalDays = remember(plan) { plan.days.size }
            val fraction = if (totalDays == 0) 0f else daysDone.toFloat() / totalDays
            // Forward only, and never animating backwards: progress that slides back would be
            // reporting a loss the learner did not suffer.
            val shown by androidx.compose.animation.core.animateFloatAsState(
                targetValue = fraction,
                animationSpec = if (com.corlang.app.ui.theme.rememberReducedMotion())
                    androidx.compose.animation.core.snap()
                else androidx.compose.animation.core.tween(400),
                label = "courseProgress"
            )
            androidx.compose.material3.LinearProgressIndicator(
                progress = { shown },
                drawStopIndicator = {},
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 12.dp)
            )
            Text(
                "${(fraction * 100).toInt()}% complete · $daysDone of $totalDays lessons",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // ---- 2. Stat tiles ----
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // "learned", not "known": it is the SAME count the Words tab shows under that name
            // (stability >= Fsrs.LEARNED_STABILITY), and two names for one number is what made
            // the three figures look like they disagreed.
            StatTile("$wordsLearned", "words learned", Modifier.weight(1f))
            StatTile("$streak", "day streak", Modifier.weight(1f))
        }

        // ---- 3. Month calendar: the streak figure above, shown as history ----
        Spacer(Modifier.height(12.dp))
        MonthCalendarCard(container, lang)

        // ---- Course milestone bar: the whole road in one line, a segment per level, each
        // filling with that level's completed lessons. ----
        Spacer(Modifier.height(22.dp))
        SectionTitle("Course milestones")
        CourseMilestoneBar(
            segments = remember(plan) {
                plan.days.groupBy { it.level }.map { (level, days) -> level to days.map { it.day } }
            },
            completed = completedSet.toSet(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        // (Quizzes, exam readiness, and mock exams live on the journey as end-of-level
        // checkpoints — see LevelJourney; no Assessment section here.)

        // The CEFR ladder used to sit here as a collapsed card. It was reference material, not
        // progress: the level you are on is already the third stat at the top of this screen, and
        // the ladder itself belongs with the rest of the reference library on Profile.

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The card these panels share: a tone step for hierarchy, and a hairline because a tone step
 * alone measures about 1.28:1. A cream tone step separates on a monitor and vanishes outdoors, so
 * the edge is not decoration.
 */
@Composable
private fun ProgressCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/**
 * One month of history, stepped with arrows.
 *
 * A month rather than a rolling window because it is the unit people actually reason about, and
 * because it lets a learner look back at a specific week instead of watching a window slide.
 *
 * Gaps are drawn in [outlineVariant]: no red, no warning icon, no apologising copy. A history
 * that flinches at every missed day teaches the learner not to open it.
 */
@Composable
private fun MonthCalendarCard(container: AppContainer, lang: String) {
    val completions by container.progress.completions(lang).collectAsState(initial = emptyList())
    val zone = remember { java.time.ZoneId.systemDefault() }
    val banked = remember(completions) {
        completions.map {
            java.time.Instant.ofEpochMilli(it.completedAtEpoch).atZone(zone).toLocalDate()
        }.toSet()
    }
    val today = remember { java.time.LocalDate.now() }
    var offset by rememberSaveable { mutableStateOf(0) }
    val earliest = remember(banked) { com.corlang.app.data.MonthHistory.earliestOffset(today, banked) }
    val month = remember(offset, banked) {
        com.corlang.app.data.MonthHistory.build(today, offset, banked)
    }
    val shownMonth = remember(offset) { today.withDayOfMonth(1).plusMonths(offset.toLong()) }
    // Locale, not hardcoded strings: the month name and the weekday initials belong to the
    // learner's phone, not to English.
    val locale = java.util.Locale.getDefault()
    val monthLabel = remember(shownMonth, locale) {
        shownMonth.format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", locale))
            .replaceFirstChar { it.titlecase(locale) }
    }

    ProgressCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Full 48dp touch targets (IconButton's own default), and the forward arrow keeps
            // its size when disabled so the header never shifts between months.
            //
            // The handoff asked for a negative vertical margin here so the targets could overlap
            // the card padding without growing the card. Compose has no such thing: Modifier
            // .padding REJECTS a negative value at layout time, which is what crashed this
            // screen. The row simply carries the buttons' height instead; the alternative,
            // Modifier.offset, moves the drawing but not the layout, so it would leave the same
            // gap while making the targets sit off-centre.
            androidx.compose.material3.IconButton(
                onClick = { offset -= 1 },
                enabled = offset > earliest
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(monthLabel, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text("${month.banked} of ${month.settled} days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.IconButton(
                onClick = { offset += 1 },
                enabled = offset < 0
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }

        Spacer(Modifier.height(10.dp))

        val weekdays = remember(locale) {
            (1..7).map {
                java.time.DayOfWeek.of(it)
                    .getDisplayName(java.time.format.TextStyle.NARROW, locale)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            weekdays.forEach {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(5.dp))

        // The whole grid is one semantic group; the count line above is its summary, so a screen
        // reader never has to walk 31 cells to learn how the month went.
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.semantics(mergeDescendants = true) {}
        ) {
            month.cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    week.forEach { cell -> DayCellBox(cell, Modifier.weight(1f)) }
                    // Pad the final week so its cells keep the same width as every other row.
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DayCellBox(cell: com.corlang.app.data.MonthHistory.DayCell, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val fill = when (cell.state) {
        DayState.Banked -> scheme.primary
        DayState.Missed -> scheme.outlineVariant
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val ink = when (cell.state) {
        DayState.Banked -> scheme.onPrimary
        DayState.Missed -> scheme.onSurfaceVariant
        DayState.Today -> scheme.primary
        else -> scheme.outline
    }
    val edge = when (cell.state) {
        DayState.Today -> 2.dp to scheme.primary
        DayState.Future -> 1.dp to scheme.outlineVariant
        else -> null
    }
    // State is never colour alone: every cell says what it is out loud.
    val label = cell.date?.let { d ->
        val day = d.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM"))
        when (cell.state) {
            DayState.Banked -> "$day, complete"
            DayState.Missed -> "$day, no lesson"
            DayState.Today -> "$day, today, not yet complete"
            else -> "$day, still to come"
        }
    }
    Box(
        modifier = modifier
            .height(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(fill)
            .then(
                edge?.let {
                    Modifier.border(it.first, it.second, RoundedCornerShape(4.dp))
                } ?: Modifier
            )
            .then(label?.let { t -> Modifier.semantics { contentDescription = t } } ?: Modifier),
        contentAlignment = Alignment.Center
    ) {
        cell.day?.let {
            Text("$it", style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold, color = ink)
        }
    }
}

/**
 * One horizontal bar for the whole course: a segment per CEFR level, width proportional to the
 * level's lesson count, filled by the share of its lessons completed, labelled underneath with
 * the level id and its done/total count. A finished level's label turns primary.
 */
@Composable
private fun CourseMilestoneBar(
    segments: List<Pair<String, List<Int>>>,
    completed: Set<Int>,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            segments.forEach { (_, days) ->
                val fraction =
                    if (days.isEmpty()) 0f
                    else days.count { it in completed }.toFloat() / days.size
                Box(
                    modifier = Modifier
                        .weight(days.size.coerceAtLeast(1).toFloat())
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(10.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            segments.forEach { (level, days) ->
                val done = days.count { it in completed }
                Text(
                    "$level $done/${days.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (done == days.size && days.isNotEmpty())
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(days.size.coerceAtLeast(1).toFloat())
                )
            }
        }
    }
}
