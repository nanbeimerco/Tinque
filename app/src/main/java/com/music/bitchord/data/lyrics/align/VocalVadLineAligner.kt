package com.music.bitchord.data.lyrics.align

import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsFurigana
import com.music.bitchord.data.lyrics.withInstrumentalGaps
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Tier-1 on-device line-level lyric aligner powered solely by the bundled Open-Unmix
 * vocal presence curve (`vocals_umxhq_int8.onnx`), requiring 0 MB of extra model downloads.
 *
 * Detects sung vocal segments using hysteresis thresholding so intros, guitar solos,
 * and outros are skipped, then projects weighted lyric lines onto active vocal time.
 */
object VocalVadLineAligner {

    private const val MIN_SEGMENT_MS = 400L
    private const val MERGE_GAP_MS = 1_800L

    internal data class VocalSegment(
        val startMs: Long,
        val endMs: Long,
    ) {
        val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    }

    fun align(
        plainText: String,
        vocalCurve: FloatArray,
        frameDurationSec: Double,
        totalDurationMs: Long,
    ): List<LyricLine> {
        val lines = parseCleanLines(plainText)
        if (lines.isEmpty()) return emptyList()

        val safeDurationMs = totalDurationMs.coerceAtLeast(lines.size * 500L)
        val segments = detectVocalSegments(
            vocalCurve = vocalCurve,
            frameDurationSec = frameDurationSec,
            totalDurationMs = safeDurationMs,
        )

        val totalVocalMs = segments.sumOf { it.durationMs }.coerceAtLeast(1_000L)
        val weights = lines.map { estimateSyllableWeight(it) + 1.5 }
        val totalWeight = weights.sum().coerceAtLeast(1.0)

        var cumWeight = 0.0
        val lineVirtualRanges = ArrayList<Pair<Long, Long>>(lines.size)
        for (i in lines.indices) {
            val vStart = ((cumWeight / totalWeight) * totalVocalMs).roundToLong()
            cumWeight += weights[i]
            val vEnd = ((cumWeight / totalWeight) * totalVocalMs).roundToLong().coerceAtLeast(vStart + 250L)
            lineVirtualRanges += vStart to min(vEnd, totalVocalMs)
        }

        val aligned = ArrayList<LyricLine>(lines.size)
        var prevEndMs = 0L
        for (i in lines.indices) {
            val (vStart, vEnd) = lineVirtualRanges[i]
            val startSegIdx = findSegmentIndex(segments, vStart)
            var startMs = virtualToRealMs(segments, startSegIdx, vStart)
            val startSeg = segments[startSegIdx]
            // Avoid starting a brand-new line in the final tiny tail (<450ms) before an instrumental break.
            if (startSeg.endMs - startMs < 450L && startSegIdx + 1 < segments.size && startSeg.durationMs > 1_000L) {
                startMs = segments[startSegIdx + 1].startMs
            }

            startMs = max(prevEndMs, startMs).coerceAtMost((safeDurationMs - 200L).coerceAtLeast(0L))
            val rawEndMs = virtualToRealMs(segments, findSegmentIndex(segments, vEnd), vEnd)
            val endSeg = segments[findSegmentIndex(segments, vStart)]
            val cappedEndMs = if (rawEndMs > endSeg.endMs + 2_000L && endSeg.endMs > startMs + 300L) {
                endSeg.endMs
            } else {
                rawEndMs
            }
            val endMs = max(startMs + 300L, cappedEndMs).coerceIn(startMs, safeDurationMs)

            aligned += LyricLine(
                timeMs = startMs,
                text = lines[i],
                sungUntilMs = endMs,
            )
            prevEndMs = startMs + 100L
        }

        return aligned.withInstrumentalGaps()
    }

    internal fun detectVocalSegments(
        vocalCurve: FloatArray,
        frameDurationSec: Double,
        totalDurationMs: Long,
    ): List<VocalSegment> {
        val safeTotalMs = totalDurationMs.coerceAtLeast(1_000L)
        if (vocalCurve.size < 4 || frameDurationSec <= 0.0) {
            return listOf(fallbackSegment(safeTotalMs))
        }

        val smoothWin = (0.5 / frameDurationSec).roundToInt().coerceIn(3, 45)
        val smoothed = movingAverage(vocalCurve, smoothWin)

        val sorted = smoothed.sortedArray()
        val p25 = sorted[((sorted.size - 1) * 0.25f).toInt()]
        val p85 = sorted[((sorted.size - 1) * 0.85f).toInt()]
        val highThresh = max(0.18f, p25 + 0.35f * (p85 - p25)).coerceAtMost(0.45f)
        val lowThresh = min(highThresh * 0.65f, 0.25f)

        val frameMs = (frameDurationSec * 1000.0)
        val raw = ArrayList<VocalSegment>()
        var inVocal = false
        var segStartFrame = 0

        for (i in smoothed.indices) {
            val v = smoothed[i]
            if (!inVocal) {
                if (v >= highThresh) {
                    inVocal = true
                    segStartFrame = i
                }
            } else if (v < lowThresh) {
                inVocal = false
                val startMs = (segStartFrame * frameMs).roundToLong().coerceIn(0L, safeTotalMs)
                val endMs = (i * frameMs).roundToLong().coerceIn(startMs, safeTotalMs)
                if (endMs - startMs >= MIN_SEGMENT_MS) {
                    raw += VocalSegment(startMs, endMs)
                }
            }
        }
        if (inVocal) {
            val startMs = (segStartFrame * frameMs).roundToLong().coerceIn(0L, safeTotalMs)
            val endMs = min(safeTotalMs, (smoothed.size * frameMs).roundToLong())
            if (endMs - startMs >= MIN_SEGMENT_MS) {
                raw += VocalSegment(startMs, endMs)
            }
        }

        if (raw.isEmpty()) {
            return listOf(fallbackSegment(safeTotalMs))
        }

        // Merge short breaths/pauses (< 1.8s) while preserving long instrumental breaks/intros/solos.
        val merged = ArrayList<VocalSegment>(raw.size)
        var current = raw[0]
        for (idx in 1 until raw.size) {
            val next = raw[idx]
            if (next.startMs - current.endMs < MERGE_GAP_MS) {
                current = VocalSegment(current.startMs, max(current.endMs, next.endMs))
            } else {
                merged += current
                current = next
            }
        }
        merged += current

        val totalActive = merged.sumOf { it.durationMs }
        return if (totalActive < safeTotalMs / 20L) {
            listOf(fallbackSegment(safeTotalMs))
        } else {
            merged
        }
    }

    private fun fallbackSegment(totalDurationMs: Long): VocalSegment {
        val start = (totalDurationMs * 0.03).roundToLong()
        val end = max(start + 500L, (totalDurationMs * 0.97).roundToLong())
        return VocalSegment(start, min(totalDurationMs, end))
    }

    private fun movingAverage(input: FloatArray, windowSize: Int): FloatArray {
        val out = FloatArray(input.size)
        val half = windowSize / 2
        var runningSum = 0.0
        var count = 0
        var left = 0
        var right = -1
        for (i in input.indices) {
            val targetRight = min(input.lastIndex, i + half)
            val targetLeft = max(0, i - half)
            while (right < targetRight) {
                right++
                runningSum += input[right].coerceIn(0f, 1f)
                count++
            }
            while (left < targetLeft) {
                runningSum -= input[left].coerceIn(0f, 1f)
                count--
                left++
            }
            out[i] = if (count > 0) (runningSum / count).toFloat() else 0f
        }
        return out
    }

    private fun findSegmentIndex(segments: List<VocalSegment>, virtualMs: Long): Int {
        var acc = 0L
        for (i in segments.indices) {
            val dur = segments[i].durationMs
            if (virtualMs <= acc + dur || i == segments.lastIndex) return i
            acc += dur
        }
        return segments.lastIndex
    }

    private fun virtualToRealMs(segments: List<VocalSegment>, segIndex: Int, virtualMs: Long): Long {
        var acc = 0L
        for (i in 0 until segIndex) {
            acc += segments[i].durationMs
        }
        val offset = (virtualMs - acc).coerceIn(0L, segments[segIndex].durationMs)
        return segments[segIndex].startMs + offset
    }

    private fun parseCleanLines(plainText: String): List<String> {
        val out = ArrayList<String>()
        plainText.lineSequence().forEach { raw ->
            val cleaned = LyricsG2pMapper.sanitizeLine(raw) ?: return@forEach
            out += cleaned
        }
        return out
    }

    private fun estimateSyllableWeight(line: String): Double {
        // Prefer reading inside inline furigana if present so weights match sung morae.
        val expanded = buildString(line.length) {
            val annotations = LyricsFurigana.annotations(line)
            var cursor = 0
            for (ann in annotations) {
                if (ann.sourceStart > cursor) append(line, cursor, ann.sourceStart)
                append(ann.reading)
                cursor = ann.sourceEndExclusive
            }
            if (cursor < line.length) append(line, cursor, line.length)
        }

        var morae = 0.0
        var inLatinWord = false
        for (ch in expanded) {
            when {
                ch in '\u3041'..'\u309F' || ch in '\u30A1'..'\u30FF' -> {
                    inLatinWord = false
                    // Small combining kana contribute half weight compared to full morae.
                    morae += if (ch in "ぁぃぅぇぉゃゅょァィゥェォャュョ") 0.35 else 1.0
                }
                Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.HAN -> {
                    inLatinWord = false
                    morae += 2.0
                }
                ch in '\uAC00'..'\uD7A3' -> {
                    inLatinWord = false
                    morae += 1.0
                }
                ch.isLetter() -> {
                    if (!inLatinWord) {
                        inLatinWord = true
                    }
                    morae += 0.45
                }
                else -> {
                    inLatinWord = false
                }
            }
        }
        return morae.coerceAtLeast(1.0)
    }
}
