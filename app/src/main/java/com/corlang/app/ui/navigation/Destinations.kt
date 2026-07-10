package com.corlang.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.vector.ImageVector

/** The five bottom-nav destinations. Learn hosts both the cheatsheet and teach-back. */
enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Filled.Today),
    WORDS("words", "Words", Icons.Filled.Style),
    QUIZ("quiz", "Quiz", Icons.Filled.Quiz),
    LEARN("learn", "Learn", Icons.AutoMirrored.Filled.MenuBook),
    PROGRESS("progress", "Progress", Icons.Filled.Insights);

    companion object {
        val all = entries
    }
}
