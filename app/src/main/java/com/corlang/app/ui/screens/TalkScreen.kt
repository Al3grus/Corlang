package com.corlang.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.ai.AiClient
import com.corlang.app.ai.ChatMessage
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
    // Sent to the worker so the 40-msg/day cap keys on this subscriber (null on DEV_PREMIUM).
    val subToken by container.languagePrefs.subPurchaseToken.collectAsState(initial = null)
    val languageName = remember(lang) { container.content.meta(lang).name }
    val profile by container.languagePrefs.profile.collectAsState(
        initial = com.corlang.app.data.prefs.LearnerProfile("", "m", "", "", "")
    )
    val studentName = profile.name.trim()
    val system = remember(lang, level, languageName, studentName) {
        tutorSystemPrompt(lang, languageName, level, studentName)
    }

    // Transcript for display + as the API history (same list; roles map directly).
    // Seeded with a NATIVE-AUTHORED greeting: an in-language few-shot anchor is one of the
    // strongest measured levers against wrong-language/variety drift (arXiv 2406.20052), it
    // pins the variety before the model generates a single word, and the first exchange
    // costs no API call.
    //
    // State lives in the app-scoped ChatStore, NOT in remember: a tab switch (or the
    // Teach↔Tutor crossfade) disposes this composable, and remember-held state wiped the
    // conversation and dropped in-flight, already-billed replies. Requests launch on
    // container.appScope for the same reason — the reply lands even if the user has left.
    val convo = container.chat.conversation(lang) {
        ChatMessage("assistant", seedGreeting(lang))
    }
    val messages: SnapshotStateList<ChatMessage> = convo.messages
    var input by convo::draft
    var sending by convo::sending
    var error by convo::error
    val listState = rememberLazyListState()

    // A conversation in progress locks the top-bar language picker — the transcript is
    // in-memory only and a language switch would wipe it. The seed greeting alone (size 1,
    // no learner turn yet) doesn't lock.
    if (messages.size > 1 || sending) {
        com.corlang.app.ui.Engagement.Report()
    }

    fun send(text: String) {
        if (text.isBlank() || sending) return
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
            val all = messages.toList()
            var tail = all.drop(1).takeLast(12)   // after the seed: u,a,u,a…
            if (tail.firstOrNull()?.role == "assistant") tail = tail.drop(1)
            val payload = listOf(ChatMessage("user", seedOpener(lang)), all.first()) + tail
            val result = container.ai.complete(
                system = system,
                messages = payload,
                model = if (lang == "hr") AiClient.FEEDBACK_MODEL else AiClient.DEFAULT_MODEL,
                // hr runs Sonnet WITH thinking, which shares max_tokens with the visible
                // reply — give it the proxy cap (2048) so reasoning can't starve the answer.
                maxTokens = if (lang == "hr") 2048 else 1024,
                subToken = subToken
            )
            sending = false
            result.fold(
                onSuccess = { messages.add(ChatMessage("assistant", it)) },
                onFailure = { error = it.message ?: "Something went wrong." }
            )
        }
    }

    // Keep the newest message in view as the conversation grows.
    LaunchedEffect(messages.size, sending) {
        val count = messages.size + if (sending) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    // imePadding: with edge-to-edge on, the keyboard would otherwise cover the composer row
    // entirely — the user typed blind on the one screen where typing is the whole point.
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(messages) { _, msg ->
                MessageBubble(
                    msg = msg,
                    onSpeak = { container.tts.speak(stripGloss(msg.content)) }
                )
            }
            // Starters BELOW the seed greeting, until the learner's first message. (The old
            // messages.isEmpty() condition went dead when the seed greeting arrived — the
            // list is never empty now, so the starters had silently vanished.)
            if (messages.size <= 1 && !sending) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(
                            "Tap a starter or type your own ($level).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        starters(lang).forEach { starter ->
                            OutlinedButton(
                                onClick = { send(starter) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            ) { Text(starter) }
                        }
                    }
                }
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

        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Explicit permission to break out into English. Beginners stall because they assume the
        // chat has to be entirely in the target language, and a learner who can ask a question
        // keeps talking instead of quitting. A0 to A2 only: from B1 the point is to push through
        // without falling back. It lives in the UI rather than the greeting on purpose, the seed
        // greeting is an in-language anchor against variety drift and must stay all in-language.
        if (level in setOf("A0", "A1", "A2")) {
            Text(
                "Stuck? Ask anything in English and your tutor will explain.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(composerHint(lang)) },
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
private val TUTOR_LANGS = setOf("hr", "pt", "fr", "de")

internal fun assertTutorLangRegistered(lang: String) {
    if (com.corlang.app.BuildConfig.DEBUG) {
        check(lang in TUTOR_LANGS) {
            "Language \"$lang\" has no authored tutor content (TalkScreen tables) — " +
                "add starters/seedGreeting/seedOpener/varietyRules/composerHint before shipping."
        }
    }
}

/** Per-language conversation starters — shown until the learner sends their first message. */
private fun starters(lang: String): List<String> = when (lang) {
    "hr" -> listOf(
        "Bok! Kako si danas?",
        "Možemo li vježbati naručivanje kave?",
        "Postavi mi jedno lako pitanje.",
        "Ispravi moje greške, molim te."
    )
    "pt" -> listOf(
        "Olá! Como estás?",
        "Podemos praticar como pedir um café?",
        "Faz-me uma pergunta fácil.",
        "Corrige os meus erros, por favor."
    )
    "fr" -> listOf(
        "Bonjour ! Comment ça va ?",
        "On peut pratiquer comment commander un café ?",
        "Pose-moi une question facile.",
        "Corrige mes erreurs, s'il te plaît."
    )
    "de" -> listOf(
        "Hallo! Wie geht es dir?",
        "Können wir üben, einen Kaffee zu bestellen?",
        "Stell mir eine leichte Frage.",
        "Korrigiere bitte meine Fehler."
    )
    else -> listOf("Hello! How are you?")
}

/** Per-language composer hint ("write in <language>" in that language). */
private fun composerHint(lang: String): String = when (lang) {
    "hr" -> "Piši na hrvatskom…"
    "pt" -> "Escreve em português…"
    "fr" -> "Écris en français…"
    "de" -> "Schreib auf Deutsch…"
    else -> "Write in your learning language…"
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
    else -> "Hi! I'm your language tutor. We can talk about anything you like, slowly and simply. How are you today?"
}

private fun seedOpener(lang: String): String = when (lang) {
    "hr" -> "Bok!"
    "pt" -> "Olá!"
    "fr" -> "Bonjour !"
    "de" -> "Hallo!"
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
    else -> "- If the student's sentence is already correct, say so — never invent corrections."
}

private fun tutorSystemPrompt(
    lang: String,
    languageName: String,
    level: String,
    studentName: String
): String = """
    You are a warm, patient $languageName conversation tutor. Your student is an adult learning
    $languageName at CEFR level $level, preparing for the official $languageName exam, so accuracy
    matters, but keep it encouraging.
    ${if (studentName.isBlank()) "" else
        "The student's name is $studentName. Use it occasionally, the way a real tutor would, " +
        "not in every message."}

    Rules:
    ${varietyRules(lang)}
    - Converse mainly in $languageName, kept at or slightly below level $level. Use short, natural sentences.
    - When you use a word or phrase the student likely doesn't know yet, add a brief English gloss
      in parentheses right after it.
    - The student may ask you anything in ENGLISH at any time: what a word means, why a form is
      used, how to say something. Answer that question directly and clearly IN ENGLISH, briefly,
      then return to $languageName and keep the conversation going. Never refuse, never pretend
      not to understand, and never scold them for using English. Falling back to English when
      stuck is normal, and a learner who can ask questions keeps talking instead of giving up.
    - If the student makes a genuine mistake, gently correct it: give the corrected $languageName
      sentence and a one-line reason, then continue naturally. Don't nitpick; focus on what helps most.
    - Always end with a simple follow-up question to keep the conversation going.
    - Keep each reply short (2 to 5 sentences) so it stays a real back-and-forth, not a lecture.
    - Use correct $languageName spelling and accents at all times.
    - PLAIN TEXT ONLY: no markdown, no asterisks, no bullet lists — your reply is shown verbatim
      in a chat bubble.
""".trimIndent()
