package com.corlang.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.data.model.DayActivity
import com.corlang.app.data.model.QuestionType
import com.corlang.app.ui.Haptics
import com.corlang.app.ui.components.SpeakerButton
import com.corlang.app.ui.theme.CorlangColors

/**
 * Renderers for embedded day activities: the content IS in the step —
 * learn material with audio, graded exercises, and partner-dialogue scripts.
 */

/** LEARN: the actual words/phrases/rules to study, each with TTS. */
@Composable
fun LearnActivity(container: AppContainer, activity: DayActivity, onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (activity.intro.isNotBlank()) {
            Text(
                activity.intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        activity.items.forEach { item ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.hr,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(item.en, style = MaterialTheme.typography.bodyMedium)
                        item.note?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    SpeakerButton(tts = container.tts, text = item.hr)
                }
            }
        }
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        ) { Text("Studied — practise it now →") }
    }
}

/** EXERCISE: sequential graded questions (MCQ / FILL / REORDER) with instant feedback. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseActivity(container: AppContainer, activity: DayActivity, onDone: () -> Unit) {
    val context = LocalContext.current
    val questions = activity.questions
    var index by remember(activity.title) { mutableIntStateOf(0) }
    var score by remember(activity.title) { mutableIntStateOf(0) }
    var checked by remember(activity.title) { mutableStateOf(false) }
    var lastCorrect by remember(activity.title) { mutableStateOf(false) }
    var finished by remember(activity.title) { mutableStateOf(false) }
    var selectedOption by remember(activity.title) { mutableStateOf<String?>(null) }
    var fillText by remember(activity.title) { mutableStateOf("") }
    val reorderAssembled = remember(activity.title) { mutableStateListOf<String>() }
    val feedback = CorlangColors.feedback

    if (questions.isEmpty()) {
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Next →") }
        return
    }
    if (finished) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                "🎯 $score / ${questions.size}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                if (score == questions.size) "Perfect. On to the next step."
                else "Review the ones you missed above — they'll come back in future drills.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text("Done — next step →")
            }
        }
        return
    }

    val q = questions[index]
    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { (index + 1f) / questions.size },
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                q.prompt,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text("${index + 1}/${questions.size}", style = MaterialTheme.typography.bodySmall)
        }
        q.audioText?.let { audio ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpeakerButton(tts = container.tts, text = audio, rate = 0.9f)
                Text("🎧 Listen, then answer", style = MaterialTheme.typography.bodySmall)
            }
        }

        when (q.type) {
            QuestionType.MCQ -> q.options.forEach { option ->
                val isChosen = selectedOption == option
                val border = when {
                    !checked && isChosen -> MaterialTheme.colorScheme.primary
                    checked && option == q.answer -> feedback.correct
                    checked && isChosen -> feedback.wrong
                    else -> MaterialTheme.colorScheme.outline
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .border(2.dp, border, RoundedCornerShape(10.dp))
                        .clickable(enabled = !checked) { selectedOption = option }
                ) { Text(option, modifier = Modifier.padding(12.dp)) }
            }

            QuestionType.FILL -> OutlinedTextField(
                value = fillText,
                onValueChange = { if (!checked) fillText = it },
                label = { Text(if (q.strictDiacritics) "Type it (diacritics count!)" else "Type it") },
                enabled = !checked,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )

            QuestionType.REORDER -> {
                val scrambled = remember(q.prompt) {
                    var s = q.options.shuffled()
                    var tries = 0
                    while (s == q.ordered && q.options.distinct().size > 1 && tries < 10) {
                        s = q.options.shuffled(); tries++
                    }
                    s
                }
                Text("Tap the words in order:", style = MaterialTheme.typography.bodySmall)
                FlowRow(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    scrambled.forEach { token ->
                        val used = reorderAssembled.count { it == token } >=
                            q.options.count { it == token }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (used) MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (used) MaterialTheme.colorScheme.onSurfaceVariant
                                           else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .padding(3.dp)
                                .clickable(enabled = !checked && !used) { reorderAssembled.add(token) }
                        ) { Text(token, modifier = Modifier.padding(10.dp)) }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .clickable(enabled = !checked && reorderAssembled.isNotEmpty()) {
                            if (reorderAssembled.isNotEmpty()) reorderAssembled.removeAt(reorderAssembled.lastIndex)
                        }
                ) {
                    Text(
                        if (reorderAssembled.isEmpty()) "(tap words — tap here to undo)"
                        else reorderAssembled.joinToString(" "),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            else -> {}
        }

        if (checked) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (lastCorrect) feedback.correctContainer else feedback.wrongContainer,
                contentColor = if (lastCorrect) feedback.onCorrectContainer else feedback.onWrongContainer,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    val correctText = if (q.type == QuestionType.REORDER)
                        q.ordered.joinToString(" ") else q.answer
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (lastCorrect) "✅ Correct" else "❌ $correctText",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        SpeakerButton(tts = container.tts, text = correctText)
                    }
                    if (q.explanation.isNotBlank()) {
                        Text(q.explanation, modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Button(
            onClick = {
                if (!checked) {
                    val correct = when (q.type) {
                        QuestionType.MCQ -> selectedOption?.let { Grading.gradeMcq(q, it) } ?: false
                        QuestionType.FILL -> Grading.gradeFill(q, fillText)
                        QuestionType.REORDER -> Grading.gradeReorder(q, reorderAssembled.toList())
                        else -> false
                    }
                    lastCorrect = correct
                    if (correct) { score++; Haptics.confirm(context) } else Haptics.reject(context)
                    checked = true
                } else if (index + 1 >= questions.size) {
                    finished = true
                } else {
                    index++
                    selectedOption = null; fillText = ""; reorderAssembled.clear()
                    checked = false; lastCorrect = false
                }
            },
            enabled = checked || when (q.type) {
                QuestionType.MCQ -> selectedOption != null
                QuestionType.FILL -> fillText.isNotBlank()
                QuestionType.REORDER -> reorderAssembled.isNotEmpty()
                else -> true
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text(
                when {
                    !checked -> "Check"
                    index + 1 >= questions.size -> "Finish exercise"
                    else -> "Next →"
                }
            )
        }
    }
}

/** DIALOGUE: a real script to act out — read your lines aloud; partner (or TTS) reads theirs. */
@Composable
fun DialogueActivity(container: AppContainer, activity: DayActivity, onDone: () -> Unit) {
    var revealed by remember(activity.title) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (activity.intro.isNotBlank()) {
            Text(
                activity.intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        activity.lines.forEach { line ->
            val you = line.speaker.equals("Me", true) || line.speaker.equals("You", true) ||
                line.speaker.equals("Vi", true) || line.speaker.equals("Ja", true)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (you) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (you) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .padding(start = if (you) 24.dp else 0.dp, end = if (you) 0.dp else 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${line.speaker}:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(line.hr, style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold)
                        if (revealed) {
                            Text(line.en, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    SpeakerButton(tts = container.tts, text = line.hr)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { revealed = !revealed },
                modifier = Modifier.weight(1f)
            ) { Text(if (revealed) "Hide English" else "Show English") }
            Button(onClick = onDone, modifier = Modifier.weight(1f)) {
                Text("Practised →")
            }
        }
        Text(
            "Act it out aloud — with your partner if possible, or tap 🔊 for the other role.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
