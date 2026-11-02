package com.nanobot.client.audio

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import com.nanobot.client.platform.AppContext
import kotlin.concurrent.thread
import kotlin.math.max

class AndroidAudioInput : AudioInput {
    private var recorder: AudioRecord? = null
    private var running = false
    private var thread: Thread? = null

    override fun start(onFrame: (ShortArray) -> Unit): Boolean {
        if (running) return false
        if (AppContext.context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val sampleRate = 16_000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return false
        val bufferSize = max(minBuffer, sampleRate / 50 * 2 * 4)
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        } catch (_: Exception) {
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }
        recorder = record
        running = true
        try {
            record.startRecording()
        } catch (_: Exception) {
            running = false
            record.release()
            recorder = null
            return false
        }
        thread = thread(name = "nanobot-audio", isDaemon = true) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val frame = ShortArray(sampleRate / 50)
            while (running) {
                val read = record.read(frame, 0, frame.size)
                if (read > 0) onFrame(frame.copyOf(read))
                else if (read < 0) break
            }
        }
        return true
    }

    override fun stop() {
        running = false
        val record = recorder
        recorder = null
        try {
            record?.stop()
        } catch (_: Exception) {
        }
        try {
            record?.release()
        } catch (_: Exception) {
        }
        thread = null
    }
}

actual fun createAudioInput(): AudioInput = AndroidAudioInput()
