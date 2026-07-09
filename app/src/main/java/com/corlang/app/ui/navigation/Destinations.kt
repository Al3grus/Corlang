package com.corlang.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.vector.ImageVector

/** The five bottom-nav destinations. */
enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Filled.Today),
    CHEATSHEET("cheatsheet", "Cheatsheet", Icons.AutoMirrored.Filled.MenuBook),
    QUIZ("quiz", "Quiz", Icons.Filled.Quiz),
    TEACH("teach", "Teach", Icons.Filled.Psychology),
    PROGRESS("progress", "Progress", Icons.Filled.Insights);

    companion object {
        val all = entries
    }
}
