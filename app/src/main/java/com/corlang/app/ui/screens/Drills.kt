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
import androidx.compose.runtime.LaunchedEffect
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

    val item = items[qIndex.coerceIn(0, items.lastIndex)]
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
                    .padding(vertical = 4.dp)
                    .border(2.dp, border, RoundedCornerShape(10.dp))
                    .clickable(enabled = chosen == null) {
                        chosen = option
                        if (option == item.answer) { score++; Haptics.confirm(context) }
                        else Haptics.reject(context)
                    }
            ) { Text(option, modifier = Modifier.padding(12.dp)) }
        }
        if (chosen != null) {
            // Text only in the verdict — no speaker on answer reveals (field feedback).
            Text(
                item.sentence.replace("___", item.answer),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
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
    val name = remember(lang) { container.content.meta(lang).name }
    RecallRunner(
        container, items, name, lang,
        "Producing the $name yourself, with the right diacritics, is what speaking needs.",
        onFinished
    )
}

/**
 * Wrap-up retrieval built from TODAY'S own lesson: produce the Croatian, from memory, for the
 * exact phrases the day's LEARN activities taught. This is the day's real closing exercise, so
 * "recall your intro / greetings / nationalities" tests the content just studied, not random words.
 */
@Composable
fun WrapupRecall(
    container: AppContainer,
    lang: String,
    day: StudyDay,
    loadResume: (suspend () -> RecallResume)? = null,
    onAnswered: (index: Int, correct: Boolean) -> Unit = { _, _ -> },
    onFinished: () -> Unit
) {
    val items = remember(day.day) {
        // No hint: this is from-memory recall, and the LEARN note often contains the target
        // language itself, which would hand you the answer right under the prompt.
        wrapupRecallPhrases(day)
            .map { DrillGen.Recall(en = it.en, answerHr = it.hr, posHint = null) }
            .take(8)
    }
    RecallRunner(
        container, items,
        remember(lang) { container.content.meta(lang).name },
        lang,
        "Recalling today's phrases from memory is what makes them stick.",
        onFinished,
        loadResume = loadResume,
        onAnswered = onAnswered
    )
}

/** What an interrupted wrap-up left behind. Items run strictly in order with no re-queue, so a
 *  count of answered items plus the number answered correctly restores the exact position. */
data class RecallResume(val answered: Int, val correctCount: Int)

/**
 * The clean, typable phrases from a day's LEARN activities, used to build the wrap-up recall.
 *
 * - "A / B" entries keep BOTH alternatives — truncating to the first form orphaned the gloss
 *   ("he / she is" graded against a bare "on"); Grading.gradeRecall accepts any alternative.
 * - "headword — example" entries (pronunciation demos, verb showcases: "morar — Moras em
 *   Lisboa?") recall just the headword against the gloss's headword. Both sides must carry
 *   the dash; when only the target does, the halves don't pair and the item is a demo, not a
 *   producible phrase — dropped.
 * - Answer-leak guard: an item whose gloss contains its own answer ("ão" glossed as "nasal
 *   diphthong 'ão'") would print the answer inside the prompt — dropped.
 * - We also drop ellipsis stubs ("Zovem se…") and anything too long to reproduce fairly.
 *
 * Shared with the session builder so it only inserts a recall wrap-up when there's enough
 * to test.
 */
fun wrapupRecallPhrases(day: StudyDay): List<LearnItem> =
    day.activities
        .filter { it.type == ActivityKind.LEARN }
        .flatMap { it.items }
        .asSequence()
        .filter { it.en.isNotBlank() && "…" !in it.hr && "..." !in it.hr }
        .mapNotNull { item ->
            if (" — " in item.hr) {
                if (" — " !in item.en) return@mapNotNull null
                item.copy(
                    hr = item.hr.substringBefore(" — ").trim(),
                    en = item.en.substringBefore(" — ").trim()
                )
            } else item
        }
        .filterNot {
            Grading.normalize(it.en, strict = true)
                .contains(Grading.normalize(it.hr, strict = true))
        }
        .filter { it.hr.length in 2..40 && it.en.isNotBlank() }
        .distinctBy { it.hr.lowercase() }
        .toList()

/** Shared EN -> HR typed-recall runner used by both the deck recall drill and the day wrap-up. */
@Composable
private fun RecallRunner(
    container: AppContainer,
    items: List<DrillGen.Recall>,
    languageName: String,
    langCode: String,
    resultLine: String,
    onFinished: () -> Unit,
    /** Persisted resume for deterministic item lists (the wrap-up). Null = always start fresh
     *  (the deck drill draws random items, so a saved position would be meaningless). */
    loadResume: (suspend () -> RecallResume)? = null,
    onAnswered: (index: Int, correct: Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current

    // Gate on the async resume load exactly like ExerciseActivity, so the first frame never
    // flashes question one before jumping to the saved position.
    var resume by remember(items) {
        mutableStateOf(if (loadResume == null) RecallResume(0, 0) else null)
    }
    LaunchedEffect(items) { if (loadResume != null) resume = loadResume() }
    val resumed = resume ?: return

    var qIndex by remember(resumed) { mutableIntStateOf(resumed.answered.coerceIn(0, items.size)) }
    var score by remember(resumed) { mutableIntStateOf(resumed.correctCount.coerceAtMost(items.size)) }
    var input by remember(resumed) { mutableStateOf("") }
    var checked by remember(resumed) { mutableStateOf(false) }
    var correct by remember(resumed) { mutableStateOf(false) }
    var finished by remember(resumed) { mutableStateOf(resumed.answered >= items.size && items.isNotEmpty()) }
    val feedback = CorlangColors.feedback

    if (items.isEmpty()) {
        Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text("Next →") }
        return
    }
    if (finished) {
        DrillResult(score, items.size, resultLine, onFinished)
        return
    }

    val item = items[qIndex.coerceIn(0, items.lastIndex)]
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
            label = { Text("Write your answer") },
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
                // Text only in the verdict — no speaker on answer reveals (field feedback).
                Text(
                    if (correct) "✅ ${item.answerHr}" else "❌ ${item.answerHr}",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
        Button(
            onClick = {
                if (!checked) {
                    // Slash-aware ("on / ona je" accepts either) and pro-drop-aware: the
                    // English gloss licenses the subject pronoun, so "ja radim" == "radim".
                    correct = Grading.gradeRecall(item.answerHr, input, en = item.en, lang = langCode)
                    if (correct) { score++; Haptics.confirm(context) } else Haptics.reject(context)
                    onAnswered(qIndex, correct)
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
            "$score / $total",
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
