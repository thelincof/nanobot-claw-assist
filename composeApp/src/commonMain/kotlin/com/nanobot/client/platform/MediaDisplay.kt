package com.nanobot.client.platform

import androidx.compose.ui.graphics.ImageBitmap

/** Load a signed/remote image (no auth header: Nanobot media URLs are signed). */
expect suspend fun loadRemoteImage(url: String): ImageBitmap?

/** Decode a locally picked image (optimistic preview before sending). */
expect fun decodeLocalImage(bytes: ByteArray): ImageBitmap?

/** Open a media file URL in the system browser/app. */
expect fun openExternalUrl(url: String)

fun resolveMediaUrl(baseUrl: String, raw: String): String {
    if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) return raw
    val base = baseUrl.trimEnd('/')
    return if (raw.startsWith("/")) "$base$raw" else "$base/$raw"
}
