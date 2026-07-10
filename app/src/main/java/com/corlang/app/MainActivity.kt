package com.corlang.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.corlang.app.reminder.ReminderScheduler
import com.corlang.app.ui.AppState
import com.corlang.app.ui.components.LanguageTopBar
import kotlinx.coroutines.flow.first
import com.corlang.app.ui.navigation.Dest
import com.corlang.app.ui.screens.LearnScreen
import com.corlang.app.ui.screens.ProgressScreen
import com.corlang.app.ui.screens.QuizScreen
import com.corlang.app.ui.screens.SettingsScreen
import com.corlang.app.ui.screens.TodayScreen
import com.corlang.app.ui.screens.WordsScreen
import com.corlang.app.ui.theme.CorlangTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CorlangApp).container
        setContent {
            CorlangTheme {
                CorlangApp(container)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CorlangApp(container: AppContainer) {
    val appState: AppState = viewModel(factory = AppState.Factory(container))
    val lang by appState.selected.collectAsState()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: Dest.TODAY.route

    // Settings lives OUTSIDE the nav graph: pushing it onto a tab's back stack gets it
    // saved/restored with the tab (the "stuck in settings" bug). An overlay can't be.
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // Re-anchor the daily reminder to the next 19:00 on every app start — WorkManager's
    // periodic work drifts with Doze deferrals and would otherwise wander off schedule.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (container.languagePrefs.reminderEnabled.first()) {
            val (h, m) = container.languagePrefs.reminderTime.first()
            ReminderScheduler.schedule(context, h, m)
        }
    }

    Scaffold(
        topBar = {
            LanguageTopBar(
                languages = appState.languages,
                selected = lang,
                onSelect = appState::selectLanguage,
                onSettings = { showSettings = true }
            )
        },
        bottomBar = {
            NavigationBar {
                Dest.all.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            showSettings = false
                            navController.navigate(dest.route) {
                                popUpTo(Dest.TODAY.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { androidx.compose.material3.Icon(dest.icon, contentDescription = dest.label) },
                        label = { androidx.compose.material3.Text(dest.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        if (showSettings) {
            androidx.activity.compose.BackHandler { showSettings = false }
            SettingsScreen(
                container,
                onBack = { showSettings = false },
                modifier = Modifier.padding(innerPadding)
            )
            return@Scaffold
        }
        NavHost(
            navController = navController,
            startDestination = Dest.TODAY.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Dest.TODAY.route) {
                TodayScreen(container, lang, onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Dest.TODAY.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                })
            }
            composable(Dest.WORDS.route) { WordsScreen(container, lang) }
            composable(Dest.QUIZ.route) { QuizScreen(container, lang) }
            composable(Dest.LEARN.route) { LearnScreen(container, lang) }
            composable(Dest.PROGRESS.route) { ProgressScreen(container, lang) }
        }
    }
}
