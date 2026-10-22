package com.music.bitchord.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.music.bitchord.data.settings.ParametricEqBand
import com.music.bitchord.data.settings.ParametricEqConfig
import com.music.bitchord.data.settings.ParametricEqFilterType
import com.music.bitchord.playback.equalizer.BiquadUtils
import com.music.bitchord.playback.equalizer.EelEqParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow

class EelEqParserAndProcessorTest {

    private fun loadSampleEelText(): String {
        val candidates = listOf(
            File("src/main/assets/eq/upper 6kHz no touch ig955 eq_swap.eel"),
            File("app/src/main/assets/eq/upper 6kHz no touch ig955 eq_swap.eel"),
            File("../upper 6kHz no touch ig955 eq_swap.eel"),
        )
        return candidates.first { it.exists() }.readText()
    }

    @Test
    fun parseUpper6kHzNoTouchIg955EqSwapEel_extractsAllBandsPreampAndSwap() {
        val text = loadSampleEelText()
        val result = EelEqParser.parse(text, "upper 6kHz no touch ig955 eq_swap.eel")
        val preset = result.preset
        val report = result.report

        assertEquals("upper 6kHz no touch ig955 eq (swap)", preset.name)
        assertTrue(preset.swapChannels)
        assertTrue(preset.swapBeforeEq)
        assertEquals(-1.5f, preset.globalPreampDb, 0.001f)
        assertEquals(-1.7f, preset.lrPreampDb, 0.001f)
        assertEquals(-3.2f, preset.totalPreampDb, 0.001f)

        // 7 Global bands
        assertEquals(7, preset.globalBands.size)
        assertEquals(ParametricEqFilterType.LOW_SHELF, preset.globalBands[0].filterType)
        assertEquals(23f, preset.globalBands[0].frequencyHz, 0.01f)
        assertEquals(0.758f, preset.globalBands[0].q, 0.001f)
        assertEquals(1.37f, preset.globalBands[0].gainDb, 0.001f)

        assertEquals(ParametricEqFilterType.PEAKING, preset.globalBands[2].filterType)
        assertEquals(141f, preset.globalBands[2].frequencyHz, 0.01f)
        assertEquals(0.297f, preset.globalBands[2].q, 0.001f)
        assertEquals(-6.31f, preset.globalBands[2].gainDb, 0.001f)

        assertEquals(ParametricEqFilterType.PEAKING, preset.globalBands[6].filterType)
        assertEquals(4456f, preset.globalBands[6].frequencyHz, 0.01f)
        assertEquals(1.609f, preset.globalBands[6].q, 0.001f)
        assertEquals(1.24f, preset.globalBands[6].gainDb, 0.001f)

        // 10 Left bands & 10 Right bands
        assertEquals(10, preset.leftBands.size)
        assertEquals(10, preset.rightBands.size)

        assertEquals(ParametricEqFilterType.LOW_SHELF, preset.leftBands[0].filterType)
        assertEquals(36f, preset.leftBands[0].frequencyHz, 0.01f)
        assertEquals(0.405f, preset.leftBands[0].q, 0.001f)
        assertEquals(0.81f, preset.leftBands[0].gainDb, 0.001f)
        assertEquals(-0.81f, preset.rightBands[0].gainDb, 0.001f)

        assertEquals(ParametricEqFilterType.HIGH_SHELF, preset.leftBands[1].filterType)
        assertEquals(41f, preset.leftBands[1].frequencyHz, 0.01f)
        assertEquals(0.895f, preset.leftBands[1].q, 0.001f)
        assertEquals(0.07f, preset.leftBands[1].gainDb, 0.001f)
        assertEquals(-0.07f, preset.rightBands[1].gainDb, 0.001f)

        assertEquals(ParametricEqFilterType.PEAKING, preset.leftBands[2].filterType)
        assertEquals(8978f, preset.leftBands[2].frequencyHz, 0.01f)
        assertEquals(3.18f, preset.leftBands[2].q, 0.001f)
        assertEquals(-1.74f, preset.leftBands[2].gainDb, 0.001f)
        assertEquals(1.74f, preset.rightBands[2].gainDb, 0.001f)

        assertEquals(10, report.lrPairs.size)
        assertTrue(report.lrPairs.all { it.isComplementary })
    }

    @Test
    fun exportAndReparseEel_roundTripsAccurately() {
        val text = loadSampleEelText()
        val first = EelEqParser.parse(text, "upper 6kHz no touch ig955 eq_swap.eel").preset
        val exported = BiquadUtils.toEelString(first.toConfig(enabled = true), first.name)
        val second = EelEqParser.parse(exported, "exported.eel").preset

        assertEquals(first.name, second.name)
        assertEquals(first.swapChannels, second.swapChannels)
        assertEquals(first.swapBeforeEq, second.swapBeforeEq)
        assertEquals(first.globalPreampDb, second.globalPreampDb, 0.01f)
        assertEquals(first.lrPreampDb, second.lrPreampDb, 0.01f)
        assertEquals(first.globalBands.size, second.globalBands.size)
        assertEquals(first.leftBands.size, second.leftBands.size)
        assertEquals(first.rightBands.size, second.rightBands.size)
    }

    @Test
    fun audioProcessor_swapsChannelsAndAppliesPreampAndIndependentEq() {
        val processor = ParametricEqAudioProcessor()
        val format = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT)
        processor.configure(format)
        processor.flush()

        // Test 1: Pure channel swap + -6 dB preamp
        val config = ParametricEqConfig(
            enabled = true,
            swapChannels = true,
            swapBeforeEq = true,
            globalPreampDb = -6.0f,
            lrPreampDb = 0f,
            globalBands = emptyList(),
            leftBands = emptyList(),
            rightBands = emptyList(),
        )
        processor.setConfig(config)

        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
        input.putFloat(0.8f) // input Left (spl0)
        input.putFloat(-0.4f) // input Right (spl1)
        input.flip()

        processor.queueInput(input)
        val out = processor.output.order(ByteOrder.nativeOrder())
        val outLeft = out.float
        val outRight = out.float

        val expectedScale = 10.0.pow(-6.0 / 20.0).toFloat()
        // Because swapChannels = true, outLeft comes from inputRight (-0.4f) * scale, outRight from inputLeft (0.8f) * scale
        assertEquals(-0.4f * expectedScale, outLeft, 1e-4f)
        assertEquals(0.8f * expectedScale, outRight, 1e-4f)

        // Test 2: Full ig955 .eel config processes stereo frames without NaN and produces different L/R outputs
        val ig955Config = EelEqParser.parse(loadSampleEelText()).preset.toConfig(enabled = true)
        processor.setConfig(ig955Config)
        processor.flush()

        val frames = 64
        val buf = ByteBuffer.allocateDirect(frames * 2 * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            // Identical impulse/sine on both input channels: L/R independent EQ will make L and R differ
            val s = if (i == 0) 0.5f else 0.1f
            buf.putFloat(s)
            buf.putFloat(s)
        }
        buf.flip()

        processor.queueInput(buf)
        val eqOut = processor.output.order(ByteOrder.nativeOrder())
        var maxDiffLR = 0f
        for (i in 0 until frames) {
            val l = eqOut.float
            val r = eqOut.float
            assertTrue(l.isFinite())
            assertTrue(r.isFinite())
            maxDiffLR = maxOf(maxDiffLR, abs(l - r))
        }
        // Because leftBands and rightBands have opposite gains, L and R outputs must differ on identical inputs
        assertTrue("Expected L and R independent filters to diverge on identical input, maxDiff=$maxDiffLR", maxDiffLR > 1e-4f)
        assertFalse(ig955Config.copy(enabled = false).hasActiveProcessing)
    }
}
