package com.nanobot.client.core.gateway

import com.nanobot.client.core.model.BootstrapResponse
import com.nanobot.client.core.model.InboundEvent
import com.nanobot.client.core.transport.NanobotTransport
import com.nanobot.client.core.transport.TransportState
import com.nanobot.client.core.transport.TransportStatus
import com.nanobot.client.core.transport.WebSocketCredentials
import com.nanobot.client.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Reconnecting(val error: String?) : ConnectionState
    data class Failed(val error: String) : ConnectionState
}

class GatewaySession(
    private val settings: SettingsRepository,
    private val client: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bootstrapClient = BootstrapClient(client)
    private val bootstrapMutex = Mutex()

    private val mutableConnection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connection: StateFlow<ConnectionState> = mutableConnection.asStateFlow()

    private val mutableTransportState = MutableStateFlow(TransportState())
    val transportState: StateFlow<TransportState> = mutableTransportState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<InboundEvent>(extraBufferCapacity = 512)
    val events: SharedFlow<InboundEvent> = mutableEvents.asSharedFlow()

    @Volatile private var baseUrl: String = ""
    @Volatile private var secret: String = ""
    @Volatile private var bootstrap: BootstrapResponse? = null
    @Volatile private var bootstrapAt: Long = 0
    @Volatile private var apiToken: String? = null

    private var api: GatewayApi? = null
    private var transport: NanobotTransport? = null
    private var stateJob: Job? = null
    private var eventsJob: Job? = null

    fun api(): GatewayApi = api ?: error("gateway is not connected")
    fun transport(): NanobotTransport = transport ?: error("gateway is not connected")
    fun isConfigured(): Boolean = baseUrl.isNotBlank() && secret.isNotBlank()
    fun currentBaseUrl(): String = baseUrl

    suspend fun connect(rawBaseUrl: String, rawSecret: String): Result<Unit> {
        logD("JVM ${System.getProperty("java.version")}, connect(raw=$rawBaseUrl)")
        val normalized = when (val result = normalizeBaseUrl(rawBaseUrl)) {
            is UrlResult.Valid -> result.url
            is UrlResult.Invalid -> return Result.failure(GatewayException("Некорректный URL gateway: ${result.reason}"))
        }
        if (rawSecret.isBlank()) return Result.failure(GatewayException("Bootstrap Secret не заполнен"))

        mutableConnection.value = ConnectionState.Connecting
        logD("connect: base=$normalized")
        return try {
            val fresh = bootstrapClient.fetch(normalized, rawSecret)
            logD("bootstrap OK: wsPath=${fresh.wsPath}, wsUrl=${fresh.wsUrl}, tokenTtl=${fresh.expiresIn}s")
            baseUrl = normalized
            secret = rawSecret
            applyBootstrap(fresh)
            settings.update { it.copy(baseUrl = normalized, secret = rawSecret) }

            val apiInstance = GatewayApi(
                client = client,
                baseUrlProvider = { baseUrl },
                tokenProvider = { apiToken },
                refreshToken = { refreshApiToken() },
            )
            api = apiInstance

            transport?.close()
            val transportInstance = NanobotTransport(
                client = client,
                credentials = object : WebSocketCredentials {
                    // WS admission tokens are single-use and short-lived: always bootstrap
                    // again right before opening the socket (initial connect included).
                    override suspend fun freshUrl(): String =
                        buildWebSocketUrl(baseUrl, fetchFreshBootstrap())
                },
            )
            transport = transportInstance
            stateJob?.cancel()
            eventsJob?.cancel()
            eventsJob = scope.launch {
                transportInstance.events.collect { event -> mutableEvents.emit(event) }
            }
            stateJob = scope.launch {
                transportInstance.state.collect { state ->
                    mutableTransportState.value = state
                    mutableConnection.value = mapStatus(state)
                }
            }
            transportInstance.connect()
            // The REST surface is already usable after a successful bootstrap; the
            // WebSocket opens in the background and must not block the UI.
            mutableConnection.value = ConnectionState.Connected
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logD("connect FAILED: ${error.javaClass.simpleName}: ${error.message}")
            mutableConnection.value = ConnectionState.Failed(error.message ?: "Ошибка подключения")
            Result.failure(error)
        }
    }

    fun disconnect() {
        stateJob?.cancel()
        stateJob = null
        eventsJob?.cancel()
        eventsJob = null
        transport?.close()
        transport = null
        api = null
        bootstrap = null
        bootstrapAt = 0
        apiToken = null
        baseUrl = ""
        secret = ""
        mutableTransportState.value = TransportState()
        mutableConnection.value = ConnectionState.Idle
    }

    private fun mapStatus(state: TransportState): ConnectionState {
        val restReady = bootstrap != null && apiToken != null
        return when (state.status) {
            TransportStatus.OPEN -> ConnectionState.Connected
            TransportStatus.CONNECTING ->
                if (restReady) ConnectionState.Connected else ConnectionState.Connecting
            TransportStatus.RECONNECTING ->
                if (restReady) ConnectionState.Connected else ConnectionState.Reconnecting(state.error)
            TransportStatus.ERROR ->
                if (restReady) ConnectionState.Connected else ConnectionState.Failed(state.error ?: "websocket error")
            TransportStatus.CLOSED ->
                if (restReady) ConnectionState.Connected else ConnectionState.Idle
            TransportStatus.IDLE -> ConnectionState.Idle
        }
    }

    private fun applyBootstrap(fresh: BootstrapResponse) {
        bootstrap = fresh
        bootstrapAt = System.currentTimeMillis()
        apiToken = fresh.apiToken?.takeIf { it.isNotBlank() } ?: fresh.token
    }

    /** Always fetches a new bootstrap (new single-use WS token). */
    private suspend fun fetchFreshBootstrap(): BootstrapResponse = bootstrapMutex.withLock {
        val url = baseUrl
        val auth = secret
        if (url.isBlank() || auth.isBlank()) throw GatewayException("gateway is not configured")
        val fresh = bootstrapClient.fetch(url, auth)
        applyBootstrap(fresh)
        fresh
    }

    /** Called from the 401 path: always issues a fresh token, never reuses the stale one. */
    private suspend fun refreshApiToken(): String? {
        return try {
            val fresh = fetchFreshBootstrap()
            fresh.apiToken?.takeIf { it.isNotBlank() } ?: fresh.token
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logD("api token refresh failed: ${error.message}")
            null
        }
    }
}

/** Lightweight stderr diagnostics (always on for now — client is in active debugging). */
internal fun logD(message: String) {
    val ts = java.time.LocalTime.now().withNano(0)
    println("[nanobot $ts] $message")
}
