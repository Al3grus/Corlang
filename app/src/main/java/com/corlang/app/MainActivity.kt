package com.corlang.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.consumeWindowInsets
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
import com.corlang.app.ui.screens.OnboardingScreen
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
    val scope = rememberCoroutineScope()

    // Settings lives OUTSIDE the nav graph: pushing it onto a tab's back stack gets it
    // saved/restored with the tab (the "stuck in settings" bug). An overlay can't be.
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Placement is also an overlay (same reasoning): it must not live on a tab's back stack.
    var showPlacement by rememberSaveable { mutableStateOf(false) }
    // Whether a guided lesson is open on the Today tab. Hoisted here so any bottom-nav tap can
    // exit it back to the Today dashboard (lesson progress is saved per step, so it resumes).
    var inLesson by rememberSaveable { mutableStateOf(false) }

    // Point the voice and speech recognizer at the active language (hr/fr).
    LaunchedEffect(lang) {
        container.tts.setLanguage(lang)
        container.speech.setLanguage(lang)
        // Switching language returns to the Today dashboard, not a half-done lesson of the old one.
        inLesson = false
    }

    // Warm every language's heavyweight content (plan + vocab) off the main thread, so the
    // first language switch doesn't parse megabytes of JSON inside composition (visible hitch).
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            container.content.availableLanguages.forEach {
                runCatching { container.content.vocab(it); container.content.plan(it) }
                // Heal any stale currentDay that lags behind completed days (legacy data).
                runCatching { container.progress.reconcileCurrentDay(it) }
            }
        }
    }

    // Keep the process-wide haptic strength in sync with the setting (runs pre-onboarding too).
    LaunchedEffect(Unit) {
        container.languagePrefs.hapticsStrength.collect { v ->
            com.corlang.app.ui.Haptics.strength = runCatching {
                com.corlang.app.ui.Haptics.Strength.valueOf(v)
            }.getOrDefault(com.corlang.app.ui.Haptics.Strength.MEDIUM)
        }
    }

    // First-run onboarding: full-screen before the app, re-runnable from Settings.
    val onboarded by container.languagePrefs.onboardingDone
        .collectAsState(initial = null as Boolean?)
    var showOnboarding by rememberSaveable { mutableStateOf(false) }
    if (onboarded == null) return   // one-frame gap while the flag loads; splash just covered it
    if (onboarded == false || showOnboarding) {
        // Re-running from Settings ("Edit profile") is cancellable with system back; the true
        // first run isn't (there is no app behind it yet to fall back to).
        if (onboarded == true) {
            androidx.activity.compose.BackHandler { showOnboarding = false }
        }
        OnboardingScreen(container, onFinish = { wantsPlacement ->
            showOnboarding = false
            if (wantsPlacement) showPlacement = true
        })
        return
    }

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
    // Sideload flavor only — the Play flavor must never self-update (Play policy).
    if (BuildConfig.ENABLE_UPDATER) {
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
    }

    // One-time "new language" placement prompt: switching to a language the learner has never
    // touched offers the placement test — profile is already known, so no full re-onboarding.
    val handledLangs by container.languagePrefs.placementHandledLanguages
        .collectAsState(initial = null as Set<String>?)
    var newLangPrompt by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lang, handledLangs) {
        val handled = handledLangs ?: return@LaunchedEffect
        if (lang in handled) { newLangPrompt = null; return@LaunchedEffect }
        val completions = container.progress.completedDays(lang).first()
        val revs = container.words.reviews(lang).first()
        val prog = container.progress.progress(lang).first()
        val touched = completions.isNotEmpty() || revs.isNotEmpty() || (prog?.currentDay ?: 1) > 1
        if (touched) {
            // Existing progress in this language → they've clearly used it; never nag.
            container.languagePrefs.markPlacementHandled(lang)
        } else {
            newLangPrompt = lang
        }
    }
    newLangPrompt?.let { pl ->
        val meta = appState.languages.firstOrNull { it.code == pl }
        val name = meta?.name ?: "this language"
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Start $name") },
            text = {
                Text(
                    "Take a quick placement test so $name starts at the right level? It's about " +
                        "two minutes. Your profile carries over — no need to set anything up again."
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch { container.languagePrefs.markPlacementHandled(pl) }
                    newLangPrompt = null
                    showPlacement = true
                }) { Text("Take placement test") }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    scope.launch { container.languagePrefs.markPlacementHandled(pl) }
                    newLangPrompt = null
                }) { Text("Start at Day 1") }
            }
        )
    }

    Scaffold(
        topBar = {
            LanguageTopBar(
                languages = appState.languages,
                selected = lang,
                onSelect = appState::selectLanguage,
                // Locked while the learner is mid-anything (lesson, review, quiz, exam,
                // placement, teach-back, tutor chat) — see Engagement.
                pickerEnabled = !com.corlang.app.ui.Engagement.engaged
            )
        },
        bottomBar = {
            NavigationBar {
                Dest.all.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            // Overlays must close when a tab is chosen, or the nav highlight
                            // moves while the overlay stays on screen (looks frozen).
                            showSettings = false
                            showPlacement = false
                            // Any tab tap (including Today itself) exits an open lesson back to the
                            // dashboard — the same as "Exit (saved)". Progress is saved per step.
                            inLesson = false
                            // A long example sentence must not keep talking over the next tab.
                            container.tts.stop()
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
            androidx.compose.foundation.layout.Box(
                Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
            ) {
                PlacementScreen(container, lang, onDone = { showPlacement = false })
            }
            return@Scaffold
        }
        if (showSettings) {
            androidx.activity.compose.BackHandler { showSettings = false }
            SettingsScreen(
                container,
                onBack = { showSettings = false },
                onEditProfile = { showSettings = false; showOnboarding = true },
                modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
            )
            return@Scaffold
        }
        NavHost(
            navController = navController,
            startDestination = Dest.TODAY.route,
            // consumeWindowInsets is the missing half of edge-to-edge keyboard handling:
            // padding(innerPadding) already spends the bottom-bar + system-bar insets, and
            // without marking them consumed every imePadding() below ALSO added them —
            // composers floated a bottom-bar-height (or more) above the keyboard.
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            // Each screen crossfades on a language switch, so content doesn't hard-cut/flicker as
            // the new language's progress, journey position and lists load in.
            composable(Dest.TODAY.route) {
                Crossfade(targetState = lang, animationSpec = tween(durationMillis = 1300, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "lang-today") { l ->
                    TodayScreen(
                        container, l,
                        inLesson = inLesson,
                        onInLessonChange = { inLesson = it },
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(Dest.TODAY.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
            composable(Dest.WORDS.route) {
                Crossfade(targetState = lang, animationSpec = tween(durationMillis = 1300, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "lang-review") { l ->
                    WordsScreen(container, l)
                }
            }
            composable(Dest.PRACTICE.route) {
                Crossfade(targetState = lang, animationSpec = tween(durationMillis = 1300, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "lang-practice") { l ->
                    QuizScreen(container, l)
                }
            }
            composable(Dest.LEARN.route) {
                Crossfade(targetState = lang, animationSpec = tween(durationMillis = 1300, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "lang-learn") { l ->
                    LearnScreen(container, l)
                }
            }
            composable(Dest.PROGRESS.route) {
                Crossfade(targetState = lang, animationSpec = tween(durationMillis = 1300, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "lang-profile") { l ->
                    ProgressScreen(container, l,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(Dest.TODAY.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenSettings = { showSettings = true })
                }
            }
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
