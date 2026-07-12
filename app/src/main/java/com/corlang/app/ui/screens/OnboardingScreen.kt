package com.corlang.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.data.prefs.LearnerProfile
import com.corlang.app.ui.components.CorlangLogo
import com.corlang.app.ui.components.InfoCard
import com.corlang.app.ui.components.LogoVariant
import com.corlang.app.ui.components.SpeakerButton
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
    NationOption("Argentina", "Argentinac", "Argentinka", "iz Argentine", "u Argentini"),
    NationOption("Australia", "Australac", "Australka", "iz Australije", "u Australiji"),
    NationOption("Austria", "Austrijanac", "Austrijanka", "iz Austrije", "u Austriji"),
    NationOption("Bosnia and Herzegovina", "Bosanac", "Bosanka", "iz Bosne i Hercegovine", "u Bosni i Hercegovini"),
    NationOption("Brazil", "Brazilac", "Brazilka", "iz Brazila", "u Brazilu"),
    NationOption("Canada", "Kanađanin", "Kanađanka", "iz Kanade", "u Kanadi"),
    NationOption("Czechia", "Čeh", "Čehinja", "iz Češke", "u Češkoj"),
    NationOption("France", "Francuz", "Francuskinja", "iz Francuske", "u Francuskoj"),
    NationOption("Germany", "Nijemac", "Njemica", "iz Njemačke", "u Njemačkoj"),
    NationOption("Ireland", "Irac", "Irkinja", "iz Irske", "u Irskoj"),
    NationOption("Italy", "Talijan", "Talijanka", "iz Italije", "u Italiji"),
    NationOption("Mexico", "Meksikanac", "Meksikanka", "iz Meksika", "u Meksiku"),
    NationOption("Netherlands", "Nizozemac", "Nizozemka", "iz Nizozemske", "u Nizozemskoj"),
    NationOption("Norway", "Norvežanin", "Norvežanka", "iz Norveške", "u Norveškoj"),
    NationOption("Poland", "Poljak", "Poljakinja", "iz Poljske", "u Poljskoj"),
    NationOption("Portugal", "Portugalac", "Portugalka", "iz Portugala", "u Portugalu"),
    NationOption("Serbia", "Srbin", "Srpkinja", "iz Srbije", "u Srbiji"),
    NationOption("Slovenia", "Slovenac", "Slovenka", "iz Slovenije", "u Sloveniji"),
    NationOption("Spain", "Španjolac", "Španjolka", "iz Španjolske", "u Španjolskoj"),
    NationOption("Sweden", "Šveđanin", "Šveđanka", "iz Švedske", "u Švedskoj"),
    NationOption("Switzerland", "Švicarac", "Švicarka", "iz Švicarske", "u Švicarskoj"),
    NationOption("United Kingdom", "Britanac", "Britanka", "iz Velike Britanije", "u Velikoj Britaniji"),
)

private const val SOMEWHERE_ELSE = "Somewhere else"

private val REASONS = listOf(
    "Family & marriage",
    "Citizenship / official exam",
    "Work",
    "Travel",
    "Heritage & roots",
)

/** The personalized phrases the profile unlocks; empty parts are simply skipped. */
fun profilePhrases(p: LearnerProfile): List<Pair<String, String>> {
    val from = NATIONS.firstOrNull { it.english == p.from }
    val livesIn = NATIONS.firstOrNull { it.english == p.livesIn }
    val out = mutableListOf<Pair<String, String>>()
    if (p.name.isNotBlank()) out += "Zovem se ${p.name.trim()}." to "My name is ${p.name.trim()}."
    from?.let {
        val nat = if (p.gender == "f") it.natF else it.natM
        out += "Ja sam $nat." to "I am ${it.english.demonymEn()}."
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

    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("m") }
    var from by remember { mutableStateOf<String?>(null) }
    var livesIn by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf<String?>(null) }
    var goal by remember { mutableStateOf(10) }
    var wantsPlacement by remember { mutableStateOf<Boolean?>(null) }

    // Editing from Settings: prefill from the saved profile.
    LaunchedEffect(Unit) {
        val p = container.languagePrefs.profile.first()
        if (p.name.isNotBlank()) name = p.name
        gender = p.gender
        if (p.from.isNotBlank()) from = p.from
        if (p.livesIn.isNotBlank()) livesIn = p.livesIn
        if (p.reason.isNotBlank()) reason = p.reason
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
                    reason = reason.orEmpty()
                )
            )
            container.languagePrefs.setNewWordsPerDay(goal)
            container.languagePrefs.setOnboardingDone(true)
            onFinish(thenPlacement)
        }
    }

    val totalSteps = 7
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp)
    ) {
        LinearProgressIndicator(
            progress = { (step + 1f) / totalSteps },
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
        )

        when (step) {
            // ---- 0 · Welcome ----
            0 -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                CorlangLogo(variant = LogoVariant.LOCKUP, size = 40.dp, modifier = Modifier.padding(top = 24.dp))
                Text(
                    "Jezik u srži",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
                )
                Text(
                    "A study-based path to real Croatian, built on the official curriculum and " +
                        "the actual B1 exam. Two minutes of setup makes it yours: your first " +
                        "phrases will literally be about you.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    Text("Set up my learning →")
                }
                OutlinedButton(
                    onClick = { save(thenPlacement = false) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Skip for now") }
            }

            // ---- 1 · Name ----
            1 -> StepFrame("What's your name?",
                "It becomes your very first phrase: introducing yourself.") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                NextRow(enabled = name.isNotBlank(), onBack = { step = 0 }, onNext = { step = 2 })
            }

            // ---- 2 · Word forms (gender) ----
            2 -> StepFrame("Which forms should Croatian use for you?",
                "Croatian words change with the speaker: a man says \"Ja sam Amerikanac, radio sam\", " +
                    "a woman says \"Ja sam Amerikanka, radila sam\".") {
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
                NextRow(enabled = true, onBack = { step = 1 }, onNext = { step = 3 })
            }

            // ---- 3 · Where from / where living ----
            3 -> StepFrame("Where are you from, and where do you live?",
                "These become your intro phrases, with the correct Croatian case endings.") {
                CountryPicker("I am from", from) { from = it }
                Spacer(Modifier.height(10.dp))
                CountryPicker("I live in", livesIn) { livesIn = it }
                NextRow(enabled = from != null && livesIn != null, onBack = { step = 2 }, onNext = { step = 4 })
            }

            // ---- 4 · Reason ----
            4 -> StepFrame("Why are you learning Croatian?",
                "So the app knows what \"ready\" means for you.") {
                REASONS.forEach { r ->
                    val chosen = reason == r
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
                            .clickable { reason = r }
                    ) { Text(r, modifier = Modifier.padding(12.dp)) }
                }
                NextRow(enabled = reason != null, onBack = { step = 3 }, onNext = { step = 5 })
            }

            // ---- 5 · Daily goal ----
            5 -> StepFrame("Your daily word goal",
                "New words per day for the flashcard deck. 10 is the sustainable default; you can " +
                    "always learn past the goal, and change this in Settings.") {
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
                NextRow(enabled = true, onBack = { step = 4 }, onNext = { step = 6 })
            }

            // ---- 6 · Level + payoff: your first phrases ----
            6 -> StepFrame("Last one: do you already know some Croatian?", "") {
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

                val phrases = profilePhrases(
                    LearnerProfile(name, gender, from.orEmpty(), livesIn.orEmpty(), reason.orEmpty())
                )
                if (phrases.isNotEmpty()) {
                    Text(
                        "Your first phrases, tap to hear them:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)
                    )
                    phrases.forEach { (hr, en) ->
                        InfoCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(hr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(en, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                SpeakerButton(tts = container.tts, text = hr)
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    OutlinedButton(onClick = { step = 5 }, modifier = Modifier.weight(1f)) { Text("← Back") }
                    Spacer(Modifier.height(0.dp))
                    Button(
                        onClick = { save(thenPlacement = wantsPlacement == true) },
                        enabled = wantsPlacement != null,
                        modifier = Modifier.weight(2f).padding(start = 8.dp)
                    ) { Text("Start learning →") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StepFrame(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun NextRow(enabled: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("← Back") }
        Button(
            onClick = onNext,
            enabled = enabled,
            modifier = Modifier.weight(2f).padding(start = 8.dp)
        ) { Text("Next →") }
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
