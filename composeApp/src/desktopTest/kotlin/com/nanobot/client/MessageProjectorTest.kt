package com.nanobot.client

import com.nanobot.client.app.MessageProjector
import com.nanobot.client.core.model.ToolProgressEvent
import com.nanobot.client.core.model.UiMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class MessageProjectorTest {
    private fun toolEvent(name: String, phase: String, args: kotlinx.serialization.json.JsonObject) =
        ToolProgressEvent(
            version = 1,
            phase = phase,
            callId = "call-$name",
            name = name,
            arguments = args,
            result = kotlinx.serialization.json.JsonPrimitive("ok"),
        )

    @Test
    fun mergesToolTraceRowsIntoOneActivityCard() {
        val exec = toolEvent("exec", "end", buildJsonObject { put("command", "ls -la") })
        val search = toolEvent("web_search", "end", buildJsonObject { put("query", "nanobot") })
        val canonical = listOf(
            UiMessage(id = "u1", role = "user", content = "hi", createdAt = 1),
            UiMessage(id = "a1", role = "assistant", content = "", createdAt = 2, activitySegmentId = "activity-1"),
            UiMessage(
                id = "tr1",
                role = "tool",
                kind = "trace",
                content = "exec({\"command\": \"ls -la\"})",
                traces = listOf("exec({\"command\": \"ls -la\"})"),
                toolEvents = listOf(exec),
                createdAt = 3,
                activitySegmentId = "activity-1",
            ),
            UiMessage(id = "a2", role = "assistant", content = "", createdAt = 4, activitySegmentId = "activity-1"),
            UiMessage(
                id = "tr2",
                role = "tool",
                kind = "trace",
                content = "web_search({\"query\": \"nanobot\"})",
                traces = listOf("web_search({\"query\": \"nanobot\"})"),
                toolEvents = listOf(search),
                createdAt = 5,
                activitySegmentId = "activity-1",
            ),
            UiMessage(id = "a3", role = "assistant", content = "done", createdAt = 6),
        )

        val projected = MessageProjector.project(canonical, emptyMap(), emptyMap())

        assertEquals(listOf("user", "activity", "assistant"), projected.map { it.role })
        assertEquals("done", projected.last().content)
        val activity = projected[1]
        assertEquals(2, activity.toolEvents?.size)
        assertEquals(2, activity.traces?.size)
        assertTrue(projected.none { it.role == "tool" })
        assertTrue(projected.none { (it.content).startsWith("exec(") || it.content.startsWith("web_search(") })
    }

    @Test
    fun foldsOneActivityAndMergesToolPhases() {
        val start = ToolProgressEvent(
            phase = "start",
            callId = "call-1",
            name = "exec",
            arguments = buildJsonObject { put("command", "sleep 1") },
        )
        val end = start.copy(phase = "end", result = kotlinx.serialization.json.JsonPrimitive("ok"))
        val canonical = listOf(
            UiMessage(id = "u1", role = "user", content = "hi", turnId = "t1", createdAt = 1),
            UiMessage(
                id = "r1",
                role = "assistant",
                content = "",
                reasoning = "step 1",
                turnId = "t1",
                createdAt = 2,
            ),
            UiMessage(
                id = "t-start",
                role = "tool",
                kind = "trace",
                toolEvents = listOf(start),
                turnId = "t1",
                createdAt = 3,
            ),
            UiMessage(
                id = "r2",
                role = "assistant",
                content = "",
                reasoning = "step 2",
                turnId = "t1",
                createdAt = 4,
            ),
            UiMessage(
                id = "t-end",
                role = "tool",
                kind = "trace",
                toolEvents = listOf(end),
                turnId = "t1",
                createdAt = 5,
            ),
            UiMessage(id = "a1", role = "assistant", content = "done", turnId = "t1", createdAt = 6),
        )

        val projected = MessageProjector.project(canonical, emptyMap(), emptyMap())

        assertEquals(listOf("user", "activity", "assistant"), projected.map { it.role })
        val activity = projected[1]
        assertEquals(1, activity.toolEvents?.size)
        assertEquals("end", activity.toolEvents?.single()?.phase)
        assertEquals("step 1\n\nstep 2", activity.reasoning)
        assertEquals("done", projected.last().content)
    }

    @Test
    fun skipsEmptyAssistantMarkers() {
        val canonical = listOf(
            UiMessage(id = "u1", role = "user", content = "hi", createdAt = 1),
            UiMessage(id = "a1", role = "assistant", content = "", createdAt = 2),
            UiMessage(id = "a2", role = "assistant", content = "", reasoning = "thinking", createdAt = 3),
            UiMessage(id = "a3", role = "assistant", content = "answer", createdAt = 4),
        )

        val projected = MessageProjector.project(canonical, emptyMap(), emptyMap())

        // Reasoning is part of the turn activity now, not a separate assistant bubble.
        assertEquals(listOf("user", "activity", "assistant"), projected.map { it.role })
        assertEquals("thinking", projected[1].reasoning)
        assertEquals("answer", projected[2].content)
    }
}

class RealThreadProjectionTest {
    @Test
    fun projectsRealNanobotThreadWithoutRawToolText() {
        val path = System.getenv("NANOBOT_THREAD_JSON") ?: return
        val text = java.io.File(path).readText()
        val payload = com.nanobot.client.core.model.GatewayJson.decodeFromString(
            com.nanobot.client.core.model.WebUiThreadPayload.serializer(),
            text,
        )
        val projected = com.nanobot.client.app.MessageProjector.project(
            payload.messages,
            emptyMap(),
            emptyMap(),
        )
        assertTrue(projected.none { it.role == "tool" })
        assertTrue(
            projected.none { message ->
                val content = message.content.trimStart()
                content.startsWith("exec(") || content.startsWith("web_search(") ||
                    content.startsWith("web_fetch(") || content.startsWith("read_file(")
            },
        )
        val activities = projected.filter { it.role == "activity" }
        assertTrue(activities.isNotEmpty(), "tool rows must become activity cards")
        assertTrue(
            activities.all {
                !it.toolEvents.isNullOrEmpty() || !it.traces.isNullOrEmpty() || !it.fileEdits.isNullOrEmpty()
            },
        )
    }
}
