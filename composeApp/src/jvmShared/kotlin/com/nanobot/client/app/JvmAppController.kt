package com.nanobot.client.app

import com.nanobot.client.audio.RecordedAudio
import com.nanobot.client.audio.VoicePhase
import com.nanobot.client.audio.VoiceRecorder
import com.nanobot.client.audio.VoskSttRecognizer
import com.nanobot.client.audio.cleanAsrText
import com.nanobot.client.audio.createVoiceRecorder
import com.nanobot.client.audio.encodeWav
import com.nanobot.client.core.gateway.ConnectionState
import com.nanobot.client.core.gateway.GatewayException
import com.nanobot.client.core.gateway.GatewaySession
import com.nanobot.client.core.gateway.logD
import com.nanobot.client.core.model.AutomationJob
import com.nanobot.client.core.model.DeltaEvent
import com.nanobot.client.core.model.FileEditEvent
import com.nanobot.client.core.model.InboundEvent
import com.nanobot.client.core.model.LocalAttachment
import com.nanobot.client.core.model.MessageEvent
import com.nanobot.client.core.model.OutboundAttachment
import com.nanobot.client.core.model.ModelPresetItem
import com.nanobot.client.core.model.PrettyJson
import com.nanobot.client.core.model.ProviderItem
import com.nanobot.client.core.model.ReasoningDeltaEvent
import com.nanobot.client.core.model.ReasoningEndEvent
import com.nanobot.client.core.model.SessionRow
import com.nanobot.client.core.model.StreamEndEvent
import com.nanobot.client.core.model.ToolProgressEvent
import com.nanobot.client.core.model.TurnEndEvent
import com.nanobot.client.core.model.UiFileEdit
import com.nanobot.client.core.model.UiMessage
import com.nanobot.client.platform.notifyTaskCompleted
import com.nanobot.client.settings.AppSettings
import com.nanobot.client.settings.SettingsRepository
import com.nanobot.client.settings.createSettings
import com.nanobot.client.tts.JvmTtsController
import com.nanobot.client.tts.TtsController
import com.nanobot.client.tts.createAudioPlayer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

class JvmAppController(
    private val settings: SettingsRepository = SettingsRepository(createSettings()),
    private val client: OkHttpClient = defaultHttpClient(),
    private val gateway: GatewaySession = GatewaySession(settings, client),
    private val tts: TtsController = JvmTtsController(settings, client, createAudioPlayer()),
    private val recorder: VoiceRecorder = createVoiceRecorder(),
) : AppController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow(AppState(settings = settings.state.value))
    override val state: StateFlow<AppState> = mutableState.asStateFlow()

    private var started = false
    private var currentChatId: String? = null
    private var canonical: List<UiMessage> = emptyList()
    private val transient = linkedMapOf<String, UiMessage>()
    private val optimistic = linkedMapOf<String, UiMessage>()
    private val completedTurns = mutableSetOf<String>()
    private var loadGeneration = 0
    private var sessionsJob: Job? = null
    private var voiceJob: Job? = null
    private var lastTtsError: String? = null
    private var lastTurnEventAt: Long = 0
    private var staleStrikes = 0
    private var assistantPending = false

    init {
        scope.launch {
            settings.state.collect { value -> update { it.copy(settings = value) } }
        }
        scope.launch {
            tts.state.collect { value ->
                val freshError = value.error?.takeIf { it != lastTtsError }
                lastTtsError = value.error
                update { it.copy(tts = value, error = freshError ?: it.error) }
            }
        }
        scope.launch {
            recorder.state.collect { value -> update { it.copy(voice = value) } }
        }
        scope.launch {
            gateway.connection.collect { value ->
                val mapped = mapConnection(value)
                val wasConnected = mutableState.value.connection.phase == ConnectionPhase.CONNECTED
                update { current ->
                    current.copy(
                        connection = mapped,
                        error = if (mapped.phase == ConnectionPhase.FAILED) mapped.error else current.error,
                        assistantLaunching = if (mapped.phase == ConnectionPhase.FAILED) false else current.assistantLaunching,
                    )
                }
                if (mapped.phase == ConnectionPhase.FAILED) assistantPending = false
                if (mapped.phase == ConnectionPhase.CONNECTED && !wasConnected) {
                    refreshSessions()
                    loadWorkspacesInfo()
                    refreshGatewaySettings()
                    loadGatewayConfig()
                    refreshMcpPresets()
                    loadSidebarState()
                    if (assistantPending) {
                        assistantPending = false
                        scope.launch { performAssistantLaunch() }
                    }
                }
            }
        }
        scope.launch {
            gateway.transportState.collect { value ->
                update { it.copy(wsConnected = value.status == com.nanobot.client.core.transport.TransportStatus.OPEN) }
            }
        }
        scope.launch {
            gateway.events.collect { event -> handleEvent(event) }
        }
        scope.launch {
            // Keep sidebar activity (run_started_at) fresh even for chats that are
            // not currently attached to this WebSocket.
            while (true) {
                delay(15_000)
                if (mutableState.value.connection.phase == ConnectionPhase.CONNECTED) {
                    refreshSessions(silent = true)
                }
            }
        }
        scope.launch {
            // Watchdog: если активный turn не подаёт признаков жизни (например,
            // соединение оборвалось на время генерации и события потеряны),
            // перепроверяем состояние у сервера, а затем сбрасываем зависший спиннер.
            while (true) {
                delay(5_000)
                val current = mutableState.value
                val active = current.activeTurnId
                val awaiting = current.awaitingFirstToken
                if ((active == null && !awaiting) || current.connection.phase != ConnectionPhase.CONNECTED) {
                    staleStrikes = 0
                    continue
                }
                if (System.currentTimeMillis() - lastTurnEventAt < 15_000) {
                    staleStrikes = 0
                    continue
                }
                val chatId = currentChatId ?: continue
                val session = current.selectedSession ?: continue
                staleStrikes += 1
                if (staleStrikes == 1) {
                    // Возможен stale activeTurnId: перечитываем поток у сервера.
                    loadThread(chatId, session.key)
                } else if (staleStrikes >= 2) {
                    // Сервер не подтверждает активность: считаем turn прерванным.
                    staleStrikes = 0
                    lastTurnEventAt = 0
                    clearStreamingFlags()
                    update {
                        it.copy(
                            activeTurnId = null,
                            stopping = false,
                            sending = false,
                            awaitingFirstToken = false,
                            messages = project(),
                        )
                    }
                }
            }
        }
    }

    override fun start() {
        if (started) return
        started = true
        val saved = settings.state.value
        if (saved.baseUrl.isNotBlank() && saved.secret.isNotBlank()) {
            connect(saved.baseUrl, saved.secret)
        }
    }

    override fun connect(baseUrl: String, secret: String) {
        if (baseUrl.isBlank() || secret.isBlank()) {
            update { it.copy(error = "Заполните URL gateway и Bootstrap Secret", connection = ConnectionUi(ConnectionPhase.FAILED, "Заполните URL и Secret")) }
            return
        }
        update { it.copy(connection = ConnectionUi(ConnectionPhase.CONNECTING), error = null) }
        scope.launch {
            val result = gateway.connect(baseUrl, secret)
            if (result.isFailure) {
                val message = result.exceptionOrNull()?.message ?: "Ошибка подключения"
                update { it.copy(connection = ConnectionUi(ConnectionPhase.FAILED, message), error = message) }
            }
        }
    }

    override fun disconnect() {
        gateway.disconnect()
        clearChatState()
        assistantPending = false
        update {
            it.copy(
                connection = ConnectionUi(ConnectionPhase.IDLE),
                sessions = emptyList(),
                sessionsLoading = false,
                sessionsError = null,
                error = null,
                wsConnected = false,
            )
        }
    }

    override fun refreshSessions() = refreshSessions(silent = false)

    private fun refreshSessions(silent: Boolean = false) {
        if (mutableState.value.connection.phase != ConnectionPhase.CONNECTED) return
        sessionsJob?.cancel()
        sessionsJob = scope.launch {
            update {
                it.copy(
                    sessionsLoading = if (silent) it.sessionsLoading else true,
                    sessionsError = if (silent) it.sessionsError else null,
                )
            }
            try {
                val payload = gateway.api().sessions()
                val current = mutableState.value.selectedSession
                val merged = if (current != null && payload.sessions.none { it.key == current.key }) {
                    listOf(current) + payload.sessions
                } else {
                    payload.sessions
                }
                update { it.copy(sessions = merged, sessionsLoading = false) }
                val selected = mutableState.value.selectedSession
                if (selected != null) {
                    val fresh = merged.firstOrNull { it.key == selected.key }
                    if (fresh != null) update { it.copy(selectedSession = fresh) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (silent) return@launch
                update { it.copy(sessionsLoading = false, sessionsError = error.message ?: "Не удалось загрузить чаты") }
            }
        }
    }

    override fun setSearch(query: String) {
        update { it.copy(search = query) }
    }

    override fun setActiveWorkspace(path: String) {
        settings.update { it.copy(activeWorkspacePath = path) }
        // Apply the scope to the currently open chat right away.
        val chatId = currentChatId
        if (chatId != null) {
            try {
                gateway.transport().setWorkspaceScope(chatId, workspaceScope())
            } catch (_: IllegalStateException) {
                // transport not open — scope will be applied on the next message
            }
        }
    }

    override fun addWorkspace(name: String, path: String) {
        val cleanPath = path.trim().trimEnd('/')
        if (!cleanPath.startsWith("/") || cleanPath.length < 2) return
        val entry = com.nanobot.client.settings.WorkspaceEntry(
            name = name.trim().ifBlank { cleanPath.substringAfterLast('/') },
            path = cleanPath,
        )
        settings.update {
            it.copy(workspaces = it.workspaces.filterNot { w -> w.path == entry.path } + entry)
        }
    }

    override fun removeWorkspace(path: String) {
        settings.update {
            it.copy(
                workspaces = it.workspaces.filterNot { w -> w.path == path },
                activeWorkspacePath = if (it.activeWorkspacePath == path) "" else it.activeWorkspacePath,
            )
        }
    }

    override fun pickWorkspaceFolder(onResult: (String?) -> Unit) {
        scope.launch {
            val result = runCatching {
                gateway.transport().webUiRequest(
                    action = "workspace.pick_folder",
                    payload = buildJsonObject { },
                )
            }
            onResult(
                result.getOrNull()
                    ?.result
                    ?.get("path")
                    ?.let { (it as? JsonPrimitive)?.content },
            )
        }
    }

    override fun renameSession(session: SessionRow, title: String) {
        scope.launch {
            try {
                // Sidebar-state rename: read → patch title_overrides → write.
                val state = gateway.api().sidebarStateRaw()
                val overrides = (state["title_overrides"] as? JsonObject) ?: JsonObject(emptyMap())
                val newOverrides = JsonObject(
                    overrides + mapOf(session.key to JsonPrimitive(title)),
                )
                gateway.transport().webUiRequest(
                    action = "sidebar.update",
                    payload = buildJsonObject {
                        put("state", JsonObject(state + mapOf("title_overrides" to newOverrides)))
                    },
                )
            } catch (_: Exception) {
                update { it.copy(error = "Не удалось переименовать чат") }
                return@launch
            }
            update {
                it.copy(sessions = it.sessions.map { row ->
                    if (row.key == session.key) row.copy(title = title) else row
                })
            }
            if (mutableState.value.selectedSession?.key == session.key) {
                update { it.copy(selectedSession = it.selectedSession?.copy(title = title)) }
            }
        }
    }

    override fun setShowAllChats(all: Boolean) {
        update { it.copy(showAllChats = all) }
    }

    override fun setShowArchived(show: Boolean) {
        update { it.copy(showArchived = show) }
    }

    private fun loadSidebarState() {
        scope.launch {
            val raw = runCatching { gateway.api().sidebarStateRaw() }.getOrNull() ?: return@launch
            update {
                it.copy(
                    pinnedKeys = raw.stringSet("pinned_keys"),
                    archivedKeys = raw.stringSet("archived_keys"),
                )
            }
        }
    }

    override fun togglePin(session: SessionRow) {
        scope.launch {
            try {
                val raw = gateway.api().sidebarStateRaw()
                val pinned = raw.stringSet("pinned_keys").toMutableSet()
                val nowPinned = !pinned.remove(session.key)
                if (nowPinned) pinned.add(session.key)
                val updated = JsonObject(
                    raw + ("pinned_keys" to JsonArray(pinned.map { JsonPrimitive(it) })),
                )
                gateway.transport().webUiRequest(
                    "sidebar.update",
                    buildJsonObject { put("state", updated) },
                )
                update { it.copy(pinnedKeys = pinned) }
            } catch (error: Exception) {
                update { it.copy(error = error.message ?: "Не удалось изменить закрепление") }
            }
        }
    }

    override fun toggleArchive(session: SessionRow) {
        scope.launch {
            try {
                val raw = gateway.api().sidebarStateRaw()
                val archived = raw.stringSet("archived_keys").toMutableSet()
                val nowArchived = !archived.remove(session.key)
                if (nowArchived) archived.add(session.key)
                val updated = JsonObject(
                    raw + ("archived_keys" to JsonArray(archived.map { JsonPrimitive(it) })),
                )
                gateway.transport().webUiRequest(
                    "sidebar.update",
                    buildJsonObject { put("state", updated) },
                )
                update { it.copy(archivedKeys = archived) }
            } catch (error: Exception) {
                update { it.copy(error = error.message ?: "Не удалось изменить архив") }
            }
        }
    }

    private fun loadWorkspacesInfo() {
        scope.launch {
            val info = runCatching { gateway.api().workspacesRaw() }.getOrNull()
            update { it.copy(workspacesInfo = info) }
        }
    }

    /** Default workspace path on the gateway machine (from GET /api/workspaces). */
    fun defaultWorkspacePath(state: AppState): String = defaultWorkspacePathOf(state)

    override fun openSession(session: SessionRow) {
        val chatId = session.chatId?.takeIf { it.isNotBlank() }
            ?: session.key.substringAfter("websocket:", session.key).takeIf { it.isNotBlank() }
        if (chatId == null) {
            update { it.copy(error = "Некорректный идентификатор сессии") }
            return
        }
        currentChatId = chatId
        canonical = emptyList()
        transient.clear()
        optimistic.clear()
        completedTurns.clear()
        loadGeneration++
        noteTurnActivity()
        update {
            it.copy(
                selectedSession = session,
                chatId = chatId,
                messages = emptyList(),
                messagesLoading = true,
                messagesError = null,
                sending = false,
                awaitingFirstToken = false,
                activeTurnId = null,
                stopping = false,
                error = null,
                contextUsage = null,
            )
        }
        settings.setLastSession(session.key)
        tts.setActiveChat(chatId)
        tts.stop()
        runCatching { gateway.transport().attach(chatId) }
        scope.launch { loadThread(chatId, session.key) }
    }

    override fun newChat() {
        scope.launch { createChat() }
    }

    override fun deleteSession(session: SessionRow) {
        scope.launch {
            try {
                // Nanobot rejects WebUI mutations over plain HTTP (the server closes
                // the connection -> IOException -> "network"). Deletion must go
                // through the authenticated WebSocket.
                gateway.transport().webUiRequest(
                    "session.delete",
                    buildJsonObject { put("key", session.key) },
                )
            } catch (error: GatewayException) {
                if (error.status != 404) {
                    update { it.copy(error = error.message ?: "Не удалось удалить чат") }
                    return@launch
                }
            } catch (error: IllegalStateException) {
                update { it.copy(error = "Нет соединения с шлюзом. Попробуйте ещё раз.") }
                return@launch
            } catch (error: Exception) {
                update { it.copy(error = error.message ?: "Не удалось удалить чат") }
                return@launch
            }
            update { it.copy(sessions = it.sessions.filterNot { row -> row.key == session.key }) }
            if (mutableState.value.selectedSession?.key == session.key) {
                clearChatState()
                settings.setLastSession(null)
                update { it.copy(selectedSession = null, chatId = null, messages = emptyList()) }
            }
        }
    }

    override fun send(text: String, attachments: List<LocalAttachment>) {
        val content = text.trim()
        if (content.isEmpty() && attachments.isEmpty()) return
        if (mutableState.value.connection.phase != ConnectionPhase.CONNECTED) {
            update { it.copy(error = "Нет подключения к gateway") }
            return
        }
        val validation = validateAttachments(attachments)
        if (validation != null) {
            update { it.copy(error = validation) }
            return
        }
        scope.launch {
            val chatId = ensureChat() ?: return@launch
            sendInternal(chatId, content, attachments)
        }
    }

    override fun stopTurn() {
        val state = mutableState.value
        if (state.stopping) return
        // A tap on a stale "stop" affordance must never inject /stop into an idle chat.
        val active = state.sending || state.awaitingFirstToken || state.activeTurnId != null
        if (!active) return
        val chatId = currentChatId ?: return
        tts.stop()
        update { it.copy(stopping = true) }
        runCatching { gateway.transport().stopTurn(chatId) }
        scope.launch {
            // Backend may not answer a cancelled turn (or the socket is down).
            // Never leave the stop button stuck in a spinner.
            delay(3_000)
            if (mutableState.value.stopping) {
                clearStreamingFlags()
                update {
                    it.copy(
                        stopping = false,
                        sending = false,
                        awaitingFirstToken = false,
                        activeTurnId = null,
                        messages = project(),
                    )
                }
                val session = mutableState.value.selectedSession
                val current = currentChatId
                if (current != null && session != null) loadThread(current, session.key)
            }
        }
    }

    override fun toggleTts() {
        tts.toggle()
    }

    override fun stopSpeaking() {
        tts.stop()
    }

    override fun speakText(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        scope.launch {
            tts.test(clean)
        }
    }

    override fun startVoice() {
        startVoiceInternal(assistant = false)
    }

    private fun startVoiceInternal(assistant: Boolean) {
        logD("assistant: startVoice assistant=$assistant, phase=${mutableState.value.voice.phase}, ws=${mutableState.value.wsConnected}")
        when (mutableState.value.voice.phase) {
            VoicePhase.LISTENING, VoicePhase.SPEECH -> {
                if (!assistant) {
                    recorder.stop()
                    return
                }
                cancelVoice()
            }
            VoicePhase.PROCESSING -> cancelVoice()
            else -> Unit
        }
        if (mutableState.value.connection.phase != ConnectionPhase.CONNECTED) {
            update { it.copy(error = "Нет подключения к gateway", assistantLaunching = false) }
            return
        }
        if (voiceJob?.isActive == true) cancelVoice()
        voiceJob = scope.launch {
            try {
                val audio: RecordedAudio = recorder.record(
                    silenceMs = settings.state.value.sttSilenceMs,
                    maxSeconds = settings.state.value.sttMaxSeconds,
                ) ?: run {
                    if (assistant) {
                        val recorderError = recorder.state.value.error
                        update {
                            it.copy(
                                error = recorderError
                                    ?: "Речь не распознана. Попробуйте ещё раз.",
                                assistantLaunching = false,
                            )
                        }
                    }
                    return@launch
                }
                val s = settings.state.value
                val rawText = if (s.sttMode == "local") {
                    VoskSttRecognizer.recognize(audio.samples)
                } else {
                    gateway.transport().transcribeAudio(
                        dataUrl = wavDataUrl(audio),
                        durationMs = audio.durationMs,
                    )
                }
                val text = cleanAsrText(rawText)
                if (text.isBlank()) {
                    update { it.copy(error = "Речь не распознана. Попробуйте ещё раз.", assistantLaunching = false) }
                    return@launch
                }
                update { it.copy(lastTranscription = text) }
                // The chat is resolved after speaking, so a slow WebSocket/new-chat
                // handshake never blocks the microphone from starting.
                logD("assistant: transcript ready, resolving chat (ws=${mutableState.value.wsConnected})")
                val chatId = ensureChat()
                if (chatId == null) {
                    update { it.copy(error = "Не удалось создать чат для отправки", assistantLaunching = false) }
                    return@launch
                }
                sendInternal(chatId, text)
            } catch (error: CancellationException) {
                recorder.reset()
                throw error
            } catch (error: Exception) {
                recorder.reset()
                update { it.copy(error = friendlyError(error.message), assistantLaunching = false) }
            } finally {
                // Keep the PROCESSING indicator visible while the chat is being
                // created/sent; reset only when the whole turn is done.
                recorder.reset()
                if (assistant) update { it.copy(assistantLaunching = false) }
            }
        }
    }

    override fun stopVoice() {
        when (mutableState.value.voice.phase) {
            VoicePhase.LISTENING, VoicePhase.SPEECH -> recorder.stop()
            VoicePhase.PROCESSING -> cancelVoice()
            else -> Unit
        }
    }

    override fun cancelVoice() {
        recorder.cancel()
        recorder.reset()
        voiceJob?.cancel()
        voiceJob = null
        update {
            it.copy(
                voice = it.voice.copy(phase = VoicePhase.IDLE, amplitude = 0f),
                assistantLaunching = false,
            )
        }
    }

    override fun saveSettings(settings: AppSettings) {
        val previous = this.settings.state.value
        this.settings.update { settings }
        update { it.copy(settings = settings) }
        if (previous.baseUrl != settings.baseUrl || previous.secret != settings.secret) {
            if (mutableState.value.connection.phase != ConnectionPhase.IDLE) disconnect()
        }
    }

    override fun testTts(text: String) {
        scope.launch { tts.test(text) }
    }

    override fun refreshGatewaySettings() {
        if (mutableState.value.connection.phase != ConnectionPhase.CONNECTED) return
        scope.launch {
            update { it.copy(modelsLoading = true) }
            try {
                val payload = gateway.api().settings()
                update {
                    it.copy(
                        modelPresets = payload.modelPresets,
                        activeModelPreset = payload.agent.modelPreset ?: payload.agent.model,
                        agentModel = payload.agent.model,
                        providers = payload.providers,
                        modelsLoading = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                update { it.copy(modelsLoading = false) }
            }
        }
    }

    override fun selectModelPreset(presetName: String) {
        if (mutableState.value.connection.phase != ConnectionPhase.CONNECTED) return
        val preset: ModelPresetItem? = mutableState.value.modelPresets.firstOrNull { it.name == presetName }
        scope.launch {
            try {
                val payload = buildJsonObject {
                    put("model_preset", presetName)
                    preset?.model?.takeIf { it.isNotBlank() }?.let { put("model", it) }
                    preset?.provider?.takeIf { it.isNotBlank() }?.let { put("provider", it) }
                }
                gateway.transport().webUiRequest("settings.agent.update", payload)
                update { it.copy(activeModelPreset = presetName, agentModel = preset?.model ?: it.agentModel) }
                refreshGatewaySettings()
            } catch (error: Exception) {
                update { it.copy(error = error.message ?: "Не удалось сменить модель") }
            }
        }
    }

    override fun loadGatewayConfig() {
        if (mutableState.value.connection.phase != ConnectionPhase.CONNECTED) return
        scope.launch {
            update { it.copy(gatewayConfigLoading = true, gatewayConfigError = null) }
            try {
                val payload = gateway.api().settings()
                val runtime = payload.runtimeConfig ?: JsonObject(emptyMap())
                val raw = PrettyJson.encodeToString(JsonObject.serializer(), runtime)
                update {
                    it.copy(
                        runtimeConfig = runtime,
                        gatewayConfigRaw = raw,
                        gatewayConfigLoading = false,
                        runtimeConfigRestartRequired = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                update {
                    it.copy(
                        gatewayConfigLoading = false,
                        gatewayConfigError = error.message ?: "Не удалось загрузить конфигурацию",
                    )
                }
            }
        }
    }

    override fun saveGatewayConfig(rawJson: String, onSuccess: () -> Unit) {
        if (mutableState.value.connection.phase != ConnectionPhase.CONNECTED) return
        val values = try {
            com.nanobot.client.core.model.GatewayJson.parseToJsonElement(rawJson).jsonObject
        } catch (_: Exception) {
            update { it.copy(gatewayConfigError = "Некорректный JSON") }
            return
        }
        scope.launch {
            update { it.copy(gatewayConfigSaving = true, gatewayConfigError = null) }
            try {
                gateway.transport().webUiRequest(
                    "settings.runtime_config.update",
                    buildJsonObject { put("values", values) },
                )
                update {
                    it.copy(
                        runtimeConfig = values,
                        gatewayConfigRaw = PrettyJson.encodeToString(JsonObject.serializer(), values),
                        gatewayConfigSaving = false,
                        gatewayConfigError = null,
                        runtimeConfigRestartRequired = true,
                    )
                }
                onSuccess()
                loadWorkspacesInfo()
            } catch (error: Exception) {
                update {
                    it.copy(
                        gatewayConfigSaving = false,
                        gatewayConfigError = error.message ?: "Не удалось сохранить конфигурацию",
                    )
                }
            }
        }
    }

    override fun saveRuntimeConfig(values: JsonObject, onSuccess: () -> Unit) {
        if (mutableState.value.connection.phase != ConnectionPhase.CONNECTED) return
        if (values.isEmpty()) {
            onSuccess()
            return
        }
        scope.launch {
            update { it.copy(gatewayConfigSaving = true, gatewayConfigError = null) }
            try {
                gateway.transport().webUiRequest(
                    "settings.runtime_config.update",
                    buildJsonObject { put("values", values) },
                )
                val current = mutableState.value.runtimeConfig ?: JsonObject(emptyMap())
                val merged = JsonObject(current + values)
                update {
                    it.copy(
                        runtimeConfig = merged,
                        gatewayConfigRaw = PrettyJson.encodeToString(JsonObject.serializer(), merged),
                        gatewayConfigSaving = false,
                        gatewayConfigError = null,
                        runtimeConfigRestartRequired = true,
                    )
                }
                onSuccess()
                loadWorkspacesInfo()
            } catch (error: Exception) {
                update {
                    it.copy(
                        gatewayConfigSaving = false,
                        gatewayConfigError = error.message ?: "Не удалось сохранить конфигурацию",
                    )
                }
            }
        }
    }

    // ── MCP ────────────────────────────────────────────────────────────────

    override fun refreshMcpPresets() {
        if (mutableState.value.connection.phase != ConnectionPhase.CONNECTED) return
        scope.launch {
            update { it.copy(mcpLoading = true, mcpError = null) }
            try {
                val payload = gateway.api().mcpPresets()
                update { it.copy(mcpPresets = payload.presets, mcpLoading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                update { it.copy(mcpLoading = false, mcpError = error.message ?: "Не удалось загрузить MCP") }
            }
        }
    }

    private fun mcpAction(name: String, action: String, payload: JsonObject) {
        if (mutableState.value.connection.phase != ConnectionPhase.CONNECTED) return
        scope.launch {
            update { it.copy(mcpBusyName = name, mcpError = null) }
            try {
                val response = gateway.transport().webUiRequest("settings.mcp.$action", payload)
                val restart = (response.result?.get("requires_restart") as? JsonPrimitive)?.booleanOrNull == true
                update { it.copy(mcpBusyName = null, mcpRestartRequired = restart) }
                refreshMcpPresets()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                update { it.copy(mcpBusyName = null, mcpError = error.message ?: "Ошибка MCP") }
            }
        }
    }

    override fun enableMcpPreset(name: String, fields: JsonObject) {
        mcpAction(
            name,
            "enable",
            JsonObject(fields + mapOf("name" to JsonPrimitive(name))),
        )
    }

    override fun setMcpEnabled(name: String, enabled: Boolean) {
        mcpAction(name, if (enabled) "enable" else "disable", buildJsonObject { put("name", name) })
    }

    override fun disableMcpPreset(name: String) {
        mcpAction(name, "disable", buildJsonObject { put("name", name) })
    }

    override fun removeMcpPreset(name: String) {
        mcpAction(name, "remove", buildJsonObject { put("name", name) })
    }

    override fun testMcpPreset(name: String) {
        mcpAction(name, "test", buildJsonObject { put("name", name) })
    }

    override fun reconnectMcpPreset(name: String) {
        mcpAction(name, "reconnect", buildJsonObject { put("name", name) })
    }

    override fun updateMcpTools(name: String, tools: List<String>) {
        mcpAction(
            name,
            "tools",
            buildJsonObject {
                put("name", name)
                put("enabled_tools", JsonArray(tools.map { JsonPrimitive(it) }))
            },
        )
    }

    override fun saveCustomMcp(values: JsonObject) {
        val name = (values["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        mcpAction(name, "custom", values)
    }

    override fun importMcp(configJson: String) {
        val parsed = runCatching {
            com.nanobot.client.core.model.GatewayJson.parseToJsonElement(configJson).jsonObject
        }.getOrElse {
            update { it.copy(mcpError = "Некорректный JSON") }
            return
        }
        mcpAction(
            "import",
            "import",
            buildJsonObject {
                put("config", com.nanobot.client.core.model.GatewayJson.encodeToString(JsonObject.serializer(), parsed))
            },
        )
    }

    // ── Automations ────────────────────────────────────────────────────────

    override fun loadAutomations() {
        if (mutableState.value.connection.phase != ConnectionPhase.CONNECTED) return
        scope.launch {
            update { it.copy(automationsLoading = true, automationsError = null) }
            try {
                val payload = gateway.api().automations()
                update { it.copy(automations = payload.jobs, automationsLoading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                update {
                    it.copy(
                        automationsLoading = false,
                        automationsError = error.message ?: "Не удалось загрузить автоматизации",
                    )
                }
            }
        }
    }

    private fun automationAction(
        id: String,
        action: String,
        payload: JsonObject = JsonObject(emptyMap()),
        reload: Boolean = true,
        onSuccess: () -> Unit = {},
    ) {
        if (mutableState.value.connection.phase != ConnectionPhase.CONNECTED) return
        scope.launch {
            update { it.copy(automationBusyId = id, automationsError = null) }
            try {
                gateway.transport().webUiRequest(
                    "automation.$action",
                    JsonObject(payload + mapOf("id" to JsonPrimitive(id))),
                )
                update { it.copy(automationBusyId = null) }
                onSuccess()
                if (reload) loadAutomations()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                update { it.copy(automationBusyId = null, automationsError = error.message ?: "Ошибка автоматизации") }
            }
        }
    }

    override fun runAutomation(id: String) {
        automationAction(id, "run", reload = false)
        scope.launch {
            delay(1_200)
            loadAutomations()
        }
    }

    override fun setAutomationEnabled(id: String, enabled: Boolean) {
        automationAction(id, if (enabled) "enable" else "disable")
    }

    override fun deleteAutomation(id: String) {
        automationAction(id, "delete")
    }

    override fun updateAutomation(id: String, values: JsonObject, onSuccess: () -> Unit) {
        automationAction(
            id,
            "update",
            buildJsonObject { put("values", values) },
            onSuccess = onSuccess,
        )
    }

    override fun loadAutomationResult(job: AutomationJob, runAtMs: Long) {
        if (mutableState.value.connection.phase != ConnectionPhase.CONNECTED) return
        val kind = if (job.kind == "local_trigger" || job.schedule.kind == "local") "local_trigger" else "cron"
        scope.launch {
            update { it.copy(automationResultLoading = true, automationResultText = null, automationResultError = null) }
            try {
                val payload = gateway.api().automationResult(job.id, runAtMs, kind)
                update {
                    it.copy(
                        automationResultLoading = false,
                        automationResultText = payload.response.orEmpty(),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                update {
                    it.copy(
                        automationResultLoading = false,
                        automationResultError = error.message ?: "Не удалось загрузить результат",
                    )
                }
            }
        }
    }

    override fun clearAutomationResult() {
        update {
            it.copy(
                automationResultLoading = false,
                automationResultText = null,
                automationResultError = null,
            )
        }
    }

    override fun saveProvider(name: String, apiBase: String?, apiKey: String?, onSuccess: () -> Unit) {
        if (mutableState.value.connection.phase != ConnectionPhase.CONNECTED) return
        scope.launch {
            try {
                val payload = buildJsonObject {
                    put("provider", name)
                    apiBase?.let { put("api_base", it) }
                    apiKey?.takeIf { it.isNotBlank() }?.let { put("api_key", it) }
                }
                gateway.transport().webUiRequest("settings.provider.update", payload)
                refreshGatewaySettings()
                onSuccess()
            } catch (error: Exception) {
                update { it.copy(error = error.message ?: "Не удалось сохранить провайдера") }
            }
        }
    }

    override fun dismissError() {
        update { it.copy(error = null) }
    }

    override fun onBackground() {
        tts.stop()
        recorder.cancel()
        recorder.reset()
        voiceJob?.cancel()
        voiceJob = null
        update { it.copy(assistantLaunching = false) }
    }

    override fun onForeground() {
        val saved = settings.state.value
        if (mutableState.value.connection.phase == ConnectionPhase.IDLE ||
            mutableState.value.connection.phase == ConnectionPhase.FAILED
        ) {
            if (saved.baseUrl.isNotBlank() && saved.secret.isNotBlank()) {
                connect(saved.baseUrl, saved.secret)
            }
            return
        }
        val session = mutableState.value.selectedSession ?: return
        val chatId = currentChatId ?: return
        // После возврата из фона состояние чата могло устареть (turn завершился
        // или оборвался) — переподключаемся и перечитываем поток.
        if (mutableState.value.connection.phase != ConnectionPhase.CONNECTED) return
        if (!mutableState.value.wsConnected) {
            // Manual "retry" from the reconnect banner: revive a transport that has
            // given up after its initial reconnect attempts.
            runCatching { gateway.transport().connect() }
        }
        runCatching { gateway.transport().attach(chatId) }
        scope.launch { loadThread(chatId, session.key) }
    }

    override fun startAssistant() {
        logD("assistant: invoked, phase=${mutableState.value.connection.phase}, ws=${mutableState.value.wsConnected}")
        update { it.copy(assistantLaunching = true) }
        if (mutableState.value.connection.phase != ConnectionPhase.CONNECTED) {
            assistantPending = true
            scope.launch {
                delay(20_000)
                if (assistantPending) {
                    assistantPending = false
                    update {
                        it.copy(
                            assistantLaunching = false,
                            error = "Нет соединения с шлюзом — проверьте сеть и повторите.",
                        )
                    }
                }
            }
            return
        }
        scope.launch { performAssistantLaunch() }
    }

    private suspend fun performAssistantLaunch() {
        assistantPending = false
        val saved = settings.state.value
        // Cancel any stale recorder state first: a previous PROCESSING/LISTENING
        // session must never swallow a fresh assistant invocation.
        cancelVoice()
        if (saved.assistantChatMode == "specific") {
            val key = saved.assistantSessionKey
            val sessions = mutableState.value.sessions.ifEmpty {
                runCatching { gateway.api().sessions().sessions }.getOrDefault(emptyList())
            }
            val session = key?.let { wanted ->
                sessions.firstOrNull { it.key == wanted || it.chatId == wanted }
            }
            if (session != null) {
                openSession(session)
            } else {
                if (currentChatId != null) clearChatState()
                if (key != null) {
                    update { it.copy(error = "Чат для ассистента не найден — создам новый") }
                }
            }
        } else if (currentChatId != null) {
            clearChatState()
        }

        if (!saved.assistantAutoListen) {
            if (currentChatId == null && createChat() == null) {
                update { it.copy(assistantLaunching = false) }
                return
            }
            update { it.copy(assistantLaunching = false) }
            return
        }

        update { it.copy(assistantLaunching = true) }
        // Start listening immediately: waiting for a WebSocket handshake here made
        // the assistant feel dead for up to 10-20 seconds. The chat/transport is
        // resolved after the user has finished speaking.
        delay(250)
        startVoiceInternal(assistant = true)
    }

    private suspend fun ensureChat(): String? = currentChatId ?: createChat()

    /** Workspace scope for outgoing frames; null = default gateway workspace. */
    private fun workspaceScope(): JsonObject? {
        val path = settings.state.value.activeWorkspacePath.trim()
        if (path.isEmpty() || !path.startsWith("/")) return null
        val defaultPath = defaultWorkspacePathOf(mutableState.value)
        val defaultMode = ((mutableState.value.workspacesInfo?.get("default_scope") as? JsonObject)
            ?.get("access_mode") as? JsonPrimitive)?.content
        // Choosing another project over a remote connection is only allowed with
        // restricted access (`can_use_full_access=false` on the WebUI surface).
        val mode = if (path == defaultPath) (defaultMode ?: "restricted") else "restricted"
        return buildJsonObject {
            put("project_path", path)
            put("access_mode", mode)
        }
    }

    private suspend fun createChat(): String? {
        if (mutableState.value.connection.phase != ConnectionPhase.CONNECTED) {
            update { it.copy(error = "Нет подключения к gateway") }
            return null
        }
        val opened = gateway.transport().ensureOpen(12_000)
        logD("chat: transport open=$opened")
        if (!opened) {
            runCatching { gateway.transport().connect() }
            if (!gateway.transport().ensureOpen(8_000)) {
                update { it.copy(error = "Нет соединения с шлюзом — проверьте сеть и повторите.") }
                return null
            }
        }
        return try {
            val chatId = gateway.transport().newChat(workspaceScope = workspaceScope())
            val key = "websocket:$chatId"
            currentChatId = chatId
            canonical = emptyList()
            transient.clear()
            optimistic.clear()
            completedTurns.clear()
            val now = Instant.now().toString()
            val row = SessionRow(
                key = key,
                channel = "websocket",
                chatId = chatId,
                createdAt = now,
                updatedAt = now,
                title = "Новый чат",
                preview = null,
                workspaceScope = settings.state.value.activeWorkspacePath
                    .trim()
                    .takeIf { it.startsWith("/") }
                    ?.let { com.nanobot.client.core.model.WorkspaceScopePayload(projectPath = it) },
            )
            update {
                it.copy(
                    selectedSession = row,
                    chatId = chatId,
                    messages = emptyList(),
                    messagesLoading = false,
                    messagesError = null,
                    sending = false,
                    awaitingFirstToken = false,
                    activeTurnId = null,
                    stopping = false,
                    error = null,
                    sessions = listOf(row) + it.sessions.filterNot { session -> session.key == key },
                )
            }
            settings.setLastSession(key)
            tts.setActiveChat(chatId)
            tts.stop()
            gateway.transport().attach(chatId)
            chatId
        } catch (error: Exception) {
            update { it.copy(error = friendlyError(error.message)) }
            null
        }
    }

    private suspend fun sendInternal(
        chatId: String,
        text: String,
        attachments: List<LocalAttachment> = emptyList(),
    ) {
        val turnId = UUID.randomUUID().toString()
        val userMessage = UiMessage(
            id = "u-$turnId",
            role = "user",
            content = text,
            createdAt = System.currentTimeMillis(),
            turnId = turnId,
            optimistic = true,
            media = attachments.map { attachment ->
                com.nanobot.client.core.model.UiMediaAttachment(
                    kind = attachment.mime.kindOfMedia(),
                    name = attachment.name,
                )
            }.takeIf { it.isNotEmpty() },
            localMedia = attachments.takeIf { it.isNotEmpty() },
        )
        optimistic[turnId] = userMessage
        noteTurnActivity()
        update {
            it.copy(
                messages = project(),
                sending = true,
                awaitingFirstToken = true,
                activeTurnId = null,
                error = null,
            )
        }
        tts.stop()
        try {
            gateway.transport().sendMessage(
                chatId = chatId,
                content = text,
                turnId = turnId,
                workspaceScope = workspaceScope(),
                attachments = attachments.map { attachment ->
                    OutboundAttachment(
                        name = attachment.name,
                        dataUrl = "data:${attachment.mime};base64," +
                            Base64.Default.encode(attachment.bytes),
                    )
                },
            )
            update { it.copy(sending = false, activeTurnId = turnId) }
        } catch (error: Exception) {
            optimistic[turnId] = userMessage.copy(failed = true)
            update {
                it.copy(
                    messages = project(),
                    sending = false,
                    awaitingFirstToken = false,
                    error = friendlyError(error.message),
                )
            }
        }
    }

    private fun clearStreamingFlags() {
        val keys = transient.keys.toList()
        keys.forEach { key ->
            val message = transient[key] ?: return@forEach
            transient[key] = message.copy(isStreaming = false, reasoningStreaming = false)
        }
    }

    private fun clearChatState() {
        currentChatId = null
        canonical = emptyList()
        transient.clear()
        optimistic.clear()
        completedTurns.clear()
        loadGeneration++
        tts.setActiveChat(null)
        tts.stop()
        recorder.cancel()
        recorder.reset()
        update {
            it.copy(
                selectedSession = null,
                chatId = null,
                messages = emptyList(),
                messagesLoading = false,
                messagesError = null,
                sending = false,
                awaitingFirstToken = false,
                activeTurnId = null,
                stopping = false,
                contextUsage = null,
            )
        }
    }

    private suspend fun loadThread(chatId: String, sessionKey: String) {
        val generation = ++loadGeneration
        update { it.copy(messagesLoading = true, messagesError = null) }
        try {
            val payload = gateway.api().thread(sessionKey)
            if (currentChatId != chatId || generation != loadGeneration) return
            canonical = payload.messages
            payload.completedTurnIds?.let { completedTurns.addAll(it) }
            val canonicalTurns = canonical.mapNotNull { it.turnId }.toSet()
            transient.keys
                .filter { it in completedTurns && it in canonicalTurns }
                .forEach { transient.remove(it); completedTurns.remove(it) }
            // Optimistic user messages never auto-expire in the canonical snapshot.
            if (canonical.isNotEmpty()) {
                optimistic.clear()
                // When we have canonical messages from server and are not actively generating,
                // clear transient messages to prevent duplicate rendering of assistant replies.
                if (mutableState.value.activeTurnId == null && !mutableState.value.sending) {
                    transient.clear()
                }
            }
            update {
                it.copy(
                    messages = project(),
                    messagesLoading = false,
                    activeTurnId = payload.activeTurnId,
                    awaitingFirstToken = payload.activeTurnId != null && it.awaitingFirstToken,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (currentChatId == chatId && generation == loadGeneration) {
                update { it.copy(messagesLoading = false, messagesError = error.message ?: "Не удалось загрузить историю") }
            }
        }
    }

    private fun handleEvent(event: InboundEvent) {
        when (event) {
            is InboundEvent.UserMessage -> {
                // Another chat may have started a turn; keep run_started_at fresh.
                scope.launch { delay(300); refreshSessions(silent = true) }
            }
            is InboundEvent.GoalStatus -> {
                scope.launch { delay(300); refreshSessions(silent = true) }
            }
            is InboundEvent.PicoMessageCreate,
            is InboundEvent.PicoMessageUpdate,
            is InboundEvent.PicoTyping,
            -> Unit
            is InboundEvent.Delta -> if (matches(event.value.chatId)) {
                noteTurnActivity()
                markStreamStarted()
                foldDelta(event.value)
            }
            is InboundEvent.ReasoningDelta -> if (matches(event.value.chatId)) {
                noteTurnActivity()
                markStreamStarted()
                foldReasoning(event.value)
            }
            is InboundEvent.ReasoningEnd -> if (matches(event.value.chatId)) {
                noteTurnActivity()
                foldReasoningEnd(event.value)
            }
            is InboundEvent.StreamEnd -> if (matches(event.value.chatId)) {
                noteTurnActivity()
                foldStreamEnd(event.value)
            }
            is InboundEvent.Message -> if (matches(event.value.chatId)) {
                noteTurnActivity()
                markStreamStarted()
                foldMessage(event.value)
            }
            is InboundEvent.FileEdit -> if (matches(event.value.chatId)) {
                noteTurnActivity()
                markStreamStarted()
                foldFileEdit(event.value)
            }
            is InboundEvent.TurnEnd -> if (matches(event.value.chatId)) {
                noteTurnActivity()
                foldTurnEnd(event.value)
            } else {
                scope.launch { delay(300); refreshSessions(silent = true) }
            }
            is InboundEvent.SessionUpdated -> refreshSessions()
            is InboundEvent.RuntimeModelUpdated, is InboundEvent.TurnModelUpdated -> refreshGatewaySettings()
            is InboundEvent.Error -> {
                val chatId = event.value.chatId
                if (chatId == null || matches(chatId)) {
                    clearStreamingFlags()
                    update {
                        it.copy(
                            error = event.value.detail ?: event.value.reason ?: "Ошибка gateway",
                            sending = false,
                            stopping = false,
                            awaitingFirstToken = false,
                            activeTurnId = null,
                            messages = project(),
                        )
                    }
                }
            }
            else -> Unit
        }
    }

    private fun markStreamStarted() {
        if (mutableState.value.awaitingFirstToken || mutableState.value.sending) {
            update { it.copy(awaitingFirstToken = false, sending = false) }
        }
    }

    private fun matches(chatId: String?): Boolean =
        // If sessionId is null in the WS frame, accept the event for the current chat.
        currentChatId != null && (chatId == null || chatId == currentChatId)

    private fun noteTurnActivity() {
        lastTurnEventAt = System.currentTimeMillis()
        staleStrikes = 0
    }

    private fun foldDelta(event: DeltaEvent) {
        val key = event.turnId ?: "current"
        val message = transient[key] ?: newAssistant(key, event.turnId)
        transient[key] = message.copy(
            content = message.content + event.text,
            isStreaming = true,
            turnPhase = event.turnPhase ?: message.turnPhase,
            turnSeq = event.turnSeq ?: message.turnSeq,
        )
        publishMessages()
    }

    private fun foldReasoning(event: ReasoningDeltaEvent) {
        val key = event.turnId ?: "current"
        val message = transient[key] ?: newAssistant(key, event.turnId)
        transient[key] = message.copy(
            reasoning = (message.reasoning ?: "") + event.text,
            reasoningStreaming = true,
            turnPhase = event.turnPhase ?: message.turnPhase,
            turnSeq = event.turnSeq ?: message.turnSeq,
        )
        publishMessages()
    }

    private fun foldReasoningEnd(event: ReasoningEndEvent) {
        val key = event.turnId ?: "current"
        val message = transient[key] ?: return
        transient[key] = message.copy(reasoningStreaming = false)
        publishMessages()
    }

    private fun foldStreamEnd(event: StreamEndEvent) {
        val key = event.turnId ?: "current"
        val message = transient[key] ?: newAssistant(key, event.turnId)
        transient[key] = message.copy(
            content = event.text ?: message.content,
            isStreaming = false,
            turnPhase = event.turnPhase ?: message.turnPhase,
            turnSeq = event.turnSeq ?: message.turnSeq,
        )
        publishMessages()
    }

    private fun foldMessage(event: MessageEvent) {
        val key = event.turnId ?: "current"
        val message = transient[key] ?: newAssistant(key, event.turnId)
        val kind = event.kind
        val text = event.text.trim()
        // Intermediate rows must never become the visible answer:
        //  - tool_hint / progress carry tool labels and executions,
        //  - reasoning / thought carry the model's private monologue.
        val traces = when (kind) {
            "tool_hint", "progress" -> mergeTrace(message.traces, text)
            else -> message.traces
        }
        val reasoning = when (kind) {
            "reasoning", "thought" ->
                if (text.isBlank()) message.reasoning
                else listOfNotNull(message.reasoning?.takeIf { it.isNotBlank() }, text).joinToString("\n\n")
            else -> message.reasoning
        }
        transient[key] = message.copy(
            content = if (kind == null && text.isNotBlank()) event.text else message.content,
            kind = kind ?: message.kind,
            isStreaming = kind != null,
            reasoning = reasoning,
            traces = traces,
            toolEvents = mergeTools(message.toolEvents, event.toolEvents),
            latencyMs = event.latencyMs ?: message.latencyMs,
            source = event.source ?: message.source,
            turnPhase = event.turnPhase ?: message.turnPhase,
            turnSeq = event.turnSeq ?: message.turnSeq,
            agentUi = event.agentUi ?: message.agentUi,
        )
        // Backend does not publish turn_end for /stop-cancelled turns; the reply
        // message ("Stopped N task(s).") is the only stop acknowledgement.
        if (mutableState.value.stopping && kind == null && text.isNotBlank()) {
            update {
                it.copy(
                    messages = project(),
                    sending = false,
                    awaitingFirstToken = false,
                    activeTurnId = null,
                    stopping = false,
                )
            }
        }
        publishMessages()
    }

    private fun mergeTrace(existing: List<String>?, incoming: String): List<String>? {
        if (incoming.isBlank()) return existing
        val merged = existing.orEmpty().toMutableList()
        if (merged.lastOrNull() != incoming) merged += incoming
        return merged.takeIf { it.isNotEmpty() }
    }

    private fun foldFileEdit(event: FileEditEvent) {
        val key = event.turnId ?: "current"
        val message = transient[key] ?: newAssistant(key, event.turnId)
        transient[key] = message.copy(
            fileEdits = mergeEdits(message.fileEdits, event.edits),
            turnPhase = event.turnPhase ?: message.turnPhase,
            turnSeq = event.turnSeq ?: message.turnSeq,
        )
        publishMessages()
    }

    private fun foldTurnEnd(event: TurnEndEvent) {
        val key = event.turnId ?: "current"
        val message = transient[key]
        if (message != null) {
            transient[key] = message.copy(
                isStreaming = false,
                reasoningStreaming = false,
                latencyMs = event.latencyMs ?: message.latencyMs,
            )
        }
        // A turn finished: nothing may remain in a "streaming" visual state.
        clearStreamingFlags()
        val turnId = event.turnId
        if (turnId != null) completedTurns.add(turnId)
        val finalText = message?.content?.takeIf { it.isNotBlank() }
            ?: canonical.firstOrNull { it.turnId == turnId && it.role == "assistant" }?.content.orEmpty()
        val chatId = currentChatId
        if (chatId != null && finalText.isNotBlank()) {
            tts.onAssistantFinal(chatId, finalText, turnId)
            val title = mutableState.value.selectedSession?.title?.takeIf { it.isNotBlank() } ?: "Nanobot"
            val preview = finalText.trim().take(120)
            notifyTaskCompleted(title, preview)
        }
        update {
            it.copy(
                messages = project(),
                sending = false,
                awaitingFirstToken = false,
                activeTurnId = null,
                stopping = false,
            )
        }
        val selected = mutableState.value.selectedSession
        if (chatId != null && selected != null) {
            scope.launch {
                delay(250)
                loadThread(chatId, selected.key)
            }
        }
        refreshSessions()
    }

    private fun newAssistant(key: String, turnId: String?): UiMessage = UiMessage(
        id = turnId ?: "assistant-$key",
        role = "assistant",
        content = "",
        createdAt = System.currentTimeMillis(),
        turnId = turnId,
        isStreaming = true,
    )

    private fun publishMessages() {
        update { it.copy(messages = project()) }
    }

    private fun project(): List<UiMessage> =
        MessageProjector.project(canonical, transient, optimistic)

    private fun mergeTools(
        existing: List<ToolProgressEvent>?,
        incoming: List<ToolProgressEvent>?,
    ): List<ToolProgressEvent>? {
        if (incoming.isNullOrEmpty()) return existing
        val merged = linkedMapOf<String, ToolProgressEvent>()
        existing?.forEachIndexed { index, item -> merged[item.callId ?: "e$index"] = item }
        incoming.forEachIndexed { index, item -> merged[item.callId ?: "i$index"] = item }
        return merged.values.toList()
    }

    private fun mergeEdits(existing: List<UiFileEdit>?, incoming: List<UiFileEdit>?): List<UiFileEdit>? {
        if (incoming.isNullOrEmpty()) return existing
        val merged = linkedMapOf<String, UiFileEdit>()
        existing?.forEachIndexed { index, item -> merged[item.callId.ifBlank { "e$index" }] = item }
        incoming.forEachIndexed { index, item -> merged[item.callId.ifBlank { "i$index" }] = item }
        return merged.values.toList()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun wavDataUrl(audio: RecordedAudio): String {
        val wav = encodeWav(audio.samples, audio.sampleRate)
        return "data:audio/wav;base64,${Base64.Default.encode(wav)}"
    }

    private fun update(transform: (AppState) -> AppState) {
        mutableState.update(transform)
    }

    private fun mapConnection(value: ConnectionState): ConnectionUi = when (value) {
        is ConnectionState.Idle -> ConnectionUi(ConnectionPhase.IDLE)
        is ConnectionState.Connecting -> ConnectionUi(ConnectionPhase.CONNECTING)
        is ConnectionState.Connected -> ConnectionUi(ConnectionPhase.CONNECTED)
        is ConnectionState.Reconnecting -> ConnectionUi(ConnectionPhase.RECONNECTING, value.error)
        is ConnectionState.Failed -> ConnectionUi(ConnectionPhase.FAILED, value.error)
    }
}

private val IMAGE_MIME = setOf("image/png", "image/jpeg", "image/webp", "image/gif")
private val VIDEO_MIME = setOf("video/mp4", "video/webm", "video/quicktime")
private val DOCUMENT_MIME = setOf(
    "application/json", "application/pdf", "application/toml",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/x-yaml", "application/xhtml+xml", "application/xml", "application/yaml",
    "text/csv", "text/html", "text/markdown", "text/plain", "text/xml", "text/yaml",
)
private const val MAX_ATTACHMENTS = 4
private const val MAX_ATTACHMENT_BYTES = 6L * 1024 * 1024
private const val MAX_ATTACHMENT_TOTAL_BYTES = 24L * 1024 * 1024

private fun validateAttachments(attachments: List<LocalAttachment>): String? {
    if (attachments.size > MAX_ATTACHMENTS) return "Можно прикрепить не более $MAX_ATTACHMENTS файлов"
    var total = 0L
    attachments.forEach { attachment ->
        if (attachment.mime !in IMAGE_MIME && attachment.mime !in VIDEO_MIME && attachment.mime !in DOCUMENT_MIME) {
            return "Неподдерживаемый тип файла: ${attachment.name}"
        }
        if (attachment.bytes.size > MAX_ATTACHMENT_BYTES) {
            return "Файл «${attachment.name}» больше 6 МБ"
        }
        total += attachment.bytes.size
    }
    if (total > MAX_ATTACHMENT_TOTAL_BYTES) return "Суммарный размер вложений больше 24 МБ"
    return null
}

private fun String.kindOfMedia(): String = when (this) {
    in IMAGE_MIME -> "image"
    in VIDEO_MIME -> "video"
    else -> "file"
}

private fun friendlyError(raw: String?): String = when (raw) {
    "transport_not_open" -> "Нет соединения с шлюзом. Подождите пару секунд и повторите."
    "transport_closed" -> "Соединение с шлюзом закрыто. Переподключитесь."
    "new_chat_timeout" -> "Шлюз не ответил при создании чата."
    "message_accept_timeout" -> "Шлюз не подтвердил отправку сообщения."
    "transcription_timeout" -> "Шлюз не ответил при распознавании речи."
    null, "" -> "Неизвестная ошибка"
    else -> raw
}

private fun JsonObject.stringSet(key: String): Set<String> =
    (this[key] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        ?.toSet()
        .orEmpty()

private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(4, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .callTimeout(0, TimeUnit.SECONDS) // websocket needs to live long; timeouts handled per-request
    .pingInterval(20, TimeUnit.SECONDS)
    .build()
