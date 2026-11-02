package com.nanobot.client.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberMicPermission(): MicPermissionState = MicPermissionState(granted = true, request = {})

@Composable
actual fun NotificationPermissionEffect() {
    // Desktop does not require runtime notification permissions
}

@Composable
actual fun AppBackgroundEffect(
    onBackground: () -> Unit,
    onForeground: () -> Unit,
) {
    // Desktop windows do not have a lifecycle equivalent; closing the app ends playback.
}
