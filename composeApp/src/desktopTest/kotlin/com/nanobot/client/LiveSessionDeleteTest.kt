package com.nanobot.client

import com.nanobot.client.core.gateway.BootstrapClient
import com.nanobot.client.core.gateway.buildWebSocketUrl
import com.nanobot.client.core.transport.NanobotTransport
import com.nanobot.client.core.transport.TransportStatus
import com.nanobot.client.core.transport.WebSocketCredentials
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

/**
 * Live check: Nanobot rejects session.delete over HTTP (empty reply -> network),
 * so the client must send it as a WebUI mutation over the authenticated socket.
 *
 * NANOBOT_LIVE=1 NANOBOT_BASE=... NANOBOT_SECRET=... ./gradlew :composeApp:desktopTest \
 *   --tests '*LiveSessionDeleteTest*'
 */
class LiveSessionDeleteTest {
    @Test
    fun createsAndDeletesChatOverWebSocket() = runBlocking {
        if (System.getenv("NANOBOT_LIVE") != "1") return@runBlocking
        val base = System.getenv("NANOBOT_BASE") ?: error("NANOBOT_BASE is required")
        val secret = System.getenv("NANOBOT_SECRET") ?: error("NANOBOT_SECRET is required")
        val client = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        val bootstrapClient = BootstrapClient(client)
        val transport = NanobotTransport(
            client = client,
            credentials = object : WebSocketCredentials {
                override suspend fun freshUrl(): String =
                    buildWebSocketUrl(base, bootstrapClient.fetch(base, secret))
            },
        )
        try {
            transport.connect()
            withTimeout(8_000) { transport.state.first { it.status == TransportStatus.OPEN } }
            val chatId = transport.newChat()
            assertTrue(chatId.isNotBlank())
            val response = transport.webUiRequest(
                "session.delete",
                buildJsonObject { put("key", "websocket:$chatId") },
            )
            assertTrue(response.ok, "session.delete must succeed over the WebSocket")
        } finally {
            transport.close()
        }
    }
}
