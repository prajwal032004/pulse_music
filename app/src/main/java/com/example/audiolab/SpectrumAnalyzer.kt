package com.example.audiolab

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real spectrum of the audio being played. The audio processor feeds decoded PCM into a ring
 * buffer ([capture], audio thread); the UI loop calls [bands] to run a windowed FFT over the most
 * recent samples and fold it into log-spaced bands. No microphone or RECORD_AUDIO permission needed.
 */
class SpectrumAnalyzer(private val bandCount: Int = 32) {
    private val ring = FloatArray(FFT_SIZE * 2)
    @Volatile private var writePos = 0
    @Volatile var sampleRate = 44100
    @Volatile private var lastCaptureAt = 0L

    private val window = FloatArray(FFT_SIZE) { i -> (0.5 - 0.5 * cos(2 * PI * i / (FFT_SIZE - 1))).toFloat() }
    private val re = FloatArray(FFT_SIZE)
    private val im = FloatArray(FFT_SIZE)

    /** Called on the audio thread with interleaved 16-bit stereo PCM. Does not consume [buffer]. */
    fun capture(buffer: ByteBuffer) {
        val buf = buffer.duplicate().order(ByteOrder.nativeOrder())
        var pos = writePos
        val size = ring.size
        while (buf.remaining() >= 4) {
            val l = buf.short.toFloat()
            val r = buf.short.toFloat()
            ring[pos] = (l + r) * (0.5f / 32768f)
            pos++
            if (pos == size) pos = 0
        }
        writePos = pos
        lastCaptureAt = System.nanoTime()
    }

    /** True when audio has flowed through the processor recently. */
    val isFresh: Boolean get() = System.nanoTime() - lastCaptureAt < 400_000_000L

    /** Band levels 0..1 for the latest audio, or null if nothing has been captured recently. */
    fun bands(): FloatArray? {
        if (!isFresh) return null
        val end = writePos
        val size = ring.size
        for (i in 0 until FFT_SIZE) {
            val idx = (end - FFT_SIZE + i + size) % size
            re[i] = ring[idx] * window[i]
            im[i] = 0f
        }
        fft(re, im)

        val nyquist = sampleRate / 2f
        val binHz = sampleRate.toFloat() / FFT_SIZE
        val out = FloatArray(bandCount)
        for (b in 0 until bandCount) {
            val f0 = MIN_HZ * (MAX_HZ / MIN_HZ).pow(b / bandCount.toFloat())
            val f1 = MIN_HZ * (MAX_HZ / MIN_HZ).pow((b + 1) / bandCount.toFloat())
            val lo = (f0 / binHz).toInt().coerceIn(1, FFT_SIZE / 2 - 1)
            val hi = (f1 / binHz).toInt().coerceIn(lo + 1, FFT_SIZE / 2)
            var peak = 0f
            for (k in lo until hi) {
                if (k * binHz > nyquist) break
                val mag = sqrt(re[k] * re[k] + im[k] * im[k])
                if (mag > peak) peak = mag
            }
            // Magnitude → dBFS (Hann window coherent gain ≈ 0.5), mapped from -72..0 dB to 0..1.
            val db = 20f * log10((peak * 4f / FFT_SIZE).coerceAtLeast(1e-6f))
            out[b] = ((db + 72f) / 72f).coerceIn(0f, 1f)
        }
        return out
    }

    private fun fft(x: FloatArray, y: FloatArray) {
        val n = x.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = x[i]; x[i] = x[j]; x[j] = t
                t = y[i]; y[i] = y[j]; y[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f
                var ci = 0f
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = a + len / 2
                    val tr = x[b] * cr - y[b] * ci
                    val ti = x[b] * ci + y[b] * cr
                    x[b] = x[a] - tr; y[b] = y[a] - ti
                    x[a] += tr; y[a] += ti
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }

    private companion object {
        const val FFT_SIZE = 2048
        const val MIN_HZ = 40f
        const val MAX_HZ = 16000f
    }
}
