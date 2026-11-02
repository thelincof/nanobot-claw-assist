package com.nanobot.client.tts

import kotlinx.coroutines.flow.StateFlow

data class PcmAudio(
    val samples: ShortArray,
    val sampleRate: Int,
    val channels: Int = 1,
) {
    val durationMs: Long get() = if (sampleRate <= 0 || channels <= 0) 0 else samples.size * 1000L / sampleRate / channels
}

data class TtsConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val voice: String,
    val speed: Float,
) {
    val configured: Boolean get() = baseUrl.isNotBlank()
}

data class TtsUiState(
    val enabled: Boolean = false,
    val speaking: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

interface TtsController {
    val state: StateFlow<TtsUiState>
    fun setActiveChat(chatId: String?)
    fun onAssistantFinal(chatId: String, text: String, turnId: String? = null)
    fun toggle()
    fun stop()
    suspend fun test(text: String)
}

interface AudioPlayer {
    fun play(audio: PcmAudio)
    fun stop()
}

expect fun createAudioPlayer(): AudioPlayer
