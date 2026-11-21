package com.music.bitchord.data.lyrics.align

import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.withInstrumentalGaps
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Pure-Kotlin Viterbi CTC forced alignment decoder and Enhanced-LRC line builder.
 * Takes per-frame character log-posteriors (e.g. from MMS-FA at 50 fps) and aligns
 * a sequence of non-blank character token IDs monotonically onto the audio timeline.
 */
object CtcForcedAligner {

    private const val MAX_DP_CELLS = 45_000_000L
    private const val MIN_WORD_DURATION_MS = 40L

    data class TokenSpan(
        val startFrame: Int,
        val endFrame: Int, // exclusive
    )

    /** Applies numerically stable row-wise log-softmax in-place to `[frames, vocabSize]`. */
    fun logSoftmaxInPlace(logits: FloatArray, frames: Int, vocabSize: Int) {
        if (frames <= 0 || vocabSize <= 0 || logits.size < frames * vocabSize) return
        for (f in 0 until frames) {
            val base = f * vocabSize
            var maxVal = Float.NEGATIVE_INFINITY
            for (v in 0 until vocabSize) {
                val x = logits[base + v]
                if (x.isFinite() && x > maxVal) maxVal = x
            }
            if (!maxVal.isFinite()) {
                val uniform = -ln(vocabSize.toDouble()).toFloat()
                for (v in 0 until vocabSize) logits[base + v] = uniform
                continue
            }
            var sumExp = 0.0
            for (v in 0 until vocabSize) {
                val x = logits[base + v]
                if (x.isFinite()) {
                    sumExp += exp((x - maxVal).toDouble())
                }
            }
            val logZ = maxVal.toDouble() + ln(sumExp.coerceAtLeast(1e-12))
            for (v in 0 until vocabSize) {
                val x = logits[base + v]
                logits[base + v] = if (x.isFinite()) (x - logZ).toFloat() else -40f
            }
        }
    }

    /**
     * Finds the highest-scoring Viterbi path aligning [tokenIds] across `[0, frames)`
     * of flattened `[frames, vocabSize]` log-probabilities.
     */
    fun align(
        emissions: FloatArray,
        frames: Int,
        vocabSize: Int,
        tokenIds: IntArray,
        blankId: Int = 0,
    ): List<TokenSpan>? {
        val numTokens = tokenIds.size
        if (frames <= 0 || vocabSize <= 0 || numTokens == 0) return null
        if (emissions.size < frames * vocabSize) return null
        if (frames < numTokens) return null

        var effEmissions = emissions
        var effFrames = frames
        var stride = 1
        val stateCount = 2 * numTokens + 1

        while (effFrames.toLong() * stateCount > MAX_DP_CELLS && effFrames / 2 >= numTokens) {
            val halfFrames = effFrames / 2
            val downsampled = FloatArray(halfFrames * vocabSize)
            for (t in 0 until halfFrames) {
                val src0 = (t * 2) * vocabSize
                val src1 = (t * 2 + 1) * vocabSize
                val dst = t * vocabSize
                for (v in 0 until vocabSize) {
                    downsampled[dst + v] = 0.5f * (effEmissions[src0 + v] + effEmissions[src1 + v])
                }
            }
            effEmissions = downsampled
            effFrames = halfFrames
            stride *= 2
        }

        if (effFrames.toLong() * stateCount > MAX_DP_CELLS) {
            return null
        }

        val stateToken = IntArray(stateCount) { s ->
            if ((s and 1) == 0) blankId else tokenIds[s shr 1]
        }

        var prev = FloatArray(stateCount) { Float.NEGATIVE_INFINITY }
        var curr = FloatArray(stateCount) { Float.NEGATIVE_INFINITY }
        val backpointers = ByteArray(effFrames * stateCount)

        prev[0] = effEmissions[blankId.coerceIn(0, vocabSize - 1)]
        prev[1] = effEmissions[tokenIds[0].coerceIn(0, vocabSize - 1)]

        for (t in 1 until effFrames) {
            val rowOffset = t * vocabSize
            val bpOffset = t * stateCount
            for (s in 0 until stateCount) {
                var bestScore = prev[s]
                var step = 0
                if (s >= 1 && prev[s - 1] > bestScore) {
                    bestScore = prev[s - 1]
                    step = 1
                }
                if (s >= 2 && stateToken[s] != blankId && stateToken[s] != stateToken[s - 2] && prev[s - 2] > bestScore) {
                    bestScore = prev[s - 2]
                    step = 2
                }
                if (bestScore == Float.NEGATIVE_INFINITY) {
                    curr[s] = Float.NEGATIVE_INFINITY
                } else {
                    val tok = stateToken[s].coerceIn(0, vocabSize - 1)
                    curr[s] = bestScore + effEmissions[rowOffset + tok]
                }
                backpointers[bpOffset + s] = step.toByte()
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }

        val finalState = if (prev[stateCount - 1] >= prev[stateCount - 2]) stateCount - 1 else stateCount - 2
        if (prev[finalState] == Float.NEGATIVE_INFINITY) return null

        val minFrame = IntArray(numTokens) { Int.MAX_VALUE }
        val maxFrame = IntArray(numTokens) { Int.MIN_VALUE }

        var s = finalState
        for (t in effFrames - 1 downTo 0) {
            if ((s and 1) == 1) {
                val tokenIdx = s shr 1
                if (t < minFrame[tokenIdx]) minFrame[tokenIdx] = t
                if (t > maxFrame[tokenIdx]) maxFrame[tokenIdx] = t
            }
            if (t > 0) {
                s -= backpointers[t * stateCount + s].toInt()
            }
        }

        val spans = ArrayList<TokenSpan>(numTokens)
        var prevEnd = 0
        for (k in 0 until numTokens) {
            val rawStart = if (minFrame[k] == Int.MAX_VALUE) prevEnd else (minFrame[k] * stride)
            val rawEnd = if (maxFrame[k] == Int.MIN_VALUE) rawStart + stride else ((maxFrame[k] + 1) * stride)
            val start = rawStart.coerceIn(prevEnd, frames - 1)
            val end = rawEnd.coerceIn(start + 1, frames)
            spans += TokenSpan(startFrame = start, endFrame = end)
            prevEnd = end.coerceAtMost(frames - 1)
        }
        return spans
    }

    /**
     * Converts aligned character [spans] back into synced [LyricLine] entries with
     * word-level [LyricWord] timing and automatic instrumental break detection.
     */
    fun buildSyncedLines(
        mapped: LyricsG2pMapper.MappedLyrics,
        spans: List<TokenSpan>,
        frameDurationMs: Double,
        totalDurationMs: Long,
    ): List<LyricLine> {
        if (mapped.lines.isEmpty() || spans.size < mapped.tokenIds.size) return emptyList()
        val maxMs = totalDurationMs.coerceAtLeast(1_000L)

        val result = ArrayList<LyricLine>(mapped.lines.size + 4)
        var cursorMs = 0L

        for (line in mapped.lines) {
            if (line.units.isEmpty()) continue
            val words = ArrayList<LyricWord>(line.units.size)

            for (unitIdx in line.units.indices) {
                val unit = line.units[unitIdx]
                val rawStart: Long
                val rawEnd: Long
                if (unit.tokenCount > 0) {
                    val firstSpan = spans[unit.tokenStartIndex]
                    val lastSpan = spans[unit.tokenStartIndex + unit.tokenCount - 1]
                    rawStart = (firstSpan.startFrame * frameDurationMs).roundToLong()
                    rawEnd = (lastSpan.endFrame * frameDurationMs).roundToLong()
                } else {
                    val nextTokenStart = line.units.drop(unitIdx + 1)
                        .firstOrNull { it.tokenCount > 0 }
                        ?.let { (spans[it.tokenStartIndex].startFrame * frameDurationMs).roundToLong() }
                    rawStart = cursorMs
                    rawEnd = max(cursorMs + MIN_WORD_DURATION_MS, nextTokenStart ?: (cursorMs + MIN_WORD_DURATION_MS))
                }
                val startMs = max(cursorMs, rawStart).coerceAtMost(maxMs)
                val minEnd = min(maxMs, startMs + MIN_WORD_DURATION_MS)
                val endMs = max(minEnd, rawEnd).coerceIn(startMs, maxMs)
                words += LyricWord(
                    startMs = startMs,
                    endMs = endMs,
                    text = unit.text,
                )
                cursorMs = endMs
            }

            if (words.isNotEmpty()) {
                result += LyricLine(
                    timeMs = words.first().startMs,
                    text = line.displayText,
                    words = words,
                    sungUntilMs = words.last().endMs,
                )
            }
        }

        return result.withInstrumentalGaps()
    }
}
