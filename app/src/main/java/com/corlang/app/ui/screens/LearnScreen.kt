package com.corlang.app.ui.screens

import androidx.compose.runtime.Composable
import com.corlang.app.AppContainer

/**
 * The "Tutor" tab (the LEARN route): the AI Tutor chat, and nothing else for now.
 *
 * The Feynman Teach-back mode is hidden — [TeachScreen] is kept in the codebase but no longer
 * surfaced, so this tab is a single focused mode. The Today-route tab (shown as "Learn") is the
 * learning half of the app; keeping the tutor on its own tab makes that split clear. If Teach
 * returns, restore the segmented Teach/Tutor switch here.
 */
@Composable
fun LearnScreen(container: AppContainer, lang: String) {
    TalkScreen(container, lang)
}
