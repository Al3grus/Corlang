package com.corlang.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Bottom-nav destinations, ordered by how often you reach for them:
 *   Today (guided daily lesson) · Review (due SRS words) · Learn (AI: Teach + Tutor — Premium,
 *   appears only when unlocked) · Progress (stats, assessment) · Profile (settings, language,
 *   premium, references).
 *
 * PRACTICE (quizzes + mock exam) is NOT a tab — reached from Progress for now; the route stays
 * registered so navigation still works. (Exams are slated to become end-of-level lessons in the
 * Today flow — see docs/monetization-roadmap.md — at which point Practice retires.)
 * Route strings are unchanged so saved back-stack state stays valid.
 */
enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Filled.Today),
    WORDS("words", "Review", Icons.Filled.Cached),
    LEARN("learn", "Learn", Icons.Filled.AutoAwesome),
    PROGRESS("progress", "Progress", Icons.Filled.Insights),
    PROFILE("profile", "Profile", Icons.Filled.Person),
    PRACTICE("quiz", "Practice", Icons.Filled.Quiz);

    companion object {
        /**
         * The bottom bar. Learn is AI-only and Premium-gated, so it appears ONLY when
         * [premium] is unlocked (Get Premium lives in Profile). PRACTICE is never in the bar.
         */
        fun bar(premium: Boolean): List<Dest> =
            if (premium) listOf(TODAY, WORDS, LEARN, PROGRESS, PROFILE)
            else listOf(TODAY, WORDS, PROGRESS, PROFILE)
    }
}
