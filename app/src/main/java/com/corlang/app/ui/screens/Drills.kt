package com.corlang.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.corlang.app.data.DrillGen
import com.corlang.app.data.WordsRepository
import com.corlang.app.data.model.ActivityKind
import com.corlang.app.data.model.LearnItem
import com.corlang.app.data.model.StudyDay
import com.corlang.app.data.model.VocabWord
import com.corlang.app.ui.Haptics
import com.corlang.app.ui.components.SpeakerButton
import com.corlang.app.ui.theme.CorlangColors

/**
 * Auto-generated in-app drills, built from the validated deck (see data/DrillGen.kt).
 * Both mirror the official exam's grammar-section format: correct form IN CONTEXT.
 */

/** Picks drill source words: due first, then already-seen, then deck order. */
@Composable
private fun drillWords(container: AppContainer, lang: String): List<VocabWord> {
    val reviews by container.words.reviews(lang).collectAsState(initial = emptyList())
    // Keyed on the reviews themselves: keying on a boolean (and consumers on source SIZE, which
    // never changes) meant the due-first ordering could never take effect — drills were always
    // built from the first deck words instead of the learner's due/seen ones.
    return remember(reviews) {
        val all = container.words.allWords(lang)
        val today = WordsRepository.todayEpochDay()
        val due = reviews.filter { it.dueEpochDay <= today }.map { it.wordId }.toSet()
        val seen = reviews.map { it.wordId }.toSet()
        (all.filter { it.id in due }.shuffled() +
            all.filter { it.id in seen && it.id !in due }.shuffled() +
            all.filter { it.id !in seen })
    }
}

/**
 * Case-in-context drill (exam section III format): the example sentence with the target
 * form blanked; pick the correct ending. Answers come verbatim from QA'd examples —
 * the app never invents a Croatian form.
 */
@Composable
fun ClozeDrill(container: AppContainer, lang: String, onFinished: () -> Unit) {
    val context = LocalContext.current
    val source = drillWords(container, lang)
    val items = remember(source) { DrillGen.buildClozeItems(source, 8) }

    // Keyed on items: when the reviews flow lands (right after open) and the item list rebuilds,
    // the drill restarts cleanly instead of pointing old indices at a new list.
    var qIndex by remember(items) { mutableIntStateOf(0) }
    var score by remember(items) { mutableIntStateOf(0) }
    var chosen by remember(items) { mutableStateOf<String?>(null) }
    var finished by remember(items) { mutableStateOf(false) }
    val feedback = CorlangColors.feedback

    if (items.isEmpty()) {
        Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text("Next →") }
        return
    }
    if (finished) {
        DrillResult(score, items.size,
            "The exam's grammar section is exactly this: the right form in the sentence.", onFinished)
        return
    }

    val item = items[qIndex]
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.sentence,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text("${qIndex + 1}/${items.size}", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            item.gloss,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
        )
        item.options.forEach { option ->
            val isChosen = chosen == option
            val border = when {
                chosen == null -> MaterialTheme.colorScheme.outline
                option == item.answer -> feedback.correct
                isChosen -> feedback.wrong
                else -> MaterialTheme.colorScheme.outline
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .border(2.dp, border, RoundedCornerShape(10.dp))
                    .clickable(enabled = chosen == null) {
                        chosen = option
                        if (option == item.answer) { score++; Haptics.confirm(context) }
                        else Haptics.reject(context)
                    }
            ) { Text(option, modifier = Modifier.padding(12.dp)) }
        }
        if (chosen != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    item.sentence.replace("___", item.answer),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                SpeakerButton(tts = container.tts, text = item.sentence.replace("___", item.answer))
            }
            Button(
                onClick = {
                    if (qIndex + 1 >= items.size) finished = true else { qIndex++; chosen = null }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text(if (qIndex + 1 >= items.size) "See result" else "Next →") }
        }
    }
}

/**
 * Typed EN→HR recall (production practice): type the Croatian word for the gloss.
 * Graded with STRICT diacritics, exactly as the exam expects you to write.
 */
@Composable
fun RecallDrill(container: AppContainer, lang: String, onFinished: () -> Unit) {
    val source = drillWords(container, lang)
    val items = remember(source) { DrillGen.buildRecallItems(source, 8) }
    RecallRunner(
        container, items,
        "Producing the Croatian yourself, with the right diacritics, is what speaking needs.",
        onFinished
    )
}

/**
 * Wrap-up retrieval built from TODAY'S own lesson: produce the Croatian, from memory, for the
 * exact phrases the day's LEARN activities taught. This is the day's real closing exercise, so
 * "recall your intro / greetings / nationalities" tests the content just studied, not random words.
 */
@Composable
fun WrapupRecall(container: AppContainer, day: StudyDay, onFinished: () -> Unit) {
    val items = remember(day.day) {
        // No hint: this is from-memory recall, and the LEARN note often contains the Croatian
        // itself, which would hand you the answer right under the prompt.
        wrapupRecallPhrases(day)
            .map { DrillGen.Recall(en = it.en, answerHr = it.hr, posHint = null) }
            .take(8)
    }
    RecallRunner(
        container, items,
        "Recalling today's phrases from memory is what makes them stick.",
        onFinished
    )
}

/**
 * The clean, typable phrases from a day's LEARN activities, used to build the wrap-up recall.
 * For "A / B" entries we keep just the first form (one clean answer to produce); we drop
 * ellipsis stubs ("Zovem se…") and anything too long to reproduce letter-for-letter fairly.
 * Shared with the session builder so it only inserts a recall wrap-up when there's enough to test.
 */
fun wrapupRecallPhrases(day: StudyDay): List<LearnItem> =
    day.activities
        .filter { it.type == ActivityKind.LEARN }
        .flatMap { it.items }
        .asSequence()
        .filter { it.en.isNotBlank() && "…" !in it.hr && "..." !in it.hr }
        .map { it.copy(hr = it.hr.substringBefore(" / ").trim()) }   // keep the first alternative form
        .filter { it.hr.length in 2..40 }
        .distinctBy { it.hr.lowercase() }
        .toList()

/** Shared EN -> HR typed-recall runner used by both the deck recall drill and the day wrap-up. */
@Composable
private fun RecallRunner(
    container: AppContainer,
    items: List<DrillGen.Recall>,
    resultLine: String,
    onFinished: () -> Unit
) {
    val context = LocalContext.current

    var qIndex by remember(items) { mutableIntStateOf(0) }
    var score by remember(items) { mutableIntStateOf(0) }
    var input by remember(items) { mutableStateOf("") }
    var checked by remember(items) { mutableStateOf(false) }
    var correct by remember(items) { mutableStateOf(false) }
    var finished by remember(items) { mutableStateOf(false) }
    val feedback = CorlangColors.feedback

    if (items.isEmpty()) {
        Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text("Next →") }
        return
    }
    if (finished) {
        DrillResult(score, items.size, resultLine, onFinished)
        return
    }

    val item = items[qIndex]
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.en,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text("${qIndex + 1}/${items.size}", style = MaterialTheme.typography.bodySmall)
        }
        item.posHint?.let {
            Text(it, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = input,
            onValueChange = { if (!checked) input = it },
            label = { Text("Croatian (diacritics count!)") },
            enabled = !checked,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
        if (checked) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (correct) feedback.correctContainer else feedback.wrongContainer,
                contentColor = if (correct) feedback.onCorrectContainer else feedback.onWrongContainer,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
                    Text(
                        if (correct) "✅ ${item.answerHr}" else "❌ ${item.answerHr}",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    SpeakerButton(tts = container.tts, text = item.answerHr)
                }
            }
        }
        Button(
            onClick = {
                if (!checked) {
                    correct = Grading.normalize(input, strict = true) ==
                        Grading.normalize(item.answerHr, strict = true)
                    if (correct) { score++; Haptics.confirm(context) } else Haptics.reject(context)
                    checked = true
                } else if (qIndex + 1 >= items.size) {
                    finished = true
                } else {
                    qIndex++; input = ""; checked = false; correct = false
                }
            },
            enabled = checked || input.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text(
                when {
                    !checked -> "Check"
                    qIndex + 1 >= items.size -> "See result"
                    else -> "Next →"
                }
            )
        }
    }
}

@Composable
private fun DrillResult(score: Int, total: Int, line: String, onFinished: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            "🎯 $score / $total",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(line, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Button(onClick = onFinished, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text("Done, next step →")
        }
        Spacer(Modifier.height(8.dp))
    }
}
