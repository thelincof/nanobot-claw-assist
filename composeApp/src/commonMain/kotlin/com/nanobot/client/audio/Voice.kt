package com.nanobot.client.audio

import kotlinx.coroutines.flow.StateFlow

data class RecordedAudio(
    val samples: ShortArray,
    val sampleRate: Int = 16_000,
) {
    val durationMs: Long get() = if (sampleRate <= 0) 0 else samples.size * 1000L / sampleRate
}

enum class VoicePhase { IDLE, LISTENING, SPEECH, PROCESSING, ERROR }

data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.IDLE,
    val amplitude: Float = 0f,
    val elapsedMs: Long = 0,
    val error: String? = null,
) {
    val active: Boolean get() = phase == VoicePhase.LISTENING || phase == VoicePhase.SPEECH
}

interface VoiceRecorder {
    val state: StateFlow<VoiceUiState>
    suspend fun record(silenceMs: Int, maxSeconds: Int): RecordedAudio?
    fun stop()
    fun cancel()
    fun reset()
}

interface AudioInput {
    /** Starts capture and emits 16 kHz mono PCM16 frames. */
    fun start(onFrame: (ShortArray) -> Unit): Boolean
    fun stop()
}

expect fun createAudioInput(): AudioInput
expect fun createVoiceRecorder(): VoiceRecorder
