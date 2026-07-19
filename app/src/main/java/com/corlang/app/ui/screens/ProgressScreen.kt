package com.corlang.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
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
            StatTile("$daysDone", "lessons done", Modifier.weight(1f))
            StatTile(currentLevel, "current level", Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("$wordsStarted", "words started", Modifier.weight(1f))
            StatTile("$wordsLearned", "words learned", Modifier.weight(1f))
            StatTile("$wordsMastered", "words mastered", Modifier.weight(1f))
        }

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
