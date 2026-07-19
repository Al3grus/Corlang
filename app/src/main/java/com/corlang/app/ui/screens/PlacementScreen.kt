package com.corlang.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.data.Placement
import com.corlang.app.data.WordsRepository
import kotlinx.coroutines.launch

/**
 * A short adaptive check across all levels. The ladder is a series of ability BANDS, each three
 * independent items, cleared on 2 of 3; the test binary-searches them for the highest band the
 * learner can clear. The result sets the current lesson day and level, so nobody starts at day 1.
 *
 * It replaced a linear walk with one item per band that ended on the first wrong answer, which
 * let a lucky guess promote a learner a whole band and gave an advanced learner a one in three
 * chance of being placed too low by a single mis-tap. See [Placement] for the arithmetic.
 */
@Composable
fun PlacementScreen(container: AppContainer, lang: String, onDone: () -> Unit) {
    // A placement test in progress locks the top-bar language picker (mid-session switch guard).
    com.corlang.app.ui.Engagement.Report()
    val scope = rememberCoroutineScope()
    val test = remember(lang) { container.content.placement(lang) }
    if (test == null) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            com.corlang.app.ui.components.CorlangLogo(
                variant = com.corlang.app.ui.components.LogoVariant.ORBIT,
                size = 56.dp,
                brand = MaterialTheme.colorScheme.onSurfaceVariant,
                core = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "No placement test for this language yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
            Button(onClick = onDone, modifier = Modifier.padding(top = 12.dp)) { Text("Back") }
        }
        return
    }
    // Bands, easiest first: each is three independent items at one ability level, cleared on
    // 2 of 3. Bands are located by binary search (see Placement), so a learner answers about a
    // dozen items whatever their level, and no single mis-tap can end the test.
    val bands = remember(lang) { Placement.bandsOf(test.questions) }
    val maxItems = remember(bands) { Placement.maxItems(bands.size) }

    var search by remember(lang) { mutableStateOf(Placement.start(bands.size)) }
    var itemInBand by remember(lang) { mutableIntStateOf(0) }
    var correctInBand by remember(lang) { mutableIntStateOf(0) }
    var wrongInBand by remember(lang) { mutableIntStateOf(0) }
    var asked by remember(lang) { mutableIntStateOf(0) }
    var chosen by remember(lang) { mutableStateOf<String?>(null) }
    var finished by remember(lang) { mutableStateOf(false) }

    // Where the search currently says the learner belongs.
    val placement = remember(search, bands) { Placement.result(bands, search) }
    val placeLevel = placement.first
    val placeDay = placement.second

    if (finished) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            Text("You're placed at", style = MaterialTheme.typography.titleMedium)
            Text(
                "$placeLevel · Lesson $placeDay",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            Text(
                "Your lessons will start here. Earlier lessons stay available to review any time, and " +
                    "you can retake this test from Settings.",
                style = MaterialTheme.typography.bodyMedium
            )
            // A short test cannot prove you know every word it skipped, so the run-up to your
            // placement is queued for REVIEW, not retaught. Anything you have forgotten shows up
            // as a failed card and returns to normal scheduling.
            val seedCount = remember(placeDay) {
                val (from, until) = WordsRepository.prePlacementRange(placeDay)
                until - from
            }
            if (seedCount > 0) {
                Text(
                    "Because this test is short, the words from the lessons just before here, " +
                        "about $seedCount of them, are added to your reviews so nothing slips " +
                        "through the cracks. They arrive a few a day over the next three weeks, " +
                        "hardest first. Anything you already know you will pass once and rarely " +
                        "see again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            Button(
                onClick = {
                    // Close only AFTER the write commits — calling onDone() first would remove
                    // this screen and cancel the scope before setPlacement ever runs (leaving
                    // you on Day 1).
                    scope.launch {
                        container.progress.setPlacement(lang, placeDay, placeLevel)
                        // Skip the deck past the placed-over days: a Day-61 learner must get
                        // Day-61 vocabulary, not the deck's day-1 basics. Stored as the DAY
                        // (offset derived at read time from the current pace, see
                        // LanguagePrefs.wordDeckStart) so a later pace change can't starve
                        // new words. Overwritten (not maxed) on retake so placing lower
                        // re-opens earlier words.
                        container.languagePrefs.setPlacementDay(lang, placeDay)
                        // Check, don't reteach: the lessons just before the placement point are
                        // queued for review, so a mis-placement surfaces as failed cards instead
                        // of silent gaps. Anchored at the placement point, so it can never touch
                        // words the learner has not reached yet.
                        container.words.seedPrePlacementForReview(lang, placeDay)
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
            ) { Text("Start at $placeLevel · Lesson $placeDay") }
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Cancel")
            }
        }
        return
    }

    // The search settles the moment lo passes hi; guard against a test with no bands at all.
    val probeIndex = search.probe
    if (probeIndex == null || bands.isEmpty()) {
        finished = true
        return
    }
    val band = bands[probeIndex]
    val q = band.items[itemInBand.coerceAtMost(band.items.lastIndex)]

    /** Folds one answer into the band, then into the search once the band is decided. */
    fun answer(wasCorrect: Boolean) {
        val correct = correctInBand + if (wasCorrect) 1 else 0
        val wrong = wrongInBand + if (wasCorrect) 0 else 1
        asked++
        val lastInBand = itemInBand + 1 >= band.items.size
        if (Placement.bandDecided(correct, wrong) || lastInBand) {
            search = Placement.advance(search, probeIndex, Placement.bandCleared(correct))
            itemInBand = 0; correctInBand = 0; wrongInBand = 0
            if (search.finished) finished = true
        } else {
            itemInBand++; correctInBand = correct; wrongInBand = wrong
        }
        chosen = null
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        if (asked == 0) {
            Text(test.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(test.intro, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 18.dp))
        }
        // Progress against the worst case: the bar only ever moves forward, and the count is
        // "about" because a band that settles in two items saves the third.
        LinearProgressIndicator(
            progress = { ((asked + 1f) / maxItems).coerceIn(0f, 1f) },
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
        )
        Text("Question ${asked + 1} of about $maxItems",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(q.prompt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 24.dp))

        // Keyed on the item actually being shown, so two identically-worded prompts never share
        // a stale shuffle.
        val shown = remember(probeIndex, itemInBand) { q.options.shuffled() }
        shown.forEach { option ->
            val isChosen = chosen == option
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 9.dp)
                    .border(
                        2.dp,
                        if (isChosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { chosen = option }
            ) { Text(option, modifier = Modifier.padding(16.dp)) }
        }

        Button(
            onClick = { answer(chosen == q.answer) },
            enabled = chosen != null,
            modifier = Modifier.fillMaxWidth().padding(top = 36.dp)
        ) { Text("Next →") }

        // Honest exit when a question is too hard. Counts as failing THIS band only, not as
        // ending the test: the search then looks lower, which is exactly what it should do.
        OutlinedButton(
            onClick = { answer(false) },
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
        ) { Text("I don't know this one") }
        Spacer(Modifier.height(16.dp))
    }
}
