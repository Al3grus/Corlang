package com.corlang.app.ui.screens

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
import com.corlang.app.data.WordsRepository
import com.corlang.app.data.model.StudyDay
import com.corlang.app.ui.Haptics
import com.corlang.app.ui.components.SpeakerButton
import com.corlang.app.ui.navigation.Dest
import com.corlang.app.ui.theme.CorlangColors
import kotlinx.coroutines.launch

/**
 * The guided daily session: the app walks you through today's work one step at a time.
 * Every step is either an in-app activity launched right here (word review, gender drill),
 * a deep link with a done-gate (e-course unit), or a concrete task. Step completion is
 * persisted per day (day_task_check), so leaving and returning resumes exactly.
 */

enum class StepKind { INFO, TASK, LINK, WORDS, GENDER, CLOZE, RECALL, LEARN, EXERCISE, DIALOGUE, COMPLETE }

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
    resourceUrls: Map<String, String?>
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
        title = "Word review",
        detail = "Clear your due flashcards. This step completes itself when nothing is due.",
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
            "quiz" in t || "mock exam" in t -> Dest.QUIZ.route
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
                title = "Case drill — the right form in context",
                detail = text,
                phase = if (isReview) "5 · Wrap-up" else "3 · Practice"
            )
            return
        }
        if (nav == null && url == null && recallRegex.containsMatchIn(text)) {
            steps += SessionStep(
                id = "$prefix-$index", kind = StepKind.RECALL,
                title = "Recall drill — type the Croatian",
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
        day.reviewBlock.items.forEachIndexed { i, r -> addItem("review", i, r, isReview = true) }
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

    steps += SessionStep(
        id = "complete", kind = StepKind.COMPLETE,
        title = "Day ${day.day} done",
        detail = "Mark the day complete — streak credited, plan advances."
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
    val steps = remember(day.day) { buildSessionSteps(day, resourceUrls) }

    val checks by container.progress.dayTaskChecks(lang, day.day)
        .collectAsState(initial = emptyList())
    val doneIds = checks.map { it.itemId }.toSet()

    // Words step completes itself from live data.
    val reviews by container.words.reviews(lang).collectAsState(initial = emptyList())
    val today = WordsRepository.todayEpochDay()
    val dueNow = reviews.count { it.dueEpochDay <= today }

    fun stepDone(s: SessionStep): Boolean = when (s.kind) {
        StepKind.WORDS -> dueNow == 0 || s.id in doneIds
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

    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val doneCount = steps.count { it.kind != StepKind.INFO && it.kind != StepKind.COMPLETE && stepDone(it) }
    val actionCount = steps.count { it.kind != StepKind.INFO && it.kind != StepKind.COMPLETE }

    fun markAndNext() {
        if (step.kind != StepKind.INFO && step.kind != StepKind.COMPLETE) {
            Haptics.confirm(context)
            scope.launch { container.progress.setDayTask(lang, day.day, step.id, true) }
        }
        if (index < steps.lastIndex) index++
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                            when (step.kind) {
                                StepKind.INFO -> "📖 Today"
                                StepKind.WORDS -> "🃏 Flashcards"
                                StepKind.GENDER -> "🎯 Drill"
                                StepKind.CLOZE -> "🎯 Case drill"
                                StepKind.RECALL -> "⌨️ Recall drill"
                                StepKind.LEARN -> "📚 Learn"
                                StepKind.EXERCISE -> "✏️ Exercise"
                                StepKind.DIALOGUE -> "🗣 Dialogue"
                                StepKind.LINK -> "🔗 Course task"
                                StepKind.COMPLETE -> "🏁 Finish"
                                else -> "✍️ Task"
                            }
                        )
                        if (step.phase.isNotBlank()) append("   ·   ${step.phase}")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    step.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (step.detail.isNotBlank()) {
                    Text(
                        step.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (step.kind == StepKind.WORDS) {
                    Text(
                        if (dueNow == 0) "✅ Nothing due — this step is done."
                        else "$dueNow words waiting.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Inline drills drive their own completion.
        val onDrillDone: () -> Unit = {
            scope.launch { container.progress.setDayTask(lang, day.day, step.id, true) }
            if (index < steps.lastIndex) index++
        }
        val activity = day.activities.getOrNull(step.activityIndex)
        when (step.kind) {
            StepKind.GENDER -> GenderDrill(container, lang, onDrillDone)
            StepKind.CLOZE -> ClozeDrill(container, lang, onDrillDone)
            StepKind.RECALL -> RecallDrill(container, lang, onDrillDone)
            StepKind.LEARN -> activity?.let { LearnActivity(container, it, onDrillDone) }
            StepKind.EXERCISE -> activity?.let { ExerciseActivity(container, it, onDrillDone) }
            StepKind.DIALOGUE -> activity?.let { DialogueActivity(container, it, onDrillDone) }
            else -> {}
        }

        // Step actions.
        when (step.kind) {
            StepKind.INFO -> Button(
                onClick = { if (index < steps.lastIndex) index++ },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Let's go →") }

            StepKind.WORDS -> {
                if (dueNow > 0) {
                    Button(
                        onClick = { onNavigate(Dest.WORDS.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Review $dueNow words now") }
                    Text(
                        "Come back to Today when you're done — your place here is saved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    OutlinedButton(
                        onClick = ::markAndNext,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text("Skip for now →") }
                } else {
                    Button(onClick = ::markAndNext, modifier = Modifier.fillMaxWidth()) {
                        Text("Next →")
                    }
                }
            }

            StepKind.LINK -> {
                Button(
                    onClick = { step.url?.let { uriHandler.openUri(it) } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open ↗") }
                Button(
                    onClick = ::markAndNext,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Done — next step →") }
            }

            StepKind.TASK -> {
                step.navRoute?.let { route ->
                    Button(
                        onClick = { onNavigate(route) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            when (route) {
                                Dest.QUIZ.route -> "Open quizzes"
                                Dest.LEARN.route -> "Open Learn tab"
                                else -> "Open Words"
                            }
                        )
                    }
                }
                Button(
                    onClick = ::markAndNext,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Done — next step →") }
            }

            StepKind.GENDER, StepKind.CLOZE, StepKind.RECALL,
            StepKind.LEARN, StepKind.EXERCISE, StepKind.DIALOGUE -> { /* content drives completion */ }

            StepKind.COMPLETE -> Button(
                onClick = {
                    scope.launch {
                        container.progress.completeDay(lang, day.day, totalDays, day.level)
                    }
                    Haptics.confirm(context)
                    onExit()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Mark day ${day.day} complete ✓") }
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
                if (score == items.size) "Perfect — genders locked in."
                else "Remember: consonant → m, -a → f, -o/-e → n (with exceptions the app flags).",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Button(onClick = onFinished, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text("Done — next step →")
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
