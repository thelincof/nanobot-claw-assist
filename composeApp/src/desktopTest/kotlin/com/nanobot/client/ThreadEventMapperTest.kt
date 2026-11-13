package com.nanobot.client

import com.nanobot.client.core.model.GatewayJson
import com.nanobot.client.core.model.ThreadEventMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.jsonObject

class ThreadEventMapperTest {
    private fun event(json: String) = GatewayJson.parseToJsonElement(json).jsonObject

    @Test
    fun projectsNanobot041EventsIntoMessages() {
        val events = listOf(
            event(
                """{"event":"user_message","projection_id":"u1","turn_id":"t1","turn_phase":"user","turn_seq":1,"created_at_ms":1000,"text":"проверка связи"}""",
            ),
            event(
                """{"event":"message","projection_id":"a1","turn_id":"t1","turn_phase":"activity","turn_seq":4,"created_at_ms":2000,"kind":"tool_hint","tool_events":[{"phase":"start","call_id":"c1","name":"exec","arguments":{"command":"ls"}}]}""",
            ),
            event(
                """{"event":"message","projection_id":"a2","turn_id":"t1","turn_phase":"activity","turn_seq":5,"created_at_ms":2500,"kind":"progress","tool_events":[{"phase":"end","call_id":"c1","name":"exec","result":"ok"}]}""",
            ),
            event(
                """{"event":"stream_end","projection_id":"a3","turn_id":"t1","turn_phase":"answer","turn_seq":6,"created_at_ms":3000,"text":"Готово"}""",
            ),
            event(
                """{"event":"turn_end","projection_id":"a4","turn_id":"t1","turn_phase":"complete","turn_seq":7,"created_at_ms":3100,"goal_state":{"active":false}}""",
            ),
        )

        val projection = ThreadEventMapper.project(events)

        assertEquals(2, projection.messages.size)
        assertEquals("проверка связи", projection.messages[0].content)
        val assistant = projection.messages[1]
        assertEquals("Готово", assistant.content)
        assertEquals(1, assistant.toolEvents?.size)
        assertEquals("end", assistant.toolEvents?.single()?.phase)
        assertEquals(false, projection.goalState?.active)
    }
}
