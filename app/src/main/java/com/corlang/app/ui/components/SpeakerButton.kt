package com.corlang.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.corlang.app.speech.TtsManager
import com.corlang.app.speech.TtsState

/**
 * Speaker icon that pronounces [text] in Croatian. When the Croatian voice is missing it
 * opens the system voice-data installer instead, the feature explains itself.
 */
@Composable
fun SpeakerButton(
    tts: TtsManager,
    text: String,
    modifier: Modifier = Modifier,
    rate: Float = 1.0f
) {
    LaunchedEffect(Unit) { tts.ensureInit() }
    val state by tts.state.collectAsState()

    IconButton(
        onClick = {
            when (state) {
                TtsState.READY -> tts.speak(text, rate)
                TtsState.LANGUAGE_MISSING -> tts.promptInstallVoice()
                else -> Unit
            }
        },
        enabled = state == TtsState.READY || state == TtsState.LANGUAGE_MISSING,
        modifier = modifier
    ) {
        Icon(
            Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = "Pronounce",
            tint = if (state == TtsState.READY) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
