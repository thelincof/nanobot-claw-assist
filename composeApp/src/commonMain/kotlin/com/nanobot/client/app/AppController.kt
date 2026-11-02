package com.nanobot.client.app

import com.nanobot.client.audio.VoiceUiState
import com.nanobot.client.core.model.AutomationJob
import com.nanobot.client.core.model.McpPresetItem
import com.nanobot.client.core.model.LocalAttachment
import com.nanobot.client.core.model.ModelPresetItem
import com.nanobot.client.core.model.ProviderItem
import com.nanobot.client.core.model.SessionRow
import com.nanobot.client.core.model.UiMessage
import com.nanobot.client.settings.AppSettings
import com.nanobot.client.tts.TtsUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

enum class ConnectionPhase { IDLE, CONNECTING, CONNECTED, RECONNECTING, FAILED }

data class ConnectionUi(
    val phase: ConnectionPhase = ConnectionPhase.IDLE,
    val error: String? = null,
)

data class AppState(
    val connection: ConnectionUi = ConnectionUi(),
    val settings: AppSettings = AppSettings(),
    /** Raw GET /api/workspaces payload: default_scope + controls. */
    val workspacesInfo: JsonObject? = null,
    val showAllChats: Boolean = false,
    val showArchived: Boolean = false,
    val pinnedKeys: Set<String> = emptySet(),
    val archivedKeys: Set<String> = emptySet(),
    val sessions: List<SessionRow> = emptyList(),
    val sessionsLoading: Boolean = false,
    val sessionsError: String? = null,
    val search: String = "",
    val selectedSession: SessionRow? = null,
    val chatId: String? = null,
    val messages: List<UiMessage> = emptyList(),
    val messagesLoading: Boolean = false,
    val messagesError: String? = null,
    val sending: Boolean = false,
    /** True between "message sent" and the first streamed token/tool event. */
    val awaitingFirstToken: Boolean = false,
    val activeTurnId: String? = null,
    val stopping: Boolean = false,
    val voice: VoiceUiState = VoiceUiState(),
    /** Assistant invocation is waiting for permission/connection before recording. */
    val assistantLaunching: Boolean = false,
    val tts: TtsUiState = TtsUiState(),
    val error: String? = null,
    val lastTranscription: String? = null,
    val modelPresets: List<ModelPresetItem> = emptyList(),
    val activeModelPreset: String? = null,
    val agentModel: String? = null,
    val providers: List<ProviderItem> = emptyList(),
    val modelsLoading: Boolean = false,
    val contextUsage: com.nanobot.client.core.model.ContextUsagePayload? = null,
    val gatewayConfigRaw: String? = null,
    /** Structured runtime_config for the parameter form (39 settings). */
    val runtimeConfig: JsonObject? = null,
    val runtimeConfigRestartRequired: Boolean = false,
    val gatewayConfigLoading: Boolean = false,
    val gatewayConfigSaving: Boolean = false,
    val gatewayConfigError: String? = null,
    /** MCP presets/custom servers. */
    val mcpPresets: List<McpPresetItem> = emptyList(),
    val mcpLoading: Boolean = false,
    val mcpError: String? = null,
    val mcpBusyName: String? = null,
    val mcpRestartRequired: Boolean = false,
    /** Cron jobs + local triggers for the automations calendar. */
    val automations: List<AutomationJob> = emptyList(),
    val automationsLoading: Boolean = false,
    val automationsError: String? = null,
    val automationBusyId: String? = null,
    val automationResultLoading: Boolean = false,
    val automationResultText: String? = null,
    val automationResultError: String? = null,
    /** WebSocket link state (REST may still be healthy while this is false). */
    val wsConnected: Boolean = false,
)

interface AppController {
    val state: StateFlow<AppState>

    fun start()
    fun connect(baseUrl: String, secret: String)
    fun disconnect()
    fun refreshSessions()
    fun setSearch(query: String)
    fun setActiveWorkspace(path: String)
    fun addWorkspace(name: String, path: String)
    fun removeWorkspace(path: String)
    fun pickWorkspaceFolder(onResult: (String?) -> Unit)
    fun renameSession(session: SessionRow, title: String)
    fun setShowAllChats(all: Boolean)
    fun setShowArchived(show: Boolean)
    fun togglePin(session: SessionRow)
    fun toggleArchive(session: SessionRow)
    fun openSession(session: SessionRow)
    fun newChat()
    fun deleteSession(session: SessionRow)
    fun send(text: String, attachments: List<LocalAttachment> = emptyList())
    fun stopTurn()
    fun toggleTts()
    fun stopSpeaking()
    fun speakText(text: String)
    fun startVoice()
    fun stopVoice()
    fun cancelVoice()
    fun saveSettings(settings: AppSettings)
    fun testTts(text: String)
    fun refreshGatewaySettings()
    fun selectModelPreset(presetName: String)
    fun loadGatewayConfig()
    fun saveGatewayConfig(rawJson: String, onSuccess: () -> Unit = {})
    fun saveRuntimeConfig(values: JsonObject, onSuccess: () -> Unit = {})
    fun saveProvider(name: String, apiBase: String?, apiKey: String?, onSuccess: () -> Unit = {})
    fun refreshMcpPresets()
    fun enableMcpPreset(name: String, fields: JsonObject)
    fun setMcpEnabled(name: String, enabled: Boolean)
    fun disableMcpPreset(name: String)
    fun removeMcpPreset(name: String)
    fun testMcpPreset(name: String)
    fun reconnectMcpPreset(name: String)
    fun updateMcpTools(name: String, tools: List<String>)
    fun saveCustomMcp(values: JsonObject)
    fun importMcp(configJson: String)
    fun loadAutomations()
    fun runAutomation(id: String)
    fun setAutomationEnabled(id: String, enabled: Boolean)
    fun deleteAutomation(id: String)
    fun updateAutomation(id: String, values: JsonObject, onSuccess: () -> Unit = {})
    fun loadAutomationResult(job: AutomationJob, runAtMs: Long)
    fun clearAutomationResult()
    fun dismissError()
    fun onBackground()
    fun onForeground()
    fun startAssistant()
}

expect fun createAppController(): AppController

/** Default workspace path on the gateway machine, from GET /api/workspaces payload. */
fun defaultWorkspacePathOf(state: AppState): String =
    ((state.workspacesInfo as? JsonObject)?.get("default_scope") as? JsonObject)
        ?.get("project_path")?.let { it as? kotlinx.serialization.json.JsonPrimitive }
        ?.content.orEmpty()

/** True when [row] belongs to the active workspace selection. */
fun rowMatchesWorkspace(state: AppState, row: SessionRow, activePath: String): Boolean {
    if (state.showAllChats) return true
    val rowPath = row.workspaceScope?.projectPath.orEmpty()
    return if (activePath.isEmpty()) {
        // Default: show chats with no scope or the gateway default scope.
        val defaultPath = defaultWorkspacePathOf(state)
        rowPath.isEmpty() || defaultPath.isEmpty() || rowPath == defaultPath
    } else {
        rowPath == activePath
    }
}

/** Sessions visible in the sidebar given the active workspace filter. */
fun filterSessionsByWorkspace(state: AppState): List<SessionRow> {
    if (state.showAllChats) return state.sessions
    val activePath = state.settings.activeWorkspacePath.trim()
    return state.sessions.filter { rowMatchesWorkspace(state, it, activePath) }
}
