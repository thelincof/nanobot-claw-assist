package com.nanobot.client.app

import com.nanobot.client.core.model.UiMessage
import kotlinx.serialization.json.JsonObject

/**
 * Merges paginated transcript snapshots.
 *
 * A Nanobot page is a contiguous suffix/prefix of whole turns. Replacing a turn
 * as one unit avoids moving a newly completed turn into the middle of history
 * and avoids duplicates when the server reissues message ids.
 */
object TranscriptMerge {
    fun messageKey(message: UiMessage): String =
        message.turnId?.takeIf { it.isNotBlank() }
            ?: message.activitySegmentId?.takeIf { it.isNotBlank() }?.let { "segment:$it" }
            ?: message.id.takeIf { it.isNotBlank() }?.let { "id:$it" }
            ?: "anon:${message.role}:${message.createdAt}"

    /** Replace the suffix represented by [latest], keeping older loaded pages. */
    fun replaceRecent(
        existing: List<UiMessage>,
        latest: List<UiMessage>,
    ): List<UiMessage> {
        if (latest.isEmpty()) return existing
        val existingByKey = existing.groupBy(::messageKey)
        val latestByKey = latest.groupBy(::messageKey)
        val result = existing.filter { messageKey(it) !in latestByKey }.toMutableList()

        latestByKey.forEach { (key, latestRows) ->
            val existingRows = existingByKey[key].orEmpty()
            val completeTurn = latestRows.any { it.role == "user" } || existingRows.isEmpty()
            result += if (completeTurn) {
                latestRows
            } else {
                // Some gateways can return only the tail of a turn in a page.
                // Keep the older rows and overlay the rows present in this page.
                val rows = linkedMapOf<String, UiMessage>()
                existingRows.forEach { rows[rowKey(it)] = it }
                latestRows.forEach { rows[rowKey(it)] = it }
                rows.values.toList()
            }
        }
        return result
    }

    /** Put an older page in front; never replace the newer copy of a turn. */
    fun prependOlder(
        older: List<UiMessage>,
        existing: List<UiMessage>,
    ): List<UiMessage> {
        if (older.isEmpty()) return existing
        val existingKeys = existing.mapTo(hashSetOf(), ::messageKey)
        val newTurnKeys = older
            .map(::messageKey)
            .filterNot(existingKeys::contains)
            .toSet()
        val newOlder = older.filter { messageKey(it) in newTurnKeys }
        return newOlder + existing
    }

    /**
     * Event pages are append-only. Deduplicate exact events while preserving
     * their original order; [com.nanobot.client.core.model.ThreadEventMapper]
     * then rebuilds one activity card per turn.
     */
    fun mergeEvents(
        older: List<JsonObject>,
        newer: List<JsonObject>,
    ): List<JsonObject> {
        if (older.isEmpty()) return newer
        if (newer.isEmpty()) return older
        val merged = linkedMapOf<String, JsonObject>()
        older.forEach { merged[it.toString()] = it }
        newer.forEach { merged[it.toString()] = it }
        return merged.values.toList()
    }

    private fun rowKey(message: UiMessage): String =
        message.id.ifBlank { "${message.role}:${message.createdAt}" }
}
