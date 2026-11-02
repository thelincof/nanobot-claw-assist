package com.nanobot.client.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class Ready(
    @SerialName("chat_id") val chatId: String,
    @SerialName("client_id") val clientId: String? = null,
)

@Serializable
data class Attached(
    @SerialName("chat_id") val chatId: String,
    val temporary: Boolean = false,
    @SerialName("model_preset") val modelPreset: String? = null,
)

@Serializable
data class MessageAccepted(
    @SerialName("chat_id") val chatId: String,
    @SerialName("turn_id") val turnId: String,
)

@Serializable
data class UserMessage(
    @SerialName("chat_id") val chatId: String,
    val text: String = "",
    @SerialName("turn_id") val turnId: String? = null,
    @SerialName("starts_turn") val startsTurn: Boolean = false,
    @SerialName("active_turn_id") val activeTurnId: String? = null,
    @SerialName("started_at") val startedAt: Double? = null,
    @SerialName("media_urls") val mediaUrls: List<UiImage>? = null,
)

@Serializable
data class MessageEvent(
    @SerialName("chat_id") val chatId: String,
    val text: String = "",
    val kind: String? = null,
    val media: List<String>? = null,
    @SerialName("media_urls") val mediaUrls: List<UiImage>? = null,
    @SerialName("tool_events") val toolEvents: List<ToolProgressEvent>? = null,
    @SerialName("latency_ms") val latencyMs: Long? = null,
    @SerialName("turn_id") val turnId: String? = null,
    @SerialName("turn_phase") val turnPhase: String? = null,
    @SerialName("turn_seq") val turnSeq: Int? = null,
    @SerialName("reply_to") val replyTo: String? = null,
    val source: UiMessageSource? = null,
    @SerialName("agent_ui") val agentUi: AgentUiBlob? = null,
)

@Serializable
data class FileEditEvent(
    @SerialName("chat_id") val chatId: String,
    val edits: List<UiFileEdit> = emptyList(),
    @SerialName("turn_id") val turnId: String? = null,
    @SerialName("turn_phase") val turnPhase: String? = null,
    @SerialName("turn_seq") val turnSeq: Int? = null,
)

@Serializable
data class DeltaEvent(
    @SerialName("chat_id") val chatId: String,
    val text: String = "",
    @SerialName("stream_id") val streamId: String? = null,
    @SerialName("turn_id") val turnId: String? = null,
    @SerialName("turn_phase") val turnPhase: String? = null,
    @SerialName("turn_seq") val turnSeq: Int? = null,
)

@Serializable
data class ReasoningDeltaEvent(
    @SerialName("chat_id") val chatId: String,
    val text: String = "",
    @SerialName("stream_id") val streamId: String? = null,
    @SerialName("turn_id") val turnId: String? = null,
    @SerialName("turn_phase") val turnPhase: String? = null,
    @SerialName("turn_seq") val turnSeq: Int? = null,
)

@Serializable
data class ReasoningEndEvent(
    @SerialName("chat_id") val chatId: String,
    @SerialName("stream_id") val streamId: String? = null,
    @SerialName("turn_id") val turnId: String? = null,
)

@Serializable
data class StreamEndEvent(
    @SerialName("chat_id") val chatId: String,
    @SerialName("stream_id") val streamId: String? = null,
    val text: String? = null,
    val resuming: Boolean? = null,
    @SerialName("merge_next") val mergeNext: Boolean? = null,
    @SerialName("turn_id") val turnId: String? = null,
    @SerialName("turn_phase") val turnPhase: String? = null,
    @SerialName("turn_seq") val turnSeq: Int? = null,
)

@Serializable
data class TurnEndEvent(
    @SerialName("chat_id") val chatId: String,
    @SerialName("latency_ms") val latencyMs: Long? = null,
    @SerialName("turn_id") val turnId: String? = null,
    @SerialName("turn_phase") val turnPhase: String? = null,
    @SerialName("turn_seq") val turnSeq: Int? = null,
    @SerialName("goal_state") val goalState: GoalStatePayload? = null,
)

@Serializable
data class GoalStatusEvent(
    @SerialName("chat_id") val chatId: String,
    val status: String = "",
    @SerialName("started_at") val startedAt: Double? = null,
    @SerialName("turn_id") val turnId: String? = null,
)

@Serializable
data class SessionUpdatedEvent(
    @SerialName("chat_id") val chatId: String,
    val scope: String? = null,
)

@Serializable
data class TranscriptionResultEvent(
    @SerialName("request_id") val requestId: String,
    val text: String = "",
)

@Serializable
data class TranscriptionErrorEvent(
    @SerialName("request_id") val requestId: String? = null,
    val detail: String? = null,
    val provider: String? = null,
)

@Serializable
data class RuntimeModelUpdatedEvent(
    @SerialName("model_name") val modelName: String = "",
    @SerialName("model_preset") val modelPreset: String? = null,
)

@Serializable
data class TurnModelUpdatedEvent(
    @SerialName("chat_id") val chatId: String,
    @SerialName("model_name") val modelName: String,
)

@Serializable
data class GoalStateEvent(
    @SerialName("chat_id") val chatId: String,
    @SerialName("goal_state") val goalState: GoalStatePayload,
)

@Serializable
data class ErrorEvent(
    @SerialName("chat_id") val chatId: String? = null,
    val detail: String? = null,
    val reason: String? = null,
    @SerialName("turn_id") val turnId: String? = null,
)

@Serializable
data class WebUiResponseEvent(
    @SerialName("request_id") val requestId: String,
    val ok: Boolean = false,
    val result: JsonObject? = null,
    val error: WebUiResponseError? = null,
)

@Serializable
data class WebUiResponseError(
    val status: Int? = null,
    val message: String? = null,
)

sealed interface InboundEvent {
    data class Ready(val value: com.nanobot.client.core.model.Ready) : InboundEvent
    data class Attached(val value: com.nanobot.client.core.model.Attached) : InboundEvent
    data class MessageAccepted(val value: com.nanobot.client.core.model.MessageAccepted) : InboundEvent
    data class UserMessage(val value: com.nanobot.client.core.model.UserMessage) : InboundEvent
    data class Message(val value: com.nanobot.client.core.model.MessageEvent) : InboundEvent
    data class FileEdit(val value: com.nanobot.client.core.model.FileEditEvent) : InboundEvent
    data class Delta(val value: com.nanobot.client.core.model.DeltaEvent) : InboundEvent
    data class ReasoningDelta(val value: com.nanobot.client.core.model.ReasoningDeltaEvent) : InboundEvent
    data class ReasoningEnd(val value: com.nanobot.client.core.model.ReasoningEndEvent) : InboundEvent
    data class StreamEnd(val value: com.nanobot.client.core.model.StreamEndEvent) : InboundEvent
    data class TurnEnd(val value: com.nanobot.client.core.model.TurnEndEvent) : InboundEvent
    data class GoalStatus(val value: com.nanobot.client.core.model.GoalStatusEvent) : InboundEvent
    data class SessionUpdated(val value: com.nanobot.client.core.model.SessionUpdatedEvent) : InboundEvent
    data class TranscriptionResult(val value: com.nanobot.client.core.model.TranscriptionResultEvent) : InboundEvent
    data class TranscriptionError(val value: com.nanobot.client.core.model.TranscriptionErrorEvent) : InboundEvent
    data class RuntimeModelUpdated(val value: com.nanobot.client.core.model.RuntimeModelUpdatedEvent) : InboundEvent
    data class TurnModelUpdated(val value: com.nanobot.client.core.model.TurnModelUpdatedEvent) : InboundEvent
    data class GoalState(val value: com.nanobot.client.core.model.GoalStateEvent) : InboundEvent
    data class Error(val value: com.nanobot.client.core.model.ErrorEvent) : InboundEvent
    data class WebUiResponse(val value: com.nanobot.client.core.model.WebUiResponseEvent) : InboundEvent
    data class PicoMessageCreate(
        val messageId: String,
        val content: String,
        val kind: String?,
        val toolEvents: List<ToolProgressEvent>?,
        val sessionId: String?,
        val contextUsage: ContextUsagePayload? = null,
    ) : InboundEvent
    data class PicoMessageUpdate(
        val messageId: String,
        val content: String,
        val kind: String?,
        val toolEvents: List<ToolProgressEvent>?,
        val sessionId: String?,
        val contextUsage: ContextUsagePayload? = null,
    ) : InboundEvent
    data class PicoTyping(val isTyping: Boolean, val sessionId: String?) : InboundEvent
}

fun parseInboundEvent(raw: String): InboundEvent? {
    val obj: JsonObject = runCatching { GatewayJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
    val type = obj["type"]?.jsonPrimitive?.contentOrNull
    val event = obj["event"]?.jsonPrimitive?.contentOrNull

    if (type != null) {
        val sessionId = obj["session_id"]?.jsonPrimitive?.contentOrNull
        val payload = obj["payload"]?.let { runCatching { it.jsonObject }.getOrNull() }
        when (type) {
            "message.create", "media.create" -> {
                val messageId = payload?.get("message_id")?.jsonPrimitive?.contentOrNull ?: "pico-${System.currentTimeMillis()}"
                val content = payload?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
                val kind = payload?.get("kind")?.jsonPrimitive?.contentOrNull
                val toolEvents = extractPicoTools(payload)
                val contextUsage = extractContextUsage(payload)
                return InboundEvent.PicoMessageCreate(messageId, content, kind, toolEvents, sessionId, contextUsage)
            }
            "message.update" -> {
                val messageId = payload?.get("message_id")?.jsonPrimitive?.contentOrNull ?: return null
                val content = payload?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
                val kind = payload?.get("kind")?.jsonPrimitive?.contentOrNull
                val toolEvents = extractPicoTools(payload)
                val contextUsage = extractContextUsage(payload)
                return InboundEvent.PicoMessageUpdate(messageId, content, kind, toolEvents, sessionId, contextUsage)
            }
            "typing.start" -> return InboundEvent.PicoTyping(true, sessionId)
            "typing.stop" -> return InboundEvent.PicoTyping(false, sessionId)
            "error" -> {
                val msg = payload?.get("message")?.jsonPrimitive?.contentOrNull ?: "PicoClaw error"
                return InboundEvent.Error(ErrorEvent(chatId = sessionId, detail = msg))
            }
        }
    }

    if (event == null) return null
    return runCatching {
        when (event) {
            "ready" -> InboundEvent.Ready(GatewayJson.decodeFromJsonElement(Ready.serializer(), obj))
            "attached" -> InboundEvent.Attached(GatewayJson.decodeFromJsonElement(Attached.serializer(), obj))
            "message_accepted" -> InboundEvent.MessageAccepted(GatewayJson.decodeFromJsonElement(MessageAccepted.serializer(), obj))
            "user_message" -> InboundEvent.UserMessage(GatewayJson.decodeFromJsonElement(UserMessage.serializer(), obj))
            "message" -> InboundEvent.Message(GatewayJson.decodeFromJsonElement(MessageEvent.serializer(), obj))
            "file_edit" -> InboundEvent.FileEdit(GatewayJson.decodeFromJsonElement(FileEditEvent.serializer(), obj))
            "delta" -> InboundEvent.Delta(GatewayJson.decodeFromJsonElement(DeltaEvent.serializer(), obj))
            "reasoning_delta" -> InboundEvent.ReasoningDelta(GatewayJson.decodeFromJsonElement(ReasoningDeltaEvent.serializer(), obj))
            "reasoning_end" -> InboundEvent.ReasoningEnd(GatewayJson.decodeFromJsonElement(ReasoningEndEvent.serializer(), obj))
            "stream_end" -> InboundEvent.StreamEnd(GatewayJson.decodeFromJsonElement(StreamEndEvent.serializer(), obj))
            "turn_end" -> InboundEvent.TurnEnd(GatewayJson.decodeFromJsonElement(TurnEndEvent.serializer(), obj))
            "goal_status" -> InboundEvent.GoalStatus(GatewayJson.decodeFromJsonElement(GoalStatusEvent.serializer(), obj))
            "session_updated" -> InboundEvent.SessionUpdated(GatewayJson.decodeFromJsonElement(SessionUpdatedEvent.serializer(), obj))
            "transcription_result" -> InboundEvent.TranscriptionResult(GatewayJson.decodeFromJsonElement(TranscriptionResultEvent.serializer(), obj))
            "transcription_error" -> InboundEvent.TranscriptionError(GatewayJson.decodeFromJsonElement(TranscriptionErrorEvent.serializer(), obj))
            "runtime_model_updated" -> InboundEvent.RuntimeModelUpdated(GatewayJson.decodeFromJsonElement(RuntimeModelUpdatedEvent.serializer(), obj))
            "turn_model_updated" -> InboundEvent.TurnModelUpdated(GatewayJson.decodeFromJsonElement(TurnModelUpdatedEvent.serializer(), obj))
            "goal_state" -> InboundEvent.GoalState(GatewayJson.decodeFromJsonElement(GoalStateEvent.serializer(), obj))
            "error" -> InboundEvent.Error(GatewayJson.decodeFromJsonElement(ErrorEvent.serializer(), obj))
            "webui_response" -> InboundEvent.WebUiResponse(GatewayJson.decodeFromJsonElement(WebUiResponseEvent.serializer(), obj))
            else -> null
        }
    }.getOrNull()
}

private fun extractPicoTools(payload: JsonObject?): List<ToolProgressEvent>? {
    val arr = payload?.get("tool_calls") as? kotlinx.serialization.json.JsonArray
    if (arr != null && arr.isNotEmpty()) {
        return arr.mapNotNull { item ->
            val toolObj = item as? JsonObject ?: return@mapNotNull null
            val id = toolObj["id"]?.jsonPrimitive?.contentOrNull
            val fn = toolObj["function"] as? JsonObject
            val name = fn?.get("name")?.jsonPrimitive?.contentOrNull
            val args = fn?.get("arguments")
            val explanation = (toolObj["extra_content"] as? JsonObject)?.get("tool_feedback_explanation")
            ToolProgressEvent(
                callId = id,
                name = name,
                arguments = args,
                result = explanation,
            )
        }.takeIf { it.isNotEmpty() }
    }

    // Текстовый fallback, как в веб-консоли PicoClaw: 🔧 `tool_name`
    val content = payload?.get("content")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    val toolRegex = Regex("""^🔧\s+`([^`\n\r]*?)(?:\.{1,2})?`[^\n\r]*(?:\r?\n([\s\S]*))?""")
    val match = toolRegex.find(content) ?: return null
    val toolName = match.groupValues.getOrNull(1)?.trim()
    val body = match.groupValues.getOrNull(2)?.trim().orEmpty()
    val jsonMatch = Regex("""```(?:json)?\r?\n([\s\S]*?)\r?\n```""").find(body)
    val argsStr = jsonMatch?.groupValues?.getOrNull(1)?.trim()
    val explanationStr = body.replace(Regex("""```(?:json)?\r?\n[\s\S]*?\r?\n```"""), "").trim()

    return listOf(
        ToolProgressEvent(
            name = toolName,
            arguments = argsStr?.let { kotlinx.serialization.json.JsonPrimitive(it) },
            result = explanationStr.takeIf { it.isNotBlank() }?.let { kotlinx.serialization.json.JsonPrimitive(it) },
        ),
    )
}

private fun extractContextUsage(payload: JsonObject?): ContextUsagePayload? {
    val cu = payload?.get("context_usage")?.let { runCatching { it.jsonObject }.getOrNull() } ?: return null
    val used = cu["used_tokens"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return null
    val total = cu["total_tokens"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return null
    if (total <= 0) return null
    val history = cu["history_tokens"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
    val compress = cu["compress_at_tokens"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
    val summarize = cu["summarize_at_tokens"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
    val percent = cu["used_percent"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
        ?: (used.toDouble() / total.toDouble() * 100.0)
    return ContextUsagePayload(
        usedTokens = used,
        totalTokens = total,
        historyTokens = history,
        compressAtTokens = compress,
        summarizeAtTokens = summarize,
        usedPercent = percent,
    )
}

@Serializable
@JsonClassDiscriminator("type")
sealed interface OutboundFrame {
    @Serializable
    @SerialName("new_chat")
    data class NewChat(
        @SerialName("workspace_scope") val workspaceScope: JsonObject? = null,
    ) : OutboundFrame

    @Serializable
    @SerialName("set_workspace_scope")
    data class SetWorkspaceScope(
        @SerialName("chat_id") val chatId: String,
        @SerialName("workspace_scope") val workspaceScope: JsonObject? = null,
    ) : OutboundFrame

    @Serializable
    @SerialName("attach")
    data class Attach(@SerialName("chat_id") val chatId: String) : OutboundFrame

    @Serializable
    @SerialName("message")
    data class Message(
        @SerialName("chat_id") val chatId: String,
        val content: String,
        @SerialName("turn_id") val turnId: String,
        val webui: Boolean = true,
        @SerialName("workspace_scope") val workspaceScope: JsonObject? = null,
        val media: List<OutboundAttachment>? = null,
    ) : OutboundFrame

    @Serializable
    @SerialName("transcribe_audio")
    data class TranscribeAudio(
        @SerialName("request_id") val requestId: String,
        @SerialName("data_url") val dataUrl: String,
        @SerialName("duration_ms") val durationMs: Long? = null,
    ) : OutboundFrame

    @Serializable
    @SerialName("webui_request")
    data class WebUiRequest(
        @SerialName("request_id") val requestId: String,
        val action: String,
        val payload: JsonObject = JsonObject(emptyMap()),
    ) : OutboundFrame
}
