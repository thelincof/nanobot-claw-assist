package com.nanobot.client

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nanobot.client.app.AssistantLaunch
import com.nanobot.client.assistant.AssistantIntents
import com.nanobot.client.platform.AppContext
import com.nanobot.client.ui.App
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppContext.context = applicationContext
        installCrashHandler()
        // Only the original launch (not an Activity/process restoration) may be
        // treated as an assistant invocation.
        if (savedInstanceState == null) {
            handleAssistantIntent(intent)
        }
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAssistantIntent(intent)
    }

    private fun handleAssistantIntent(intent: Intent?) {
        if (!AssistantIntents.isAssistantIntent(intent)) return
        AssistantLaunch.trigger()
        // Do not re-use the assist action when the task is restored later.
        intent?.action = null
        intent?.removeExtra(AssistantIntents.EXTRA)
        intent?.data = null
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val file = File(applicationContext.filesDir, "last-crash.txt")
                file.writeText(
                    buildString {
                        appendLine(java.time.Instant.now().toString() + " " + thread.name)
                        appendLine(Log.getStackTraceString(error))
                    },
                )
            }
            Log.e("Clawd", "Uncaught exception", error)
            previous?.uncaughtException(thread, error)
        }
    }
}
