package com.corlang.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.ai.AiClient
import com.corlang.app.ai.ChatMessage
import com.corlang.app.ai.ReplyGuard
import kotlinx.coroutines.launch

/**
 * The AI conversation partner. Chat with Claude acting as a patient Croatian tutor pitched at
 * the learner's current CEFR level: it converses in simple Croatian, glosses new words, and
 * gently corrects mistakes. Tap any tutor line to hear it in the Croatian voice.
 *
 * Premium-gated. The transcript lives in memory for the session.
 */
@Composable
fun TalkScreen(container: AppContainer, lang: String) {
    // Premium-gated. Null while the entitlement loads, render nothing for that frame.
    val entitled by container.premium.entitled.collectAsState(initial = null as Boolean?)
    if (entitled == null) return
    if (entitled == false) {
        // Coming-soon dialog until Play Billing ships; then this onClick becomes the
        // purchase flow (docs/server-ai.md, step 4) and nothing else here changes.
        var showComingSoon by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            com.corlang.app.ui.components.CorlangLogo(
                variant = com.corlang.app.ui.components.LogoVariant.ORBIT,
                size = 72.dp,
                brand = MaterialTheme.colorScheme.onSurfaceVariant,
                core = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Meet your conversation partner",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 14.dp)
            )
            Text(
                "Practice real back-and-forth conversation at your level, with gentle corrections " +
                    "and a voice for every reply. Part of Corlang Premium.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Button(
                onClick = { showComingSoon = true },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            ) { Text("⭐ Subscribe to Premium") }
        }
        if (showComingSoon) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showComingSoon = false },
                title = { Text("Almost there") },
                text = {
                    Text(
                        "Premium subscriptions arrive with the Google Play release. " +
                            "Everything else in Corlang stays free while you learn."
                    )
                },
                confirmButton = {
                    Button(onClick = { showComingSoon = false }) { Text("OK") }
                }
            )
        }
        return
    }

    assertTutorLangRegistered(lang)   // debug builds fail loudly on a language with no tutor content
    val progress by container.progress.progress(lang).collectAsState(initial = null)
    val level = progress?.currentLevel ?: "A1"
    val currentDay = progress?.currentDay ?: 1
    // Sent to the worker so the 40-msg/day cap keys on this subscriber (null on DEV_PREMIUM).
    val subToken by container.languagePrefs.subPurchaseToken.collectAsState(initial = null)
    val languageName = remember(lang) { container.content.meta(lang).name }
    val profile by container.languagePrefs.profile.collectAsState(
        initial = com.corlang.app.data.prefs.LearnerProfile("", "m", "", "", "")
    )
    val studentName = profile.name.trim()

    // English-help preference, per language. Unset (null) defaults ON for beginners (A0-A2) and
    // OFF above — the old passive hint's behaviour — and the learner's explicit choice sticks.
    val englishHelpPref by container.languagePrefs.tutorEnglishHelp(lang)
        .collectAsState(initial = null as Boolean?)
    val englishHelp = englishHelpPref ?: (level in setOf("A0", "A1", "A2"))

    // Compact progress snapshot for the tutor, so "review my last lesson" / "practise my words"
    // have something concrete to work from. Small on purpose — the worker caps the request.
    val lessonContext = remember(lang, currentDay) { buildTutorContext(container, lang, currentDay) }

    val system = remember(lang, level, languageName, studentName, englishHelp, lessonContext) {
        tutorSystemPrompt(lang, languageName, level, studentName, englishHelp, lessonContext)
    }

    // Transcript for display + as the API history (same list; roles map directly).
    //
    // It starts EMPTY. A native-authored greeting used to be message zero, so opening the tab
    // always dropped a bubble hard against the top bar before the learner had chosen anything.
    // That greeting still goes out as a hidden anchor in every request (see send): an
    // in-language few-shot anchor is one of the strongest measured levers against
    // wrong-language/variety drift (arXiv 2406.20052) and it pins the variety before the model
    // generates a word. Hiding it costs nothing, because it was never an API call.
    //
    // State lives in the app-scoped ChatStore, NOT in remember: a tab switch (or the
    // Teach↔Tutor crossfade) disposes this composable, and remember-held state wiped the
    // conversation and dropped in-flight, already-billed replies. Requests launch on
    // container.appScope for the same reason — the reply lands even if the user has left.
    var chatEpoch by rememberSaveable(lang) { mutableStateOf(0) }
    val convo = remember(lang, chatEpoch) { container.chat.conversation(lang) }
    val messages: SnapshotStateList<ChatMessage> = convo.messages
    var input by convo::draft
    var sending by convo::sending
    var error by convo::error
    val listState = rememberLazyListState()

    // The daily allowance, straight from the worker that enforces it.
    val quota by container.ai.quota.collectAsState()
    var limitReached by rememberSaveable { mutableStateOf(false) }

    // A conversation in progress locks the top-bar language picker — the transcript is
    // in-memory only and a language switch would wipe it. An empty screen doesn't lock.
    if (messages.isNotEmpty() || sending) {
        com.corlang.app.ui.Engagement.Report()
    }

    // Declared before send(): send() hides it, and a local fun cannot see a val
    // introduced after it.
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    fun send(text: String) {
        if (text.isBlank() || sending) return
        // Nothing left today: say so instead of spending a round trip to be refused.
        if (quota?.remaining == 0) { limitReached = true; return }
        // Put the keyboard away: the message is gone, so the composer has nothing left to type
        // into, and leaving it up hides the reply the learner is waiting for.
        keyboard?.hide()
        messages.add(ChatMessage("user", text.trim()))
        input = ""
        sending = true
        error = null
        container.appScope.launch {
            // Per-language chat model (verified via tools/ai-variety-eval.py, §4 gate):
            //   hr → Sonnet 5 WITH thinking. Haiku's Croatian bled into Serbian (~30% fail),
            //        and thinking-disabled Sonnet slipped on adversarial 'da'-explanation
            //        prompts; only Sonnet + reasoning passes 12/12 consistently.
            //   pt/fr → Haiku (both pass 12/12; higher-resource, no self-contradiction trap).
            //        Haiku doesn't think by default, so it's the cheap path.
            //
            // Payload shape: a hidden in-language user opener (the API requires user-first)
            // + the authored seed greeting + recent turns. History is trimmed: variety and
            // CEFR-level adherence measurably DRIFT as conversations grow (alignment drift;
            // pt-PT→pt-BR reversion over turns), and a short window also caps cost. The
            // trim drops whole exchanges so user/assistant alternation stays valid.
            //
            // The opener and the greeting are BOTH synthetic: neither is in `messages`, so the
            // screen stays blank until the learner speaks, while the model still sees an
            // in-language assistant turn before it generates anything.
            var tail = messages.toList().takeLast(12)
            if (tail.firstOrNull()?.role == "assistant") tail = tail.drop(1)
            val payload = listOf(
                ChatMessage("user", seedOpener(lang)),
                ChatMessage("assistant", seedGreeting(lang))
            ) + tail
            val result = container.ai.complete(
                system = system,
                messages = payload,
                model = if (lang == "hr") AiClient.FEEDBACK_MODEL else AiClient.DEFAULT_MODEL,
                // hr runs Sonnet WITH thinking, which shares max_tokens with the visible
                // reply — give it the proxy cap (2048) so reasoning can't starve the answer.
                maxTokens = if (lang == "hr") 2048 else 1024,
                subToken = subToken
            )
            /*
             * Nothing reaches the learner unvetted. A reply once arrived carrying a Russian word
             * and the model narrating its own recovery ("wait, let me stay in croatian properly"),
             * which is not something a prompt can promise never to produce. ReplyGuard inspects
             * the actual output; a rejected reply is retried ONCE with the failure named, and if
             * the retry also fails the learner gets an honest error rather than the bad text.
             * The discarded attempt is still billed, which is the right trade against showing it.
             */
            var vetted = result.mapCatching { reply ->
                when (val v = ReplyGuard.inspect(reply, lang)) {
                    is ReplyGuard.Verdict.Ok -> reply
                    is ReplyGuard.Verdict.Reject -> throw GuardRejection(v.reason)
                }
            }
            (vetted.exceptionOrNull() as? GuardRejection)?.let { rejected ->
                vetted = container.ai.complete(
                    system = system + ReplyGuard.retryNudge(rejected.reason, languageName),
                    messages = payload,
                    model = if (lang == "hr") AiClient.FEEDBACK_MODEL else AiClient.DEFAULT_MODEL,
                    maxTokens = if (lang == "hr") 2048 else 1024,
                    subToken = subToken
                ).mapCatching { reply ->
                    when (val v = ReplyGuard.inspect(reply, lang)) {
                        is ReplyGuard.Verdict.Ok -> reply
                        is ReplyGuard.Verdict.Reject -> throw GuardRejection(v.reason)
                    }
                }
            }
            sending = false
            vetted.fold(
                onSuccess = { messages.add(ChatMessage("assistant", it)) },
                onFailure = {
                    error = if (it is GuardRejection)
                        "That reply broke one of the tutor's own rules, so it was thrown " +
                            "away before you saw it. Send it again."
                    else it.message ?: "Something went wrong."
                }
            )
        }
    }

    // Keep the newest message in view as the conversation grows.
    LaunchedEffect(messages.size, sending) {
        val count = messages.size + if (sending) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    // The keyboard shortens the list, so the newest message slides out of view under it and the
    // learner has to scroll back to read what they are answering. Following the IME height keeps
    // the end of the conversation pinned as the keyboard opens and closes.
    val imeBottom = androidx.compose.foundation.layout.WindowInsets.ime
        .getBottom(androidx.compose.ui.platform.LocalDensity.current)
    LaunchedEffect(imeBottom) {
        val count = messages.size + if (sending) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    // imePadding: with edge-to-edge on, the keyboard would otherwise cover the composer row
    // entirely — the user typed blind on the one screen where typing is the whole point.
    if (limitReached) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { limitReached = false },
            title = { Text("That is today's tutor time") },
            text = {
                Text(
                    "You have used all ${quota?.limit ?: 0} tutor messages for today. They come " +
                        "back tomorrow. Everything else in the app keeps working in the meantime."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { limitReached = false }) {
                    Text("Got it")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        // Deliberately plain text rather than a chip or a progress bar: it is a fact the learner
        // may want, not a scoreboard, and a conversation is the wrong place to put a countdown
        // in front of somebody. It only raises its voice at the end, when it is actionable.
        quota?.let { q ->
            Text(
                if (q.remaining == 0) "No tutor messages left today"
                else "${q.remaining} of ${q.limit} messages left today",
                style = MaterialTheme.typography.labelSmall,
                color = if (q.remaining == 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        // The opening screen: nothing has been said yet, so this IS the screen until the learner
        // picks a starter or types. It lives OUTSIDE the LazyColumn on purpose — as a list item
        // it hung off the top edge, leaving the choices stranded under the title bar with the
        // whole screen empty below them. Centred in the free space, the starters read as the
        // point of the screen. verticalScroll + Arrangement.Center centres while it fits and
        // scrolls when it does not (small screen, large font).
        if (messages.isEmpty() && !sending) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TutorEnglishHelpToggle(
                    checked = englishHelp,
                    languageName = languageName,
                    onCheckedChange = { on ->
                        container.appScope.launch {
                            container.languagePrefs.setTutorEnglishHelp(lang, on)
                        }
                    }
                )
                Text(
                    "How would you like to practise? ($level)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp)
                )
                // Clear English labels (an A0 could not read the old target-language
                // starters); the tutor replies in $languageName using the progress context.
                tutorModes().forEach { mode ->
                    OutlinedButton(
                        onClick = { send(mode.kickoff) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp, vertical = 10.dp
                        )
                    ) {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                mode.label,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Text(
                                mode.desc,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                Text(
                    "…or just type a message below.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            // Top air so the first bubble does not sit hard against the top bar.
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 12.dp, bottom = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(messages) { _, msg ->
                MessageBubble(
                    msg = msg,
                    onSpeak = { container.tts.speak(stripGloss(msg.content)) }
                )
            }
            if (sending) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        com.corlang.app.ui.components.CorlangRingSpinner(size = 20.dp)
                        Text("Tutor is typing…", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
        }
        }

        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // End the conversation. A chat outlives this screen on purpose (a tab switch must not
        // drop an in-flight, already-billed reply), which also meant the only way to start a
        // fresh one was to kill the app. Bumping the epoch re-reads a brand new conversation.
        if (messages.isNotEmpty() && !sending) {
            androidx.compose.material3.TextButton(
                onClick = {
                    container.chat.reset(lang)
                    chatEpoch++
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            ) {
                Text("End chat and start a new one")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                // Hard cap: a tutor message is conversation, not a document. Without this a
                // pasted wall of text rides the transcript into EVERY following request (the
                // last 12 messages are resent each turn), multiplying its token cost by up
                // to 12. The worker's body cap is the wall; this is the fence.
                onValueChange = { input = it.take(TUTOR_INPUT_MAX) },
                placeholder = { Text(composerHint(lang, languageName, englishHelp)) },
                supportingText = if (input.length >= TUTOR_INPUT_MAX * 9 / 10) {
                    { Text("${input.length}/$TUTOR_INPUT_MAX") }
                } else null,
                modifier = Modifier.weight(1f),
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { send(input) }, enabled = input.isNotBlank() && !sending) {
                Text("Send")
            }
        }
    }
}

/**
 * The "Teach me in English" switch on the opening screen.
 *
 * It used to be a bare Row on the same flat background as the starters directly below it, so the
 * label read as a heading for them and the switch looked like it belonged to whatever text sat
 * nearest. Its own tinted, rounded surface — plus a line saying what it does — makes it one
 * self-contained control instead of loose text with a switch beside it. The whole card is the
 * tap target, so the label works as well as the switch.
 */
@Composable
private fun TutorEnglishHelpToggle(
    checked: Boolean,
    languageName: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    "Teach me in English",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (checked) "The tutor explains in English and gives you $languageName to use."
                    else "The tutor speaks $languageName. Turn this on if you get stuck.",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, onSpeak: () -> Unit) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(msg.content, style = MaterialTheme.typography.bodyMedium)
                if (!isUser) {
                    Text(
                        "🔊 Tap to hear",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clickable { onSpeak() }
                    )
                }
            }
        }
    }
}

/**
 * Languages with authored tutor content (starters, seed greeting/opener, variety rules,
 * composer hint). The `else` branches below degrade an unregistered language to English
 * silently — safe, but a NEW course must never ship on that fallback unnoticed: the seed
 * greeting would anchor the whole chat in English and there'd be no variety rule. The debug
 * check makes it fail loudly during development instead.
 */
/** Chat input cap, chars. Generous for real sentences, hostile to pasted documents. */
private const val TUTOR_INPUT_MAX = 500

private val TUTOR_LANGS = setOf("hr", "pt", "fr", "de", "it", "es")

internal fun assertTutorLangRegistered(lang: String) {
    if (com.corlang.app.BuildConfig.DEBUG) {
        check(lang in TUTOR_LANGS) {
            "Language \"$lang\" has no authored tutor content (TalkScreen tables), " +
                "add starters/seedGreeting/seedOpener/varietyRules/composerHint before shipping."
        }
    }
}

/** A way to start a tutor session. [kickoff] is the (English) opening the learner sends; the
 *  tutor replies in the target language, steered by the progress context in its system prompt. */
private data class TutorMode(val label: String, val desc: String, val kickoff: String)

private fun tutorModes(): List<TutorMode> = listOf(
    TutorMode(
        "Have a conversation", "Free chat at your level",
        "Let's just have a conversation. Ask me something to get started."
    ),
    TutorMode(
        "Learn something new", "A new word or grammar point",
        "Teach me one new thing that fits my level, then help me practise it."
    ),
    TutorMode(
        "Review my last lesson", "Practise what you just studied",
        "Let's practise what I studied in my last lesson."
    ),
    TutorMode(
        "Practise my words", "Quiz on words you've learned",
        "Quiz me on a few of the words I've been learning recently."
    )
)

/**
 * A compact progress snapshot for the tutor's system prompt: the current lesson plus a sample of
 * recently-learned words, so the "review last lesson" and "practise my words" modes are grounded
 * in what the learner has actually done. Deliberately small — the worker caps the request, and a
 * long dump would crowd out the reply.
 */
private fun buildTutorContext(container: AppContainer, lang: String, currentDay: Int): String {
    val day = container.content.plan(lang).days.firstOrNull { it.day == currentDay }
    val perDay = com.corlang.app.data.Fsrs.NEW_WORDS_PER_DAY
    // Everything the course has introduced so far. Deck position IS the introduction schedule
    // (WordsRepository.unlockedNewWords), so this window is exactly what the student has met.
    val met = container.words.allWords(lang).take(currentDay.coerceAtLeast(1) * perDay)
    // The tutor needs the BOUNDARY, not the whole list: a full deck runs to thousands of words
    // and the worker caps the request. The newest words are the ones being consolidated, so they
    // are the ones worth naming; the count tells the model how small the world is.
    val recent = met.takeLast(TUTOR_CONTEXT_WORDS).joinToString(", ") { "${it.hr} (${it.en})" }
    return buildString {
        if (day != null) append("- Current lesson: \"${day.title}\", ${day.objective}\n")
        append("- Vocabulary the student has met so far: about ${met.size} words, the first " +
            "${met.size} of this course. Assume ANY word past that is unknown to them.\n")
        if (recent.isNotBlank()) append("- Their most recent words (lean on these): $recent")
    }.trim()
}

/** How many recent words to name for the tutor. Enough to anchor, small enough for the cap. */
private const val TUTOR_CONTEXT_WORDS = 45

/**
 * Composer hint. With "Teach me in English" ON the learner is being taught in English, and a
 * hint written in the target language is the one instruction they cannot read — so it is given
 * in English, naming the language they are meant to answer in. OFF, it stays in-language.
 */
private fun composerHint(lang: String, languageName: String, englishHelp: Boolean): String =
    if (englishHelp) "Chat with Tutor…" else inLanguageHint(lang)

/** Per-language composer hint ("write in <language>" in that language). */
private fun inLanguageHint(lang: String): String = when (lang) {
    "hr" -> "Piši na hrvatskom…"
    "pt" -> "Escreve em português…"
    "fr" -> "Écris en français…"
    "de" -> "Schreib auf Deutsch…"
    "it" -> "Scrivi in italiano…"
    "es" -> "Escribe en español…"
    else -> "Chat with Tutor…"
}

/** Removes "(English gloss)" parentheticals so the Croatian voice doesn't read English aloud. */
private fun stripGloss(text: String): String =
    text.replace(Regex("\\([^)]*\\)"), "").replace(Regex("\\s{2,}"), " ").trim()

/**
 * Native-authored seed exchange: the greeting the learner sees on opening the Tutor, and the
 * hidden one-word opener that precedes it in the API payload (the Messages API is user-first).
 * Being IN the target variety, the pair doubles as a few-shot anchor — measurably one of the
 * strongest levers against wrong-language drift (arXiv 2406.20052: 5-shot raised line-level
 * language consistency from 86% to 99%).
 */
fun seedGreeting(lang: String): String = when (lang) {
    "hr" -> "Bok! Ja sam tvoj hrvatski tutor. Možemo razgovarati o čemu god želiš, polako i jednostavno. Kako si danas?"
    "pt" -> "Olá! Sou o teu tutor de português europeu. Podemos falar sobre o que quiseres, com calma e frases simples. Como estás hoje?"
    "fr" -> "Bonjour ! Je suis ton tuteur de français. On peut parler de ce que tu veux, doucement et simplement. Comment vas-tu aujourd'hui ?"
    "de" -> "Hallo! Ich bin dein Deutschtutor. Wir können über alles reden, was du möchtest, ganz langsam und mit einfachen Sätzen. Wie geht es dir heute?"
    "it" -> "Ciao! Sono il tuo tutor di italiano. Possiamo parlare di quello che vuoi, con calma e con frasi semplici. Come stai oggi?"
    "es" -> "¡Hola! Soy tu tutor de español. Podemos hablar de lo que quieras, con calma y con frases sencillas. ¿Qué tal estás hoy?"
    else -> "Hi! I'm your language tutor. We can talk about anything you like, slowly and simply. How are you today?"
}

private fun seedOpener(lang: String): String = when (lang) {
    "hr" -> "Bok!"
    "pt" -> "Olá!"
    "fr" -> "Bonjour !"
    "de" -> "Hallo!"
    "it" -> "Ciao!"
    "es" -> "¡Hola!"
    else -> "Hello!"
}

/**
 * The variety guardrail is the load-bearing part: without it the model drifted into SERBIAN
 * for Croatian — it "corrected" the correct 'trebam učiti' into the Serbian da-construction
 * 'trebam da učim' (field report). Exam prep punishes exactly those variety mistakes.
 */
private fun varietyRules(lang: String): String = when (lang) {
    "hr" -> """
    - You speak STANDARD CROATIAN (hrvatski standardni jezik) — NEVER Serbian, Bosnian, or mixed
      forms. Concretely: after modal and semi-modal verbs use the INFINITIVE (trebam učiti, mogu
      doći, želim ići) — NEVER the Serbian 'da' + present ('trebam da učim' is WRONG in Croatian).
      Yes/no questions use the '-li' enclitic or 'je li' (Dolaziš li?, Je li točno?) — NEVER the
      Serbian 'da li' ('da li dolaziš' is non-standard in Croatian). Use ijekavian forms (lijepo,
      mlijeko, htjeti), Croatian month names (siječanj, veljača...), and Croatian vocabulary
      (tjedan, kruh, tisuća, zrak, vlak — never nedelja, hleb, hiljada, vazduh, voz).
    - If the student's sentence is ALREADY correct standard Croatian, do not invent a correction —
      confirm it's right and continue. Never "correct" a correct form, and do not present two
      valid orderings as if one were an error.""".trimIndent()
    "pt" -> """
    - You speak EUROPEAN Portuguese (português europeu, Portugal) — NEVER Brazilian. Concretely:
      'estar a' + infinitive (estou a aprender — never 'estou aprendendo'), tu with correct verb
      forms in informal speech, European clitic placement (chamo-me, disse-lhe), and European
      vocabulary (pequeno-almoço, autocarro, telemóvel, casa de banho — never café da manhã,
      ônibus, celular, banheiro).
    - If the student's sentence is ALREADY correct European Portuguese, do not invent a
      correction — confirm it's right and continue. Never "correct" a correct form.""".trimIndent()
    "fr" -> """
    - You speak standard metropolitan French, as tested by the DELF exams.
    - If the student's sentence is ALREADY correct, do not invent a correction — confirm it's
      right and continue.""".trimIndent()
    "de" -> """
    - You speak STANDARD GERMAN (Standarddeutsch, Bundesrepublik), as tested by the
      Goethe-Zertifikat and telc exams. Use the standard forms: ich habe/bin as the perfect
      auxiliary per verb (NOT the southern 'ich bin gesessen/gestanden/gelegen'), 'am Samstag'
      style prepositions, and standard vocabulary (Brötchen, Tüte, Sahne, Januar, Tschüss).
    - Austrian and Swiss forms are NOT errors in their own countries, but they are not what this
      course teaches: if the student uses Jänner, Sackerl, Obers, Grüß Gott, Velo or Rüebli,
      say plainly that it is Austrian or Swiss and give the standard German equivalent, rather
      than marking it simply wrong.
    - Swiss German drops the ß entirely; this course writes ß where the standard requires it
      (Straße, groß, heißen), and after a short vowel writes ss (dass, muss, Fluss).
    - Never mix in English words a German speaker would not use, and keep capitalisation of
      nouns correct, since that is what an exam corrector checks first.
    - If the student's sentence is ALREADY correct standard German, do not invent a correction,
      confirm it is right and continue. Never "correct" a correct form.""".trimIndent()
    "it" -> """
    - You speak STANDARD ITALIAN (italiano standard), as tested by the CILS and CELI exams, not a
      regional variety. Concretely: use the standard passato prossimo and imperfetto rather than
      the passato remoto that is still spoken in the south, avoid regional lexis (use anguria or
      cocomero as regional twins but say which is which, and prefer standard forms over dialect),
      and keep the standard double consonants, which carry meaning (nono against nonno, capello
      against cappello).
    - Regional forms are NOT errors where they are spoken, but they are not what this course
      teaches: if the student writes a Tuscan, Roman or southern form, name it as regional and
      give the standard equivalent rather than marking it simply wrong.
    - Watch the two traps an exam corrector checks first: the accents that distinguish words
      (e against è, si against sì, la against là) and the auxiliary in the passato prossimo,
      essere for movement and reflexives, avere for the rest, with the participle agreeing when
      the auxiliary is essere.
    - If the student's sentence is ALREADY correct standard Italian, do not invent a correction,
      confirm it is right and continue. Never "correct" a correct form.""".trimIndent()
    "es" -> """
    - You speak PENINSULAR SPANISH (español de España, the Castilian standard), which is what
      this course teaches. Concretely: use the full four-way address system, tú and vosotros
      informally and usted and ustedes formally, and conjugate vosotros forms (habláis, tenéis,
      hablad, no habléis) rather than avoiding them. Use the peninsular pretérito perfecto for
      anything inside a period reaching now (hoy he comido, esta semana he trabajado). Use
      peninsular lexis: coche, ordenador, móvil, patata, zumo, billete, piso, gafas, conducir,
      nevera, chaqueta. Note that coger is the ordinary verb here and is not to be avoided or
      apologised for.
    - AMERICAN SPANISH IS NOT WRONG. The official exam explicitly accepts any Hispanic norm
      followed coherently, and from B1 its own reading and listening texts are drawn from every
      variety. So if the student writes carro, celular, computadora, manejar, jugo, boleto, or
      uses ustedes for an informal plural, or writes hoy comí where Spain would write hoy he
      comido, do NOT mark it as an error: name it as the American form, give the peninsular
      equivalent this course teaches, and move on. The same applies to voseo (vos sos, vos
      tenés): recognise it, explain it, do not produce it yourself.
    - Watch the two traps an exam corrector checks first. The written accent, because it can
      carry the whole meaning (hablo against habló, esta against está, si against sí, tu against
      tú, que against qué), and the object pronouns, especially le turning into se before lo and
      la (se lo doy, never le lo doy) and the doubled indirect object (le doy el libro a Ana).
    - Keep to the level. At A1 and A2 there is no subjunctive at all. At B1 use only the PRESENT
      subjunctive: do not introduce the imperfect subjunctive or the si tuviera dinero, viajaría
      pattern, which belong above this course. A condition with si at B1 takes the present
      indicative: si tengo tiempo, voy.
    - If the student's sentence is ALREADY correct peninsular Spanish, do not invent a
      correction, confirm it is right and continue. Never "correct" a correct form.""".trimIndent()
    else -> "- If the student's sentence is already correct, say so, never invent corrections."
}

private fun tutorSystemPrompt(
    lang: String,
    languageName: String,
    level: String,
    studentName: String,
    englishHelp: Boolean,
    lessonContext: String
): String {
    // Toggled by the learner (pre-chat). English is ALWAYS allowed on request either way; this
    // only controls how PROACTIVELY the tutor glosses/explains in English.
    val englishRule = if (englishHelp)
        // English LEADS. The old rule kept the whole reply in the target language and only
        // glossed the new word, which assumes the student can already follow a full message in
        // it. A learner who turns this on is telling you they cannot.
        "- ENGLISH-LED MODE. Assume the student cannot yet follow a full message in " +
            "$languageName. Write to them in ENGLISH: say what you mean, and what you are asking, " +
            "in English first.\n" +
            "- Give the $languageName they need as SHORT quoted material inside that English: the " +
            "phrase to say, the word to learn, the question to answer. Immediately explain each " +
            "piece in English, including what it means literally and why the form is what it is.\n" +
            "- Ask your follow-up question in English, and tell them in English what you want them " +
            "to reply in $languageName. Never send a reply that is entirely in $languageName.\n" +
            "- Keep the $languageName itself correct and level-appropriate; the English is the " +
            "scaffolding around it, not a translation bolted on at the end."
    else
        "- The student prefers to stay in $languageName, so the burden is on you to stay inside " +
            "what they can read. Build your sentences from words they have already met (see their " +
            "progress below) plus obvious international words. Keep sentences short and simple.\n" +
            "- If a word outside that vocabulary is genuinely unavoidable, choose the most common " +
            "option and put a two or three word English gloss in parentheses straight after it. " +
            "Never send a paragraph of unknown vocabulary: the student cannot ask about a word " +
            "they cannot even parse.\n" +
            "- Switch to English fully only if they explicitly ask."
    // Progress context appended AFTER trimIndent so it isn't re-indented; blank for a new learner.
    val contextBlock = if (lessonContext.isBlank()) "" else
        "\n\nWhat this student is working on right now (use it to tailor the session, especially if " +
            "they ask to review their last lesson or practise their words; do not read it out " +
            "verbatim):\n$lessonContext"
    return """
    You are a warm, patient $languageName conversation tutor. Your student is an adult learning
    $languageName at CEFR level $level, preparing for the official $languageName exam, so accuracy
    matters, but keep it encouraging.
    ${if (studentName.isBlank()) "" else
        "The student's name is $studentName. Use it occasionally, the way a real tutor would, " +
        "not in every message."}

    Rules:
    ${varietyRules(lang)}
    $englishRule
    - Converse mainly in $languageName, kept at or slightly below level $level. Use short, natural sentences.
    - The student may ask you anything in ENGLISH at any time (what a word means, why a form is used,
      how to say something). Answer directly and briefly IN ENGLISH, then return to $languageName.
      Never refuse, never pretend not to understand, and never scold them for using English.
    - If the student makes a genuine mistake, gently correct it: give the corrected $languageName
      sentence and a one-line reason, then continue naturally. Don't nitpick; focus on what helps most.
    - NEVER WRITE THE ANSWER YOU ARE ASKING FOR, in either mode. When you want the student to
      produce something, say what it should MEAN in English and stop there. Write "how would you
      say 'I like coffee'?" and never "how would you answer this? type 'volim kavu'", and never
      hand over the pieces either: "say it using 'ne radim' and 'trazim posao'" is the same
      mistake, because once you name the words there is nothing left to produce. If you need to
      demonstrate a pattern, demonstrate it on a DIFFERENT phrase than the one you are asking
      for. A reply that breaks this is discarded before the student sees it.
    - Always end with a simple follow-up question to keep the conversation going.
    - Keep each reply short (2 to 5 sentences) so it stays a real back-and-forth, not a lecture.
    - Use correct $languageName spelling and accents at all times.
    - PLAIN TEXT ONLY: no markdown, no asterisks, no bullet lists — your reply is shown verbatim
      in a chat bubble.
    """.trimIndent() + contextBlock
}

/** A reply that failed [ReplyGuard]; carried as a failure so the retry can name it. */
private class GuardRejection(val reason: String) : Exception(reason)
