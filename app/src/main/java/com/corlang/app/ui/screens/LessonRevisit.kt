package com.corlang.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.data.Fsrs
import com.corlang.app.data.SessionCard
import com.corlang.app.data.SrsGrade
import com.corlang.app.data.WordsRepository
import com.corlang.app.data.model.StudyDay
import com.corlang.app.ui.Haptics
import kotlinx.coroutines.launch

/**
 * Revisiting a finished lesson: choose the part you want instead of walking the whole thing again.
 *
 * A revisit used to be all-or-nothing. The player wiped the lesson's step marks and reopened at
 * step one, so wanting the dialogue in the middle of lesson 9 meant tapping through the words, the
 * learn block and the exercise to reach it. This screen stands in front of the player on a
 * completed lesson and offers its steps as jump targets, plus a pass over the words that lesson
 * introduced.
 *
 * Nothing chosen here re-marks the day: a section replay writes no step checks, no completion and
 * no streak (see the `startAt` parameter of [SessionPlayer]). The one thing that does persist is
 * word grading, under the once-a-day rule in [WordsRepository.practiceReview].
 *
 * There is no "whole lesson again" button, and none is needed: the first section runs on to the
 * end, and the flashcards the lesson opens with are the button above it.
 */

/**
 * Indices of [steps] a learner can jump straight to: the lesson's teaching parts, in lesson order.
 *
 * Four kinds are not on the list. The intro and the finish are not parts of the lesson, the new
 * words step has nothing left to serve on a lesson already done (its words are what the flashcard
 * pass above the list re-runs), and the due-words review is the day's SRS queue rather than
 * anything this lesson taught - the Review tab owns that.
 */
fun revisitSections(steps: List<SessionStep>): List<Int> =
    steps.indices.filter {
        when (steps[it].kind) {
            StepKind.INFO, StepKind.COMPLETE, StepKind.WORDS, StepKind.REVIEW -> false
            else -> true
        }
    }

/**
 * One button label per section, in lesson order: the kind of work it is, numbered only when the
 * lesson has more than one of that kind (two LEARN activities are common, and two buttons both
 * reading "Learn" would be a coin toss).
 */
fun revisitLabels(steps: List<SessionStep>, sections: List<Int>): List<String> {
    val kinds = sections.map { sectionLabel(steps[it].kind) }
    val totals = kinds.groupingBy { it }.eachCount()
    val seen = mutableMapOf<String, Int>()
    return kinds.map { k ->
        if (totals.getValue(k) == 1) k
        else {
            val n = (seen[k] ?: 0) + 1
            seen[k] = n
            "$k $n"
        }
    }
}

/** The short kind label a section row carries, matching the player's own step card. */
internal fun sectionLabel(kind: StepKind): String = when (kind) {
    StepKind.WORDS -> "New words"
    StepKind.REVIEW -> "Review"
    StepKind.GENDER -> "Drill"
    StepKind.CLOZE -> "Case drill"
    StepKind.RECALL -> "Recall drill"
    StepKind.WRAPUP -> "Wrap-Up"
    StepKind.LEARN -> "Learn"
    StepKind.EXERCISE -> "Exercise"
    StepKind.DIALOGUE -> "Dialogue"
    else -> "Task"
}

@Composable
fun LessonRevisit(
    container: AppContainer,
    lang: String,
    day: StudyDay,
    onPickSection: (Int) -> Unit,
    onExit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val languageName = remember(lang) { container.content.meta(lang).name }
    val steps = remember(lang, day.day) { buildSessionSteps(day, languageName) }
    val sections = remember(steps) { revisitSections(steps) }

    // The lesson's own block of the deck. Null until loaded so the button never flashes a wrong
    // count, and re-read after a pass so the "counts today" line below it stays true.
    val perLesson by container.languagePrefs.newWordsPerDay
        .collectAsState(initial = Fsrs.NEW_WORDS_PER_DAY)
    var lessonCards by remember(lang, day.day) { mutableStateOf<List<SessionCard>?>(null) }
    var reload by remember(lang, day.day) { mutableIntStateOf(0) }
    LaunchedEffect(lang, day.day, perLesson, reload) {
        lessonCards = container.words.lessonWords(lang, day.day, perLesson)
    }

    // The word pass itself runs right here: a short queue over this lesson's words, graded by the
    // once-a-day practice rule. Deliberately NOT the daily session, and it never touches the Words
    // tab's snapshot, so a half-finished daily queue survives a revisit untouched.
    val queue = remember(lang, day.day) { mutableStateListOf<SessionCard>() }
    var inWords by remember(lang, day.day) { mutableStateOf(false) }
    var served by remember(lang, day.day) { mutableIntStateOf(0) }
    var wordsDone by remember(lang, day.day) { mutableIntStateOf(0) }
    var wordsTotal by remember(lang, day.day) { mutableIntStateOf(0) }

    fun gradeWord(g: SrsGrade) {
        if (queue.isEmpty()) return   // late fling/tap after the pass already ended
        val card = queue.removeAt(0)
        served++
        if (g == SrsGrade.AGAIN) { Haptics.reject(context); queue.add(card) }
        else { Haptics.confirm(context); wordsDone++ }
        // App-scoped: leaving the instant after the last swipe must not cancel the write.
        container.appScope.launch { container.words.gradePractice(lang, card.word.id, g) }
        if (queue.isEmpty()) { inWords = false; reload++ }
    }

    if (inWords && queue.isNotEmpty()) {
        // System back leaves the pass back to the chooser (grades already persisted).
        androidx.activity.compose.BackHandler { inWords = false }
        WordSession(
            card = queue.first(),
            cardKey = served,
            tts = container.tts,
            languageName = languageName,
            review = true,
            done = wordsDone,
            total = wordsTotal,
            onGrade = ::gradeWord,
            onExit = { inWords = false }
        )
        return
    }

    val cards = lessonCards
    val today = WordsRepository.todayEpochDay()
    // How many of this lesson's words a pass would actually reschedule right now. Anything already
    // reviewed today rides along for the practice but leaves the schedule alone.
    val countsToday = cards.orEmpty().count { c ->
        val r = c.review
        r != null && (r.reps == 0 || r.lastReviewEpochDay < today)
    }
    val labels = remember(steps, sections) { revisitLabels(steps, sections) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Lesson ${day.day} · ${day.level} · complete ✓",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(day.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Select the part you want. It runs on to the end of the lesson from there, and none of " +
                "it re-marks anything: the lesson stays complete, and your streak and today's goal " +
                "are untouched.",
            style = com.corlang.app.ui.theme.CorlangType.reading,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // The flashcards lead, because that is how the lesson itself opens. Offered only when this
        // lesson actually introduced words the learner has met: a placement start can leave an
        // early lesson's block behind entirely.
        if (!cards.isNullOrEmpty()) {
            RevisitButton("Review lesson words (${cards.size})") {
                scope.launch {
                    val fresh = container.words.lessonWords(lang, day.day, perLesson)
                    if (fresh.isEmpty()) return@launch
                    queue.clear(); queue.addAll(fresh.shuffled())
                    wordsTotal = fresh.size; wordsDone = 0; served = 0
                    inWords = true
                }
            }
        }

        sections.forEachIndexed { n, stepIndex ->
            RevisitButton(labels[n]) { onPickSection(stepIndex) }
        }

        // Under the whole run rather than under the word button, so the choices read as one column
        // of boxes. It answers the question the word button raises: does this count?
        if (!cards.isNullOrEmpty()) {
            Text(
                when {
                    countsToday == 0 ->
                        "The words were reviewed already today, so another pass is free practice: " +
                            "it will not move their schedule. Tomorrow it counts."
                    countsToday == cards.size ->
                        "The words count as a real review: answer well and they space out further, " +
                            "miss one and it comes back sooner."
                    else ->
                        "$countsToday of the ${cards.size} words count towards spacing today; the " +
                            "rest were reviewed already and ride along as practice."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        RevisitButton("Exit", modifier = Modifier.padding(top = 8.dp), onClick = onExit)
        Spacer(Modifier.height(24.dp))
    }
}

/** One choice on the revisit screen: a bordered box with its label centred, nothing else. */
@Composable
private fun RevisitButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 12.dp)
        )
    }
}

