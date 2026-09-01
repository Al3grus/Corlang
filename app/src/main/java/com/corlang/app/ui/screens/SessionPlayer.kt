package com.corlang.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The guided daily session: the app walks you through today's work one step at a time.
 * Every step is an in-app activity launched right here (word review, gender drill, learn,
 * exercise, dialogue) or a concrete task. Step completion is persisted per day
 * (day_task_check), so leaving and returning resumes exactly.
 */

enum class StepKind { INFO, TASK, WORDS, REVIEW, GENDER, CLOZE, RECALL, LEARN, EXERCISE, DIALOGUE, WRAPUP, COMPLETE }

/**
 * Where a session opens, and whether the lesson's step marks should be wiped first.
 *
 * [done] is `stepDone` evaluated over [kinds], in the same order. Returns the step index to open
 * at, and `replay = true` when the lesson was already finished.
 *
 * Pure, because both halves of this have been wrong in the field and neither is visible in a
 * build log:
 *
 *  - Resuming used to pick "the first step that is not done", and on a FINISHED lesson that is
 *    the COMPLETE step, so revisiting lesson 9 from lesson 19 opened straight onto the
 *    congratulations screen with nothing to do but leave.
 *  - Jumping at all is wrong before the underlying flows have emitted, which once landed a
 *    learner past a review step that still had twelve cards due. The caller still guards that;
 *    this function only decides, given settled data.
 */
fun sessionOpensAt(
    kinds: List<StepKind>,
    done: List<Boolean>,
    hasChecks: Boolean,
): Pair<Int, Boolean> {
    require(kinds.size == done.size) { "kinds and done must line up" }
    val actionable = kinds.indices.filter {
        kinds[it] != StepKind.INFO && kinds[it] != StepKind.COMPLETE
    }
    if (actionable.isNotEmpty() && actionable.all { done[it] }) return 0 to true
    if (!hasChecks) return 0 to false
    val firstOpen = kinds.indices.firstOrNull { kinds[it] != StepKind.INFO && !done[it] } ?: -1
    return (if (firstOpen > 0) firstOpen else 0) to false
}

/**
 * Everything the player draws around the wrap-up's question block, added up: 16 of page padding,
 * ~20 for the header row, ~20 for the progress bar and its padding, ~76 for the collapsed Wrap-up
 * bar with its margins, ~28 for the counter line, and ~84 for the Back/Exit row and the spacer
 * under it. An estimate by construction - it is measured against the shipped composition above,
 * and being a few dp out only moves the centred block by half of that.
 */
private val WRAPUP_CHROME = 244.dp

/**
 * The part of [WRAPUP_CHROME] that leaves while the wrap-up is being typed into: the Back/Exit
 * row and the spacer under it. Handed back to the question block in step with the row's own
 * departure, so the block does not lurch the moment the row goes.
 */
private val WRAPUP_BACK_ROW = 84.dp

/**
 * How long the wrap-up's intro panel takes to be eaten down to its title bar.
 *
 * Long for a tap response - Material puts a container transform at 300-500ms - and deliberately
 * so: this is not a control changing state, it is the door closing on the instructions and opening
 * on the hardest block of the lesson, and the soft edge doing the eating is not legible at 350ms.
 * The cost of the ceremony is paid back by making it skippable: a tap anywhere lands it at once,
 * so the learner who has seen it sixty times never waits for it.
 */
private const val WRAPUP_COLLAPSE_MS = 1000

/**
 * The soft edge the collapsing panel drags behind its rising bottom. Content within this much of
 * that edge is already part-transparent, so the panel dissolves what it passes over instead of
 * guillotining it - which is what a plain shrinking Surface gives you, since it clips its content.
 */
private val WRAPUP_EAT_BAND = 32.dp

/** Fraction of the collapse by which the intro text (and the Start button under it) has gone. */
private const val WRAPUP_INTRO_OUT = 0.65f

/**
 * Fraction of the collapse at which "Wrap-up" starts arriving. Before the bar lands, not after:
 * a container that empties, travels, stops, and only then fills is three beats for one gesture,
 * and the middle one is a blank blue slab. One short word can afford to be early.
 */
private const val WRAPUP_TITLE_IN = 0.70f

/**
 * Fraction of the collapse at which the lesson's own content starts arriving, and how long it
 * then takes. It overlaps the tail of the collapse on purpose - waiting for the panel to stop
 * would put a hole between the two halves of one gesture - but it outlives it by half a second,
 * so the questions rise into a screen that has already come to rest rather than racing it there.
 */
private const val WRAPUP_CONTENT_IN = 0.80f
private const val WRAPUP_CONTENT_MS = 500

/** Opacity of the intro text, and of the Start button below it, at a given point in the collapse. */
private fun wrapupIntroAlpha(collapse: Float): Float =
    1f - (collapse / WRAPUP_INTRO_OUT).coerceIn(0f, 1f)

/**
 * The wrap-up step's card, drawn anywhere between its two states.
 *
 * [collapse] 0 is the full instruction panel; 1 is the one-word title bar. In between the card's
 * BOTTOM edge travels up while its top stays put, and the intro dissolves into that edge over
 * [WRAPUP_EAT_BAND], so the panel reads as eating its own contents rather than as a box that got
 * shorter. Everything else in the gesture - the Start button below, the scroll - hangs off the
 * same [collapse], which is why it is passed in rather than animated here.
 *
 * Both heights are MEASURED, not assumed: the intro's depends on the day's text and the bar's on
 * the learner's font scale, and a hardcoded 76dp would be wrong for somebody on every lesson.
 */
@Composable
private fun WrapupStepCard(
    collapse: Float,
    intro: @Composable () -> Unit
) {
    var introH by remember { mutableIntStateOf(0) }
    var barH by remember { mutableIntStateOf(0) }
    val bandPx = with(LocalDensity.current) { WRAPUP_EAT_BAND.toPx() }

    // Until BOTH states have been measured the card is simply its intro. Lerping towards a barH
    // that is still zero is how you get a card that flashes to nothing on its first frame.
    val measured = introH > 0 && barH > 0
    val height = if (measured) lerp(introH, barH, collapse.coerceIn(0f, 1f)) else introH
    val introAlpha = wrapupIntroAlpha(collapse)
    val titleAlpha = ((collapse - WRAPUP_TITLE_IN) / (1f - WRAPUP_TITLE_IN)).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val h = if (measured) height else placeable.height
                // Placed at the top, so the edge that moves is the bottom one.
                layout(placeable.width, h.coerceAtLeast(0)) { placeable.place(0, 0) }
            }
            .clipToBounds()
    ) {
        Box(
            modifier = Modifier
                .onSizeChanged { introH = it.height }
                .graphicsLayer {
                    alpha = introAlpha
                    // DstIn has to have something to punch through: without an offscreen layer
                    // the mask would erase whatever was already painted behind this card.
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    if (!measured || collapse <= 0f || introAlpha <= 0f) return@drawWithContent
                    val edge = height.toFloat()
                    val top = (edge - bandPx).coerceAtLeast(0f)
                    if (edge <= top) return@drawWithContent
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startY = top,
                            endY = edge
                        ),
                        topLeft = Offset(0f, top),
                        size = Size(size.width, edge - top),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) { intro() }

        Text(
            "Wrap-up",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { barH = it.height }
                .alpha(titleAlpha)
                .padding(vertical = 14.dp)
        )
    }
}

data class SessionStep(
    val id: String,
    val kind: StepKind,
    val title: String,
    val detail: String = "",
    val navRoute: String? = null,
    /** Which phase of the evidence-based session shape this step belongs to (docs/sources/method.md). */
    val phase: String = "",
    /** Index into day.activities for LEARN/EXERCISE/DIALOGUE steps. */
    val activityIndex: Int = -1
)

/** Derives the guided steps for a plan day from its drills + review block. */
fun buildSessionSteps(
    day: StudyDay,
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

    /*
     * A lesson NEVER sends the learner to another site to study. The plan was originally written
     * around the Zagreb e-course, and this used to turn drill lines like "Sign in at
     * a1.ffzg.unizg.hr and do Unit 1" into a link step placed right after the new words, so day
     * one of Croatian opened with an instruction to go and learn somewhere else. The content is
     * cleaned, and this filter keeps it that way: any drill still naming an external course or a
     * sign-in is dropped rather than shown. External material belongs on Profile, under
     * references (resources.json), where the learner goes looking for it deliberately.
     */
    val externalRegex = Regex(
        "https?://|www\\.|ffzg|unizg|a1\\.hr|a2\\.hr|e-tečaj|e-course|\\bhrt\\b|" +
            "sign in|sign up|log in|\\blogin\\b|\\bunit \\d",
        RegexOption.IGNORE_CASE
    )
    fun isExternal(text: String): Boolean = externalRegex.containsMatchIn(text)

    fun navFor(text: String): String? {
        val t = text.lowercase()
        return when {
            "words tab" in t || "due words" in t -> Dest.WORDS.route
            // The level quiz lives on the journey as an end-of-level checkpoint; a drill that
            // mentions it deep-links straight to this day's level quiz.
            "quiz" in t || "mock exam" in t -> "quiz/${day.level}"
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
        if (isExternal(text)) return
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
        if (nav != Dest.WORDS.route && caseRegex.containsMatchIn(text)) {
            steps += SessionStep(
                id = "$prefix-$index", kind = StepKind.CLOZE,
                title = "Case drill, the right form in context",
                detail = text,
                phase = if (isReview) "5 · Wrap-up" else "3 · Practice"
            )
            return
        }
        if (nav == null && recallRegex.containsMatchIn(text)) {
            steps += SessionStep(
                id = "$prefix-$index", kind = StepKind.RECALL,
                title = "Recall drill, type the $languageName",
                detail = text,
                phase = if (isReview) "5 · Wrap-up" else "3 · Practice"
            )
            return
        }
        steps += SessionStep(
            id = "$prefix-$index",
            kind = StepKind.TASK,
            title = text,
            navRoute = nav,
            phase = when {
                isReview -> "5 · Wrap-up"
                outputRegex.containsMatchIn(text) -> "4 · Output"
                else -> "3 · Practice"
            }
        )
    }

    if (day.activities.isNotEmpty()) {
        // The day's content is embedded, so the activities ARE the lesson. This used to emit a
        // link step first, sending the learner to the external course before their own lesson
        // had started. Nothing precedes the activities now.
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
                detail = "No peeking. You'll see an English phrase from today's lesson; " +
                    "write it in $languageName, one word or phrase per answer.",
                phase = "5 · Wrap-up"
            )
            // Fallback for long-sentence days: a quick retest of today's exercise (still real content).
            exerciseIndex >= 0 -> steps += SessionStep(
                id = "wrapup", kind = StepKind.EXERCISE,
                title = "Wrap-up: quick retest",
                detail = "One more pass over today's practice, from memory this time.",
                phase = "5 · Wrap-up", activityIndex = exerciseIndex
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
        detail = "A quick spaced-repetition pass over words coming due, closing out the day.",
        phase = "6 · Review"
    )

    steps += SessionStep(
        id = "complete", kind = StepKind.COMPLETE,
        title = "Lesson ${day.day} done",
        detail = "Mark the lesson complete, streak credited, plan advances."
    )
    return steps
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionPlayer(
    container: AppContainer,
    lang: String,
    day: StudyDay,
    totalDays: Int,
    onNavigate: (String) -> Unit,
    onExit: () -> Unit,
    /**
     * Open at this step and REPLAY rather than resume: the revisit chooser's "jump to a section"
     * (see [LessonRevisit]). A replay writes nothing about the day - no step checks, no completion,
     * no streak - because the lesson it replays is already complete and redoing part of it must not
     * move today's ring. Null = the ordinary guided lesson, which resumes and marks as it goes.
     */
    startAt: Int? = null,
    /**
     * How tall the player's own area is, measured by the caller (the scaffold's content box). Used
     * only to centre the wrap-up's question block in the room left below its counter; 0 = unknown,
     * and the wrap-up lays out from the top as it always did.
     */
    viewportHeight: Dp = 0.dp
) {
    // One flag derived from one parameter, so a replay cannot get out of step with where it opened.
    val practice = startAt != null
    // A lesson in progress locks the top-bar language picker (switching would tear it down).
    com.corlang.app.ui.Engagement.Report()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Mistake bank: up to 3 questions missed on EARLIER days, resurfaced as a repair step.
    // Null until loaded; the base steps render immediately and the repair step splices in
    // before the wrap-up tail, so indices already passed never shift.
    var repairQuestions by remember(lang, day.day) {
        androidx.compose.runtime.mutableStateOf<List<com.corlang.app.data.model.Question>?>(null)
    }
    // Not in a replay: the repair step is the DAY's business (three questions banked from earlier
    // lessons), and splicing it in would also shift the very step indices the chooser just offered.
    LaunchedEffect(lang, day.day) {
        if (!practice) repairQuestions = container.progress.dueMistakes(lang, 3)
    }

    val steps = remember(lang, day.day, repairQuestions?.size) {
        val base = buildSessionSteps(day, container.content.meta(lang).name)
        val repairs = repairQuestions
        if (repairs.isNullOrEmpty()) base else {
            val tail = base.indexOfFirst { it.id == "wrapup" || it.kind == StepKind.COMPLETE }
                .let { if (it < 0) base.size else it }
            base.subList(0, tail) + SessionStep(
                id = "mistakes", kind = StepKind.EXERCISE,
                title = "Fix your mistakes",
                detail = "Questions you missed in earlier lessons. Answer right and they stop coming back.",
                phase = "5 · Wrap-up"
            ) + base.subList(tail, base.size)
        }
    }

    // null until the first DB emit — used to gate the resume jump so we don't flash step 0.
    val rawChecks by container.progress.dayTaskChecks(lang, day.day)
        .collectAsState(initial = null)
    val checks = rawChecks.orEmpty()
    // A replay shows every step live. The stored marks are the lesson's FINISHED state, and a
    // section replay neither reads them (an exercise would open with all its questions solved) nor
    // writes them.
    val doneIds = if (practice) emptySet() else checks.map { it.itemId }.toSet()

    // The NEW-words step = this lesson's unlocked words (deck order, first day.day * perLesson) not
    // yet introduced. The REVIEW step = due cards, capped so they can't pile up (overflow → Words
    // tab). Both are lesson-scoped, so an earlier lesson never marks this one done.
    // Nullable-until-loaded (same as rawChecks): stepDone() reads all of these, so the resume
    // jump below must not run before they emit — an empty-initial reviews list read as "review
    // step done" and resume could land PAST a step with 12 cards actually due.
    val rawReviews by container.words.reviews(lang).collectAsState(initial = null)
    val reviews = rawReviews.orEmpty()
    val rawPerLesson by container.languagePrefs.newWordsPerDay
        .collectAsState(initial = null)
    val perLesson = rawPerLesson ?: Fsrs.NEW_WORDS_PER_DAY
    // The learner's cap on due-word reviews per day; new words are fixed at [perLesson].
    val rawMaxReviews by container.languagePrefs.maxReviewsPerDay
        .collectAsState(initial = null)
    val maxReviews = rawMaxReviews ?: Fsrs.REVIEW_CAP
    val today = WordsRepository.todayEpochDay()
    val allWords = remember(lang) { container.words.allWords(lang) }
    val dueNow = reviews.count { it.dueEpochDay <= today }
    val seenIds = remember(reviews) { reviews.map { it.wordId }.toSet() }
    // Placement offset: deck words before deckStart are never taught as new (a Day-61
    // placement must not serve day-1 basics).
    val rawDeckStart by container.languagePrefs.wordDeckStart(lang)
        .collectAsState(initial = null)
    val deckStart = rawDeckStart ?: 0
    val unlockedNew = allWords.take(day.day * perLesson).drop(deckStart).count { it.id !in seenIds }
    // Every deck word has been introduced: no lesson can ever serve a new word again. Distinct
    // from "none left for today", and unavoidable at the faster paces, so the UI names it.
    val deckFinished = allWords.drop(deckStart).none { it.id !in seenIds }
    val deckLanguageName = remember(lang) { container.content.meta(lang).name }
    /*
     * One lesson serves at most perLesson new words, even when a placement jump unlocked a large
     * backlog — it drains one lesson-sized block at a time, never a 300-card dump.
     *
     * The +1 is the catch-up. The deck is sized at exactly lessons x perLesson with no slack
     * (Croatian: 3440 words, 344 lessons), so unlock and serve rates were identical and a
     * learner who ever fell behind stayed behind for the rest of the course: the shortfall could
     * not be recovered by lessons (rate-matched) or by the Words tab (review-only), and the
     * deck's tail was simply never introduced. A learner 22 words behind at lesson 17 reached
     * lesson 344 having never met the last 12 words on their path, while the screen still told
     * them more were "coming in later lessons".
     *
     * Skipping is gone, so no new backlog can form; this repairs the ones already on devices,
     * invisibly, one extra word at a time rather than as a lump the learner would feel.
     */
    val newBlock = minOf(unlockedNew, if (unlockedNew > perLesson) perLesson + 1 else perLesson)
    val reviewPending = minOf(dueNow, maxReviews)

    fun stepDone(s: SessionStep): Boolean = when (s.kind) {
        // Done when nothing is waiting OR this day's block was completed. The check is written
        // only by finishing the block (never by skipping), and it matters when a capped block
        // leaves a backlog/overflow behind — the step must still count as done for the day.
        StepKind.WORDS -> unlockedNew == 0 || s.id in doneIds
        StepKind.REVIEW -> reviewPending == 0 || s.id in doneIds
        else -> s.id in doneIds
    }

    // Start at the first unfinished step (resume), or at the section a replay picked. Keyed on
    // startAt as well as the day: picking section 3, backing out, then picking section 5 must open
    // at 5, and a key of the day alone would hand back the remembered 3.
    var index by rememberSaveable(day.day, startAt) {
        mutableIntStateOf(startAt ?: 0)
    }
    // On first composition per day, jump past finished steps. Gated on EVERY flow stepDone()
    // reads (checks, reviews, perLesson, deckStart) having actually emitted — not just being
    // non-empty — so the resume target is resolved from real data before the first visible
    // frame. Two bugs lived here: (a) gating on checks alone let resume race the reviews flow
    // and land past a review step with cards still due; (b) latching `resumed` only when
    // checks were non-empty left the jump armed on a fresh day — the first check written
    // mid-session (e.g. after skipping the words step) then yanked the user backwards.
    // Pre-latched in a replay: the chooser already decided where this opens, and the resume jump
    // must not second-guess it (on a finished lesson it would reset the marks and go back to 0).
    var resumed by rememberSaveable(day.day, startAt) { mutableStateOf(practice) }
    if (!resumed && (rawChecks == null || rawReviews == null ||
            rawPerLesson == null || rawDeckStart == null)
    ) {
        return   // one blank frame < wrong-step flash
    }
    if (!resumed) {
        // A FINISHED lesson replays; only a half-done one resumes. Revisiting a lesson is for
        // doing it again, so its marks are cleared and it opens at step one. The lesson stays
        // completed on the journey: this clears step marks only, never the completed-days
        // record, and `advancePosition` already refuses to move the learner backwards.
        val (openAt, replay) = sessionOpensAt(
            steps.map { it.kind }, steps.map { stepDone(it) }, checks.isNotEmpty()
        )
        if (replay) scope.launch { container.progress.resetDayTasks(lang, day.day) }
        index = openAt
        resumed = true   // latch unconditionally: the jump must never fire mid-session
    }

    // The wrap-up opens as an instruction panel and collapses to a one-word bar the moment the
    // learner starts: the rules are worth reading once, not worth half the screen for eight
    // questions. Reset per day; a resumed wrap-up starts itself (see RecallRunner).
    var wrapupStarted by rememberSaveable(day.day, startAt) { mutableStateOf(false) }
    /*
     * One clock for the whole start-the-wrap-up gesture: the panel's height, the soft edge eating
     * its way up through the instructions, the Start button fading under that edge, and the
     * scroll. They have to be the SAME animation or the edge and what it is eating drift apart -
     * which is also why this is a tween and not the house spring. A spring has no duration to
     * hang a gradient off.
     *
     * Starts at 1 when the wrap-up is already under way (process death restores wrapupStarted,
     * which is saveable, but not this): coming back must land on the bar, not replay the collapse.
     */
    val wrapupCollapse = remember(day.day, startAt) {
        Animatable(if (wrapupStarted) 1f else 0f)
    }
    /** The second half of that gesture: see [WRAPUP_CONTENT_IN]. */
    val wrapupContent = remember(day.day, startAt) {
        Animatable(if (wrapupStarted) 1f else 0f)
    }
    /** Held so a skip can cancel the collapse mid-flight instead of racing it to the end. */
    var wrapupRun by remember(day.day, startAt) { mutableStateOf<Job?>(null) }
    /*
     * How far the keyboard reaches INTO the player's own area. Not the raw ime inset: the tab bar
     * and the nav bar below this screen are already spent (MainActivity consumes the scaffold's
     * insets, and every imePadding here is net of them), so the raw inset overstates the overlap by
     * their height. That height is theirs, not ours, so it is measured rather than assumed.
     */
    var bottomGapPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    fun imeOverlap(inset: Int) = (inset - bottomGapPx).coerceAtLeast(0)
    val imeOverlapPx = imeOverlap(WindowInsets.ime.getBottom(density))
    val imeSpanPx = maxOf(
        imeOverlapPx,
        imeOverlap(WindowInsets.imeAnimationTarget.getBottom(density)),
        imeOverlap(WindowInsets.imeAnimationSource.getBottom(density))
    )
    /*
     * The keyboard's own clock: 0 with it away, 1 with it fully up, and every value between read
     * live off the system's animated inset (imeAnimationSource and imeAnimationTarget are the two
     * ends that animation is travelling between). Everything on this screen that reacts to the
     * keyboard hangs off THIS rather than off its own animateFloatAsState - the screen has to
     * travel with the keyboard, and a second clock for one gesture is what threw the question
     * block up the screen the frame the keyboard appeared. Zero outside a started wrap-up: no
     * other step moves for the keyboard.
     */
    val wrapupKeyboard =
        if (wrapupStarted && steps.getOrNull(index)?.kind == StepKind.WRAPUP && imeSpanPx > 0)
            imeOverlapPx.toFloat() / imeSpanPx
        else 0f
    // With the keyboard up on a wrap-up question, the session chrome steps back so the prompt and
    // the field are what the eye lands on. On the keyboard's clock, like everything else here.
    val chromeAlpha = 1f - 0.6f * wrapupKeyboard
    /*
     * The room the wrap-up has for its question block, so it can sit in the middle of the screen
     * instead of stacking under the counter with the bottom half empty. The player's own height
     * less what is drawn around the block: the page padding, the header row, the progress bar, the
     * collapsed Wrap-up bar and the counter line above it, and the Back/Exit row below it.
     *
     * The keyboard takes its overlap out of this and the departing Back/Exit row hands its own
     * height back, both continuously, so the block settles into the room left ABOVE the keyboard
     * at the keyboard's own speed - and, where the keyboard was never going to reach it, barely
     * moves at all. This used to be zeroed outright the frame the keyboard opened, which threw
     * the question the learner was mid-way through reading up the screen in one frame.
     */
    val wrapupFill =
        if (viewportHeight <= 0.dp) 0.dp
        else (viewportHeight - WRAPUP_CHROME + WRAPUP_BACK_ROW * wrapupKeyboard -
            with(density) { imeOverlapPx.toDp() }).coerceAtLeast(0.dp)

    val doneCount = steps.count { it.kind != StepKind.INFO && it.kind != StepKind.COMPLETE && stepDone(it) }
    val actionCount = steps.count { it.kind != StepKind.INFO && it.kind != StepKind.COMPLETE }
    val reducedMotion = rememberReducedMotion()

    // The session bar creeps with partial exercise progress within the CURRENT step, not only when
    // a whole step finishes. Reads the same persisted per-question "<stepId>::q<i>" checks the
    // resume uses (plus legacy "::x<n>" count checks; never the "::missed" flag), so clearing
    // 3 of 8 exercises nudges the bar by ~3/8 of one step.
    val currentPartial: Float = run {
        val cs = steps.getOrNull(index) ?: return@run 0f
        if (cs.kind != StepKind.EXERCISE || stepDone(cs)) return@run 0f
        val total = day.activities.getOrNull(cs.activityIndex)?.questions?.size ?: 0
        if (total <= 0) return@run 0f
        doneIds.count { it.startsWith("${cs.id}::q") || it.startsWith("${cs.id}::x") }
            .coerceAtMost(total).toFloat() / total
    }
    val sessionProgress = when {
        // A replay has no marks to count, so its bar measures the only thing it knows: how far
        // through the lesson the learner has walked since they jumped in.
        practice -> if (steps.size <= 1) 1f else index.toFloat() / (steps.size - 1)
        actionCount == 0 -> 0f
        else -> ((doneCount + currentPartial) / actionCount).coerceIn(0f, 1f)
    }
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = sessionProgress,
        animationSpec = if (reducedMotion) androidx.compose.animation.core.snap()
                        else androidx.compose.animation.core.tween(durationMillis = 400),
        label = "session-progress"
    )

    // In-lesson word block, shared by the new-words step and the review step. New vocabulary only
    // ever enters through lessons; the review block is capped. Grading persists per card.
    val wordQueue = remember(day.day) { mutableStateListOf<SessionCard>() }
    var inWords by remember(day.day) { mutableStateOf(false) }
    var wordServed by remember(day.day) { mutableIntStateOf(0) }
    var wordDone by remember(day.day) { mutableIntStateOf(0) }
    var wordTotal by remember(day.day) { mutableIntStateOf(0) }
    var wordStepId by remember(day.day) { mutableStateOf("words") }
    var wordIsReview by remember(day.day) { mutableStateOf(false) }

    /**
     * Records one step check. THE only writer of day marks in this screen, and a no-op during a
     * replay: the lesson a replay walks is already complete, so redoing part of it must leave the
     * day's marks, ring and streak exactly as they were. What a replay DOES still persist is
     * learning state rather than day state: word grades (under the once-a-day rule in
     * WordsRepository.practiceReview) and the mistake bank, which should keep catching wrong
     * answers whenever they are given.
     *
     * App-scoped: persists even if the player is closed the same instant.
     */
    fun mark(itemId: String) {
        if (practice) return
        container.appScope.launch { container.progress.setDayTask(lang, day.day, itemId, true) }
    }

    fun markStepDoneAndAdvance(stepId: String) {
        mark(stepId)
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

    // One scroll state for the whole player, reset on every step change: without the reset, a
    // long step left scrolled down bled its offset into the next step, which then opened
    // mid-content and had to be scrolled UP to see its own title.
    val stepScroll = rememberScrollState()
    LaunchedEffect(index, inWords) { stepScroll.scrollTo(0) }
    val focus = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            // What sits between this screen's bottom edge and the window's - the tab bar and the
            // nav bar - which is the part of the keyboard's inset that never reaches us.
            .onGloballyPositioned { c ->
                bottomGapPx = (c.findRootCoordinates().size.height -
                    (c.positionInRoot().y + c.size.height)).toInt().coerceAtLeast(0)
            }
            .verticalScroll(stepScroll)
            // A tap on anything that is not itself a control puts the keyboard away. The wrap-up
            // types into a field in the middle of the screen with no Done key in sight, and
            // system back was the only other way to be rid of it.
            .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }
            // imePadding: the recall/cloze/FILL drills type into fields below the step card —
            // without it the keyboard covers them and they can't even be scrolled into view.
            .imePadding()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.alpha(chromeAlpha)
        ) {
            Text(
                if (practice) "Lesson ${day.day} · replay" else "Lesson ${day.day}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (practice) "step ${index + 1} of ${steps.size}"
                else "$doneCount / $actionCount done",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // One counter per progress bar, and it sits ABOVE the bar it describes (the row above).
        // There used to be a second "Step 3 of 8" line under this bar, and a third counter inside
        // the exercise, so a learner mid-question could see three different fractions at once.
        LinearProgressIndicator(
            progress = { animatedProgress },
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).alpha(chromeAlpha)
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
                mark(s.id)
                advanceFrom()
            }
            val markNext: () -> Unit = {
                if (s.kind != StepKind.INFO && s.kind != StepKind.COMPLETE) {
                    Haptics.confirm(context)
                    mark(s.id)
                }
                advanceFrom()
            }
            val activity = day.activities.getOrNull(s.activityIndex)

            // A second of ceremony is a gift the first time and a toll booth the sixtieth, so
            // any tap during the collapse ends it. The Start button is a descendant and still
            // gets the event first; this only catches taps that land nowhere in particular.
            val collapsing = s.kind == StepKind.WRAPUP && wrapupStarted &&
                (wrapupCollapse.value < 1f || wrapupContent.value < 1f)
            Column(
                modifier = Modifier.fillMaxWidth().then(
                    if (collapsing) Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            wrapupRun?.cancel()
                            scope.launch {
                                wrapupCollapse.snapTo(1f); wrapupContent.snapTo(1f)
                            }
                        }
                    } else Modifier
                )
            ) {
                // The step card. A wrap-up under way keeps only its name: the instructions it
                // carries (no peeking, you will see an English phrase...) are a briefing, and a
                // briefing that stays on screen for the whole exercise is just less room for the
                // exercise. Collapsed, it is a title bar over centred questions.
                val isWrapup = s.kind == StepKind.WRAPUP
                val collapse = if (isWrapup && wrapupStarted) wrapupCollapse.value else 0f
                val collapsed = isWrapup && collapse >= 1f
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    // No minimum height: the card wraps its content. The old heightIn(180.dp)
                    // left a one-line instruction floating over a lake of empty blue.
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .alpha(if (collapsed) chromeAlpha else 1f)
                ) {
                    // Every step draws this panel; only the wrap-up ever takes it away again,
                    // and it does that by eating it rather than by cutting to the bar.
                    val introPanel: @Composable () -> Unit = {
                      Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            buildString {
                                append(
                                    when (s.kind) {
                                        StepKind.INFO -> "Today"
                                        StepKind.WORDS -> "New words"
                                        StepKind.REVIEW -> "Review"
                                        StepKind.GENDER -> "Drill"
                                        StepKind.CLOZE -> "Case drill"
                                        StepKind.RECALL -> "Recall drill"
                                        StepKind.WRAPUP -> "Wrap-up recall"
                                        StepKind.LEARN -> "Learn"
                                        StepKind.EXERCISE -> "Exercise"
                                        StepKind.DIALOGUE -> "Dialogue"
                                        StepKind.COMPLETE -> "Finish"
                                        else -> "Task"
                                    }
                                )
                                if (s.phase.isNotBlank()) append("   ·   ${s.phase}")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            // "Lesson 9 done" would be a lie at the end of a replay: it was
                            // already done, and this run banked nothing.
                            if (practice && s.kind == StepKind.COMPLETE) "End of the replay"
                            else s.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        if (s.detail.isNotBlank()) {
                            Text(
                                s.detail,
                                style = com.corlang.app.ui.theme.CorlangType.reading,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        // Deliberately no counts here. The old line read "10 new words this
                        // lesson (22 more unlocked, coming in later lessons)", which was both
                        // noise and untrue: unlock and serve rates are identical (10 a lesson
                        // against a deck sized at exactly lessons x 10), so a backlog never
                        // arrives in a later lesson, and on the FINAL lesson the same sentence
                        // still promised words that no lesson would ever teach. The learner's
                        // job here is not arithmetic, it is the block in front of them.
                        if (s.kind == StepKind.WORDS && stepDone(s)) {
                            Text(
                                "New words done ✓",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        if (s.kind == StepKind.REVIEW) {
                            Text(
                                if (stepDone(s)) "Review done ✓"
                                else "$reviewPending card${if (reviewPending == 1) "" else "s"} to review" +
                                    if (dueNow > reviewPending) " (${dueNow - reviewPending} more in the Words tab)." else ".",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                      }
                    }
                    if (isWrapup) WrapupStepCard(collapse) { introPanel() } else introPanel()
                }

                // Inline drills drive their own completion.
                when (s.kind) {
                    StepKind.GENDER -> GenderDrill(container, lang, onDrillDone)
                    StepKind.CLOZE -> ClozeDrill(container, lang, onDrillDone)
                    StepKind.RECALL -> RecallDrill(container, lang, onDrillDone)
                    StepKind.LEARN -> activity?.let { LearnActivity(container, it, onDrillDone) }
                    StepKind.EXERCISE -> if (s.id == "mistakes") {
                        val repairs = repairQuestions.orEmpty()
                        ExerciseActivity(
                            container,
                            com.corlang.app.data.model.DayActivity(
                                type = com.corlang.app.data.model.ActivityKind.EXERCISE,
                                title = "Fix your mistakes",
                                intro = "One more try at what got away earlier.",
                                questions = repairs
                            ),
                            loadResumeState = {
                                val ids = if (practice) emptyList()
                                else container.progress.dayTaskChecks(lang, day.day).first()
                                    .map { it.itemId }
                                val qPrefix = "${s.id}::q"
                                ExerciseResume(
                                    solvedIndices = ids.filter { it.startsWith(qPrefix) }
                                        .mapNotNull { it.removePrefix(qPrefix).toIntOrNull() }
                                        .toSet(),
                                    missedAny = "${s.id}::missed" in ids
                                )
                            },
                            onSolved = { i -> mark("${s.id}::q$i") },
                            // Right answer retires the banked question; wrong answer re-banks
                            // it with a bumped count, so it returns in a later session.
                            onQuestionCleared = { q ->
                                container.appScope.launch { container.progress.clearMistake(lang, q) }
                            },
                            onQuestionMissed = { q ->
                                container.appScope.launch { container.progress.recordMistake(lang, day.day, q) }
                            },
                            onDone = onDrillDone
                        )
                    } else activity?.let { act ->
                        ExerciseActivity(
                            container, act,
                            // Resume within a multi-exercise step: WHICH questions were cleared
                            // ("<stepId>::q<i>") + whether any answer was missed ("<stepId>::missed").
                            // Legacy count-style "<stepId>::x<n>" checks (pre-0.20.12) map to "the
                            // first n questions" — the best a bare count can say.
                            loadResumeState = {
                                val ids = if (practice) emptyList()
                                else container.progress.dayTaskChecks(lang, day.day).first()
                                    .map { it.itemId }
                                val qPrefix = "${s.id}::q"
                                val solved = ids.filter { it.startsWith(qPrefix) }
                                    .mapNotNull { it.removePrefix(qPrefix).toIntOrNull() }
                                    .toSet()
                                val legacy = ids.count { it.startsWith("${s.id}::x") }
                                ExerciseResume(
                                    solvedIndices = solved + (0 until legacy),
                                    missedAny = "${s.id}::missed" in ids
                                )
                            },
                            onSolved = { i -> mark("${s.id}::q$i") },
                            onMissed = { mark("${s.id}::missed") },
                            onQuestionCleared = { q ->
                                container.appScope.launch { container.progress.clearMistake(lang, q) }
                            },
                            onQuestionMissed = { q ->
                                container.appScope.launch { container.progress.recordMistake(lang, day.day, q) }
                            },
                            onDone = onDrillDone
                        )
                    }
                    StepKind.DIALOGUE -> activity?.let { DialogueActivity(container, it, onDrillDone) }
                    StepKind.WRAPUP -> WrapupRecall(
                        container, lang, day,
                        fillBelowCounter = wrapupFill,
                        // Same persistence scheme as EXERCISE, per ITEM rather than per answer:
                        // "<stepId>::q<i>" = cleared, "<stepId>::w<i>#<n>" = the n-th miss on i.
                        // A missed item is re-queued now, so a bare count of answers no longer
                        // says where the learner is - only which items are still owed does.
                        loadResume = {
                            val ids = if (practice) emptyList()
                            else container.progress.dayTaskChecks(lang, day.day).first()
                                .map { it.itemId }
                            recallResumeFrom(ids, s.id)
                        },
                        // The block is composed from the tap, not from the landing: it is what
                        // the region under the panel GROWS INTO, and it can only be grown into
                        // if it has been laid out and measured. It is held at contentAlpha 0
                        // until the room for it exists.
                        started = wrapupStarted,
                        startAlpha = wrapupIntroAlpha(collapse),
                        collapse = collapse,
                        contentAlpha = wrapupContent.value,
                        onStart = { animated ->
                            if (!wrapupStarted) {
                                wrapupStarted = true
                                wrapupRun = scope.launch {
                                    if (!animated || reducedMotion) {
                                        wrapupCollapse.snapTo(1f); wrapupContent.snapTo(1f)
                                    } else {
                                        val spec = tween<Float>(
                                            WRAPUP_COLLAPSE_MS, easing = FastOutSlowInEasing
                                        )
                                        // Same clock, so the panel always lands where the layout
                                        // below assumes it landed - even if the learner had
                                        // scrolled the Start button up to reach it.
                                        launch {
                                            stepScroll.animateScrollTo(
                                                0,
                                                tween(WRAPUP_COLLAPSE_MS, easing = FastOutSlowInEasing)
                                            )
                                        }
                                        launch {
                                            delay((WRAPUP_COLLAPSE_MS * WRAPUP_CONTENT_IN).toLong())
                                            wrapupContent.animateTo(
                                                1f, tween(WRAPUP_CONTENT_MS, easing = FastOutSlowInEasing)
                                            )
                                        }
                                        wrapupCollapse.animateTo(1f, spec)
                                    }
                                }
                            } else if (wrapupCollapse.value < 1f || wrapupContent.value < 1f) {
                                // A tap on a Start button that is already fading, or anywhere in
                                // the region below it: end the gesture rather than replay it.
                                wrapupRun?.cancel()
                                scope.launch {
                                    wrapupCollapse.snapTo(1f); wrapupContent.snapTo(1f)
                                }
                            }
                        },
                        onAnswered = { i, ok, attempt ->
                            mark(if (ok) "${s.id}::q$i" else "${s.id}::w$i#$attempt")
                        },
                        onFinished = onDrillDone
                    )
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
                                        // perLesson sets the UNLOCK window (uptoDay x perLesson);
                                        // newBlock is how many of them this lesson serves, which
                                        // is one more than the pace while a backlog is draining.
                                        container.words.unlockedNewWords(lang, day.day, perLesson)
                                            .take(newBlock)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Learn new words") }
                            // No skip. New words are not optional: the course is built on spaced
                            // retrieval, and a lesson that introduces nothing breaks the schedule
                            // the rest of the course depends on. Skipping was also the only way a
                            // learner could fall behind the deck, and the deck has no slack to
                            // give (3440 words against 344 lessons at 10 a lesson), so every
                            // skipped block cost words that no later lesson could hand back.
                        } else {
                            // The deck is finite, and at 15 or 20 words a lesson it is spent
                            // well before lesson 250. Say so plainly instead of showing a bare
                            // "Next" under a "New words" heading, which reads like a bug.
                            if (deckFinished) {
                                // Count from deckStart: a placed learner skipped the deck's
                                // opening stretch, so "met every word, 2566 in total" would
                                // claim words they never saw.
                                Text(
                                    "You have met every word on your ${deckLanguageName} path, " +
                                        "${allWords.size - deckStart} in total. From here the " +
                                        "word work is pure review, keeping what you know from " +
                                        "fading.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }
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
                                        container.words.buildReviewSession(lang, today).take(maxReviews)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Review $reviewPending card${if (reviewPending == 1) "" else "s"}") }
                            // No skip here either. The review block IS the retention system;
                            // its size is already the learner's own dial (Settings, reviews per
                            // day), so the choice they get is how much, not whether.
                        } else {
                            Button(onClick = markNext, modifier = Modifier.fillMaxWidth()) {
                                Text("Next →")
                            }
                        }
                    }

                    StepKind.TASK -> {
                        s.navRoute?.let { route ->
                            Button(
                                onClick = { onNavigate(route) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    when {
                                        route.startsWith("quiz/") -> "Open the level quiz"
                                        route == Dest.LEARN.route -> "Open Tutor tab"
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

                    // A replay ends where the lesson ends, but with nothing to bank: it never
                    // reaches the completion write, the streak, or the celebration overlay.
                    StepKind.COMPLETE -> if (practice) {
                        Text(
                            "That is the end of Lesson ${day.day}. Nothing was re-marked: it was " +
                                "already complete, and your streak and today's goal are untouched.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                            Text("Back to the sections →")
                        }
                    } else {
                        // App-scoped so exiting the player can't cancel the write mid-flight
                        // (a composition-scoped launch here silently lost day completions).
                        // completing guards a double-tap from inserting the day twice.
                        var completing by remember(day.day) { mutableStateOf(false) }
                        var celebrate by remember(day.day) { mutableStateOf(false) }
                        val completedList by container.progress.completedDays(lang)
                            .collectAsState(initial = null)
                        // Live progress, collected BEFORE the completion write so the freeze
                        // bank can be snapshotted at tap time. The snapshot is the bank AFTER
                        // settling any lapse the completion is about to pay for — comparing the
                        // raw pre-write bank with the post-write one lied when a return from a
                        // covered lapse both spent freezes and crossed a milestone (bank falls,
                        // yet a freeze really was earned).
                        val prog by container.progress.progress(lang)
                            .collectAsState(initial = null)
                        var preFreezes by remember(day.day) { mutableStateOf(-1) }
                        // The streak at tap time, so the overlay can tell "the write has landed"
                        // from "still showing pre-write state". Without it, tapping complete on a
                        // day whose CURRENT streak already sits on a milestone flashed the freeze
                        // line for the frame before the new streak arrived.
                        var preStreak by remember(day.day) { mutableStateOf(-1) }
                        // Revisits don't re-mark: the day is already banked, and re-completing
                        // must not re-credit the streak (completeDay is also idempotent). The
                        // !completing guard keeps THIS session's fresh completion on the
                        // celebration path instead of flipping mid-overlay.
                        val alreadyDone = !completing && completedList?.contains(day.day) == true
                        if (alreadyDone) {
                            Text(
                                "Lesson ${day.day} is already complete ✓, revisiting doesn't need re-marking.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Button(
                                onClick = onExit,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Back to Today →") }
                        } else {
                            Button(
                                enabled = !completing && completedList != null,
                                onClick = {
                                    completing = true
                                    preFreezes = prog?.let {
                                        com.corlang.app.data.ProgressRepository.settle(
                                            streak = it.streak,
                                            lastStudiedEpochDay = it.lastStudiedEpochDay,
                                            freezes = it.streakFreezes,
                                            today = com.corlang.app.data.WordsRepository.todayEpochDay()
                                        ).second
                                    } ?: 0
                                    preStreak = prog?.streak ?: 0
                                    container.appScope.launch {
                                        container.progress.completeDay(lang, day.day, totalDays, day.level)
                                    }
                                    Haptics.confirm(context)
                                    celebrate = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Mark lesson ${day.day} complete ✓") }
                        }
                        if (celebrate) {
                            // completeDay's write lands async; the flow recomposes the overlay
                            // with the freshly banked streak, and the freeze line appears once
                            // that new streak is a milestone the settled bank had room for.
                            val nowStreak = prog?.streak ?: 1
                            val nowFreezes = prog?.streakFreezes ?: 0
                            com.corlang.app.ui.components.CelebrationOverlay(
                                dayNumber = day.day,
                                streak = nowStreak,
                                freezeEarned = preFreezes >= 0 && nowStreak != preStreak &&
                                    com.corlang.app.data.ProgressRepository.freezeEarnedBy(
                                        newStreak = nowStreak, freezesBefore = preFreezes
                                    ),
                                freezes = nowFreezes,
                                onDone = onExit
                            )
                        }
                    }
                }
            }
        }

        /*
         * The two ways out of the lesson - and they leave while the wrap-up is being typed into.
         * With the keyboard up this row is pushed to sit directly under the answer field, which
         * puts an exit under the thumb at the one moment the learner is mid-answer, and it is of
         * no use to them there. It goes on the keyboard's clock and is eaten from below, like the
         * wrap-up's intro panel before it, so nothing above it moves while it goes.
         */
        val exitsShown = 1f - wrapupKeyboard
        if (exitsShown > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(exitsShown)
                    .then(
                        if (exitsShown < 1f) Modifier
                            .layout { measurable, constraints ->
                                val p = measurable.measure(constraints)
                                val h = (p.height * exitsShown).roundToInt()
                                layout(p.width, h) { p.place(0, 0) }
                            }
                            .clipToBounds()
                        else Modifier
                    )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
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
                            Text(if (practice) "Back to sections" else "Exit (saved)")
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
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
                "$score / ${items.size}",
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
