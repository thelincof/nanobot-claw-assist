package com.nanobot.client.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Placeholder recognition service required by the VoiceInteractionService
 * manifest contract. Speech is captured by Clawd's own recorder instead.
 */
class ClawdRecognitionService : RecognitionService() {
    override fun onStartListening(intent: Intent, listener: Callback) {
        listener.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onStopListening(listener: Callback) = Unit

    override fun onCancel(listener: Callback) = Unit
}
