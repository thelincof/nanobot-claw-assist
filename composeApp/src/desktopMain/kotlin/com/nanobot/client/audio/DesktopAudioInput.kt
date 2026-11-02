package com.nanobot.client.audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread

class DesktopAudioInput : AudioInput {
    private var line: TargetDataLine? = null
    private var running = false
    private var thread: Thread? = null

    override fun start(onFrame: (ShortArray) -> Unit): Boolean {
        if (running) return false
        val opened = try {
            openLine()
        } catch (_: Exception) {
            return false
        }
        line = opened.line
        running = true
        opened.line.start()
        thread = thread(name = "nanobot-audio", isDaemon = true) {
            val bytes = ByteArray(4096)
            val samples = ShortArray(bytes.size / 2)
            while (running) {
                val read = opened.line.read(bytes, 0, bytes.size)
                if (read <= 0) continue
                val count = read / 2
                var index = 0
                while (index < count) {
                    val lo = bytes[index * 2].toInt() and 0xff
                    val hi = bytes[index * 2 + 1].toInt()
                    samples[index] = ((hi shl 8) or lo).toShort()
                    index++
                }
                val frame = opened.resampler?.process(samples, count) ?: samples.copyOf(count)
                if (frame.isNotEmpty()) onFrame(frame)
            }
        }
        return true
    }

    private fun openLine(): OpenedLine {
        for (rate in intArrayOf(16_000, 48_000, 44_100)) {
            val format = AudioFormat(rate.toFloat(), 16, 1, true, false)
            try {
                val info = DataLine.Info(TargetDataLine::class.java, format)
                if (!AudioSystem.isLineSupported(info)) continue
                val line = AudioSystem.getLine(info) as TargetDataLine
                line.open(format, rate / 10 * 2)
                val resampler = if (rate == 16_000) null else AverageResampler(rate, 16_000)
                return OpenedLine(line, resampler)
            } catch (_: Exception) {
            }
        }
        throw IllegalStateException("microphone is unavailable")
    }

    override fun stop() {
        running = false
        try {
            line?.stop()
        } catch (_: Exception) {
        }
        try {
            line?.close()
        } catch (_: Exception) {
        }
        line = null
        thread = null
    }

    private data class OpenedLine(val line: TargetDataLine, val resampler: AverageResampler?)
}

private class AverageResampler(private val sourceRate: Int, private val targetRate: Int) {
    private var accumulator = 0.0
    private var sum = 0.0
    private var count = 0
    private val step = sourceRate.toDouble() / targetRate.toDouble()

    fun process(input: ShortArray, length: Int): ShortArray {
        val output = ShortArray((length * targetRate / sourceRate) + 2)
        var outIndex = 0
        for (index in 0 until length) {
            sum += input[index].toDouble()
            count++
            accumulator += 1.0
            if (accumulator >= step) {
                output[outIndex++] = (sum / count).toInt().toShort()
                sum = 0.0
                count = 0
                accumulator -= step
            }
        }
        return output.copyOf(outIndex)
    }
}

actual fun createAudioInput(): AudioInput = DesktopAudioInput()
