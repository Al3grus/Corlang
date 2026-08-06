package com.corlang.app.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.corlang.app.AppContainer
import com.corlang.app.speech.VoiceMemo
import com.corlang.app.ui.Haptics

/**
 * Say the line aloud, hear yourself against the model.
 *
 * This used to judge pronunciation with the system speech recogniser, and it is not coming back.
 * The judgement was the broken part, not the microphone: Google ships no reliable on-device model
 * for Croatian or European Portuguese, so the recogniser transcribed the learner's speech through
 * whatever model it did have and the scorer faithfully reported the resulting nonsense as bad
 * pronunciation. Tuning the scorer cannot fix a wrong transcript, and a check that fails correct
 * speech is worse than no check, because the learner cannot tell the two apart. Two rounds of
 * fixes (all hypotheses, edit-distance scoring, offline first, an availability check) improved it
 * and it still was not trustworthy.
 *
 * What is left needs no recogniser, no network and no language support, and cannot tell a learner
 * they said something they did not: record the attempt, play it back next to the model line, and
 * let the learner hear the difference. It is the oldest pronunciation technique there is and it
 * works in every language the app will ever ship.
 */
@Composable
fun SpeakCheck(container: AppContainer, target: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val memo = remember { VoiceMemo(context) }

    var recording by remember { mutableStateOf(false) }
    var hasTake by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun toggle() {
        message = null
        if (recording) {
            recording = false
            hasTake = memo.stopRecording()
            if (!hasTake) message = "That was too short. Tap, speak, then tap again."
            else Haptics.tick(context)
        } else {
            recording = memo.startRecording()
            if (!recording) message = "Couldn't start recording."
        }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) toggle() else message = "Microphone permission is needed."
    }

    // Leaving the screen must close the mic and drop the take: the recording exists for the one
    // comparison it was made for.
    DisposableEffect(Unit) { onDispose { memo.release() } }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) }) {
                Icon(
                    if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (recording) "Stop recording"
                    else "Record yourself saying it",
                    tint = if (recording) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
            Text(
                when {
                    recording -> "Recording… tap to stop"
                    message != null -> message!!
                    hasTake -> "Compare yours with the model"
                    else -> "Tap to record yourself"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Yours first, then the model: that order is what makes a difference audible.
        if (hasTake && !recording) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { memo.play() }) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Text("Yours", modifier = Modifier.padding(start = 4.dp))
                }
                TextButton(onClick = { container.tts.speak(target) }) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Text("Model", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}
