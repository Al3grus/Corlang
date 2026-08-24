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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
fun PlacementScreen(
    container: AppContainer,
    lang: String,
    onDone: () -> Unit,
    /**
     * Left the test WITHOUT finishing it. Distinct from [onDone] because the consequences are
     * different: an unfinished test placed nobody, so the caller must undo whatever it set up in
     * order to run it. Back used to call onDone, which silently left the learner in a language
     * they had never studied, at lesson 1, with the placement prompt already marked handled.
     */
    onAbandon: () -> Unit = onDone,
    /** Where leaving lands them, named so the confirmation can say it out loud. */
    returnTo: String? = null
) {
    // A placement test in progress locks the top-bar language picker (mid-session switch guard).
    com.corlang.app.ui.Engagement.Report()
    val scope = rememberCoroutineScope()
    val test = remember(lang) { container.content.placement(lang) }
    val unlockedLevels by container.premium.unlockedLevels.collectAsState(initial = emptySet())
    // How far into the deck this install may legitimately seed. The run-up to a placement is
    // review material for a level the learner is about to study; until they own that level there
    // is nothing legitimate to check them on, so the ceiling is the last lesson they can open.
    // It rises the moment they buy, and TodayScreen re-runs the seeder then.
    //
    // Hoisted to the top of the composable rather than kept beside the copy it feeds: the
    // confirm button that does the seeding sits outside that branch, and both must read the
    // same number or the screen would promise one thing and queue another.
    val context = androidx.compose.ui.platform.LocalContext.current
    // Does this install own the course outright? Decides whether the top-of-course screen may
    // say "every lesson is open" - see the copy there.
    val ownsWholeCourse = remember(lang, unlockedLevels) {
        com.corlang.app.BuildConfig.DEV_PREMIUM ||
            container.content.plan(lang).days.lastOrNull()?.level?.let {
                com.corlang.app.billing.PremiumManager.key(lang, it) in unlockedLevels
            } == true
    }
    val seedCeiling = remember(lang, unlockedLevels) {
        if (com.corlang.app.BuildConfig.DEV_PREMIUM) Int.MAX_VALUE
        else com.corlang.app.billing.PremiumManager.accessibleThroughDay(
            container.content.plan(lang).days,
            lang,
            container.content.meta(lang).freeLessons,
            unlockedLevels
        ) * com.corlang.app.data.Fsrs.NEW_WORDS_PER_DAY
    }
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
    // Sized from the largest band this course actually ships, so the "about N questions" promise
    // stays honest for a course still authored at three items per band.
    val maxItems = remember(bands) {
        Placement.maxItems(bands.size, bands.maxOfOrNull { it.items.size } ?: Placement.ITEMS_PER_BAND)
    }

    var search by remember(lang) { mutableStateOf(Placement.start(bands.size)) }
    var itemInBand by remember(lang) { mutableIntStateOf(0) }
    var correctInBand by remember(lang) { mutableIntStateOf(0) }
    var wrongInBand by remember(lang) { mutableIntStateOf(0) }
    var asked by remember(lang) { mutableIntStateOf(0) }
    var chosen by remember(lang) { mutableStateOf<String?>(null) }
    var finished by remember(lang) { mutableStateOf(false) }
    // The explanation used to sit above question one, so the first thing a learner met was a
    // paragraph about how the test adapts and, directly under it, a question already counting
    // against them. It is a gate now: read, then start deliberately.
    var started by rememberSaveable(lang) { mutableStateOf(false) }

    // The course's final lesson, so clearing the TOP band lands on it rather than on that band's
    // anchor (which is authored short of the end). See Placement.result.
    val courseEnd = remember(lang) {
        container.content.plan(lang).days.maxByOrNull { it.day }?.let { it.level to it.day }
    }
    // Where the search currently says the learner belongs.
    val placement = remember(search, bands, courseEnd) { Placement.result(bands, search, courseEnd) }
    val placeLevel = placement.first
    val placeDay = placement.second

    /*
     * Leaving mid-test is a real decision, so it is confirmed rather than obeyed. A placement
     * test is a dozen questions deep by the end and the back gesture is one swipe: losing it to
     * a mis-swipe, and being dropped into a language at lesson 1 as a result, is the worst
     * outcome this screen can produce. The confirmation names where leaving lands you.
     */
    var confirmLeave by remember(lang) { mutableStateOf(false) }
    // Only while the test is running: once the result is on screen there is nothing to lose, and
    // that screen has its own Cancel.
    androidx.activity.compose.BackHandler(enabled = started && !finished) { confirmLeave = true }
    if (confirmLeave) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave the placement test?") },
            text = {
                Text(
                    if (returnTo != null)
                        "Your answers so far will be lost and nothing will be placed. " +
                            "You'll go back to $returnTo."
                    else
                        "Your answers so far will be lost and nothing will be placed. " +
                            "You'll be asked again how you want to start this language."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmLeave = false
                    onAbandon()
                }) { Text("Leave") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmLeave = false }) {
                    Text("Keep going")
                }
            }
        )
    }

    // Cleared the hardest band the test has. The ladder cannot measure any higher, so saying
    // "you're placed at B1, lesson N" would report a ceiling as if it were a reading: the test
    // ran out of questions, it did not find this learner's level. Told plainly instead.
    val atCeiling = finished && search.placedIndex == bands.lastIndex && bands.isNotEmpty()

    if (finished) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            if (atCeiling) {
                // No "LEVEL · Lesson N" hero here, unlike the ordinary result. A number is the
                // answer to "where do I start?", and this learner's answer is "nowhere, you are
                // past it all" - printing a lesson number invited them to read a ceiling as a
                // reading, which is the one thing this branch exists to avoid.
                Text(
                    "You're at the top of this course",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
                Text(
                    "You answered everything this test can ask, so it has placed you at the " +
                        "final lesson. Your real level may well be higher: the test stops here " +
                        "because the course does.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    // Positional, not financial. Placement moves where the learner stands; it
                    // buys nothing. Saying "every lesson is unlocked" to someone on the free
                    // window would be flatly false, so what is open depends on what they own.
                    if (ownsWholeCourse)
                        "Every lesson and quiz in the course is open to you now, to practise and " +
                            "review in any order. The mock exams are probably what you came for, " +
                            "and word review will keep any gaps honest."
                    else
                        "Nothing in the course is ahead of you any more: every lesson you own is " +
                            "open to practise and review in any order. Levels you have not " +
                            "unlocked stay locked, because a placement test measures where you " +
                            "are, it does not buy the course.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    "Want to go further than this course goes? Ask for the level you need and " +
                        "we will email you when it exists. The form opens in your browser, " +
                        "because it asks for an address and this app does not.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://corlang.app/requests/")
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text("Request a language or level") }
            } else {
            Text("You're placed at", style = MaterialTheme.typography.titleMedium)
            Text(
                "$placeLevel · Lesson $placeDay",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            Text(
                "Your lessons will start here, and earlier lessons stay available to review any " +
                    "time.",
                style = MaterialTheme.typography.bodyMedium
            )
            // A short test cannot prove you know every word it skipped, so the run-up to your
            // placement is queued for REVIEW, not retaught. Anything you have forgotten shows up
            // as a failed card and returns to normal scheduling.
            val seedCount = remember(placeDay, seedCeiling) {
                val (from, until) = WordsRepository.prePlacementRange(placeDay)
                (minOf(until, seedCeiling) - from).coerceAtLeast(0)
            }
            if (seedCount > 0) {
                Text(
                    "Because this test is short, the words from the lessons just before here, " +
                        "about $seedCount of them, are added to your reviews so nothing slips " +
                        "through the cracks. They arrive a few a day, hardest first, never more " +
                        "than half your daily review limit. Anything you already know you will " +
                        "pass once and rarely see again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
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
                        // Clamped to what this install can actually open. Placement is free and
                        // offered at onboarding, so an unclamped seed let anyone who answered
                        // well enough collect 600 deck words without paying for the level that
                        // teaches them. TodayScreen tops it up the moment the course is bought.
                        container.words.seedPrePlacementForReview(
                            lang, placeDay, maxDeckIndex = seedCeiling
                        )
                        // Settled, and only now. The flag retires the one-time offer, so it is
                        // written where a placement actually exists rather than where one was
                        // merely intended: an app killed mid-test is offered the test again.
                        container.languagePrefs.markPlacementHandled(lang)
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
            ) {
                Text(
                    if (atCeiling) "Open the course at its last lesson"
                    else "Start at $placeLevel · Lesson $placeDay"
                )
            }
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
        if (Placement.bandDecided(correct, wrong, band.items.size) || lastInBand) {
            search = Placement.advance(search, probeIndex, Placement.bandCleared(correct, band.items.size))
            itemInBand = 0; correctInBand = 0; wrongInBand = 0
            if (search.finished) finished = true
        } else {
            itemInBand++; correctInBand = correct; wrongInBand = wrong
        }
        chosen = null
    }

    if (!started) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            Text(test.title, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text(test.intro, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 18.dp))
            Text(
                "About $maxItems questions at most, and fewer if the test settles early. " +
                    "Answer as well as you can and skip nothing: this only decides where you " +
                    "begin, and getting it wrong in either direction costs you time later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { started = true },
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp)
            ) { Text("Start placement") }
            OutlinedButton(
                onClick = onAbandon,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) { Text("Not now") }
            Spacer(Modifier.height(16.dp))
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        // Progress against the worst case: the bar only ever moves forward, and the count is
        // "about" because a band that settles in two items saves the third.
        LinearProgressIndicator(
            progress = { ((asked + 1f) / maxItems).coerceIn(0f, 1f) },
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
        )
        Text("Question ${asked + 1} of $maxItems",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(q.prompt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 24.dp))

        // Keyed on the item actually being shown, so two identically-worded prompts never share
        // a stale shuffle.
        val shown = remember(probeIndex, itemInBand) { q.options.shuffled() }
        shown.forEach { option ->
            // OptionRow, the same answer row quizzes and mock exams use, rather than a local
            // copy: this screen's own version marked the chosen answer with a border color only,
            // which became invisible in the light theme. Selection is one component's job.
            com.corlang.app.ui.components.OptionRow(
                text = option,
                state = if (chosen == option) com.corlang.app.ui.components.OptionState.SELECTED
                else com.corlang.app.ui.components.OptionState.DEFAULT,
                enabled = true,
                onClick = { chosen = option }
            )
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
