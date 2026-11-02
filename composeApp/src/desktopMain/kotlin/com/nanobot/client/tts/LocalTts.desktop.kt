package com.nanobot.client.tts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DesktopLocalTtsEngine : LocalTtsEngine {
    private var currentProcess: Process? = null

    override val isAvailable: Boolean = true

    override suspend fun speak(text: String, speed: Float) = withContext(Dispatchers.IO) {
        stop()
        val speechDispatcher = File("/usr/bin/spd-say")
        val espeak = File("/usr/bin/espeak")
        val espeakNg = File("/usr/bin/espeak-ng")

        val rate = ((speed - 1.0f) * 100).toInt().coerceIn(-100, 100)
        val proc = when {
            speechDispatcher.canExecute() -> {
                ProcessBuilder("spd-say", "-l", "ru", "-r", "$rate", text).start()
            }
            espeakNg.canExecute() -> {
                ProcessBuilder("espeak-ng", "-v", "ru", "-s", "${(160 * speed).toInt()}", text).start()
            }
            espeak.canExecute() -> {
                ProcessBuilder("espeak", "-v", "ru", "-s", "${(160 * speed).toInt()}", text).start()
            }
            else -> null
        }
        currentProcess = proc
        proc?.waitFor()
        return@withContext
    }

    override fun stop() {
        try {
            currentProcess?.destroyForcibly()
            currentProcess = null
        } catch (_: Exception) {}
    }
}

actual fun createLocalTtsEngine(): LocalTtsEngine = DesktopLocalTtsEngine()

actual fun availableTtsEngines(): List<TtsEngineInfo> = emptyList()

actual fun systemDefaultTtsEngine(): String? = null
