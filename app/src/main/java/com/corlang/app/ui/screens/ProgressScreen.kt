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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import com.corlang.app.ui.components.Bullet
import com.corlang.app.ui.components.SectionTitle
import com.corlang.app.ui.components.StatTile
import com.corlang.app.ui.theme.Radius

/**
 * Progress, organised into bands so it leads with identity and progress instead of a wall:
 *   You (name), Progress (streak/days/level + vocab stats), Course milestones, and the CEFR
 *   ladder (collapsed by default since it's browse-when-you-want material). Quizzes, exam
 *   readiness, and mock exams live on the journey as end-of-level checkpoints.
 */
@Composable
fun ProgressScreen(
    container: AppContainer,
    lang: String
) {
    val meta = remember(lang) { container.content.meta(lang) }
    val levels = remember(lang) { container.content.levels(lang).levels }

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

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("Progress",
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Kept on this device. Nothing here is compared with anyone else.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))

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
            StatTile("$wordsLearned", "words known", Modifier.weight(1f))
            StatTile("$streak", "day streak", Modifier.weight(1f))
        }

        // ---- 3. Month calendar: the streak figure above, shown as history ----
        Spacer(Modifier.height(12.dp))
        MonthCalendarCard(container, lang)

        // ---- 4. Review load ahead ----
        Spacer(Modifier.height(12.dp))
        ReviewLoadCard(reviews)

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
            // 48dp targets with a negative vertical margin: full-size touch areas that do not
            // inflate the card. The forward arrow keeps its size when disabled so the header
            // never shifts between months.
            androidx.compose.material3.IconButton(
                onClick = { offset -= 1 },
                enabled = offset > earliest,
                modifier = Modifier.padding(vertical = (-14).dp)
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
                enabled = offset < 0,
                modifier = Modifier.padding(vertical = (-14).dp)
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
 * What review is about to ask of you, for the next seven days. Bars are scaled to the largest
 * value in the window rather than to a fixed ceiling, so a quiet week reads as quiet instead of
 * drawing a full-height bar for two cards.
 */
@Composable
private fun ReviewLoadCard(reviews: List<com.corlang.app.data.db.WordReview>) {
    val today = remember { com.corlang.app.data.WordsRepository.todayEpochDay() }
    val load = remember(reviews, today) {
        (0..6).map { offset ->
            val day = today + offset
            // Anything overdue is work waiting today, not work that quietly vanished.
            reviews.count { if (offset == 0) it.dueEpochDay <= day else it.dueEpochDay == day }
        }
    }
    val peak = (load.maxOrNull() ?: 0).coerceAtLeast(1)
    val locale = java.util.Locale.getDefault()
    val labels = remember(locale) {
        (0..6).map {
            java.time.LocalDate.now().plusDays(it.toLong()).dayOfWeek
                .getDisplayName(java.time.format.TextStyle.NARROW, locale)
        }
    }

    ProgressCard {
        Text("Review load ahead", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            load.forEachIndexed { i, count ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text("$count", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            // A day with nothing due still shows a sliver, so the row reads as
                            // seven days rather than four days and a gap.
                            .height((4 + 44f * count / peak).dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .semantics { contentDescription = "$count due" }
                    )
                    Text(labels[i], style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
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
