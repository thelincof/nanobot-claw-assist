package com.nanobot.client.core.gateway

import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GatewayException(message: String, val status: Int? = null, cause: Throwable? = null) : Exception(message, cause)

class GatewayApi(
    private val client: OkHttpClient,
    private val baseUrlProvider: () -> String,
    private val tokenProvider: () -> String?,
    private val refreshToken: suspend () -> String?,
) {
    suspend fun sessions(): com.nanobot.client.core.model.SessionsPayload =
        request("/api/sessions", com.nanobot.client.core.model.SessionsPayload.serializer())

    /** Raw GET /api/workspaces payload: default_scope + controls (can_pick_folder etc.). */
    suspend fun workspacesRaw(): kotlinx.serialization.json.JsonObject =
        requestJson("/api/workspaces")

    /** Raw GET /api/webui/sidebar-state payload (contains title_overrides). */
    suspend fun sidebarStateRaw(): kotlinx.serialization.json.JsonObject =
        requestJson("/api/webui/sidebar-state")

    /** GET /api/settings: agent, model presets, providers, runtime config. */
    suspend fun settings(): com.nanobot.client.core.model.NanobotSettingsPayload =
        request("/api/settings", com.nanobot.client.core.model.NanobotSettingsPayload.serializer())

    /** GET /api/settings/mcp-presets: available/installed MCP servers. */
    suspend fun mcpPresets(): com.nanobot.client.core.model.McpPresetsPayload =
        request("/api/settings/mcp-presets", com.nanobot.client.core.model.McpPresetsPayload.serializer())

    /** GET /api/webui/automations: cron jobs and local triggers. */
    suspend fun automations(): com.nanobot.client.core.model.AutomationsPayload =
        request("/api/webui/automations", com.nanobot.client.core.model.AutomationsPayload.serializer())

    /** GET /api/webui/automations/result: stored text of one automation run. */
    suspend fun automationResult(id: String, runAtMs: Long, kind: String): com.nanobot.client.core.model.AutomationResultPayload =
        request(
            "/api/webui/automations/result?id=${id.pathEncoded()}&run_at_ms=$runAtMs&kind=${kind.pathEncoded()}",
            com.nanobot.client.core.model.AutomationResultPayload.serializer(),
        )

    suspend fun thread(sessionKey: String): com.nanobot.client.core.model.WebUiThreadPayload =
        request(
            "/api/sessions/${sessionKey.pathEncoded()}/webui-thread?limit=160&direction=latest",
            com.nanobot.client.core.model.WebUiThreadPayload.serializer(),
        )

    private suspend fun <T> request(
        path: String,
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
        method: String = "GET",
    ): T = withContext(Dispatchers.IO) {
        try {
            val first = execute(path, method, tokenProvider())
            if (first.code == 401) {
                val refreshed = refreshToken()
                val second = execute(path, method, refreshed)
                if (second.code == 401) throw GatewayException("Unauthorized", 401)
                return@withContext decode(second, deserializer)
            }
            decode(first, deserializer)
        } catch (error: CancellationException) {
            throw error
        } catch (error: GatewayException) {
            throw error
        } catch (error: InterruptedIOException) {
            throw GatewayException("timeout", cause = error)
        } catch (error: IOException) {
            throw GatewayException("network", cause = error)
        }
    }

    private suspend fun requestJson(path: String): kotlinx.serialization.json.JsonObject =
        withContext(Dispatchers.IO) {
            try {
                val response = execute(path, "GET", tokenProvider())
                if (response.code !in 200..299) {
                    throw GatewayException(response.body.ifBlank { "HTTP ${response.code}" }, response.code)
                }
                com.nanobot.client.core.model.GatewayJson.parseToJsonElement(response.body).jsonObject
            } catch (error: CancellationException) {
                throw error
            } catch (error: GatewayException) {
                throw error
            } catch (error: InterruptedIOException) {
                throw GatewayException("timeout", cause = error)
            } catch (error: IOException) {
                throw GatewayException("network", cause = error)
            }
        }

    private fun execute(path: String, method: String, token: String?): RawResponse {
        val url = baseUrlProvider().trimEnd('/') + path
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        val body = if (method == "GET") null else ByteArray(0).toRequestBody(null)
        builder.method(method, body)
        client.newCall(builder.build()).execute().use { response ->
            return RawResponse(
                code = response.code,
                contentType = response.header("Content-Type").orEmpty(),
                body = response.body?.string().orEmpty(),
            )
        }
    }

    private fun <T> decode(
        response: RawResponse,
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
    ): T {
        if (response.code !in 200..299) {
            throw GatewayException(response.body.ifBlank { "HTTP ${response.code}" }, response.code)
        }
        if (response.contentType.isNotBlank() && "application/json" !in response.contentType.lowercase()) {
            throw GatewayException("non-json response")
        }
        return try {
            com.nanobot.client.core.model.GatewayJson.decodeFromString(deserializer, response.body)
        } catch (error: Exception) {
            throw GatewayException("invalid payload: ${error.message}", cause = error)
        }
    }

    private data class RawResponse(val code: Int, val contentType: String, val body: String)
}

internal fun String.pathEncoded(): String =
    java.net.URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")
