package com.corlang.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
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
    // The next sentence fades in rather than replacing this one between two frames.
    val itemAlpha = com.corlang.app.ui.theme.rememberAppearAlpha(qIndex)
    val reducedMotion = com.corlang.app.ui.theme.rememberReducedMotion()
    Column(modifier = Modifier.fillMaxWidth().alpha(itemAlpha)) {
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
        androidx.compose.animation.AnimatedVisibility(
            visible = chosen != null,
            enter = com.corlang.app.ui.theme.Motion.revealEnter(reducedMotion),
            exit = com.corlang.app.ui.theme.Motion.revealExit(reducedMotion)
        ) {
        // AnimatedVisibility stacks its children in a box, so the reveal and the button that
        // follows it need a column of their own.
        Column(modifier = Modifier.fillMaxWidth()) {
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
    started: Boolean = true,
    onStart: (animated: Boolean) -> Unit = {},
    /** The start-the-wrap-up gesture, as passed straight to [RecallRunner]: see there for all three. */
    startAlpha: Float = 1f,
    collapse: Float = 1f,
    contentAlpha: Float = 1f,
    onAnswered: (index: Int, correct: Boolean, attempt: Int) -> Unit = { _, _, _ -> },
    /**
     * Height to give the question block below the counter, so it sits in the MIDDLE of the room
     * the step has left rather than jammed under the bar with dead space beneath it. Only the
     * caller knows that room (it depends on the screen the player is drawn in), so it is passed
     * in; 0 = lay the block out at its own size, directly under the counter.
     */
    fillBelowCounter: Dp = 0.dp,
    /**
     * Opacity of the session's own furniture while the keyboard is up, computed by the caller off
     * the keyboard's animated inset. The counter above the question is part of that furniture and
     * has to dim and return WITH the rest of it - see [RecallRunner].
     */
    chromeAlpha: Float = 1f,
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
        onAnswered = onAnswered,
        started = started,
        onStart = onStart,
        startAlpha = startAlpha,
        collapse = collapse,
        contentAlpha = contentAlpha,
        centered = true,
        fillBelowCounter = fillBelowCounter,
        chromeAlpha = chromeAlpha
    )
}

/**
 * Misses allowed on one recall item before it is set down for the session.
 *
 * A typed recall graded on spelling and diacritics is not an MCQ: there is no "eventually you
 * tap the right one". Without a ceiling, one item a learner cannot produce (or one authoring
 * defect) would hold the whole lesson hostage, since the step no longer ends until the queue
 * empties. Three tries, then it goes, and the final score says so.
 */
const val RECALL_MAX_MISSES = 3

/**
 * What the recall's verdict panel is saying, held apart from the live question.
 *
 * The panel animates out, and the frame that STARTS that exit is the same frame the next item
 * arrives in: read the live state there and a correct answer's green panel repaints red, with the
 * next item's answer inside it, for as long as the exit lasts (field report: tapping Next after a
 * right answer flashed the wrong-answer box). So the check writes this once and nothing clears it
 * - it is the record of the answer that has just left, not of the question now on screen.
 */
private data class RecallVerdict(
    val correct: Boolean,
    val answer: String,
    /** Whether that answer was the item's last try, so the panel can say the item is going. */
    val setDown: Boolean
)

/**
 * What an interrupted recall left behind, per item: which items are already cleared and how
 * many times each was missed.
 *
 * It used to be a pair of counts ("answered", "of which right"), which worked only because
 * items ran strictly in order and never came back. Re-queuing breaks that the same way it broke
 * the exercise step (see ExerciseActivity's resume note): after one miss, position is no longer
 * a function of how many answers you gave.
 */
data class RecallResume(
    val cleared: Set<Int> = emptySet(),
    val misses: Map<Int, Int> = emptyMap()
) {
    /** Items given up on: [RECALL_MAX_MISSES] tries each, so they do not return this session. */
    val dropped: Set<Int> get() = misses.filterValues { it >= RECALL_MAX_MISSES }.keys - cleared

    val missedAny: Boolean get() = misses.isNotEmpty()

    /** The items still owed, in their original order. */
    fun queue(total: Int): List<Int> = (0 until total).filter { it !in cleared && it !in dropped }
}

/**
 * Where a just-answered item goes next.
 *
 * A cleared item leaves. A missed one goes to the BACK of what is left, so it returns with the
 * whole rest of the queue between the two tries - an immediate retry would only test copying the
 * answer still on screen. On its [RECALL_MAX_MISSES]-th miss it leaves too, unanswered.
 */
fun nextRecallQueue(queue: List<Int>, correct: Boolean, missesForItem: Int): List<Int> {
    if (queue.isEmpty()) return queue
    val head = queue.first()
    val rest = queue.drop(1)
    return if (correct || missesForItem >= RECALL_MAX_MISSES) rest else rest + head
}

/**
 * Per-item recall state read back from a day's persisted task checks.
 *
 * `<step>::q<i>` = item i cleared; `<step>::w<i>#<n>` = the n-th miss on item i. Builds before
 * 0.86.4 wrote a bare `<step>::w<i>` for a miss, which reads here as exactly one miss — the
 * right answer for a learner who upgrades mid-lesson.
 */
fun recallResumeFrom(ids: List<String>, stepId: String): RecallResume {
    val cleared = ids.filter { it.startsWith("$stepId::q") }
        .mapNotNull { it.removePrefix("$stepId::q").toIntOrNull() }
        .toSet()
    val misses = ids.filter { it.startsWith("$stepId::w") }
        .mapNotNull { it.removePrefix("$stepId::w").substringBefore('#').toIntOrNull() }
        .groupingBy { it }
        .eachCount()
    return RecallResume(cleared, misses)
}

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
 * - [PAIR_SYMBOLS] guard: a transformation row ("kava → kavu") is a table, not a phrase. It
 *   asks the learner to type a symbol no phone keyboard offers, and Grading.normalize does not
 *   strip it, so the whole item was ungradable (field report: a day 11 wrap-up scored 0/8 for
 *   answers that were linguistically perfect). This is a BACKSTOP: the content gate forbids the
 *   shape outright, so in a clean course nothing reaches it.
 * - Prompt uniqueness: two rows glossed identically ask the same English question with two
 *   different right answers, which cannot be answered by anyone. First one wins.
 *
 * Shared with the session builder so it only inserts a recall wrap-up when there's enough
 * to test.
 */
fun wrapupRecallPhrases(day: StudyDay): List<LearnItem> =
    recallCandidates(day)
        .filterNot { item -> PAIR_SYMBOLS.any { it in item.hr || it in item.en } }
        .distinctBy { Grading.normalize(it.en, strict = true) }

/**
 * Everything a day OFFERS the wrap-up, before the two guards that hide authoring defects.
 * Split out so the content gate can see what the guards are covering up: measured against
 * [wrapupRecallPhrases] the defects are invisible (they have already been dropped), and a gate
 * that can never fail is worse than no gate at all (registry §V).
 */
fun recallCandidates(day: StudyDay): List<LearnItem> =
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
        // 80, not 40. The cap exists so a learner is never asked to retype a paragraph, but
        // 40 was set when the wrap-up was designed around A0 vocabulary, and B1 teaches in
        // SENTENCES: at 40 characters, 88 of 171 B1 lessons offered fewer than four producible
        // items and silently fell back to replaying their exercise. A0 gains nothing from the
        // change (its items are all short), A1 and A2 gain real sentences they already teach.
        // Sentence-length targets are graded diacritic-leniently, see Grading.isSentenceTarget.
        .filter { it.hr.length in 2..RECALL_MAX_CHARS && it.en.isNotBlank() }
        .distinctBy { it.hr.lowercase() }
        .toList()

/**
 * Characters that turn a LEARN row into a table rather than a producible phrase: an arrow or an
 * equation is a RELATION between two forms, and asking a learner to type the relation is asking
 * for something their keyboard cannot produce and their mouth would never say. Kept in one place
 * so the app filter and the content gate (`typedAnswersAreTypable`) can never drift apart.
 */
const val RECALL_MAX_CHARS = 80

val PAIR_SYMBOLS = listOf("→", "←", "↔", "⇒", "=", "+", "«", "»", "–", "—", "✓", "✗")

/**
 * Shared EN -> HR typed-recall runner used by both the deck recall drill and the day wrap-up.
 *
 * A missed item is not a dead end. The same prompt returns at the END of the queue — never as an
 * immediate retry, which would only test copying the answer still on screen — and keeps coming
 * back until it is produced correctly. That is the whole point of a wrap-up: the items you can
 * already say are not the ones worth repeating. After [RECALL_MAX_MISSES] tries an item is set
 * down for the session, which is why the final score can read less than the total.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
    /** `attempt` is which try this was on that item: 1 on the first miss, 3 on the last. */
    onAnswered: (index: Int, correct: Boolean, attempt: Int) -> Unit = { _, _, _ -> },
    /** Wrap-up only: the step card above stands as an intro panel until Start is tapped. */
    started: Boolean = true,
    /**
     * Wrap-up only. `animated` is false when the start was not a tap - a wrap-up already under way
     * announces itself the moment it composes, and a panel that collapses on its own, before the
     * learner has touched anything, reads as a glitch rather than as a response.
     */
    onStart: (animated: Boolean) -> Unit = {},
    /**
     * Wrap-up only: opacity of the Start button. The intro panel above it does not vanish, it is
     * eaten from below over most of a second; the button sits under the same rising edge and
     * goes with it, rather than blinking out while the panel is still travelling.
     */
    startAlpha: Float = 1f,
    /**
     * Wrap-up only: how far through that collapse we are, 0 to 1. The region between the panel
     * and the session's Back row travels with it - from the height the Start button needed to
     * the height the question block needs - so the buttons below slide to their final place
     * rather than being teleported there on the last frame.
     */
    collapse: Float = 1f,
    /**
     * Wrap-up only: opacity of the question block. It arrives AFTER the room for it does, on its
     * own slower clock, so the lesson fades up into a screen that has already stopped moving.
     */
    contentAlpha: Float = 1f,
    /** Wrap-up only: prompt, field and verdict centred, with the counter above them. */
    centered: Boolean = false,
    /** Wrap-up only: see [WrapupRecall]. Vertically centres everything under the counter. */
    fillBelowCounter: Dp = 0.dp,
    /**
     * Wrap-up only: opacity of the counter while the keyboard is up, so the eye stays on the
     * prompt and the field - everything else on screen is context already read.
     *
     * Passed in, and deliberately not animated here. This used to be its own animateFloatAsState
     * off isImeVisible, which is a boolean that flips at the START of the keyboard's animation,
     * while the rest of the session's furniture rides the keyboard's live inset. Putting the
     * keyboard away therefore brought the header and the progress bar back with the keyboard and
     * the counter back afterwards, on its own spring: one line arriving late to its own screen.
     */
    chromeAlpha: Float = 1f
) {
    val context = LocalContext.current

    // Gate on the async resume load exactly like ExerciseActivity, so the first frame never
    // flashes question one before jumping to the saved position.
    var resume by remember(items) {
        mutableStateOf(if (loadResume == null) RecallResume() else null)
    }
    LaunchedEffect(items) { if (loadResume != null) resume = loadResume() }
    val resumed = resume ?: return

    // A wrap-up already under way skips its own intro: it was read before the learner walked away.
    LaunchedEffect(resumed) {
        if (resumed.cleared.isNotEmpty() || resumed.missedAny) onStart(false)
    }

    // Live queue of remaining item INDICES (identity survives re-queuing), mirroring the exercise
    // step. A miss re-appends; the third miss drops the item instead.
    val queue = remember(resumed) {
        mutableStateListOf<Int>().apply { addAll(resumed.queue(items.size)) }
    }
    val misses = remember(resumed) {
        mutableStateMapOf<Int, Int>().apply { putAll(resumed.misses) }
    }
    var solved by remember(resumed) {
        mutableIntStateOf(resumed.cleared.count { it in items.indices })
    }
    var missedAny by remember(resumed) { mutableStateOf(resumed.missedAny) }
    var input by remember(resumed) { mutableStateOf("") }
    var checked by remember(resumed) { mutableStateOf(false) }
    var correct by remember(resumed) { mutableStateOf(false) }
    var verdict by remember(resumed) { mutableStateOf<RecallVerdict?>(null) }
    var finished by remember(resumed) {
        mutableStateOf(items.isNotEmpty() && resumed.queue(items.size).isEmpty())
    }
    val feedback = CorlangColors.feedback

    // Wrap-up only, and deliberately declared ABOVE the start gate below: the two states of the
    // region under the panel are composed in different branches, and the height of the one on
    // its way out has to survive being replaced by the one on its way in.
    var buttonH by remember { mutableIntStateOf(0) }
    var blockH by remember { mutableIntStateOf(0) }

    if (items.isEmpty()) {
        Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text("Next →") }
        return
    }
    // The intro panel is the step card above; this is the only thing under it until Start.
    if (!started) {
        Button(
            onClick = { onStart(true) },
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { buttonH = it.height }
                .alpha(startAlpha)
        ) { Text("Start recall →") }
        return
    }
    if (finished || queue.isEmpty()) {
        DrillResult(solved, items.size, resultLine, onFinished, missedAny = missedAny)
        return
    }

    val idx = queue.first()
    val item = items[idx]

    // The prompt fades in on every new item. Keyed on the queue as well as the index: a missed
    // item comes back later at the same index, and that return is a new prompt like any other.
    val itemAlpha = com.corlang.app.ui.theme.rememberAppearAlpha(idx to queue.size)
    val reducedMotion = com.corlang.app.ui.theme.rememberReducedMotion()

    // While the keyboard is up the chrome steps back, so the eye stays on the prompt and the
    // field: everything else on screen is context the learner has already read.
    val imeVisible = WindowInsets.isImeVisible
    // imePadding on the session column stops the keyboard COVERING the field; it does not scroll
    // the field to where it can be seen. This does.
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(imeVisible, idx) { if (imeVisible) bring.bringIntoView() }

    val counter = "$solved/${items.size}" +
        if (queue.size > 1) "  ·  ${queue.size} left" else ""

    /*
     * Wrap-up only: the region between the collapsing panel and the session's Back row.
     *
     * It travels from the height the Start button needed to the height the question block needs,
     * on the panel's own clock, so everything BELOW it - Back, Back to sections - slides to its
     * final place instead of being teleported there the frame the panel lands. Both ends are
     * measured rather than assumed: the button's while it was the only thing here, the block's
     * from its real layout (which is why the block is measured at full size and then clipped,
     * not laid out small and stretched).
     *
     * Zero means "no override": before the gesture there is nothing to travel between, and after
     * it the block simply is its own height.
     */
    val regionH = when {
        !centered || collapse >= 1f -> 0
        blockH > 0 -> lerp(buttonH, blockH, collapse.coerceIn(0f, 1f))
        else -> buttonH
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (regionH > 0) Modifier
                    .layout { measurable, constraints ->
                        val p = measurable.measure(constraints)
                        layout(p.width, regionH) { p.place(0, 0) }
                    }
                    .clipToBounds()
                else Modifier
            )
    ) {
    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { blockH = it.height }
            .alpha(contentAlpha)
    ) {
        // The counter keeps its place right under the step bar. Everything below it - the prompt,
        // the field and the verdict - goes in a box that takes whatever room the step was given
        // and centres the work inside it, rather than stacking from the top and leaving the
        // bottom half of the screen empty.
        if (centered) {
            Text(
                counter,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(chromeAlpha).padding(bottom = 12.dp)
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (fillBelowCounter > 0.dp) Modifier.heightIn(min = fillBelowCounter)
                    else Modifier
                )
        ) {
            Column(
                horizontalAlignment =
                    if (centered) Alignment.CenterHorizontally else Alignment.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (centered) {
                    Text(
                        item.en,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().alpha(itemAlpha)
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.alpha(itemAlpha)
                    ) {
                        Text(
                            item.en,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(counter, style = MaterialTheme.typography.bodySmall)
                    }
                }
                item.posHint?.let {
                    Text(
                        it, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = if (centered) TextAlign.Center else TextAlign.Start
                    )
                }
                // The ask, spelled out on every item. "Write your answer" alone left learners guessing
                // which language to answer in and whether accents mattered (field report), and the
                // instruction has to sit next to the field, not only in the step header they scrolled past.
                Text(
                    "Write it in $languageName. Spelling and accents count.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Column(modifier = Modifier.fillMaxWidth().bringIntoViewRequester(bring)) {
                    /*
                     * Neither disabled nor readOnly once the answer is checked, and that is the
                     * whole point: both of them end the field's input session, which takes the
                     * keyboard down with it. Eight items then meant watching the keyboard leave
                     * and come back eight times, and on the wrap-up - which is typed straight
                     * through - that is most of what you would see it do.
                     *
                     * The lock is onValueChange refusing everything while `checked`, which is the
                     * only lock that matters: the answer cannot be edited after it has been
                     * graded. The verdict panel below says the item is over; the field does not
                     * need to go grey to repeat it.
                     */
                    OutlinedTextField(
                        value = input,
                        onValueChange = { if (!checked) input = it },
                        label = { Text("Your answer in $languageName") },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                    androidx.compose.animation.AnimatedVisibility(
                        visible = checked,
                        enter = com.corlang.app.ui.theme.Motion.revealEnter(reducedMotion),
                        exit = com.corlang.app.ui.theme.Motion.revealExit(reducedMotion)
                    ) {
                        val shown = verdict
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (shown?.correct == true) feedback.correctContainer
                                    else feedback.wrongContainer,
                            contentColor = if (shown?.correct == true) feedback.onCorrectContainer
                                           else feedback.onWrongContainer,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                // Text only in the verdict — no speaker on answer reveals (field feedback).
                                Text(
                                    (if (shown?.correct == true) "✅ " else "❌ ") +
                                        (shown?.answer ?: ""),
                                    fontWeight = FontWeight.Bold
                                )
                                // Say what happens next out loud, or a re-queued item looks like the app
                                // repeating itself and a set-down item looks like the app losing it.
                                if (shown != null && !shown.correct) {
                                    Text(
                                        if (shown.setDown) "Three tries — setting this one aside for today."
                                        else "This one comes back before the end.",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            if (!checked) {
                                // Slash-aware ("on / ona je" accepts either) and pro-drop-aware: the
                                // English gloss licenses the subject pronoun, so "ja radim" == "radim".
                                correct = Grading.gradeRecall(item.answerHr, input, en = item.en, lang = langCode)
                                if (correct) {
                                    solved++
                                    verdict = RecallVerdict(true, item.answerHr, false)
                                    Haptics.confirm(context)
                                    onAnswered(idx, true, (misses[idx] ?: 0) + 1)
                                } else {
                                    val attempt = (misses[idx] ?: 0) + 1
                                    misses[idx] = attempt
                                    missedAny = true
                                    verdict = RecallVerdict(
                                        false, item.answerHr, attempt >= RECALL_MAX_MISSES
                                    )
                                    Haptics.reject(context)
                                    onAnswered(idx, false, attempt)
                                }
                                checked = true
                            } else {
                                val next = nextRecallQueue(queue, correct, misses[idx] ?: 0)
                                queue.clear(); queue.addAll(next)
                                // `verdict` is deliberately NOT cleared: the panel is on its way
                                // out and has to keep the words and the colour it went in with.
                                input = ""; checked = false; correct = false
                                if (queue.isEmpty()) finished = true
                            }
                        },
                        enabled = checked || input.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(
                            when {
                                !checked -> "Check"
                                nextRecallQueue(queue, correct, misses[idx] ?: 0).isEmpty() -> "See result"
                                else -> "Next →"
                            }
                        )
                    }
                }
            }
        }
    }
        // The Start button on its way out, over the question block on its way in. They never
        // overlap in sight - the button is gone by 65% of the collapse and the block only starts
        // arriving at 80% - but they share this one region, so neither one's departure nor the
        // other's arrival moves anything else on the screen.
        if (centered && startAlpha > 0f) {
            Button(
                onClick = { onStart(true) },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .alpha(startAlpha)
            ) { Text("Start recall →") }
        }
        // Until the block is all the way here, a tap in this region ends the gesture rather than
        // landing on a text field that is still fading in and cannot be read yet.
        if (centered && contentAlpha < 1f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) { detectTapGestures { onStart(true) } }
            )
        }
    }
}

@Composable
private fun DrillResult(
    score: Int,
    total: Int,
    line: String,
    onFinished: () -> Unit,
    /** Re-queuing drills (the recall runner) only: names which of the three ends this was.
     *  Null for the one-pass drills, which have no story beyond their score. */
    missedAny: Boolean? = null
) {
    // Three ends, three different things to say. One "well done" over a score the learner can see
    // is lower than the total reads as the app not having noticed.
    val verdict = missedAny?.let {
        when {
            score >= total && !it -> "Perfect, first try on every phrase."
            score >= total -> "All correct now, the ones you missed came back until you nailed them."
            else -> "The ones you set aside after three tries are the ones to look at tomorrow."
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            "$score / $total",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        verdict?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
        Text(
            line,
            style = if (verdict == null) MaterialTheme.typography.bodyMedium
                    else MaterialTheme.typography.bodySmall,
            color = if (verdict == null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
        Button(onClick = onFinished, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text("Done, next step →")
        }
        Spacer(Modifier.height(8.dp))
    }
}
