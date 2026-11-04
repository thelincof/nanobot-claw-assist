package com.nanobot.client.hotword

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.nanobot.client.audio.AudioInput
import com.nanobot.client.audio.VoskModelProvider
import com.nanobot.client.audio.createAudioInput
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

private const val TAG = "ClawdHotword"
private const val SAMPLE_RATE = 16_000
private const val CHECK_INTERVAL_NS = 150_000_000L

@Composable
actual fun HotwordEffect(
    enabled: Boolean,
    phrase: String,
    active: Boolean,
    onDetected: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnDetected by rememberUpdatedState(onDetected)
    val normalizedPhrase = normalizeHotwordPhrase(phrase)

    LaunchedEffect(enabled, normalizedPhrase, active, lifecycleOwner) {
        if (!enabled || !active || normalizedPhrase.isEmpty()) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                val detector = AndroidHotwordDetector(context, normalizedPhrase) {
                    latestOnDetected()
                }
                if (!detector.start()) {
                    // Microphone may be temporarily busy (call/recording). Retry
                    // while the screen is still in front.
                    delay(2_000)
                    continue
                }
                try {
                    detector.detected.await()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } finally {
                    detector.stop()
                }
                // Avoid immediately retriggering from the tail of the phrase or
                // from the assistant's own startup sounds.
                delay(1_500)
            }
        }
    }
}

actual fun hotwordSupported(): Boolean = true

private class AndroidHotwordDetector(
    private val context: Context,
    private val phrase: String,
    private val onDetected: () -> Unit,
) {
    private val detection = CompletableDeferred<Unit>()
    val detected: Deferred<Unit> = detection

    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val triggered = AtomicBoolean(false)

    @Volatile
    private var running = false

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var input: AudioInput? = null
    private var lastCheckNs = 0L

    suspend fun start(): Boolean {
        if (running) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val modelDir = VoskModelProvider.getModelDir() ?: return false
        val loadedModel = withContext(Dispatchers.IO) {
            runCatching { Model(modelDir.absolutePath) }.getOrNull()
        } ?: return false
        val loadedRecognizer = runCatching {
            Recognizer(loadedModel, SAMPLE_RATE.toFloat(), buildVoskGrammar(phrase))
        }.getOrNull()
        if (loadedRecognizer == null) {
            loadedModel.close()
            return false
        }

        val audio = createAudioInput()
        synchronized(this) {
            model = loadedModel
            recognizer = loadedRecognizer
            input = audio
            lastCheckNs = 0L
            triggered.set(false)
            running = true
        }
        if (!audio.start(::onFrame)) {
            stop()
            return false
        }
        return true
    }

    private fun onFrame(frame: ShortArray) {
        if (!running) return
        val activeRecognizer = recognizer ?: return
        try {
            val final = activeRecognizer.acceptWaveForm(frame, frame.size)
            val now = System.nanoTime()
            if (final) {
                lastCheckNs = now
                checkText(activeRecognizer.result)
            } else if (now - lastCheckNs >= CHECK_INTERVAL_NS) {
                lastCheckNs = now
                checkText(activeRecognizer.partialResult)
            }
        } catch (error: Exception) {
            Log.w(TAG, "wake-word recognition failed", error)
            stop()
        }
    }

    private fun checkText(json: String) {
        val text = runCatching {
            val obj = JSONObject(json)
            obj.optString("partial").ifBlank { obj.optString("text") }
        }.getOrDefault("")
        if (text.isNotBlank() && transcriptMatchesPhrase(text, phrase)) {
            trigger()
        }
    }

    private fun trigger() {
        if (!triggered.compareAndSet(false, true)) return
        running = false
        cleanupScope.launch {
            stopAudioAndRecognizer()
            withContext(Dispatchers.Main) {
                playChime()
                delay(220)
                runCatching { onDetected() }
                detection.complete(Unit)
            }
        }
    }

    fun stop() {
        running = false
        triggered.set(true)
        cleanupScope.launch { stopAudioAndRecognizer() }
    }

    private fun stopAudioAndRecognizer() {
        val audio: AudioInput?
        val currentRecognizer: Recognizer?
        val currentModel: Model?
        synchronized(this) {
            audio = input
            currentRecognizer = recognizer
            currentModel = model
            input = null
            recognizer = null
            model = null
        }
        runCatching { audio?.stop() }
        // Let the capture thread leave acceptWaveForm before releasing the
        // native recognizer/model.
        Thread.sleep(120)
        runCatching { currentRecognizer?.close() }
        runCatching { currentModel?.close() }
    }

    private fun playChime() {
        val tone = runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, 65)
        }.getOrNull() ?: return
        runCatching { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { tone.release() } }, 400)
    }
}
