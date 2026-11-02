package com.nanobot.client.audio

import java.io.File

expect object VoskModelProvider {
    /**
     * Returns the model directory File, or null if model cannot be located/extracted.
     */
    suspend fun getModelDir(): File?
}
