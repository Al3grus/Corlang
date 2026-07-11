package com.corlang.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.ai.ChatMessage
import kotlinx.coroutines.launch

/**
 * The AI conversation partner. Chat with Claude acting as a patient Croatian tutor pitched at
 * the learner's current CEFR level: it converses in simple Croatian, glosses new words, and
 * gently corrects mistakes. Tap any tutor line to hear it in the Croatian voice.
 *
 * Gated on the user's own API key (Settings). The transcript lives in memory for the session.
 */
@Composable
fun TalkScreen(container: AppContainer, lang: String) {
    val apiKey by container.languagePrefs.anthropicApiKey.collectAsState(initial = "")
    if (apiKey.isBlank()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🤖", style = MaterialTheme.typography.displayMedium)
            Text(
                "Meet your conversation partner",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                "Practice real back-and-forth Croatian at your level, with gentle corrections. " +
                    "To turn it on, add your Anthropic API key in Settings (top-right).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        return
    }

    val progress by container.progress.progress(lang).collectAsState(initial = null)
    val level = progress?.currentLevel ?: "A1"
    val system = remember(level) { tutorSystemPrompt(level) }

    // Transcript for display + as the API history (same list; roles map directly).
    val messages: SnapshotStateList<ChatMessage> = remember(lang) { mutableListOf<ChatMessage>().toMutableStateList() }
    var input by remember(lang) { mutableStateOf("") }
    var sending by remember(lang) { mutableStateOf(false) }
    var error by remember(lang) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun send(text: String) {
        if (text.isBlank() || sending) return
        messages.add(ChatMessage("user", text.trim()))
        input = ""
        sending = true
        error = null
        scope.launch {
            val result = container.ai.complete(system = system, messages = messages.toList())
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

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                        Text(
                            "Start a conversation ($level). Tap a starter or type your own.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        STARTERS.forEach { starter ->
                            OutlinedButton(
                                onClick = { send(starter) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            ) { Text(starter) }
                        }
                    }
                }
            }
            itemsIndexed(messages) { _, msg ->
                MessageBubble(
                    msg = msg,
                    onSpeak = { container.tts.speak(stripGloss(msg.content)) }
                )
            }
            if (sending) {
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(20.dp),
                            strokeWidth = 2.dp
                        )
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

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Piši na hrvatskom…") },
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

private val STARTERS = listOf(
    "Bok! Kako si danas?",
    "Možemo li vježbati naručivanje kave?",
    "Postavi mi jedno lako pitanje.",
    "Ispravi moje greške, molim te."
)

/** Removes "(English gloss)" parentheticals so the Croatian voice doesn't read English aloud. */
private fun stripGloss(text: String): String =
    text.replace(Regex("\\([^)]*\\)"), "").replace(Regex("\\s{2,}"), " ").trim()

private fun tutorSystemPrompt(level: String): String = """
    You are a warm, patient Croatian conversation tutor. Your student is an adult learning
    Croatian at CEFR level $level. He is preparing for the official Croatian B1 exam and wants
    to speak naturally with his Croatian family, so accuracy matters, but keep it encouraging.

    Rules:
    - Converse mainly in Croatian, kept at or slightly below level $level. Use short, natural sentences.
    - When you use a word or phrase the student likely doesn't know yet, add a brief English gloss
      in parentheses right after it.
    - If the student makes a mistake, gently correct it: give the corrected Croatian sentence and a
      one-line reason, then continue naturally. Don't nitpick every tiny error; focus on what helps most.
    - Always end with a simple follow-up question to keep the conversation going.
    - Keep each reply short (2 to 5 sentences) so it stays a real back-and-forth, not a lecture.
    - Use correct Croatian diacritics (č, ć, š, ž, đ) at all times.
""".trimIndent()
