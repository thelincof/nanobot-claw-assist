package com.nanobot.client

import com.nanobot.client.core.model.GatewayJson
import com.nanobot.client.core.model.SessionsPayload
import com.nanobot.client.ui.sessionHandleColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SidebarStateTest {
    @Test
    fun handleColorIsDeterministicAndDistinct() {
        val first = sessionHandleColor("handle_6d7439aa2114cae47cea2a165f4f94e7", dark = false)
        val again = sessionHandleColor("handle_6d7439aa2114cae47cea2a165f4f94e7", dark = false)
        val other = sessionHandleColor("handle_daa223c99731017d2eb34f1a644ad9bb", dark = false)
        assertEquals(first, again)
        assertNotEquals(first, other)
        assertTrue(first.alpha == 1f)
    }

    @Test
    fun parsesHandleAndRunState() {
        val payload = GatewayJson.decodeFromString(
            SessionsPayload.serializer(),
            """
            {"sessions":[
              {"key":"websocket:abc","chat_id":"abc","title":"Chat",
               "handle":{"id":"handle_deadbeef","name":"tiyu"},
               "run_started_at":1793565000.5}
            ]}
            """.trimIndent(),
        )
        val row = payload.sessions.single()
        assertEquals("tiyu", row.handle?.name)
        assertEquals("handle_deadbeef", row.handle?.id)
        assertTrue(row.running)
    }
}
