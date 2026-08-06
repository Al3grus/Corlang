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
import com.corlang.app.speech.PronunciationScore
import com.corlang.app.speech.VoiceMemo
import com.corlang.app.ui.Haptics
import com.corlang.app.ui.theme.CorlangColors

/**
 * Say [target] aloud and get something useful back, on every device.
 *
 * Two modes, and the learner never has to know which one they are in:
 *
 *  - RECOGNISE, when the device can actually transcribe the course language: what was heard is
 *    scored against the target and shown. Scoring is [PronunciationScore], which reads characters
 *    rather than whole words and considers every hypothesis the recogniser returned, because the
 *    old exact-word rule failed correct speech routinely (one inflected ending was enough).
 *  - COMPARE, when it cannot: record the attempt and play it back against the model line. The
 *    Croatian and European Portuguese packs are frequently missing, and in that case the
 *    recogniser used to transcribe the learner's speech through the device's own locale and
 *    report the resulting nonsense as bad pronunciation. Hearing yourself next to the model is
 *    honest, works offline, and works for every language.
 *
 * The switch is automatic and permanent for the session: the first time recognition reports the
 * LANGUAGE as unusable (not merely a misheard attempt), this drops to compare mode rather than
 * asking the learner to keep failing at it.
 */
@Composable
fun SpeakCheck(container: AppContainer, target: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val feedback = CorlangColors.feedback

    // Recognition is the better mode when it works, so it is the default; availability is checked
    // BEFORE offering it, which the previous version never did (it launched regardless and let
    // the error read as a failed attempt).
    var canRecognise by remember { mutableStateOf(container.speech.isAvailable()) }
    val memo = remember { VoiceMemo(context) }

    var listening by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var hasTake by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf<String?>(null) }
    var verdict by remember { mutableStateOf<PronunciationScore.Verdict?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun recognise() {
        heard = null; verdict = null; message = null
        container.speech.listen(
            onListening = { listening = it },
            onResult = { hypotheses ->
                heard = PronunciationScore.bestHypothesis(target, hypotheses)
                val v = PronunciationScore.verdict(target, hypotheses)
                verdict = v
                if (v == PronunciationScore.Verdict.PASS) Haptics.confirm(context)
                else Haptics.tick(context)
            },
            onError = { msg, unusableLanguage ->
                listening = false
                if (unusableLanguage) {
                    // Not the learner's fault and not fixable by trying again: switch modes and
                    // say what changed, rather than repeating an error they cannot act on.
                    canRecognise = false
                    message = "This device can't check this language. Record yourself instead " +
                        "and compare with the model."
                } else {
                    message = msg
                }
            }
        )
    }

    fun toggleRecording() {
        message = null
        if (recording) {
            recording = false
            hasTake = memo.stopRecording()
            if (!hasTake) message = "That was too short. Hold while you speak."
            else Haptics.tick(context)
        } else {
            heard = null; verdict = null
            recording = memo.startRecording()
            if (!recording) message = "Couldn't start recording."
        }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) message = "Microphone permission is needed."
        else if (canRecognise) recognise() else toggleRecording()
    }

    // Leaving the screen must close the mic and drop the take: otherwise the recogniser stays hot
    // until its own timeout and fires into a disposed composition, and the recording outlives the
    // practice it was made for.
    DisposableEffect(Unit) {
        onDispose {
            if (listening) container.speech.cancel()
            memo.release()
        }
    }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                enabled = !listening,
                onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) }
            ) {
                Icon(
                    if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = when {
                        recording -> "Stop recording"
                        canRecognise -> "Say it aloud"
                        else -> "Record yourself saying it"
                    },
                    tint = if (listening || recording) feedback.wrong
                    else MaterialTheme.colorScheme.primary
                )
            }
            Text(
                when {
                    listening -> "Listening…"
                    recording -> "Recording… tap to stop"
                    verdict == PronunciationScore.Verdict.PASS -> "✅ Sounds right"
                    verdict == PronunciationScore.Verdict.CLOSE -> "≈ Close, try once more"
                    verdict == PronunciationScore.Verdict.MISS -> "❌ Not quite"
                    message != null -> message!!
                    hasTake -> "Compare yours with the model"
                    canRecognise -> "Tap and say it"
                    else -> "Tap to record yourself"
                },
                style = MaterialTheme.typography.bodySmall,
                color = when (verdict) {
                    PronunciationScore.Verdict.PASS -> feedback.correct
                    PronunciationScore.Verdict.MISS -> feedback.wrong
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        heard?.let {
            Text(
                "Heard: “$it”",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        // Playback pair, only once there is something to compare: your attempt, then the model,
        // which is the order that makes a difference audible.
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
