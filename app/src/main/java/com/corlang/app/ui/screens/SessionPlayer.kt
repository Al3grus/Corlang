package com.corlang.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
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
import com.corlang.app.ui.components.SpeakerButton
import com.corlang.app.ui.navigation.Dest
import com.corlang.app.ui.theme.CorlangColors
import com.corlang.app.ui.theme.Motion
import com.corlang.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch

/**
 * The guided daily session: the app walks you through today's work one step at a time.
 * Every step is either an in-app activity launched right here (word review, gender drill),
 * a deep link with a done-gate (e-course unit), or a concrete task. Step completion is
 * persisted per day (day_task_check), so leaving and returning resumes exactly.
 */

enum class StepKind { INFO, TASK, LINK, WORDS, REVIEW, GENDER, CLOZE, RECALL, LEARN, EXERCISE, DIALOGUE, WRAPUP, COMPLETE }

data class SessionStep(
    val id: String,
    val kind: StepKind,
    val title: String,
    val detail: String = "",
    val url: String? = null,
    val navRoute: String? = null,
    /** Which phase of the evidence-based session shape this step belongs to (docs/sources/method.md). */
    val phase: String = "",
    /** Index into day.activities for LEARN/EXERCISE/DIALOGUE steps. */
    val activityIndex: Int = -1
)

/** Derives the guided steps for a plan day from its drills + review block. */
fun buildSessionSteps(
    day: StudyDay,
    resourceUrls: Map<String, String?>,
    languageName: String = "Croatian"
): List<SessionStep> {
    val steps = mutableListOf<SessionStep>()

    steps += SessionStep(
        id = "intro", kind = StepKind.INFO,
        title = day.title,
        detail = "${day.objective}\n\nWhy this matters: ${day.paretoFocus}"
    )

    // The habit anchor always comes first: clear the due words.
    steps += SessionStep(
        id = "words", kind = StepKind.WORDS,
        title = "New words",
        detail = "Learn the new words this lesson introduces.",
        navRoute = Dest.WORDS.route,
        phase = "1 · Recall"
    )

    fun urlFor(text: String): String? {
        val t = text.lowercase()
        return when {
            "a1.ffzg" in t || "a1.hr" in t -> "https://a1.ffzg.unizg.hr/"
            "a2.ffzg" in t || "a2.hr" in t -> "https://a2.ffzg.unizg.hr/"
            "unit" in t || "e-tečaj" in t ->
                day.resources.firstNotNullOfOrNull { r ->
                    resourceUrls[r]?.takeIf { "ffzg" in it }
                }
            "hrt" in t -> "https://vijesti.hrt.hr/"
            else -> null
        }
    }

    fun navFor(text: String): String? {
        val t = text.lowercase()
        return when {
            "words tab" in t || "due words" in t -> Dest.WORDS.route
            "quiz" in t || "mock exam" in t -> Dest.PRACTICE.route
            "cheatsheet" in t || "grammar tab" in t || "feynman" in t || "teach" in t -> Dest.LEARN.route
            else -> null
        }
    }

    val genderRegex = Regex("gender|rod imenic", RegexOption.IGNORE_CASE)
    val caseRegex = Regex(
        "accusative|genitive|dative|locative|instrumental|vocative|nominative|declension|padež",
        RegexOption.IGNORE_CASE
    )
    val recallRegex = Regex(
        "from memory|recall|re-test|test yourself|without looking",
        RegexOption.IGNORE_CASE
    )
    val outputRegex = Regex(
        "speak|say |aloud|write|wrote|conversation|partner|spouse|role-play|monologue|record|retell|describe|tell |ask your|interview",
        RegexOption.IGNORE_CASE
    )

    fun addItem(prefix: String, index: Int, text: String, isReview: Boolean) {
        val url = urlFor(text)
        val nav = navFor(text)
        // Instruction-shaped drills become real in-app exercises.
        if (genderRegex.containsMatchIn(text)) {
            steps += SessionStep(
                id = "$prefix-$index", kind = StepKind.GENDER,
                title = "Gender drill",
                detail = text,
                phase = "3 · Practice"
            )
            return
        }
        if (nav != Dest.WORDS.route && url == null && caseRegex.containsMatchIn(text)) {
            steps += SessionStep(
                id = "$prefix-$index", kind = StepKind.CLOZE,
                title = "Case drill, the right form in context",
                detail = text,
                phase = if (isReview) "5 · Wrap-up" else "3 · Practice"
            )
            return
        }
        if (nav == null && url == null && recallRegex.containsMatchIn(text)) {
            steps += SessionStep(
                id = "$prefix-$index", kind = StepKind.RECALL,
                title = "Recall drill, type the $languageName",
                detail = text,
                phase = if (isReview) "5 · Wrap-up" else "3 · Practice"
            )
            return
        }
        val phase = when {
            isReview -> "5 · Wrap-up"
            url != null -> "2 · Input"
            outputRegex.containsMatchIn(text) -> "4 · Output"
            else -> "3 · Practice"
        }
        steps += SessionStep(
            id = "$prefix-$index",
            kind = if (url != null) StepKind.LINK else StepKind.TASK,
            title = text,
            url = url,
            navRoute = nav,
            phase = phase
        )
    }

    if (day.activities.isNotEmpty()) {
        // The upgraded path: the day's content is embedded. Keep only course-link drills
        // from the text (e.g. "do Unit 12"), then run the real lesson activities.
        day.drills.forEachIndexed { i, d ->
            if (urlFor(d) != null) {
                steps += SessionStep(
                    id = "drill-$i", kind = StepKind.LINK, title = d,
                    url = urlFor(d), phase = "2 · Input"
                )
            }
        }
        day.activities.forEachIndexed { i, a ->
            val (kind, phase) = when (a.type) {
                com.corlang.app.data.model.ActivityKind.LEARN -> StepKind.LEARN to "2 · Input"
                com.corlang.app.data.model.ActivityKind.EXERCISE -> StepKind.EXERCISE to "3 · Practice"
                com.corlang.app.data.model.ActivityKind.DIALOGUE -> StepKind.DIALOGUE to "4 · Output"
            }
            steps += SessionStep(
                id = "activity-$i", kind = kind, title = a.title,
                detail = "", phase = phase, activityIndex = i
            )
        }
        // Wrap-up: a real from-memory recall of TODAY'S taught phrases, replacing the old
        // free-text review instructions that had no exercise behind them. Falls back to the
        // text review only when the day has too little LEARN content to build a recall.
        val exerciseIndex = day.activities.indexOfFirst {
            it.type == com.corlang.app.data.model.ActivityKind.EXERCISE
        }
        when {
            // Best: produce today's taught phrases from memory.
            wrapupRecallPhrases(day).size >= 4 -> steps += SessionStep(
                id = "wrapup", kind = StepKind.WRAPUP,
                title = "Wrap-up: recall today's phrases from memory",
                detail = "No peeking. Produce the $languageName for each phrase you learned today.",
                phase = "5 · Wrap-up"
            )
            // Fallback for long-sentence days: a quick retest of today's exercise (still real content).
            exerciseIndex >= 0 -> steps += SessionStep(
                id = "wrapup", kind = StepKind.EXERCISE,
                title = "Wrap-up: quick retest",
                detail = "", phase = "5 · Wrap-up", activityIndex = exerciseIndex
            )
            // Last resort (bare days only): the plan's text review items.
            else -> day.reviewBlock.items.forEachIndexed { i, r ->
                addItem("review", i, r, isReview = true)
            }
        }
    } else {
        day.drills.forEachIndexed { i, d -> addItem("drill", i, d, isReview = false) }
        day.reviewBlock.items.forEachIndexed { i, r -> addItem("review", i, r, isReview = true) }
    }

    // Keep the evidence-based order: Recall → Input → Practice → Output → Wrap-up.
    val order = listOf("", "1 · Recall", "2 · Input", "3 · Practice", "4 · Output", "5 · Wrap-up")
    val head = steps.filter { it.kind == StepKind.INFO }
    val body = steps.filterNot { it.kind == StepKind.INFO }
        .sortedBy { order.indexOf(it.phase).let { i -> if (i < 0) 99 else i } }
    steps.clear(); steps += head; steps += body

    // Reviews run last, as end-of-session consolidation (retrieval practice). Capped in-session so
    // they can't pile up — overflow stays in the Words tab. Empty when nothing is due yet.
    steps += SessionStep(
        id = "review", kind = StepKind.REVIEW,
        title = "Review due words",
        detail = "A quick spaced-repetition pass over words coming due — closes out the day.",
        phase = "6 · Review"
    )

    steps += SessionStep(
        id = "complete", kind = StepKind.COMPLETE,
        title = "Day ${day.day} done",
        detail = "Mark the day complete, streak credited, plan advances."
    )
    return steps
}

@Composable
fun SessionPlayer(
    container: AppContainer,
    lang: String,
    day: StudyDay,
    totalDays: Int,
    onNavigate: (String) -> Unit,
    onExit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val resourceUrls = remember(lang) {
        container.content.resources(lang).resources.associate { it.name to it.url }
    }
    val steps = remember(lang, day.day) {
        buildSessionSteps(day, resourceUrls, container.content.meta(lang).name)
    }

    val checks by container.progress.dayTaskChecks(lang, day.day)
        .collectAsState(initial = emptyList())
    val doneIds = checks.map { it.itemId }.toSet()

    // The NEW-words step = this lesson's unlocked words (deck order, first day.day * perLesson) not
    // yet introduced. The REVIEW step = due cards, capped so they can't pile up (overflow → Words
    // tab). Both are lesson-scoped, so an earlier lesson never marks this one done.
    val reviews by container.words.reviews(lang).collectAsState(initial = emptyList())
    val perLesson by container.languagePrefs.newWordsPerDay.collectAsState(initial = 10)
    val today = WordsRepository.todayEpochDay()
    val allWords = remember(lang) { container.words.allWords(lang) }
    val dueNow = reviews.count { it.dueEpochDay <= today }
    val seenIds = remember(reviews) { reviews.map { it.wordId }.toSet() }
    val unlockedNew = allWords.take(day.day * perLesson).count { it.id !in seenIds }
    // One lesson serves at most perLesson new words, even when a placement jump unlocked a large
    // backlog — it drains one lesson-sized block at a time, never a 300-card dump.
    val newBlock = minOf(unlockedNew, perLesson)
    val reviewPending = minOf(dueNow, Fsrs.REVIEW_CAP)

    fun stepDone(s: SessionStep): Boolean = when (s.kind) {
        // Done when nothing is waiting OR this day's block was completed. The check is written
        // only by finishing the block (never by skipping), and it matters when a capped block
        // leaves a backlog/overflow behind — the step must still count as done for the day.
        StepKind.WORDS -> unlockedNew == 0 || s.id in doneIds
        StepKind.REVIEW -> reviewPending == 0 || s.id in doneIds
        else -> s.id in doneIds
    }

    // Start at the first unfinished step (resume).
    var index by rememberSaveable(day.day) {
        mutableIntStateOf(0)
    }
    // On first composition per day, jump past finished steps.
    var resumed by rememberSaveable(day.day) { mutableStateOf(false) }
    if (!resumed && checks.isNotEmpty()) {
        val firstOpen = steps.indexOfFirst { it.kind != StepKind.INFO && !stepDone(it) }
        if (firstOpen > 0) index = firstOpen
        resumed = true
    }

    val doneCount = steps.count { it.kind != StepKind.INFO && it.kind != StepKind.COMPLETE && stepDone(it) }
    val actionCount = steps.count { it.kind != StepKind.INFO && it.kind != StepKind.COMPLETE }
    val reducedMotion = rememberReducedMotion()

    // In-lesson word block, shared by the new-words step and the review step. New vocabulary only
    // ever enters through lessons; the review block is capped. Grading persists per card.
    val wordQueue = remember(day.day) { mutableStateListOf<SessionCard>() }
    var inWords by remember(day.day) { mutableStateOf(false) }
    var wordServed by remember(day.day) { mutableIntStateOf(0) }
    var wordDone by remember(day.day) { mutableIntStateOf(0) }
    var wordTotal by remember(day.day) { mutableIntStateOf(0) }
    var wordStepId by remember(day.day) { mutableStateOf("words") }
    var wordIsReview by remember(day.day) { mutableStateOf(false) }

    fun markStepDoneAndAdvance(stepId: String) {
        // App-scoped: persists even if the player is closed the same instant.
        container.appScope.launch { container.progress.setDayTask(lang, day.day, stepId, true) }
        if (index < steps.lastIndex) index++
    }

    fun startWordBlock(stepId: String, isReview: Boolean, build: suspend () -> List<SessionCard>) {
        scope.launch {
            val cards = build()
            if (cards.isEmpty()) { markStepDoneAndAdvance(stepId); return@launch }
            wordStepId = stepId; wordIsReview = isReview
            wordQueue.clear(); wordQueue.addAll(cards)
            wordTotal = cards.size; wordDone = 0; wordServed = 0
            inWords = true
        }
    }

    fun gradeLessonWord(g: SrsGrade) {
        if (wordQueue.isEmpty()) return   // late fling/tap after the block already ended
        val card = wordQueue.removeAt(0)
        wordServed++
        if (g == SrsGrade.AGAIN) { Haptics.reject(context); wordQueue.add(card) }
        else { Haptics.confirm(context); wordDone++ }
        // App-scoped: an exit right after the last swipe must not cancel the FSRS write.
        container.appScope.launch { container.words.grade(lang, card.word.id, g) }
        if (wordQueue.isEmpty()) {
            inWords = false
            markStepDoneAndAdvance(wordStepId)
        }
    }

    if (inWords && wordQueue.isNotEmpty()) {
        // System back leaves the word block back to the step (grades already persisted).
        androidx.activity.compose.BackHandler { inWords = false }
        WordSession(
            card = wordQueue.first(),
            cardKey = wordServed,
            tts = container.tts,
            languageName = remember(lang) { container.content.meta(lang).name },
            review = wordIsReview,
            done = wordDone,
            total = wordTotal,
            onGrade = ::gradeLessonWord,
            onExit = { inWords = false }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // imePadding: the recall/cloze/FILL drills type into fields below the step card —
            // without it the keyboard covers them and they can't even be scrolled into view.
            .imePadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Day ${day.day} session",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$doneCount / $actionCount done",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { if (actionCount == 0) 0f else doneCount.toFloat() / actionCount },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        Text(
            "Step ${index + 1} of ${steps.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // The step card + its inline drill + actions slide as a unit so moving to the next step
        // reads as forward motion (back = reverse). Everything below derives from the animated
        // index `i`, never the outer state, so the outgoing content stays correct mid-transition.
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                if (reducedMotion) {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                } else {
                    val dir = if (targetState >= initialState) 1 else -1
                    (slideInHorizontally(Motion.snappy()) { w -> dir * w } + fadeIn(Motion.snappy())) togetherWith
                        (slideOutHorizontally(Motion.snappy()) { w -> -dir * w } + fadeOut(Motion.snappy()))
                }
            },
            label = "session-step"
        ) { i ->
            val s = steps[i.coerceIn(0, steps.lastIndex)]
            // Advance only if we're still ON this step: during the slide transition the outgoing
            // step's buttons remain tappable, and a double-tap must not skip a step.
            val advanceFrom: () -> Unit = {
                if (index == i && index < steps.lastIndex) index++
            }
            val onDrillDone: () -> Unit = {
                container.appScope.launch { container.progress.setDayTask(lang, day.day, s.id, true) }
                advanceFrom()
            }
            val markNext: () -> Unit = {
                if (s.kind != StepKind.INFO && s.kind != StepKind.COMPLETE) {
                    Haptics.confirm(context)
                    container.appScope.launch { container.progress.setDayTask(lang, day.day, s.id, true) }
                }
                advanceFrom()
            }
            val activity = day.activities.getOrNull(s.activityIndex)

            Column(modifier = Modifier.fillMaxWidth()) {
                // The step card.
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .heightIn(min = 180.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            buildString {
                                append(
                                    when (s.kind) {
                                        StepKind.INFO -> "📖 Today"
                                        StepKind.WORDS -> "🃏 New words"
                                        StepKind.REVIEW -> "🔁 Review"
                                        StepKind.GENDER -> "🎯 Drill"
                                        StepKind.CLOZE -> "🎯 Case drill"
                                        StepKind.RECALL -> "⌨️ Recall drill"
                                        StepKind.WRAPUP -> "🧠 Wrap-up recall"
                                        StepKind.LEARN -> "📚 Learn"
                                        StepKind.EXERCISE -> "✏️ Exercise"
                                        StepKind.DIALOGUE -> "🗣 Dialogue"
                                        StepKind.LINK -> "🔗 Course task"
                                        StepKind.COMPLETE -> "🏁 Finish"
                                        else -> "✍️ Task"
                                    }
                                )
                                if (s.phase.isNotBlank()) append("   ·   ${s.phase}")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            s.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        if (s.detail.isNotBlank()) {
                            Text(
                                s.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        if (s.kind == StepKind.WORDS) {
                            Text(
                                when {
                                    stepDone(s) -> "✅ New words done."
                                    unlockedNew > newBlock ->
                                        "$newBlock new words this lesson (${unlockedNew - newBlock} more unlocked, coming in later lessons)."
                                    else -> "$newBlock new word${if (newBlock == 1) "" else "s"} to learn."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        if (s.kind == StepKind.REVIEW) {
                            Text(
                                if (stepDone(s)) "✅ Review done."
                                else "$reviewPending card${if (reviewPending == 1) "" else "s"} to review" +
                                    if (dueNow > reviewPending) " (${dueNow - reviewPending} more in the Words tab)." else ".",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                // Inline drills drive their own completion.
                when (s.kind) {
                    StepKind.GENDER -> GenderDrill(container, lang, onDrillDone)
                    StepKind.CLOZE -> ClozeDrill(container, lang, onDrillDone)
                    StepKind.RECALL -> RecallDrill(container, lang, onDrillDone)
                    StepKind.LEARN -> activity?.let { LearnActivity(container, it, onDrillDone) }
                    StepKind.EXERCISE -> activity?.let { ExerciseActivity(container, it, onDrillDone) }
                    StepKind.DIALOGUE -> activity?.let { DialogueActivity(container, it, onDrillDone) }
                    StepKind.WRAPUP -> WrapupRecall(container, lang, day, onDrillDone)
                    else -> {}
                }

                // Step actions.
                when (s.kind) {
                    StepKind.INFO -> Button(
                        onClick = advanceFrom,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Let's go →") }

                    StepKind.WORDS -> {
                        if (!stepDone(s) && newBlock > 0) {
                            Button(
                                onClick = {
                                    startWordBlock("words", isReview = false) {
                                        container.words.unlockedNewWords(lang, day.day, perLesson)
                                            .take(perLesson)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Learn $newBlock new word${if (newBlock == 1) "" else "s"}") }
                            // Skip only moves past the step for now; it must NOT mark it done.
                            OutlinedButton(
                                onClick = advanceFrom,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) { Text("Skip for now →") }
                        } else {
                            Button(onClick = markNext, modifier = Modifier.fillMaxWidth()) {
                                Text("Next →")
                            }
                        }
                    }

                    StepKind.REVIEW -> {
                        if (!stepDone(s) && reviewPending > 0) {
                            Button(
                                onClick = {
                                    startWordBlock("review", isReview = true) {
                                        container.words.buildReviewSession(lang, today).take(Fsrs.REVIEW_CAP)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Review $reviewPending card${if (reviewPending == 1) "" else "s"}") }
                            OutlinedButton(
                                onClick = advanceFrom,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) { Text("Skip for now →") }
                        } else {
                            Button(onClick = markNext, modifier = Modifier.fillMaxWidth()) {
                                Text("Next →")
                            }
                        }
                    }

                    StepKind.LINK -> {
                        Button(
                            onClick = { s.url?.let { uriHandler.openUri(it) } },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Open ↗") }
                        Button(
                            onClick = markNext,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) { Text("Done, next step →") }
                    }

                    StepKind.TASK -> {
                        s.navRoute?.let { route ->
                            Button(
                                onClick = { onNavigate(route) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    when (route) {
                                        Dest.PRACTICE.route -> "Open quizzes"
                                        Dest.LEARN.route -> "Open Learn tab"
                                        else -> "Open Review"
                                    }
                                )
                            }
                        }
                        Button(
                            onClick = markNext,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) { Text("Done, next step →") }
                    }

                    StepKind.GENDER, StepKind.CLOZE, StepKind.RECALL, StepKind.WRAPUP,
                    StepKind.LEARN, StepKind.EXERCISE, StepKind.DIALOGUE -> { /* content drives completion */ }

                    StepKind.COMPLETE -> {
                        // App-scoped so exiting the player can't cancel the write mid-flight
                        // (a composition-scoped launch here silently lost day completions).
                        // completing guards a double-tap from inserting the day twice.
                        var completing by remember(day.day) { mutableStateOf(false) }
                        var celebrate by remember(day.day) { mutableStateOf(false) }
                        Button(
                            enabled = !completing,
                            onClick = {
                                completing = true
                                container.appScope.launch {
                                    container.progress.completeDay(lang, day.day, totalDays, day.level)
                                }
                                Haptics.confirm(context)
                                celebrate = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Mark day ${day.day} complete ✓") }
                        if (celebrate) {
                            // Live streak: completeDay's write lands async and the flow
                            // recomposes the overlay with the freshly banked value.
                            val prog by container.progress.progress(lang)
                                .collectAsState(initial = null)
                            com.corlang.app.ui.components.CelebrationOverlay(
                                dayNumber = day.day,
                                streak = prog?.streak ?: 1,
                                onDone = onExit
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { if (index > 0) index-- },
                enabled = index > 0,
                modifier = Modifier.weight(1f)
            ) { Text("← Back") }
            OutlinedButton(onClick = onExit, modifier = Modifier.weight(1f)) {
                Text("Exit (saved)")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Auto-generated interactive drill: 8 nouns from the deck (preferring words you've already
 * met), guess masculine / feminine / neuter. Powered by the `pos` field of the vocabulary.
 */
@Composable
private fun GenderDrill(container: AppContainer, lang: String, onFinished: () -> Unit) {
    val context = LocalContext.current
    val reviews by container.words.reviews(lang).collectAsState(initial = emptyList())

    data class Item(val hr: String, val gender: String)

    val items = remember(reviews.size > 0) {
        val seen = reviews.map { it.wordId }.toSet()
        val nouns = container.words.allWords(lang).mapNotNull { w ->
            val pos = w.pos ?: return@mapNotNull null
            val gender = when {
                pos.startsWith("n. m") -> "m"
                pos.startsWith("n. f") -> "f"
                pos.startsWith("n. n") -> "n"
                else -> null
            } ?: return@mapNotNull null
            Triple(w.id, w.hr, gender)
        }
        val preferred = nouns.filter { it.first in seen }.shuffled()
        (preferred + nouns.filterNot { it.first in seen }.shuffled())
            .take(8).map { Item(it.second, it.third) }
    }

    var qIndex by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }
    val feedback = CorlangColors.feedback

    if (items.isEmpty()) {
        Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text("Next →") }
        return
    }

    if (finished) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                "🎯 $score / ${items.size}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                if (score == items.size) "Perfect, genders locked in."
                else "Remember: consonant → m, -a → f, -o/-e → n (with exceptions the app flags).",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Button(onClick = onFinished, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text("Done, next step →")
            }
        }
        return
    }

    val item = items[qIndex]
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.hr,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            SpeakerButton(tts = container.tts, text = item.hr)
            Text("${qIndex + 1}/${items.size}", style = MaterialTheme.typography.bodySmall)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("m" to "muški", "f" to "ženski", "n" to "srednji").forEach { (g, label) ->
                val isChosen = chosen == g
                val border = when {
                    chosen == null -> MaterialTheme.colorScheme.outline
                    g == item.gender -> feedback.correct
                    isChosen -> feedback.wrong
                    else -> MaterialTheme.colorScheme.outline
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .weight(1f)
                        .border(2.dp, border, RoundedCornerShape(10.dp))
                        .clickable(enabled = chosen == null) {
                            chosen = g
                            if (g == item.gender) { score++; Haptics.confirm(context) }
                            else Haptics.reject(context)
                        }
                ) {
                    Text(
                        label,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )
                }
            }
        }
        if (chosen != null) {
            Button(
                onClick = {
                    if (qIndex + 1 >= items.size) finished = true else { qIndex++; chosen = null }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) { Text(if (qIndex + 1 >= items.size) "See result" else "Next word →") }
        }
    }
}
