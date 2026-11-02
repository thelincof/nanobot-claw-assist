package com.nanobot.client.assistant

import android.service.voice.VoiceInteractionService
import com.nanobot.client.MainActivity
import android.content.Intent

class ClawdVoiceInteractionService : VoiceInteractionService() {
    override fun onLaunchVoiceAssistFromKeyguard() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = AssistantIntents.ACTION
                putExtra(AssistantIntents.EXTRA, true)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            },
        )
    }
}
