package com.corlang.app.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.corlang.app.ui.Haptics
import com.corlang.app.ui.screens.Grading
import com.corlang.app.ui.theme.CorlangColors

/**
 * Mic button that lets the learner say [target] aloud and checks their pronunciation against it.
 * Speech recognition is imperfect (especially for diacritics), so matching is lenient: an exact
 * or near match passes, a partial overlap is "close", otherwise retry. Shows what was heard.
 */
@Composable
fun SpeakCheck(container: AppContainer, target: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val feedback = CorlangColors.feedback
    var listening by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf<String?>(null) }
    var verdict by remember { mutableStateOf<Verdict?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun start() {
        heard = null; verdict = null; message = null
        container.speech.listen(
            onListening = { listening = it },
            onResult = { text ->
                heard = text
                val v = score(target, text)
                verdict = v
                if (v == Verdict.PASS) Haptics.confirm(context) else Haptics.tick(context)
            },
            onError = { message = it; listening = false }
        )
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) start() else message = "Microphone permission is needed." }

    // Leaving the screen mid-listen must close the mic; otherwise it stays hot until the
    // recognizer's own timeout and its callbacks fire into a disposed composition.
    DisposableEffect(Unit) {
        onDispose { if (listening) container.speech.cancel() }
    }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                enabled = !listening,
                onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) }
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Say it aloud",
                    tint = if (listening) feedback.wrong else MaterialTheme.colorScheme.primary
                )
            }
            Text(
                when {
                    listening -> "Listening…"
                    verdict == Verdict.PASS -> "✅ Sounds right"
                    verdict == Verdict.CLOSE -> "≈ Close, try once more"
                    verdict == Verdict.MISS -> "❌ Not quite"
                    message != null -> message!!
                    else -> "Tap and say it"
                },
                style = MaterialTheme.typography.bodySmall,
                color = when (verdict) {
                    Verdict.PASS -> feedback.correct
                    Verdict.MISS -> feedback.wrong
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
    }
}

private enum class Verdict { PASS, CLOSE, MISS }

/** Lenient scoring: word-overlap after accent/case-insensitive normalisation. */
private fun score(target: String, heard: String): Verdict {
    val t = Grading.normalize(target).split(" ").filter { it.isNotBlank() }
    val h = Grading.normalize(heard).split(" ").filter { it.isNotBlank() }.toSet()
    if (t.isEmpty()) return Verdict.MISS
    val matched = t.count { it in h }
    val ratio = matched.toDouble() / t.size
    return when {
        ratio >= 0.8 -> Verdict.PASS
        ratio >= 0.4 -> Verdict.CLOSE
        else -> Verdict.MISS
    }
}
