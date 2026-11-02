package com.nanobot.client

import com.nanobot.client.core.gateway.BootstrapClient
import com.nanobot.client.core.gateway.buildWebSocketUrl
import com.nanobot.client.core.model.BootstrapResponse
import com.nanobot.client.core.model.InboundEvent
import com.nanobot.client.core.transport.NanobotTransport
import com.nanobot.client.core.transport.TransportStatus
import com.nanobot.client.core.transport.WebSocketCredentials
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient

/**
 * End-to-end check of the nanobot transport against tools/mock_gateway.py.
 *
 * Run the mock first:
 *   python3 tools/mock_gateway.py
 * then:
 *   NANOBOT_MOCK=1 ./gradlew :composeApp:desktopTest --tests '*NanobotTransportTest*'
 */
class NanobotTransportTest {
    @Test
    fun roundTripAgainstMockGateway() = runBlocking {
        if (System.getenv("NANOBOT_MOCK") != "1") return@runBlocking
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val bootstrapClient = BootstrapClient(client)
        val base = "http://127.0.0.1:8765"
        var bootstraps = 0
        val transport = NanobotTransport(
            client = client,
            credentials = object : WebSocketCredentials {
                // Nanobot issues single-use WS tokens: every connect needs a fresh bootstrap.
                override suspend fun freshUrl(): String {
                    bootstraps += 1
                    val boot: BootstrapResponse = bootstrapClient.fetch(base, "test")
                    return buildWebSocketUrl(base, boot)
                }
            },
        )
        val events = Channel<InboundEvent>(Channel.UNLIMITED)
        val collector = launch { transport.events.collect { events.send(it) } }
        try {
            transport.connect()
            withTimeout(5_000) { transport.state.first { it.status == TransportStatus.OPEN } }
            val chatId = transport.newChat()
            assertTrue(chatId.isNotBlank(), "new_chat must return a server chat id")
            transport.attach(chatId)
            transport.sendMessage(chatId, "hello")
            var sawDelta = false
            withTimeout(10_000) {
                while (true) {
                    when (val event = events.receive()) {
                        is InboundEvent.Delta -> sawDelta = true
                        is InboundEvent.TurnEnd -> return@withTimeout
                        else -> Unit
                    }
                }
            }
            assertTrue(sawDelta, "expected streamed delta before turn_end")
            assertTrue(bootstraps >= 1)
        } finally {
            collector.cancel()
            transport.close()
        }
    }
}

/**
 * Live handshake check against a real Nanobot gateway.
 *
 * NANOBOT_LIVE=1 NANOBOT_BASE=http://192.168.0.250:8765 NANOBOT_SECRET=... \
 *   ./gradlew :composeApp:desktopTest --tests '*NanobotTransportTest.liveGatewayHandshake*'
 */
class NanobotLiveGatewayTest {
    @Test
    fun liveGatewayHandshake() = runBlocking {
        if (System.getenv("NANOBOT_LIVE") != "1") return@runBlocking
        val base = System.getenv("NANOBOT_BASE") ?: error("NANOBOT_BASE is required")
        val secret = System.getenv("NANOBOT_SECRET") ?: error("NANOBOT_SECRET is required")
        val client = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        val bootstrapClient = BootstrapClient(client)
        val first = bootstrapClient.fetch(base, secret)
        assertTrue(first.token.isNotBlank(), "bootstrap must issue a WS token")
        assertTrue(!first.apiToken.isNullOrBlank(), "bootstrap must issue an API token")

        val rest = okhttp3.Request.Builder()
            .url("${base.trimEnd('/')}/api/sessions")
            .header("Authorization", "Bearer ${first.apiToken}")
            .header("Accept", "application/json")
            .build()
        client.newCall(rest).execute().use { response ->
            assertTrue(response.code in 200..299, "GET /api/sessions -> HTTP ${response.code}")
        }

        var bootstraps = 0
        val transport = NanobotTransport(
            client = client,
            credentials = object : WebSocketCredentials {
                override suspend fun freshUrl(): String {
                    bootstraps += 1
                    return buildWebSocketUrl(base, bootstrapClient.fetch(base, secret))
                }
            },
        )
        try {
            // Two consecutive connects prove single-use WS tokens are refreshed.
            repeat(2) { attempt ->
                transport.connect()
                withTimeout(8_000) { transport.state.first { it.status == TransportStatus.OPEN } }
                assertTrue(transport.isOpen(), "WS should be open on attempt ${attempt + 1}")
                transport.close()
            }
            assertTrue(bootstraps >= 2, "each WS connect must fetch a fresh bootstrap token")
        } finally {
            transport.close()
        }
    }
}
