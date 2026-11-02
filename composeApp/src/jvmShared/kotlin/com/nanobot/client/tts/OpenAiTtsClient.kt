package com.nanobot.client.tts

import com.nanobot.client.settings.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class SpeechRequest(
    val model: String,
    val input: String,
    val voice: String,
    val response_format: String = "wav",
    val speed: Float = 1.0f,
)

class OpenAiTtsClient(private val baseClient: OkHttpClient) {
    private val client = baseClient.newBuilder()
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(150, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun synthesize(text: String, config: TtsConfig): PcmAudio = withContext(Dispatchers.IO) {
        if (!config.configured) throw IOException("TTS API URL не настроен")
        val url = speechUrl(config.baseUrl)
        val payload = SpeechRequest(
            model = config.model.ifBlank { "tts-1" },
            input = text,
            voice = config.voice.ifBlank { "alloy" },
            speed = config.speed.coerceIn(0.25f, 4.0f),
        )
        val body = com.nanobot.client.core.model.GatewayJson.encodeToString(payload)
            .toRequestBody("application/json".toMediaType())
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "audio/wav, application/octet-stream")
            .post(body)
        if (config.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${config.apiKey}")
        try {
            client.newCall(builder.build()).execute().use { response ->
                val bytes = response.body?.bytes()
                if (response.code !in 200..299 || bytes == null) {
                    val detail = bytes?.decodeToString().orEmpty()
                    throw IOException("TTS HTTP ${response.code}: ${detail.take(300)}")
                }
                if (bytes.isEmpty()) throw IOException("TTS вернул пустой ответ")
                parseWav(bytes)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("TTS: ${error.message}", error)
        }
    }

    private fun speechUrl(baseUrl: String): String {
        val base = baseUrl.trim().trimEnd('/')
        if (base.endsWith("/audio/speech")) return base
        if (base.endsWith("/v1")) return "$base/audio/speech"
        return "$base/v1/audio/speech"
    }
}

fun parseWav(bytes: ByteArray): PcmAudio {
    if (bytes.size < 44) throw IOException("Некорректный WAV: слишком короткий")
    if (bytes.copyOfRange(0, 4).decodeToString() != "RIFF" || bytes.copyOfRange(8, 12).decodeToString() != "WAVE") {
        throw IOException("Некорректный WAV: нет RIFF/WAVE")
    }
    var offset = 12
    var sampleRate = 0
    var channels = 0
    var bits = 0
    var format = 0
    var dataOffset = -1
    var dataSize = 0
    while (offset + 8 <= bytes.size) {
        val id = bytes.copyOfRange(offset, offset + 4).decodeToString()
        val size = readIntLE(bytes, offset + 4)
        val payload = offset + 8
        if (size < 0 || payload + size > bytes.size) break
        when (id) {
            "fmt " -> {
                if (size >= 16) {
                    format = readShortLE(bytes, payload).toInt()
                    channels = readShortLE(bytes, payload + 2).toInt()
                    sampleRate = readIntLE(bytes, payload + 4)
                    bits = readShortLE(bytes, payload + 14).toInt()
                }
            }
            "data" -> {
                dataOffset = payload
                dataSize = size
            }
        }
        offset = payload + size + (size and 1)
    }
    if (format != 1) throw IOException("Поддерживается только PCM WAV (format=$format)")
    if (channels <= 0 || sampleRate <= 0) throw IOException("Некорректные параметры WAV")
    if (bits != 16) throw IOException("Поддерживается только 16-bit WAV (bits=$bits)")
    if (dataOffset < 0) throw IOException("В WAV нет data")
    val sampleCount = dataSize / 2
    val samples = ShortArray(sampleCount)
    for (index in 0 until sampleCount) {
        samples[index] = readShortLE(bytes, dataOffset + index * 2)
    }
    return PcmAudio(samples = samples, sampleRate = sampleRate, channels = channels)
}

private fun readIntLE(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xff) or
        ((bytes[offset + 1].toInt() and 0xff) shl 8) or
        ((bytes[offset + 2].toInt() and 0xff) shl 16) or
        ((bytes[offset + 3].toInt() and 0xff) shl 24)

private fun readShortLE(bytes: ByteArray, offset: Int): Short =
    (((bytes[offset + 1].toInt() and 0xff) shl 8) or (bytes[offset].toInt() and 0xff)).toShort()
