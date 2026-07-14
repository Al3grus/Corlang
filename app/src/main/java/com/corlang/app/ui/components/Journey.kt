package com.corlang.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corlang.app.data.model.StudyDay

/*
 * The course as a journey: one segment per CEFR level with live progress, milestone dots
 * between levels, and the next checkpoint named. The plan data already carries the story —
 * day 60 is the CIPLE checkpoint, 85 the DEPLE mock, 105 the DIPLE finale.
 */

private data class JourneySegment(
    val level: String,
    val firstDay: Int,
    val lastDay: Int,
    val total: Int
)

@Composable
fun JourneyPath(
    days: List<StudyDay>,
    completedDays: Set<Int>,
    currentDay: Int,
    modifier: Modifier = Modifier
) {
    val segments = remember(days) {
        days.groupBy { it.level }.map { (level, ds) ->
            JourneySegment(level, ds.minOf { it.day }, ds.maxOf { it.day }, ds.size)
        }.sortedBy { it.firstDay }
    }
    if (segments.isEmpty()) return

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            segments.forEachIndexed { i, seg ->
                val done = (seg.firstDay..seg.lastDay).count { it in completedDays }
                val isCurrent = currentDay in seg.firstDay..seg.lastDay
                Column(
                    Modifier.weight(seg.total.toFloat()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        seg.level,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { if (seg.total == 0) 0f else done.toFloat() / seg.total },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                if (i < segments.lastIndex) {
                    // Milestone dot between levels; fills once the level behind it is finished.
                    val levelDone = (seg.firstDay..seg.lastDay).all { it in completedDays }
                    Surface(
                        shape = CircleShape,
                        color = if (levelDone) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(10.dp)
                            .align(Alignment.Bottom)
                    ) {}
                }
            }
            Text(
                "🏆",
                modifier = Modifier
                    .padding(start = 6.dp)
                    .align(Alignment.Bottom)
            )
        }

        // Name the next checkpoint: the final day of the level you're currently in.
        val currentSeg = segments.firstOrNull { currentDay in it.firstDay..it.lastDay }
        val checkpoint = currentSeg?.let { seg -> days.firstOrNull { it.day == seg.lastDay } }
        if (checkpoint != null && checkpoint.day !in completedDays) {
            Text(
                "Next milestone: Day ${checkpoint.day} — ${checkpoint.title}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
