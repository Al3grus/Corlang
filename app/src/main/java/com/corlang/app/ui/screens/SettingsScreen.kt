package com.corlang.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.reminder.ReminderScheduler
import com.corlang.app.speech.TtsState
import com.corlang.app.ui.components.InfoCard
import com.corlang.app.ui.components.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** All app settings in one place: reminder, SRS pace, speech, about. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("← Back") }
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        // ----- Study reminder -----
        SectionTitle("Study reminder", Icons.Outlined.Alarm)
        val enabled by container.languagePrefs.reminderEnabled.collectAsState(initial = false)
        val time by container.languagePrefs.reminderTime.collectAsState(initial = 19 to 0)
        var showPicker by remember { mutableStateOf(false) }

        fun apply(on: Boolean) {
            scope.launch { container.languagePrefs.setReminderEnabled(on) }
            if (on) ReminderScheduler.schedule(context, time.first, time.second)
            else ReminderScheduler.cancel(context)
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> if (granted) apply(true) }

        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { showPicker = true }
                ) {
                    Text(
                        "Remind me at %d:%02d  (tap to change)".format(time.first, time.second),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Set it to when you plan to study. Silent on days you've already studied.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        if (on && Build.VERSION.SDK_INT >= 33) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            apply(on)
                        }
                    }
                )
            }

            if (enabled) {
                // Which courses may nag. Trying a placement test in another language must not
                // sign you up for its daily reminders — only the languages picked here do.
                val selectedLang by container.languagePrefs.selectedLanguage
                    .collectAsState(initial = "hr")
                val chosenLangs by container.languagePrefs.reminderLanguages
                    .collectAsState(initial = null)
                val effective = chosenLangs ?: setOf(selectedLang)

                Spacer(Modifier.height(8.dp))
                Text(
                    "Remind me about",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row {
                    container.content.allMeta().forEach { meta ->
                        val checked = meta.code in effective
                        FilterChip(
                            selected = checked,
                            onClick = {
                                // Never allow zero languages — turn the reminder off instead.
                                val next = if (checked) effective - meta.code else effective + meta.code
                                if (next.isNotEmpty()) {
                                    scope.launch { container.languagePrefs.setReminderLanguages(next) }
                                }
                            },
                            label = { Text("${meta.flagEmoji} ${meta.name}") },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                Text(
                    "Only the languages you pick here send the daily nudge.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showPicker) {
            val pickerState = rememberTimePickerState(
                initialHour = time.first, initialMinute = time.second, is24Hour = true
            )
            AlertDialog(
                onDismissRequest = { showPicker = false },
                title = { Text("Remind me at…") },
                text = { TimePicker(state = pickerState) },
                confirmButton = {
                    Button(onClick = {
                        showPicker = false
                        scope.launch {
                            container.languagePrefs.setReminderTime(pickerState.hour, pickerState.minute)
                        }
                        if (enabled) {
                            ReminderScheduler.schedule(context, pickerState.hour, pickerState.minute)
                        }
                    }) { Text("Save") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showPicker = false }) { Text("Cancel") }
                }
            )
        }

        // ----- Learning pace -----
        SectionTitle("Learning pace", Icons.Outlined.Speed)
        val newPerDay by container.languagePrefs.newWordsPerDay.collectAsState(initial = 10)
        InfoCard {
            Text("New words per lesson", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "How many new words each lesson introduces. 10 is the sustainable default. New words " +
                    "come from lessons, so the Words tab stays a pure review of what you've learned.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(10, 15, 20).forEachIndexed { i, v ->
                    SegmentedButton(
                        selected = newPerDay == v,
                        onClick = { scope.launch { container.languagePrefs.setNewWordsPerDay(v) } },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
                        icon = {}
                    ) { Text("$v") }
                }
            }
        }

        // ----- Speech -----
        // Follows the ACTIVE language: a French learner manages the French voice here.
        val ttsLang by container.languagePrefs.selectedLanguage.collectAsState(initial = "hr")
        val voiceName = remember(ttsLang) { container.content.meta(ttsLang).name }
        SectionTitle("$voiceName voice (TTS)", Icons.AutoMirrored.Outlined.VolumeUp)
        val ttsState by container.tts.state.collectAsState()
        InfoCard {
            Text(
                when (ttsState) {
                    TtsState.READY -> "$voiceName voice ready ✓"
                    TtsState.LANGUAGE_MISSING -> "$voiceName voice not installed"
                    TtsState.UNAVAILABLE -> "Text-to-speech unavailable on this device"
                    else -> "Checking…"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (ttsState == TtsState.LANGUAGE_MISSING) {
                Button(
                    onClick = { container.tts.promptInstallVoice() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Install $voiceName voice") }
            } else {
                OutlinedButton(
                    onClick = {
                        container.tts.speak(
                            when (ttsLang) {
                                "fr" -> "Bonjour ! Je suis Corlang."
                                "pt" -> "Olá! Eu sou o Corlang."
                                else -> "Dobar dan! Ja sam Corlang."
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Test voice") }
            }
        }

        // ----- Haptic feedback -----
        SectionTitle("Haptic feedback", Icons.Outlined.Vibration)
        val haptics by container.languagePrefs.hapticsStrength.collectAsState(initial = "MEDIUM")
        InfoCard {
            Text("Vibration on answers and card grades",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Stronger levels are easier to feel mid-swipe at the gym. Picking one plays a sample.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf("OFF" to "Off", "LIGHT" to "Light", "MEDIUM" to "Medium", "STRONG" to "Strong")
                options.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = haptics == value,
                        onClick = {
                            scope.launch { container.languagePrefs.setHapticsStrength(value) }
                            // Let them feel the choice immediately (collector updates the level,
                            // set it here first so the sample uses the new strength).
                            com.corlang.app.ui.Haptics.strength =
                                com.corlang.app.ui.Haptics.Strength.valueOf(value)
                            com.corlang.app.ui.Haptics.confirm(context)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                        icon = {}
                    ) { Text(label, maxLines = 1, softWrap = false) }
                }
            }
        }

        // ----- Profile -----
        SectionTitle("Profile", Icons.Outlined.Person)
        val prof by container.languagePrefs.profile.collectAsState(
            initial = com.corlang.app.data.prefs.LearnerProfile("", "m", "", "", "")
        )
        InfoCard {
            Text(
                if (prof.name.isBlank()) "No profile yet" else prof.name,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
            )
            Text(
                buildString {
                    if (prof.from.isNotBlank()) append("From ${prof.from}")
                    if (prof.livesIn.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append("lives in ${prof.livesIn}")
                    }
                    if (isEmpty()) append("Set it up to unlock your personalized intro phrases.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
            OutlinedButton(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) {
                Text(if (prof.name.isBlank()) "Set up profile" else "Edit profile")
            }
            Text(
                "Editing the profile also lets you retake the level placement test.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // ----- Backup & restore -----
        SectionTitle("Backup & restore", Icons.Outlined.Save)
        var backupMsg by remember { mutableStateOf("") }
        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                backupMsg = try {
                    val text = container.backup.export(System.currentTimeMillis())
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(text.toByteArray(Charsets.UTF_8))
                        } ?: error("Couldn't open the file.")
                    }
                    "Backup saved ✓"
                } catch (e: Exception) {
                    "Backup failed: ${e.message}"
                }
            }
        }
        val importLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use {
                            it.readBytes().toString(Charsets.UTF_8)
                        }
                    }.getOrNull()
                }
                backupMsg = if (text == null) {
                    "Couldn't read that file."
                } else {
                    container.backup.import(text).fold(
                        onSuccess = { "Restored $it items ✓" },
                        onFailure = { "Restore failed: ${it.message}" }
                    )
                }
            }
        }
        InfoCard {
            Text("Save your progress to a file", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            Text(
                "Export your streak, learned words, and lesson progress to a file you keep, then " +
                    "restore it after reinstalling or on a new phone. Restoring replaces the current " +
                    "progress on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
            if (backupMsg.isNotBlank()) {
                Text(backupMsg, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 6.dp))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { exportLauncher.launch("corlang-backup.json") },
                    modifier = Modifier.weight(1f)
                ) { Text("Back up") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.weight(1f)
                ) { Text("Restore") }
            }
        }

        // ----- Updates -----
        SectionTitle("App updates", Icons.Outlined.SystemUpdate)
        var checkState by remember { mutableStateOf("") }
        var updateInfo by remember { mutableStateOf<com.corlang.app.update.ReleaseInfo?>(null) }
        var dl by remember { mutableStateOf(false) }
        var pct by remember { mutableStateOf(0) }
        InfoCard {
            Text(
                "Installed: v${container.updater.installedVersionName()}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Updates download and install from within the app, no browsing needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
            if (checkState.isNotBlank()) {
                Text(checkState, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 6.dp))
            }
            val info = updateInfo
            if (info != null) {
                Button(
                    enabled = !dl,
                    onClick = {
                        dl = true
                        scope.launch {
                            val apk = container.updater.downloadApk(info) { pct = it }
                            dl = false
                            if (apk != null) container.updater.installApk(apk)
                            else checkState = "Download failed, try again."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (dl) "Downloading… $pct%" else "Install v${info.versionName}") }
            } else {
                OutlinedButton(
                    onClick = {
                        checkState = "Checking…"
                        scope.launch {
                            val latest = container.updater.fetchLatest()
                            checkState = when {
                                latest == null -> "Couldn't reach the update server."
                                container.updater.isNewer(latest) -> "v${latest.versionName} available!"
                                else -> "You're on the latest version ✓"
                            }
                            if (latest != null && container.updater.isNewer(latest)) updateInfo = latest
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Check for updates") }
            }
        }

        // ----- Premium -----
        SectionTitle("Corlang Premium", Icons.Outlined.WorkspacePremium)
        val entitled by container.premium.entitled.collectAsState(initial = false)
        InfoCard {
            Text(
                if (entitled) "Active ✓" else "Coming soon",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = if (entitled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Premium unlocks the AI tutor: a conversation partner that chats in your learning " +
                    "language at your level (Talk tab) and examiner feedback on your exam writing. " +
                    if (entitled) "Enjoy!"
                    else "It arrives with the Google Play release.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // ----- About -----
        SectionTitle("About", Icons.Outlined.Info)
        InfoCard {
            com.corlang.app.ui.components.CorlangLogo(
                variant = com.corlang.app.ui.components.LogoVariant.LOCKUP,
                size = 28.dp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Core + Language · v${container.updater.installedVersionName()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                "Learn the core of a language on an evidence-based daily method (retrieval practice + " +
                    "distributed practice), anchored to official curricula and the real exam formats " +
                    "(Croatian NN B1, French DELF B1/B2).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                "Private by design — your learning data lives only on this device. No account, no " +
                    "sign-in, no tracking. Back it up yourself any time from Backup & restore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            val privacyUri = androidx.compose.ui.platform.LocalUriHandler.current
            Text(
                "Read the full privacy policy ↗",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable { privacyUri.openUri("https://github.com/Al3grus/Corlang/blob/main/PRIVACY.md") }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
