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

    /**
     * A conversation starts EMPTY. The tutor used to open with a seeded greeting, which meant
     * the screen was never blank: opening the Tutor tab dropped a message hard against the top
     * bar before the learner had chosen anything. The greeting still exists, but as a hidden
     * anchor in the request payload (see TalkScreen.send) rather than as a message on screen,
     * so it keeps pinning the language variety without speaking first.
     */
    class Conversation internal constructor() {
        val messages: SnapshotStateList<ChatMessage> = mutableStateListOf()
        var draft by mutableStateOf("")
        var sending by mutableStateOf(false)
        var error by mutableStateOf<String?>(null)
    }

    private val conversations = mutableMapOf<String, Conversation>()

    /** The (lazily created) conversation for [lang]. */
    fun conversation(lang: String): Conversation =
        conversations.getOrPut(lang) { Conversation() }

    /**
     * Throw the transcript away and start fresh. A chat outlives the screen by design, so
     * without this the only way to end one was to kill the app. An in-flight reply is left to
     * land in the discarded conversation and is simply dropped.
     */
    fun reset(lang: String) {
        conversations.remove(lang)
    }
}
