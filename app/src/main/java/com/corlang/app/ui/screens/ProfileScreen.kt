package com.corlang.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.ui.components.InfoCard
import com.corlang.app.ui.components.SectionTitle
import kotlinx.coroutines.launch

/**
 * Profile = the app's control panel: four uniform rows — Settings, Language, Get Premium,
 * References. Progress-related stats live on the separate Progress tab; this tab is where you
 * change how the app works, not where you check how you're doing.
 */
@Composable
fun ProfileScreen(
    container: AppContainer,
    lang: String,
    resetTick: Int = 0,
    onSelectLanguage: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onGetPremium: () -> Unit = {}
) {
    // Sub-page routing within the tab (null = the menu). A short crossfade smooths the
    // menu↔sub-page transitions (matching the app's tab fade, not a slow one).
    // Plain remember, NOT rememberSaveable: the NavHost saves/restores each tab's saveable
    // state across switches, so a saveable page re-opened the sub-page (Premium, References)
    // when you came BACK to Profile from another tab. Leaving the tab should reset to the
    // Profile main view, so this state is meant to die with the visit.
    var page by remember(lang) { mutableStateOf<String?>(null) }
    // A Profile tab tap closes whatever sub-page is open, landing back on the menu.
    LaunchedEffect(resetTick) { if (resetTick > 0) page = null }

    androidx.compose.animation.Crossfade(
        targetState = page,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 260),
        label = "profile-page"
    ) { p -> when (p) {
        "language" -> SubPage("Language", onBack = { page = null }) {
            // Choosing a language returns to the Profile menu (like closing Settings does).
            LanguagePage(container, lang) { code -> onSelectLanguage(code); page = null }
        }
        "premium" -> SubPage("Corlang Premium", onBack = { page = null }) {
            PremiumPage(container, onGetPremium)
        }
        "references" -> SubPage("References", onBack = { page = null }) {
            ReferencesPage(container, lang)
        }
        else -> Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            // Just the title: the menu rows below already name everything here, so a subtitle
            // listing them again added nothing. Same size and air as the Settings header.
            Text(
                "Profile",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val entitled by container.premium.entitled.collectAsState(initial = false)
            val meta = remember(lang) { container.content.meta(lang) }

            MenuRow(Icons.Outlined.Settings, "Settings",
                "Reminder, study pace, voice, backup", onClick = onOpenSettings)
            MenuRow(Icons.Outlined.Language, "Language",
                "${meta.flagEmoji} ${meta.name} · tap to switch", onClick = { page = "language" })
            MenuRow(Icons.Outlined.WorkspacePremium, "Get Premium",
                if (entitled) "Active ✓ · AI tutor unlocked" else "Unlock the AI tutor (Learn tab)",
                onClick = { page = "premium" })
            // References is HIDDEN, not removed: the page and its "references" branch above are
            // intact and one line brings the entrance back. Same pattern as a hidden language,
            // which stays in the repo and only leaves content/_index.json.
            // MenuRow(Icons.AutoMirrored.Outlined.MenuBook, "References",
            //     "Cheatsheet, grammar, best resources", onClick = { page = "references" })
        }
    } }
}

/** One uniform Profile menu row: icon · title/subtitle · chevron. */
@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A titled sub-page with a back button; system back returns to the menu too.
 *  Header matches the Settings screen exactly: arrow + headlineSmall bold, 16dp above and
 *  below so the title sits centred in its band. */
@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // top = 16 mirrors the Settings screen, whose whole column carries 16dp padding;
            // 4dp here left these headers sitting visibly higher than the Settings one.
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 16.dp, end = 16.dp)
        ) {
            // A bare arrow, no box or text: system back works too, this is just the visible way.
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        // 16dp under the header mirrors the 16dp above it, centring the title in its band.
        Spacer(Modifier.height(16.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

/**
 * Choose the language you're learning — the ONLY place to switch (top bar no longer picks).
 *
 * Split in two, because these are different decisions wearing the same shape: RESUMING a course
 * you have days banked in, and STARTING one you have never opened. A single flat list made a
 * six-course build read as six equal options every time, and buried the one or two the learner
 * actually studies among four they have never touched.
 */
@Composable
private fun LanguagePage(container: AppContainer, lang: String, onSelect: (String) -> Unit) {
    val all = remember { container.content.allMeta() }
    // A course counts as started once a day is banked or the learner has been moved off day 1
    // (placement does that without completing anything). Read per language and remembered as one
    // map, so the two lists below cannot disagree about where a course belongs.
    // null until Room answers. Load-then-show, the same rule Today and Progress follow: with an
    // empty map as the initial value every language except the selected one was classified as
    // never started for one frame, so a course you HAVE started visibly jumped from "Not started
    // yet" up to "Your courses" as the page opened.
    val startedDays by produceState<Map<String, Int>?>(initialValue = null, all) {
        value = all.associate { m ->
            val done = container.progress.completedDayCount(m.code).first()
            val day = container.progress.progress(m.code).first()?.currentDay ?: 1
            m.code to if (done > 0 || day > 1) maxOf(day, 1) else 0
        }
    }
    val days = startedDays
    val started = days?.let { d -> all.filter { (d[it.code] ?: 0) > 0 || it.code == lang } }
    val fresh = started?.let { s -> all.filterNot { it in s } }

    /*
     * The WHOLE page fades in once the read lands, rather than the heading painting immediately
     * and the lists snapping in underneath it a frame later. Withholding the lists stopped a
     * language appearing in the wrong group, but it left the pop: the page arrived in two
     * pieces. One short fade covers the gap, and because it is driven by the data rather than by
     * a timer, it is exactly as long as the wait actually is.
     */
    val ready = days != null
    val fade by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (ready) 1f else 0f,
        animationSpec = if (com.corlang.app.ui.theme.rememberReducedMotion())
            androidx.compose.animation.core.snap()
        else androidx.compose.animation.core.tween(durationMillis = 220),
        label = "languagePageFade"
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
            .alpha(fade)
    ) {
        Text("Your progress is kept separately for each language, so switching never loses anything.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp))

        // Both lists appear together or not at all: a language must never be seen in the wrong
        // group, however briefly.
        if (days != null && started != null && fresh != null) {
            if (started.isNotEmpty()) {
                LanguageGroupHeading("Your courses")
                started.forEach { m ->
                    LanguageRow(
                        meta = m,
                        chosen = m.code == lang,
                        // The current language can be here without a day banked yet (it was just
                        // picked in onboarding), so name it without claiming a lesson.
                        subtitle = (days[m.code] ?: 0).let {
                            if (it > 0) "${m.nativeName} · Lesson $it" else m.nativeName
                        },
                        onSelect = onSelect
                    )
                }
            }
            if (fresh.isNotEmpty()) {
                LanguageGroupHeading("Not started yet")
                fresh.forEach { m ->
                    LanguageRow(meta = m, chosen = false, subtitle = m.nativeName, onSelect = onSelect)
                }
            }
        }
    }
}

@Composable
private fun LanguageGroupHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun LanguageRow(
    meta: com.corlang.app.data.model.LanguageMeta,
    chosen: Boolean,
    subtitle: String,
    onSelect: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (chosen) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (chosen) 2.dp else 1.dp,
            if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .clickable { onSelect(meta.code) }
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(meta.flagEmoji, style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(meta.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (chosen) Text("✓", color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Premium = the AI-tutor subscription: what it unlocks and its current state. */
@Composable
private fun PremiumPage(container: AppContainer, onGetPremium: () -> Unit) {
    val entitled by container.premium.entitled.collectAsState(initial = false)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        InfoCard {
            Text(if (entitled) "Premium is active ✓" else "Corlang Premium",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = if (entitled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(
                "A subscription that unlocks the Learn tab's AI:",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp))
            listOf(
                "AI tutor: chat in your language at your level",
                "AI review of your teach-back explanations",
                "AI examiner feedback on your exam writing",
            ).forEach {
                Text("•  $it", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp))
            }
            Text(
                if (entitled) "Enjoy! The Learn tab is in your bottom bar."
                else "The whole course, spaced-repetition review and progress tracking stay free.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp))
        }
        if (!entitled) {
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Button(
                onClick = onGetPremium, modifier = Modifier.fillMaxWidth()
            ) { Text("See plans") }
        }
        // Restore also runs automatically on every app resume; this is the explicit affordance
        // (a reinstall / new device picks the purchase back up from the Play account).
        OutlinedButton(
            onClick = { container.billing.start() },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("Restore purchases") }
        Spacer(Modifier.height(24.dp))
    }
}

/** Reference library: cheatsheet, grammar, the Pareto note, and curated external resources. */
@Composable
private fun ReferencesPage(container: AppContainer, lang: String) {
    // Plain remember for the same reason as `page` above: an open document must not be
    // restored when the learner comes back to the Profile tab later.
    var doc by remember(lang) { mutableStateOf<String?>(null) }
    if (doc != null) {
        BackHandler { doc = null }
        Column(Modifier.fillMaxSize()) {
            IconButton(onClick = { doc = null }, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Box(Modifier.weight(1f)) {
                if (doc == "cheatsheet") CheatsheetScreen(container, lang)
                else GrammarScreen(container, lang)
            }
        }
        return
    }
    val meta = remember(lang) { container.content.meta(lang) }
    val resources = remember(lang) {
        container.content.resources(lang).resources.sortedBy { it.rank }
    }
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        OutlinedButton(onClick = { doc = "cheatsheet" }, modifier = Modifier.fillMaxWidth()) {
            Text("Cheatsheet: the language on one page →")
        }
        OutlinedButton(onClick = { doc = "grammar" },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp)) {
            Text("Grammar syllabus →")
        }

        SectionTitle("The 20% that drives 80%")
        Text(meta.paretoSummary, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp))

        SectionTitle("Best resources to learn ${meta.name}")
        resources.forEach { r ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .clickable(enabled = r.url != null) { r.url?.let { uriHandler.openUri(it) } }
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("${r.rank}. ${r.name}", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text(
                        r.type.replaceFirstChar { it.uppercase() } + (r.url?.let { url ->
                            " · " + url.removePrefix("https://").removePrefix("http://")
                                .substringBefore('/') + " ↗"
                        } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(r.why, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
