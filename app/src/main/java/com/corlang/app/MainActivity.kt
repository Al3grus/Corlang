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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.corlang.app.ui.AppState
import com.corlang.app.ui.components.LanguageTopBar
import com.corlang.app.ui.navigation.Dest
import com.corlang.app.ui.screens.CheatsheetScreen
import com.corlang.app.ui.screens.ProgressScreen
import com.corlang.app.ui.screens.QuizScreen
import com.corlang.app.ui.screens.TeachScreen
import com.corlang.app.ui.screens.TodayScreen
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

    Scaffold(
        topBar = {
            LanguageTopBar(
                languages = appState.languages,
                selected = lang,
                onSelect = appState::selectLanguage
            )
        },
        bottomBar = {
            NavigationBar {
                Dest.all.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
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
        NavHost(
            navController = navController,
            startDestination = Dest.TODAY.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Dest.TODAY.route) { TodayScreen(container, lang) }
            composable(Dest.CHEATSHEET.route) { CheatsheetScreen(container, lang) }
            composable(Dest.QUIZ.route) { QuizScreen(container, lang) }
            composable(Dest.TEACH.route) { TeachScreen(container, lang) }
            composable(Dest.PROGRESS.route) { ProgressScreen(container, lang) }
        }
    }
}
