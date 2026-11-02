package com.nanobot.client.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nanobot.client.core.model.UiMediaAttachment
import com.nanobot.client.core.model.UiMessage
import com.nanobot.client.platform.decodeLocalImage
import com.nanobot.client.platform.loadRemoteImage
import com.nanobot.client.platform.openExternalUrl
import com.nanobot.client.platform.resolveMediaUrl

private sealed interface Photo {
    data class Local(val bytes: ByteArray, val name: String?) : Photo
    data class Remote(val url: String, val name: String?) : Photo
}

fun UiMessage.hasMediaContent(): Boolean =
    !localMedia.isNullOrEmpty() || !media.isNullOrEmpty() || !images.isNullOrEmpty()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageMedia(message: UiMessage, baseUrl: String, modifier: Modifier = Modifier) {
    val locals = message.localMedia.orEmpty()
    val photos = mutableListOf<Photo>()
    val files = mutableListOf<UiMediaAttachment>()

    if (locals.isNotEmpty()) {
        locals.forEach { local ->
            if (local.mime.startsWith("image/")) {
                photos += Photo.Local(local.bytes, local.name)
            } else {
                files += UiMediaAttachment(
                    kind = if (local.mime.startsWith("video/")) "video" else "file",
                    name = local.name,
                )
            }
        }
    } else {
        val seen = mutableSetOf<String>()
        fun addRemote(url: String?, name: String?) {
            val clean = url?.takeIf { it.isNotBlank() } ?: return
            if (!seen.add(clean)) return
            photos += Photo.Remote(resolveMediaUrl(baseUrl, clean), name)
        }
        message.media.orEmpty().forEach { item ->
            if (isImage(item)) addRemote(item.url, item.name) else files += item
        }
        // Nanobot duplicates image attachments in `images`; dedupe by URL above.
        message.images.orEmpty().forEach { image -> addRemote(image.url, image.name) }
    }

    if (photos.isEmpty() && files.isEmpty()) return

    Column(modifier.fillMaxWidth()) {
        if (photos.isNotEmpty()) {
            PhotoGrid(photos)
        }
        if (files.isNotEmpty()) {
            if (photos.isNotEmpty()) Spacer(Modifier.height(2.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                files.forEach { item ->
                    val url = item.url?.takeIf { it.isNotBlank() }?.let { resolveMediaUrl(baseUrl, it) }
                    MediaFileChip(
                        name = item.name ?: url?.substringAfterLast('/') ?: "file",
                        isVideo = item.kind == "video",
                        onClick = { url?.let(::openExternalUrl) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoGrid(photos: List<Photo>) {
    val shown = photos.take(4)
    when (shown.size) {
        1 -> SinglePhoto(shown[0])
        2 -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            PhotoCell(shown[0], Modifier.weight(1f).aspectRatio(1f))
            PhotoCell(shown[1], Modifier.weight(1f).aspectRatio(1f))
        }
        3 -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            shown.forEach { photo -> PhotoCell(photo, Modifier.weight(1f).aspectRatio(1f)) }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                PhotoCell(shown[0], Modifier.weight(1f).aspectRatio(1f))
                PhotoCell(shown[1], Modifier.weight(1f).aspectRatio(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                PhotoCell(shown[2], Modifier.weight(1f).aspectRatio(1f))
                PhotoCell(shown[3], Modifier.weight(1f).aspectRatio(1f))
            }
        }
    }
    if (photos.size > 4) {
        Text(
            "+${photos.size - 4}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun SinglePhoto(photo: Photo) {
    val bitmap = rememberPhotoBitmap(photo)
    val ratio = bitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: (4f / 3f)
    // Fill the whole bubble width; height follows the photo (clamped so a very
    // tall/wide image does not take over the screen or turn into a thin strip).
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio.coerceIn(0.72f, 1.8f))
            .clip(RoundedCornerShape(12.dp))
            .then(if (photo is Photo.Remote) Modifier.clickable { openExternalUrl(photo.url) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        PhotoContent(photo, bitmap)
    }
}

@Composable
private fun PhotoCell(photo: Photo, modifier: Modifier) {
    val bitmap = rememberPhotoBitmap(photo)
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (photo is Photo.Remote) Modifier.clickable { openExternalUrl(photo.url) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        PhotoContent(photo, bitmap)
    }
}

@Composable
private fun PhotoContent(photo: Photo, bitmap: ImageBitmap?) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = when (photo) {
                is Photo.Local -> photo.name
                is Photo.Remote -> photo.name
            },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    when (photo) {
                        is Photo.Local -> photo.name ?: "image"
                        is Photo.Remote -> photo.name ?: "image"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(6.dp),
                )
                if (photo is Photo.Remote) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun rememberPhotoBitmap(photo: Photo): ImageBitmap? = when (photo) {
    is Photo.Local -> remember(photo.bytes) { decodeLocalImage(photo.bytes) }
    is Photo.Remote -> {
        var bitmap by remember(photo.url) { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(photo.url) { bitmap = loadRemoteImage(photo.url) }
        bitmap
    }
}

@Composable
private fun MediaFileChip(name: String, isVideo: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isVideo) Icons.Rounded.PlayCircle else Icons.Rounded.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(180.dp),
            )
        }
    }
}

private fun isImage(item: UiMediaAttachment): Boolean {
    if (item.kind.equals("image", ignoreCase = true)) return true
    val name = item.name.orEmpty().substringAfterLast('.', "").lowercase()
    return name in setOf("png", "jpg", "jpeg", "webp", "gif")
}
