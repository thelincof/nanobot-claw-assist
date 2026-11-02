package com.nanobot.client.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.nanobot.client.core.model.LocalAttachment
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

@Composable
actual fun rememberAttachmentPicker(onPicked: (List<LocalAttachment>) -> Unit): AttachmentPicker {
    val scope = rememberCoroutineScope()

    fun open(imagesOnly: Boolean) {
        scope.launch {
            val paths = withContext(Dispatchers.Swing) {
                val chooser = JFileChooser().apply {
                    isMultiSelectionEnabled = true
                    if (imagesOnly) {
                        fileFilter = FileNameExtensionFilter(
                            "Images",
                            "png", "jpg", "jpeg", "webp", "gif",
                        )
                    }
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    chooser.selectedFiles.map { it.toPath() }
                } else {
                    emptyList()
                }
            }
            if (paths.isNotEmpty()) {
                val files = withContext(Dispatchers.IO) {
                    paths.mapNotNull { path -> readAttachment(path) }
                }
                if (files.isNotEmpty()) onPicked(files)
            }
        }
    }

    return remember(scope) {
        AttachmentPicker(
            pickImages = { open(true) },
            pickFiles = { open(false) },
        )
    }
}

private fun readAttachment(path: Path): LocalAttachment? = try {
    val size = Files.size(path)
    if (size > 6L * 1024 * 1024) return null
    val name = path.fileName.toString()
    val mime = Files.probeContentType(path) ?: guessMime(name)
    LocalAttachment(name = name, mime = mime, bytes = Files.readAllBytes(path))
} catch (_: Exception) {
    null
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
