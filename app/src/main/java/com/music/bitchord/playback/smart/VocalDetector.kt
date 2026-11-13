package com.music.bitchord.playback.smart

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.net.Uri
import android.util.Log
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.playback.AudioCache
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * Classifies whether a local or cached audio track has singing vocals using the
 * bundled MIT-licensed `vocals_umxhq_int8.onnx` open-unmix model, catching pure
 * instrumental tracks (e.g. piano/classical/BGM/Lo-Fi) that lack title/genre tags.
 */
object VocalDetector {

    private const val TAG = "BitChordVocalDetector"
    private const val PREFS_CACHE = "local_vocal_detector_cache"
    private const val CACHE_VERSION = "umxhq-v1"
    const val MODEL_ASSET = "vocals_umxhq_int8.onnx"
    const val FIXED_FRAMES = 960
    private const val LOW_HZ = 200.0
    private const val HIGH_HZ = 4000.0
    private const val DEFAULT_WINDOW_SECONDS = 6.0
    const val INSTRUMENTAL_MAX_VOCAL_PROB = 0.16f

    data class VocalResult(
        val vocalProbability: Float,
        val isInstrumental: Boolean,
    )

    @Volatile private var session: OrtSession? = null
    private val lock = Any()

    /** Returns cached classification if available, or null when not yet analyzed. */
    fun cached(context: Context, song: Song): VocalResult? {
        val prefs = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
        val prob = prefs.getFloat("${CACHE_VERSION}_${cacheKey(song)}_vp", Float.NaN)
        if (!prob.isFinite()) return null
        return VocalResult(
            vocalProbability = prob.coerceIn(0f, 1f),
            isInstrumental = prob < INSTRUMENTAL_MAX_VOCAL_PROB,
        )
    }

    /** True if this song does not yet have a cached vocal classification. */
    fun needsAnalysis(context: Context, song: Song): Boolean = cached(context, song) == null

    /** Persists a vocal classification result for [song]. */
    fun saveResult(context: Context, song: Song, vocalProbability: Float): VocalResult {
        val clamped = vocalProbability.coerceIn(0f, 1f)
        context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
            .edit()
            .putFloat("${CACHE_VERSION}_${cacheKey(song)}_vp", clamped)
            .apply()
        return VocalResult(vocalProbability = clamped, isInstrumental = clamped < INSTRUMENTAL_MAX_VOCAL_PROB)
    }

    /**
     * Classifies an already-decoded stereo PCM window (reused from Music2EMO's middle-window
     * decode so no second MediaCodec pass is ever needed).
     */
    fun classifyStereoWindow(
        context: Context,
        song: Song,
        left: FloatArray,
        right: FloatArray,
        sampleRate: Double,
    ): VocalResult? {
        cached(context, song)?.let { return it }
        val resLeft = AudioDecoder.resampleLinear(left, sampleRate, VocalSpectrogram.SAMPLE_RATE) ?: return null
        val resRight = AudioDecoder.resampleLinear(right, sampleRate, VocalSpectrogram.SAMPLE_RATE) ?: return null
        val spectrogram = VocalSpectrogram.compute(resLeft, resRight, VocalSpectrogram.SAMPLE_RATE) ?: return null
        val curve = runModel(context, spectrogram) ?: return null
        if (curve.isEmpty()) return null
        val prob = summarizeVocalCurve(curve)
        return saveResult(context, song, prob)
    }

    /**
     * Standalone best-effort vocal classification on a local or fully-cached song when Music2EMO
     * analysis has not already populated the cache.
     */
    fun classifyStandalone(
        context: Context,
        song: Song,
        abort: () -> Boolean = { false },
    ): VocalResult? {
        cached(context, song)?.let { return it }
        val localUri = song.localUri?.takeIf(String::isNotBlank)?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: song.localPath?.takeIf(String::isNotBlank)?.let { Uri.fromFile(File(it)) }
        val source = when {
            localUri != null && LocalAudioSource.isLocal(localUri) -> LocalAudioSource.open(context.contentResolver, localUri)
            song.videoId.isNotBlank() -> runCatching { AudioCache.mediaDataSource(Uri.parse("bitchord://watch?v=${song.videoId}")) }.getOrNull()
            else -> null
        } ?: return null

        return source.use { dataSource ->
            val start = (song.localStartMs ?: 0L).coerceAtLeast(0L) / 1000.0
            val containerDuration = AudioDecoder.containerDurationSeconds(dataSource)
            val metaDuration = song.durationMillis().takeIf { it > 0L }?.div(1000.0)
            val end = listOfNotNull(containerDuration, metaDuration?.let { start + it })
                .minOrNull() ?: (start + DEFAULT_WINDOW_SECONDS)
            val duration = (end - start).takeIf { it.isFinite() && it > 0.0 } ?: return null
            val winLen = min(DEFAULT_WINDOW_SECONDS, duration)
            val regionStart = start + ((duration - winLen) * 0.5).coerceAtLeast(0.0)
            val regionEnd = min(regionStart + winLen, end)
            val decoded = AudioDecoder.decodeRegionStereo(dataSource, regionStart, regionEnd, abort)
                ?: return null
            classifyStereoWindow(context, song, decoded.first.left, decoded.first.right, decoded.first.sampleRate)
        }
    }

    /**
     * Summarizes frame-level vocal mask ratios into [0f, 1f]: using the upper half's mean so
     * brief instrumental breaks between vocal phrases do not drag a singing track down, while
     * pure instrumental tracks stay uniformly near zero.
     */
    internal fun summarizeVocalCurve(curve: FloatArray): Float {
        if (curve.isEmpty()) return 0f
        val sorted = curve.sortedArray()
        val start = sorted.size / 2
        var sum = 0.0
        var count = 0
        for (i in start until sorted.size) {
            sum += sorted[i].coerceIn(0f, 1f)
            count++
        }
        return if (count > 0) (sum / count).toFloat().coerceIn(0f, 1f) else 0f
    }

    internal fun reduceToBandCurve(
        mix: FloatBuffer,
        target: FloatBuffer,
        bins: Int,
        usableFrames: Int,
    ): FloatArray {
        val lowBin = floor(LOW_HZ * VocalSpectrogram.FFT_SIZE / VocalSpectrogram.SAMPLE_RATE)
            .toInt().coerceAtLeast(0)
        val highBin = ceil(HIGH_HZ * VocalSpectrogram.FFT_SIZE / VocalSpectrogram.SAMPLE_RATE)
            .toInt().coerceAtMost(bins - 1)
        if (highBin <= lowBin || usableFrames <= 0) return FloatArray(0)

        val curve = FloatArray(usableFrames)
        for (frame in 0 until usableFrames) {
            var sum = 0.0
            var count = 0
            for (channel in 0 until VocalSpectrogram.CHANNELS) {
                for (bin in lowBin..highBin) {
                    val index = (channel * bins + bin) * FIXED_FRAMES + frame
                    val mixValue = mix.get(index)
                    if (mixValue <= 1e-6f) continue
                    val ratio = target.get(index) / mixValue
                    sum += ratio.coerceIn(0f, 1f)
                    count += 1
                }
            }
            curve[frame] = if (count > 0) (sum / count).toFloat() else 0f
        }
        return curve
    }

    private fun runModel(context: Context, spectrogram: VocalSpectrogram.Spectrogram): FloatArray? {
        if (spectrogram.frames <= 0 || spectrogram.frames > FIXED_FRAMES) return null
        val activeSession = ensureSession(context) ?: return null
        return runCatching {
            val bins = spectrogram.bins
            val backing = ByteBuffer
                .allocateDirect(VocalSpectrogram.CHANNELS * bins * FIXED_FRAMES * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
            fillFixedFrames(backing.asFloatBuffer(), spectrogram.values, bins, spectrogram.frames)
            val env = OrtEnvironment.getEnvironment()
            val shape = longArrayOf(1, VocalSpectrogram.CHANNELS.toLong(), bins.toLong(), FIXED_FRAMES.toLong())
            OnnxTensor.createTensor(env, backing.asFloatBuffer(), shape).use { tensor ->
                activeSession.run(mapOf(activeSession.inputNames.first() to tensor)).use { outputs ->
                    val target = (outputs.get(0) as OnnxTensor).floatBuffer
                    reduceToBandCurve(backing.asFloatBuffer(), target, bins, spectrogram.frames)
                }
            }
        }.onFailure { Log.w(TAG, "Vocal inference failed", it) }.getOrNull()
    }

    private fun fillFixedFrames(into: FloatBuffer, values: FloatArray, bins: Int, frames: Int) {
        if (frames == FIXED_FRAMES) {
            into.put(values)
            return
        }
        val pad = FloatArray(FIXED_FRAMES - frames)
        for (channel in 0 until VocalSpectrogram.CHANNELS) {
            for (bin in 0 until bins) {
                into.put(values, (channel * bins + bin) * frames, frames)
                into.put(pad)
            }
        }
    }

    private fun ensureSession(context: Context): OrtSession? {
        session?.let { return it }
        synchronized(lock) {
            session?.let { return it }
            val unpacked = unpackModel(context) ?: return null
            return runCatching {
                val options = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(1)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    setCPUArenaAllocator(false)
                    setMemoryPatternOptimization(false)
                }
                try {
                    OrtEnvironment.getEnvironment().createSession(unpacked.absolutePath, options).also {
                        session = it
                    }
                } finally {
                    options.close()
                }
            }.onFailure { Log.w(TAG, "Could not open vocal detection ONNX session", it) }.getOrNull()
        }
    }

    private fun unpackModel(context: Context): File? {
        val dir = File(context.noBackupFilesDir, "smart").apply { mkdirs() }
        val target = File(dir, MODEL_ASSET)
        if (target.isFile && target.length() > 0L) return target
        val temp = File(dir, "$MODEL_ASSET.tmp")
        return runCatching {
            context.assets.open(MODEL_ASSET).use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            }
            temp.renameTo(target)
            target
        }.onFailure {
            temp.delete()
            Log.w(TAG, "Failed to unpack $MODEL_ASSET from assets", it)
        }.getOrNull()
    }

    fun release() {
        synchronized(lock) {
            runCatching { session?.close() }
            session = null
        }
    }

    internal fun cacheKey(song: Song): String {
        val identity = song.localUri?.takeIf(String::isNotBlank)
            ?: song.localPath?.takeIf(String::isNotBlank)
            ?: song.videoId
        val raw = identity + "|" + song.durationText + "|" + song.localStartMs + "|" + song.localEndMs
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)
    }
}
