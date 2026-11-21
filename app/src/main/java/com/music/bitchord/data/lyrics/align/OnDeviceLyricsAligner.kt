package com.music.bitchord.data.lyrics.align

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import android.util.Log
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.toEnhancedLrc
import com.music.bitchord.data.lyrics.toLrc
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.playback.AudioCache
import com.music.bitchord.playback.smart.AudioDecoder
import com.music.bitchord.playback.smart.LocalAudioSource
import com.music.bitchord.playback.smart.VocalDetector
import com.music.bitchord.playback.smart.VocalSpectrogram
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Facade for 100% on-device lyrics timestamp synchronization from audio + plain text:
 * - Tier 2 ([AlignMode.CTC_WORD_AI]): bundled Open-Unmix vocal isolation -> downloaded MMS-FA
 *   (`mms_fa_uint8.onnx`) acoustic CTC posteriors -> Kuromoji/multilingual G2P -> Viterbi forced
 *   alignment yielding word/character-level synced [LyricLine] + Enhanced LRC.
 * - Tier 1 ([AlignMode.VOCAL_VAD_LINE]): bundled Open-Unmix vocal presence curve -> hysteresis
 *   vocal-activity segmentation -> weighted line-level [LyricLine] + standard LRC (0 MB download).
 */
object OnDeviceLyricsAligner {

    private const val TAG = "BitChordLyricsAligner"
    private const val SAMPLE_RATE_16K = 16_000
    private const val CTC_WINDOW_SAMPLES = 20 * SAMPLE_RATE_16K
    private const val CTC_OVERLAP_SAMPLES = 2 * SAMPLE_RATE_16K
    private const val CTC_STEP_SAMPLES = CTC_WINDOW_SAMPLES - CTC_OVERLAP_SAMPLES
    private const val MAX_TRACK_SECONDS = 720.0

    enum class AlignMode {
        CTC_WORD_AI,
        VOCAL_VAD_LINE,
    }

    enum class AlignStage {
        DECODING_VOCALS,
        CTC_INFERENCE,
        ALIGNING,
    }

    data class AlignProgress(
        val fraction: Float,
        val stage: AlignStage,
    )

    data class AlignResult(
        val lines: List<LyricLine>,
        val mode: AlignMode,
        val enhancedLrc: String,
        val lineLrc: String,
    )

    /** True if [song] has a local file/content URI or a cached stream playable offline. */
    fun canAlignAudio(context: Context, song: Song): Boolean {
        val uri = song.localUri?.takeIf(String::isNotBlank)?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (uri != null && LocalAudioSource.isLocal(uri)) return true
        val path = song.localPath?.takeIf(String::isNotBlank)
        if (path != null && File(path).isFile) return true
        if (song.videoId.isNotBlank()) {
            return runCatching {
                AudioCache.mediaDataSource(Uri.parse("bitchord://watch?v=${song.videoId}"))?.use { true } ?: false
            }.getOrDefault(false)
        }
        return false
    }

    suspend fun align(
        context: Context,
        song: Song,
        plainText: String,
        abort: () -> Boolean = { false },
        onProgress: (AlignProgress) -> Unit = {},
    ): AlignResult? = withContext(Dispatchers.Default) {
        if (plainText.isBlank()) return@withContext null
        val appContext = context.applicationContext
        val dataSource = openAudioSource(appContext, song) ?: return@withContext null

        val useCtc = OnDeviceLyricsAlignerModel.isModelInstalled(appContext)
        val vocalCurveList = ArrayList<FloatArray>(32)
        val vocal16kList = ArrayList<FloatArray>(32)
        var totalCurveFrames = 0
        var total16kSamples = 0

        val startSec = (song.localStartMs ?: 0L).coerceAtLeast(0L) / 1000.0
        val cueMaxDur = song.localEndMs
            ?.takeIf { it > (song.localStartMs ?: 0L) }
            ?.let { (it - (song.localStartMs ?: 0L)) / 1000.0 }
            ?.coerceIn(1.0, MAX_TRACK_SECONDS)
            ?: MAX_TRACK_SECONDS

        val expectedDurSec = song.durationMillis().takeIf { it > 0L }?.div(1000.0)
        onProgress(AlignProgress(0.02f, AlignStage.DECODING_VOCALS))

        val decodedSec = dataSource.use { src ->
            AudioDecoder.decodeStereoChunked(
                source = src,
                startSeconds = startSec,
                maxDurationSeconds = cueMaxDur,
                chunkSeconds = 10.0,
                abort = abort,
            ) { chunk, chunkStartSec, totalDurSec ->
                if (abort()) return@decodeStereoChunked
                val separated = VocalDetector.separateChunk(
                    context = appContext,
                    left = chunk.left,
                    right = chunk.right,
                    sampleRate = chunk.sampleRate,
                    reconstructAudio = useCtc,
                )
                if (separated != null) {
                    if (separated.vocalCurve.isNotEmpty()) {
                        vocalCurveList += separated.vocalCurve
                        totalCurveFrames += separated.vocalCurve.size
                    }
                    if (useCtc && separated.vocalMono16k.isNotEmpty()) {
                        vocal16kList += separated.vocalMono16k
                        total16kSamples += separated.vocalMono16k.size
                    }
                }
                val denom = totalDurSec ?: expectedDurSec ?: 240.0
                val chunkEndRel = (chunkStartSec - startSec) + (chunk.left.size.toDouble() / chunk.sampleRate)
                val decodeFrac = (chunkEndRel / denom.coerceAtLeast(1.0)).toFloat().coerceIn(0.05f, 1f)
                val maxStageFrac = if (useCtc) 0.58f else 0.85f
                onProgress(AlignProgress(decodeFrac * maxStageFrac, AlignStage.DECODING_VOCALS))
            }
        } ?: return@withContext null

        currentCoroutineContext().ensureActive()
        if (abort()) return@withContext null

        val totalDurationMs = (decodedSec * 1000.0).roundToLong()
            .coerceAtLeast(song.durationMillis())
            .coerceAtLeast(1_000L)

        // Tier 2: On-device MMS-FA CTC word/character forced alignment
        if (useCtc && total16kSamples >= SAMPLE_RATE_16K / 2) {
            val vocal16k = stitchArrays(vocal16kList, total16kSamples)
            vocal16kList.clear()
            val ctcResult = runCatching {
                runCtcAlignment(
                    context = appContext,
                    vocal16k = vocal16k,
                    plainText = plainText,
                    totalDurationMs = totalDurationMs,
                    abort = abort,
                    onProgress = onProgress,
                )
            }.onFailure { err ->
                if (err is CancellationException) throw err
                Log.w(TAG, "CTC alignment failed, falling back to vocal VAD line sync", err)
            }.getOrNull()

            if (ctcResult != null) {
                return@withContext ctcResult
            }
        }

        if (abort()) return@withContext null

        // Tier 1: Zero-extra-download vocal VAD line synchronization
        onProgress(AlignProgress(0.90f, AlignStage.ALIGNING))
        val fullCurve = stitchArrays(vocalCurveList, totalCurveFrames)
        vocalCurveList.clear()
        val vadLines = VocalVadLineAligner.align(
            plainText = plainText,
            vocalCurve = fullCurve,
            frameDurationSec = VocalSpectrogram.HOP.toDouble() / VocalSpectrogram.SAMPLE_RATE,
            totalDurationMs = totalDurationMs,
        )
        if (vadLines.isEmpty()) return@withContext null
        onProgress(AlignProgress(1f, AlignStage.ALIGNING))

        val lineLrc = vadLines.toLrc()
        if (lineLrc.isBlank()) return@withContext null
        AlignResult(
            lines = vadLines,
            mode = AlignMode.VOCAL_VAD_LINE,
            enhancedLrc = lineLrc,
            lineLrc = lineLrc,
        )
    }

    private fun runCtcAlignment(
        context: Context,
        vocal16k: FloatArray,
        plainText: String,
        totalDurationMs: Long,
        abort: () -> Boolean,
        onProgress: (AlignProgress) -> Unit,
    ): AlignResult? {
        val ortSession = OnDeviceLyricsAlignerModel.openSession(context) ?: return null
        var detectedVocabSize = 0
        val emissionChunks = ArrayList<FloatArray>(16)
        var keptFramesTotal = 0

        try {
            val totalSamples = vocal16k.size
            val totalWindows = max(1, (max(0, totalSamples - CTC_OVERLAP_SAMPLES) + CTC_STEP_SAMPLES - 1) / CTC_STEP_SAMPLES)
            var winStart = 0
            var winIdx = 0

            while (winStart < totalSamples) {
                if (abort()) return null
                val winEnd = min(totalSamples, winStart + CTC_WINDOW_SAMPLES)
                val winLen = winEnd - winStart
                if (winLen < 1600) break
                val isFirst = winStart == 0
                val isLast = winEnd >= totalSamples

                val normInput = normalizeZeroMeanUnitVar(vocal16k, winStart, winLen)
                val windowOut = runSingleCtcWindow(ortSession, normInput) ?: return null
                val (logits, numFrames, vocabSize) = windowOut
                if (numFrames <= 0 || vocabSize <= 0) return null
                detectedVocabSize = vocabSize

                CtcForcedAligner.logSoftmaxInPlace(logits, numFrames, vocabSize)

                val winDurSec = winLen.toDouble() / SAMPLE_RATE_16K
                val winFrameSec = winDurSec / numFrames.toDouble()
                val keepStartSec = if (isFirst) 0.0 else (CTC_OVERLAP_SAMPLES * 0.5) / SAMPLE_RATE_16K
                val keepEndSec = if (isLast) winDurSec + 1.0 else winDurSec - ((CTC_OVERLAP_SAMPLES * 0.5) / SAMPLE_RATE_16K)

                var startFrame = -1
                var endFrame = 0
                for (f in 0 until numFrames) {
                    val midSec = (f + 0.5) * winFrameSec
                    if (midSec >= keepStartSec && midSec < keepEndSec) {
                        if (startFrame < 0) startFrame = f
                        endFrame = f + 1
                    }
                }
                if (startFrame >= 0 && endFrame > startFrame) {
                    val sliceLen = (endFrame - startFrame) * vocabSize
                    val kept = FloatArray(sliceLen)
                    System.arraycopy(logits, startFrame * vocabSize, kept, 0, sliceLen)
                    emissionChunks += kept
                    keptFramesTotal += (endFrame - startFrame)
                }

                winIdx++
                val ctcFrac = 0.58f + 0.32f * (winIdx.toFloat() / totalWindows).coerceIn(0f, 1f)
                onProgress(AlignProgress(ctcFrac, AlignStage.CTC_INFERENCE))

                if (isLast) break
                winStart += CTC_STEP_SAMPLES
            }
        } finally {
            OnDeviceLyricsAlignerModel.releaseSession()
        }

        if (abort() || keptFramesTotal <= 0 || detectedVocabSize <= 0) return null

        onProgress(AlignProgress(0.93f, AlignStage.ALIGNING))
        val fullEmissions = stitchArrays(emissionChunks, keptFramesTotal * detectedVocabSize)
        emissionChunks.clear()

        val mapped = LyricsG2pMapper.map(plainText, vocabSize = detectedVocabSize)
        if (mapped.lines.isEmpty() || mapped.tokenIds.isEmpty()) return null

        val spans = CtcForcedAligner.align(
            emissions = fullEmissions,
            frames = keptFramesTotal,
            vocabSize = detectedVocabSize,
            tokenIds = mapped.tokenIds,
            blankId = 0,
        ) ?: return null

        val frameDurationMs = totalDurationMs.toDouble() / keptFramesTotal.toDouble()
        val lines = CtcForcedAligner.buildSyncedLines(
            mapped = mapped,
            spans = spans,
            frameDurationMs = frameDurationMs,
            totalDurationMs = totalDurationMs,
        )
        if (lines.isEmpty()) return null

        onProgress(AlignProgress(1f, AlignStage.ALIGNING))
        val lineLrc = lines.toLrc()
        val enhancedLrc = lines.toEnhancedLrc().ifBlank { lineLrc }
        return AlignResult(
            lines = lines,
            mode = AlignMode.CTC_WORD_AI,
            enhancedLrc = enhancedLrc,
            lineLrc = lineLrc,
        )
    }

    private data class WindowOutput(
        val logits: FloatArray,
        val frames: Int,
        val vocabSize: Int,
    )

    private fun runSingleCtcWindow(session: OrtSession, normInput: FloatArray): WindowOutput? {
        val env = OrtEnvironment.getEnvironment()
        val len = normInput.size.toLong()
        val inputNames = session.inputNames
        val audioInputName = inputNames.firstOrNull { !it.equals("attention_mask", ignoreCase = true) }
            ?: inputNames.firstOrNull()
            ?: return null
        val maskName = inputNames.firstOrNull { it.equals("attention_mask", ignoreCase = true) }

        val audioTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(normInput), longArrayOf(1L, len))
        val maskTensor = maskName?.let {
            OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(normInput.size) { 1L }), longArrayOf(1L, len))
        }

        return try {
            val inputs = buildMap<String, OnnxTensor> {
                put(audioInputName, audioTensor)
                if (maskName != null && maskTensor != null) {
                    put(maskName, maskTensor)
                }
            }
            session.run(inputs).use { outputs ->
                val outTensor = outputs.get(0) as? OnnxTensor ?: return null
                val shape = outTensor.info.shape
                val frames: Int
                val vocab: Int
                when (shape.size) {
                    3 -> {
                        frames = shape[1].toInt()
                        vocab = shape[2].toInt()
                    }
                    2 -> {
                        frames = shape[0].toInt()
                        vocab = shape[1].toInt()
                    }
                    else -> return null
                }
                if (frames <= 0 || vocab <= 0) return null
                val buf = outTensor.floatBuffer
                val needed = frames * vocab
                val arr = FloatArray(needed)
                buf.rewind()
                buf.get(arr, 0, min(needed, buf.remaining()))
                WindowOutput(logits = arr, frames = frames, vocabSize = vocab)
            }
        } finally {
            runCatching { audioTensor.close() }
            runCatching { maskTensor?.close() }
        }
    }

    private fun normalizeZeroMeanUnitVar(samples: FloatArray, offset: Int, length: Int): FloatArray {
        val out = FloatArray(length)
        var sum = 0.0
        for (i in 0 until length) {
            sum += samples[offset + i].toDouble()
        }
        val mean = sum / length.coerceAtLeast(1)
        var sq = 0.0
        for (i in 0 until length) {
            val d = samples[offset + i].toDouble() - mean
            sq += d * d
        }
        val variance = sq / length.coerceAtLeast(1)
        val std = sqrt(variance + 1e-7)
        if (std < 1e-6) return out
        for (i in 0 until length) {
            out[i] = ((samples[offset + i].toDouble() - mean) / std).toFloat()
        }
        return out
    }

    private fun stitchArrays(chunks: List<FloatArray>, totalLength: Int): FloatArray {
        if (totalLength <= 0 || chunks.isEmpty()) return FloatArray(0)
        val out = FloatArray(totalLength)
        var offset = 0
        for (chunk in chunks) {
            val copy = min(chunk.size, totalLength - offset)
            if (copy <= 0) break
            System.arraycopy(chunk, 0, out, offset, copy)
            offset += copy
        }
        return out
    }

    private fun openAudioSource(context: Context, song: Song): MediaDataSource? {
        val localUri = song.localUri?.takeIf(String::isNotBlank)?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: song.localPath?.takeIf(String::isNotBlank)?.let { File(it).takeIf(File::isFile)?.let(Uri::fromFile) }
        return when {
            localUri != null && LocalAudioSource.isLocal(localUri) -> LocalAudioSource.open(context.contentResolver, localUri)
            song.videoId.isNotBlank() -> runCatching {
                AudioCache.mediaDataSource(Uri.parse("bitchord://watch?v=${song.videoId}"))
            }.getOrNull()
            else -> null
        }
    }
}
