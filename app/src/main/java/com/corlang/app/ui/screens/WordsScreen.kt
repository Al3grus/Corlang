package com.corlang.app.ui.screens

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import com.corlang.app.ui.theme.CorlangColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Cards per gym "set" — sized to finish inside one rest between exercise sets (~60-90 s). */
private const val SET_SIZE = 7

/** Words graduate to EN→HR production (recall, not recognition) from this SRS box up. */
private const val PRODUCTION_BOX = 3

/** Persisted mid-session state so a pocketed/killed phone resumes exactly where it left off. */
@Serializable
private data class SessionSnapshot(
    val epochDay: Long,
    val remainingIds: List<String>,
    val done: Int,
    val total: Int,
    val setDone: Int
)

private val snapshotJson = Json { ignoreUnknownKeys = true }

/**
 * Daily vocabulary on a spaced-repetition schedule, built for micro-sessions:
 * sets of [SET_SIZE] cards, swipe grading (left = again, right = good, up = easy),
 * instant resume, and a daily goal ring. Established words flip to EN→HR production.
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
    var setDone by remember(lang) { mutableIntStateOf(0) }     // graded within the current set
    var served by remember(lang) { mutableIntStateOf(0) }

    // UI mode: dashboard, in-session, or the between-sets breather.
    var inSession by remember(lang) { mutableStateOf(false) }
    var setComplete by remember(lang) { mutableStateOf(false) }
    var celebration by remember(lang) { mutableStateOf(false) }

    /** Snapshot of the CURRENT queue state, encoded synchronously (null = nothing to resume). */
    fun snapshotNow(): String? =
        if (queue.isEmpty()) null else snapshotJson.encodeToString(
            SessionSnapshot(
                epochDay = WordsRepository.todayEpochDay(),
                remainingIds = queue.map { it.word.id },
                done = doneCount, total = sessionTotal, setDone = setDone
            )
        )

    LaunchedEffect(lang, refreshKey, newPerDay) {
        // Never clobber a live session (pace changes are only reachable from the dashboard).
        if (inSession) return@LaunchedEffect
        val snapText = container.languagePrefs.wordsSessionSnapshot.first()
        val today = WordsRepository.todayEpochDay()
        val snap = snapText?.let {
            runCatching { snapshotJson.decodeFromString<SessionSnapshot>(it) }.getOrNull()
        }
        queue.clear()
        if (refreshKey == 0 && snap != null && snap.epochDay == today && snap.remainingIds.isNotEmpty()) {
            // Resume the interrupted session exactly where it was left.
            queue.addAll(container.words.sessionFromIds(lang, snap.remainingIds))
            sessionTotal = snap.total
            doneCount = snap.done
            setDone = snap.setDone
        } else {
            queue.addAll(container.words.buildSession(lang, today, newPerDay))
            sessionTotal = queue.size
            doneCount = 0
            setDone = 0
            // Keep prefs in agreement with the freshly built queue — otherwise a stale
            // same-day snapshot could be restored later and re-serve already-graded cards.
            container.languagePrefs.setWordsSessionSnapshot(snapshotNow())
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
            setDone++
        }
        val sessionDone = queue.isEmpty()
        val setJustDone = !sessionDone && setDone >= SET_SIZE
        if (setJustDone) setDone = 0
        // Encode the snapshot from the already-mutated queue, then write grade + snapshot in
        // ONE coroutine in order — a force-kill can no longer desync Room from DataStore
        // (which would re-serve or silently skip a card on resume).
        val snap = if (sessionDone) null else snapshotNow()
        scope.launch {
            container.words.grade(lang, card.word.id, g)
            container.languagePrefs.setWordsSessionSnapshot(snap)
            if (sessionDone || setJustDone) container.progress.recordStudyActivity(lang)
            // Rebuild only after the final grade is committed, so buildSession can't
            // momentarily re-include the just-graded card.
            if (sessionDone) refreshKey++
        }
        if (sessionDone) {
            inSession = false
            setComplete = false
            celebration = true
        } else if (setJustDone) {
            setComplete = true   // breather between sets; any completed set = streak credit
        }
    }

    if (inSession && setComplete && queue.isNotEmpty()) {
        SetBreather(
            remaining = queue.size,
            done = doneCount,
            total = sessionTotal,
            onNextSet = { setComplete = false },
            onPause = { setComplete = false; inSession = false }
        )
        return
    }

    if (inSession && queue.isNotEmpty()) {
        WordSession(
            card = queue.first(),
            cardKey = served,
            tts = container.tts,
            done = doneCount,
            total = sessionTotal,
            onGrade = ::grade,
            onExit = { inSession = false }   // snapshot already persisted per grade
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
                    "${allWords.size} core words · sets of $SET_SIZE fit a gym rest",
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
            WordStat("${queue.size}", "to review", Modifier.weight(1f))
            WordStat("${seenIds.size}", "started", Modifier.weight(1f))
            WordStat("$mastered", "mastered", Modifier.weight(1f))
        }

        if (celebration && queue.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
            ) {
                Text(
                    "🎉 Goal ring closed — today's words are done and your streak is safe.",
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
                    queue.isEmpty() -> "All done for today ✓"
                    doneCount > 0 -> "Continue session (${queue.size} left)"
                    else -> "Start session (${queue.size} words)"
                }
            )
        }

        SectionTitle("📦 Packs")
        vocab.packs.forEach { pack ->
            val seen = pack.words.count { it.id in seenIds }
            InfoCard {
                Text(pack.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${pack.level} · $seen / ${pack.words.size} started",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
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
private fun WordStat(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Between-sets breather: celebrate the set, offer the next one. Rest-timer friendly. */
@Composable
private fun SetBreather(
    remaining: Int,
    done: Int,
    total: Int,
    onNextSet: () -> Unit,
    onPause: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GoalRing(
            progress = if (total == 0) 1f else done.toFloat() / total,
            label = "$done/$total",
            size = 120.dp,
            stroke = 12.dp
        )
        Spacer(Modifier.height(16.dp))
        Text("💪 Set done!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Streak credited. $remaining words left today — next set whenever you're ready.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        Button(onClick = onNextSet, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
            Text("Next set ($SET_SIZE cards)")
        }
        OutlinedButton(onClick = onPause, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Done for now")
        }
    }
}

@Composable
private fun WordSession(
    card: SessionCard,
    cardKey: Int,
    tts: TtsManager,
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

    // Swipe-to-grade offsets (one-handed gym grading).
    var dragX by remember(cardKey) { mutableStateOf(0f) }
    var dragY by remember(cardKey) { mutableStateOf(0f) }
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (production) "Say it in Croatian" else "Word review",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$done / $total" + if (isNew) " · ✨ new" else "",
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
                        translationX = dragX
                        translationY = dragY.coerceAtMost(0f)
                        rotationZ = dragX / 60f
                    }
                    .pointerInput(cardKey, isRevealed) {
                        if (isRevealed) {
                            detectDragGestures(
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragX += amount.x
                                    dragY += amount.y
                                },
                                onDragEnd = {
                                    when {
                                        dragY < -threshold -> onGrade(SrsGrade.GOOD)
                                        dragX > threshold -> onGrade(SrsGrade.EASY)
                                        dragX < -threshold -> onGrade(SrsGrade.AGAIN)
                                    }
                                    dragX = 0f; dragY = 0f
                                },
                                onDragCancel = { dragX = 0f; dragY = 0f }
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
                ) { Text("Again") }
                Button(onClick = { onGrade(SrsGrade.GOOD) }, modifier = Modifier.weight(1f)) {
                    Text("Good")
                }
                Button(
                    onClick = { onGrade(SrsGrade.EASY) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = feedback.correctContainer,
                        contentColor = feedback.onCorrectContainer
                    ),
                    modifier = Modifier.weight(1f)
                ) { Text("Easy") }
            }
            Text(
                "Swipe the card: ← again · ↑ good · → easy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text("Pause — resumes exactly here")
        }
    }
}
