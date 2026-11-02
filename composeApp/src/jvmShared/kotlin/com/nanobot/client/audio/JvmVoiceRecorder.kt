package com.nanobot.client.audio

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Client-side VAD. Keeps a short pre-roll, waits for stable speech, then stops
 * after a configurable silence interval. All PCM is 16 kHz mono.
 */
class JvmVoiceRecorder(private val input: AudioInput) : VoiceRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow(VoiceUiState())
    override val state: StateFlow<VoiceUiState> = mutableState.asStateFlow()

    private val finished = AtomicBoolean(true)
    private var deferred: CompletableDeferred<RecordedAudio?>? = null
    private var pcm = ShortArray(16_000 * 120)
    private var pcmSize = 0
    private var preRoll = ArrayDeque<ShortArray>()
    private var inSpeech = false
    private var speechFrames = 0
    private var silenceFrames = 0
    private var totalFrames = 0
    private var noiseFloorDb = -60.0
    private var startedAtNanos = 0L
    private var lastStateUpdateMs = 0L
    private var silenceMs = 900
    private var maxSeconds = 60
    private var lastDb = -90.0

    override suspend fun record(silenceMs: Int, maxSeconds: Int): RecordedAudio? {
        if (!finished.compareAndSet(true, false)) return null
        this.silenceMs = silenceMs.coerceIn(300, 5000)
        this.maxSeconds = maxSeconds.coerceIn(5, 300)
        resetBuffers()
        val result = CompletableDeferred<RecordedAudio?>()
        deferred = result
        mutableState.value = VoiceUiState(phase = VoicePhase.LISTENING)
        startedAtNanos = System.nanoTime()
        var started = input.start(::onFrame)
        if (!started) {
            // Another assistant (Bixby/Google) may still hold the microphone
            // right after the assistant gesture. Retry briefly before failing.
            delay(250)
            started = input.start(::onFrame)
        }
        if (!started) {
            finish(null, "Нет доступа к микрофону")
            return null
        }
        return withTimeoutOrNull((this.maxSeconds + 10) * 1000L) { result.await() }
            ?: run {
                cancel()
                null
            }
    }

    override fun stop() {
        if (finished.get()) return
        synchronized(this) {
            if (!inSpeech || pcmSize < 16_000 / 4) {
                finish(null, null)
            } else {
                finish(pcm.copyOf(pcmSize), null)
            }
        }
    }

    override fun cancel() {
        if (finished.get()) return
        synchronized(this) { finish(null, null) }
    }

    override fun reset() {
        if (!finished.get()) return
        mutableState.value = VoiceUiState()
    }

    private fun resetBuffers() {
        pcm = ShortArray(16_000 * 120)
        pcmSize = 0
        preRoll = ArrayDeque()
        inSpeech = false
        speechFrames = 0
        silenceFrames = 0
        totalFrames = 0
        noiseFloorDb = -60.0
        lastStateUpdateMs = 0L
        lastDb = -90.0
    }

    private fun onFrame(frame: ShortArray) {
        if (finished.get()) return
        synchronized(this) {
            if (finished.get()) return
            val nowMs = (System.nanoTime() - startedAtNanos) / 1_000_000
            totalFrames++
            val db = dbfs(frame)
            lastDb = db
            if (!inSpeech) {
                if (db < -45.0) noiseFloorDb = noiseFloorDb * 0.97 + db * 0.03
                val threshold = max(noiseFloorDb + 8.0, -42.0)
                if (db > threshold) {
                    speechFrames++
                    if (speechFrames >= 3) {
                        inSpeech = true
                        silenceFrames = 0
                        preRoll.forEach { append(it) }
                        preRoll.clear()
                        append(frame)
                        mutableState.value = VoiceUiState(
                            phase = VoicePhase.SPEECH,
                            amplitude = amplitude(db),
                            elapsedMs = nowMs,
                        )
                    }
                } else {
                    speechFrames = 0
                    preRoll.addLast(frame.copyOf())
                    while (preRoll.size > 15) preRoll.removeFirst()
                }
            } else {
                append(frame)
                if (db < max(noiseFloorDb + 6.0, -44.0)) {
                    silenceFrames++
                } else {
                    silenceFrames = 0
                }
                if (silenceFrames * 20L >= silenceMs) {
                    finish(pcm.copyOf(pcmSize), null)
                    return
                }
                if (nowMs - lastStateUpdateMs >= 50) {
                    lastStateUpdateMs = nowMs
                    mutableState.value = VoiceUiState(
                        phase = VoicePhase.SPEECH,
                        amplitude = amplitude(db),
                        elapsedMs = nowMs,
                    )
                }
            }
            if (nowMs >= maxSeconds * 1000L) {
                if (inSpeech && pcmSize >= 16_000 / 4) {
                    finish(pcm.copyOf(pcmSize), null)
                } else {
                    finish(null, null)
                }
            }
        }
    }

    private fun append(frame: ShortArray) {
        if (pcmSize + frame.size > pcm.size) {
            pcm = pcm.copyOf(max(pcm.size * 2, pcmSize + frame.size))
        }
        frame.copyInto(pcm, pcmSize)
        pcmSize += frame.size
    }

    private fun finish(samples: ShortArray?, error: String?) {
        if (!finished.compareAndSet(false, true)) return
        input.stop()
        val result = deferred
        deferred = null
        if (samples != null) {
            mutableState.value = VoiceUiState(phase = VoicePhase.PROCESSING)
            result?.complete(RecordedAudio(samples, 16_000))
        } else {
            mutableState.value = VoiceUiState(
                phase = if (error == null) VoicePhase.IDLE else VoicePhase.ERROR,
                error = error,
            )
            result?.complete(null)
        }
    }

    private fun dbfs(frame: ShortArray): Double {
        if (frame.isEmpty()) return -90.0
        var sum = 0.0
        for (sample in frame) {
            val value = sample.toDouble()
            sum += value * value
        }
        val rms = sqrt(sum / frame.size)
        return if (rms <= 0.0) -90.0 else (20.0 * log10(rms / 32768.0)).coerceIn(-90.0, 0.0)
    }

    private fun amplitude(db: Double): Float = ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
}

fun encodeWav(samples: ShortArray, sampleRate: Int): ByteArray {
    val dataSize = samples.size * 2
    val out = ByteArray(44 + dataSize)
    fun writeAscii(offset: Int, value: String) {
        value.forEachIndexed { index, c -> out[offset + index] = c.code.toByte() }
    }
    fun writeIntLE(offset: Int, value: Int) {
        out[offset] = (value and 0xff).toByte()
        out[offset + 1] = ((value ushr 8) and 0xff).toByte()
        out[offset + 2] = ((value ushr 16) and 0xff).toByte()
        out[offset + 3] = ((value ushr 24) and 0xff).toByte()
    }
    fun writeShortLE(offset: Int, value: Int) {
        out[offset] = (value and 0xff).toByte()
        out[offset + 1] = ((value ushr 8) and 0xff).toByte()
    }
    writeAscii(0, "RIFF")
    writeIntLE(4, 36 + dataSize)
    writeAscii(8, "WAVE")
    writeAscii(12, "fmt ")
    writeIntLE(16, 16)
    writeShortLE(20, 1)
    writeShortLE(22, 1)
    writeIntLE(24, sampleRate)
    writeIntLE(28, sampleRate * 2)
    writeShortLE(32, 2)
    writeShortLE(34, 16)
    writeAscii(36, "data")
    writeIntLE(40, dataSize)
    var offset = 44
    for (sample in samples) {
        out[offset] = (sample.toInt() and 0xff).toByte()
        out[offset + 1] = ((sample.toInt() ushr 8) and 0xff).toByte()
        offset += 2
    }
    return out
}
