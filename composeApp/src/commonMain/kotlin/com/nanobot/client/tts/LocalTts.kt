package com.nanobot.client.tts

/**
 * Platform-independent local TTS interface.
 */
data class TtsEngineInfo(
    val id: String,
    val label: String,
)

interface LocalTtsEngine {
    val isAvailable: Boolean
    suspend fun speak(text: String, speed: Float)
    fun stop()

    /** Selects an explicit engine package, or "" for the system default. */
    fun updateEngine(engineId: String) {}
}

expect fun createLocalTtsEngine(): LocalTtsEngine

/** Installed system TTS engines (empty on desktop). */
expect fun availableTtsEngines(): List<TtsEngineInfo>

/** System default TTS engine package, or null if not set. */
expect fun systemDefaultTtsEngine(): String?
