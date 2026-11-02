package com.nanobot.client.platform

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nanobot.client.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun AssistantPlatformSection() {
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(Res.string.assistant_desktop_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
