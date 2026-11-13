package com.music.bitchord.feature.homeai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.music.bitchord.BuildConfig
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.AudioCache
import com.music.bitchord.playback.smart.AudioDecoder
import com.music.bitchord.playback.smart.LocalAudioSource
import com.music.bitchord.playback.smart.VocalDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import android.os.Process
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Optional on-device music emotion & embedding analyzer backed by MERT-v1-95M
 * and Music2Emo (`music2emo-mert-layers-5-6.onnx` + `music2emo-j-all-head.onnx`).
 *
 * Models are downloaded only after explicit opt-in (disabled in offline / `.nointernet`
 * builds). No audio ever leaves the device. Stores:
 * - Normalized `valence` (`0..1`), `arousal` (`0..1`), `uplifting` (`0..1`), `calm` (`0..1`)
 * - 1536-dim pooled MERT Layer 5/6 binary LRU embeddings under `noBackupFilesDir` (~6 KB/song)
 * - Middle-window stereo reuse for [VocalDetector] (`vocals_umxhq_int8.onnx`) to avoid double decode
 */
object LocalHomeAi {
    private const val PREFS = "local_home_ai"
    private const val PREFS_MOOD_CACHE = "local_home_ai_mood_cache"
    private const val MODEL_VERSION = "music2emo-mert95m-jall-v1"
    private const val MERT_EMBEDDING_DIR = "home_ai_mert_v1"
    private const val KEY_MODEL_DECISION = "${MODEL_VERSION}_model_download_decision"
    private const val KEY_FAILED_TRACKS = "${MODEL_VERSION}_failed_tracks_v2"
    private const val MAX_FAILED_TRACKS = 4096
    private const val MAX_MEMORY_EMBEDDINGS = 256
    private const val MAX_DISK_EMBEDDINGS = 1500
    private const val MAX_CONSECUTIVE_OOM = 2
    private const val OOM_RECOVERY_DELAY_MS = 2_500L
    private val LEGACY_MODEL_VERSIONS = listOf(
        "essentia-discogs-effnet-moods-v2",
        "essentia-yamnet-mood-v1",
        "essentia-deam-msd-musicnn-v1",
    )
    private const val MODEL_HOST = "raw.githubusercontent.com"
    private const val ANALYSIS_WINDOW_SECONDS = 4.2
    private const val SAMPLE_RATE = 24_000
    private const val MERT_FEATURE_SIZE = 768
    private const val MERT_LAYER_COUNT = 2
    const val MERT_EMBEDDING_DIM = MERT_FEATURE_SIZE * MERT_LAYER_COUNT
    private const val M2E_CHORD_SEQUENCE_LENGTH = 100
    private const val UNKNOWN_CHORD_ROOT = 0L
    private const val UNKNOWN_CHORD_ATTRIBUTE = 0L
    private const val MAJOR_MODE = 0f
    private const val DOWNLOAD_LIMIT_BYTES = 128_000_000L
    private const val ANALYSIS_BATCH_SIZE = 4
    private const val ANALYSIS_TRACK_TIMEOUT_MS = 30_000L
    private const val ANALYSIS_BATCH_PAUSE_MS = 500L
    private const val TAG = "LocalHomeAi"

    // Official Music2Emo tag_list.npy mood/theme slice indices (127..182).
    private val UPLIFTING_TAG_INDICES = intArrayOf(18, 26, 29, 30, 35, 38, 39, 54, 55)
    private val CALM_TAG_INDICES = intArrayOf(5, 16, 32, 37, 41, 46, 47)

    data class TrackMoodFeatures(
        val valence: Float,
        val arousal: Float,
        val uplifting: Float,
        val calm: Float,
    )

    data class DownloadProgress(val bytes: Long, val totalBytes: Long)
    data class AnalysisProgress(
        val completed: Int,
        val total: Int,
        val failed: Int,
        val finished: Boolean,
        val hasMore: Boolean = false,
        val lowMemory: Boolean = false,
    )

    private data class ModelFile(
        val name: String,
        val url: String,
        val bytes: Long,
        val sha256: String,
    )

    private val models = listOf(
        ModelFile(
            name = "music2emo-mert-layers-5-6.onnx",
            url = "https://raw.githubusercontent.com/Kdroidwin/MusicBeatSP/main/model-assets/music2emo-mert-layers-5-6.onnx",
            bytes = 86_839_269L,
            sha256 = "2f35ec389db172fb3d6279b0650f59d0171229b62bd23df7b3cc0fe000b09a36",
        ),
        ModelFile(
            name = "music2emo-j-all-head.onnx",
            url = "https://raw.githubusercontent.com/Kdroidwin/MusicBeatSP/main/model-assets/music2emo-j-all-head.onnx",
            bytes = 4_327_266L,
            sha256 = "256c20d2af88c7fb15796ab0bc4a9d2775c164bc262c654bde5ae247dad782dc",
        ),
    )

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _analysisProgress = MutableStateFlow<AnalysisProgress?>(null)
    val analysisProgress = _analysisProgress.asStateFlow()

    private val _cacheRevision = MutableStateFlow(0)
    val cacheRevision = _cacheRevision.asStateFlow()

    private val _modelInstalled = MutableStateFlow(false)
    val modelInstalled = _modelInstalled.asStateFlow()

    private val analysisScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val analysisLock = Any()
    private val queueState = AnalysisQueueState(::cacheKey)
    private var activeContext: Context? = null
    private var lastLibrarySignature: String? = null
    private var completedLibrarySignature: String? = null
    private var lowMemoryPausedSignature: String? = null
    private var lastLibraryQueue: List<Song> = emptyList()
    private var analysisJob: Job? = null
    private var analysisSessionGeneration = 0L

    private val featuresMemoryCache = ConcurrentHashMap<String, TrackMoodFeatures>()
    private val failedKeysLock = Any()
    private var failedKeysMemory: LinkedHashSet<String>? = null

    /**
     * Thread-safe monotonic queue state for background mood analysis.
     * Enqueueing updated library lists or prioritizing playback tracks reorders/appends
     * pending items without resetting [completed] back to zero.
     */
    internal class AnalysisQueueState(
        private val keyOf: (Song) -> String,
    ) {
        private val pending = LinkedHashMap<String, Song>()
        private val inFlightKey = mutableSetOf<String>()
        var completed: Int = 0
            private set
        var failed: Int = 0
            private set
        var total: Int = 0
            private set
        var lowMemory: Boolean = false

        val hasPending: Boolean
            get() = pending.isNotEmpty()

        fun clear() {
            pending.clear()
            inFlightKey.clear()
            completed = 0
            failed = 0
            total = 0
            lowMemory = false
        }

        /** Merges [candidates] into the active queue; returns false if nothing new was added. */
        fun enqueueLibrary(candidates: List<Song>, alreadyFailedCount: Int): Boolean {
            if (candidates.isEmpty()) return false
            if (pending.isEmpty() && inFlightKey.isEmpty()) {
                completed = 0
                failed = alreadyFailedCount
                total = 0
                lowMemory = false
            }
            var added = 0
            candidates.forEach { song ->
                val key = keyOf(song)
                if (key !in inFlightKey && !pending.containsKey(key)) {
                    pending[key] = song
                    added++
                }
            }
            total += added
            return added > 0
        }

        /** Moves [playbackCandidates] to the front of [pending] without resetting [completed]. */
        fun prioritizeFront(playbackCandidates: List<Song>, alreadyFailedCount: Int) {
            if (playbackCandidates.isEmpty()) return
            if (pending.isEmpty() && inFlightKey.isEmpty()) {
                enqueueLibrary(playbackCandidates, alreadyFailedCount)
                return
            }
            val reordered = LinkedHashMap<String, Song>()
            var added = 0
            playbackCandidates.forEach { song ->
                val key = keyOf(song)
                if (key !in inFlightKey && !reordered.containsKey(key)) {
                    if (!pending.containsKey(key)) added++
                    reordered[key] = song
                }
            }
            pending.forEach { (key, song) ->
                if (!reordered.containsKey(key)) reordered[key] = song
            }
            pending.clear()
            pending.putAll(reordered)
            total += added
            lowMemory = false
        }

        fun pollNext(): Pair<String, Song>? {
            val iterator = pending.entries.iterator()
            if (!iterator.hasNext()) return null
            val entry = iterator.next()
            iterator.remove()
            inFlightKey.add(entry.key)
            return entry.key to entry.value
        }

        fun markTrackDone(key: String, succeeded: Boolean, wasLowMemory: Boolean) {
            inFlightKey.remove(key)
            completed++
            if (!succeeded) failed++
            if (wasLowMemory) lowMemory = true
        }

        fun snapshot(finished: Boolean): AnalysisProgress = AnalysisProgress(
            completed = completed.coerceAtMost(total.coerceAtLeast(completed)),
            total = total.coerceAtLeast(completed),
            failed = failed,
            finished = finished,
            hasMore = pending.isNotEmpty(),
            lowMemory = lowMemory,
        )
    }

    private val classificationMutex = Mutex()
    private val modelOperationMutex = Mutex()
    @Volatile private var verificationFingerprint: String? = null
    @Volatile private var verificationResult: Boolean = false
    private val verificationLock = Any()

    private val embeddingCache = object : LinkedHashMap<String, FloatArray>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean =
            size > MAX_MEMORY_EMBEDDINGS
    }
    private val embeddingLock = Any()

    /** True when the APK flavor and OS permission permit downloading the optional model set. */
    fun isDownloadSupported(context: Context): Boolean {
        if (BuildConfig.APPLICATION_ID.endsWith(".nointernet")) return false
        return runCatching {
            context.checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    /** null = not asked yet, true = opted in, false = declined. */
    fun modelDecision(context: Context): Boolean? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_MODEL_DECISION)) prefs.getBoolean(KEY_MODEL_DECISION, false) else null
    }

    fun setModelDecision(context: Context, accepted: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MODEL_DECISION, accepted).apply()
    }

    fun isModelInstalled(context: Context): Boolean {
        val directory = modelDirectory(context)
        val fingerprint = models.joinToString("|") { model ->
            val file = File(directory, model.name)
            "${file.name}:${file.length()}:${file.lastModified()}:${file.isFile}"
        }
        if (fingerprint == verificationFingerprint) return verificationResult
        synchronized(verificationLock) {
            if (fingerprint == verificationFingerprint) return verificationResult
            val valid = models.all { model ->
                val file = File(directory, model.name)
                file.isFile && file.length() == model.bytes && sha256(file) == model.sha256
            }
            verificationFingerprint = fingerprint
            verificationResult = valid
            _modelInstalled.value = valid
            return valid
        }
    }

    suspend fun downloadModels(context: Context) = modelOperationMutex.withLock {
        check(isDownloadSupported(context)) { "Model downloads are disabled in offline builds" }
        check(!AppSettings.offlineMode.value) { "Model downloads are unavailable while Offline mode is enabled" }
        withContext(Dispatchers.IO) {
            val directory = modelDirectory(context).apply { mkdirs() }
            val total = models.sumOf(ModelFile::bytes)
            var completed = 0L
            try {
                models.forEach { model ->
                    val destination = File(directory, model.name)
                    if (destination.isFile && destination.length() == model.bytes && sha256(destination) == model.sha256) {
                        completed += model.bytes
                        _downloadProgress.value = DownloadProgress(completed, total)
                        return@forEach
                    }
                    destination.delete()
                    val temporary = File(directory, "${model.name}.part")
                    temporary.delete()
                    val connection = URL(model.url).openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 20_000
                        connection.readTimeout = 120_000
                        connection.instanceFollowRedirects = false
                        connection.setRequestProperty("User-Agent", "Tinque-LocalMood/1")
                        connection.connect()
                        check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                            "Model download returned HTTP ${connection.responseCode}"
                        }
                        check(connection.url.host.equals(MODEL_HOST, ignoreCase = true)) {
                            "Model URL is not hosted by the pinned model-assets host"
                        }
                        val contentLength = connection.contentLengthLong
                        check(contentLength < 0L || contentLength == model.bytes) { "Unexpected model size" }
                        val digest = MessageDigest.getInstance("SHA-256")
                        var modelBytes = 0L
                        var lastProgress = 0L
                        BufferedInputStream(connection.inputStream).use { input ->
                            FileOutputStream(temporary).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    modelBytes += count
                                    check(modelBytes <= model.bytes && modelBytes <= DOWNLOAD_LIMIT_BYTES) {
                                        "Model exceeded its verified size limit"
                                    }
                                    digest.update(buffer, 0, count)
                                    output.write(buffer, 0, count)
                                    if (modelBytes - lastProgress >= 256 * 1024) {
                                        lastProgress = modelBytes
                                        _downloadProgress.value = DownloadProgress(completed + modelBytes, total)
                                    }
                                }
                            }
                        }
                        val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                        check(modelBytes == model.bytes && actualHash == model.sha256) {
                            "Model verification failed; the incomplete file was discarded"
                        }
                        check(temporary.renameTo(destination)) { "Could not install the verified model" }
                        completed += model.bytes
                        _downloadProgress.value = DownloadProgress(completed, total)
                    } finally {
                        connection.disconnect()
                        temporary.delete()
                    }
                }
                check(isModelInstalled(context)) { "The model set is incomplete" }
                LEGACY_MODEL_VERSIONS.forEach { version ->
                    File(context.noBackupFilesDir, "home_ai_models/$version").deleteRecursively()
                }
                _modelInstalled.value = true
                _cacheRevision.value++
            } catch (error: Throwable) {
                models.forEach { File(directory, "${it.name}.part").delete() }
                throw error
            } finally {
                _downloadProgress.value = null
            }
        }
    }

    /** Removes downloaded Music2EMO models, cached scores, and stored MERT embeddings. */
    suspend fun deleteModels(context: Context) {
        val appContext = context.applicationContext
        val runningAnalysis = synchronized(analysisLock) {
            analysisSessionGeneration++
            queueState.clear()
            activeContext = null
            lastLibraryQueue = emptyList()
            lastLibrarySignature = null
            completedLibrarySignature = null
            lowMemoryPausedSignature = null
            analysisJob
        }
        runningAnalysis?.cancelAndJoin()
        classificationMutex.withLock {
            modelOperationMutex.withLock {
                withContext(Dispatchers.IO) {
                    releaseRuntimeAfterMemoryPressure()
                    modelDirectory(appContext).deleteRecursively()
                    embeddingDirectory(appContext).deleteRecursively()
                    synchronized(embeddingLock) { embeddingCache.clear() }
                    featuresMemoryCache.clear()
                    synchronized(failedKeysLock) { failedKeysMemory = LinkedHashSet() }
                    val cache = appContext.getSharedPreferences(PREFS_MOOD_CACHE, Context.MODE_PRIVATE)
                    cache.edit().apply {
                        cache.all.keys.filter { it.startsWith("${MODEL_VERSION}_") }.forEach(::remove)
                    }.apply()
                    appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .remove(KEY_FAILED_TRACKS)
                        .putBoolean(KEY_MODEL_DECISION, false)
                        .apply()
                    synchronized(verificationLock) {
                        verificationFingerprint = null
                        verificationResult = false
                    }
                    _modelInstalled.value = false
                    _cacheRevision.value++
                }
            }
        }
        _analysisProgress.value = null
        _downloadProgress.value = null
    }

    private fun librarySignature(songs: List<Song>): String {
        val keys = songs.asSequence()
            .filter(::hasReadableSource)
            .map(::cacheKey)
            .distinct()
            .sorted()
            .joinToString("|")
        return digest(keys)
    }

    /** Queues background mood (and optional middle-window vocal) analysis for [prioritizedSongs]. */
    fun requestAnalysis(context: Context, prioritizedSongs: List<Song>) {
        if (!isModelInstalled(context)) return
        val appContext = context.applicationContext
        val readable = prioritizedSongs
            .filter(::hasReadableSource)
            .distinctBy { cacheKey(it) }
        if (readable.isEmpty()) return
        val signature = librarySignature(readable)
        val pending = readable.filter {
            cachedFeatures(appContext, it) == null && !hasAnalysisFailure(appContext, it)
        }
        val alreadyFailed = readable.count { hasAnalysisFailure(appContext, it) }

        synchronized(analysisLock) {
            if (prioritizedSongs.size >= lastLibraryQueue.size && prioritizedSongs.isNotEmpty()) {
                lastLibraryQueue = prioritizedSongs
            }
            activeContext = appContext
            lastLibrarySignature = signature

            // Same library already completed (or paused for low memory) -> do not restart from 0.
            if (pending.isEmpty()) {
                completedLibrarySignature = signature
                lowMemoryPausedSignature = null
                if (analysisJob?.isActive != true && !queueState.hasPending) {
                    _analysisProgress.value = AnalysisProgress(0, 0, alreadyFailed, finished = true)
                }
                return
            }
            if (signature == completedLibrarySignature || signature == lowMemoryPausedSignature) {
                return
            }

            completedLibrarySignature = null
            lowMemoryPausedSignature = null
            val addedAny = queueState.enqueueLibrary(pending, alreadyFailed)
            if (!addedAny && analysisJob?.isActive == true) return
            if (queueState.hasPending) {
                _analysisProgress.value = queueState.snapshot(finished = false)
                if (analysisJob?.isActive != true) {
                    val generation = ++analysisSessionGeneration
                    analysisJob = analysisScope.launch { runAnalysisLoop(generation) }
                }
            }
        }
    }

    /**
     * Pushes playback/queue tracks to the front of the background analysis queue
     * without discarding the library backlog or resetting current session progress.
     */
    fun prioritizePlaybackTracks(context: Context, playbackSongs: List<Song>) {
        if (!isModelInstalled(context) || playbackSongs.isEmpty()) return
        val appContext = context.applicationContext
        val readablePlayback = playbackSongs
            .filter(::hasReadableSource)
            .distinctBy { cacheKey(it) }
        if (readablePlayback.isEmpty()) return
        val pendingPlayback = readablePlayback.filter {
            cachedFeatures(appContext, it) == null && !hasAnalysisFailure(appContext, it)
        }
        if (pendingPlayback.isEmpty()) return
        val alreadyFailed = readablePlayback.count { hasAnalysisFailure(appContext, it) }

        synchronized(analysisLock) {
            activeContext = appContext
            lowMemoryPausedSignature = null
            queueState.prioritizeFront(pendingPlayback, alreadyFailed)
            if (queueState.hasPending) {
                _analysisProgress.value = queueState.snapshot(finished = false)
                if (analysisJob?.isActive != true) {
                    val generation = ++analysisSessionGeneration
                    analysisJob = analysisScope.launch { runAnalysisLoop(generation) }
                }
            }
        }
    }

    private suspend fun runAnalysisLoop(generation: Long) {
        var processedInBatch = 0
        var consecutiveOom = 0
        try {
            while (currentCoroutineContext().isActive) {
                val next = synchronized(analysisLock) {
                    if (generation != analysisSessionGeneration) return@synchronized null
                    val ctx = activeContext ?: return@synchronized null
                    queueState.pollNext()?.let { (key, song) -> Triple(ctx, key, song) }
                } ?: break

                val (appContext, key, song) = next
                if (cachedFeatures(appContext, song) != null || hasAnalysisFailure(appContext, song)) {
                    val stillRunning = synchronized(analysisLock) {
                        if (generation != analysisSessionGeneration) return@synchronized false
                        queueState.markTrackDone(key, succeeded = true, wasLowMemory = false)
                        _analysisProgress.value = queueState.snapshot(finished = false)
                        queueState.hasPending
                    }
                    if (!stillRunning) break
                    continue
                }

                var succeeded = false
                var trackLowMemory = false
                try {
                    val features = withTimeout(ANALYSIS_TRACK_TIMEOUT_MS) {
                        classify(appContext, song)
                    }
                    if (features != null) {
                        succeeded = true
                        consecutiveOom = 0
                    } else {
                        markAnalysisFailure(appContext, song)
                        consecutiveOom = 0
                    }
                } catch (_: TimeoutCancellationException) {
                    markAnalysisFailure(appContext, song)
                    consecutiveOom = 0
                    Log.w(TAG, "Mood analysis timed out for a track")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: OutOfMemoryError) {
                    trackLowMemory = true
                    consecutiveOom++
                    markAnalysisFailure(appContext, song)
                } catch (error: Exception) {
                    markAnalysisFailure(appContext, song)
                    consecutiveOom = 0
                    Log.w(TAG, "Skipping an unprocessable mood-analysis track", error)
                }

                val abortForOom = trackLowMemory && consecutiveOom >= MAX_CONSECUTIVE_OOM
                val shouldContinue = synchronized(analysisLock) {
                    if (generation != analysisSessionGeneration) return@synchronized false
                    queueState.markTrackDone(key, succeeded = succeeded, wasLowMemory = abortForOom)
                    if (abortForOom) {
                        lowMemoryPausedSignature = lastLibrarySignature
                        _analysisProgress.value = queueState.snapshot(finished = false)
                        false
                    } else {
                        _analysisProgress.value = queueState.snapshot(finished = false)
                        queueState.hasPending
                    }
                }
                if (abortForOom || !shouldContinue) break

                if (trackLowMemory) {
                    System.gc()
                    delay(OOM_RECOVERY_DELAY_MS)
                } else {
                    processedInBatch++
                    if (processedInBatch >= ANALYSIS_BATCH_SIZE) {
                        processedInBatch = 0
                        delay(ANALYSIS_BATCH_PAUSE_MS)
                    } else {
                        delay(150L)
                    }
                }
            }
        } finally {
            synchronized(analysisLock) {
                analysisJob = null
                if (generation == analysisSessionGeneration) {
                    if (queueState.lowMemory) {
                        _analysisProgress.value = queueState.snapshot(finished = false)
                    } else if (queueState.hasPending) {
                        val nextGen = ++analysisSessionGeneration
                        analysisJob = analysisScope.launch { runAnalysisLoop(nextGen) }
                    } else if (queueState.total > 0) {
                        completedLibrarySignature = lastLibrarySignature
                        _analysisProgress.value = queueState.snapshot(finished = true)
                    }
                }
            }
        }
    }

    /** Returns cached Music2EMO features keyed by [Song.videoId] for all analyzed tracks in [songs]. */
    fun cachedFeaturesByVideoId(context: Context, songs: List<Song>): Map<String, TrackMoodFeatures> {
        val prefs = context.getSharedPreferences(PREFS_MOOD_CACHE, Context.MODE_PRIVATE)
        return buildMap {
            songs.forEach { song ->
                val features = cachedFeaturesWithPrefs(prefs, song) ?: return@forEach
                if (song.videoId.isNotBlank()) put(song.videoId, features)
            }
        }
    }

    fun cachedFeatures(context: Context, song: Song): TrackMoodFeatures? =
        cachedFeaturesWithPrefs(context.getSharedPreferences(PREFS_MOOD_CACHE, Context.MODE_PRIVATE), song)

    private fun cachedFeaturesWithPrefs(
        prefs: android.content.SharedPreferences,
        song: Song,
    ): TrackMoodFeatures? {
        val key = cacheKey(song)
        featuresMemoryCache[key]?.let { return it }
        readFeatures(prefs, key)?.let {
            featuresMemoryCache[key] = it
            return it
        }
        val legacyKey = legacyCacheKey(song)
        if (legacyKey != key) {
            featuresMemoryCache[legacyKey]?.let {
                featuresMemoryCache[key] = it
                return it
            }
            readFeatures(prefs, legacyKey)?.let { promoted ->
                featuresMemoryCache[key] = promoted
                writeFeatures(prefs, key, promoted)
                return promoted
            }
        }
        return null
    }

    private fun readFeatures(prefs: android.content.SharedPreferences, key: String): TrackMoodFeatures? {
        val valence = prefs.getFloat("${MODEL_VERSION}_${key}_v", Float.NaN)
        val arousal = prefs.getFloat("${MODEL_VERSION}_${key}_a", Float.NaN)
        val uplifting = prefs.getFloat("${MODEL_VERSION}_${key}_up", Float.NaN)
        val calm = prefs.getFloat("${MODEL_VERSION}_${key}_calm", Float.NaN)
        if (!valence.isFinite() || !arousal.isFinite() || !uplifting.isFinite() || !calm.isFinite()) {
            return null
        }
        return TrackMoodFeatures(
            valence = valence.coerceIn(0f, 1f),
            arousal = arousal.coerceIn(0f, 1f),
            uplifting = uplifting.coerceIn(0f, 1f),
            calm = calm.coerceIn(0f, 1f),
        )
    }

    private fun writeFeatures(
        prefs: android.content.SharedPreferences,
        key: String,
        features: TrackMoodFeatures,
    ) {
        prefs.edit()
            .putFloat("${MODEL_VERSION}_${key}_v", features.valence)
            .putFloat("${MODEL_VERSION}_${key}_a", features.arousal)
            .putFloat("${MODEL_VERSION}_${key}_up", features.uplifting)
            .putFloat("${MODEL_VERSION}_${key}_calm", features.calm)
            .apply()
    }

    fun saveFeatures(context: Context, song: Song, features: TrackMoodFeatures) {
        val key = cacheKey(song)
        featuresMemoryCache[key] = features
        writeFeatures(context.getSharedPreferences(PREFS_MOOD_CACHE, Context.MODE_PRIVATE), key, features)
        _cacheRevision.value++
    }

    /** Loads the 1536-dim pooled MERT Layer 5/6 embedding for [song], or null if not cached. */
    fun loadEmbedding(context: Context, song: Song): FloatArray? {
        val key = cacheKey(song)
        synchronized(embeddingLock) {
            embeddingCache[key]?.let { return it }
        }
        val dir = embeddingDirectory(context)
        val primary = File(dir, "$key.f32")
        val file = when {
            primary.isFile && primary.length() == MERT_EMBEDDING_DIM * 4L -> primary
            else -> {
                val legacyKey = legacyCacheKey(song)
                val fallback = File(dir, "$legacyKey.f32")
                if (legacyKey != key && fallback.isFile && fallback.length() == MERT_EMBEDDING_DIM * 4L) {
                    runCatching { fallback.copyTo(primary, overwrite = true) }
                    fallback
                } else {
                    return null
                }
            }
        }
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        if (bytes.size != MERT_EMBEDDING_DIM * 4) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val vec = FloatArray(MERT_EMBEDDING_DIM)
        buf.get(vec)
        synchronized(embeddingLock) { embeddingCache[key] = vec }
        return vec
    }

    private fun saveEmbedding(context: Context, song: Song, embedding: FloatArray) {
        if (embedding.size != MERT_EMBEDDING_DIM) return
        val key = cacheKey(song)
        synchronized(embeddingLock) { embeddingCache[key] = embedding }
        val dir = embeddingDirectory(context).apply { mkdirs() }
        val bytes = ByteBuffer.allocate(MERT_EMBEDDING_DIM * 4).order(ByteOrder.LITTLE_ENDIAN)
        bytes.asFloatBuffer().put(embedding)
        runCatching { File(dir, "$key.f32").writeBytes(bytes.array()) }
        trimDiskEmbeddings(dir)
    }

    private fun trimDiskEmbeddings(dir: File) {
        val files = dir.listFiles()?.takeIf { it.size > MAX_DISK_EMBEDDINGS } ?: return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_DISK_EMBEDDINGS)
            .forEach { runCatching { it.delete() } }
    }

    /** Cosine similarity in [-1f, 1f] between two equal-length vectors (0f if invalid). */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || a.size != b.size) return 0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            val av = a[i].toDouble()
            val bv = b[i].toDouble()
            dot += av * bv
            normA += av * av
            normB += bv * bv
        }
        val denom = sqrt(normA) * sqrt(normB)
        if (!denom.isFinite() || denom <= 1e-8) return 0f
        return (dot / denom).toFloat().coerceIn(-1f, 1f)
    }

    private fun failedKeySet(context: Context): LinkedHashSet<String> = synchronized(failedKeysLock) {
        failedKeysMemory?.let { return LinkedHashSet(it) }
        val loaded = LinkedHashSet(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_FAILED_TRACKS, emptySet()).orEmpty(),
        )
        failedKeysMemory = loaded
        LinkedHashSet(loaded)
    }

    fun hasAnalysisFailure(context: Context, song: Song): Boolean {
        val set = failedKeySet(context)
        return cacheKey(song) in set || legacyCacheKey(song) in set
    }

    fun markAnalysisFailure(context: Context, song: Song) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val snapshot = synchronized(failedKeysLock) {
            val current = failedKeysMemory ?: LinkedHashSet(
                prefs.getStringSet(KEY_FAILED_TRACKS, emptySet()).orEmpty(),
            )
            current.add(cacheKey(song))
            while (current.size > MAX_FAILED_TRACKS) current.remove(current.first())
            failedKeysMemory = current
            LinkedHashSet(current)
        }
        prefs.edit().putStringSet(KEY_FAILED_TRACKS, snapshot).apply()
    }

    fun clearAnalysisFailure(context: Context, song: Song) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val snapshot = synchronized(failedKeysLock) {
            val current = failedKeysMemory ?: LinkedHashSet(
                prefs.getStringSet(KEY_FAILED_TRACKS, emptySet()).orEmpty(),
            )
            current.remove(cacheKey(song))
            current.remove(legacyCacheKey(song))
            failedKeysMemory = current
            LinkedHashSet(current)
        }
        prefs.edit().putStringSet(KEY_FAILED_TRACKS, snapshot).apply()
    }

    /**
     * Runs MERT + Music2Emo inference on [song] (local file or fully-cached stream),
     * reusing the mid-track stereo decode for [VocalDetector] without double-decoding.
     */
    suspend fun classify(context: Context, song: Song): TrackMoodFeatures? = classificationMutex.withLock {
        withContext(Dispatchers.IO) {
            val previousPriority = runCatching { Process.getThreadPriority(Process.myTid()) }.getOrNull()
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
            try {
                cachedFeatures(context, song)?.let { return@withContext it }
                val source = openReadableSource(context, song) ?: return@withContext null
                val operationContext = currentCoroutineContext()
                val start = (song.localStartMs ?: 0L).coerceAtLeast(0L) / 1000.0
                val cueEnd = song.localEndMs?.takeIf { it > (song.localStartMs ?: 0L) }?.div(1000.0)
                val metadataDuration = song.durationMillis().takeIf { it > 0L }?.div(1000.0)

                val audioSegments = source.use { dataSource ->
                    val containerDuration = AudioDecoder.containerDurationSeconds(dataSource)
                    val end = listOfNotNull(containerDuration, cueEnd, metadataDuration?.let { start + it })
                        .minOrNull() ?: (start + ANALYSIS_WINDOW_SECONDS)
                    val windows = analysisWindows(start, end)
                    val midIndex = windows.size / 2
                    val needVocal = VocalDetector.needsAnalysis(context, song)

                    windows.mapIndexedNotNull { idx, (regionStart, regionEnd) ->
                        operationContext.ensureActive()
                        if (needVocal && idx == midIndex) {
                            val stereo = AudioDecoder.decodeRegionStereo(dataSource, regionStart, regionEnd) {
                                !operationContext.isActive
                            } ?: return@mapIndexedNotNull null
                            val (pcm, _) = stereo
                            // Reuse decoded stereo mid window for open-unmix vocal detection.
                            runCatching {
                                VocalDetector.classifyStereoWindow(context, song, pcm.left, pcm.right, pcm.sampleRate)
                            }
                            val mono = FloatArray(pcm.left.size) { i -> (pcm.left[i] + pcm.right[i]) * 0.5f }
                            AudioDecoder.resampleLinear(mono, pcm.sampleRate, SAMPLE_RATE.toDouble())
                        } else {
                            val mono = AudioDecoder.decodeRegion(dataSource, regionStart, regionEnd) {
                                !operationContext.isActive
                            } ?: return@mapIndexedNotNull null
                            AudioDecoder.resampleLinear(mono.first.samples, mono.first.sampleRate, SAMPLE_RATE.toDouble())
                        }
                    }
                }

                operationContext.ensureActive()
                if (audioSegments.isEmpty()) {
                    markAnalysisFailure(context, song)
                    return@withContext null
                }
                val prediction = withModels(context) { runtime -> runtime.predict(audioSegments) }
                saveFeatures(context, song, prediction.features)
                saveEmbedding(context, song, prediction.mertEmbedding)
                clearAnalysisFailure(context, song)
                prediction.features
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (memoryError: OutOfMemoryError) {
                Log.e(TAG, "Mood analysis ran out of memory for ${song.title}; releasing ONNX sessions", memoryError)
                releaseRuntimeAfterMemoryPressure()
                throw memoryError
            } catch (error: Exception) {
                Log.w(TAG, "Skipping mood analysis for ${song.title}", error)
                markAnalysisFailure(context, song)
                null
            } finally {
                if (previousPriority != null) {
                    runCatching { Process.setThreadPriority(previousPriority) }
                }
            }
        }
    }

    private fun hasReadableSource(song: Song): Boolean {
        if (!song.localUri.isNullOrBlank() || !song.localPath.isNullOrBlank()) return true
        if (song.videoId.isBlank()) return false
        return runCatching {
            AudioCache.isFullyCached(Uri.parse("bitchord://watch?v=${song.videoId}"))
        }.getOrDefault(false)
    }

    private fun openReadableSource(context: Context, song: Song) = run {
        val localUri = song.localUri?.takeIf(String::isNotBlank)?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: song.localPath?.takeIf(String::isNotBlank)?.let { Uri.fromFile(File(it)) }
        when {
            localUri != null && LocalAudioSource.isLocal(localUri) -> LocalAudioSource.open(context.contentResolver, localUri)
            song.videoId.isNotBlank() -> runCatching {
                AudioCache.mediaDataSource(Uri.parse("bitchord://watch?v=${song.videoId}"))
            }.getOrNull()
            else -> null
        }
    }

    private fun modelDirectory(context: Context) = File(context.noBackupFilesDir, "home_ai_models/$MODEL_VERSION")
    private fun embeddingDirectory(context: Context) = File(context.noBackupFilesDir, MERT_EMBEDDING_DIR)

    private fun analysisWindows(start: Double, end: Double): List<Pair<Double, Double>> {
        val duration = (end - start).takeIf { it.isFinite() && it > 0.0 } ?: return emptyList()
        val length = min(ANALYSIS_WINDOW_SECONDS, duration)
        val latestStart = (end - length).coerceAtLeast(start)
        val ratios = when {
            duration >= 30.0 -> listOf(0.02, 0.50, 0.96)
            duration > length * 1.7 -> listOf(0.02, 0.96)
            else -> listOf(0.02)
        }
        return ratios.map { ratio ->
            val regionStart = start + (latestStart - start) * ratio
            regionStart to min(regionStart + length, end)
        }.distinctBy { (regionStart, _) -> (regionStart * 10.0).roundToInt() }
    }

    private fun songIdentity(song: Song): String =
        song.localUri?.takeIf(String::isNotBlank)
            ?: song.localPath?.takeIf(String::isNotBlank)
            ?: song.videoId

    internal fun cacheKey(song: Song): String =
        digest("${songIdentity(song)}|${song.localStartMs}|${song.localEndMs}").take(32)

    private fun legacyCacheKey(song: Song): String =
        digest("${songIdentity(song)}|${song.durationText}|${song.localStartMs}|${song.localEndMs}").take(32)

    private fun sha256(file: File): String = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrDefault("")

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private data class Prediction(val features: TrackMoodFeatures, val mertEmbedding: FloatArray)

    private data class Runtime(
        val mert: OrtSession,
        val music2emo: OrtSession,
    ) {
        fun close() {
            runCatching(mert::close)
            runCatching(music2emo::close)
        }

        fun predict(segments: List<FloatArray>): Prediction {
            val env = OrtEnvironment.getEnvironment()
            val mertEmbedding = meanMertSegments(env, segments)
            val output = inferMusic2Emo(env, mertEmbedding)
            val valenceRaw = output.valenceArousal.getOrNull(0)?.takeIf { it.isFinite() } ?: error("Music2Emo valence invalid")
            val arousalRaw = output.valenceArousal.getOrNull(1)?.takeIf { it.isFinite() } ?: error("Music2Emo arousal invalid")
            val normValence = ((valenceRaw.coerceIn(1f, 9f) - 1f) / 8f).coerceIn(0f, 1f)
            val normArousal = ((arousalRaw.coerceIn(1f, 9f) - 1f) / 8f).coerceIn(0f, 1f)
            val vaUplifting = (normValence * 0.55f + normArousal * 0.45f).coerceIn(0f, 1f)
            val vaCalm = ((1f - normArousal) * 0.75f + normValence * 0.25f).coerceIn(0f, 1f)
            val classUplifting = strongestTagMean(output.moodLogits, UPLIFTING_TAG_INDICES)
            val classCalm = strongestTagMean(output.moodLogits, CALM_TAG_INDICES)
            return Prediction(
                features = TrackMoodFeatures(
                    valence = normValence,
                    arousal = normArousal,
                    uplifting = (vaUplifting * 0.6f + classUplifting * 0.4f).coerceIn(0f, 1f),
                    calm = (vaCalm * 0.6f + classCalm * 0.4f).coerceIn(0f, 1f),
                ),
                mertEmbedding = mertEmbedding,
            )
        }

        private fun strongestTagMean(logits: FloatArray, indices: IntArray): Float {
            val probabilities = indices.asSequence().mapNotNull { index ->
                logits.getOrNull(index)?.takeIf(Float::isFinite)?.let { logit ->
                    (1f / (1f + exp(-logit))).coerceIn(0f, 1f)
                }
            }.sortedDescending()
            if (probabilities.none()) return 0f
            return probabilities.take(3).average().toFloat()
        }

        private fun meanMertSegments(env: OrtEnvironment, segments: List<FloatArray>): FloatArray {
            val sum = FloatArray(MERT_EMBEDDING_DIM)
            var count = 0
            segments.forEach { samples ->
                val embedding = inferMert(env, samples) ?: return@forEach
                for (index in sum.indices) sum[index] += embedding[index]
                count++
            }
            check(count > 0) { "MERT returned no usable embeddings" }
            return FloatArray(sum.size) { sum[it] / count }
        }

        private fun inferMert(env: OrtEnvironment, samples: FloatArray): FloatArray? {
            if (samples.isEmpty()) return null
            val normalized = normalizeWaveform(samples)
            return OnnxTensor.createTensor(env, FloatBuffer.wrap(normalized), longArrayOf(1L, normalized.size.toLong())).use { input ->
                mert.run(mapOf("input_values" to input)).use { result ->
                    val layer5Index = mert.outputNames.indexOf("layer_norm_13")
                    val layer6Index = mert.outputNames.indexOf("layer_norm_15")
                    check(layer5Index >= 0 && layer6Index >= 0) { "MERT output layer order is invalid" }
                    val first = meanFrameEmbedding(result[layer5Index].value)
                    val second = meanFrameEmbedding(result[layer6Index].value)
                    if (first == null || second == null) null else first + second
                }
            }
        }

        private data class Music2EmoOutput(val moodLogits: FloatArray, val valenceArousal: FloatArray)

        private fun inferMusic2Emo(env: OrtEnvironment, mertEmbedding: FloatArray): Music2EmoOutput {
            check(mertEmbedding.size == MERT_EMBEDDING_DIM) { "Music2Emo expects pooled MERT layers 5 and 6" }
            val roots = LongArray(M2E_CHORD_SEQUENCE_LENGTH) { UNKNOWN_CHORD_ROOT }
            val attributes = LongArray(M2E_CHORD_SEQUENCE_LENGTH) { UNKNOWN_CHORD_ATTRIBUTE }
            val mode = floatArrayOf(MAJOR_MODE)
            val input = OnnxTensor.createTensor(env, FloatBuffer.wrap(mertEmbedding), longArrayOf(1L, mertEmbedding.size.toLong()))
            val rootInput = OnnxTensor.createTensor(env, LongBuffer.wrap(roots), longArrayOf(1L, roots.size.toLong()))
            val attrInput = OnnxTensor.createTensor(env, LongBuffer.wrap(attributes), longArrayOf(1L, attributes.size.toLong()))
            val modeInput = OnnxTensor.createTensor(env, FloatBuffer.wrap(mode), longArrayOf(1L, 1L))
            return input.use { mertTensor -> rootInput.use { rootTensor -> attrInput.use { attrTensor -> modeInput.use { modeTensor ->
                music2emo.run(mapOf(
                    "mert_embedding" to mertTensor,
                    "chord_root" to rootTensor,
                    "chord_attr" to attrTensor,
                    "key_mode" to modeTensor,
                )).use { result ->
                    val moodsIndex = music2emo.outputNames.indexOf("mood_logits")
                    check(moodsIndex >= 0) { "Music2Emo mood output is missing" }
                    val outputIndex = music2emo.outputNames.indexOf("valence_arousal")
                    check(outputIndex >= 0) { "Music2Emo valence/arousal output is missing" }
                    val moods = flattenFloat(result[moodsIndex].value)
                    check(moods.size == 56) { "Music2Emo mood output has an unexpected label count" }
                    val valenceArousal = flattenFloat(result[outputIndex].value)
                    check(valenceArousal.size >= 2) { "Music2Emo returned no valence/arousal scores" }
                    Music2EmoOutput(moods, valenceArousal)
                }
            } } } }
        }

        private fun meanFrameEmbedding(value: Any?): FloatArray? {
            val flattened = flattenFloat(value)
            if (flattened.isEmpty() || flattened.size % MERT_FEATURE_SIZE != 0) return null
            val frames = flattened.size / MERT_FEATURE_SIZE
            if (frames <= 0) return null
            val mean = FloatArray(MERT_FEATURE_SIZE)
            for (frame in 0 until frames) {
                val offset = frame * MERT_FEATURE_SIZE
                for (dimension in 0 until MERT_FEATURE_SIZE) mean[dimension] += flattened[offset + dimension]
            }
            for (dimension in mean.indices) mean[dimension] /= frames
            return mean
        }

        companion object {
            private fun normalizeWaveform(samples: FloatArray): FloatArray {
                val mean = samples.average().toFloat()
                var variance = 0.0
                samples.forEach { sample ->
                    val centered = sample - mean
                    variance += centered * centered
                }
                val scale = sqrt(variance / samples.size + 1e-7).toFloat()
                return FloatArray(samples.size) { (samples[it] - mean) / scale }
            }

            private fun flattenFloat(value: Any?): FloatArray = when (value) {
                is FloatArray -> value
                is Array<*> -> value.flatMap { flattenFloat(it).asIterable() }.toFloatArray()
                else -> FloatArray(0)
            }
        }
    }

    @Volatile private var runtime: Runtime? = null
    private val runtimeLock = Any()

    private fun releaseRuntimeAfterMemoryPressure() {
        synchronized(runtimeLock) {
            runtime?.close()
            runtime = null
        }
        VocalDetector.release()
    }

    private suspend fun <T> withModels(context: Context, block: (Runtime) -> T): T = withContext(Dispatchers.IO) {
        check(isModelInstalled(context)) { "AI model files are missing or failed verification" }
        val active = runtime ?: synchronized(runtimeLock) {
            runtime ?: run {
                val directory = modelDirectory(context)
                val options = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(1)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    setCPUArenaAllocator(false)
                    setMemoryPatternOptimization(false)
                }
                val environment = OrtEnvironment.getEnvironment()
                val opened = ArrayList<OrtSession>(models.size)
                try {
                    val mert = environment.createSession(File(directory, models[0].name).absolutePath, options).also(opened::add)
                    val music2emo = environment.createSession(File(directory, models[1].name).absolutePath, options).also(opened::add)
                    check(mert.inputNames.contains("input_values") && mert.outputNames.containsAll(listOf("layer_norm_13", "layer_norm_15"))) {
                        "MERT model missing expected hidden layers"
                    }
                    check(music2emo.inputNames.containsAll(listOf("mert_embedding", "chord_root", "chord_attr", "key_mode")) && music2emo.outputNames.contains("valence_arousal")) {
                        "Music2Emo head missing expected inputs or outputs"
                    }
                    Runtime(mert, music2emo).also { runtime = it }
                } catch (error: Throwable) {
                    opened.forEach { runCatching(it::close) }
                    throw error
                } finally {
                    options.close()
                }
            }
        }
        block(active)
    }
}
