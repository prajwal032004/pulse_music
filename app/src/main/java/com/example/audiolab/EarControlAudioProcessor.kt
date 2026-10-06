package com.example.audiolab

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/**
 * Real-time stereo processor inserted into ExoPlayer's audio sink. It applies per-ear volume,
 * stereo balance, channel swap, mono down-mix and EQ preamp gain to 16-bit PCM.
 *
 * Settings are read once per buffer from volatile fields, so changes are heard immediately; the
 * gain ramps across each buffer to avoid clicks.
 */
@OptIn(UnstableApi::class)
class EarControlAudioProcessor : BaseAudioProcessor() {

    @Volatile private var targetLeft = 1f
    @Volatile private var targetRight = 1f
    @Volatile private var swap = false
    @Volatile private var mono = false

    /** Taps the decoded audio for the Audio Lab's live spectrum. */
    val analyzer = SpectrumAnalyzer()

    private var currentLeft = 1f
    private var currentRight = 1f

    /**
     * @param leftPercent/rightPercent per-ear volume 0..100
     * @param balance -1 (full left) .. +1 (full right)
     * @param preampDb -12..+12 dB applied to both channels
     */
    fun update(leftPercent: Int, rightPercent: Int, balance: Float, swapChannels: Boolean, monoMix: Boolean, preampDb: Float) {
        val pre = 10f.pow(preampDb / 20f)
        val balLeft = if (balance > 0f) 1f - balance else 1f
        val balRight = if (balance < 0f) 1f + balance else 1f
        targetLeft = (leftPercent / 100f) * balLeft * pre
        targetRight = (rightPercent / 100f) * balRight * pre
        swap = swapChannels
        mono = monoMix
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Only 16-bit stereo is processed; anything else passes through untouched (NOT_SET = inactive).
        val supported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT && inputAudioFormat.channelCount == 2
        if (supported) analyzer.sampleRate = inputAudioFormat.sampleRate
        return if (supported) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        analyzer.capture(inputBuffer)
        val output = replaceOutputBuffer(remaining)
        val tl = targetLeft
        val tr = targetRight
        val doSwap = swap
        val doMono = mono
        val neutral = !doSwap && !doMono && tl == 1f && tr == 1f && currentLeft == 1f && currentRight == 1f
        if (neutral) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        val frames = remaining / 4
        val stepL = if (frames > 0) (tl - currentLeft) / frames else 0f
        val stepR = if (frames > 0) (tr - currentRight) / frames else 0f
        var gl = currentLeft
        var gr = currentRight
        repeat(frames) {
            var l = inputBuffer.short.toFloat()
            var r = inputBuffer.short.toFloat()
            if (doSwap) {
                val t = l; l = r; r = t
            }
            if (doMono) {
                val m = (l + r) * 0.5f
                l = m; r = m
            }
            gl += stepL
            gr += stepR
            output.putShort(clamp(l * gl))
            output.putShort(clamp(r * gr))
        }
        // Copy any trailing partial frame unchanged.
        while (inputBuffer.hasRemaining()) output.put(inputBuffer.get())
        currentLeft = tl
        currentRight = tr
        output.flip()
    }

    override fun onReset() {
        currentLeft = targetLeft
        currentRight = targetRight
    }

    private fun clamp(sample: Float): Short = when {
        sample > Short.MAX_VALUE -> Short.MAX_VALUE
        sample < Short.MIN_VALUE -> Short.MIN_VALUE
        else -> sample.toInt().toShort()
    }
}
