package com.nanobot.client.app

import com.nanobot.client.core.model.ToolProgressEvent
import com.nanobot.client.core.model.UiFileEdit
import com.nanobot.client.core.model.UiMessage

/**
 * Projects the canonical Nanobot thread + live transient/optimistic messages into
 * the list the chat renders.
 *
 * Nanobot stores reasoning/tool calls as separate rows. They are all folded into
 * exactly one activity card per turn, so a live stream, a thread refresh and a
 * replay never produce duplicate or reordered cards.
 */
object MessageProjector {
    private fun isToolRow(message: UiMessage): Boolean =
        message.role == "tool" || message.kind == "trace"

    private fun hasActivityContent(message: UiMessage): Boolean =
        !message.reasoning.isNullOrBlank() ||
            !message.toolEvents.isNullOrEmpty() ||
            !message.fileEdits.isNullOrEmpty() ||
            !message.traces.isNullOrEmpty()

    private fun hasVisibleContent(message: UiMessage): Boolean =
        message.content.isNotBlank() || hasActivityContent(message)

    private fun keyOf(message: UiMessage, fallback: String = message.id): String =
        message.turnId?.takeIf { it.isNotBlank() }
            ?: message.activitySegmentId?.takeIf { it.isNotBlank() }?.let { "segment:$it" }
            ?: fallback.ifBlank { "anonymous" }

    private fun tracesOf(message: UiMessage): List<String> {
        val raw = message.traces?.takeIf { it.isNotEmpty() }
            ?: listOf(message.content).filter { it.isNotBlank() }
        return raw.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun mergeReasoning(existing: String?, incoming: String?): String? {
        val next = incoming?.trim().orEmpty()
        if (next.isEmpty()) return existing
        val current = existing?.trim().orEmpty()
        return when {
            current.isEmpty() -> next
            current == next -> current
            current.contains(next) -> current
            next.contains(current) -> next
            else -> "$current\n\n$next"
        }
    }

    private fun toolKey(tool: ToolProgressEvent, index: Int): String =
        tool.callId?.takeIf { it.isNotBlank() }
            ?: buildString {
                append(tool.name.orEmpty())
                append('|')
                append(tool.arguments?.toString().orEmpty())
                if (isEmpty()) append("tool-$index")
            }

    private fun mergeTool(
        existing: ToolProgressEvent,
        incoming: ToolProgressEvent,
    ): ToolProgressEvent = existing.copy(
        version = incoming.version ?: existing.version,
        // The latest event wins for the phase; start/end/error usually arrive in order.
        phase = incoming.phase ?: existing.phase,
        callId = incoming.callId ?: existing.callId,
        name = incoming.name ?: existing.name,
        arguments = incoming.arguments ?: existing.arguments,
        result = incoming.result ?: existing.result,
        error = incoming.error ?: existing.error,
        files = incoming.files ?: existing.files,
        embeds = incoming.embeds ?: existing.embeds,
        function = incoming.function ?: existing.function,
    )

    private fun editKey(edit: UiFileEdit, index: Int): String =
        edit.callId.ifBlank { "${edit.tool}|${edit.path}|$index" }

    private class TurnBucket(val key: String) {
        var turnId: String? = null
        var turnPhase: String? = null
        var turnSeq: Int? = null
        var activityCreatedAt: Long = Long.MAX_VALUE
        var activitySegmentId: String? = null
        var reasoning: String? = null
        var reasoningStreaming: Boolean = false
        var activityStreaming: Boolean = false
        val users = mutableListOf<UiMessage>()
        val finals = mutableListOf<UiMessage>()
        val tools = linkedMapOf<String, ToolProgressEvent>()
        val edits = linkedMapOf<String, UiFileEdit>()
        val traces = linkedSetOf<String>()

        fun mergeActivity(message: UiMessage) {
            if (!hasActivityContent(message)) return
            turnId = turnId ?: message.turnId
            turnPhase = turnPhase ?: message.turnPhase
            turnSeq = turnSeq ?: message.turnSeq
            if (message.createdAt > 0 && message.createdAt < activityCreatedAt) {
                activityCreatedAt = message.createdAt
            }
            activitySegmentId = activitySegmentId ?: message.activitySegmentId
            reasoning = mergeReasoning(reasoning, message.reasoning)
            reasoningStreaming = reasoningStreaming || message.reasoningStreaming
            activityStreaming = activityStreaming || message.isStreaming || message.reasoningStreaming
            message.toolEvents.orEmpty().forEachIndexed { index, tool ->
                val key = toolKey(tool, index)
                tools[key] = tools[key]?.let { mergeTool(it, tool) } ?: tool
            }
            message.fileEdits.orEmpty().forEachIndexed { index, edit ->
                edits[editKey(edit, index)] = edit
            }
            traces += tracesOf(message)
        }

        fun hasActivity(): Boolean =
            !reasoning.isNullOrBlank() ||
                tools.isNotEmpty() ||
                edits.isNotEmpty() ||
                traces.isNotEmpty()
    }

    private fun activityMessage(bucket: TurnBucket): UiMessage? {
        if (!bucket.hasActivity()) return null
        return UiMessage(
            id = "activity-${bucket.key}",
            role = "activity",
            content = "",
            kind = "trace",
            createdAt = bucket.activityCreatedAt.takeIf { it != Long.MAX_VALUE } ?: 0L,
            reasoning = bucket.reasoning,
            reasoningStreaming = bucket.reasoningStreaming,
            isStreaming = bucket.activityStreaming,
            turnId = bucket.turnId,
            turnPhase = bucket.turnPhase,
            turnSeq = bucket.turnSeq,
            toolEvents = bucket.tools.values.toList().takeIf { it.isNotEmpty() },
            fileEdits = bucket.edits.values.toList().takeIf { it.isNotEmpty() },
            traces = bucket.traces.toList().takeIf { it.isNotEmpty() },
            activitySegmentId = bucket.activitySegmentId,
        )
    }

    private fun finalMessage(message: UiMessage, id: String): UiMessage? {
        if (message.content.isBlank()) return null
        return message.copy(
            id = id,
            role = "assistant",
            reasoning = null,
            reasoningStreaming = false,
            toolEvents = null,
            fileEdits = null,
            traces = null,
        )
    }

    fun project(
        canonical: List<UiMessage>,
        transient: Map<String, UiMessage>,
        optimistic: Map<String, UiMessage>,
    ): List<UiMessage> {
        val buckets = linkedMapOf<String, TurnBucket>()
        val systemMessages = mutableListOf<UiMessage>()

        fun bucketFor(key: String): TurnBucket = buckets.getOrPut(key) { TurnBucket(key) }

        canonical.forEach { message ->
            if (message.role == "system") {
                systemMessages += message
                return@forEach
            }
            val key = keyOf(message)
            val bucket = bucketFor(key)
            bucket.turnId = bucket.turnId ?: message.turnId
            bucket.turnPhase = bucket.turnPhase ?: message.turnPhase
            bucket.turnSeq = bucket.turnSeq ?: message.turnSeq

            when {
                isToolRow(message) -> bucket.mergeActivity(message)
                message.role == "assistant" -> {
                    bucket.mergeActivity(message)
                    if (message.content.isNotBlank()) {
                        val id = if (bucket.finals.isEmpty()) {
                            "assistant-${bucket.key}"
                        } else {
                            "assistant-${bucket.key}-${bucket.finals.size + 1}"
                        }
                        finalMessage(message, id)?.let { bucket.finals += it }
                    }
                }
                message.role == "user" -> {
                    if (bucket.users.none { it.id == message.id || it.turnId == message.turnId }) {
                        bucket.users += message
                    }
                }
                hasVisibleContent(message) -> bucket.users += message
            }
        }

        optimistic.forEach { (turnId, message) ->
            val key = message.turnId?.takeIf { it.isNotBlank() } ?: turnId
            val bucket = bucketFor(key)
            bucket.turnId = bucket.turnId ?: message.turnId
                ?: turnId.takeIf { it.isNotBlank() && it != "current" }
            bucket.turnPhase = bucket.turnPhase ?: message.turnPhase
            bucket.turnSeq = bucket.turnSeq ?: message.turnSeq
            if (bucket.users.none { it.turnId == message.turnId && it.role == "user" }) {
                bucket.users += message
            }
        }

        transient.forEach { (turnId, message) ->
            val key = message.turnId?.takeIf { it.isNotBlank() } ?: turnId
            val bucket = bucketFor(key)
            bucket.turnId = bucket.turnId ?: message.turnId
                ?: turnId.takeIf { it.isNotBlank() && it != "current" }
            bucket.turnPhase = bucket.turnPhase ?: message.turnPhase
            bucket.turnSeq = bucket.turnSeq ?: message.turnSeq

            if (message.role == "assistant") {
                bucket.mergeActivity(message)
                if (message.content.isNotBlank()) {
                    val index = bucket.finals.indexOfFirst { it.content.isNotBlank() }
                    val current = bucket.finals.getOrNull(index)
                    val shouldPreferTransient = current == null ||
                        (message.isStreaming && message.content.length >= current.content.length)
                    if (shouldPreferTransient) {
                        val id = current?.id ?: "assistant-${bucket.key}"
                        finalMessage(message, id)?.let { replacement ->
                            if (index >= 0) bucket.finals[index] = replacement else bucket.finals += replacement
                        }
                    }
                }
            } else if (message.role == "user" && bucket.users.none { it.turnId == message.turnId }) {
                bucket.users += message
            }
        }

        val result = mutableListOf<UiMessage>()
        systemMessages.forEach { result += it }
        buckets.values.forEach { bucket ->
            result += bucket.users
            activityMessage(bucket)?.let { result += it }
            result += bucket.finals
        }
        return result
    }
}
