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
import androidx.compose.runtime.LaunchedEffect
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
import com.corlang.app.data.model.Question
import com.corlang.app.data.model.QuestionType
import com.corlang.app.ui.Haptics
import com.corlang.app.ui.components.SpeakCheck
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
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
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
        ) { Text("Studied, practise it now →") }
    }
}

/** What an interrupted exercise step left behind: which questions were cleared, and whether
 *  any answer was ever missed (drives the honest finish message across an exit/resume). */
data class ExerciseResume(val solvedIndices: Set<Int>, val missedAny: Boolean)

/** EXERCISE: sequential graded questions (MCQ / FILL / REORDER) with instant feedback.
 *
 * A missed question isn't a dead end: it's re-queued to the end and re-asked until answered
 * correctly, so you only ever repeat the ones you got wrong, not the whole set. MCQ options
 * are shuffled per showing so the correct answer isn't positionally predictable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseActivity(
    container: AppContainer,
    activity: DayActivity,
    loadResumeState: suspend () -> ExerciseResume = { ExerciseResume(emptySet(), false) },
    onSolved: (questionIndex: Int) -> Unit = {},
    onMissed: () -> Unit = {},
    // Mistake bank hooks: the QUESTION that was answered, right or wrong, so a wrong answer
    // is banked for later sessions and a right answer clears any banked copy.
    onQuestionCleared: (Question) -> Unit = {},
    onQuestionMissed: (Question) -> Unit = {},
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val total = activity.questions.size

    // Resume: load WHICH questions were already cleared (persisted per step as indices, not a
    // count — a count broke when a missed question was re-queued: "drop the first N" then
    // silently dropped the miss, and the step ended claiming "first try on every one").
    // Gated on the async load so the first frame doesn't momentarily start from question one.
    var resume by remember(activity.title) { mutableStateOf<ExerciseResume?>(null) }
    LaunchedEffect(activity.title) { resume = loadResumeState() }
    val resumed = resume ?: return
    val resumeIdx = remember(activity.title) {
        resumed.solvedIndices.filter { it in activity.questions.indices }.toSet()
    }

    // Live queue of remaining question INDICES (identity survives re-queuing); missed ones get
    // re-appended. `served` bumps each time we advance so option-shuffle / response state
    // re-init cleanly.
    val queue = remember(activity.title) {
        mutableStateListOf<Int>().apply { addAll(activity.questions.indices.filter { it !in resumeIdx }) }
    }
    var served by remember(activity.title) { mutableIntStateOf(0) }
    var solved by remember(activity.title) { mutableIntStateOf(resumeIdx.size) }   // distinct questions cleared
    var missedAny by remember(activity.title) { mutableStateOf(resumed.missedAny) }
    var checked by remember(activity.title) { mutableStateOf(false) }
    var lastCorrect by remember(activity.title) { mutableStateOf(false) }
    var finished by remember(activity.title) { mutableStateOf(false) }
    var selectedOption by remember(activity.title) { mutableStateOf<String?>(null) }
    var fillText by remember(activity.title) { mutableStateOf("") }
    val reorderAssembled = remember(activity.title) { mutableStateListOf<String>() }
    val feedback = CorlangColors.feedback

    if (total == 0) {
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Next →") }
        return
    }
    if (finished || queue.isEmpty()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                "$total / $total",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                if (!missedAny) "Perfect, first try on every one."
                else "All correct now, the ones you missed came back until you nailed them.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text("Done, next step →")
            }
        }
        return
    }

    val q = activity.questions[queue.first()]
    // Shuffle MCQ options once per showing so position never gives away the answer.
    val displayOptions = remember(activity.title, served) {
        if (q.type == QuestionType.MCQ) q.options.shuffled() else q.options
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        // Tripled air on both sides: this bar sits between the step card above and the question
        // below, and at the old 6dp it read as glued to both.
        LinearProgressIndicator(
            progress = { solved.toFloat() / total },
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 18.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                q.prompt,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$solved/$total" + if (queue.size > 1) "  ·  ${queue.size} left" else "",
                style = MaterialTheme.typography.bodySmall
            )
        }
        q.audioText?.let { audio ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpeakerButton(tts = container.tts, text = audio, rate = 0.9f)
                Text("🎧 Listen, then answer", style = MaterialTheme.typography.bodySmall)
            }
        }

        when (q.type) {
            QuestionType.MCQ -> displayOptions.forEach { option ->
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
                        .padding(vertical = 4.dp)
                        .border(2.dp, border, RoundedCornerShape(10.dp))
                        .clickable(enabled = !checked) { selectedOption = option }
                ) { Text(option, modifier = Modifier.padding(12.dp)) }
            }

            QuestionType.FILL -> OutlinedTextField(
                value = fillText,
                onValueChange = { if (!checked) fillText = it },
                label = { Text("Write your answer") },
                enabled = !checked,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )

            QuestionType.REORDER -> {
                // Tokens are shown lowercased with edge punctuation stripped: the sentence-case
                // capital and the trailing dot would give the first and last word away.
                // Keyed on `served` (like displayOptions), not prompt text: identical wording
                // must not reuse a previous question's scramble.
                val scrambled = remember(activity.title, served) {
                    val tokens = q.options.map(Grading::reorderToken)
                    val answer = q.ordered.map(Grading::reorderToken)
                    var s = tokens.shuffled()
                    var tries = 0
                    while (s == answer && tokens.distinct().size > 1 && tries < 10) {
                        s = tokens.shuffled(); tries++
                    }
                    s
                }
                Text("Tap the words in order:", style = MaterialTheme.typography.bodySmall)
                FlowRow(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    scrambled.forEach { token ->
                        val used = reorderAssembled.count { it == token } >=
                            scrambled.count { it == token }
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
                        if (reorderAssembled.isEmpty()) "(tap words, tap here to undo)"
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
                    // Text only — no speaker in the verdict (field feedback: audio on the
                    // answer reveal doesn't make sense; pronunciation lives on prompts/LEARN).
                    Text(
                        if (lastCorrect) "✅ Correct" else "❌ $correctText",
                        fontWeight = FontWeight.Bold
                    )
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
                    if (correct) onQuestionCleared(q) else onQuestionMissed(q)
                    if (correct) {
                        Haptics.confirm(context)
                        // Persist the solve NOW, at Check time — not on the advance tap. Exiting
                        // from the "✅ Correct" feedback screen (tab tap, back) used to lose this
                        // question and re-serve it on resume.
                        solved++
                        onSolved(queue.first())
                    } else {
                        Haptics.reject(context)
                        if (!missedAny) onMissed()   // persist once; the finish message must
                        missedAny = true             // stay honest across an exit/resume
                    }
                    checked = true
                } else {
                    val cur = queue.removeAt(0)
                    if (!lastCorrect) {
                        queue.add(cur)      // re-ask this one later, until it's right
                    }
                    if (queue.isEmpty()) {
                        finished = true
                    } else {
                        served++
                        selectedOption = null; fillText = ""; reorderAssembled.clear()
                        checked = false; lastCorrect = false
                    }
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
                    lastCorrect && queue.size <= 1 -> "Finish exercise"
                    lastCorrect -> "Next →"
                    else -> "Got it, try this one again later"
                }
            )
        }
    }
}

/** DIALOGUE: a real script to act out, read your lines aloud; partner (or TTS) reads theirs. */
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
                    .padding(vertical = 4.dp)
                    .padding(start = if (you) 24.dp else 0.dp, end = if (you) 0.dp else 24.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                    // The learner's own lines can be practised aloud with pronunciation feedback.
                    if (you) {
                        SpeakCheck(container = container, target = line.hr)
                    }
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
            "Act it out aloud, with your partner if possible, or tap 🔊 for the other role.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
