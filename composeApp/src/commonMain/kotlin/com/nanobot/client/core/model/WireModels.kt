package com.nanobot.client.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Transient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

val GatewayJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
}

val PrettyJson = Json {
    prettyPrint = true
    isLenient = true
    ignoreUnknownKeys = true
}

@Serializable
data class TransportLimits(
    @SerialName("max_frame_bytes") val maxFrameBytes: Int = 64 * 1024 * 1024,
    @SerialName("envelope_reserve_bytes") val envelopeReserveBytes: Int = 64 * 1024,
)

@Serializable
data class MessageLimits(@SerialName("max_text_bytes") val maxTextBytes: Int = 64 * 1024)

@Serializable
data class AttachmentLimits(
    @SerialName("max_count") val maxCount: Int = 4,
    @SerialName("max_file_bytes") val maxFileBytes: Long = 6 * 1024 * 1024,
    @SerialName("max_total_bytes") val maxTotalBytes: Long = 24 * 1024 * 1024,
)

@Serializable
data class IngressLimits(
    val transport: TransportLimits = TransportLimits(),
    val message: MessageLimits = MessageLimits(),
    val attachments: AttachmentLimits = AttachmentLimits(),
)

@Serializable
data class BootstrapResponse(
    val token: String = "",
    @SerialName("api_token") val apiToken: String? = null,
    @SerialName("ws_path") val wsPath: String = "/ws",
    @SerialName("ws_url") val wsUrl: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 0,
    val limits: IngressLimits? = null,
    @SerialName("model_name") val modelName: String? = null,
)

@Serializable
data class SessionsPayload(val sessions: List<SessionRow> = emptyList())

@Serializable
data class AgentSettingsPayload(
    val model: String? = null,
    val provider: String? = null,
    @SerialName("model_preset") val modelPreset: String? = null,
    @SerialName("context_window_tokens") val contextWindowTokens: Int? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val temperature: Double? = null,
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
)

@Serializable
data class ModelPresetItem(
    val name: String = "",
    val label: String = "",
    val active: Boolean = false,
    @SerialName("is_default") val isDefault: Boolean = false,
    val model: String = "",
    val provider: String = "",
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("context_window_tokens") val contextWindowTokens: Int? = null,
    val temperature: Double? = null,
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
)

@Serializable
data class ProviderItem(
    val name: String = "",
    val label: String = "",
    val configured: Boolean = false,
    @SerialName("auth_type") val authType: String? = null,
    @SerialName("api_key_hint") val apiKeyHint: String? = null,
    @SerialName("api_base") val apiBase: String? = null,
    @SerialName("default_api_base") val defaultApiBase: String? = null,
    @SerialName("model_selectable") val modelSelectable: Boolean = false,
)

@Serializable
data class NanobotSettingsPayload(
    val agent: AgentSettingsPayload = AgentSettingsPayload(),
    @SerialName("model_presets") val modelPresets: List<ModelPresetItem> = emptyList(),
    @SerialName("model_call_order") val modelCallOrder: List<String> = emptyList(),
    val providers: List<ProviderItem> = emptyList(),
    @SerialName("runtime_config") val runtimeConfig: JsonObject? = null,
    @SerialName("requires_restart") val requiresRestart: Boolean = false,
)

@Serializable
data class WorkspaceScopePayload(
    @SerialName("project_path") val projectPath: String? = null,
    @SerialName("project_name") val projectName: String? = null,
    @SerialName("access_mode") val accessMode: String? = null,
)

@Serializable
data class SessionHandlePayload(
    val id: String = "",
    val name: String = "",
)

@Serializable
data class SessionRow(
    val key: String,
    val channel: String = "websocket",
    @SerialName("chat_id") val chatId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val title: String? = null,
    val preview: String? = null,
    @SerialName("message_count") val messageCount: Int? = null,
    @SerialName("model_preset") val modelPreset: String? = null,
    @SerialName("run_started_at") val runStartedAt: Double? = null,
    @SerialName("workspace_scope") val workspaceScope: WorkspaceScopePayload? = null,
    val handle: SessionHandlePayload? = null,
) {
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: "Новый чат"
    val running: Boolean get() = runStartedAt != null
}

@Serializable
data class UiMessageSource(val kind: String, val label: String? = null)

@Serializable
data class ToolProgressEvent(
    val version: Int? = null,
    val phase: String? = null,
    @SerialName("call_id") val callId: String? = null,
    val name: String? = null,
    val arguments: JsonElement? = null,
    val result: JsonElement? = null,
    val error: JsonElement? = null,
    val files: List<JsonElement>? = null,
    val embeds: List<JsonElement>? = null,
    val function: JsonElement? = null,
)

@Serializable
data class UiFileDiff(
    val format: String? = null,
    val context: Int? = null,
    val truncated: Boolean? = null,
    val text: String? = null,
)

@Serializable
data class UiFileEdit(
    val version: Int? = null,
    @SerialName("call_id") val callId: String,
    val tool: String = "",
    val path: String = "",
    @SerialName("absolute_path") val absolutePath: String? = null,
    val phase: String? = null,
    val added: Int = 0,
    val deleted: Int = 0,
    val approximate: Boolean? = null,
    val status: String = "",
    val operation: String? = null,
    val binary: Boolean? = null,
    val error: String? = null,
    val pending: Boolean? = null,
    val diff: UiFileDiff? = null,
)

@Serializable
data class UiImage(val url: String? = null, val name: String? = null)

@Serializable
data class UiMediaAttachment(val kind: String, val url: String? = null, val name: String? = null)

@Serializable
data class AgentUiBlob(val kind: String, val data: JsonElement? = null)

@Serializable
data class ContextUsagePayload(
    @SerialName("used_tokens") val usedTokens: Long = 0,
    @SerialName("total_tokens") val totalTokens: Long = 0,
    @SerialName("history_tokens") val historyTokens: Long? = null,
    @SerialName("compress_at_tokens") val compressAtTokens: Long? = null,
    @SerialName("summarize_at_tokens") val summarizeAtTokens: Long? = null,
    @SerialName("used_percent") val usedPercent: Double = 0.0,
)

@Serializable
data class UiMessage(
    val id: String = "",
    val role: String = "assistant",
    val content: String = "",
    val kind: String? = null,
    @SerialName("isStreaming") val isStreaming: Boolean = false,
    @SerialName("createdAt") val createdAt: Long = 0,
    val reasoning: String? = null,
    @SerialName("reasoningStreaming") val reasoningStreaming: Boolean = false,
    @SerialName("toolEvents") val toolEvents: List<ToolProgressEvent>? = null,
    @SerialName("fileEdits") val fileEdits: List<UiFileEdit>? = null,
    @SerialName("latencyMs") val latencyMs: Long? = null,
    @SerialName("completedAt") val completedAt: Long? = null,
    val source: UiMessageSource? = null,
    @SerialName("turnId") val turnId: String? = null,
    @SerialName("turnPhase") val turnPhase: String? = null,
    @SerialName("turnSeq") val turnSeq: Int? = null,
    val media: List<UiMediaAttachment>? = null,
    val images: List<UiImage>? = null,
    @Transient val localMedia: List<LocalAttachment>? = null,
    @SerialName("agentUi") val agentUi: AgentUiBlob? = null,
    /** Human-readable tool-call labels from Nanobot trace rows (`exec({...})` etc.). */
    val traces: List<String>? = null,
    /** Groups all tool calls of one agent activity segment into a single card. */
    @SerialName("activitySegmentId") val activitySegmentId: String? = null,
    val optimistic: Boolean = false,
    val failed: Boolean = false,
    val contextUsage: ContextUsagePayload? = null,
)

@Serializable
data class ThreadPage(
    @SerialName("before_cursor") val beforeCursor: String? = null,
    @SerialName("has_more_before") val hasMoreBefore: Boolean = false,
    @SerialName("loaded_message_count") val loadedMessageCount: Int? = null,
    @SerialName("total_known_message_count") val totalKnownMessageCount: Int? = null,
)

@Serializable
data class GoalStatePayload(
    val active: Boolean = false,
    @SerialName("ui_summary") val uiSummary: String? = null,
    val objective: String? = null,
)

@Serializable
data class WebUiThreadPayload(
    @SerialName("schemaVersion") val schemaVersion: Int = 0,
    @SerialName("sessionKey") val sessionKey: String? = null,
    @SerialName("savedAt") val savedAt: String? = null,
    val messages: List<UiMessage> = emptyList(),
    @SerialName("completed_turn_ids") val completedTurnIds: List<String>? = null,
    @SerialName("active_turn_id") val activeTurnId: String? = null,
    val page: ThreadPage? = null,
)

@Serializable
data class SessionDeleteResult(
    val deleted: Boolean = false,
    val status: String? = null,
    @SerialName("blocked_by_automations") val blockedByAutomations: Boolean = false,
)

@Serializable
data class PicoClawSessionSummary(
    val id: String,
    val title: String? = null,
    val preview: String? = null,
    @SerialName("message_count") val messageCount: Int? = null,
    val created: String? = null,
    val updated: String? = null,
)

@Serializable
data class PicoClawFunctionCall(
    val name: String? = null,
    val arguments: String? = null,
)

@Serializable
data class PicoClawToolCall(
    val id: String? = null,
    val type: String? = null,
    val function: PicoClawFunctionCall? = null,
    @SerialName("extra_content") val extraContent: kotlinx.serialization.json.JsonObject? = null,
)

@Serializable
data class PicoClawMessage(
    val role: String,
    val content: String = "",
    val kind: String? = null,
    @SerialName("model_name") val modelName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("tool_calls") val toolCalls: List<PicoClawToolCall>? = null,
)

@Serializable
data class PicoClawSessionDetail(
    val id: String,
    val created: String? = null,
    val updated: String? = null,
    val summary: String? = null,
    val messages: List<PicoClawMessage> = emptyList(),
)

@Serializable
data class PicoClawModelItem(
    @SerialName("model_name") val modelName: String,
    val provider: String? = null,
    val model: String? = null,
    val available: Boolean = false,
    @SerialName("is_default") val isDefault: Boolean = false,
    val status: String? = null,
)

@Serializable
data class PicoClawModelsResponse(
    @SerialName("default_model") val defaultModel: String? = null,
    val models: List<PicoClawModelItem> = emptyList(),
)

// ── MCP presets ────────────────────────────────────────────────────────────

@Serializable
data class McpFieldItem(
    val name: String = "",
    val label: String = "",
    val secret: Boolean = false,
    val required: Boolean = false,
    val configured: Boolean = false,
    val placeholder: String? = null,
    @SerialName("env_var") val envVar: String? = null,
)

@Serializable
data class McpPresetItem(
    val name: String = "",
    @SerialName("display_name") val displayName: String = "",
    val category: String = "",
    val description: String = "",
    @SerialName("docs_url") val docsUrl: String? = null,
    val transport: String? = null,
    val auth: String? = null,
    val requires: String? = null,
    val note: String? = null,
    @SerialName("install_supported") val installSupported: Boolean = false,
    val installed: Boolean = false,
    val configured: Boolean = false,
    val available: Boolean = false,
    val enabled: Boolean? = null,
    val status: String? = null,
    @SerialName("connection_summary") val connectionSummary: String? = null,
    @SerialName("required_fields") val requiredFields: List<McpFieldItem> = emptyList(),
    @SerialName("tool_names") val toolNames: List<String> = emptyList(),
    @SerialName("enabled_tools") val enabledTools: List<String> = emptyList(),
    val source: String? = null,
)

@Serializable
data class McpActionMessage(val ok: Boolean = false, val message: String? = null)

@Serializable
data class McpPresetsPayload(
    val presets: List<McpPresetItem> = emptyList(),
    @SerialName("installed_count") val installedCount: Int = 0,
    @SerialName("last_action") val lastAction: McpActionMessage? = null,
    @SerialName("requires_restart") val requiresRestart: Boolean = false,
)

// ── Automations ────────────────────────────────────────────────────────────

@Serializable
data class AutomationSchedule(
    val kind: String = "",
    @SerialName("at_ms") val atMs: Long? = null,
    @SerialName("every_ms") val everyMs: Long? = null,
    val expr: String? = null,
    val tz: String? = null,
)

@Serializable
data class AutomationRun(
    @SerialName("run_at_ms") val runAtMs: Long = 0,
    val status: String = "",
    @SerialName("duration_ms") val durationMs: Long? = null,
    val error: String? = null,
)

@Serializable
data class AutomationState(
    @SerialName("next_run_at_ms") val nextRunAtMs: Long? = null,
    @SerialName("last_run_at_ms") val lastRunAtMs: Long? = null,
    @SerialName("last_status") val lastStatus: String? = null,
    @SerialName("last_error") val lastError: String? = null,
    val pending: Boolean = false,
    @SerialName("run_history") val runHistory: List<AutomationRun> = emptyList(),
)

@Serializable
data class AutomationPayload(
    val message: String = "",
    val kind: String? = null,
    val command: String? = null,
)

@Serializable
data class AutomationOrigin(
    @SerialName("session_key") val sessionKey: String? = null,
    val channel: String? = null,
    @SerialName("chat_id") val chatId: String? = null,
    val title: String? = null,
    val preview: String? = null,
)

@Serializable
data class AutomationJob(
    val id: String = "",
    val name: String = "",
    val enabled: Boolean = false,
    val kind: String? = null,
    val schedule: AutomationSchedule = AutomationSchedule(),
    val payload: AutomationPayload = AutomationPayload(),
    val state: AutomationState = AutomationState(),
    val protected: Boolean = false,
    @SerialName("delete_after_run") val deleteAfterRun: Boolean = false,
    @SerialName("created_at_ms") val createdAtMs: Long? = null,
    @SerialName("updated_at_ms") val updatedAtMs: Long? = null,
    val origin: AutomationOrigin? = null,
)

@Serializable
data class AutomationsPayload(val jobs: List<AutomationJob> = emptyList())

@Serializable
data class AutomationResultPayload(val response: String? = null)
