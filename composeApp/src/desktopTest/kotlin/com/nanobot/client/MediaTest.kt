package com.nanobot.client

import com.nanobot.client.core.model.GatewayJson
import com.nanobot.client.core.model.OutboundAttachment
import com.nanobot.client.core.model.OutboundFrame
import com.nanobot.client.core.model.UiMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaTest {
    @Test
    fun serializesAttachmentsInMessageFrame() {
        val frame = OutboundFrame.Message(
            chatId = "chat-1",
            content = "",
            turnId = "turn-1",
            media = listOf(OutboundAttachment("photo.png", "data:image/png;base64,AAAA")),
        )
        val json = GatewayJson.encodeToString(OutboundFrame.serializer(), frame)
        assertTrue(json.contains("\"media\""))
        assertTrue(json.contains("\"name\":\"photo.png\""))
        assertTrue(json.contains("\"data_url\":\"data:image/png;base64,AAAA\""))
    }

    @Test
    fun parsesIncomingMedia() {
        val message = GatewayJson.decodeFromString(
            UiMessage.serializer(),
            """
            {"id":"m1","role":"assistant","content":"done",
             "media":[{"kind":"image","url":"/api/media/x.png","name":"x.png"}]}
            """.trimIndent(),
        )
        assertEquals(1, message.media?.size)
        assertEquals("image", message.media?.first()?.kind)
        assertEquals("/api/media/x.png", message.media?.first()?.url)
    }
}
