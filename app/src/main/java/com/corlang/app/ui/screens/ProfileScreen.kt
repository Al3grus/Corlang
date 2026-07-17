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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
    onSelectLanguage: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    // Sub-page routing within the tab (null = the menu).
    var page by rememberSaveable(lang) { mutableStateOf<String?>(null) }

    when (page) {
        "language" -> SubPage("Language", onBack = { page = null }) {
            LanguagePage(container, lang, onSelectLanguage)
        }
        "premium" -> SubPage("Corlang Premium", onBack = { page = null }) {
            PremiumPage(container)
        }
        "references" -> SubPage("References", onBack = { page = null }) {
            ReferencesPage(container, lang)
        }
        else -> Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Text("Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Settings, language, premium and reference material.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))

            val entitled by container.premium.entitled.collectAsState(initial = false)
            val meta = remember(lang) { container.content.meta(lang) }

            MenuRow(Icons.Outlined.Settings, "Settings",
                "Reminder, study pace, voice, backup", onClick = onOpenSettings)
            MenuRow(Icons.Outlined.Language, "Language",
                "${meta.flagEmoji} ${meta.name} · tap to switch", onClick = { page = "language" })
            MenuRow(Icons.Outlined.WorkspacePremium, "Get Premium",
                if (entitled) "Active ✓ — AI tutor unlocked" else "Unlock the AI tutor (Learn tab)",
                onClick = { page = "premium" })
            MenuRow(Icons.AutoMirrored.Outlined.MenuBook, "References",
                "Cheatsheet, grammar, best resources", onClick = { page = "references" })
        }
    }
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

/** A titled sub-page with a back button; system back returns to the menu too. */
@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) { Text("← Back") }
            Text(title, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

/** Choose the language you're learning — the ONLY place to switch (top bar no longer picks). */
@Composable
private fun LanguagePage(container: AppContainer, lang: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Which language are you learning? Your progress is kept separately for each.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp))
        container.content.allMeta().forEach { m ->
            val chosen = m.code == lang
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (chosen) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    if (chosen) 2.dp else 1.dp,
                    if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onSelect(m.code) }
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(m.flagEmoji, style = MaterialTheme.typography.headlineSmall)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(m.name, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(m.nativeName, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (chosen) Text("✓", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Premium = the AI-tutor subscription: what it unlocks and its current state. */
@Composable
private fun PremiumPage(container: AppContainer) {
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
                "AI tutor — chat in your language at your level",
                "AI review of your teach-back explanations",
                "AI examiner feedback on your exam writing",
            ).forEach {
                Text("•  $it", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp))
            }
            Text(
                if (entitled) "Enjoy — the Learn tab is in your bottom bar."
                else "Subscriptions arrive with the Google Play release. The whole course, " +
                    "spaced-repetition review and progress tracking stay free.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Reference library: cheatsheet, grammar, the Pareto note, and curated external resources. */
@Composable
private fun ReferencesPage(container: AppContainer, lang: String) {
    var doc by rememberSaveable(lang) { mutableStateOf<String?>(null) }
    if (doc != null) {
        BackHandler { doc = null }
        Column(Modifier.fillMaxSize()) {
            OutlinedButton(onClick = { doc = null },
                modifier = Modifier.padding(start = 16.dp, top = 8.dp)) { Text("← Back") }
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
            Text("Cheatsheet — the language on one page →")
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
