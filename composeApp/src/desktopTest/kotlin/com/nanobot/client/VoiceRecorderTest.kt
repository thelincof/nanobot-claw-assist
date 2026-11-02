package com.nanobot.client

import com.nanobot.client.audio.AudioInput
import com.nanobot.client.audio.JvmVoiceRecorder
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class VoiceRecorderTest {
    @Test
    fun stopsAfterSilenceAndReturnsSpeech() = runBlocking {
        val recorder = JvmVoiceRecorder(SyntheticInput())
        val audio = recorder.record(silenceMs = 500, maxSeconds = 10)
        assertNotNull(audio)
        assertTrue(audio.samples.isNotEmpty())
        assertTrue(audio.durationMs >= 500)
    }
}

private class SyntheticInput : AudioInput {
    private var running = false
    private var worker: Thread? = null

    override fun start(onFrame: (ShortArray) -> Unit): Boolean {
        running = true
        worker = thread(isDaemon = true) {
            repeat(10) { if (running) onFrame(ShortArray(320)) }
            repeat(55) { if (running) onFrame(ShortArray(320) { 8_000 }) }
            repeat(60) { if (running) onFrame(ShortArray(320)) }
        }
        return true
    }

    override fun stop() {
        running = false
        worker = null
    }
}
