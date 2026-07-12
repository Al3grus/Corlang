package com.corlang.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.data.SessionCard
import com.corlang.app.data.SrsGrade
import com.corlang.app.data.WordsRepository
import com.corlang.app.speech.TtsManager
import com.corlang.app.ui.Haptics
import com.corlang.app.ui.components.GoalRing
import com.corlang.app.ui.components.InfoCard
import com.corlang.app.ui.components.SectionTitle
import com.corlang.app.ui.components.SpeakerButton
import com.corlang.app.ui.components.StatTile
import com.corlang.app.ui.theme.CorlangColors
import com.corlang.app.ui.theme.Motion
import com.corlang.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Words graduate to EN→HR production (recall, not recognition) from this SRS box up. */
private const val PRODUCTION_BOX = 3

/** Persisted mid-session state so a closed/killed phone resumes exactly where it left off. */
@Serializable
private data class SessionSnapshot(
    val epochDay: Long,
    val remainingIds: List<String>,
    val done: Int,
    val total: Int,
    // Stamped so a snapshot can never be resumed under the wrong language (defense in depth on
    // top of the per-language DataStore key). Defaults empty for snapshots written by old builds.
    val langCode: String = ""
)

private val snapshotJson = Json { ignoreUnknownKeys = true }

/**
 * Daily vocabulary on a spaced-repetition schedule: the day's due reviews plus new words up
 * to your chosen goal (10/15/20 in Settings). Swipe grading (left = again, up = good,
 * right = easy), instant resume, and a daily goal ring. Established words flip to EN→HR
 * production. You can always learn more past the goal, and revisit any pack you've started.
 */
@Composable
fun WordsScreen(container: AppContainer, lang: String) {
    val scope = rememberCoroutineScope()
    val allWords = remember(lang) { container.words.allWords(lang) }
    val reviews by container.words.reviews(lang).collectAsState(initial = emptyList())
    val newPerDay by container.languagePrefs.newWordsPerDay.collectAsState(initial = 10)

    var refreshKey by remember(lang) { mutableIntStateOf(0) }
    val queue = remember(lang) { mutableStateListOf<SessionCard>() }
    var sessionTotal by remember(lang) { mutableIntStateOf(0) }
    var doneCount by remember(lang) { mutableIntStateOf(0) }
    var served by remember(lang) { mutableIntStateOf(0) }

    var inSession by remember(lang) { mutableStateOf(false) }
    // Pack-revisit mode: a review round of a chosen pack that must NOT touch the daily
    // session's saved progress; when it ends we restore the daily session.
    var reviewMode by remember(lang) { mutableStateOf(false) }
    var celebration by remember(lang) { mutableStateOf(false) }

    /** Snapshot of the CURRENT daily queue, encoded synchronously (null = nothing to resume). */
    fun snapshotNow(): String? =
        if (queue.isEmpty()) null else snapshotJson.encodeToString(
            SessionSnapshot(
                epochDay = WordsRepository.todayEpochDay(),
                remainingIds = queue.map { it.word.id },
                done = doneCount, total = sessionTotal,
                langCode = lang
            )
        )

    LaunchedEffect(lang, refreshKey, newPerDay) {
        // Never clobber a live session (pace changes / pack reviews are dashboard-only).
        if (inSession) return@LaunchedEffect
        val snapText = container.languagePrefs.wordsSessionSnapshot(lang).first()
        val today = WordsRepository.todayEpochDay()
        val snap = snapText?.let {
            runCatching { snapshotJson.decodeFromString<SessionSnapshot>(it) }.getOrNull()
        }
        queue.clear()
        if (snap != null && snap.epochDay == today && snap.remainingIds.isNotEmpty() &&
            (snap.langCode.isEmpty() || snap.langCode == lang)) {
            // Resume the interrupted daily session exactly where it was left.
            queue.addAll(container.words.sessionFromIds(lang, snap.remainingIds))
            sessionTotal = snap.total
            doneCount = snap.done
        } else {
            queue.addAll(container.words.buildSession(lang, today, newPerDay))
            sessionTotal = queue.size
            doneCount = 0
            // Keep prefs in agreement with the freshly built queue, otherwise a stale
            // same-day snapshot could be restored later and re-serve already-graded cards.
            container.languagePrefs.setWordsSessionSnapshot(lang, snapshotNow())
        }
        container.tts.ensureInit()   // warm TTS so the first speaker tap isn't swallowed
    }

    val context = LocalContext.current

    fun grade(g: SrsGrade) {
        val card = queue.removeAt(0)
        served++
        if (g == SrsGrade.AGAIN) Haptics.reject(context) else Haptics.confirm(context)
        if (g == SrsGrade.AGAIN) {
            queue.add(card)      // re-serve failed cards until they pass
        } else {
            doneCount++
        }
        val sessionDone = queue.isEmpty()
        // In review mode we never write the daily snapshot; in daily mode we write grade +
        // snapshot in ONE ordered coroutine so a force-kill can't desync Room from DataStore.
        val snap = if (sessionDone || reviewMode) null else snapshotNow()
        val wasReview = reviewMode
        scope.launch {
            container.words.grade(lang, card.word.id, g)
            if (!wasReview) container.languagePrefs.setWordsSessionSnapshot(lang, snap)
            // Rebuild only after the final grade is committed (restores the daily session
            // after a review, or produces the post-completion state after the daily session).
            if (sessionDone) refreshKey++
        }
        if (sessionDone) {
            inSession = false
            reviewMode = false
            celebration = !wasReview
        }
    }

    /** Start a review round of a pack you've already begun (its started words, shuffled). */
    fun startPackReview(wordIds: List<String>) {
        scope.launch {
            val cards = container.words.sessionFromIds(lang, wordIds.shuffled())
            if (cards.isEmpty()) return@launch
            queue.clear(); queue.addAll(cards)
            sessionTotal = cards.size
            doneCount = 0
            reviewMode = true
            celebration = false
            inSession = true
        }
    }

    if (inSession && queue.isNotEmpty()) {
        WordSession(
            card = queue.first(),
            cardKey = served,
            tts = container.tts,
            review = reviewMode,
            done = doneCount,
            total = sessionTotal,
            onGrade = ::grade,
            onExit = {
                inSession = false
                if (reviewMode) { reviewMode = false; refreshKey++ }  // restore daily session
                // (daily snapshot already persisted per grade)
            }
        )
        return
    }

    // ---------------- Dashboard ----------------

    val seenIds = remember(reviews) { reviews.map { it.wordId }.toSet() }
    val mastered = remember(reviews) { reviews.count { it.box >= 5 } }
    val vocab = remember(lang) { container.content.vocab(lang) }
    val ringProgress =
        if (sessionTotal == 0) 1f else doneCount.toFloat() / sessionTotal

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Words", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${allWords.size} core words · $newPerDay new a day, plus reviews",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            GoalRing(
                progress = ringProgress,
                label = if (queue.isEmpty()) "✓" else "${queue.size}"
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("${queue.size}", "to review", Modifier.weight(1f))
            StatTile("${seenIds.size}", "started", Modifier.weight(1f))
            StatTile("$mastered", "mastered", Modifier.weight(1f))
        }

        if (celebration && queue.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
            ) {
                Text(
                    "🎉 Today's words are done. Finish today's lesson to bank the streak.",
                    modifier = Modifier.padding(14.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Button(
            onClick = { celebration = false; inSession = true },
            enabled = queue.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
        ) {
            Text(
                when {
                    queue.isEmpty() -> "Daily goal reached ✓"
                    doneCount > 0 -> "Continue session (${queue.size} left)"
                    else -> "Start session (${queue.size} words)"
                }
            )
        }

        // The daily count is a floor, not a ceiling, keep learning past it whenever you want.
        if (queue.isEmpty()) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val extra = container.words.extraNewWords(lang, 10)
                        if (extra.isNotEmpty()) {
                            celebration = false
                            queue.addAll(extra)
                            sessionTotal += extra.size
                            container.languagePrefs.setWordsSessionSnapshot(lang, snapshotNow())
                            inSession = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            ) { Text("Learn 10 more words →") }
        }

        SectionTitle("📦 Packs")
        Text(
            "Tap a pack you've started to review its words any time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        vocab.packs.forEach { pack ->
            val startedIds = pack.words.map { it.id }.filter { it in seenIds }
            val seen = startedIds.size
            val canReview = seen > 0
            InfoCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (canReview) Modifier.clickable { startPackReview(startedIds) }
                            else Modifier
                        )
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(pack.title, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(
                            "${pack.level} · $seen / ${pack.words.size} started",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        if (canReview) "↻ review" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                LinearProgressIndicator(
                    progress = { if (pack.words.isEmpty()) 0f else seen.toFloat() / pack.words.size },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WordSession(
    card: SessionCard,
    cardKey: Int,
    tts: TtsManager,
    review: Boolean,
    done: Int,
    total: Int,
    onGrade: (SrsGrade) -> Unit,
    onExit: () -> Unit
) {
    var revealed by remember(cardKey) { mutableStateOf(false) }
    val feedback = CorlangColors.feedback
    val context = LocalContext.current
    val isNew = card.review == null
    // Established words flip direction: recall the Croatian from English (production).
    val production = (card.review?.box ?: 0) >= PRODUCTION_BOX

    // Swipe-to-grade with real physics: the card follows the finger, then either springs back
    // (under threshold) or flings off-screen in the swipe direction before the grade commits.
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    val offset = remember(cardKey) { Animatable(Offset.Zero, Offset.VectorConverter) }
    var graded by remember(cardKey) { mutableStateOf(false) }
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val threshold = with(density) { 96.dp.toPx() }
    val flyX = with(density) { config.screenWidthDp.dp.toPx() } * 1.3f
    val flyY = with(density) { config.screenHeightDp.dp.toPx() } * 1.3f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    review -> "Pack review"
                    production -> "Say it in Croatian"
                    else -> "Word review"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$done / $total" + if (isNew && !review) " · ✨ new" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )

        val front = if (production) card.word.en else card.word.hr
        val back = if (production) card.word.hr else card.word.en

        AnimatedContent(targetState = revealed, label = "card-flip") { isRevealed ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .heightIn(min = 240.dp)
                    .graphicsLayer {
                        translationX = offset.value.x
                        translationY = offset.value.y.coerceAtMost(0f)
                        rotationZ = offset.value.x / 60f
                    }
                    .pointerInput(cardKey, isRevealed) {
                        if (isRevealed) {
                            // Tick once when the drag crosses the grade threshold, so the swipe
                            // confirms itself under the finger (a moving hand masks weak buzzes).
                            var armed = false
                            detectDragGestures(
                                onDrag = { change, amount ->
                                    change.consume()
                                    val projected = offset.value + amount
                                    scope.launch { offset.snapTo(projected) }
                                    val over = projected.y < -threshold ||
                                        projected.x > threshold || projected.x < -threshold
                                    if (over && !armed) Haptics.tick(context)
                                    armed = over
                                },
                                onDragEnd = {
                                    armed = false
                                    val o = offset.value
                                    val grade = when {
                                        o.y < -threshold -> SrsGrade.GOOD
                                        o.x > threshold -> SrsGrade.EASY
                                        o.x < -threshold -> SrsGrade.AGAIN
                                        else -> null
                                    }
                                    if (grade != null && !graded) {
                                        // Fling the card off-screen in its direction, THEN commit
                                        // the grade (which swaps in the next card at center).
                                        graded = true
                                        val target = when (grade) {
                                            SrsGrade.GOOD -> Offset(o.x, -flyY)
                                            SrsGrade.EASY -> Offset(flyX, o.y)
                                            else -> Offset(-flyX, o.y)
                                        }
                                        scope.launch {
                                            if (reducedMotion) offset.snapTo(target)
                                            else offset.animateTo(target, Motion.settle())
                                            onGrade(grade)
                                        }
                                    } else {
                                        // Under threshold: settle back to center.
                                        scope.launch {
                                            if (reducedMotion) offset.snapTo(Offset.Zero)
                                            else offset.animateTo(Offset.Zero, Motion.settle())
                                        }
                                    }
                                },
                                onDragCancel = {
                                    armed = false
                                    scope.launch {
                                        if (reducedMotion) offset.snapTo(Offset.Zero)
                                        else offset.animateTo(Offset.Zero, Motion.settle())
                                    }
                                }
                            )
                        }
                    }
                    .clickable {
                        if (!isRevealed) {
                            revealed = true
                            Haptics.tick(context)
                            // Hear the Croatian at the moment it appears.
                            if (production) tts.speak(card.word.hr)
                        }
                    }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        front,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    if (!production) SpeakerButton(tts = tts, text = card.word.hr)
                    Spacer(Modifier.height(8.dp))
                    if (isRevealed) {
                        Text(
                            back,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = if (production) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                        if (production) SpeakerButton(tts = tts, text = card.word.hr)
                        card.word.pos?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                        }
                        card.word.note?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                        }
                        card.word.example?.let { ex ->
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "“${ex.target}”",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                ex.gloss,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                            SpeakerButton(tts = tts, text = ex.target)
                        }
                    } else {
                        Text(
                            if (production) "Say the Croatian aloud, then tap to check"
                            else "Say it aloud, then tap to reveal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        if (revealed) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onGrade(SrsGrade.AGAIN) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = feedback.wrongContainer,
                        contentColor = feedback.onWrongContainer
                    ),
                    modifier = Modifier.weight(1f)
                ) { Text("← Again") }
                Button(onClick = { onGrade(SrsGrade.GOOD) }, modifier = Modifier.weight(1f)) {
                    Text("↑ Good ↑")
                }
                Button(
                    onClick = { onGrade(SrsGrade.EASY) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = feedback.correctContainer,
                        contentColor = feedback.onCorrectContainer
                    ),
                    modifier = Modifier.weight(1f)
                ) { Text("Easy →") }
            }
            Text(
                "Tap a button, or swipe the card in its arrow's direction.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text(if (review) "End review" else "Pause, resumes exactly here")
        }
    }
}
