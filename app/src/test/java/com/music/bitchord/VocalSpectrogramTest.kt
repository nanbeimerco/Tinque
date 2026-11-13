package com.music.bitchord

import com.music.bitchord.feature.homeai.LocalHomeAi
import com.music.bitchord.playback.smart.AudioDecoder
import com.music.bitchord.playback.smart.VocalDetector
import com.music.bitchord.playback.smart.VocalSpectrogram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

class VocalSpectrogramTest {

    @Test
    fun `periodicHannWindow matches torch hann_window periodic=True boundaries`() {
        val window = VocalSpectrogram.periodicHannWindow(VocalSpectrogram.FFT_SIZE)
        assertEquals(VocalSpectrogram.FFT_SIZE, window.size)
        assertEquals(0.0, window[0], 1e-12)
        assertEquals(1.0, window[VocalSpectrogram.FFT_SIZE / 2], 1e-12)
        assertTrue(window.last() > 0.0)
    }

    @Test
    fun `fftInPlace peaks sharply at expected bin for pure cosine wave`() {
        val n = 64
        val targetBin = 5
        val real = DoubleArray(n) { i -> kotlin.math.cos(2.0 * PI * targetBin * i / n) }
        val imag = DoubleArray(n)
        VocalSpectrogram.fftInPlace(real, imag)

        val mags = DoubleArray(n / 2 + 1) { bin -> kotlin.math.hypot(real[bin], imag[bin]) }
        val maxBin = mags.indices.maxByOrNull { mags[it] }
        assertEquals(targetBin, maxBin)
        assertEquals(n / 2.0, mags[targetBin], 1e-9)
    }

    @Test
    fun `compute rejects short or mismatched stereo inputs and shapes valid stereo correctly`() {
        val tooShort = FloatArray(1024)
        assertNull(VocalSpectrogram.compute(tooShort, tooShort, VocalSpectrogram.SAMPLE_RATE))
        assertNull(VocalSpectrogram.compute(FloatArray(4096), FloatArray(4000), VocalSpectrogram.SAMPLE_RATE))
        assertNull(VocalSpectrogram.compute(FloatArray(4096), FloatArray(4096), 48_000.0))

        val n = 44_100 // 1 second
        val left = FloatArray(n) { i -> sin(2.0 * PI * 1000.0 * i / VocalSpectrogram.SAMPLE_RATE).toFloat() }
        val right = FloatArray(n) { i -> 0.5f * sin(2.0 * PI * 440.0 * i / VocalSpectrogram.SAMPLE_RATE).toFloat() }
        val spec = VocalSpectrogram.compute(left, right, VocalSpectrogram.SAMPLE_RATE)
        assertNotNull(spec)
        spec!!
        assertEquals(VocalSpectrogram.BINS, spec.bins)
        val expectedFrames = ((n + VocalSpectrogram.FFT_SIZE) - VocalSpectrogram.FFT_SIZE) / VocalSpectrogram.HOP + 1
        assertEquals(expectedFrames, spec.frames)
        assertEquals(VocalSpectrogram.CHANNELS * spec.bins * spec.frames, spec.values.size)

        // Check channel 0 peak around 1000 Hz at middle frame
        val midFrame = spec.frames / 2
        val expected1kBin = (1000.0 * VocalSpectrogram.FFT_SIZE / VocalSpectrogram.SAMPLE_RATE).roundToInt()
        val ch0PeakBin = (0 until spec.bins).maxByOrNull { bin ->
            spec.values[(0 * spec.bins + bin) * spec.frames + midFrame]
        }!!
        assertTrue(abs(ch0PeakBin - expected1kBin) <= 1)

        // Check channel 1 peak around 440 Hz at middle frame
        val expected440Bin = (440.0 * VocalSpectrogram.FFT_SIZE / VocalSpectrogram.SAMPLE_RATE).roundToInt()
        val ch1PeakBin = (0 until spec.bins).maxByOrNull { bin ->
            spec.values[(1 * spec.bins + bin) * spec.frames + midFrame]
        }!!
        assertTrue(abs(ch1PeakBin - expected440Bin) <= 1)
    }

    @Test
    fun `reduceToBandCurve and summarizeVocalCurve distinguish vocal mask energy cleanly`() {
        val bins = VocalSpectrogram.BINS
        val usableFrames = 6
        val totalFloats = VocalSpectrogram.CHANNELS * bins * VocalDetector.FIXED_FRAMES
        val mix = ByteBuffer.allocateDirect(totalFloats * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val target = ByteBuffer.allocateDirect(totalFloats * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

        for (c in 0 until VocalSpectrogram.CHANNELS) {
            for (b in 0 until bins) {
                for (f in 0 until usableFrames) {
                    val idx = (c * bins + b) * VocalDetector.FIXED_FRAMES + f
                    mix.put(idx, 1.0f)
                    // First 3 frames instrumental (0.05), last 3 frames singing (0.70)
                    target.put(idx, if (f < 3) 0.05f else 0.70f)
                }
            }
        }

        val curve = VocalDetector.reduceToBandCurve(mix, target, bins, usableFrames)
        assertEquals(usableFrames, curve.size)
        assertEquals(0.05f, curve[0], 0.01f)
        assertEquals(0.70f, curve[4], 0.01f)
        val summary = VocalDetector.summarizeVocalCurve(curve)
        assertTrue(summary >= 0.65f)
    }

    @Test
    fun `LocalHomeAi cosineSimilarity and resampleLinear behave accurately`() {
        assertEquals(1.0f, LocalHomeAi.cosineSimilarity(floatArrayOf(1f, 2f, 3f), floatArrayOf(2f, 4f, 6f)), 1e-4f)
        assertEquals(-1.0f, LocalHomeAi.cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f)), 1e-4f)
        assertEquals(0.0f, LocalHomeAi.cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-4f)
        assertEquals(0.0f, LocalHomeAi.cosineSimilarity(FloatArray(0), FloatArray(0)), 1e-4f)

        val resampled = AudioDecoder.resampleLinear(FloatArray(480) { 1f }, 48_000.0, 24_000.0)
        assertNotNull(resampled)
        assertEquals(240, resampled!!.size)
        assertEquals(1f, resampled[10], 1e-5f)
    }
}
