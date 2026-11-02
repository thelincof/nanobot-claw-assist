package com.nanobot.client.platform

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

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
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.also {
                    imageCache[url] = it
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

actual fun decodeLocalImage(bytes: ByteArray): ImageBitmap? =
    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()

actual fun openExternalUrl(url: String) {
    try {
        val context = AppContext.context
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
