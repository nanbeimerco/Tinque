package com.music.bitchord.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.music.bitchord.data.settings.ParametricEqBand
import com.music.bitchord.data.settings.ParametricEqConfig
import com.music.bitchord.playback.equalizer.BiquadUtils
import com.music.bitchord.playback.equalizer.BiquadUtils.BiquadCoefficients
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * High-precision 64-bit floating-point in-app Parametric Equalizer & DSP processor
 * for decoded PCM. Modeled after RootlessJamesDSP & EEL2 LiveProg:
 * - Supports Peaking (PK), Low-Shelf (LSC), High-Shelf (HSC), LPF, HPF, BPF, Notch, All-Pass
 * - Supports Global bands (both channels) + Left/Right independent bands
 * - Supports Global Preamp + L/R Preamp + per-channel gain offsets
 * - Supports Left <-> Right stereo channel swap (pre-EQ or post-EQ)
 * - Disabled or flat configurations copy the input buffer byte-for-byte unchanged.
 */
@UnstableApi
class ParametricEqAudioProcessor : BaseAudioProcessor() {

    private class BiquadState {
        var x1: Double = 0.0
        var x2: Double = 0.0
        var y1: Double = 0.0
        var y2: Double = 0.0

        fun reset() {
            x1 = 0.0
            x2 = 0.0
            y1 = 0.0
            y2 = 0.0
        }

        fun process(xn: Double, c: BiquadCoefficients): Double {
            var yn = c.b0 * xn + c.b1 * x1 + c.b2 * x2 - c.a1 * y1 - c.a2 * y2
            x2 = x1
            x1 = xn
            y2 = y1
            if (!yn.isFinite()) {
                reset()
                return 0.0
            }
            if (abs(yn) < 1e-30) yn = 0.0
            y1 = yn
            return yn
        }
    }

    @Volatile
    private var config: ParametricEqConfig = ParametricEqConfig()

    private var sampleRateHz = 0
    private var channelCount = 0

    private var appliedConfig: ParametricEqConfig? = null
    private var leftLinearGain: Double = 1.0
    private var rightLinearGain: Double = 1.0

    private var globalCoeffs: Array<BiquadCoefficients> = emptyArray()
    private var leftCoeffs: Array<BiquadCoefficients> = emptyArray()
    private var rightCoeffs: Array<BiquadCoefficients> = emptyArray()

    // Per-channel states for global filters: globalStates[ch][bandIdx]
    private var globalStates: Array<Array<BiquadState>> = emptyArray()
    // Left channel (ch 0) specific filter states
    private var leftStates: Array<BiquadState> = emptyArray()
    // Right channel (ch 1) specific filter states
    private var rightStates: Array<BiquadState> = emptyArray()

    fun setConfig(newConfig: ParametricEqConfig) {
        config = newConfig.sanitized()
    }

    /** Legacy 3-band entry point kept for compatibility. */
    fun setTuning(enabled: Boolean, bands: List<ParametricEqBand>) {
        val current = config
        config = current.copy(
            enabled = enabled,
            globalBands = bands.map { it.sanitized() },
        )
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supportedEncoding = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_24BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_32BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (!supportedEncoding) return AudioProcessor.AudioFormat.NOT_SET
        if (inputAudioFormat.sampleRate <= 0 || inputAudioFormat.channelCount <= 0) {
            return AudioProcessor.AudioFormat.NOT_SET
        }

        sampleRateHz = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        appliedConfig = null
        resetStates()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetStates()
    }

    override fun onReset() {
        sampleRateHz = 0
        channelCount = 0
        appliedConfig = null
        globalCoeffs = emptyArray()
        leftCoeffs = emptyArray()
        rightCoeffs = emptyArray()
        globalStates = emptyArray()
        leftStates = emptyArray()
        rightStates = emptyArray()
    }

    private fun resetStates() {
        for (chStates in globalStates) {
            for (s in chStates) s.reset()
        }
        for (s in leftStates) s.reset()
        for (s in rightStates) s.reset()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val byteCount = inputBuffer.remaining()
        if (byteCount == 0) return
        val output = replaceOutputBuffer(byteCount)
        val current = config
        if (!current.hasActiveProcessing || sampleRateHz <= 0 || channelCount <= 0) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        if (appliedConfig != current) {
            rebuildFilterPipeline(current)
        }

        val bytesPerSample = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> {
                output.put(inputBuffer)
                output.flip()
                return
            }
        }

        val frameBytes = bytesPerSample * channelCount
        inputBuffer.order(ByteOrder.nativeOrder())
        output.order(ByteOrder.nativeOrder())

        val swap = current.swapChannels && channelCount >= 2
        val swapBefore = current.swapBeforeEq
        val gCoeffs = globalCoeffs
        val lCoeffs = leftCoeffs
        val rCoeffs = rightCoeffs
        val lGain = leftLinearGain
        val rGain = rightLinearGain
        val encoding = inputAudioFormat.encoding

        if (channelCount == 2 && gCoeffs.size == globalStates.getOrNull(0)?.size && gCoeffs.size == globalStates.getOrNull(1)?.size) {
            val gStateL = globalStates[0]
            val gStateR = globalStates[1]
            val lState = leftStates
            val rState = rightStates

            while (inputBuffer.remaining() >= frameBytes) {
                var spl0 = readSampleNormalized(inputBuffer, encoding)
                var spl1 = readSampleNormalized(inputBuffer, encoding)

                // 1. Swap Channels First (Left <-> Right)
                if (swap && swapBefore) {
                    val tmp = spl0
                    spl0 = spl1
                    spl1 = tmp
                }

                // 2. Apply Total Preamp Gain (Global + L/R Preamp + channel offset)
                spl0 *= lGain
                spl1 *= rGain

                // 3. Apply Global Filters (Same coefficients for both channels)
                for (b in gCoeffs.indices) {
                    val coeff = gCoeffs[b]
                    spl0 = gStateL[b].process(spl0, coeff)
                    spl1 = gStateR[b].process(spl1, coeff)
                }

                // 4. Apply Channel Specific Filters (L filters to spl0, R filters to spl1)
                for (b in lCoeffs.indices) {
                    spl0 = lState[b].process(spl0, lCoeffs[b])
                }
                for (b in rCoeffs.indices) {
                    spl1 = rState[b].process(spl1, rCoeffs[b])
                }

                // Post-EQ channel swap if configured
                if (swap && !swapBefore) {
                    val tmp = spl0
                    spl0 = spl1
                    spl1 = tmp
                }

                writeSampleNormalized(output, encoding, spl0)
                writeSampleNormalized(output, encoding, spl1)
            }
        } else {
            val frameSamples = DoubleArray(channelCount)
            while (inputBuffer.remaining() >= frameBytes) {
                for (ch in 0 until channelCount) {
                    frameSamples[ch] = readSampleNormalized(inputBuffer, encoding)
                }
                if (swap && swapBefore) {
                    val tmp = frameSamples[0]
                    frameSamples[0] = frameSamples[1]
                    frameSamples[1] = tmp
                }
                for (ch in 0 until channelCount) {
                    var s = frameSamples[ch] * (if (ch == 1) rGain else lGain)
                    val gStateCh = globalStates[ch]
                    for (b in gCoeffs.indices) {
                        s = gStateCh[b].process(s, gCoeffs[b])
                    }
                    if (ch == 0) {
                        for (b in lCoeffs.indices) {
                            s = leftStates[b].process(s, lCoeffs[b])
                        }
                    } else if (ch == 1) {
                        for (b in rCoeffs.indices) {
                            s = rightStates[b].process(s, rCoeffs[b])
                        }
                    }
                    frameSamples[ch] = s
                }
                if (swap && !swapBefore) {
                    val tmp = frameSamples[0]
                    frameSamples[0] = frameSamples[1]
                    frameSamples[1] = tmp
                }
                for (ch in 0 until channelCount) {
                    writeSampleNormalized(output, encoding, frameSamples[ch])
                }
            }
        }

        if (inputBuffer.hasRemaining()) output.put(inputBuffer)
        output.flip()
    }

    private fun readSampleNormalized(buf: ByteBuffer, encoding: Int): Double = when (encoding) {
        C.ENCODING_PCM_16BIT -> buf.short.toDouble() / 32768.0
        C.ENCODING_PCM_24BIT -> {
            val b0 = buf.get().toInt() and 0xFF
            val b1 = buf.get().toInt() and 0xFF
            val b2 = buf.get().toInt() // sign-extended
            val sampleInt = (b2 shl 16) or (b1 shl 8) or b0
            sampleInt.toDouble() / 8388608.0
        }
        C.ENCODING_PCM_32BIT -> buf.int.toDouble() / 2147483648.0
        C.ENCODING_PCM_FLOAT -> {
            val f = buf.float
            if (f.isFinite()) f.toDouble() else 0.0
        }
        else -> 0.0
    }

    private fun writeSampleNormalized(buf: ByteBuffer, encoding: Int, sample: Double) {
        val safe = if (sample.isFinite()) sample else 0.0
        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                val scaled = (safe * 32768.0).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                buf.putShort(scaled.toShort())
            }
            C.ENCODING_PCM_24BIT -> {
                val scaled = (safe * 8388608.0).roundToInt().coerceIn(-8388608, 8388607)
                buf.put((scaled and 0xFF).toByte())
                buf.put(((scaled shr 8) and 0xFF).toByte())
                buf.put(((scaled shr 16) and 0xFF).toByte())
            }
            C.ENCODING_PCM_32BIT -> {
                val scaled = (safe * 2147483648.0).roundToLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                buf.putInt(scaled.toInt())
            }
            C.ENCODING_PCM_FLOAT -> {
                buf.putFloat(safe.toFloat())
            }
        }
    }

    private fun rebuildFilterPipeline(target: ParametricEqConfig) {
        val sr = sampleRateHz.toDouble()
        leftLinearGain = 10.0.pow(target.effectiveLeftPreampDb.toDouble() / 20.0)
        rightLinearGain = 10.0.pow(target.effectiveRightPreampDb.toDouble() / 20.0)

        val activeG = target.activeGlobalBands
        val activeL = target.activeLeftBands
        val activeR = target.activeRightBands

        globalCoeffs = Array(activeG.size) { i -> BiquadUtils.computeCoefficients(activeG[i], sr) }
        leftCoeffs = Array(activeL.size) { i -> BiquadUtils.computeCoefficients(activeL[i], sr) }
        rightCoeffs = Array(activeR.size) { i -> BiquadUtils.computeCoefficients(activeR[i], sr) }

        if (globalStates.size != channelCount || globalStates.firstOrNull()?.size != activeG.size) {
            globalStates = Array(channelCount) { Array(activeG.size) { BiquadState() } }
        }
        if (leftStates.size != activeL.size) {
            leftStates = Array(activeL.size) { BiquadState() }
        }
        if (rightStates.size != activeR.size) {
            rightStates = Array(activeR.size) { BiquadState() }
        }

        appliedConfig = target
    }
}
