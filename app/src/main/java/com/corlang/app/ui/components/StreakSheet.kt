package com.corlang.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.corlang.app.data.ProgressRepository
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The streak, explained in one place.
 *
 * Everything the freeze system has to say lives here rather than being dripped into the Learn
 * page as a subtitle nobody could act on. The Learn page keeps a flame and a number; tapping it
 * opens this.
 *
 * The week strip is drawn from COMPLETED days only. A day held by a freeze is not a day studied,
 * so it stays an empty circle exactly like the month calendar on Progress — the streak number may
 * be generous, the record of what you actually did may not.
 */

/**
 * The tappable flame + number in the top bar.
 *
 * A lone icon in that corner used to read as a button and do nothing (see [CorlangTopBar]); this
 * one genuinely is a button, which is what makes it safe to put back there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakChip(streak: Int, lit: Boolean, onClick: () -> Unit) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            StreakFlame(streak = streak, lit = lit, size = 20.dp)
            Text(
                "$streak",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

/**
 * The full panel. [studiedDays] is every date a lesson was completed (the same set the month
 * calendar on Progress draws from); the strip shows the learner's current week.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakSheet(
    streak: Int,
    freezes: Int,
    longestStreak: Int,
    studiedDays: Set<LocalDate>,
    today: LocalDate,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Streak",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))
            StreakFlame(streak = streak, lit = studiedDays.contains(today), size = 88.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                "$streak",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "day streak",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(22.dp))
            WeekStrip(studiedDays = studiedDays, today = today)

            Spacer(Modifier.height(22.dp))
            StatsRow(freezes = freezes, longestStreak = longestStreak)

            Spacer(Modifier.height(20.dp))
            HowItWorks()
        }
    }
}

/** A ring per day of the current week: ticked when that day's lesson was completed. */
@Composable
private fun WeekStrip(studiedDays: Set<LocalDate>, today: LocalDate) {
    val locale = Locale.getDefault()
    // Monday-first, matching the month calendar on Progress (MonthHistory.build). Two views of
    // the same history disagreeing about where a week starts is worse than either convention.
    // The weekday LETTERS are still the learner's locale, as they are there.
    val weekStart = today.minusDays(((today.dayOfWeek.value + 6) % 7).toLong())

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        (0..6).forEach { i ->
            val date = weekStart.plusDays(i.toLong())
            val done = studiedDays.contains(date)
            val isToday = date == today
            val future = date.isAfter(today)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (done) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .border(
                            BorderStroke(
                                if (isToday) 2.dp else 1.dp,
                                if (isToday) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            CircleShape
                        )
                ) {
                    if (done) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "lesson completed",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (future) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

/** The two numbers worth keeping: what protects the run, and the record it is chasing. */
@Composable
private fun StatsRow(freezes: Int, longestStreak: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Stat(
                value = "$freezes",
                label = if (freezes == 1) "streak freeze" else "streak freezes",
                modifier = Modifier.weight(1f)
            )
            VerticalRule()
            Stat(
                value = "$longestStreak ${if (longestStreak == 1) "day" else "days"}",
                label = "longest streak",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun VerticalRule() {
    Box(
        Modifier
            .width(1.dp)
            .height(34.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/** The rules, stated once, somewhere a learner can actually read them. */
@Composable
private fun HowItWorks() {
    val milestones = ProgressRepository.FREEZE_MILESTONES.joinToString(", ")
    val cap = ProgressRepository.MAX_FREEZES
    Column(Modifier.fillMaxWidth()) {
        Text(
            "How it works",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Rule("Finishing a lesson keeps the streak. Reviewing words on their own does not.")
        Rule("A streak freeze is earned at $milestones days, $cap in all.")
        Rule(
            "Miss a day and a freeze covers it for you. With a full bank you can miss " +
                "$cap days in a row before the streak ends."
        )
        Rule("A covered day is still a day off: it stays unticked here and on your calendar.")
        Rule("Lose the streak and the freezes go with it. Build it up again and you earn them again.")
    }
}

@Composable
private fun Rule(text: String) {
    Row(Modifier.padding(top = 6.dp)) {
        Text(
            "·",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
