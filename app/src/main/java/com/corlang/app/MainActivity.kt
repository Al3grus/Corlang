package com.corlang.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.runtime.withFrameNanos
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
import com.corlang.app.data.prefs.LanguagePrefs
import com.corlang.app.data.prefs.ThemeMode
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
import com.corlang.app.ui.screens.ThemeChoiceScreen
import com.corlang.app.ui.screens.ProfileScreen
import com.corlang.app.ui.screens.ProgressScreen
import com.corlang.app.ui.screens.ReadinessScreen
import com.corlang.app.ui.screens.SettingsScreen
import com.corlang.app.ui.screens.TodayScreen
import com.corlang.app.ui.screens.WordsScreen
import com.corlang.app.ui.theme.CorlangThemeSwap
import androidx.compose.ui.draw.alpha

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate, because this decides the WINDOW BACKGROUND — the very first
        // frame, painted before Compose runs. Read synchronously from the SharedPreferences
        // mirror (DataStore cannot answer in time); a light-mode learner otherwise gets a flash
        // of ink at every launch.
        val launchDark = LanguagePrefs.launchThemeIsDark(this)
        setTheme(if (launchDark) R.style.Theme_Corlang else R.style.Theme_Corlang_Light)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CorlangApp).container
        setContent {
            // The theme choice, hoisted to the root so the first-run picker can re-theme the
            // ENTIRE app live. `preview` is the picker's in-flight tap (nothing saved yet) and
            // wins while it is set; otherwise the saved mode rules, and `launchDark` covers the
            // frames before DataStore has emitted — the same value the window is already painted.
            val themeMode by container.languagePrefs.themeMode
                .collectAsState(initial = null as ThemeMode?)
            var preview by remember { mutableStateOf<Boolean?>(null) }
            val scope = rememberCoroutineScope()
            val dark = preview ?: when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                else -> launchDark
            }

            // Not CorlangTheme directly: the swap wrapper is what turns a change of `dark` into
            // one clean fade instead of a visible repaint (see ThemeSwap.kt).
            CorlangThemeSwap(dark = dark) {
                // Branded loader while content preloads; reveals the app when it hits 100%.
                // Plain remember, NOT rememberSaveable: after process death the content caches
                // are cold again, and skipping the splash meant the first composition parsed
                // the full plan synchronously on the main thread (visible freeze). Within a
                // live process (config change) the caches are warm and the splash is instant.
                var ready by remember { mutableStateOf(false) }
                when {
                    !ready -> CorlangSplash(container, onReady = { ready = true })
                    // First run: pick a look before onboarding starts. UNSET is the "never
                    // asked" state; a null themeMode is merely DataStore still loading, so it
                    // must NOT open the picker (that would flash it for existing learners).
                    themeMode == ThemeMode.UNSET -> ThemeChoiceScreen(
                        dark = dark,
                        onPreview = { preview = it },
                        onConfirm = {
                            // Persisting flips themeMode away from UNSET, which is what hands
                            // over to onboarding. preview is cleared so the saved value rules.
                            scope.launch {
                                container.languagePrefs.setThemeMode(dark)
                                preview = null
                            }
                        }
                    )
                    else -> CorlangApp(container)
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

    // The streak chip lives on the app bar, so its number is computed ONCE here and every tab
    // shows the same figure. Settled to right-now (a lapse the bank cannot cover reads 0), because
    // the stored streak only self-heals on the next completion.
    val streakRow by container.progress.progress(lang).collectAsState(initial = null)
    var showStreakSheet by rememberSaveable { mutableStateOf(false) }
    val streakToday = com.corlang.app.data.WordsRepository.todayEpochDay()
    // -1 while progress is still loading: the chip hides rather than flashing a placeholder 0.
    val chipStreak = streakRow?.let {
        com.corlang.app.data.ProgressRepository.displayStreak(
            streak = it.streak,
            lastStudiedEpochDay = it.lastStudiedEpochDay,
            freezes = it.streakFreezes,
            today = streakToday
        )
    } ?: -1
    val chipLit = streakRow?.lastStudiedEpochDay == streakToday

    // Settings lives OUTSIDE the nav graph: pushing it onto a tab's back stack gets it
    // saved/restored with the tab (the "stuck in settings" bug). An overlay can't be.
    var showSettings by rememberSaveable { mutableStateOf(false) }
    /**
     * Whether the NEXT navigation skips the tab crossfade. Every navigation sets it, rather than
     * one setting it and something else resetting it: a reset would have to land between the tap
     * and the moment AnimatedContent latches its transition spec, and that ordering is not ours
     * to depend on.
     *
     * True for exactly one case: the navigation that happens as an overlay closes.
     *
     * Field report: tapping Review from inside Settings showed Settings, then the Profile tab
     * for a moment, then Review. Closing the overlay and navigating are one recomposition, so the
     * frame after the tap has the overlay gone and the base layer still on the route you came
     * FROM, and the 150ms crossfade then plays that old tab out in full. Fading the overlay
     * instead would not have helped: the wrong tab is underneath either way.
     *
     * So the switch underneath the overlay is instant, and the overlay coming away IS the
     * transition. Deliberately not saveable: it describes one navigation, not a screen state.
     */
    var skipTabAnim by remember { mutableStateOf(false) }
    /**
     * The route an overlay is handing off to, or null. The overlay STAYS UP until the NavHost is
     * actually showing it.
     *
     * Skipping the crossfade cut the flash from 150ms to a frame, and a frame was still visible
     * from Settings (the heaviest overlay to tear down, and the one still reported). Closing the
     * overlay in the same recomposition as the navigate always leaves that gap: the overlay is
     * gone as soon as the flag flips, and the NavHost cannot have drawn the destination yet.
     * So the overlay is not dismissed by the tap at all. It stays up for the frames the NavHost
     * needs to put the destination underneath it, and only then comes away.
     */
    var overlayHandoffTo by remember { mutableStateOf<String?>(null) }
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

    // Re-arm the daily alarm on every app start. Alarms do not survive a force-stop (and the
    // boot receiver only covers reboots), so opening the app is the other moment we know the
    // schedule is intact. It also hands back a nudge the phone slept through, via the catch-up.
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
    // The last language the learner was actually settled on (onboarded, or already-used). If the
    // new-language prompt is dismissed by accident, we revert to this instead of stranding them
    // in the just-picked language at day 1.
    var lastSettledLang by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lang, handledLangs, showPlacement) {
        val handled = handledLangs ?: return@LaunchedEffect
        // Never stack the offer under the test itself: the flag is now written on completion,
        // so while the test is open this language is legitimately still "unhandled".
        if (showPlacement) return@LaunchedEffect
        if (lang in handled) { lastSettledLang = lang; newLangPrompt = null; return@LaunchedEffect }
        val completions = container.progress.completedDays(lang).first()
        val revs = container.words.reviews(lang).first()
        val prog = container.progress.progress(lang).first()
        val touched = completions.isNotEmpty() || revs.isNotEmpty() || (prog?.currentDay ?: 1) > 1
        if (touched) {
            // Existing progress in this language → they've clearly used it; never nag.
            container.languagePrefs.markPlacementHandled(lang)
            lastSettledLang = lang
        } else {
            newLangPrompt = lang
        }
    }
    newLangPrompt?.let { pl ->
        val meta = appState.languages.firstOrNull { it.code == pl }
        val name = meta?.name ?: "this language"
        // Dismissing (tap outside / system back) is treated as "I didn't mean to switch": revert
        // to the language the learner was on before, and do NOT mark placement handled, so a
        // later, deliberate switch to this language still offers the test. This stops an accidental
        // tap from stranding them in a new language at day 1 with placement locked out.
        val dismiss: () -> Unit = {
            val prev = lastSettledLang
            newLangPrompt = null
            if (prev != null && prev != pl) {
                scope.launch { container.languagePrefs.setLanguage(prev) }
            }
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text("Start $name") },
            text = {
                // The actions live in the body so they can be full-width and centred instead
                // of huddling in the dialog's bottom-right corner.
                androidx.compose.foundation.layout.Column {
                    Text(
                        "Take a quick placement test so $name starts at the right level? " +
                            "It will take about two minutes."
                    )
                    Button(
                        onClick = {
                            // Deliberately NOT marked handled here: opening the test settles
                            // nothing. PlacementScreen marks it once a placement commits, so a
                            // process death mid-test leaves the offer standing instead of
                            // silently retiring it and stranding the learner on lesson 1.
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
                            skipTabAnim = false
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

    // The whole freeze system explains itself here and nowhere else. Completions are collected
    // only while the sheet is up, so the week strip costs nothing on the other 99% of frames.
    if (showStreakSheet) {
        val completions by container.progress.completions(lang).collectAsState(initial = emptyList())
        val zone = java.time.ZoneId.systemDefault()
        val studiedDays = remember(completions) {
            completions.map {
                java.time.Instant.ofEpochMilli(it.completedAtEpoch).atZone(zone).toLocalDate()
            }.toSet()
        }
        com.corlang.app.ui.components.StreakSheet(
            streak = maxOf(chipStreak, 0),
            freezes = streakRow?.let {
                com.corlang.app.data.ProgressRepository.displayFreezes(
                    streak = it.streak,
                    lastStudiedEpochDay = it.lastStudiedEpochDay,
                    freezes = it.streakFreezes,
                    today = streakToday
                )
            } ?: 0,
            longestStreak = streakRow?.longestStreak ?: 0,
            studiedDays = studiedDays,
            today = java.time.LocalDate.now(),
            onDismiss = { showStreakSheet = false }
        )
    }

    Scaffold(
        topBar = {
            CorlangTopBar(
                streak = chipStreak,
                streakLit = chipLit,
                onStreakClick = { showStreakSheet = true }
            )
        },
        bottomBar = {
            // The placement test owns the screen while it runs: it's a short, ordered flow with
            // its own exit, and leaving the tabs tappable mid-test silently abandoned the test.
            // Same ground as the top bar. Material's default for a NavigationBar is
            // surfaceContainer, one step off the `surface` CorlangTopBar uses. On the dark theme
            // that gap is 1.07:1 and the two bars already read as one; on the light theme it is
            // #FFFBF3 against #F2EADC, two different beiges bracketing the page.
            if (!showPlacement) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                /*
                 * LIGHT ONLY, and only the selected pill.
                 *
                 * Material's default indicator is secondaryContainer, which on the light theme
                 * is a warm taupe sitting on a beige bar: 1.12:1, near enough invisible, so
                 * which tab you were on had to be read from the icon weight alone. The dark
                 * theme's pill is barely higher at 1.36:1 but a rust on near-black reads by hue
                 * where two beiges do not, and it looks right as it is, so it keeps Material's
                 * defaults untouched.
                 *
                 * Walnut takes the light pill to 7.29:1 against the bar, with a white icon on it
                 * at 7.53 and the label matching the pill at 7.29.
                 */
                val navColors = if (com.corlang.app.ui.theme.CorlangColors.isDark) {
                    NavigationBarItemDefaults.colors()
                } else {
                    NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                    )
                }
                Dest.bar(premium).forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            // EVERY overlay must close when a tab is chosen, or the nav highlight
                            // moves while the overlay stays on screen (looks frozen). The paywall
                            // was missing from this list: Get Premium then any tab tap navigated
                            // underneath while the opaque paywall stayed up.
                            // Only a handoff FROM an overlay skips the fade; an ordinary
                            // tab-to-tab tap keeps it.
                            val overlayUp = showSettings || showPlacement || showPaywall
                            skipTabAnim = overlayUp
                            if (overlayUp && currentRoute != dest.route) {
                                // Keep it on screen; it comes down on arrival, below.
                                overlayHandoffTo = dest.route
                            } else {
                                showSettings = false
                                showPlacement = false
                                showPaywall = false
                            }
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
                        label = { androidx.compose.material3.Text(dest.label) },
                        colors = navColors
                    )
                }
            }
        }
    ) { innerPadding ->
        // One fade-through for every tab switch. The bars never move - the top bar and the
        // bottom nav are identical on every tab - so the whole change is carried by the page
        // between them leaving and the next one arriving.
        //
        // Sequential, not a crossfade: the outgoing tab is gone (90ms) before the incoming one
        // starts (210ms). The old 150ms simultaneous crossfade had to stay that short precisely
        // because both tabs were painted at once - long enough to see was long enough to watch
        // Today linger over a Review tab still resolving its flows - and 150ms with the outgoing
        // tab up for all of it reads as a cut, which is what it looked like.
        //
        // Pairs with each screen's load gate, which now fades its content in on arrival
        // (rememberAppearAlpha) instead of snapping it on. The gate was the other half of the
        // instant feeling: the tab transition played over a blank page and the content popped in
        // after it had already finished.
        val reducedMotion = com.corlang.app.ui.theme.rememberReducedMotion()
        // The overlay comes away once the destination has had frames to compose and draw.
        //
        // Deliberately counted in FRAMES rather than waiting for currentRoute to equal the route
        // we asked for. That match is not guaranteed: the tab navigations restore saved state, so
        // tapping a tab whose stack was left inside a quiz restores THAT route, and an overlay
        // waiting for an arrival that never comes would strand the learner in Settings with no
        // way out but system back. Stranding someone is far worse than a frame of the wrong tab,
        // so this always runs and always finishes.
        LaunchedEffect(overlayHandoffTo) {
            if (overlayHandoffTo == null) return@LaunchedEffect
            withFrameNanos {}
            withFrameNanos {}
            showSettings = false
            showPlacement = false
            showPaywall = false
            overlayHandoffTo = null
        }
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
            enterTransition = {
                if (skipTabAnim) EnterTransition.None
                else com.corlang.app.ui.theme.Motion.enter(reducedMotion)
            },
            exitTransition = {
                if (skipTabAnim) ExitTransition.None
                else com.corlang.app.ui.theme.Motion.exit(reducedMotion)
            },
            popEnterTransition = {
                if (skipTabAnim) EnterTransition.None
                else com.corlang.app.ui.theme.Motion.enter(reducedMotion)
            },
            popExitTransition = {
                if (skipTabAnim) ExitTransition.None
                else com.corlang.app.ui.theme.Motion.exit(reducedMotion)
            }
        ) {
            // Tab switches share ONE uniform fade (below), so every tab — Review included —
            // animates identically. The old per-screen Crossfade(lang) wrappers are gone: they
            // fired a slow 1300ms fade on every language change and animated inconsistently
            // across tabs (Review reveals its async-loaded queue, so a content-crossfade there
            // showed a loading frame). Language now switches only from Profile, so a fade on
            // `lang` isn't needed at all.
            composable(Dest.TODAY.route) {
                // key(lang): a language switch tears the screen down and rebuilds it, so the
                // first frame of the new language starts from the load-gate instead of showing
                // one frame of the OLD language's numbers mapped onto the new plan. Without it,
                // collectAsState retains the previous language's non-null values across the
                // switch, the gate passes on stale data, and saveable state (viewedDay, the
                // journey's selected level) initializes from the wrong course (field: erasing
                // Portuguese landed a day-8 A0 Croatian learner on A1).
                androidx.compose.runtime.key(lang) {
                TodayScreen(
                    container, lang,
                    inLesson = inLesson,
                    onInLessonChange = { inLesson = it },
                    onNavigate = { route ->
                        skipTabAnim = false
                        navController.navigate(route) {
                            popUpTo(Dest.TODAY.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenPaywall = { level -> paywallLevel = level; showPaywall = true }
                )
                }
            }
            composable(Dest.WORDS.route) { androidx.compose.runtime.key(lang) { WordsScreen(container, lang) } }
            composable(Dest.LEARN.route) { androidx.compose.runtime.key(lang) { LearnScreen(container, lang) } }
            composable(Dest.PROGRESS.route) { androidx.compose.runtime.key(lang) { ProgressScreen(container, lang) } }
            // End-of-level checkpoints, opened from the journey (Today tab). Argumented routes,
            // not tabs: each exits by popping back to wherever the journey was.
            composable("quiz/{level}") { entry ->
                LevelQuizScreen(
                    container, lang,
                    levelId = entry.arguments?.getString("level") ?: "",
                    onExit = { skipTabAnim = false; navController.popBackStack() }
                )
            }
            composable("readiness/{level}") { entry ->
                ReadinessScreen(
                    container, lang,
                    levelId = entry.arguments?.getString("level") ?: "",
                    onExit = { skipTabAnim = false; navController.popBackStack() }
                )
            }
            composable("exam/{level}") { entry ->
                ExamScreen(
                    container, lang,
                    levelId = entry.arguments?.getString("level") ?: "",
                    onExit = { skipTabAnim = false; navController.popBackStack() }
                )
            }
            composable(Dest.PROFILE.route) {
                ProfileScreen(
                    container, lang,
                    resetTick = profileTabTick,
                    onSelectLanguage = appState::selectLanguage,
                    onOpenSettings = { showSettings = true },
                    onGetPremium = { paywallLevel = null; showPaywall = true },
                    // Any paid level opens the shelf: PaywallScreen shows every tier of the
                    // course, so this names the top one only to be unambiguous about which
                    // course is being offered.
                    onUnlockCourse = {
                        paywallLevel = container.content.plan(lang).days.lastOrNull()?.level
                        if (paywallLevel != null) showPaywall = true
                    }
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
                    // Fades UP over the tab it covers, so opening a full-screen overlay is a
                    // move rather than a cut. No exit fade: an overlay coming down hands off to
                    // a page that is already composed underneath, and the frame-counted handoff
                    // above depends on it going away the moment it is told to.
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(com.corlang.app.ui.theme.rememberAppearAlpha())
                ) {
                    // Where "Leave" lands them: the course they were actually studying, if any.
                    val backTo = lastSettledLang?.takeIf { it != lang }
                    PlacementScreen(
                        container, lang,
                        onDone = { showPlacement = false },
                        onAbandon = {
                            showPlacement = false
                            scope.launch {
                                // Belt and braces. Nothing marks this language handled until a
                                // placement commits, so this is normally a no-op; it stays for
                                // an install carrying the flag from a build that set it early.
                                container.languagePrefs.unmarkPlacementHandled(lang)
                                if (backTo != null) container.languagePrefs.setLanguage(backTo)
                            }
                        },
                        returnTo = backTo?.let { code ->
                            appState.languages.firstOrNull { it.code == code }?.name
                        }
                    )
                }
            }
            showSettings -> {
                androidx.activity.compose.BackHandler { showSettings = false }
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    // Fades UP over the tab it covers, so opening a full-screen overlay is a
                    // move rather than a cut. No exit fade: an overlay coming down hands off to
                    // a page that is already composed underneath, and the frame-counted handoff
                    // above depends on it going away the moment it is told to.
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(com.corlang.app.ui.theme.rememberAppearAlpha())
                ) {
                    SettingsScreen(
                        container,
                        onBack = { showSettings = false },
                        onEditProfile = { showSettings = false; showOnboarding = true },
                        onProgressReset = { landOn ->
                            // Land on the Today dashboard of the chosen course (the one with the
                            // most remaining progress, or the freshly reset course itself).
                            appState.selectLanguage(landOn)
                            // Same handoff: this navigates as Settings closes.
                            skipTabAnim = true
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
                    // Fades UP over the tab it covers, so opening a full-screen overlay is a
                    // move rather than a cut. No exit fade: an overlay coming down hands off to
                    // a page that is already composed underneath, and the frame-counted handoff
                    // above depends on it going away the moment it is told to.
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(com.corlang.app.ui.theme.rememberAppearAlpha())
                ) {
                    PaywallScreen(
                        container, lang = lang, levelId = paywallLevel,
                        onClose = { showPaywall = false }
                    )
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
