package com.nanobot.client

import com.nanobot.client.app.TranscriptMerge
import com.nanobot.client.core.model.UiMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TranscriptMergeTest {
    private fun user(turn: String, text: String) = UiMessage(
        id = "u-$turn",
        role = "user",
        content = text,
        turnId = turn,
        createdAt = turn.removePrefix("t").toLongOrNull() ?: 0L,
    )

    private fun assistant(turn: String, text: String) = UiMessage(
        id = "a-$turn",
        role = "assistant",
        content = text,
        turnId = turn,
        createdAt = (turn.removePrefix("t").toLongOrNull() ?: 0L) + 1,
    )

    @Test
    fun latestPageReplacesTurnsInPlaceAndAppendsNewTurns() {
        val existing = listOf(
            user("t1", "one"), assistant("t1", "answer one"),
            user("t2", "two"), assistant("t2", "old answer two"),
        )
        val latest = listOf(
            user("t2", "two"), assistant("t2", "new answer two"),
            user("t3", "three"), assistant("t3", "answer three"),
        )

        val merged = TranscriptMerge.replaceRecent(existing, latest)

        assertEquals(
            listOf("t1", "t1", "t2", "t2", "t3", "t3"),
            merged.map { it.turnId },
        )
        assertEquals("old answer two", existing.last().content)
        assertEquals("new answer two", merged[3].content)
        assertEquals("answer three", merged.last().content)
    }

    @Test
    fun partialAssistantPageKeepsExistingUserRow() {
        val existing = listOf(user("t1", "question"), assistant("t1", "old"))
        val latest = listOf(assistant("t1", "completed answer"))

        val merged = TranscriptMerge.replaceRecent(existing, latest)

        assertEquals(listOf("user", "assistant"), merged.map { it.role })
        assertEquals("question", merged.first().content)
        assertEquals("completed answer", merged.last().content)
    }

    @Test
    fun olderPagePrependsOnlyMissingTurns() {
        val existing = listOf(user("t2", "two"), assistant("t2", "answer two"))
        val older = listOf(
            user("t1", "one"), assistant("t1", "answer one"),
            user("t2", "stale two"), assistant("t2", "stale answer two"),
        )

        val merged = TranscriptMerge.prependOlder(older, existing)

        assertEquals(
            listOf("t1", "t1", "t2", "t2"),
            merged.map { it.turnId },
        )
        assertEquals("answer two", merged.last().content)
    }

    @Test
    fun exactEventDuplicatesAreDropped() {
        val first = buildJsonObject {
            put("event", "delta")
            put("turn_id", "t1")
            put("text", "hello")
        }
        val second = buildJsonObject {
            put("event", "delta")
            put("turn_id", "t1")
            put("text", " world")
        }

        val merged = TranscriptMerge.mergeEvents(listOf(first), listOf(first, second))

        assertEquals(2, merged.size)
        assertTrue(merged.first().toString().contains("hello"))
        assertTrue(merged.last().toString().contains("world"))
    }
}
