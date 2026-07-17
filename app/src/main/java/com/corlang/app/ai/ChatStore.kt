package com.corlang.app.ai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * App-scoped holder for tutor conversations, one per language.
 *
 * The transcript must OUTLIVE the TalkScreen composable: `remember` state dies on any tab
 * switch (or the Teach↔Tutor crossfade), which wiped the whole conversation and dropped
 * in-flight — already billed — replies. Snapshot state works outside composition, so screens
 * observe these fields exactly as before; requests are launched on the app scope by the
 * caller so a reply lands in the store even if the user has navigated away.
 *
 * In-memory only, by design: a chat is session-scoped (process death clears it), and the
 * language picker stays locked mid-conversation for the same reason.
 */
class ChatStore {

    class Conversation internal constructor(seed: ChatMessage) {
        val messages: SnapshotStateList<ChatMessage> = mutableStateListOf(seed)
        var draft by mutableStateOf("")
        var sending by mutableStateOf(false)
        var error by mutableStateOf<String?>(null)
    }

    private val conversations = mutableMapOf<String, Conversation>()

    /** The (lazily created) conversation for [lang]; [seed] authors the native greeting. */
    fun conversation(lang: String, seed: () -> ChatMessage): Conversation =
        conversations.getOrPut(lang) { Conversation(seed()) }
}
