package com.nanobot.client.core.gateway

import com.nanobot.client.core.model.BootstrapResponse
import com.nanobot.client.core.model.GatewayJson
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class BootstrapClient(private val client: OkHttpClient) {
    // Bootstrap must never hang: hard deadline for the whole call (connect+headers+body).
    private val bootstrapClient = client.newBuilder()
        .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun fetch(baseUrl: String, secret: String): BootstrapResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/webui/bootstrap")
            .header("Accept", "application/json")
            .header("X-Nanobot-Auth", secret)
            .get()
            .build()
        try {
            com.nanobot.client.core.gateway.logD("bootstrap: sending GET ${baseUrl.trimEnd('/')}/webui/bootstrap")
            bootstrapClient.newCall(request).execute().use { response ->
                com.nanobot.client.core.gateway.logD("bootstrap: response HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                com.nanobot.client.core.gateway.logD("bootstrap: body ${body.length}B read")
                if (response.code != 200) {
                    com.nanobot.client.core.gateway.logD("bootstrap HTTP ${response.code}: ${body.take(200)}")
                }
                if (response.code == 401 || response.code == 403) {
                    throw GatewayException("Неверный Bootstrap Secret", response.code)
                }
                if (response.code !in 200..299) {
                    throw GatewayException(body.ifBlank { "HTTP ${response.code}" }, response.code)
                }
                try {
                    GatewayJson.decodeFromString(BootstrapResponse.serializer(), body)
                } catch (error: Exception) {
                    throw GatewayException("Некорректный ответ bootstrap: ${error.message}", cause = error)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: GatewayException) {
            throw error
        } catch (error: IOException) {
            com.nanobot.client.core.gateway.logD("bootstrap IO error: ${error.javaClass.simpleName}: ${error.message}")
            val reason = when {
                error.message?.contains("timeout", ignoreCase = true) == true ->
                    "Gateway не отвечает (таймаут 15с): $baseUrl"
                error.message?.contains("Failed to connect", ignoreCase = true) == true ->
                    "Gateway недоступен: $baseUrl (порт закрыт или хост не отвечает)"
                else -> "Нет связи с gateway: ${error.message}"
            }
            throw GatewayException(reason, cause = error)
        }
    }
}
