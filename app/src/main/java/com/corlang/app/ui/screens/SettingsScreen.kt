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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    onPlacement: () -> Unit = {},
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
        SectionTitle("⏰ Study reminder")
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

        // ----- Placement -----
        SectionTitle("🎯 Starting level")
        InfoCard {
            Text("Find your level", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "A two-minute check across A0 to B1 places you at the right lesson, so you don't " +
                    "start at day 1 if you already know some Croatian. Earlier days stay open to review.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
            OutlinedButton(onClick = onPlacement, modifier = Modifier.fillMaxWidth()) {
                Text("Take the placement test")
            }
        }

        // ----- Learning pace -----
        SectionTitle("🃏 Learning pace")
        val newPerDay by container.languagePrefs.newWordsPerDay.collectAsState(initial = 10)
        InfoCard {
            Text("Daily word goal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Your daily target of new words. 10 is the sustainable default, but it's a goal, " +
                    "not a cap: you can always keep learning past it with \"Learn 10 more\" in the Words tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(10, 15, 20).forEachIndexed { i, v ->
                    SegmentedButton(
                        selected = newPerDay == v,
                        onClick = { scope.launch { container.languagePrefs.setNewWordsPerDay(v) } },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 3)
                    ) { Text("$v") }
                }
            }
        }

        // ----- Speech -----
        SectionTitle("🔊 Croatian voice (TTS)")
        val ttsState by container.tts.state.collectAsState()
        InfoCard {
            Text(
                when (ttsState) {
                    TtsState.READY -> "Croatian voice ready ✓"
                    TtsState.LANGUAGE_MISSING -> "Croatian voice not installed"
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
                ) { Text("Install Croatian voice") }
            } else {
                OutlinedButton(
                    onClick = { container.tts.speak("Dobar dan! Ja sam Corlang.") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Test voice") }
            }
        }

        // ----- Updates -----
        SectionTitle("⬇️ App updates")
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

        // ----- Backup & restore -----
        SectionTitle("💾 Backup & restore")
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

        // ----- About -----
        SectionTitle("ℹ️ About")
        InfoCard {
            Text("Corlang, Core + Language", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Learn the core of a language on an evidence-based daily method (retrieval practice + " +
                    "distributed practice), anchored to the official Croatian curriculum and the real " +
                    "B1 exam format. Current target: A1 → B1.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
