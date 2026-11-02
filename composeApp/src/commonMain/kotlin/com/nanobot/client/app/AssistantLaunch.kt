package com.nanobot.client.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Platform entry points (Android ASSIST intent, VoiceInteractionService, app
 * shortcut/tile, desktop `--assistant` flag) publish an invocation here; the
 * Compose root consumes it and starts microphone listening.
 *
 * Invocations are consumed exactly once: recomposing/restoring the Activity must
 * never start the microphone again.
 */
object AssistantLaunch {
    private val mutableRequests = MutableStateFlow(0L)
    val requests: StateFlow<Long> = mutableRequests.asStateFlow()

    @Volatile
    private var handled = 0L

    fun trigger() {
        mutableRequests.update { it + 1 }
    }

    @Synchronized
    fun consumePending(): Boolean {
        val current = mutableRequests.value
        if (current <= handled) return false
        handled = current
        return true
    }
}
