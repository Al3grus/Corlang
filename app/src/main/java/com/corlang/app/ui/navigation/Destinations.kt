package com.corlang.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Four bottom-nav destinations, ordered do → study → review → track:
 *   Today (the guided daily lesson), Learn (reference: cheatsheet/grammar/teach/tutor),
 *   Review (due spaced-repetition words), Profile (progress, assessments, resources).
 *
 * PRACTICE (quizzes + mock exam) is deliberately NOT a tab — it's reached from Profile, where
 * exam readiness already lives. Its route stays registered so navigation to it still works.
 * Route strings are unchanged so saved back-stack state and navigation code stay valid.
 */
enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Filled.Today),
    LEARN("learn", "Learn", Icons.AutoMirrored.Filled.MenuBook),
    WORDS("words", "Review", Icons.Filled.Cached),
    PROGRESS("progress", "Profile", Icons.Filled.Person),
    PRACTICE("quiz", "Practice", Icons.Filled.Quiz);

    companion object {
        /** Only these four appear in the bottom bar; PRACTICE is reached from Profile. */
        val all = listOf(TODAY, LEARN, WORDS, PROGRESS)
    }
}
