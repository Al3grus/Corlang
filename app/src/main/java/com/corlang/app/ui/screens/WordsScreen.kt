package com.corlang.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.key
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
import com.corlang.app.data.isLearned
import com.corlang.app.data.isMastered
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
 * Vocabulary REVIEW: the day's due spaced-repetition cards. New words are introduced through
 * lessons (each lesson unlocks its batch), not here — so this tab never runs ahead. Swipe grading
 * (left = again, up = good, right = easy), instant resume, a progress ring, and per-pack review of
 * words you've already started. Established words flip to EN→HR production.
 */
@Composable
fun WordsScreen(container: AppContainer, lang: String) {
    val scope = rememberCoroutineScope()
    val allWords = remember(lang) { container.words.allWords(lang) }
    val languageName = remember(lang) { container.content.meta(lang).name }
    val reviews by container.words.reviews(lang).collectAsState(initial = emptyList())

    var refreshKey by remember(lang) { mutableIntStateOf(0) }
    val queue = remember(lang) { mutableStateListOf<SessionCard>() }
    var sessionTotal by remember(lang) { mutableIntStateOf(0) }
    var doneCount by remember(lang) { mutableIntStateOf(0) }
    var served by remember(lang) { mutableIntStateOf(0) }

    var inSession by remember(lang) { mutableStateOf(false) }
    // False until the async queue build lands: the ring must not flash "✓ complete" (total==0)
    // on first frame and then visibly unwind to the real due count.
    var queueLoaded by remember(lang) { mutableStateOf(false) }
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

    LaunchedEffect(lang, refreshKey) {
        // Never clobber a live session (pack reviews are dashboard-only).
        if (inSession) return@LaunchedEffect
        val snapText = container.languagePrefs.wordsSessionSnapshot(lang).first()
        val today = WordsRepository.todayEpochDay()
        val snap = snapText?.let {
            runCatching { snapshotJson.decodeFromString<SessionSnapshot>(it) }.getOrNull()
        }
        queue.clear()
        // Resume candidates, minus any card that is no longer due — the in-lesson word blocks
        // grade cards without touching this snapshot, so a stale same-day snapshot would
        // otherwise re-serve (and re-grade) cards already cleared in a lesson.
        val resumed =
            if (snap != null && snap.epochDay == today && snap.remainingIds.isNotEmpty() &&
                (snap.langCode.isEmpty() || snap.langCode == lang)
            ) {
                container.words.sessionFromIds(lang, snap.remainingIds)
                    .filter { it.review == null || it.review.dueEpochDay <= today }
            } else emptyList()
        if (resumed.isNotEmpty() && snap != null) {
            // Resume the interrupted session; total = done + what actually remains due.
            queue.addAll(resumed)
            doneCount = snap.done
            sessionTotal = snap.done + resumed.size
        } else {
            queue.addAll(container.words.buildReviewSession(lang, today))
            sessionTotal = queue.size
            doneCount = 0
            // Keep prefs in agreement with the freshly built queue, otherwise a stale
            // same-day snapshot could be restored later and re-serve already-graded cards.
            container.languagePrefs.setWordsSessionSnapshot(lang, snapshotNow())
        }
        queueLoaded = true
        container.tts.ensureInit()   // warm TTS so the first speaker tap isn't swallowed
    }

    val context = LocalContext.current

    fun grade(g: SrsGrade) {
        if (queue.isEmpty()) return   // late fling/tap after the session already ended
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
        // App-scoped: switching tabs right after the last swipe must not cancel the write.
        val snap = if (sessionDone || reviewMode) null else snapshotNow()
        val wasReview = reviewMode
        container.appScope.launch {
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
        // System back pauses the session (state is persisted per grade), not the app.
        androidx.activity.compose.BackHandler {
            inSession = false
            if (reviewMode) { reviewMode = false; refreshKey++ }
        }
        WordSession(
            card = queue.first(),
            cardKey = served,
            tts = container.tts,
            languageName = languageName,
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
    val mastered = remember(reviews) { reviews.count { it.isMastered } }
    val vocab = remember(lang) { container.content.vocab(lang) }
    val ringProgress = when {
        !queueLoaded -> 0f
        sessionTotal == 0 -> 1f
        else -> doneCount.toFloat() / sessionTotal
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Review", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Spaced repetition of what you've learned. New words come from your lessons.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            GoalRing(
                progress = ringProgress,
                label = when {
                    !queueLoaded -> ""
                    queue.isEmpty() -> "✓"
                    else -> "${queue.size}"
                }
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
                    "🎉 All caught up on reviews. New words unlock as you do your lessons.",
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
                    queue.isEmpty() -> "No reviews due ✓"
                    doneCount > 0 -> "Continue review (${queue.size} left)"
                    else -> "Review ${queue.size} words"
                }
            )
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
internal fun WordSession(
    card: SessionCard,
    cardKey: Int,
    tts: TtsManager,
    languageName: String,
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
    val production = card.review?.isLearned == true

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

    // The incoming card settles in with a quick fade/scale so it doesn't just pop at center.
    val enter = remember(cardKey) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(cardKey) {
        if (enter.value < 1f) enter.animateTo(1f, Motion.snappy())
    }

    // ONE fling-then-grade path shared by swipes and the grade buttons, so both feel identical:
    // the old card flies off in the grade's direction (buttons fade meanwhile), and only then
    // does the next card arrive. Also the single guard against double-grading a card.
    fun flingAndGrade(g: SrsGrade) {
        if (graded) return
        graded = true
        val o = offset.value
        val target = when (g) {
            SrsGrade.GOOD -> Offset(o.x, -flyY)
            SrsGrade.EASY -> Offset(flyX, o.y)
            SrsGrade.AGAIN -> Offset(-flyX, o.y)
        }
        scope.launch {
            if (reducedMotion) offset.snapTo(target)
            else offset.animateTo(target, Motion.snappy())
            onGrade(g)
        }
    }

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
                    production -> "Say it in $languageName"
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

        // key(cardKey): the reveal-crossfade must be scoped to ONE card. Without it, the card
        // swap animated old-revealed → new-hidden and, mid-transition, rendered the NEW card's
        // data in the OLD revealed layout — the "what just flashed?" glitch.
        key(cardKey) {
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
                        alpha = enter.value
                        scaleX = 0.96f + 0.04f * enter.value
                        scaleY = 0.96f + 0.04f * enter.value
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
                                    if (grade != null) {
                                        flingAndGrade(grade)
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
                                style = com.corlang.app.ui.theme.CorlangType.readingLarge,
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
                            if (production) "Say the $languageName aloud, then tap to check"
                            else "Say it aloud, then tap to reveal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
        }   // key(cardKey)

        // Fade + collapse instead of popping in/out: the buttons appear when the card is
        // revealed and melt away DURING the fling, so the next card starts from a calm layout
        // instead of the row flashing on/off between cards.
        AnimatedVisibility(
            visible = revealed && !graded,
            enter = if (reducedMotion) fadeIn(snap()) else
                fadeIn(Motion.snappy()) + expandVertically(Motion.snappy()),
            exit = if (reducedMotion) fadeOut(snap()) else
                fadeOut(Motion.snappy()) + shrinkVertically(Motion.snappy())
        ) {
            Column {
                // Slim horizontal padding: three labels must fit side-by-side on 320dp-wide
                // phones and at large accessibility font scales without wrapping.
                val slimPad = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { flingAndGrade(SrsGrade.AGAIN) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = feedback.wrongContainer,
                            contentColor = feedback.onWrongContainer
                        ),
                        contentPadding = slimPad,
                        modifier = Modifier.weight(1f)
                    ) { Text("← Again", maxLines = 1, softWrap = false) }
                    Button(
                        onClick = { flingAndGrade(SrsGrade.GOOD) },
                        contentPadding = slimPad,
                        modifier = Modifier.weight(1f)
                    ) { Text("↑ Good ↑", maxLines = 1, softWrap = false) }
                    Button(
                        onClick = { flingAndGrade(SrsGrade.EASY) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = feedback.correctContainer,
                            contentColor = feedback.onCorrectContainer
                        ),
                        contentPadding = slimPad,
                        modifier = Modifier.weight(1f)
                    ) { Text("Easy →", maxLines = 1, softWrap = false) }
                }
                Text(
                    "Tap a button, or swipe the card in its arrow's direction.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }

        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text(if (review) "End review" else "Pause, resumes exactly here")
        }
    }
}
