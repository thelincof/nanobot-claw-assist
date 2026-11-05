package com.nanobot.client.app

import com.nanobot.client.core.model.ToolProgressEvent
import com.nanobot.client.core.model.UiFileEdit
import com.nanobot.client.core.model.UiMessage

/**
 * Projects the canonical Nanobot thread + live transient/optimistic messages into
 * the flat list the chat renders.
 *
 * Nanobot stores tool calls as separate `role="tool"` / `kind="trace"` rows, and
 * keeps an empty assistant marker before each row. Rendering those rows as chat
 * bubbles leaks raw `exec({...})` text into the conversation, so they are merged
 * into one `role="activity"` message per [UiMessage.activitySegmentId].
 */
object MessageProjector {
    private fun isToolRow(message: UiMessage): Boolean =
        message.role == "tool" || message.kind == "trace"

    /** Empty assistant rows are stream/activity markers, not visible messages. */
    private fun hasVisibleContent(message: UiMessage): Boolean =
        message.content.isNotBlank() ||
            !message.reasoning.isNullOrBlank() ||
            !message.toolEvents.isNullOrEmpty() ||
            !message.fileEdits.isNullOrEmpty() ||
            !message.traces.isNullOrEmpty()

    private fun segmentKey(message: UiMessage): String =
        message.activitySegmentId?.takeIf { it.isNotBlank() }
            ?: message.turnId?.takeIf { it.isNotBlank() }
            ?: message.id.ifBlank { "anonymous" }

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

    private fun tracesOf(message: UiMessage): List<String> {
        val raw = message.traces?.takeIf { it.isNotEmpty() }
            ?: listOf(message.content).filter { it.isNotBlank() }
        return raw.map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun project(
        canonical: List<UiMessage>,
        transient: Map<String, UiMessage>,
        optimistic: Map<String, UiMessage>,
    ): List<UiMessage> {
        val result = mutableListOf<UiMessage>()
        val canonicalById = canonical.filter { it.id.isNotBlank() }.associateBy { it.id }
        val canonicalTurnIds = canonical.mapNotNull { it.turnId }.toSet()
        val emittedSegments = mutableSetOf<String>()
        val toolRowsBySegment = canonical.filter { isToolRow(it) }.groupBy { segmentKey(it) }

        canonical.forEach { message ->
            if (isToolRow(message)) {
                val key = segmentKey(message)
                if (emittedSegments.add(key)) {
                    val group = toolRowsBySegment[key].orEmpty()
                    var tools: List<ToolProgressEvent>? = null
                    var edits: List<UiFileEdit>? = null
                    val traces = mutableListOf<String>()
                    group.forEach { row ->
                        tools = mergeTools(tools, row.toolEvents)
                        edits = mergeEdits(edits, row.fileEdits)
                        tracesOf(row).forEach { trace -> if (trace !in traces) traces += trace }
                    }
                    if (!tools.isNullOrEmpty() || !edits.isNullOrEmpty() || traces.isNotEmpty()) {
                        result += UiMessage(
                            id = "activity-$key",
                            role = "activity",
                            content = "",
                            kind = "trace",
                            createdAt = message.createdAt,
                            turnId = message.turnId,
                            turnPhase = message.turnPhase,
                            turnSeq = message.turnSeq,
                            toolEvents = tools,
                            fileEdits = edits,
                            traces = traces.takeIf { it.isNotEmpty() },
                            activitySegmentId = message.activitySegmentId,
                        )
                    }
                }
                return@forEach
            }

            // Empty assistant rows are stream/activity segment markers, not messages.
            if (
                message.role == "assistant" &&
                message.content.isBlank() &&
                message.toolEvents.isNullOrEmpty() &&
                message.fileEdits.isNullOrEmpty() &&
                message.reasoning.isNullOrBlank() &&
                message.traces.isNullOrEmpty()
            ) {
                return@forEach
            }

            // Prefer transient by turnId (webui protocol) or by server message id.
            val transientByTurnId = message.turnId?.let { transient[it] }
            val transientById = if (message.id.isNotBlank()) transient[message.id] else null
            val transientMessage = transientByTurnId ?: transientById
            val useTransient = transientMessage != null &&
                message.role == "assistant" &&
                (transientMessage.isStreaming || !hasVisibleContent(message)) &&
                (
                    transientMessage.content.isNotBlank() ||
                        transientMessage.toolEvents != null ||
                        transientMessage.reasoning != null ||
                        transientMessage.traces != null
                    )
            result += if (useTransient && transientMessage != null) transientMessage else message
        }

        transient.forEach { (turnId, message) ->
            // Do not suppress a live streaming message just because the
            // canonical snapshot currently has an empty assistant marker.
            val inCanonicalByTurnId = canonical.any {
                it.turnId == turnId && it.role == "assistant" && hasVisibleContent(it)
            }
            val inCanonicalById = message.id.isNotBlank() && canonicalById.containsKey(message.id)
            if (!inCanonicalByTurnId && !inCanonicalById) {
                val hasCanonicalTools = canonical.any { isToolRow(it) && it.turnId == turnId }
                val candidate = if (hasCanonicalTools) {
                    // Canonical activity rows already render these tools; keeping
                    // the transient copy would show the same tool twice.
                    message.copy(toolEvents = null, fileEdits = null, traces = null)
                } else {
                    message
                }
                if (hasVisibleContent(candidate)) result += candidate
            }
        }

        optimistic.forEach { (turnId, message) ->
            if (turnId !in canonicalTurnIds) result += message
        }

        val seen = mutableSetOf<String>()
        return result
            .filter { message ->
                val key = if (message.id.isNotBlank()) {
                    "id:${message.id}"
                } else {
                    "t:${message.turnId}:${message.role}:${message.createdAt}"
                }
                seen.add(key)
            }
            .sortedWith(compareBy({ it.createdAt }, { it.id }))
    }
}
