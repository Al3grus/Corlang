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
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Vibration
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
            // A bare arrow, no box or text: system back works too, this is just the visible way.
            androidx.compose.material3.IconButton(onClick = onBack) {
                androidx.compose.material3.Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back"
                )
            }
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(10.dp))

        // ----- Study reminder -----
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

        SettingsCard(Icons.Outlined.Alarm, "Study reminder") {
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
        val newPerDay by container.languagePrefs.newWordsPerDay.collectAsState(initial = 10)
        SettingsCard(Icons.Outlined.Speed, "New words per lesson") {
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

        // ----- Speech: only surfaces when something is WRONG (voice missing/unavailable).
        // A healthy voice needs no card, and the old "Test voice" button had no utility:
        // every speaker button in a lesson is a live test.
        val ttsLang by container.languagePrefs.selectedLanguage.collectAsState(initial = "hr")
        val voiceName = remember(ttsLang) { container.content.meta(ttsLang).name }
        val ttsState by container.tts.state.collectAsState()
        if (ttsState == TtsState.LANGUAGE_MISSING || ttsState == TtsState.UNAVAILABLE) {
            SettingsCard(Icons.AutoMirrored.Outlined.VolumeUp, "$voiceName voice") {
                Text(
                    if (ttsState == TtsState.LANGUAGE_MISSING) "$voiceName voice not installed"
                    else "Text-to-speech unavailable on this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (ttsState == TtsState.LANGUAGE_MISSING) {
                    Button(
                        onClick = { container.tts.promptInstallVoice() },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text("Install $voiceName voice") }
                }
            }
        }

        // ----- Haptic feedback -----
        val haptics by container.languagePrefs.hapticsStrength.collectAsState(initial = "MEDIUM")
        SettingsCard(Icons.Outlined.Vibration, "Haptic feedback") {
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
        SettingsCard(Icons.Outlined.Person, "Profile") {
            Text(
                "Your name, word forms, learning pace and starting level.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedButton(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) {
                Text("Edit profile")
            }
            // No placement here: placement's purpose is onboarding and starting a language
            // that has never been touched, and it lives only in those two moments.
        }

        // ----- Backup & restore -----
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
        SettingsCard(Icons.Outlined.Save, "Backup & restore") {
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

        // ----- Updates (sideload flavor only; Play builds update through the store) -----
        if (com.corlang.app.BuildConfig.ENABLE_UPDATER) {
            UpdatesSection(container)
        }

        // ----- About -----
        // (No Premium card here: Profile -> Get Premium is its one home.)
        SettingsCard(Icons.Outlined.Info, "About") {
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
                    "distributed practice), anchored to each language's official curriculum and real " +
                    "exam format.",
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

/**
 * One settings card in the same visual language as the Profile tab's menu rows: a bordered
 * surface, a primary-tinted icon, a semibold title. Collapsed by default, tap the header to
 * expand: eight cards of controls open at once read as clutter, and a visit usually touches
 * one of them.
 */
@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
    ) {
        Column(Modifier.padding(16.dp).animateContentSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                androidx.compose.material3.Icon(
                    icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = 14.dp)
                )
                androidx.compose.material3.Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

/**
 * The self-updater card (check → download → hand to the system installer). Compiled into every
 * build but only reachable in the sideload flavor (BuildConfig.ENABLE_UPDATER) — the Play flavor
 * updates through the store and carries no install permission.
 */
@Composable
private fun UpdatesSection(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var checkState by remember { mutableStateOf("") }
    var updateInfo by remember { mutableStateOf<com.corlang.app.update.ReleaseInfo?>(null) }
    var dl by remember { mutableStateOf(false) }
    var pct by remember { mutableStateOf(0) }
    SettingsCard(Icons.Outlined.SystemUpdate, "App updates") {
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
}
