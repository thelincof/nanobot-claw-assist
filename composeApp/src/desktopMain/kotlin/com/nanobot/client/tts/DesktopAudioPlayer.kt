package com.nanobot.client.tts

import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.min

class DesktopAudioPlayer : AudioPlayer {
    @Volatile private var line: SourceDataLine? = null
    private val generation = AtomicInteger(0)

    override fun play(audio: PcmAudio) {
        if (audio.samples.isEmpty() || audio.sampleRate <= 0) return
        val myGeneration = generation.incrementAndGet()
        val format = AudioFormat(audio.sampleRate.toFloat(), 16, audio.channels, true, false)
        val info = DataLine.Info(SourceDataLine::class.java, format)
        val output = try {
            AudioSystem.getLine(info) as SourceDataLine
        } catch (_: Exception) {
            return
        }
        line = output
        try {
            output.open(format, audio.sampleRate / 5 * 2 * audio.channels)
            output.start()
            val bytes = ByteArray(audio.samples.size * 2)
            var index = 0
            for (sample in audio.samples) {
                bytes[index] = (sample.toInt() and 0xff).toByte()
                bytes[index + 1] = ((sample.toInt() ushr 8) and 0xff).toByte()
                index += 2
            }
            var offset = 0
            while (offset < bytes.size && myGeneration == generation.get()) {
                val written = output.write(bytes, offset, min(4096, bytes.size - offset))
                if (written <= 0) break
                offset += written
            }
            if (myGeneration == generation.get()) output.drain()
        } catch (_: Exception) {
        } finally {
            try {
                output.stop()
            } catch (_: Exception) {
            }
            try {
                output.close()
            } catch (_: Exception) {
            }
            if (line === output) line = null
        }
    }

    override fun stop() {
        generation.incrementAndGet()
        val output = line
        try {
            output?.stop()
        } catch (_: Exception) {
        }
        try {
            output?.flush()
        } catch (_: Exception) {
        }
    }
}

actual fun createAudioPlayer(): AudioPlayer = DesktopAudioPlayer()
