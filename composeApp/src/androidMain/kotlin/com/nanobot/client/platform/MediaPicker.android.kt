package com.nanobot.client.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.nanobot.client.core.model.LocalAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberAttachmentPicker(onPicked: (List<LocalAttachment>) -> Unit): AttachmentPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun read(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> readAttachment(context, uri) }
            }
            if (files.isNotEmpty()) onPicked(files)
        }
    }

    // ACTION_GET_CONTENT: the system chooser includes Photos/Gallery apps,
    // unlike ACTION_OPEN_DOCUMENT which opens the Files app only.
    val imagesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        read(uris)
    }
    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        read(uris)
    }

    return remember(imagesLauncher, filesLauncher) {
        AttachmentPicker(
            pickImages = { imagesLauncher.launch("image/*") },
            pickFiles = { filesLauncher.launch(arrayOf("*/*")) },
        )
    }
}

private fun readAttachment(context: Context, uri: Uri): LocalAttachment? {
    return try {
        var name = "file"
        var size = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        if (size > 6L * 1024 * 1024) return null
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val mime = context.contentResolver.getType(uri) ?: guessMime(name)
        LocalAttachment(name = name, mime = mime, bytes = bytes)
    } catch (_: Exception) {
        null
    }
}

private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "mp4" -> "video/mp4"
    "webm" -> "video/webm"
    "mov" -> "video/quicktime"
    "pdf" -> "application/pdf"
    "json" -> "application/json"
    "md" -> "text/markdown"
    "txt" -> "text/plain"
    else -> "application/octet-stream"
}
