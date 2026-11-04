package com.nanobot.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nanobot.client.app.AppController
import com.nanobot.client.app.ConnectionPhase
import com.nanobot.client.app.AssistantLaunch
import com.nanobot.client.app.createAppController
import com.nanobot.client.audio.VoicePhase
import com.nanobot.client.hotword.HotwordEffect
import com.nanobot.client.platform.rememberMicPermission
import com.nanobot.client.platform.AppBackgroundEffect
import com.nanobot.client.platform.NotificationPermissionEffect

private enum class Screen { Main, Settings, Automations }

@Composable
fun App() {
    val controller = remember { createAppController() }
    LaunchedEffect(Unit) { controller.start() }
    NotificationPermissionEffect()
    AppBackgroundEffect(
        onBackground = { controller.onBackground() },
        onForeground = { controller.onForeground() },
    )

    val state by controller.state.collectAsState()
    var screen by remember { mutableStateOf(Screen.Main) }
    val snackbar = remember { SnackbarHostState() }
    val micPermission = rememberMicPermission()
    var pendingAssistant by remember { mutableStateOf(false) }
    var pendingAssistantForceListen by remember { mutableStateOf(false) }
    val assistantRequests by AssistantLaunch.requests.collectAsState()

    HotwordEffect(
        enabled = state.settings.hotwordEnabled && state.connection.phase == ConnectionPhase.CONNECTED,
        phrase = state.settings.hotwordPhrase,
        active = state.voice.phase == VoicePhase.IDLE &&
            !state.tts.speaking &&
            !state.tts.loading &&
            !state.assistantLaunching,
        onDetected = {
            screen = Screen.Main
            pendingAssistantForceListen = true
            if (micPermission.granted) {
                pendingAssistantForceListen = false
                controller.startAssistant(forceListen = true)
            } else {
                pendingAssistant = true
                micPermission.request()
            }
        },
    )

    LaunchedEffect(assistantRequests) {
        if (!AssistantLaunch.consumePending()) return@LaunchedEffect
        screen = Screen.Main
        if (state.settings.assistantAutoListen) {
            if (micPermission.granted) {
                controller.startAssistant()
            } else {
                pendingAssistant = true
                micPermission.request()
            }
        } else {
            controller.startAssistant()
        }
    }
    LaunchedEffect(micPermission.granted) {
        if (pendingAssistant && micPermission.granted) {
            pendingAssistant = false
            val forceListen = pendingAssistantForceListen
            pendingAssistantForceListen = false
            controller.startAssistant(forceListen = forceListen)
        }
    }

    LaunchedEffect(state.error) {
        val message = state.error
        if (message != null) {
            snackbar.showSnackbar(message)
            controller.dismissError()
        }
    }

    NanobotTheme(themeMode = state.settings.themeMode) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                val hasSavedCredentials = state.settings.baseUrl.isNotBlank() && state.settings.secret.isNotBlank()
                // With saved credentials the main screen is always usable (cached chats +
                // an inline reconnect banner); the full-screen connect form is only for
                // the very first launch or when credentials are missing.
                val showConnect = !hasSavedCredentials &&
                    (state.connection.phase == ConnectionPhase.IDLE ||
                        state.connection.phase == ConnectionPhase.FAILED ||
                        state.connection.phase == ConnectionPhase.CONNECTING)
                val showFailedConnect = !hasSavedCredentials && state.connection.phase == ConnectionPhase.FAILED
                when {
                    screen == Screen.Settings -> SettingsScreen(
                        controller = controller,
                        state = state,
                        onBack = { screen = Screen.Main },
                    )
                    screen == Screen.Automations -> AutomationsScreen(
                        controller = controller,
                        state = state,
                        onBack = { screen = Screen.Main },
                    )
                    showConnect || showFailedConnect -> ConnectScreen(
                        controller = controller,
                        state = state,
                        onOpenSettings = { screen = Screen.Settings },
                    )
                    else -> MainScreen(
                        controller = controller,
                        state = state,
                        micPermission = micPermission,
                        onOpenSettings = { screen = Screen.Settings },
                        onOpenAutomations = { screen = Screen.Automations },
                    )
                }
                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        }
    }
}
