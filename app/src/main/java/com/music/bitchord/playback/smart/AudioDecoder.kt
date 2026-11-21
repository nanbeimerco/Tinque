/*
 * Modeled on Orchard's own AudioDecoder (https://github.com/SFG5453/Orchard),
 * scoped down to the platform MediaCodec path.
 *
 * Copyright (C) 2026 Kushagra Singh
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.music.bitchord.playback.smart

import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decodes a region of a local or cached audio track to mono or planar stereo PCM
 * via platform [MediaCodec], for on-device mood and vocal analysis.
 */
object AudioDecoder {

    private const val TAG = "BitChordAudioDecoder"
    private const val TIMEOUT_US = 10_000L

    /** Decoded mono PCM at the container's own sample rate; the caller resamples. */
    data class Pcm(val samples: FloatArray, val sampleRate: Double)

    /** Decoded planar stereo PCM at the container's own sample rate. */
    data class StereoPcm(val left: FloatArray, val right: FloatArray, val sampleRate: Double)

    /** Reads the audio duration a readable container advertises without decoding samples. */
    fun containerDurationSeconds(source: MediaDataSource): Double? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(source)
            readContainerDurationSeconds(extractor)
        } catch (error: Exception) {
            Log.w(TAG, "Could not read duration from cached media", error)
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    /** Decodes [startSeconds] to [endSeconds] of [source], downmixed to mono at native rate. */
    fun decodeRegion(
        source: MediaDataSource,
        startSeconds: Double,
        endSeconds: Double,
        abort: () -> Boolean = { false },
    ): Pair<Pcm, Double>? {
        var samplesOut: FloatAccumulator? = null
        var scratch = ShortArray(2)
        val decoded = decodeRaw(
            source = source,
            abort = abort,
            resolveWindow = { startSeconds to endSeconds },
            onPrepared = { windowSeconds, expectedRate ->
                val cap = ((min(windowSeconds, 30.0) + 0.5) * expectedRate).roundToInt().coerceAtLeast(4096)
                samplesOut = FloatAccumulator(cap)
            },
            onBuffer = { buffer, info, channels, maxFrames ->
                if (scratch.size < max(1, channels)) scratch = ShortArray(max(1, channels))
                val target = requireNotNull(samplesOut)
                toMonoInto(buffer, info, channels, target, scratch, maxFrames)
            },
        ) ?: return null
        val mono = samplesOut?.toArray() ?: return null
        if (mono.isEmpty()) return null
        return Pcm(mono, decoded.first) to decoded.second
    }

    /** Decodes [startSeconds] to [endSeconds] of [source] as planar stereo PCM. */
    fun decodeRegionStereo(
        source: MediaDataSource,
        startSeconds: Double,
        endSeconds: Double,
        abort: () -> Boolean = { false },
    ): Pair<StereoPcm, Double>? {
        var leftOut: FloatAccumulator? = null
        var rightOut: FloatAccumulator? = null
        var scratch = ShortArray(2)
        val decoded = decodeRaw(
            source = source,
            abort = abort,
            resolveWindow = { startSeconds to endSeconds },
            onPrepared = { windowSeconds, expectedRate ->
                val cap = ((min(windowSeconds, 30.0) + 0.5) * expectedRate).roundToInt().coerceAtLeast(4096)
                leftOut = FloatAccumulator(cap)
                rightOut = FloatAccumulator(cap)
            },
            onBuffer = { buffer, info, channels, maxFrames ->
                if (scratch.size < max(1, channels)) scratch = ShortArray(max(1, channels))
                toStereoInto(buffer, info, channels, requireNotNull(leftOut), requireNotNull(rightOut), scratch, maxFrames)
            },
        ) ?: return null
        val left = leftOut?.toArray() ?: return null
        val right = rightOut?.toArray() ?: return null
        if (left.isEmpty() || right.isEmpty()) return null
        return StereoPcm(left, right, decoded.first) to decoded.second
    }

    /**
     * Reads container duration and decodes the resolved window inside one [MediaExtractor]
     * session so [source] is never reused after `extractor.release()` closes it.
     */
    fun decodeStereoWindow(
        source: MediaDataSource,
        abort: () -> Boolean = { false },
        selectWindow: (containerDurationSeconds: Double?) -> Pair<Double, Double>?,
    ): Pair<StereoPcm, Double>? {
        var leftOut: FloatAccumulator? = null
        var rightOut: FloatAccumulator? = null
        var scratch = ShortArray(2)
        val decoded = decodeRaw(
            source = source,
            abort = abort,
            resolveWindow = { extractor ->
                selectWindow(readContainerDurationSeconds(extractor))
            },
            onPrepared = { windowSeconds, expectedRate ->
                val cap = ((min(windowSeconds, 30.0) + 0.5) * expectedRate).roundToInt().coerceAtLeast(4096)
                leftOut = FloatAccumulator(cap)
                rightOut = FloatAccumulator(cap)
            },
            onBuffer = { buffer, info, channels, maxFrames ->
                if (scratch.size < max(1, channels)) scratch = ShortArray(max(1, channels))
                toStereoInto(buffer, info, channels, requireNotNull(leftOut), requireNotNull(rightOut), scratch, maxFrames)
            },
        ) ?: return null
        val left = leftOut?.toArray() ?: return null
        val right = rightOut?.toArray() ?: return null
        if (left.isEmpty() || right.isEmpty()) return null
        return StereoPcm(left, right, decoded.first) to decoded.second
    }

    /**
     * Streams a track in a single [MediaExtractor]/[MediaCodec] pass, emitting planar stereo chunks
     * of roughly [chunkSeconds] seconds so full-song vocal separation never allocates huge decode buffers.
     * Returns total decoded audio duration in seconds, or null if decoding failed.
     */
    fun decodeStereoChunked(
        source: MediaDataSource,
        startSeconds: Double = 0.0,
        maxDurationSeconds: Double = 720.0,
        chunkSeconds: Double = 10.0,
        abort: () -> Boolean = { false },
        onChunk: (chunk: StereoPcm, chunkStartSec: Double, totalDurationSec: Double?) -> Unit,
    ): Double? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(source)
            val containerDuration = readContainerDurationSeconds(extractor)
            val safeStart = startSeconds.coerceAtLeast(0.0)
            val safeMaxDur = maxDurationSeconds.coerceAtLeast(1.0)
            val endSeconds = if (containerDuration != null) {
                min(containerDuration, safeStart + safeMaxDur)
            } else {
                safeStart + safeMaxDur
            }
            if (endSeconds <= safeStart) return null

            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            val startUs = (safeStart * 1_000_000.0).toLong()
            val endUs = (endSeconds * 1_000_000.0).toLong()
            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            codec = runCatching { MediaCodec.createDecoderByType(mime) }
                .onFailure { Log.w(TAG, "No decoder for $mime", it) }
                .getOrNull() ?: return null
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var outputChannels = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 1
            var outputRate = (format.intOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: 44_100).coerceAtLeast(8_000)

            val initChunkCap = ((chunkSeconds + 1.0) * outputRate).roundToInt().coerceAtLeast(8192)
            var leftOut = FloatAccumulator(initChunkCap)
            var rightOut = FloatAccumulator(initChunkCap)
            var scratch = ShortArray(max(2, outputChannels))

            var sawFirstSample = false
            var inputDone = false
            var outputDone = false
            var chunkCursorSec = safeStart
            var totalDecodedFrames = 0L

            fun flushFullChunks(forceFinal: Boolean) {
                val safeRate = max(8_000, outputRate)
                val targetChunkFrames = (chunkSeconds * safeRate).roundToInt().coerceAtLeast(safeRate)
                while (leftOut.count >= targetChunkFrames) {
                    val lAll = leftOut.toArray()
                    val rAll = rightOut.toArray()
                    val lChunk = lAll.copyOfRange(0, targetChunkFrames)
                    val rChunk = rAll.copyOfRange(0, targetChunkFrames)
                    onChunk(StereoPcm(lChunk, rChunk, safeRate.toDouble()), chunkCursorSec, containerDuration)
                    chunkCursorSec += targetChunkFrames.toDouble() / safeRate
                    leftOut = FloatAccumulator(initChunkCap)
                    rightOut = FloatAccumulator(initChunkCap)
                    if (lAll.size > targetChunkFrames) {
                        val rem = lAll.size - targetChunkFrames
                        leftOut.appendArray(lAll, targetChunkFrames, rem)
                        rightOut.appendArray(rAll, targetChunkFrames, rem)
                    }
                }
                if (forceFinal && leftOut.count >= safeRate / 4) {
                    val lChunk = leftOut.toArray()
                    val rChunk = rightOut.toArray()
                    onChunk(StereoPcm(lChunk, rChunk, safeRate.toDouble()), chunkCursorSec, containerDuration)
                    chunkCursorSec += lChunk.size.toDouble() / safeRate
                    leftOut = FloatAccumulator(256)
                    rightOut = FloatAccumulator(256)
                }
            }

            while (!outputDone) {
                if (abort()) return null
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            val sampleTimeUs = extractor.sampleTime
                            if (sampleSize < 0 || (sampleTimeUs in 0..Long.MAX_VALUE && sampleTimeUs > endUs)) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        outputRate = (newFormat.intOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: outputRate).coerceAtLeast(8_000)
                        outputChannels = newFormat.intOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: outputChannels
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                        if (bufferInfo.size > 0) {
                            val safeRate = max(8_000, outputRate)
                            val safeChannels = max(1, outputChannels)
                            val bufferFrames = bufferInfo.size / (2 * safeChannels)
                            val bufferDurationUs = if (bufferFrames > 0) (bufferFrames * 1_000_000L) / safeRate else 0L
                            val ptsUs = bufferInfo.presentationTimeUs
                            val isPreRoll = !sawFirstSample &&
                                startUs > 0L &&
                                ptsUs >= 0L &&
                                bufferDurationUs > 0L &&
                                ptsUs + bufferDurationUs <= startUs

                            if (!isPreRoll) {
                                if (!sawFirstSample) {
                                    if (ptsUs > startUs && startUs > 0L) {
                                        chunkCursorSec = ptsUs / 1_000_000.0
                                    }
                                    sawFirstSample = true
                                }
                                if (scratch.size < safeChannels) scratch = ShortArray(safeChannels)
                                val maxRemainingFrames = ((endSeconds - safeStart) * safeRate - totalDecodedFrames)
                                    .roundToInt()
                                    .coerceAtLeast(0)
                                if (maxRemainingFrames <= 0) {
                                    outputDone = true
                                } else {
                                    codec.getOutputBuffer(outputIndex)?.let { output ->
                                        val added = toStereoInto(
                                            output,
                                            bufferInfo,
                                            safeChannels,
                                            leftOut,
                                            rightOut,
                                            scratch,
                                            maxRemainingFrames,
                                        )
                                        totalDecodedFrames += added
                                    }
                                    flushFullChunks(forceFinal = false)
                                    if (ptsUs > endUs || totalDecodedFrames.toDouble() / safeRate >= safeMaxDur) {
                                        outputDone = true
                                    }
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            if (!sawFirstSample || totalDecodedFrames <= 0L) return null
            if (!abort()) {
                flushFullChunks(forceFinal = true)
            }
            return totalDecodedFrames.toDouble() / outputRate.toDouble()
        } catch (error: Exception) {
            Log.w(TAG, "Chunked stereo decode failed", error)
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /** Resamples [samples] linearly from [sourceRate] to [targetRate]. */
    fun resampleLinear(
        samples: FloatArray,
        sourceRate: Double,
        targetRate: Double,
    ): FloatArray? {
        if (samples.isEmpty() || !sourceRate.isFinite() || sourceRate <= 0.0 ||
            !targetRate.isFinite() || targetRate <= 0.0
        ) {
            return null
        }
        if (sourceRate.roundToInt() == targetRate.roundToInt()) return samples
        val size = (samples.size * targetRate / sourceRate).roundToInt().coerceAtLeast(1)
        return FloatArray(size) { index ->
            val position = index * sourceRate / targetRate
            val before = position.toInt().coerceIn(0, samples.lastIndex)
            val after = min(before + 1, samples.lastIndex)
            val fraction = (position - before).toFloat()
            samples[before] * (1f - fraction) + samples[after] * fraction
        }
    }

    private fun readContainerDurationSeconds(extractor: MediaExtractor): Double? =
        (0 until extractor.trackCount)
            .mapNotNull { index ->
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: return@mapNotNull null
                if (!mime.startsWith("audio/") || !format.containsKey(MediaFormat.KEY_DURATION)) {
                    return@mapNotNull null
                }
                format.getLong(MediaFormat.KEY_DURATION).takeIf { it > 0 }?.div(1_000_000.0)
            }
            .maxOrNull()
            ?.takeIf { it.isFinite() && it > 0 }

    private fun decodeRaw(
        source: MediaDataSource,
        abort: () -> Boolean,
        resolveWindow: (MediaExtractor) -> Pair<Double, Double>?,
        onPrepared: (windowSeconds: Double, expectedRate: Int) -> Unit,
        onBuffer: (ByteBuffer, MediaCodec.BufferInfo, Int, Int) -> Int,
    ): Pair<Double, Double>? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(source)
            val window = resolveWindow(extractor) ?: return null
            val startSeconds = window.first
            val endSeconds = window.second
            if (endSeconds <= startSeconds) return null

            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            val startUs = (startSeconds * 1_000_000).toLong()
            val endUs = (endSeconds * 1_000_000).toLong()
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            codec = runCatching { MediaCodec.createDecoderByType(mime) }
                .onFailure { Log.w(TAG, "No decoder for $mime", it) }
                .getOrNull() ?: return null
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var outputChannels = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 1
            var outputRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: 0
            val windowSeconds = (endSeconds - startSeconds).coerceAtLeast(0.0)
            onPrepared(windowSeconds, max(8_000, outputRate))

            var collectedFrames = 0
            var actualStartSeconds = -1.0
            var sawFirstSample = false
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (abort()) return null
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            val sampleTimeUs = extractor.sampleTime
                            if (sampleSize < 0 || (sampleTimeUs in 0..Long.MAX_VALUE && sampleTimeUs > endUs)) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        outputRate = newFormat.intOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: outputRate
                        outputChannels = newFormat.intOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: outputChannels
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                        if (bufferInfo.size > 0) {
                            val safeRate = max(1, outputRate)
                            val safeChannels = max(1, outputChannels)
                            val bufferFrames = bufferInfo.size / (2 * safeChannels)
                            val bufferDurationUs = if (outputRate > 0 && bufferFrames > 0) {
                                (bufferFrames * 1_000_000L) / safeRate
                            } else {
                                0L
                            }
                            val ptsUs = bufferInfo.presentationTimeUs
                            // Closest-sync seek on poorly indexed containers (e.g. FLAC) can jump far
                            // before startUs; discard pre-roll buffers before accumulating PCM.
                            val isPreRoll = !sawFirstSample &&
                                ptsUs >= 0L &&
                                bufferDurationUs > 0L &&
                                ptsUs + bufferDurationUs <= startUs

                            if (!isPreRoll) {
                                val maxFrames = ((windowSeconds + 1.5).coerceAtLeast(1.0) * safeRate)
                                    .roundToInt()
                                    .coerceAtMost(safeRate * 60)
                                val remainingFrames = maxFrames - collectedFrames
                                if (remainingFrames <= 0) {
                                    outputDone = true
                                } else {
                                    if (!sawFirstSample) {
                                        actualStartSeconds = ptsUs / 1_000_000.0
                                        sawFirstSample = true
                                    }
                                    codec.getOutputBuffer(outputIndex)?.let { output ->
                                        collectedFrames += onBuffer(output, bufferInfo, outputChannels, remainingFrames)
                                    }
                                    if (ptsUs > endUs || collectedFrames >= maxFrames) {
                                        outputDone = true
                                    }
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            if (!sawFirstSample || outputRate <= 0) return null
            return outputRate.toDouble() to actualStartSeconds
        } catch (error: Exception) {
            Log.w(TAG, "Region decode failed", error)
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun toMonoInto(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        channels: Int,
        out: FloatAccumulator,
        scratch: ShortArray,
        maxFrames: Int,
    ): Int {
        val safeChannels = max(1, channels)
        val shorts = buffer.duplicate().apply {
            order(ByteOrder.LITTLE_ENDIAN)
            position(info.offset)
            limit(info.offset + info.size)
        }.asShortBuffer()
        val frames = min(maxFrames, shorts.remaining() / safeChannels)
        if (frames <= 0) return 0
        out.ensureAdditional(frames)
        for (index in 0 until frames) {
            shorts.get(scratch, 0, safeChannels)
            var sum = 0
            for (c in 0 until safeChannels) sum += scratch[c]
            out.add((sum / safeChannels.toFloat()) / 32768f)
        }
        return frames
    }

    private fun toStereoInto(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        channels: Int,
        leftOut: FloatAccumulator,
        rightOut: FloatAccumulator,
        scratch: ShortArray,
        maxFrames: Int,
    ): Int {
        val safeChannels = max(1, channels)
        val shorts = buffer.duplicate().apply {
            order(ByteOrder.LITTLE_ENDIAN)
            position(info.offset)
            limit(info.offset + info.size)
        }.asShortBuffer()
        val frames = min(maxFrames, shorts.remaining() / safeChannels)
        if (frames <= 0) return 0
        leftOut.ensureAdditional(frames)
        rightOut.ensureAdditional(frames)
        for (index in 0 until frames) {
            shorts.get(scratch, 0, safeChannels)
            leftOut.add(scratch[0] / 32768f)
            rightOut.add((if (safeChannels > 1) scratch[1] else scratch[0]) / 32768f)
        }
        return frames
    }

    private class FloatAccumulator(initialCapacity: Int) {
        private var data = FloatArray(max(256, initialCapacity))
        private var size = 0

        val count: Int get() = size

        fun ensureAdditional(count: Int) {
            if (count <= 0) return
            val needed = size + count
            if (needed <= data.size) return
            var newCapacity = data.size
            while (newCapacity < needed) {
                newCapacity = max(needed, newCapacity + (newCapacity shr 1))
            }
            data = data.copyOf(newCapacity)
        }

        fun add(value: Float) {
            ensureAdditional(1)
            data[size++] = value
        }

        fun appendArray(src: FloatArray, offset: Int, length: Int) {
            if (length <= 0) return
            ensureAdditional(length)
            System.arraycopy(src, offset, data, size, length)
            size += length
        }

        fun toArray(): FloatArray = if (size == data.size) data else data.copyOf(size)
    }

    private fun MediaFormat.intOrNull(key: String): Int? = if (containsKey(key)) getInteger(key) else null
}
