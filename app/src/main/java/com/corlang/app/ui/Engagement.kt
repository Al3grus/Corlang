package com.corlang.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf

/**
 * App-wide "the learner is mid-something" signal: a lesson step, a word-review card, a quiz,
 * an exam section, a placement test, a teach-back, a tutor chat. While engaged, the top-bar
 * language picker locks (it still shows the current language) — switching languages would
 * tear the session's state down and lose partial work.
 *
 * Counter-based so nested sessions (a word block inside a lesson) stay balanced: each [Report]
 * pairs its increment with a guaranteed dispose-time decrement, so the flag can never stick.
 */
object Engagement {
    private val count = mutableIntStateOf(0)

    /** True while any session composable is on screen. Reading it in composition recomposes. */
    val engaged: Boolean get() = count.intValue > 0

    /** Call at the root of a session composable; registers for exactly its composed lifetime. */
    @Composable
    fun Report() {
        DisposableEffect(Unit) {
            count.intValue++
            onDispose { count.intValue-- }
        }
    }
}
