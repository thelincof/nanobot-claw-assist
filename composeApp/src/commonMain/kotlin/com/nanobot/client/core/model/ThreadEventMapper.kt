package com.nanobot.client.core.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Nanobot 0.4+ returns the web thread as canonical projection events instead of
 * prepared `messages`. This mapper converts that event stream back into the
 * client's [UiMessage] model, which the existing projector/rendering pipeline
 * already knows how to display.
 */
data class ThreadProjection(
    val messages: List<UiMessage>,
    val goalState: GoalStatePayload? = null,
)

object ThreadEventMapper {
    private class Bucket(val key: String) {
        var turnId: String? = null
        var turnPhase: String? = null
        var turnSeq: Int? = null
        var createdAt: Long = 0
        var latencyMs: Long? = null
        var content: String = ""
        var reasoning: String? = null
        var reasoningStreaming: Boolean = false
        var isStreaming: Boolean = false
        var source: UiMessageSource? = null
        var assistantMedia: List<UiMediaAttachment>? = null
        val users = mutableListOf<UiMessage>()
        val tools = linkedMapOf<String, ToolProgressEvent>()
        val edits = linkedMapOf<String, UiFileEdit>()
        val traces = linkedSetOf<String>()

        fun hasAssistantContent(): Boolean =
            content.isNotBlank() || !reasoning.isNullOrBlank() ||
                tools.isNotEmpty() || edits.isNotEmpty() || traces.isNotEmpty()

        fun assistant(id: String): UiMessage? {
            if (!hasAssistantContent()) return null
            return UiMessage(
                id = id,
                role = "assistant",
                content = content,
                createdAt = createdAt,
                reasoning = reasoning,
                reasoningStreaming = reasoningStreaming,
                isStreaming = isStreaming,
                toolEvents = tools.values.toList().takeIf { it.isNotEmpty() },
                fileEdits = edits.values.toList().takeIf { it.isNotEmpty() },
                traces = traces.toList().takeIf { it.isNotEmpty() },
                latencyMs = latencyMs,
                source = source,
                turnId = turnId,
                turnPhase = turnPhase,
                turnSeq = turnSeq,
            )
        }
    }

    fun project(events: List<JsonObject>): ThreadProjection {
        val buckets = linkedMapOf<String, Bucket>()
        var goalState: GoalStatePayload? = null

        fun bucketFor(event: JsonObject, fallbackIndex: Int): Bucket {
            val turnId = event.string("turn_id")?.takeIf { it.isNotBlank() }
            val key = turnId ?: event.string("projection_id")?.takeIf { it.isNotBlank() } ?: "event-$fallbackIndex"
            return buckets.getOrPut(key) { Bucket(key) }.also { bucket ->
                bucket.turnId = bucket.turnId ?: turnId
                bucket.turnPhase = bucket.turnPhase ?: event.string("turn_phase")
                bucket.turnSeq = bucket.turnSeq ?: event.int("turn_seq")
                if (bucket.createdAt == 0L) bucket.createdAt = event.long("created_at_ms") ?: 0L
            }
        }

        events.forEachIndexed { index, event ->
            when (event.string("event")) {
                "model_request" -> Unit
                "context_compaction" -> Unit
                "goal_state" -> {
                    (event["goal_state"] as? JsonObject)?.let { goal ->
                        goalState = runCatching {
                            GatewayJson.decodeFromJsonElement(GoalStatePayload.serializer(), goal)
                        }.getOrNull() ?: goalState
                    }
                }
                "user_message" -> {
                    val bucket = bucketFor(event, index)
                    val text = event.string("text").orEmpty()
                    if (text.isNotBlank()) {
                        bucket.users += UiMessage(
                            id = event.string("projection_id") ?: "user-${bucket.key}-${bucket.users.size}",
                            role = "user",
                            content = text,
                            createdAt = event.long("created_at_ms") ?: bucket.createdAt,
                            turnId = event.string("turn_id"),
                            turnPhase = event.string("turn_phase"),
                            turnSeq = event.int("turn_seq"),
                            media = event.mediaAttachments(),
                        )
                    }
                }
                "delta" -> {
                    val bucket = bucketFor(event, index)
                    bucket.content += event.string("text").orEmpty()
                    bucket.isStreaming = true
                }
                "stream_end" -> {
                    val bucket = bucketFor(event, index)
                    val text = event.string("text").orEmpty()
                    if (text.isNotBlank()) bucket.content = text
                    bucket.isStreaming = false
                    bucket.latencyMs = event.long("latency_ms") ?: bucket.latencyMs
                    bucket.source = event.source() ?: bucket.source
                }
                "reasoning_delta" -> {
                    val bucket = bucketFor(event, index)
                    bucket.reasoning = appendUnique(bucket.reasoning, event.string("text").orEmpty())
                    bucket.reasoningStreaming = true
                }
                "reasoning_end" -> {
                    bucketFor(event, index).reasoningStreaming = false
                }
                "message" -> {
                    val bucket = bucketFor(event, index)
                    val kind = event.string("kind")
                    val text = event.string("text").orEmpty()
                    when (kind) {
                        "tool_hint", "progress" -> {
                            if (text.isNotBlank()) bucket.traces += text
                            bucket.mergeTools(event)
                        }
                        "reasoning" -> {
                            bucket.reasoning = appendUnique(bucket.reasoning, text)
                            bucket.reasoningStreaming = false
                        }
                        else -> {
                            if (text.isNotBlank()) {
                                bucket.content = appendUnique(bucket.content, text)
                                bucket.isStreaming = false
                            }
                            bucket.mergeTools(event)
                            bucket.mergeEdits(event)
                            bucket.latencyMs = event.long("latency_ms") ?: bucket.latencyMs
                            bucket.source = event.source() ?: bucket.source
                        }
                    }
                    event.mediaAttachments()?.let { media ->
                        if (media.isNotEmpty()) {
                            // Assistant media is attached to the final answer below.
                            bucket.assistantMedia = media
                        }
                    }
                }
                "file_edit" -> {
                    bucketFor(event, index).mergeEdits(event)
                }
                "turn_end" -> {
                    val bucket = bucketFor(event, index)
                    bucket.isStreaming = false
                    bucket.reasoningStreaming = false
                    (event["goal_state"] as? JsonObject)?.let { goal ->
                        goalState = runCatching {
                            GatewayJson.decodeFromJsonElement(GoalStatePayload.serializer(), goal)
                        }.getOrNull() ?: goalState
                    }
                }
            }
        }

        val messages = mutableListOf<UiMessage>()
        buckets.values.forEach { bucket ->
            messages += bucket.users
            bucket.assistant("thread-${bucket.key}")?.let { message ->
                messages += if (bucket.assistantMedia.isNullOrEmpty()) {
                    message
                } else {
                    message.copy(media = bucket.assistantMedia)
                }
            }
        }
        return ThreadProjection(messages, goalState)
    }

    private fun Bucket.mergeTools(event: JsonObject) {
        val raw = event["tool_events"] as? JsonArray ?: return
        raw.forEachIndexed { index, element ->
            val item = element as? JsonObject ?: return@forEachIndexed
            val tool = runCatching {
                GatewayJson.decodeFromJsonElement(ToolProgressEvent.serializer(), item)
            }.getOrNull() ?: return@forEachIndexed
            val key = tool.callId?.takeIf { it.isNotBlank() }
                ?: "${tool.name.orEmpty()}|${tool.arguments?.toString().orEmpty()}|$index"
            tools[key] = tools[key]?.let { existing ->
                existing.copy(
                    phase = tool.phase ?: existing.phase,
                    name = tool.name ?: existing.name,
                    arguments = tool.arguments ?: existing.arguments,
                    result = tool.result ?: existing.result,
                    error = tool.error ?: existing.error,
                    files = tool.files ?: existing.files,
                    embeds = tool.embeds ?: existing.embeds,
                    function = tool.function ?: existing.function,
                )
            } ?: tool
        }
    }

    private fun Bucket.mergeEdits(event: JsonObject) {
        val raw = event["edits"] as? JsonArray ?: return
        raw.forEachIndexed { index, element ->
            val item = element as? JsonObject ?: return@forEachIndexed
            val edit = runCatching {
                GatewayJson.decodeFromJsonElement(UiFileEdit.serializer(), item)
            }.getOrNull() ?: return@forEachIndexed
            val key = edit.callId.ifBlank { "${edit.tool}|${edit.path}|$index" }
            edits[key] = edit
        }
    }

    private fun appendUnique(existing: String?, incoming: String): String {
        val next = incoming.trim()
        if (next.isEmpty()) return existing.orEmpty()
        val current = existing.orEmpty()
        return when {
            current.isEmpty() -> next
            current == next -> current
            current.contains(next) -> current
            next.contains(current) -> next
            else -> "$current\n\n$next"
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.source(): UiMessageSource? {
        val source = this["source"] as? JsonObject ?: return null
        val kind = source.string("kind")?.takeIf { it.isNotBlank() } ?: return null
        return UiMessageSource(kind = kind, label = source.string("label"))
    }

    private fun JsonObject.mediaAttachments(): List<UiMediaAttachment>? {
        val raw = this["media_urls"] as? JsonArray ?: return null
        val result = raw.mapNotNull { element ->
            when (element) {
                is JsonObject -> {
                    val url = element.string("url") ?: return@mapNotNull null
                    UiMediaAttachment(
                        kind = element.string("kind").orEmpty(),
                        url = url,
                        name = element.string("name"),
                    )
                }
                is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                    UiMediaAttachment(kind = "", url = it)
                }
                else -> null
            }
        }
        return result.takeIf { it.isNotEmpty() }
    }
}
