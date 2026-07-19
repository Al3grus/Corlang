package com.corlang.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.corlang.app.reminder.ReminderScheduler
import com.corlang.app.ui.AppState
import com.corlang.app.ui.components.CorlangTopBar
import com.corlang.app.update.ReleaseInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.corlang.app.ui.navigation.Dest
import com.corlang.app.ui.screens.CorlangSplash
import com.corlang.app.ui.screens.ExamScreen
import com.corlang.app.ui.screens.LevelQuizScreen
import com.corlang.app.ui.screens.PaywallScreen
import com.corlang.app.ui.screens.LearnScreen
import com.corlang.app.ui.screens.OnboardingScreen
import com.corlang.app.ui.screens.PlacementScreen
import com.corlang.app.ui.screens.ProfileScreen
import com.corlang.app.ui.screens.ProgressScreen
import com.corlang.app.ui.screens.ReadinessScreen
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
                // Plain remember, NOT rememberSaveable: after process death the content caches
                // are cold again, and skipping the splash meant the first composition parsed
                // the full plan synchronously on the main thread (visible freeze). Within a
                // live process (config change) the caches are warm and the splash is instant.
                var ready by remember { mutableStateOf(false) }
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
    // Load-then-show at the root: lang is null until DataStore emits (see AppState.selected),
    // and premium uses a null initial for the same reason — with `initial = false` a premium
    // user's bottom bar rendered 4 tabs for a frame before Learn popped in. Both emit within
    // ~a frame and the splash is still on screen, so the gate is invisible.
    val langOrNull by appState.selected.collectAsState()
    val premiumOrNull by container.premium.entitled.collectAsState(initial = null)
    val lang = langOrNull ?: return
    val premium = premiumOrNull ?: return
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: Dest.TODAY.route
    val scope = rememberCoroutineScope()

    // Settings lives OUTSIDE the nav graph: pushing it onto a tab's back stack gets it
    // saved/restored with the tab (the "stuck in settings" bug). An overlay can't be.
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Placement is also an overlay (same reasoning): it must not live on a tab's back stack.
    var showPlacement by rememberSaveable { mutableStateOf(false) }
    // Paywall overlay: open flag + mode. paywallLevel null = Premium subscription; else the CEFR
    // level id ("A2"/"B1"/"B2") being unlocked. Overlay (not a nav dest) for the same reason.
    var showPaywall by rememberSaveable { mutableStateOf(false) }
    var paywallLevel by rememberSaveable { mutableStateOf<String?>(null) }
    // Whether a guided lesson is open on the Today tab. Hoisted here so any bottom-nav tap can
    // exit it back to the Today dashboard (lesson progress is saved per step, so it resumes).
    var inLesson by rememberSaveable { mutableStateOf(false) }
    // Bumped on every Profile tab tap. ProfileScreen watches it to close any open sub-page:
    // when the tab is ALREADY selected, navigate() is a no-op and nothing else would reset it.
    var profileTabTick by remember { mutableStateOf(0) }

    // Point the voice and speech recognizer at the active language (hr/fr).
    // prevLang distinguishes a real language SWITCH from the first composition after process
    // death / recreation: this effect always runs once on entry, and unconditionally clearing
    // inLesson there destroyed the restored mid-lesson state that rememberSaveable had just
    // brought back.
    var prevLang by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(lang) {
        container.tts.setLanguage(lang)
        container.speech.setLanguage(lang)
        // Switching language returns to the Today dashboard, not a half-done lesson of the old one.
        if (prevLang != null && prevLang != lang) inLesson = false
        prevLang = lang
    }

    // Play Billing: connect + reconcile entitlement on every resume. start() is idempotent and
    // re-queries purchases, so a subscription bought/refunded (or a purchase completed while the
    // app was backgrounded on the Play sheet) is reflected the moment the user returns.
    val billingOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(billingOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) container.billing.start()
        }
        billingOwner.lifecycle.addObserver(obs)
        onDispose { billingOwner.lifecycle.removeObserver(obs) }
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
        OnboardingScreen(
            container,
            // Re-running from Settings edits the profile: intro pages skipped, the progress
            // bar counts only the profile steps. Only the true first run gets the full intro.
            editProfile = onboarded == true,
            onFinish = { wantsPlacement ->
                showOnboarding = false
                if (wantsPlacement) showPlacement = true
            }
        )
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
        // Dismissing (tap outside / system back) also marks the language handled: an accidental
        // switch must not re-arm this dialog on every app open until a choice is made. The
        // learner just starts at day 1 by default and can retake placement from Settings.
        val dismiss: () -> Unit = {
            scope.launch { container.languagePrefs.markPlacementHandled(pl) }
            newLangPrompt = null
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text("Start $name") },
            text = {
                // The actions live in the body so they can be full-width and centred instead
                // of huddling in the dialog's bottom-right corner.
                androidx.compose.foundation.layout.Column {
                    Text(
                        "Take a quick placement test so $name starts at the right level? It's " +
                            "about two minutes. Your profile carries over, no need to set " +
                            "anything up again."
                    )
                    Button(
                        onClick = {
                            scope.launch { container.languagePrefs.markPlacementHandled(pl) }
                            newLangPrompt = null
                            showPlacement = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp)
                    ) { Text("Take placement test") }
                    OutlinedButton(
                        onClick = {
                            scope.launch { container.languagePrefs.markPlacementHandled(pl) }
                            newLangPrompt = null
                            // Lesson 1 means the lesson itself, so land the learner on it, not back on
                            // the Profile page they switched languages from.
                            navController.navigate(Dest.TODAY.route) {
                                popUpTo(Dest.TODAY.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) { Text("Start at Lesson 1") }
                }
            },
            confirmButton = {}
        )
    }

    Scaffold(
        topBar = { CorlangTopBar() },
        bottomBar = {
            // The placement test owns the screen while it runs: it's a short, ordered flow with
            // its own exit, and leaving the tabs tappable mid-test silently abandoned the test.
            if (!showPlacement) NavigationBar {
                Dest.bar(premium).forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            // EVERY overlay must close when a tab is chosen, or the nav highlight
                            // moves while the overlay stays on screen (looks frozen). The paywall
                            // was missing from this list: Get Premium then any tab tap navigated
                            // underneath while the opaque paywall stayed up.
                            showSettings = false
                            showPlacement = false
                            showPaywall = false
                            if (dest.route == Dest.PROFILE.route) profileTabTick++
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
        // One snappy fade for every tab switch. Kept short on purpose: a long crossfade keeps the
        // OUTGOING tab painted while the incoming one is still populating its flows, so you'd see
        // e.g. Today linger for a beat before Review resolves. 150ms is long enough to read as a
        // soft fade (not a hard cut) but short enough that the old tab never lingers. Uniform
        // across all destinations. Pairs with each screen's own load-gate so the incoming tab
        // fades in already-populated rather than mid-load.
        val tabFade = tween<Float>(durationMillis = 150)
        // The NavHost is the BASE layer and always composes; overlays draw on top of it.
        // It must never be skipped: NavHost is the only thing that calls setGraph(), so an
        // early-return overlay (placement opening straight out of onboarding) left the
        // controller graph-less and the next bottom-bar tap threw
        // "Cannot navigate to <route>. Navigation graph has not been set".
        //
        // consumeWindowInsets is the missing half of edge-to-edge keyboard handling:
        // padding(innerPadding) already spends the bottom-bar + system-bar insets, and
        // without marking them consumed every imePadding() below ALSO added them,
        // composers floated a bottom-bar-height (or more) above the keyboard. Spent once
        // here on the Box so every layer inherits it.
        Box(
            Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
        NavHost(
            navController = navController,
            startDestination = Dest.TODAY.route,
            enterTransition = { fadeIn(tabFade) },
            exitTransition = { fadeOut(tabFade) },
            popEnterTransition = { fadeIn(tabFade) },
            popExitTransition = { fadeOut(tabFade) }
        ) {
            // Tab switches share ONE uniform fade (below), so every tab — Review included —
            // animates identically. The old per-screen Crossfade(lang) wrappers are gone: they
            // fired a slow 1300ms fade on every language change and animated inconsistently
            // across tabs (Review reveals its async-loaded queue, so a content-crossfade there
            // showed a loading frame). Language now switches only from Profile, so a fade on
            // `lang` isn't needed at all.
            composable(Dest.TODAY.route) {
                TodayScreen(
                    container, lang,
                    inLesson = inLesson,
                    onInLessonChange = { inLesson = it },
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Dest.TODAY.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenPaywall = { level -> paywallLevel = level; showPaywall = true }
                )
            }
            composable(Dest.WORDS.route) { WordsScreen(container, lang) }
            composable(Dest.LEARN.route) { LearnScreen(container, lang) }
            composable(Dest.PROGRESS.route) { ProgressScreen(container, lang) }
            // End-of-level checkpoints, opened from the journey (Today tab). Argumented routes,
            // not tabs: each exits by popping back to wherever the journey was.
            composable("quiz/{level}") { entry ->
                LevelQuizScreen(
                    container, lang,
                    levelId = entry.arguments?.getString("level") ?: "",
                    onExit = { navController.popBackStack() }
                )
            }
            composable("readiness/{level}") { entry ->
                ReadinessScreen(
                    container, lang,
                    levelId = entry.arguments?.getString("level") ?: "",
                    onExit = { navController.popBackStack() }
                )
            }
            composable("exam/{level}") { entry ->
                ExamScreen(
                    container, lang,
                    levelId = entry.arguments?.getString("level") ?: "",
                    onExit = { navController.popBackStack() }
                )
            }
            composable(Dest.PROFILE.route) {
                ProfileScreen(
                    container, lang,
                    resetTick = profileTabTick,
                    onSelectLanguage = appState::selectLanguage,
                    onOpenSettings = { showSettings = true },
                    onGetPremium = { paywallLevel = null; showPaywall = true }
                )
            }
        }

        // Overlays: opaque, full-bleed, drawn over the (still-composed) NavHost. Each is
        // mutually exclusive and dismissed with system back. Surface supplies the opaque
        // background, without it the tab underneath shows through.
        when {
            showPlacement -> {
                androidx.activity.compose.BackHandler { showPlacement = false }
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    PlacementScreen(container, lang, onDone = { showPlacement = false })
                }
            }
            showSettings -> {
                androidx.activity.compose.BackHandler { showSettings = false }
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    SettingsScreen(
                        container,
                        onBack = { showSettings = false },
                        onEditProfile = { showSettings = false; showOnboarding = true },
                        onProgressReset = { landOn ->
                            // Land on the Today dashboard of the chosen course (the one with the
                            // most remaining progress, or the freshly reset course itself).
                            appState.selectLanguage(landOn)
                            showSettings = false
                            inLesson = false
                            navController.navigate(Dest.TODAY.route) {
                                popUpTo(Dest.TODAY.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
            showPaywall -> {
                androidx.activity.compose.BackHandler { showPaywall = false }
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    PaywallScreen(container, levelId = paywallLevel, onClose = { showPaywall = false })
                }
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
