package com.nanobot.client.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Desktop
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image

private val imageClient = OkHttpClient.Builder().build()
private val imageCache = ConcurrentHashMap<String, ImageBitmap>()

actual suspend fun loadRemoteImage(url: String): ImageBitmap? {
    imageCache[url]?.let { return it }
    return withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            imageClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bytes = response.body?.bytes() ?: return@withContext null
                Image.makeFromEncoded(bytes).toComposeImageBitmap().also { imageCache[url] = it }
            }
        } catch (_: Exception) {
            null
        }
    }
}

actual fun decodeLocalImage(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

actual fun openExternalUrl(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}
