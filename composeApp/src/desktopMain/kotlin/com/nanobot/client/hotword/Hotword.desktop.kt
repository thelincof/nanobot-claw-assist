package com.nanobot.client.hotword

import androidx.compose.runtime.Composable

@Composable
actual fun HotwordEffect(
    enabled: Boolean,
    phrase: String,
    active: Boolean,
    onDetected: () -> Unit,
) {
    // In-app wake word is currently Android-only.
}

actual fun hotwordSupported(): Boolean = false
