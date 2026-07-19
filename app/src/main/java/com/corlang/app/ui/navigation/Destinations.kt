package com.corlang.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Bottom-nav destinations, ordered by how often you reach for them:
 *   Today (guided daily lesson) · Review (due SRS words) · Learn (AI: Teach + Tutor — Premium,
 *   appears only when unlocked) · Progress (stats, milestones) · Profile (settings, language,
 *   premium, references).
 *
 * Quizzes, exam readiness, and mock exams are NOT tabs: they're end-of-level checkpoints on
 * the journey (Today tab), registered as the argumented routes quiz/{level}, readiness/{level},
 * exam/{level} in the NavHost.
 */
enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Filled.Today),
    WORDS("words", "Review", Icons.Filled.Cached),
    LEARN("learn", "Learn", Icons.Filled.AutoAwesome),
    PROGRESS("progress", "Progress", Icons.Filled.Insights),
    PROFILE("profile", "Profile", Icons.Filled.Person);

    companion object {
        /**
         * The bottom bar. Learn is AI-only and Premium-gated, so it appears ONLY when
         * [premium] is unlocked (Get Premium lives in Profile).
         */
        fun bar(premium: Boolean): List<Dest> =
            if (premium) listOf(TODAY, WORDS, LEARN, PROGRESS, PROFILE)
            else listOf(TODAY, WORDS, PROGRESS, PROFILE)
    }
}
