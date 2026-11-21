package com.music.bitchord.data.lyrics.align

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.music.bitchord.BuildConfig
import com.music.bitchord.data.Http
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Manages the optional on-device MMS-FA (`xycld/lyric-align-mms-fa` UINT8 ONNX)
 * forced-alignment model (~302 MB). Never downloaded without explicit user action
 * and disabled in `.nointernet` offline builds.
 */
object OnDeviceLyricsAlignerModel {

    private const val TAG = "BitChordLyricsAiModel"
    private const val PREFS = "lyrics_ai_model_prefs"
    private const val KEY_VERIFIED_FINGERPRINT = "mms_fa_uint8_v1_fp"

    const val MODEL_URL = "https://huggingface.co/xycld/lyric-align-mms-fa/resolve/main/mms_fa_uint8.onnx"
    const val MODEL_FILENAME = "mms_fa_uint8.onnx"
    const val EXPECTED_BYTES = 316_795_521L
    const val EXPECTED_SHA256 = "3949a7957375d802a9f813ef2a26315a44a30cd65da647f9405f82cd9f55d42a"

    data class DownloadProgress(val bytes: Long, val totalBytes: Long)

    private val _modelInstalled = MutableStateFlow(false)
    val modelInstalled: StateFlow<Boolean> = _modelInstalled.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    private val downloadMutex = Mutex()
    private val sessionLock = Any()
    @Volatile
    private var session: OrtSession? = null

    fun modelDir(context: Context): File =
        File(context.noBackupFilesDir, "lyrics_ai_models/mms-fa-uint8-v1")

    fun modelFile(context: Context): File =
        File(modelDir(context), MODEL_FILENAME)

    fun isDownloadSupported(context: Context): Boolean {
        if (BuildConfig.APPLICATION_ID.endsWith(".nointernet")) return false
        return runCatching {
            context.checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    /**
     * Checks whether the pinned ONNX model exists on disk and passes byte-length +
     * SHA-256 verification (fast-pathed via file size + lastModified fingerprint once verified).
     */
    fun isModelInstalled(context: Context): Boolean {
        val file = modelFile(context)
        if (!file.isFile || file.length() != EXPECTED_BYTES) {
            _modelInstalled.value = false
            return false
        }
        val fp = "${file.length()}_${file.lastModified()}"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_VERIFIED_FINGERPRINT, null) == fp) {
            _modelInstalled.value = true
            return true
        }
        val shaOk = runCatching { computeSha256(file) == EXPECTED_SHA256 }.getOrDefault(false)
        if (shaOk) {
            prefs.edit().putString(KEY_VERIFIED_FINGERPRINT, fp).apply()
            _modelInstalled.value = true
            return true
        }
        Log.w(TAG, "Cached MMS-FA model failed SHA-256 check; ignoring corrupted file")
        _modelInstalled.value = false
        return false
    }

    suspend fun downloadModel(context: Context): Result<Unit> = downloadMutex.withLock {
        val appContext = context.applicationContext
        if (!isDownloadSupported(appContext)) {
            return Result.failure(IllegalStateException("Downloads are not supported in this build"))
        }
        withContext(Dispatchers.IO) {
            if (isModelInstalled(appContext)) {
                return@withContext Result.success(Unit)
            }
            val dir = modelDir(appContext).apply { mkdirs() }
            val target = modelFile(appContext)
            val part = File(dir, "$MODEL_FILENAME.part")
            if (part.exists()) part.delete()

            try {
                _downloadProgress.value = DownloadProgress(0L, EXPECTED_BYTES)
                val downloadClient = Http.client.newBuilder()
                    .followRedirects(true)
                    .followSslRedirects(false)
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .callTimeout(0, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(MODEL_URL)
                    .header("Accept", "*/*")
                    .build()

                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }
                    if (!response.request.url.isHttps) {
                        throw IOException("Refused non-HTTPS redirect for model download")
                    }
                    val body = response.body ?: throw IOException("Empty response body")
                    val contentLen = body.contentLength()
                    val totalBytes = if (contentLen > 0L) contentLen else EXPECTED_BYTES
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastReportMs = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(part).use { output ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                if (read == 0) continue
                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                downloaded += read
                                val now = System.currentTimeMillis()
                                if (now - lastReportMs >= 150L || downloaded >= totalBytes) {
                                    lastReportMs = now
                                    _downloadProgress.value = DownloadProgress(downloaded, totalBytes)
                                }
                            }
                            output.fd.sync()
                        }
                    }

                    if (downloaded != EXPECTED_BYTES) {
                        throw IOException("Model size mismatch: expected $EXPECTED_BYTES bytes, got $downloaded")
                    }
                    val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                    if (actualHash != EXPECTED_SHA256) {
                        throw IOException("Model checksum verification failed ($actualHash)")
                    }
                }

                releaseSession()
                if (target.exists() && !target.delete()) {
                    throw IOException("Could not replace existing model file")
                }
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                val fp = "${target.length()}_${target.lastModified()}"
                appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_VERIFIED_FINGERPRINT, fp)
                    .apply()
                _modelInstalled.value = true
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "MMS-FA download failed", e)
                Result.failure(e)
            } finally {
                if (part.exists()) part.delete()
                _downloadProgress.value = null
            }
        }
    }

    fun deleteModel(context: Context) {
        val appContext = context.applicationContext
        releaseSession()
        val dir = modelDir(appContext)
        runCatching {
            File(dir, MODEL_FILENAME).delete()
            File(dir, "$MODEL_FILENAME.part").delete()
            if (dir.listFiles().isNullOrEmpty()) dir.delete()
        }
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_VERIFIED_FINGERPRINT)
            .apply()
        _modelInstalled.value = false
    }

    fun openSession(context: Context): OrtSession? {
        session?.let { return it }
        synchronized(sessionLock) {
            session?.let { return it }
            val appContext = context.applicationContext
            if (!isModelInstalled(appContext)) return null
            val target = modelFile(appContext)
            return runCatching {
                val options = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    setCPUArenaAllocator(false)
                    setMemoryPatternOptimization(false)
                }
                try {
                    OrtEnvironment.getEnvironment().createSession(target.absolutePath, options).also {
                        session = it
                    }
                } finally {
                    options.close()
                }
            }.onFailure { Log.w(TAG, "Failed to open MMS-FA ONNX session", it) }.getOrNull()
        }
    }

    fun releaseSession() {
        synchronized(sessionLock) {
            runCatching { session?.close() }
            session = null
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
