package com.corlang.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Style
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The five bottom-nav destinations. "Lesson" is the guided day-by-day course (opens on the
 * day after your last completed one); "Learn" is the reference library (cheatsheet + grammar
 * + teach-back). Route strings stay stable so navigation code is untouched.
 */
enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Lesson", Icons.Filled.School),
    WORDS("words", "Words", Icons.Filled.Style),
    QUIZ("quiz", "Quiz", Icons.Filled.Quiz),
    LEARN("learn", "Learn", Icons.AutoMirrored.Filled.MenuBook),
    PROGRESS("progress", "Progress", Icons.Filled.Insights);

    companion object {
        val all = entries
    }
}
