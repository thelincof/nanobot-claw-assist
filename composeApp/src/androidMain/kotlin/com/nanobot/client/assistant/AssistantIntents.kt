package com.nanobot.client.assistant

import android.content.Context
import android.content.Intent
import com.nanobot.client.MainActivity

object AssistantIntents {
    const val ACTION = "com.nanobot.client.action.ASSIST"
    const val EXTRA = "com.nanobot.client.extra.ASSISTANT"

    fun isAssistantIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        if (intent.getBooleanExtra(EXTRA, false)) return true
        when (intent.action) {
            Intent.ACTION_ASSIST,
            "android.intent.action.VOICE_COMMAND",
            ACTION,
            -> return true
        }
        val data = intent.data
        return data?.scheme == "clawd" && data.host == "assistant"
    }

    fun launch(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION
            putExtra(EXTRA, true)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        context.startActivity(intent)
    }
}
