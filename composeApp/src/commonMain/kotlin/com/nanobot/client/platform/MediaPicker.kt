package com.nanobot.client.platform

import androidx.compose.runtime.Composable
import com.nanobot.client.core.model.LocalAttachment

class AttachmentPicker(
    val pickImages: () -> Unit,
    val pickFiles: () -> Unit,
)

/**
 * Returns platform actions for picking photos (system gallery chooser) and files
 * (document picker). [onPicked] receives the selected files before upload.
 */
@Composable
expect fun rememberAttachmentPicker(onPicked: (List<LocalAttachment>) -> Unit): AttachmentPicker
