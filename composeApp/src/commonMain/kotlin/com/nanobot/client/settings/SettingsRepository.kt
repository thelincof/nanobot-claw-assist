package com.nanobot.client.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class AppSettings(
    val baseUrl: String = "",
    val secret: String = "",
    val ttsEnabled: Boolean = false,
    val ttsMode: String = "local", // "local" or "http"
    val ttsBaseUrl: String = "",
    val ttsApiKey: String = "",
    val ttsModel: String = "tts-1",
    val ttsVoice: String = "alloy",
    val ttsEngine: String = "", // "" = system default TTS engine
    val ttsSpeed: Float = 1.0f,
    val sttMode: String = "local", // "local" (Vosk) or "gateway"
    val sttSilenceMs: Int = 900,
    val sttMaxSeconds: Int = 60,
    val chatFontSize: Float = 15f,
    val themeMode: String = "system", // "system" | "light" | "dark"
    val workspaces: List<WorkspaceEntry> = emptyList(),
    val activeWorkspacePath: String = "", // "" = default gateway workspace
    val lastSessionKey: String? = null,
    // Digital assistant invocation
    val assistantChatMode: String = "new", // "new" | "specific"
    val assistantSessionKey: String? = null,
    val assistantAutoListen: Boolean = true,
    val hotwordEnabled: Boolean = false,
    val hotwordPhrase: String = "клод",
)

@Serializable
data class WorkspaceEntry(
    val name: String,
    val path: String,
)

class SettingsRepository(private val settings: Settings) {
    private val mutableState = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = mutableState.asStateFlow()

    private fun read(): AppSettings = AppSettings(
        baseUrl = settings.getString(KEY_BASE_URL, ""),
        secret = settings.getString(KEY_SECRET, ""),
        ttsEnabled = settings.getBoolean(KEY_TTS_ENABLED, false),
        ttsMode = settings.getString(KEY_TTS_MODE, "local"),
        ttsBaseUrl = settings.getString(KEY_TTS_BASE_URL, ""),
        ttsApiKey = settings.getString(KEY_TTS_API_KEY, ""),
        ttsModel = settings.getString(KEY_TTS_MODEL, "tts-1"),
        ttsVoice = settings.getString(KEY_TTS_VOICE, "alloy"),
        ttsEngine = settings.getString(KEY_TTS_ENGINE, ""),
        ttsSpeed = settings.getFloat(KEY_TTS_SPEED, 1.0f).coerceIn(0.25f, 4.0f),
        sttMode = settings.getString(KEY_STT_MODE, "local"),
        sttSilenceMs = settings.getInt(KEY_STT_SILENCE_MS, 900).coerceIn(300, 5000),
        sttMaxSeconds = settings.getInt(KEY_STT_MAX_SECONDS, 60).coerceIn(5, 300),
        chatFontSize = settings.getFloat(KEY_CHAT_FONT_SIZE, 15f).coerceIn(12f, 22f),
        themeMode = normalizeThemeMode(settings.getString(KEY_THEME_MODE, "system")),
        workspaces = runCatching {
            workspaceJson.decodeFromString(ListSerializer(WorkspaceEntry.serializer()), settings.getString(KEY_WORKSPACES, "[]"))
        }.getOrDefault(emptyList()),
        activeWorkspacePath = settings.getString(KEY_ACTIVE_WORKSPACE, ""),
        lastSessionKey = settings.getStringOrNull(KEY_LAST_SESSION),
        assistantChatMode = settings.getString(KEY_ASSISTANT_CHAT_MODE, "new").let {
            if (it == "specific") "specific" else "new"
        },
        assistantSessionKey = settings.getStringOrNull(KEY_ASSISTANT_SESSION),
        assistantAutoListen = settings.getBoolean(KEY_ASSISTANT_AUTO_LISTEN, true),
        hotwordEnabled = settings.getBoolean(KEY_HOTWORD_ENABLED, false),
        hotwordPhrase = settings.getString(KEY_HOTWORD_PHRASE, "клод"),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(mutableState.value)
        settings.putString(KEY_BASE_URL, next.baseUrl)
        settings.putString(KEY_SECRET, next.secret)
        settings.putBoolean(KEY_TTS_ENABLED, next.ttsEnabled)
        settings.putString(KEY_TTS_MODE, next.ttsMode)
        settings.putString(KEY_TTS_BASE_URL, next.ttsBaseUrl)
        settings.putString(KEY_TTS_API_KEY, next.ttsApiKey)
        settings.putString(KEY_TTS_MODEL, next.ttsModel)
        settings.putString(KEY_TTS_VOICE, next.ttsVoice)
        settings.putString(KEY_TTS_ENGINE, next.ttsEngine)
        settings.putFloat(KEY_TTS_SPEED, next.ttsSpeed)
        settings.putString(KEY_STT_MODE, next.sttMode)
        settings.putInt(KEY_STT_SILENCE_MS, next.sttSilenceMs)
        settings.putInt(KEY_STT_MAX_SECONDS, next.sttMaxSeconds)
        settings.putFloat(KEY_CHAT_FONT_SIZE, next.chatFontSize)
        settings.putString(KEY_THEME_MODE, normalizeThemeMode(next.themeMode))
        settings.putString(KEY_WORKSPACES, workspaceJson.encodeToString(ListSerializer(WorkspaceEntry.serializer()), next.workspaces))
        settings.putString(KEY_ACTIVE_WORKSPACE, next.activeWorkspacePath)
        if (next.lastSessionKey == null) settings.remove(KEY_LAST_SESSION)
        else settings.putString(KEY_LAST_SESSION, next.lastSessionKey)
        settings.putString(KEY_ASSISTANT_CHAT_MODE, next.assistantChatMode)
        if (next.assistantSessionKey == null) settings.remove(KEY_ASSISTANT_SESSION)
        else settings.putString(KEY_ASSISTANT_SESSION, next.assistantSessionKey)
        settings.putBoolean(KEY_ASSISTANT_AUTO_LISTEN, next.assistantAutoListen)
        settings.putBoolean(KEY_HOTWORD_ENABLED, next.hotwordEnabled)
        settings.putString(KEY_HOTWORD_PHRASE, next.hotwordPhrase)
        mutableState.value = next
    }

    fun setLastSession(key: String?) = update { it.copy(lastSessionKey = key) }

    private companion object {
        val workspaceJson = Json { ignoreUnknownKeys = true }
        const val KEY_BASE_URL = "gateway.baseUrl"
        const val KEY_SECRET = "gateway.secret"
        const val KEY_THEME_MODE = "ui.themeMode"
        const val KEY_TTS_ENABLED = "tts.enabled"
        const val KEY_TTS_MODE = "tts.mode"
        const val KEY_TTS_BASE_URL = "tts.baseUrl"
        const val KEY_TTS_API_KEY = "tts.apiKey"
        const val KEY_TTS_MODEL = "tts.model"
        const val KEY_TTS_VOICE = "tts.voice"
        const val KEY_TTS_ENGINE = "tts.engine"
        const val KEY_TTS_SPEED = "tts.speed"
        const val KEY_STT_MODE = "stt.mode"
        const val KEY_STT_SILENCE_MS = "stt.silenceMs"
        const val KEY_STT_MAX_SECONDS = "stt.maxSeconds"
        const val KEY_CHAT_FONT_SIZE = "chat.fontSize"
        const val KEY_WORKSPACES = "workspace.entries"
        const val KEY_ACTIVE_WORKSPACE = "workspace.active"
        const val KEY_LAST_SESSION = "chat.lastSessionKey"
        const val KEY_ASSISTANT_CHAT_MODE = "assistant.chatMode"
        const val KEY_ASSISTANT_SESSION = "assistant.sessionKey"
        const val KEY_ASSISTANT_AUTO_LISTEN = "assistant.autoListen"
        const val KEY_HOTWORD_ENABLED = "assistant.hotwordEnabled"
        const val KEY_HOTWORD_PHRASE = "assistant.hotwordPhrase"
    }
}

private fun normalizeThemeMode(value: String): String = when (value) {
    "light", "dark" -> value
    else -> "system"
}
