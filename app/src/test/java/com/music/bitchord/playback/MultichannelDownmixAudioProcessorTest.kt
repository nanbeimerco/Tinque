package com.music.bitchord.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.music.bitchord.data.settings.ParametricEqFilterType
import com.music.bitchord.playback.equalizer.BiquadUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

class MultichannelDownmixAudioProcessorTest {

    private fun processFloatFrames(
        processor: MultichannelDownmixAudioProcessor,
        frames: Array<FloatArray>,
    ): FloatArray {
        val chCount = frames[0].size
        val inBuf = ByteBuffer.allocateDirect(frames.size * chCount * 4).order(ByteOrder.nativeOrder())
        for (frame in frames) {
            for (sample in frame) {
                inBuf.putFloat(sample)
            }
        }
        inBuf.flip()
        processor.queueInput(inBuf)
        val outBuf = processor.output.order(ByteOrder.nativeOrder())
        val outFloats = FloatArray(outBuf.remaining() / 4)
        var i = 0
        while (outBuf.remaining() >= 4) {
            outFloats[i++] = outBuf.float
        }
        return outFloats
    }

    @Test
    fun stereoInputBypassesAnd714OutputsStereo() {
        val processor = MultichannelDownmixAudioProcessor()
        processor.config = DownmixConfig(enabled = true, preampDb = 0f)

        val stereoFmt = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT)
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, processor.configure(stereoFmt))
        assertFalse(processor.isActivelyDownmixing)

        // 7.1.4 native mask 0x2D63F -> 12ch
        processor.setPendingChannelMask(0x2D63F)
        val atmosFmt = AudioProcessor.AudioFormat(48_000, 12, C.ENCODING_PCM_FLOAT)
        val outFmt = processor.configure(atmosFmt)
        assertEquals(48_000, outFmt.sampleRate)
        assertEquals(2, outFmt.channelCount)
        assertEquals(C.ENCODING_PCM_FLOAT, outFmt.encoding)
        assertTrue(processor.isActivelyDownmixing)
    }

    @Test
    fun nonLfeImpulseDelayedExactly172SamplesWithExactMatrix() {
        val processor = MultichannelDownmixAudioProcessor()
        processor.config = DownmixConfig(enabled = true, preampDb = 0f)
        processor.setPendingChannelMask(0xB58FC) // AudioFormat.CHANNEL_OUT_7POINT1POINT4
        processor.configure(AudioProcessor.AudioFormat(48_000, 12, C.ENCODING_PCM_FLOAT))
        processor.flush()

        val totalFrames = 200
        val frames = Array(totalFrames) { FloatArray(12) }
        // Frame 0: impulse on FL (ch 0) = 1.0, FC (ch 2) = 0.5
        frames[0][0] = 1.0f
        frames[0][2] = 0.5f

        val out = processFloatFrames(processor, frames)
        for (f in 0 until 172) {
            assertEquals("Frame $f L should be delayed", 0f, out[f * 2], 1e-6f)
            assertEquals("Frame $f R should be delayed", 0f, out[f * 2 + 1], 1e-6f)
        }
        // Frame 172: FL(1.0 * 1.0) + FC(0.5 * 0.70710678) on L, FC(0.5 * 0.70710678) on R
        // Note: L sum = 1.35355339 is clamped to 1.0 in PCM_FLOAT output. Verify un-clamped at -6 dB below;
        // check R directly = 0.35355339!
        assertEquals(0.35355338f, out[172 * 2 + 1], 1e-4f)
        assertEquals(1.0f, out[172 * 2], 1e-5f)
        assertEquals(0f, out[173 * 2], 1e-6f)
    }

    @Test
    fun lfeAppliesGain22646AndFourthOrderButterworth125HzLowPass() {
        val processor = MultichannelDownmixAudioProcessor()
        processor.config = DownmixConfig(enabled = true, preampDb = -12f)
        processor.setPendingChannelMask(0xB58FC)
        processor.configure(AudioProcessor.AudioFormat(48_000, 12, C.ENCODING_PCM_FLOAT))
        processor.flush()

        val sr = 48_000.0
        val preampLin = Math.pow(10.0, -12.0 / 20.0)
        // 1) Low frequency 30 Hz sine on LFE (ch 3): should pass at ~2.26464431 * preampLin
        val nFrames = 4800 // 100 ms
        val lowFrames = Array(nFrames) { i ->
            FloatArray(12).also { it[3] = (0.25 * sin(2.0 * PI * 30.0 * i / sr)).toFloat() }
        }
        val lowOut = processFloatFrames(processor, lowFrames)
        var maxLowL = 0f
        for (i in nFrames / 2 until nFrames) {
            maxLowL = maxOf(maxLowL, abs(lowOut[i * 2]))
        }
        val expectedLowPeak = (0.25 * 2.26464431 * preampLin).toFloat()
        assertEquals(expectedLowPeak, maxLowL, expectedLowPeak * 0.05f)

        // 2) High frequency 1000 Hz sine on LFE (ch 3): 3 octaves above 125 Hz -> ~ -72 dB (< 0.0005 of passband)
        processor.flush()
        val highFrames = Array(nFrames) { i ->
            FloatArray(12).also { it[3] = (0.25 * sin(2.0 * PI * 1000.0 * i / sr)).toFloat() }
        }
        val highOut = processFloatFrames(processor, highFrames)
        var maxHighL = 0f
        for (i in nFrames / 2 until nFrames) {
            maxHighL = maxOf(maxHighL, abs(highOut[i * 2]))
        }
        assertTrue("Expected >= 60 dB attenuation at 1 kHz, got maxHighL=$maxHighL vs passband=$expectedLowPeak", maxHighL < expectedLowPeak * 0.001f)
    }

    @Test
    fun muteAndSoloIsolateRequestedChannels() {
        val processor = MultichannelDownmixAudioProcessor()
        processor.setPendingChannelMask(0xB58FC)
        processor.configure(AudioProcessor.AudioFormat(48_000, 12, C.ENCODING_PCM_FLOAT))
        processor.flush()

        // Solo TFL (index 8 in 7.1.4) -> FL (0), FR (1), TFR (9) silenced, TFL (8) audible on L
        processor.config = DownmixConfig(
            enabled = true,
            preampDb = 0f,
            soloChannels = setOf("TFL"),
        )
        val frames = Array(180) { FloatArray(12) }
        frames[0][0] = 0.5f // FL
        frames[0][1] = 0.5f // FR
        frames[0][8] = 0.4f // TFL
        frames[0][9] = 0.6f // TFR
        val out = processFloatFrames(processor, frames)
        assertEquals(0.4f, out[172 * 2], 1e-5f)
        assertEquals(0f, out[172 * 2 + 1], 1e-5f)
    }

    @Test
    fun preampPreventsClippingWhenSummingMultipleChannels() {
        val processor = MultichannelDownmixAudioProcessor()
        processor.config = DownmixConfig(enabled = true, preampDb = -6f)
        processor.setPendingChannelMask(0xB58FC)
        processor.configure(AudioProcessor.AudioFormat(48_000, 12, C.ENCODING_PCM_FLOAT))
        processor.flush()

        val frames = Array(180) { FloatArray(12) }
        frames[0][0] = 0.8f // FL
        frames[0][6] = 0.8f // SL
        val out = processFloatFrames(processor, frames)
        val expected = (1.6 * Math.pow(10.0, -6.0 / 20.0)).toFloat()
        assertEquals(expected, out[172 * 2], 1e-4f)
        assertTrue(out[172 * 2] < 1.0f)
    }

    @Test
    fun lr4AndSecondOrderAllPassMatchDigitalPhaseAndKeepAllPassUnityGain() {
        val sr = 48_000.0
        val fc = 125.0
        val q = 1.0 / sqrt(2.0)
        val lr4Section = BiquadUtils.computeCoefficients(
            frequency = fc,
            gainDb = 0.0,
            q = q,
            filterType = ParametricEqFilterType.LOW_PASS,
            sampleRate = sr,
        )
        val ap2 = BiquadUtils.computeAllPassCoefficients(
            frequency = fc,
            q = q,
            sampleRate = sr,
        )

        fun evalBiquad(c: BiquadUtils.BiquadCoefficients, freqHz: Double): Pair<Double, Double> {
            val w = 2.0 * PI * freqHz / sr
            val cos1 = cos(w)
            val sin1 = sin(w)
            val cos2 = cos(2.0 * w)
            val sin2 = sin(2.0 * w)
            val numRe = c.b0 + c.b1 * cos1 + c.b2 * cos2
            val numIm = -(c.b1 * sin1 + c.b2 * sin2)
            val denRe = 1.0 + c.a1 * cos1 + c.a2 * cos2
            val denIm = -(c.a1 * sin1 + c.a2 * sin2)
            val denMagSq = denRe * denRe + denIm * denIm
            val re = (numRe * denRe + numIm * denIm) / denMagSq
            val im = (numIm * denRe - numRe * denIm) / denMagSq
            return re to im
        }

        val testFreqs = doubleArrayOf(30.0, 60.0, 125.0, 250.0, 500.0, 1000.0, 5000.0, 15000.0)
        for (f in testFreqs) {
            val (sRe, sIm) = evalBiquad(lr4Section, f)
            // LR4 is two cascaded identical 2nd-order Butterworth sections: H_LR4 = H_section^2
            val lr4Re = sRe * sRe - sIm * sIm
            val lr4Im = 2.0 * sRe * sIm

            val (apRe, apIm) = evalBiquad(ap2, f)
            val apMag = hypot(apRe, apIm)
            assertEquals("All-Pass magnitude must be 1.0 at $f Hz", 1.0, apMag, 1e-9)

            // Phase difference angle via complex conjugate product: arg(H_LR4 * conj(H_AP2))
            val crossRe = lr4Re * apRe + lr4Im * apIm
            val crossIm = lr4Im * apRe - lr4Re * apIm
            val phaseDiff = abs(atan2(crossIm, crossRe))
            assertTrue("Phase mismatch at $f Hz: $phaseDiff rad", phaseDiff < 1e-9)
        }
    }

    @Test
    fun lr4AllPassModeEliminatesDryDelayAndRespondsAtFrameZero() {
        val processor = MultichannelDownmixAudioProcessor()
        processor.config = DownmixConfig(
            enabled = true,
            preampDb = 0f,
            lfeFilterMode = DownmixLfeFilterMode.LR4_ALLPASS,
        )
        processor.setPendingChannelMask(0xB58FC)
        processor.configure(AudioProcessor.AudioFormat(48_000, 12, C.ENCODING_PCM_FLOAT))
        processor.flush()

        val frames = Array(16) { FloatArray(12) }
        frames[0][0] = 0.5f // FL impulse at frame 0

        val out = processFloatFrames(processor, frames)
        // 2nd-order APF sample 0 output = b0 * 0.5 != 0 (no 172-sample delay!)
        assertTrue("Expected immediate non-zero response at frame 0, got ${out[0]}", abs(out[0]) > 0.4f)
        assertEquals(0f, out[1], 1e-6f)
    }

    @Test
    fun clipCounterIncrementsAndResetsOnRequestAndReconfigure() {
        val processor = MultichannelDownmixAudioProcessor()
        processor.isPrimaryChain = true
        MultichannelDownmixMonitor.onUiAttached()
        try {
            processor.config = DownmixConfig(enabled = true, preampDb = 0f)
            processor.setPendingChannelMask(0xB58FC)
            processor.configure(AudioProcessor.AudioFormat(48_000, 12, C.ENCODING_PCM_FLOAT))
            processor.flush()

            // Feed 1000 frames so publish interval (16ms = 768 frames) triggers, with clipping on frame 172..174.
            val frames = Array(1000) { idx ->
                FloatArray(12).also {
                    if (idx in 0..2) {
                        it[0] = 0.9f // FL
                        it[6] = 0.9f // SL -> sums to 1.8 >= CLIP_THRESHOLD on L
                    }
                }
            }
            processFloatFrames(processor, frames)
            assertTrue("Expected clippedSamples >= 3, was ${MultichannelDownmixMonitor.snapshot.value.clippedSamples}", MultichannelDownmixMonitor.snapshot.value.clippedSamples >= 3L)

            processor.resetClipCounter()
            assertEquals(0L, MultichannelDownmixMonitor.snapshot.value.clippedSamples)

            // Re-trigger clips after the 16ms publish throttle window, then verify configure() also resets
            Thread.sleep(20)
            processFloatFrames(processor, frames)
            assertTrue(MultichannelDownmixMonitor.snapshot.value.clippedSamples >= 3L)
            processor.configure(AudioProcessor.AudioFormat(48_000, 12, C.ENCODING_PCM_FLOAT))
            assertEquals(0L, MultichannelDownmixMonitor.snapshot.value.clippedSamples)
        } finally {
            MultichannelDownmixMonitor.onUiDetached()
        }
    }

    @Test
    fun autoHeadroomLowersPreampWhenSummedOutputApproachesZeroDbfs() {
        val processor = MultichannelDownmixAudioProcessor()
        var adjustedTo: Float? = null
        processor.onAutoHeadroomAdjusted = { adjustedTo = it }
        processor.config = DownmixConfig(
            enabled = true,
            preampDb = 0f,
            autoHeadroom = true,
            lfeFilterMode = DownmixLfeFilterMode.LR4_ALLPASS,
        )
        processor.setPendingChannelMask(0xB58FC)
        processor.configure(AudioProcessor.AudioFormat(48_000, 12, C.ENCODING_PCM_FLOAT))
        processor.flush()

        // FL + SL = 1.0 + 1.0 = 2.0 (+6.02 dBFS summed) at frame 0 (All-Pass b0*2.0 ~ +5.8 dB)
        val frames = Array(16) { FloatArray(12) }
        frames[0][0] = 1.0f
        frames[0][6] = 1.0f
        val out = processFloatFrames(processor, frames)

        assertTrue("Auto-headroom should lower preamp below 0 dB, got $adjustedTo", (adjustedTo ?: 0f) < -4f)
        assertTrue("Output sample should be attenuated below clipping threshold, got ${out[0]}", abs(out[0]) < 0.99f)
    }
}
