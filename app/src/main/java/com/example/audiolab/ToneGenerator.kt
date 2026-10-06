package com.example.audiolab

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

class ToneGenerator {

    private var activeTrack: AudioTrack? = null
    private var isPlaying = false

    suspend fun playHearingTone(
        frequencyHz: Int,
        volumeDb: Float, // -60dB to 0dB
        channel: HearingChannel, // LEFT, RIGHT, BOTH
        durationMs: Long = 1000L
    ) = withContext(Dispatchers.Default) {
        stopTone()

        val sampleRate = 44100
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val amplitude = Math.pow(10.0, volumeDb / 20.0).coerceIn(0.0, 1.0)

        val buffer = ShortArray(numSamples * 2) // Stereo

        for (i in 0 until numSamples) {
            val sampleVal = (sin(2.0 * Math.PI * frequencyHz * i / sampleRate) * amplitude * 32767.0).toInt().toShort()
            val leftSample = if (channel == HearingChannel.LEFT || channel == HearingChannel.BOTH) sampleVal else 0
            val rightSample = if (channel == HearingChannel.RIGHT || channel == HearingChannel.BOTH) sampleVal else 0

            buffer[i * 2] = leftSample
            buffer[i * 2 + 1] = rightSample
        }

        try {
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBufSize, buffer.size * 2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(buffer, 0, buffer.size)
            track.play()
            activeTrack = track
            isPlaying = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopTone() {
        try {
            activeTrack?.stop()
            activeTrack?.release()
        } catch (e: Exception) {
            // Ignore
        } finally {
            activeTrack = null
            isPlaying = false
        }
    }

    enum class HearingChannel {
        LEFT, RIGHT, BOTH
    }
}
