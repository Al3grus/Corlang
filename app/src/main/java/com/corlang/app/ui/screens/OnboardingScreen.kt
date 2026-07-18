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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.data.prefs.LearnerProfile
import com.corlang.app.ui.components.CorlangLogo
import com.corlang.app.ui.components.LogoVariant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/*
 * First-run onboarding: who you are, why you're learning, your daily goal, and your level.
 * The payoff step turns the answers into your personalized first Croatian phrases
 * (Zovem se…, Ja sam iz…, Živim u…) with the Croatian voice, so onboarding doubles as
 * lesson zero. Question-driven setup before content is the convention every serious
 * language app follows; re-runnable any time from Settings.
 */

/**
 * Curated Croatian forms per country: nationality (m/f), origin ("iz" + genitive) and
 * residence ("u" + locative). Standard dictionary forms only, the app never generates
 * Croatian grammar, so an unknown country simply omits those phrases.
 */
data class NationOption(
    val english: String,
    val natM: String,
    val natF: String,
    val fromHr: String,   // "iz Amerike"
    val inHr: String      // "u Americi"
)

val NATIONS: List<NationOption> = listOf(
    NationOption("United States", "Amerikanac", "Amerikanka", "iz Amerike", "u Americi"),
    NationOption("Croatia", "Hrvat", "Hrvatica", "iz Hrvatske", "u Hrvatskoj"),
    NationOption("Albania", "Albanac", "Albanka", "iz Albanije", "u Albaniji"),
    // Microstates: blank nationality = no widely attested Croatian demonym; the
    // nationality phrase is simply skipped, the from/lives-in phrases still work.
    NationOption("Andorra", "", "", "iz Andore", "u Andori"),
    NationOption("Argentina", "Argentinac", "Argentinka", "iz Argentine", "u Argentini"),
    NationOption("Australia", "Australac", "Australka", "iz Australije", "u Australiji"),
    NationOption("Austria", "Austrijanac", "Austrijanka", "iz Austrije", "u Austriji"),
    NationOption("Belarus", "Bjelorus", "Bjeloruskinja", "iz Bjelorusije", "u Bjelorusiji"),
    NationOption("Belgium", "Belgijanac", "Belgijanka", "iz Belgije", "u Belgiji"),
    NationOption("Bosnia and Herzegovina", "Bosanac", "Bosanka", "iz Bosne i Hercegovine", "u Bosni i Hercegovini"),
    NationOption("Brazil", "Brazilac", "Brazilka", "iz Brazila", "u Brazilu"),
    NationOption("Bulgaria", "Bugarin", "Bugarka", "iz Bugarske", "u Bugarskoj"),
    NationOption("Canada", "Kanađanin", "Kanađanka", "iz Kanade", "u Kanadi"),
    NationOption("Cyprus", "Cipranin", "Cipranka", "s Cipra", "na Cipru"),
    NationOption("Czechia", "Čeh", "Čehinja", "iz Češke", "u Češkoj"),
    NationOption("Denmark", "Danac", "Dankinja", "iz Danske", "u Danskoj"),
    NationOption("Estonia", "Estonac", "Estonka", "iz Estonije", "u Estoniji"),
    NationOption("Finland", "Finac", "Finkinja", "iz Finske", "u Finskoj"),
    NationOption("France", "Francuz", "Francuskinja", "iz Francuske", "u Francuskoj"),
    NationOption("Germany", "Nijemac", "Njemica", "iz Njemačke", "u Njemačkoj"),
    NationOption("Greece", "Grk", "Grkinja", "iz Grčke", "u Grčkoj"),
    NationOption("Hungary", "Mađar", "Mađarica", "iz Mađarske", "u Mađarskoj"),
    NationOption("Iceland", "Islanđanin", "Islanđanka", "s Islanda", "na Islandu"),
    NationOption("Ireland", "Irac", "Irkinja", "iz Irske", "u Irskoj"),
    NationOption("Italy", "Talijan", "Talijanka", "iz Italije", "u Italiji"),
    NationOption("Kosovo", "Kosovar", "Kosovarka", "s Kosova", "na Kosovu"),
    NationOption("Latvia", "Latvijac", "Latvijka", "iz Latvije", "u Latviji"),
    NationOption("Liechtenstein", "", "", "iz Lihtenštajna", "u Lihtenštajnu"),
    NationOption("Lithuania", "Litavac", "Litavka", "iz Litve", "u Litvi"),
    NationOption("Luxembourg", "Luksemburžanin", "Luksemburžanka", "iz Luksemburga", "u Luksemburgu"),
    NationOption("Malta", "Maltežanin", "Maltežanka", "s Malte", "na Malti"),
    NationOption("Mexico", "Meksikanac", "Meksikanka", "iz Meksika", "u Meksiku"),
    NationOption("Moldova", "Moldavac", "Moldavka", "iz Moldavije", "u Moldaviji"),
    NationOption("Monaco", "", "", "iz Monaka", "u Monaku"),
    NationOption("Montenegro", "Crnogorac", "Crnogorka", "iz Crne Gore", "u Crnoj Gori"),
    NationOption("Netherlands", "Nizozemac", "Nizozemka", "iz Nizozemske", "u Nizozemskoj"),
    NationOption("North Macedonia", "Makedonac", "Makedonka", "iz Sjeverne Makedonije", "u Sjevernoj Makedoniji"),
    NationOption("Norway", "Norvežanin", "Norvežanka", "iz Norveške", "u Norveškoj"),
    NationOption("Poland", "Poljak", "Poljakinja", "iz Poljske", "u Poljskoj"),
    NationOption("Portugal", "Portugalac", "Portugalka", "iz Portugala", "u Portugalu"),
    NationOption("Romania", "Rumunj", "Rumunjka", "iz Rumunjske", "u Rumunjskoj"),
    NationOption("Russia", "Rus", "Ruskinja", "iz Rusije", "u Rusiji"),
    NationOption("San Marino", "", "", "iz San Marina", "u San Marinu"),
    NationOption("Serbia", "Srbin", "Srpkinja", "iz Srbije", "u Srbiji"),
    NationOption("Slovakia", "Slovak", "Slovakinja", "iz Slovačke", "u Slovačkoj"),
    NationOption("Slovenia", "Slovenac", "Slovenka", "iz Slovenije", "u Sloveniji"),
    NationOption("Spain", "Španjolac", "Španjolka", "iz Španjolske", "u Španjolskoj"),
    NationOption("Sweden", "Šveđanin", "Šveđanka", "iz Švedske", "u Švedskoj"),
    NationOption("Switzerland", "Švicarac", "Švicarka", "iz Švicarske", "u Švicarskoj"),
    NationOption("Turkey", "Turčin", "Turkinja", "iz Turske", "u Turskoj"),
    NationOption("Ukraine", "Ukrajinac", "Ukrajinka", "iz Ukrajine", "u Ukrajini"),
    NationOption("United Kingdom", "Britanac", "Britanka", "iz Velike Britanije", "u Velikoj Britaniji"),
)

private const val SOMEWHERE_ELSE = "Somewhere else"

/*
 * Onboarding steps, as identities rather than bare indices: the language step only exists when
 * more than one course is available, so hardcoded numbers drift the moment a course is hidden
 * (French currently is). `visibleSteps` below builds the real running order, and every Back/Next
 * walks THAT list, so no step ever needs to know its own number.
 */
/*
 * Shared vertical rhythm for every step: the gap under a step's title, and the gap above the
 * button row. Both are generous on purpose. The step body is centred in the space left by the
 * progress bar, so with cramped spacing a short step reads as one dense clump floating in the
 * middle; letting title, content and actions breathe fills the screen instead.
 */
private val STEP_TITLE_GAP = 28.dp
private val STEP_ACTION_GAP = 24.dp
/** Air above and below the welcome lockup, equal on both sides so it sits between bar and title. */
private val LOGO_BAND = 22.dp

private const val STEP_WELCOME = 0
private const val STEP_HOW = 1
private const val STEP_LANG = 2
private const val STEP_NAME = 3
private const val STEP_GENDER = 4
private const val STEP_ORIGIN = 5
private const val STEP_GOAL = 6
private const val STEP_LEVEL = 7

/** The personalized phrases the profile unlocks; empty parts are simply skipped. */
fun profilePhrases(p: LearnerProfile): List<Pair<String, String>> {
    val from = NATIONS.firstOrNull { it.english == p.from }
    val livesIn = NATIONS.firstOrNull { it.english == p.livesIn }
    val out = mutableListOf<Pair<String, String>>()
    if (p.name.isNotBlank()) out += "Zovem se ${p.name.trim()}." to "My name is ${p.name.trim()}."
    from?.let {
        val nat = if (p.gender == "f") it.natF else it.natM
        if (nat.isNotBlank()) out += "Ja sam $nat." to "I am ${it.english.demonymEn()}."
        out += "Ja sam ${it.fromHr}." to "I am from ${it.english}."
    }
    livesIn?.let { out += "Živim ${it.inHr}." to "I live in ${it.english}." }
    return out
}

private fun String.demonymEn(): String = when (this) {
    "United States" -> "American"; "Croatia" -> "Croatian"; "United Kingdom" -> "British"
    else -> "from $this"
}

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
    var from by remember { mutableStateOf<String?>(null) }
    var livesIn by remember { mutableStateOf<String?>(null) }
    var goal by remember { mutableStateOf(10) }
    var wantsPlacement by remember { mutableStateOf<Boolean?>(null) }

    // Editing from Settings: prefill from the saved profile.
    LaunchedEffect(Unit) {
        learnLang = container.languagePrefs.selectedLanguage.first()
        val p = container.languagePrefs.profile.first()
        if (p.name.isNotBlank()) name = p.name
        gender = p.gender
        if (p.from.isNotBlank()) from = p.from
        if (p.livesIn.isNotBlank()) livesIn = p.livesIn
        goal = container.languagePrefs.newWordsPerDay.first()
    }

    fun save(thenPlacement: Boolean) {
        scope.launch {
            container.languagePrefs.setProfile(
                LearnerProfile(
                    name = name.trim(),
                    gender = gender,
                    from = from.orEmpty().takeIf { it != SOMEWHERE_ELSE }.orEmpty(),
                    livesIn = livesIn.orEmpty().takeIf { it != SOMEWHERE_ELSE }.orEmpty(),
                    // The "why are you learning" step was removed: nothing consumed it.
                    // The field stays for backup compatibility, just no longer collected.
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
            STEP_NAME, STEP_GENDER, STEP_ORIGIN, STEP_GOAL, STEP_LEVEL
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
                modifier = Modifier.padding(vertical = LOGO_BAND)
            )
        }
        // Steps that have no logo still start their title where the welcome's title starts.
        if (step != STEP_WELCOME) Spacer(Modifier.height(LOGO_BAND))

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
                title = "What's your name?",
                subtitle = "It becomes your very first phrase: introducing yourself.",
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

            // ---- Where from / where living ----
            STEP_ORIGIN -> StepFrame(
                title = "Where are you from, and where do you live?",
                subtitle = if (learnLang == "hr")
                    "These become your intro phrases, with the correct Croatian case endings."
                else "These become your first intro phrases.",
                actions = {
                    NextRow(
                        enabled = from != null && livesIn != null,
                        onBack = { go(-1) },
                        onNext = { go(+1) }
                    )
                }
            ) {
                CountryPicker("I am from", from) { from = it }
                Spacer(Modifier.height(10.dp))
                CountryPicker("I live in", livesIn) { livesIn = it }
            }

            // ---- Daily goal ----
            STEP_GOAL -> StepFrame(
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

@Composable
private fun StepFrame(
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
        Spacer(Modifier.height(STEP_TITLE_GAP))

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

        Spacer(Modifier.height(STEP_ACTION_GAP))
        actions()
    }
}

@Composable
private fun NextRow(enabled: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
    // 50/50: a 1:2 split squeezed "← Back" into wrapping at larger font scales.
    Row(modifier = Modifier.fillMaxWidth().padding(top = STEP_ACTION_GAP)) {
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

@Composable
private fun CountryPicker(label: String, selected: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label:  ${selected ?: "choose…"}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            NATIONS.forEach { n ->
                DropdownMenuItem(
                    text = { Text(n.english) },
                    onClick = { onSelect(n.english); expanded = false }
                )
            }
            DropdownMenuItem(
                text = { Text(SOMEWHERE_ELSE) },
                onClick = { onSelect(SOMEWHERE_ELSE); expanded = false }
            )
        }
    }
}
