package com.corlang.app.ui.screens

import androidx.compose.runtime.Composable
import com.corlang.app.AppContainer

/**
 * The "Tutor" tab (the LEARN route): the AI Tutor chat, and nothing else for now.
 *
 * The Feynman Teach-back mode that used to share this tab is gone: its screen had no caller
 * for months and its content is no longer shipped, so it was deleted rather than left hidden.
 * The Today-route tab (shown as "Learn") is the learning half of the app; keeping the tutor on
 * its own tab makes that split clear.
 */
@Composable
fun LearnScreen(container: AppContainer, lang: String) {
    TalkScreen(container, lang)
}
