package com.nanobot.client.tts

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.nanobot.client.platform.AppContext
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AndroidLocalTtsEngine : LocalTtsEngine {
    private val context: Context = AppContext.context.applicationContext
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var selectedEngine: String = ""
    @Volatile private var activeEngine: String? = null
    @Volatile private var isInit = false
    @Volatile private var ready = CompletableDeferred<Boolean>()
    @Volatile private var generation = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var lastSystemDefault: String? = systemDefaultTtsEngine()

    private val engineObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (selectedEngine.isBlank()) {
                val fresh = systemDefaultTtsEngine()
                if (fresh != lastSystemDefault) {
                    lastSystemDefault = fresh
                    reinitialize()
                }
            }
        }
    }

    init {
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.TTS_DEFAULT_SYNTH),
                false,
                engineObserver,
            )
        }
        reinitialize()
    }

    override val isAvailable: Boolean
        get() = isInit

    override fun updateEngine(engineId: String) {
        if (selectedEngine == engineId) return
        selectedEngine = engineId
        reinitialize()
    }

    override suspend fun speak(text: String, speed: Float) {
        val engine = awaitEngine() ?: error("TTS-движок не готов")
        withContext(Dispatchers.Main) {
            engine.setSpeechRate(speed)
        }
        val deferred = CompletableDeferred<Unit>()
        val utteranceId = UUID.randomUUID().toString()
        withContext(Dispatchers.Main) {
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId) deferred.complete(Unit)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId) deferred.complete(Unit)
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId) deferred.complete(Unit)
                }

                override fun onStop(id: String?, interrupted: Boolean) {
                    if (id == utteranceId) deferred.complete(Unit)
                }
            })
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result == TextToSpeech.ERROR) deferred.completeExceptionally(IllegalStateException("Не удалось запустить озвучку"))
        }
        // Safety net: never keep the controller in "speaking" forever.
        withTimeoutOrNull(maxOf(20_000L, text.length * 90L)) { deferred.await() }
    }

    override fun stop() {
        mainHandler.post { runCatching { tts?.stop() } }
    }

    private fun desiredEngine(): String? =
        selectedEngine.takeIf { it.isNotBlank() } ?: systemDefaultTtsEngine()

    private suspend fun awaitEngine(): TextToSpeech? {
        val desired = desiredEngine()
        synchronized(this) {
            val needsInit = tts == null || activeEngine != desired || (ready.isCompleted && !isInit)
            if (needsInit) reinitialize()
        }
        val ok = withTimeoutOrNull(6_000) { ready.await() } ?: false
        return if (ok) tts else null
    }

    @Synchronized
    private fun reinitialize() {
        val desired = desiredEngine()
        if (isInit && activeEngine == desired && tts != null) return
        generation += 1
        val gen = generation
        isInit = false
        ready = CompletableDeferred()
        activeEngine = desired
        val run = { createEngine(desired, gen) }
        if (Looper.myLooper() == Looper.getMainLooper()) run() else mainHandler.post(run)
    }

    private fun createEngine(desired: String?, gen: Int) {
        if (gen != generation) return
        val old = tts
        tts = null
        runCatching { old?.stop() }
        runCatching { old?.shutdown() }
        Log.d(TAG, "Initializing TTS engine: ${desired ?: "<system default>"} (default=${systemDefaultTtsEngine()})")

        val listener = TextToSpeech.OnInitListener { status ->
            if (gen != generation) return@OnInitListener
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                val result = engine.setLanguage(Locale("ru", "RU"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.getDefault())
                }
                isInit = true
                ready.complete(true)
                Log.d(TAG, "TTS ready: engine=${activeEngine ?: "<system default>"} status=$result")
            } else {
                isInit = false
                ready.complete(false)
                Log.e(TAG, "TTS init failed: status=$status engine=${desired ?: "<system default>"}")
            }
        }
        tts = if (desired != null) {
            TextToSpeech(context, listener, desired)
        } else {
            TextToSpeech(context, listener)
        }
    }

    private companion object {
        const val TAG = "LocalTts"
    }
}

actual fun createLocalTtsEngine(): LocalTtsEngine = AndroidLocalTtsEngine()

actual fun availableTtsEngines(): List<TtsEngineInfo> {
    val context = AppContext.context
    val pm = context.packageManager
    val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
    val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentServices(intent, android.content.pm.PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentServices(intent, 0)
    }
    return services.mapNotNull { info ->
        val service = info.serviceInfo ?: return@mapNotNull null
        TtsEngineInfo(
            id = service.packageName,
            label = runCatching { service.loadLabel(pm).toString() }.getOrDefault(service.packageName),
        )
    }.distinctBy { it.id }
}

actual fun systemDefaultTtsEngine(): String? =
    runCatching {
        Settings.Secure.getString(
            AppContext.context.contentResolver,
            Settings.Secure.TTS_DEFAULT_SYNTH,
        )?.takeIf { it.isNotBlank() }
    }.getOrNull()
