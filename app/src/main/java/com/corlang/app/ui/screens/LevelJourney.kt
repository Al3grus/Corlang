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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
        SectionTitle("🪜 Your journey")

        // Level selector chips.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
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
                    modifier = Modifier.then(
                        if (reached) Modifier.clickable { selectedLevel = level } else Modifier
                    )
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

        Row(
            // Inner padding (child of the scroll) gives the pulsing current-day node room to
            // expand without being clipped by the scroll container's edges — otherwise day 1,
            // flush against the left edge, gets its left side cut off as it breathes.
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
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
                            if (current) Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse }
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
        }
        Text(
            "$selectedLevel · $doneInLevel / ${stones.size} lessons done" +
                if (stones.isNotEmpty()) "  (day ${stones.first().day}–${stones.last().day})" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
