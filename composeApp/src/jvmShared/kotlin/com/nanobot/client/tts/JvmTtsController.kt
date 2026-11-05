package com.nanobot.client.tts

import com.nanobot.client.core.gateway.logD
import com.nanobot.client.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class JvmTtsController(
    private val settings: SettingsRepository,
    client: OkHttpClient,
    private val player: AudioPlayer,
    private val localEngine: LocalTtsEngine = createLocalTtsEngine(),
) : TtsController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api = OpenAiTtsClient(client)
    private val mutableState = MutableStateFlow(
        TtsUiState(enabled = settings.state.value.ttsEnabled),
    )
    override val state: StateFlow<TtsUiState> = mutableState.asStateFlow()

    private var activeChatId: String? = null
    private var job: Job? = null
    private val spokenTurns = mutableSetOf<String>()
    private var lastSpokenChatId: String? = null
    private var lastSpokenText: String = ""
    private var lastSpokenAt: Long = 0

    init {
        scope.launch {
            settings.state
                .map { it.ttsEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    mutableState.value = mutableState.value.copy(enabled = enabled)
                    if (!enabled) stop()
                }
        }
        scope.launch {
            settings.state
                .map { it.ttsEngine }
                .distinctUntilChanged()
                .collect { engine -> localEngine.updateEngine(engine) }
        }
    }

    override fun setActiveChat(chatId: String?) {
        if (activeChatId != chatId) {
            activeChatId = chatId
            stop()
        }
    }

    override fun onAssistantFinal(chatId: String, text: String, turnId: String?) {
        val enabled = settings.state.value.ttsEnabled && mutableState.value.enabled
        logD("tts: final enabled=$enabled chat=$chatId turn=$turnId chars=${text.length}")
        if (!enabled) return
        if (activeChatId != chatId) return

        val turnKey = turnId?.let { "$chatId:$it" }
        if (turnKey != null && !spokenTurns.add(turnKey)) {
            logD("tts: skip duplicate turn=$turnKey")
            return
        }
        if (spokenTurns.size > 200) spokenTurns.clear()

        val now = System.currentTimeMillis()
        // Some gateways omit turn_id or send a second final event with a new id.
        // Suppress the same text in the same chat within a short window.
        if (
            text == lastSpokenText &&
            chatId == lastSpokenChatId &&
            now - lastSpokenAt < 15_000
        ) {
            logD("tts: skip duplicate text chat=$chatId")
            return
        }
        lastSpokenChatId = chatId
        lastSpokenText = text
        lastSpokenAt = now
        speak(text)
    }

    override fun setEnabled(enabled: Boolean) {
        logD("tts: setEnabled=$enabled")
        settings.update { it.copy(ttsEnabled = enabled) }
        mutableState.value = mutableState.value.copy(enabled = enabled)
        if (!enabled) stop()
    }

    override fun toggle() {
        setEnabled(!settings.state.value.ttsEnabled)
    }

    override fun stop() {
        logD("tts: stop")
        job?.cancel()
        job = null
        player.stop()
        localEngine.stop()
        mutableState.value = mutableState.value.copy(speaking = false, loading = false)
    }

    override suspend fun test(text: String) {
        speak(text, force = true)
    }

    private fun speak(text: String, force: Boolean = false) {
        if (!force && (!settings.state.value.ttsEnabled || !mutableState.value.enabled)) {
            logD("tts: speak blocked (disabled)")
            return
        }
        val currentSettings = settings.state.value
        val isLocalMode = currentSettings.ttsMode == "local"
        logD("tts: speak force=$force mode=${currentSettings.ttsMode} chars=${text.length}")

        val config = TtsConfig(
            baseUrl = currentSettings.ttsBaseUrl,
            apiKey = currentSettings.ttsApiKey,
            model = currentSettings.ttsModel,
            voice = currentSettings.ttsVoice,
            speed = currentSettings.ttsSpeed,
        )

        if (!isLocalMode && !force && !config.configured) {
            mutableState.value = mutableState.value.copy(
                error = "TTS API URL не настроен",
                speaking = false,
                loading = false,
            )
            return
        }

        val speechText = text.trim()
        if (speechText.isBlank()) return
        job?.cancel()
        player.stop()
        localEngine.stop()

        job = scope.launch {
            mutableState.value = mutableState.value.copy(
                speaking = false,
                loading = true,
                error = null,
            )
            try {
                if (isLocalMode) {
                    // Apply the selected engine immediately (settings save happens
                    // just before the test button).
                    localEngine.updateEngine(currentSettings.ttsEngine)
                    mutableState.value = mutableState.value.copy(loading = false, speaking = true)
                    localEngine.speak(speechText, currentSettings.ttsSpeed)
                    mutableState.value = mutableState.value.copy(speaking = false, loading = false)
                } else {
                    val chunks = chunkForSpeech(speechText)
                    for ((index, chunk) in chunks.withIndex()) {
                        val audio = api.synthesize(chunk, config)
                        if (!isActive) return@launch
                        mutableState.value = mutableState.value.copy(loading = false, speaking = true)
                        withContext(Dispatchers.IO) { player.play(audio) }
                        if (index == chunks.lastIndex) {
                            mutableState.value = mutableState.value.copy(speaking = false, loading = false)
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    speaking = false,
                    loading = false,
                    error = error.message ?: "Ошибка TTS",
                )
            } finally {
                if (job == coroutineContext[Job]) job = null
            }
        }
    }
}

fun chunkForSpeech(text: String, maxChars: Int = 900): List<String> {
    if (text.length <= maxChars) return listOf(text)
    val sentences = text.split(Regex("(?<=[.!?…])\\s+"))
    val chunks = mutableListOf<String>()
    val current = StringBuilder()
    for (sentence in sentences) {
        if (sentence.length > maxChars) {
            if (current.isNotEmpty()) {
                chunks += current.toString()
                current.clear()
            }
            sentence.split(Regex("\\s+")).forEach { word ->
                if (current.length + word.length + 1 > maxChars && current.isNotEmpty()) {
                    chunks += current.toString()
                    current.clear()
                }
                if (current.isNotEmpty()) current.append(" ")
                current.append(word)
            }
        } else if (current.length + sentence.length + 1 > maxChars) {
            chunks += current.toString()
            current.clear()
            current.append(sentence)
        } else {
            if (current.isNotEmpty()) current.append(" ")
            current.append(sentence)
        }
    }
    if (current.isNotEmpty()) chunks += current.toString()
    return chunks
}
