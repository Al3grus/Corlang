package com.corlang.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.corlang.app.update.ReleaseInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.corlang.app.ui.navigation.Dest
import com.corlang.app.ui.screens.CorlangSplash
import com.corlang.app.ui.screens.LearnScreen
import com.corlang.app.ui.screens.PlacementScreen
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
                // Branded loader while content preloads; reveals the app when it hits 100%.
                var ready by rememberSaveable { mutableStateOf(false) }
                if (ready) {
                    CorlangApp(container)
                } else {
                    CorlangSplash(container, onReady = { ready = true })
                }
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
    // Placement is also an overlay (same reasoning): it must not live on a tab's back stack.
    var showPlacement by rememberSaveable { mutableStateOf(false) }

    // Re-anchor the daily reminder to the next 19:00 on every app start, WorkManager's
    // periodic work drifts with Doze deferrals and would otherwise wander off schedule.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (container.languagePrefs.reminderEnabled.first()) {
            val (h, m) = container.languagePrefs.reminderTime.first()
            ReminderScheduler.schedule(context, h, m)
        }
    }

    // Silent update check on launch; shows a dialog only if a newer build exists.
    var pendingUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }
    LaunchedEffect(Unit) {
        container.updater.fetchLatest()?.let { info ->
            if (container.updater.isNewer(info)) pendingUpdate = info
        }
    }
    pendingUpdate?.let { info ->
        UpdateDialog(
            container = container,
            info = info,
            onDismiss = { pendingUpdate = null }
        )
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
        if (showPlacement) {
            androidx.activity.compose.BackHandler { showPlacement = false }
            Modifier.padding(innerPadding).let { pad ->
                androidx.compose.foundation.layout.Box(pad) {
                    PlacementScreen(container, lang, onDone = { showPlacement = false })
                }
            }
            return@Scaffold
        }
        if (showSettings) {
            androidx.activity.compose.BackHandler { showSettings = false }
            SettingsScreen(
                container,
                onBack = { showSettings = false },
                onPlacement = { showSettings = false; showPlacement = true },
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
                }, onPlacement = { showPlacement = true })
            }
            composable(Dest.WORDS.route) { WordsScreen(container, lang) }
            composable(Dest.QUIZ.route) { QuizScreen(container, lang) }
            composable(Dest.LEARN.route) { LearnScreen(container, lang) }
            composable(Dest.PROGRESS.route) { ProgressScreen(container, lang) }
        }
    }
}

/** Prompts to install a newer build: one tap downloads the APK and opens the installer. */
@Composable
private fun UpdateDialog(container: AppContainer, info: ReleaseInfo, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var percent by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("Update available") },
        text = {
            Text(
                buildString {
                    append("Version ${info.versionName} is ready.")
                    if (info.notes.isNotBlank()) append("\n\n${info.notes}")
                    if (downloading) append("\n\nDownloading… $percent%")
                    error?.let { append("\n\n$it") }
                }
            )
        },
        confirmButton = {
            Button(
                enabled = !downloading,
                onClick = {
                    downloading = true; error = null
                    scope.launch {
                        val apk = container.updater.downloadApk(info) { percent = it }
                        if (apk != null) {
                            container.updater.installApk(apk)
                            downloading = false
                            onDismiss()
                        } else {
                            downloading = false
                            error = "Download failed, check your connection and try again."
                        }
                    }
                }
            ) { Text(if (downloading) "Downloading…" else "Update now") }
        },
        dismissButton = {
            OutlinedButton(enabled = !downloading, onClick = onDismiss) { Text("Later") }
        }
    )
}
