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

import java.nio.FloatBuffer
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

    /** Complex stereo STFT retaining real/imag phase for masked ISTFT vocal reconstruction. */
    class ComplexSpectrogram(
        val real: FloatArray,
        val imag: FloatArray,
        val magnitude: FloatArray,
        val frames: Int,
        val bins: Int,
        val inputLength: Int,
    )

    private val hannWindow: DoubleArray by lazy { periodicHannWindow(FFT_SIZE) }

    /**
     * Computes both complex stereo STFT coefficients and magnitude spectrogram `[channel][bin][frame]`
     * for planar stereo PCM at 44,100 Hz.
     */
    fun computeComplex(left: FloatArray, right: FloatArray, rate: Double = SAMPLE_RATE): ComplexSpectrogram? {
        if (abs(rate - SAMPLE_RATE) > 1.0) return null
        val inputLength = left.size
        if (inputLength == 0 || right.size != inputLength) return null

        val pad = FFT_SIZE / 2
        if (inputLength <= pad + 1) return null

        val paddedLength = inputLength + 2 * pad
        if (paddedLength < FFT_SIZE) return null
        val frames = (paddedLength - FFT_SIZE) / HOP + 1
        val totalSize = CHANNELS * BINS * frames
        val magValues = FloatArray(totalSize)
        val realValues = FloatArray(totalSize)
        val imagValues = FloatArray(totalSize)

        val real = DoubleArray(FFT_SIZE)
        val imag = DoubleArray(FFT_SIZE)

        for (channel in 0 until CHANNELS) {
            val samples = if (channel == 0) left else right
            val channelBase = channel * BINS * frames
            for (frame in 0 until frames) {
                val start = frame * HOP
                for (i in 0 until FFT_SIZE) {
                    real[i] = reflectedSample(samples, pad, start + i) * hannWindow[i]
                    imag[i] = 0.0
                }
                fftInPlace(real, imag)
                for (bin in 0 until BINS) {
                    val idx = channelBase + bin * frames + frame
                    val r = real[bin].toFloat()
                    val im = imag[bin].toFloat()
                    realValues[idx] = r
                    imagValues[idx] = im
                    magValues[idx] = hypot(real[bin], imag[bin]).toFloat()
                }
            }
        }
        return ComplexSpectrogram(
            real = realValues,
            imag = imagValues,
            magnitude = magValues,
            frames = frames,
            bins = BINS,
            inputLength = inputLength,
        )
    }

    /**
     * Reconstructs a mono 44.1 kHz vocal waveform from [complex] by applying a soft mask derived
     * from Open-Unmix [targetMag] (strided by [fixedFrames]) and running overlap-add ISTFT.
     */
    fun reconstructMonoFromMask(
        complex: ComplexSpectrogram,
        targetMag: FloatBuffer,
        fixedFrames: Int,
    ): FloatArray {
        val frames = complex.frames
        val bins = complex.bins
        val pad = FFT_SIZE / 2
        val inputLength = complex.inputLength
        val paddedLength = maxOf(inputLength + 2 * pad, (frames - 1) * HOP + FFT_SIZE)

        val windowNorm = DoubleArray(paddedLength)
        for (frame in 0 until frames) {
            val start = frame * HOP
            for (i in 0 until FFT_SIZE) {
                val w = hannWindow[i]
                windowNorm[start + i] += w * w
            }
        }

        val invReal = DoubleArray(FFT_SIZE)
        val invImag = DoubleArray(FFT_SIZE)
        val monoAccum = DoubleArray(paddedLength)

        for (channel in 0 until CHANNELS) {
            val ola = DoubleArray(paddedLength)
            val specChanBase = channel * bins * frames
            val targetChanBase = channel * bins * fixedFrames
            for (frame in 0 until frames) {
                for (bin in 0 until bins) {
                    val sIdx = specChanBase + bin * frames + frame
                    val tIdx = targetChanBase + bin * fixedFrames + frame
                    val mixMag = complex.magnitude[sIdx]
                    val ratio = if (mixMag > 1e-6f) (targetMag.get(tIdx) / mixMag).coerceIn(0f, 1f) else 0f
                    val re = complex.real[sIdx].toDouble() * ratio
                    val im = complex.imag[sIdx].toDouble() * ratio
                    // Conjugate spectrum before forward fftInPlace to compute inverse DFT.
                    invReal[bin] = re
                    invImag[bin] = -im
                    if (bin in 1 until bins - 1) {
                        val mirror = FFT_SIZE - bin
                        invReal[mirror] = re
                        invImag[mirror] = im
                    }
                }
                fftInPlace(invReal, invImag)
                val start = frame * HOP
                for (i in 0 until FFT_SIZE) {
                    ola[start + i] += (invReal[i] / FFT_SIZE.toDouble()) * hannWindow[i]
                }
            }
            for (i in 0 until paddedLength) {
                val norm = windowNorm[i]
                if (norm > 1e-6) {
                    monoAccum[i] += ola[i] / norm
                }
            }
        }

        return FloatArray(inputLength) { index ->
            (monoAccum[pad + index] / CHANNELS.toDouble()).toFloat()
        }
    }

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

        val real = DoubleArray(FFT_SIZE)
        val imag = DoubleArray(FFT_SIZE)

        for (channel in 0 until CHANNELS) {
            val samples = if (channel == 0) left else right
            val channelBase = channel * BINS * frames
            for (frame in 0 until frames) {
                val start = frame * HOP
                for (i in 0 until FFT_SIZE) {
                    real[i] = reflectedSample(samples, pad, start + i) * hannWindow[i]
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

    private fun reflectedSample(input: FloatArray, pad: Int, index: Int): Double {
        val len = input.size
        return when {
            index < pad -> input[pad - index].toDouble()
            index < pad + len -> input[index - pad].toDouble()
            else -> input[(2 * len + pad - 2) - index].toDouble()
        }
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
