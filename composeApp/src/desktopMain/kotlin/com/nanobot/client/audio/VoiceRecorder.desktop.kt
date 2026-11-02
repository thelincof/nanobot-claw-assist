package com.nanobot.client.audio

actual fun createVoiceRecorder(): VoiceRecorder = JvmVoiceRecorder(createAudioInput())
