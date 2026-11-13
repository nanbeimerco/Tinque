/*
 * Pure-Kotlin port of Orchard's vocal STFT front end for open-unmix.
 *
 * Copyright (C) 2026 SFG545 (original Orchard implementation)
 * Copyright (C) 2026 Kushagra Singh (BitChord adaptation)
 *
 * Orchard's original source is licensed under the GNU Affero General Public
 * License, version 3 or later. Per AGPLv3 section 13, this file is combined
 * here into BitChord -- a work licensed under the GNU General Public
 * License, version 3 or later -- and remains itself governed by the AGPLv3
 * as part of that combination.
 */

package com.music.bitchord.playback.smart

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Linear-frequency stereo STFT front end expected by open-unmix (`vocals_umxhq_int8.onnx`). */
object VocalSpectrogram {

    const val SAMPLE_RATE = 44_100.0
    const val CHANNELS = 2
    const val FFT_SIZE = 4096
    const val HOP = 1024
    const val BINS = FFT_SIZE / 2 + 1 // 2049

    /**
     * Bin-major flattened magnitude spectrogram `[channel][bin][frame]`:
     * index = `(channel * bins + bin) * frames + frame`, matching ONNX `[1, 2, 2049, frames]`.
     */
    data class Spectrogram(val values: FloatArray, val frames: Int, val bins: Int) {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Spectrogram && frames == other.frames &&
                bins == other.bins && values.contentEquals(other.values))

        override fun hashCode(): Int = 31 * (31 * values.contentHashCode() + frames) + bins
    }

    private val hannWindow: DoubleArray by lazy { periodicHannWindow(FFT_SIZE) }

    /**
     * Computes unnormalized magnitude STFT with reflect padding (`FFT_SIZE / 2` on both ends)
     * and periodic Hann windowing for planar stereo PCM at 44,100 Hz.
     */
    fun compute(left: FloatArray, right: FloatArray, rate: Double = SAMPLE_RATE): Spectrogram? {
        if (abs(rate - SAMPLE_RATE) > 1.0) return null
        val inputLength = left.size
        if (inputLength == 0 || right.size != inputLength) return null

        val pad = FFT_SIZE / 2
        if (inputLength <= pad + 1) return null

        val paddedLength = inputLength + 2 * pad
        if (paddedLength < FFT_SIZE) return null
        val frames = (paddedLength - FFT_SIZE) / HOP + 1
        val values = FloatArray(CHANNELS * BINS * frames)

        val padded = DoubleArray(paddedLength)
        val real = DoubleArray(FFT_SIZE)
        val imag = DoubleArray(FFT_SIZE)
        val channels = arrayOf(left, right)

        for (channel in 0 until CHANNELS) {
            reflectPad(channels[channel], pad, padded)
            val channelBase = channel * BINS * frames
            for (frame in 0 until frames) {
                val start = frame * HOP
                for (i in 0 until FFT_SIZE) {
                    real[i] = padded[start + i] * hannWindow[i]
                    imag[i] = 0.0
                }
                fftInPlace(real, imag)
                for (bin in 0 until BINS) {
                    values[channelBase + bin * frames + frame] = hypot(real[bin], imag[bin]).toFloat()
                }
            }
        }
        return Spectrogram(values = values, frames = frames, bins = BINS)
    }

    internal fun reflectPad(input: FloatArray, pad: Int, out: DoubleArray) {
        val len = input.size
        var writeIdx = 0
        for (i in pad downTo 1) {
            out[writeIdx++] = input[i].toDouble()
        }
        for (i in 0 until len) {
            out[writeIdx++] = input[i].toDouble()
        }
        for (i in 1..pad) {
            out[writeIdx++] = input[len - 1 - i].toDouble()
        }
    }

    /** Periodic Hann matching `torch.hann_window(size, periodic=True)`. */
    internal fun periodicHannWindow(size: Int): DoubleArray = DoubleArray(size) { index ->
        0.5 - 0.5 * cos(2.0 * PI * index.toDouble() / size.toDouble())
    }

    /** Unnormalized in-place radix-2 Cooley-Tukey FFT (`size` must be a power of 2). */
    internal fun fftInPlace(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var swapped = 0
        for (index in 1 until n) {
            var bit = n shr 1
            while ((swapped and bit) != 0) {
                swapped = swapped xor bit
                bit = bit shr 1
            }
            swapped = swapped xor bit
            if (index < swapped) {
                val tr = real[index]
                real[index] = real[swapped]
                real[swapped] = tr
                val ti = imag[index]
                imag[index] = imag[swapped]
                imag[swapped] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wStepReal = cos(angle)
            val wStepImag = sin(angle)
            val half = len shr 1
            var start = 0
            while (start < n) {
                var wReal = 1.0
                var wImag = 0.0
                for (offset in 0 until half) {
                    val i0 = start + offset
                    val i1 = i0 + half
                    val oddReal = real[i1] * wReal - imag[i1] * wImag
                    val oddImag = real[i1] * wImag + imag[i1] * wReal
                    val evenReal = real[i0]
                    val evenImag = imag[i0]
                    real[i0] = evenReal + oddReal
                    imag[i0] = evenImag + oddImag
                    real[i1] = evenReal - oddReal
                    imag[i1] = evenImag - oddImag
                    val nextWReal = wReal * wStepReal - wImag * wStepImag
                    wImag = wReal * wStepImag + wImag * wStepReal
                    wReal = nextWReal
                }
                start += len
            }
            len = len shl 1
        }
    }
}
