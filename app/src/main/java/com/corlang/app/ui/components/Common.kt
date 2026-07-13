package com.corlang.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corlang.app.speech.TtsManager
import com.corlang.app.ui.theme.CorlangColors
import com.corlang.app.ui.theme.Motion
import com.corlang.app.ui.theme.Radius
import com.corlang.app.ui.theme.Space
import com.corlang.app.ui.theme.rememberReducedMotion

/** Section header used across screens. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

/** A single bullet line with a leading dot. */
@Composable
fun Bullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("•  ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Monospace "diagram" box that scrolls horizontally so tables/figures keep alignment. */
@Composable
fun DiagramBox(text: String) {
    val scroll = rememberScrollState()
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Box {
            Row(modifier = Modifier.horizontalScroll(scroll)) {
                Text(
                    text = text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
            // Right-edge fade signals that a wide table continues offscreen.
            if (scroll.canScrollForward) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                0.88f to Color.Transparent,
                                1.0f to MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                )
            }
        }
    }
}

/** Example line: target phrase in primary color, English gloss muted, optional TTS speaker. */
@Composable
fun ExampleLine(target: String, gloss: String, tts: TtsManager? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                target,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                gloss,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (tts != null) SpeakerButton(tts = tts, text = target)
    }
}

@Composable
fun InfoCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(Space.lg)) { content() }
    }
}

/** The four visual states an answer option can be in. */
enum class OptionState { DEFAULT, SELECTED, CORRECT, WRONG }

/**
 * A single selectable answer row, shared by quizzes and mock exams so grading looks and feels
 * identical everywhere. On the check moment the correct option gives a soft spring-pop and a
 * wrong choice a quick shake — motion that confirms the result, not decoration. Honors reduced
 * motion (states still recolor; they just don't animate).
 */
@Composable
fun OptionRow(
    text: String,
    state: OptionState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val feedback = CorlangColors.feedback
    val border = when (state) {
        OptionState.SELECTED -> MaterialTheme.colorScheme.primary
        OptionState.CORRECT -> feedback.correct
        OptionState.WRONG -> feedback.wrong
        OptionState.DEFAULT -> MaterialTheme.colorScheme.outline
    }
    val reduced = rememberReducedMotion()
    val pop = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state) {
        if (reduced) return@LaunchedEffect
        when (state) {
            OptionState.CORRECT -> {
                pop.animateTo(1.04f, Motion.settle())
                pop.animateTo(1f, Motion.settle())
            }
            OptionState.WRONG -> {
                shake.animateTo(-12f, tween(40))
                shake.animateTo(12f, tween(80))
                shake.animateTo(-6f, tween(60))
                shake.animateTo(0f, tween(40))
            }
            // Reset to rest: a quick "Next" can interrupt the pop/shake mid-flight, and without
            // this the row would stay scaled/offset for every following question.
            else -> {
                pop.snapTo(1f)
                shake.snapTo(0f)
            }
        }
    }
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs)
            .graphicsLayer {
                scaleX = pop.value; scaleY = pop.value; translationX = shake.value
            }
            .border(2.dp, border, RoundedCornerShape(Radius.md))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(text, modifier = Modifier.padding(14.dp))
    }
}

/** Compact metric tile (value over label), shared by the Words and Progress dashboards. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(Space.md)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
