package com.nanobot.client.platform

import androidx.compose.runtime.Composable

class MicPermissionState(
    val granted: Boolean,
    val request: () -> Unit,
)

@Composable
expect fun rememberMicPermission(): MicPermissionState

@Composable
expect fun NotificationPermissionEffect()

@Composable
expect fun AppBackgroundEffect(
    onBackground: () -> Unit,
    onForeground: () -> Unit = {},
)
