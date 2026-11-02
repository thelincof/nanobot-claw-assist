package com.nanobot.client.core.transport

import com.nanobot.client.core.gateway.GatewayException
import com.nanobot.client.core.gateway.logD
import com.nanobot.client.core.model.GatewayJson
import com.nanobot.client.core.model.InboundEvent
import com.nanobot.client.core.model.OutboundFrame
import com.nanobot.client.core.model.WebUiResponseEvent
import com.nanobot.client.core.model.parseInboundEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

enum class TransportStatus { IDLE, CONNECTING, OPEN, RECONNECTING, CLOSED, ERROR }

data class TransportState(
    val status: TransportStatus = TransportStatus.IDLE,
    val error: String? = null,
    val openedAt: Long? = null,
)

interface WebSocketCredentials {
    suspend fun freshUrl(): String
}

class NanobotTransport(
    private val client: OkHttpClient,
    private val credentials: WebSocketCredentials,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(TransportState())
    private val mutableEvents = MutableSharedFlow<InboundEvent>(extraBufferCapacity = 256)

    val state: StateFlow<TransportState> = mutableState.asStateFlow()
    val events: SharedFlow<InboundEvent> = mutableEvents.asSharedFlow()

    private val knownChats = linkedSetOf<String>()
    private val pendingMessages = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val pendingTranscriptions = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val pendingWebUiRequests = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val webUiRequestResults = ConcurrentHashMap<String, WebUiResponseEvent>()
    private val attachQueue = ArrayDeque<OutboundFrame.Attach>()

    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var credentialJob: Job? = null
    private var reconnectAttempt = 0
    private var sessionActive = false
    private var everOpened = false
    private var pendingNewChat: CompletableDeferred<String>? = null

    @Synchronized
    fun connect() {
        sessionActive = true
        everOpened = false
        reconnectAttempt = 0
        connectIfNeeded()
    }

    @Synchronized
    private fun connectIfNeeded() {
        if (!sessionActive || socket != null || credentialJob != null || reconnectJob != null) return
        mutableState.value = mutableState.value.copy(
            status = if (reconnectAttempt > 0) TransportStatus.RECONNECTING else TransportStatus.CONNECTING,
            error = null,
        )
        credentialJob = scope.launch {
            val url = try {
                credentials.freshUrl()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logD("credentials unavailable: ${error.javaClass.simpleName}: ${error.message}")
                synchronized(this@NanobotTransport) {
                    credentialJob = null
                    if (sessionActive) {
                        mutableState.value = mutableState.value.copy(
                            status = TransportStatus.RECONNECTING,
                            error = error.message ?: "websocket credentials unavailable",
                        )
                        scheduleReconnectLocked()
                    }
                }
                return@launch
            }
            synchronized(this@NanobotTransport) {
                credentialJob = null
                if (!sessionActive || socket != null) return@launch
                logD("ws connect -> ${url.substringBefore("token=")}token=<hidden>")
                openLocked(url)
            }
        }
    }

    @Synchronized
    fun close() {
        sessionActive = false
        everOpened = false
        reconnectJob?.cancel()
        reconnectJob = null
        credentialJob?.cancel()
        credentialJob = null
        val active = socket
        socket = null
        pendingMessages.values.forEach { it.completeExceptionally(IllegalStateException("closed")) }
        pendingMessages.clear()
        pendingTranscriptions.values.forEach { it.completeExceptionally(IllegalStateException("closed")) }
        pendingTranscriptions.clear()
        pendingWebUiRequests.values.forEach { it.completeExceptionally(IllegalStateException("closed")) }
        pendingWebUiRequests.clear()
        webUiRequestResults.clear()
        pendingNewChat?.completeExceptionally(IllegalStateException("closed"))
        pendingNewChat = null
        attachQueue.clear()
        active?.close(1000, "client_closed")
        mutableState.value = mutableState.value.copy(status = TransportStatus.CLOSED)
    }

    @Synchronized
    fun attach(chatId: String) {
        knownChats += chatId
        if (socket != null) {
            send(OutboundFrame.Attach(chatId))
        } else {
            attachQueue.removeAll { it.chatId == chatId }
            attachQueue.addLast(OutboundFrame.Attach(chatId))
        }
    }

    fun isOpen(): Boolean = socket != null && state.value.status == TransportStatus.OPEN

    /** Waits until the socket is open (bounded), so sends during a reconnect are not lost. */
    private suspend fun awaitOpen(timeoutMs: Long) {
        if (isOpen()) return
        val opened = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            state.first { it.status == TransportStatus.OPEN }
        }
        if (opened == null) throw IllegalStateException("transport_not_open")
    }

    /** Non-throwing readiness probe used by the assistant launch flow. */
    suspend fun ensureOpen(timeoutMs: Long = 10_000): Boolean = try {
        awaitOpen(timeoutMs)
        true
    } catch (_: Exception) {
        false
    }

    @Synchronized
    fun detach(chatId: String) {
        knownChats -= chatId
        attachQueue.removeAll { it.chatId == chatId }
    }

    suspend fun newChat(timeoutMs: Long = 8_000, workspaceScope: JsonObject? = null): String {
        awaitOpen(timeoutMs)
        val pending = CompletableDeferred<String>()
        synchronized(this) {
            check(sessionActive) { "transport_closed" }
            check(pendingNewChat == null) { "new_chat_in_flight" }
            pendingNewChat = pending
            send(OutboundFrame.NewChat(workspaceScope = workspaceScope))
        }
        return try {
            withTimeout(timeoutMs) { pending.await() }
        } catch (error: TimeoutCancellationException) {
            synchronized(this) { if (pendingNewChat === pending) pendingNewChat = null }
            throw IllegalStateException("new_chat_timeout", error)
        }
    }

    suspend fun sendMessage(
        chatId: String,
        content: String,
        turnId: String = UUID.randomUUID().toString(),
        workspaceScope: JsonObject? = null,
        attachments: List<com.nanobot.client.core.model.OutboundAttachment> = emptyList(),
    ): String {
        awaitOpen(10_000)
        val pending = CompletableDeferred<Unit>()
        synchronized(this) {
            check(sessionActive) { "transport_closed" }
            check(socket != null) { "transport_not_open" }
            pendingMessages[turnId] = pending
            send(
                OutboundFrame.Message(
                    chatId = chatId,
                    content = content,
                    turnId = turnId,
                    workspaceScope = workspaceScope,
                    media = attachments.takeIf { it.isNotEmpty() },
                ),
            )
        }
        try {
            withTimeout(15_000) { pending.await() }
            return turnId
        } catch (error: TimeoutCancellationException) {
            pendingMessages.remove(turnId)
            throw IllegalStateException("message_accept_timeout", error)
        } catch (error: Throwable) {
            pendingMessages.remove(turnId)
            throw error
        }
    }

    /** Fire-and-forget ws command to change the workspace scope of an existing chat. */
    fun setWorkspaceScope(chatId: String, workspaceScope: JsonObject?) {
        synchronized(this) {
            check(sessionActive) { "transport_closed" }
            check(socket != null) { "transport_not_open" }
            send(OutboundFrame.SetWorkspaceScope(chatId = chatId, workspaceScope = workspaceScope))
        }
    }

    @Synchronized
    fun stopTurn(chatId: String) {
        if (socket == null) return
        send(OutboundFrame.Message(chatId = chatId, content = "/stop", turnId = "sys:${UUID.randomUUID()}"))
    }

    /**
     * Send a WebUI mutation (e.g. session.delete) over the authenticated websocket.
     * Mutations are rejected over plain HTTP; this is the only supported channel.
     */
    suspend fun webUiRequest(action: String, payload: JsonObject, timeoutMs: Long = 15_000): WebUiResponseEvent {
        val requestId = UUID.randomUUID().toString()
        val pending = CompletableDeferred<Unit>()
        synchronized(this) {
            check(sessionActive) { "transport_closed" }
            check(socket != null) { "transport_not_open" }
            pendingWebUiRequests[requestId] = pending
            send(OutboundFrame.WebUiRequest(requestId = requestId, action = action, payload = payload))
        }
        try {
            withTimeout(timeoutMs) { pending.await() }
        } catch (error: TimeoutCancellationException) {
            pendingWebUiRequests.remove(requestId)
            webUiRequestResults.remove(requestId)
            throw IllegalStateException("webui_request_timeout", error)
        } catch (error: Throwable) {
            pendingWebUiRequests.remove(requestId)
            webUiRequestResults.remove(requestId)
            throw error
        }
        val result = webUiRequestResults.remove(requestId)
            ?: error("webui_request_missing_result")
        if (!result.ok) {
            val err = result.error
            throw GatewayException(
                err?.message ?: "webui_request_failed",
                err?.status,
            )
        }
        return result
    }

    suspend fun transcribeAudio(dataUrl: String, durationMs: Long?, timeoutMs: Long = 90_000): String {
        awaitOpen(10_000)
        val requestId = UUID.randomUUID().toString()
        val pending = CompletableDeferred<String>()
        synchronized(this) {
            check(sessionActive) { "transport_closed" }
            check(socket != null) { "transport_not_open" }
            pendingTranscriptions[requestId] = pending
            send(OutboundFrame.TranscribeAudio(requestId = requestId, dataUrl = dataUrl, durationMs = durationMs))
        }
        return try {
            withTimeout(timeoutMs) { pending.await() }
        } catch (error: TimeoutCancellationException) {
            pendingTranscriptions.remove(requestId)
            throw IllegalStateException("transcription_timeout", error)
        } catch (error: Throwable) {
            pendingTranscriptions.remove(requestId)
            throw error
        }
    }

    @Synchronized
    private fun openLocked(url: String) {
        mutableState.value = mutableState.value.copy(status = TransportStatus.CONNECTING, error = null)
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, Listener())
    }

    @Synchronized
    private fun send(frame: OutboundFrame) {
        val active = socket ?: return
        val payload = GatewayJson.encodeToString(OutboundFrame.serializer(), frame)
        active.send(payload)
    }

    @Synchronized
    private fun scheduleReconnectLocked() {
        if (!sessionActive || reconnectJob != null) return
        // Never-opened session (initial connect): a few tries, then give up so
        // the UI surfaces FAILED instead of endless "connecting".
        if (!everOpened && reconnectAttempt >= 3) {
            sessionActive = false
            logD("give up reconnecting after $reconnectAttempt attempts: ${mutableState.value.error}")
            mutableState.value = mutableState.value.copy(
                status = TransportStatus.ERROR,
                error = mutableState.value.error ?: "Не удалось подключиться",
            )
            return
        }
        reconnectAttempt += 1
        val delayMs = (1_000L shl (reconnectAttempt - 1).coerceAtMost(4)).coerceAtMost(5_000L)
        logD("reconnect #$reconnectAttempt in ${delayMs}ms (everOpened=$everOpened)")
        reconnectJob = scope.launch {
            delay(delayMs)
            synchronized(this@NanobotTransport) {
                reconnectJob = null
                if (sessionActive) connectIfNeeded()
            }
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            logD("ws OPEN (HTTP ${response.code})")
            synchronized(this@NanobotTransport) {
                if (socket !== webSocket) return
                reconnectAttempt = 0
                everOpened = true
                mutableState.value = TransportState(
                    status = TransportStatus.OPEN,
                    openedAt = System.currentTimeMillis(),
                )
                while (attachQueue.isNotEmpty()) {
                    send(attachQueue.removeFirst())
                }
                knownChats.forEach { chatId -> send(OutboundFrame.Attach(chatId)) }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val event = parseInboundEvent(text) ?: return
            when (event) {
                is InboundEvent.MessageAccepted -> pendingMessages.remove(event.value.turnId)?.complete(Unit)
                is InboundEvent.TranscriptionResult -> pendingTranscriptions.remove(event.value.requestId)?.complete(event.value.text)
                is InboundEvent.WebUiResponse -> {
                    val id = event.value.requestId
                    if (id != null) {
                        webUiRequestResults[id] = event.value
                        pendingWebUiRequests.remove(id)?.complete(Unit)
                    }
                }
                is InboundEvent.TranscriptionError -> {
                    val id = event.value.requestId
                    if (id != null) pendingTranscriptions.remove(id)?.completeExceptionally(
                        IllegalStateException(event.value.detail ?: "transcription_error"),
                    )
                }
                is InboundEvent.Error -> {
                    val turnId = event.value.turnId
                    if (turnId != null) pendingMessages.remove(turnId)?.completeExceptionally(
                        IllegalStateException(event.value.reason ?: event.value.detail ?: "turn_rejected"),
                    )
                }
                is InboundEvent.Attached -> {
                    val chatId = event.value.chatId
                    synchronized(this@NanobotTransport) {
                        knownChats += chatId
                        pendingNewChat?.complete(chatId)
                        pendingNewChat = null
                    }
                }
                else -> Unit
            }
            mutableEvents.tryEmit(event)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            onMessage(webSocket, bytes.utf8())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            logD("ws CLOSED code=$code reason=${reason.ifEmpty { "<none>" }}")
            synchronized(this@NanobotTransport) {
                if (socket !== webSocket) return
                socket = null
                pendingMessages.values.forEach { it.completeExceptionally(IllegalStateException("disconnected")) }
                pendingMessages.clear()
                pendingTranscriptions.values.forEach { it.completeExceptionally(IllegalStateException("disconnected")) }
                pendingTranscriptions.clear()
                pendingWebUiRequests.values.forEach { it.completeExceptionally(IllegalStateException("disconnected")) }
                pendingWebUiRequests.clear()
                if (sessionActive) {
                    mutableState.value = mutableState.value.copy(status = TransportStatus.RECONNECTING, error = reason)
                    scheduleReconnectLocked()
                } else {
                    mutableState.value = mutableState.value.copy(status = TransportStatus.CLOSED)
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            logD("ws FAILURE ${t.javaClass.simpleName}: ${t.message} (HTTP ${response?.code ?: "?"})")
            synchronized(this@NanobotTransport) {
                if (socket !== webSocket) return
                socket = null
                pendingMessages.values.forEach { it.completeExceptionally(t) }
                pendingMessages.clear()
                pendingTranscriptions.values.forEach { it.completeExceptionally(t) }
                pendingTranscriptions.clear()
                pendingWebUiRequests.values.forEach { it.completeExceptionally(t) }
                pendingWebUiRequests.clear()
                // Include the HTTP status of a failed handshake when available.
                val detail = buildString {
                    append(t.message ?: "websocket_failure")
                    if (response != null) append(" (HTTP ${response.code})")
                }
                if (sessionActive) {
                    mutableState.value = mutableState.value.copy(
                        status = TransportStatus.RECONNECTING,
                        error = detail,
                    )
                    scheduleReconnectLocked()
                } else {
                    mutableState.value = mutableState.value.copy(status = TransportStatus.ERROR, error = detail)
                }
            }
        }
    }
}
