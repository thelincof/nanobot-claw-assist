package com.nanobot.client

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.nanobot.client.app.AssistantLaunch
import com.nanobot.client.resources.Res
import com.nanobot.client.resources.nanobot
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.painterResource

fun main(args: Array<String>) {
    installCrashHandler()
    val assistant = args.any { it == "--assistant" || it == "--listen" }
    if (!DesktopSingleInstance.acquire()) {
        if (assistant) DesktopSingleInstance.forwardAssistantTrigger()
        return
    }
    DesktopSingleInstance.watchForAssistantTriggers { AssistantLaunch.trigger() }
    if (assistant) AssistantLaunch.trigger()
    runApp()
}

private fun installCrashHandler() {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        runCatching {
            val dir = java.io.File(System.getProperty("user.home"), ".clawd").apply { mkdirs() }
            java.io.File(dir, "last-crash.txt").writeText(
                buildString {
                    appendLine(java.time.Instant.now().toString() + " " + thread.name)
                    error.stackTraceToString().forEach { append(it) }
                },
            )
        }
        error.printStackTrace()
        previous?.uncaughtException(thread, error)
    }
}

private fun runApp() = application {
    val icon = painterResource(Res.drawable.nanobot)
    Window(
        onCloseRequest = ::exitApplication,
        title = "Clawd",
        icon = icon,
        state = rememberWindowState(
            width = 1100.dp,
            height = 700.dp,
            position = WindowPosition(Alignment.Center),
        ),
    ) {
        val desktopWindow = window
        LaunchedEffect(Unit) {
            AssistantLaunch.requests.drop(1).collect {
                desktopWindow.toFront()
                desktopWindow.requestFocus()
            }
        }
        App()
    }
}
