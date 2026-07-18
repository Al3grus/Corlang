package com.corlang.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.data.prefs.LearnerProfile
import com.corlang.app.ui.components.CorlangLogo
import com.corlang.app.ui.components.LogoVariant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/*
 * First-run onboarding: which course, who you are, your daily goal, and your level.
 * Question-driven setup before content is the convention every serious language app follows,
 * and it is re-runnable any time from Settings.
 *
 * It deliberately asks as little as possible. "Where are you from / where do you live" used to
 * be here, feeding a curated-Croatian phrases payoff on the last step; both are gone. Nothing
 * else consumed those answers, so collecting them contradicted the app's own promise not to
 * collect anything. A learner who wants "I am from ..." can ask the tutor, or meet it in a
 * lesson. The curated NationOption/NATIONS table and profilePhrases() are recoverable from git
 * history (see the v0.20.37 tree) if a lesson step ever wants them.
 */

/*
 * Onboarding steps, as identities rather than bare indices: the language step only exists when
 * more than one course is available, so hardcoded numbers drift the moment a course is hidden
 * (French currently is). `visibleSteps` below builds the real running order, and every Back/Next
 * walks THAT list, so no step ever needs to know its own number.
 */
/*
 * One number for the whole onboarding rhythm: the lockup, the title and the button row each get
 * this much air on both sides. Deliberately large, the steps are short and the space is the design.
 *
 * It is a MAXIMUM, not a fixed height. Five of these gaps plus a title and a button row overrun a
 * short screen outright, which would push the buttons off the bottom with nothing to scroll to.
 * OnboardingScreen measures the frame and caps the gap at a share of it, so a normal phone gets
 * the full 100dp and a short screen (or one with the keyboard open) degrades smoothly.
 */
private val STEP_GAP = 100.dp

private const val STEP_WELCOME = 0
private const val STEP_HOW = 1
private const val STEP_LANG = 2
private const val STEP_NAME = 3
private const val STEP_GENDER = 4
private const val STEP_GOAL = 5
private const val STEP_LEVEL = 6

@Composable
fun OnboardingScreen(container: AppContainer, onFinish: (wantsPlacement: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()

    // Which language they're setting up. Profile below is language-neutral; this just picks the
    // course. Defaults to (and prefills) the currently-selected language.
    val allMeta = remember { container.content.allMeta() }
    val multiLang = allMeta.size > 1
    var learnLang by remember { mutableStateOf(container.content.availableLanguages.first()) }
    val langName = allMeta.firstOrNull { it.code == learnLang }?.name ?: "the language"

    var step by remember { mutableIntStateOf(STEP_WELCOME) }
    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("m") }
    var goal by remember { mutableStateOf(10) }
    var wantsPlacement by remember { mutableStateOf<Boolean?>(null) }

    // Editing from Settings: prefill from the saved profile.
    LaunchedEffect(Unit) {
        learnLang = container.languagePrefs.selectedLanguage.first()
        val p = container.languagePrefs.profile.first()
        if (p.name.isNotBlank()) name = p.name
        gender = p.gender
        goal = container.languagePrefs.newWordsPerDay.first()
    }

    fun save(thenPlacement: Boolean) {
        scope.launch {
            container.languagePrefs.setProfile(
                // from / livesIn / reason are no longer collected: nothing consumed them, and
                // asking for them contradicted "no data collection". The fields stay on
                // LearnerProfile so existing backups still parse, and are written blank so a
                // learner who re-runs onboarding ends up with nothing stored rather than a
                // stale answer to a question the app no longer asks.
                LearnerProfile(
                    name = name.trim(),
                    gender = gender,
                    from = "",
                    livesIn = "",
                    reason = ""
                )
            )
            container.languagePrefs.setNewWordsPerDay(goal)
            container.languagePrefs.setLanguage(learnLang)
            // Mark this course handled BEFORE onboarding-done flips, so MainActivity's
            // new-language prompt never fires for the language just set up here.
            container.languagePrefs.markPlacementHandled(learnLang)
            container.languagePrefs.setOnboardingDone(true)
            onFinish(thenPlacement)
        }
    }

    // The steps that actually run, in order. With a single course the language step disappears
    // and the progress bar counts the shorter path honestly.
    val visibleSteps = remember(multiLang) {
        listOfNotNull(
            STEP_WELCOME, STEP_HOW,
            STEP_LANG.takeIf { multiLang },
            STEP_NAME, STEP_GENDER, STEP_GOAL, STEP_LEVEL
        )
    }
    val stepIndex = visibleSteps.indexOf(step).coerceAtLeast(0)
    // +1 = moving forward, -1 = went Back. Read by the transition below so the slide runs the
    // way you travelled: forward pushes left, Back pulls right.
    var direction by remember { mutableIntStateOf(1) }
    /** Walk the visible order, so Back/Next never land on a step that isn't shown. */
    fun go(delta: Int) {
        direction = if (delta < 0) -1 else 1
        step = visibleSteps[(stepIndex + delta).coerceIn(0, visibleSteps.lastIndex)]
    }

    // Rendered OUTSIDE the Scaffold: the Surface supplies the theme's content color (plain
    // Text here would otherwise default to black, unreadable in dark theme), and with
    // edge-to-edge the screen must inset itself or the progress bar collides with the
    // status bar/cutout and the bottom buttons hide under the navigation bar.
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // The full 100dp everywhere once there is room for it (roughly a 700dp-tall frame and up,
    // which is any current phone in portrait), scaled down proportionally on anything shorter
    // or when the keyboard halves the screen. Without this the fixed gaps simply overrun the
    // column and the button row walks off the bottom edge with no way to scroll to it.
    val gap = minOf(STEP_GAP, maxHeight / 7)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(24.dp)
    ) {
        // drawStopIndicator = {}: Material3 1.3.0 draws a dot at the end of the track by
        // default (the spec's "stop indicator"). Nobody chose it here and it reads as a stray
        // blue dot on an otherwise plain bar, so it is switched off.
        // The bar fills toward the new step rather than jumping, so it reads as part of the
        // same forward movement as the slide below.
        val barProgress by animateFloatAsState(
            targetValue = (stepIndex + 1f) / visibleSteps.size,
            animationSpec = tween(durationMillis = 320),
            label = "onboardingProgress"
        )
        LinearProgressIndicator(
            progress = { barProgress },
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth()
        )

        // The lockup sits in its own band between the bar and the step title, with equal air
        // above and below so it belongs to neither. It fades rather than popping when the
        // welcome step leaves.
        AnimatedVisibility(
            visible = step == STEP_WELCOME,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            CorlangLogo(
                variant = LogoVariant.LOCKUP,
                size = 44.dp,
                modifier = Modifier.padding(vertical = gap)
            )
        }
        // Steps with no logo still need the bar-to-title gap.
        if (step != STEP_WELCOME) Spacer(Modifier.height(gap))

        // Every step fills this same frame, so the title lands in one place and the buttons
        // land in another, on every screen. Only the middle differs, and it centres itself
        // between the two. That is what keeps the slide from looking ragged: the fixed
        // furniture stays put while the content moves.
        AnimatedContent(
            targetState = step,
            modifier = Modifier.fillMaxWidth().weight(1f),
            // The incoming step waits for the outgoing one to clear before it fades up,
            // otherwise two differently-sized bodies overlap mid-slide and it reads as a smear.
            transitionSpec = {
                val d = direction
                (slideInHorizontally(tween(300)) { w -> d * w } +
                    fadeIn(tween(durationMillis = 200, delayMillis = 120))) togetherWith
                    (slideOutHorizontally(tween(300)) { w -> -d * w } + fadeOut(tween(160)))
            },
            label = "onboardingStep"
        ) { animatedStep ->
        when (animatedStep) {
            // ---- Welcome: what this app is, before it asks for anything ----
            // Just "Welcome!": the lockup above already reads Corlang, and the thesis sentence
            // names it again. Naming it in the greeting too put it three times in five words.
            STEP_WELCOME -> StepFrame(
                gap = gap,
                title = "Welcome!",
                centered = true,
                actions = {
                    Button(onClick = { go(+1) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Get started →")
                    }
                }
            ) {
                Text(
                    "Corlang is built on how people actually learn a language: structured study, " +
                        "deliberate repetition, and retention methods with real evidence behind them.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // ---- How it works: the substance, one page before anything is asked ----
            STEP_HOW -> StepFrame(
                gap = gap,
                title = "How it works",
                // No Back here: the intro pages carry nothing you can get wrong. The label
                // names the destination; a single-course build skips to the profile questions.
                actions = {
                    Button(onClick = { go(+1) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (multiLang) "Choose your language →" else "Next →")
                    }
                }
            ) {
                Text(
                    "Every course runs from absolute beginner to B2, the level asked for by " +
                        "employers, universities and citizenship applications, and the level " +
                        "certified exams test.",
                    style = MaterialTheme.typography.bodyLarge
                )
                // "mock exams in the official format" and not "one after every level": mocks
                // exist at A1/A2/B1 for Croatian and B1/B2 for Portuguese and French, so the
                // stronger claim would be false. The format really is the official one.
                Text(
                    "Daily lessons, word review that catches you just before you forget, quizzes, " +
                        "and full mock exams in the official exam format. An optional AI tutor for " +
                        "conversation practice and written feedback.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 14.dp)
                )
                // Not "everything works offline": the AI tutor calls out to a server, so the
                // blanket claim was false. The course itself genuinely is offline.
                Text(
                    "No accounts, no tracking, no data collection. Lessons, review, quizzes and " +
                        "exams all work offline and stay on your device. Only the AI tutor needs " +
                        "a connection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            // ---- Language choice (only when more than one course ships) ----
            // No supporting copy here on purpose: the intro already explained what Corlang is,
            // and a description under the choices either repeats it or (as it once did) changes
            // as you tap between languages. No Back either, everything before this is intro.
            STEP_LANG -> StepFrame(
                gap = gap,
                title = "Which language do you want to learn?",
                actions = {
                    Button(onClick = { go(+1) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Next →")
                    }
                }
            ) {
                // One full-width row per language: names never fight for horizontal space,
                // so they can't overflow at large font scales (a 3-way segmented row did).
                Column(modifier = Modifier.fillMaxWidth()) {
                    allMeta.forEach { m ->
                        val chosen = learnLang == m.code
                        OutlinedButton(
                            onClick = { learnLang = m.code },
                            border = androidx.compose.foundation.BorderStroke(
                                if (chosen) 2.dp else 1.dp,
                                if (chosen) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            ),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor =
                                    if (chosen) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surface,
                                contentColor =
                                    if (chosen) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text(
                                "${m.flagEmoji}  ${m.name}" + if (chosen) "  ✓" else "",
                                maxLines = 1,
                                fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // ---- Name ----
            // Back starts at the next step: everything before this is intro or a choice the
            // following screens let you change anyway.
            STEP_NAME -> StepFrame(
                gap = gap,
                title = "What's your name?",
                // The old subtitle promised it would become "your very first phrase", which went
                // with the removed phrases payoff. The name's real jobs are the daily reminder
                // ("Vrijeme je za hrvatski, <name>!") and the tutor addressing the learner.
                subtitle = "Your reminders and your tutor will use it.",
                actions = {
                    Button(
                        onClick = { go(+1) },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Next →") }
                }
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ---- Word forms (gender) ----
            STEP_GENDER -> StepFrame(
                gap = gap,
                title = "Which forms should $langName use for you?",
                subtitle = if (learnLang == "hr")
                    "Croatian words change with the speaker: a man says \"Ja sam Amerikanac, radio sam\", " +
                        "a woman says \"Ja sam Amerikanka, radila sam\"."
                else
                    "Many words and endings change with the speaker's gender, so we'll use the right " +
                        "forms for you.",
                actions = { NextRow(enabled = true, onBack = { go(-1) }, onNext = { go(+1) }) }
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("m" to "Male forms", "f" to "Female forms").forEachIndexed { i, (v, label) ->
                        SegmentedButton(
                            selected = gender == v,
                            onClick = { gender = v },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                            icon = {}
                        ) { Text(label) }
                    }
                }
            }

            // ---- Daily goal ----
            STEP_GOAL -> StepFrame(
                gap = gap,
                title = "New words per lesson",
                subtitle = "How many new words each lesson introduces. 10 is the sustainable " +
                    "default; you can change this anytime in Settings.",
                actions = { NextRow(enabled = true, onBack = { go(-1) }, onNext = { go(+1) }) }
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(10, 15, 20).forEachIndexed { i, v ->
                        SegmentedButton(
                            selected = goal == v,
                            onClick = { goal = v },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
                            icon = {}
                        ) { Text("$v") }
                    }
                }
            }

            // ---- Level: start at day 1, or place ----
            // The old "your first phrases" payoff lived here (curated Croatian intro lines with
            // a speaker button). Removed: this step asks one question with two answers, and a
            // reward block underneath buried the actual decision.
            STEP_LEVEL -> StepFrame(
                gap = gap,
                title = "Last one: do you already know some $langName?",
                actions = {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { go(-1) },
                            modifier = Modifier.weight(1f)
                        ) { Text("← Back", maxLines = 1) }
                        Button(
                            onClick = { save(thenPlacement = wantsPlacement == true) },
                            enabled = wantsPlacement != null,
                            modifier = Modifier.weight(2f).padding(start = 8.dp)
                        ) { Text("Start learning →", maxLines = 1) }
                    }
                }
            ) {
                listOf(
                    false to "I'm new, start me at Day 1",
                    true to "I know some, take the 2-minute placement test"
                ).forEach { (wants, label) ->
                    val chosen = wantsPlacement == wants
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .border(
                                2.dp,
                                if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { wantsPlacement = wants }
                    ) { Text(label, modifier = Modifier.padding(12.dp)) }
                }
            }
        }
        }
    }
    }
    }
}

@Composable
private fun StepFrame(
    gap: Dp,
    title: String,
    subtitle: String = "",
    centered: Boolean = false,
    actions: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val align = if (centered) Alignment.CenterHorizontally else Alignment.Start
    Column(Modifier.fillMaxSize(), horizontalAlignment = align) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = if (centered) TextAlign.Center else null
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        Spacer(Modifier.height(gap))

        // The body claims the space between title and buttons and centres itself in it, so a
        // one-line step and a six-paragraph step both look composed. heightIn(min = viewport)
        // is what allows centring while still scrolling: the inner column is at least a
        // frameful tall, so Arrangement.Center has room, and it grows past the frame (or when
        // the keyboard shrinks it) and scrolls instead.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val viewport = maxHeight
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = viewport),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = align
                ) { content() }
            }
        }

        Spacer(Modifier.height(gap))
        actions()
        // Lifts the button row off the bottom edge; without it the primary button sat flush
        // against the navigation bar and read as pinned to the screen rather than to the step.
        Spacer(Modifier.height(gap))
    }
}

@Composable
private fun NextRow(enabled: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
    // 50/50: a 1:2 split squeezed "← Back" into wrapping at larger font scales. No top padding:
    // StepFrame already spends STEP_ACTION_GAP above the actions slot.
    Row(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
            Text("← Back", maxLines = 1)
        }
        Button(
            onClick = onNext,
            enabled = enabled,
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        ) { Text("Next →", maxLines = 1) }
    }
}
