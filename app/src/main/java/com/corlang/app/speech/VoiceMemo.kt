package com.corlang.app.speech

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Record your own voice and play it straight back.
 *
 * This is what the mic falls back to when the device cannot transcribe the language at all
 * (Croatian and European Portuguese packs are frequently absent). Hearing your own attempt next
 * to the model line is the oldest pronunciation technique there is, it needs no recogniser, no
 * network and no language support, and unlike a wrong transcript it never tells a learner they
 * said something they did not.
 *
 * One recording at a time, overwritten each attempt and living in the cache directory: this is a
 * practice aid, not a keepsake, and nothing should accumulate on the learner's device.
 */
class VoiceMemo(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null

    private val file: File get() = File(context.cacheDir, "corlang_attempt.m4a")

    /** True once something has been recorded this session and is still on disk. */
    fun hasRecording(): Boolean = file.exists() && file.length() > 0

    /** Returns false if the device refused to start recording, so the caller can say so. */
    fun startRecording(): Boolean {
        stopPlayback()
        stopRecording()
        return runCatching {
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
            else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
        }.isSuccess
    }

    /**
     * Stops and keeps the recording. Returns false when the take is unusable, which is normal
     * rather than exceptional: MediaRecorder.stop() throws if it is stopped almost immediately
     * after start (too short to produce a valid file), and a tap-instead-of-hold does exactly
     * that. Treated as "no recording" so the UI can just say try again.
     */
    fun stopRecording(): Boolean {
        val r = recorder ?: return false
        recorder = null
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        if (!ok) runCatching { file.delete() }
        return ok && hasRecording()
    }

    /** Plays the last take back. [onDone] fires when it finishes or fails to start. */
    fun play(onDone: () -> Unit = {}) {
        stopPlayback()
        if (!hasRecording()) { onDone(); return }
        runCatching {
            val p = MediaPlayer()
            p.setDataSource(file.absolutePath)
            p.setOnCompletionListener { stopPlayback(); onDone() }
            p.prepare()
            p.start()
            player = p
        }.onFailure { onDone() }
    }

    fun stopPlayback() {
        val p = player ?: return
        player = null
        runCatching { p.stop() }
        runCatching { p.release() }
    }

    /** Leaving the screen: nothing should keep the mic open or the file around. */
    fun release() {
        stopRecording()
        stopPlayback()
        runCatching { file.delete() }
    }
}
