package com.nanobot.client.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException

/**
 * Runs Vosk speech recognition offline from pre-recorded PCM 16 kHz mono samples.
 */
object VoskSttRecognizer {

    private var cachedModel: Model? = null
    private var cachedModelPath: String? = null

    private fun getOrCreateModel(modelDir: File): Model {
        val path = modelDir.absolutePath
        if (cachedModel != null && cachedModelPath == path) {
            return cachedModel!!
        }
        val model = Model(path)
        cachedModel = model
        cachedModelPath = path
        return model
    }

    /**
     * Recognise [samples] (16 kHz mono PCM ShortArray) using embedded Vosk model.
     */
    suspend fun recognize(samples: ShortArray): String = withContext(Dispatchers.IO) {
        val modelDir = VoskModelProvider.getModelDir()
            ?: throw IOException("Модель Vosk не найдена или не удалось распаковать")
        val model = getOrCreateModel(modelDir)
        val recognizer = Recognizer(model, 16000.0f)
        try {
            val bytes = ByteArray(samples.size * 2)
            for (i in samples.indices) {
                val s = samples[i].toInt()
                bytes[i * 2] = (s and 0xff).toByte()
                bytes[i * 2 + 1] = ((s ushr 8) and 0xff).toByte()
            }
            recognizer.acceptWaveForm(bytes, bytes.size)
            parseVoskResult(recognizer.finalResult)
        } finally {
            recognizer.close()
        }
    }

    private fun parseVoskResult(json: String): String = try {
        Json.parseToJsonElement(json).jsonObject["text"]
            ?.jsonPrimitive?.contentOrNull?.trim() ?: ""
    } catch (_: Exception) { "" }
}
