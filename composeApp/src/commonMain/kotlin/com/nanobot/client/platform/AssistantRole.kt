package com.nanobot.client.platform

import androidx.compose.runtime.Composable

/**
 * Platform block inside the "Digital assistant" settings section.
 * Android shows the system role status/assignment; desktop shows the launch flag hint.
 */
@Composable
expect fun AssistantPlatformSection()
