package com.nanobot.client.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

class AndroidAudioPlayer : AudioPlayer {
    @Volatile private var track: AudioTrack? = null
    private val generation = AtomicInteger(0)

    override fun play(audio: PcmAudio) {
        if (audio.samples.isEmpty() || audio.sampleRate <= 0) return
        val myGeneration = generation.incrementAndGet()
        val channelMask = if (audio.channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val minBuffer = AudioTrack.getMinBufferSize(
            audio.sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = max(minBuffer, audio.sampleRate / 5 * 2 * audio.channels)
        val player = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(audio.sampleRate)
                        .setChannelMask(channelMask)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Exception) {
            return
        }
        track = player
        try {
            player.play()
            var offset = 0
            while (offset < audio.samples.size && myGeneration == generation.get()) {
                val count = min(4096, audio.samples.size - offset)
                val written = player.write(audio.samples, offset, count)
                if (written <= 0) break
                offset += written
            }
            if (myGeneration == generation.get()) player.stop()
        } catch (_: Exception) {
        } finally {
            try {
                player.release()
            } catch (_: Exception) {
            }
            if (track === player) track = null
        }
    }

    override fun stop() {
        generation.incrementAndGet()
        val player = track
        try {
            player?.pause()
        } catch (_: Exception) {
        }
        try {
            player?.flush()
        } catch (_: Exception) {
        }
        try {
            player?.stop()
        } catch (_: Exception) {
        }
    }
}

actual fun createAudioPlayer(): AudioPlayer = AndroidAudioPlayer()
