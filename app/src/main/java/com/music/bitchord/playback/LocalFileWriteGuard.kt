package com.music.bitchord.playback

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

/**
 * Coordinates in-place metadata rewrites of local audio files with [PlaybackService].
 *
 * Modifying tags or artwork on a FLAC/audio file via an open read-write
 * descriptor shifts frame byte offsets while ExoPlayer's `ContentDataSource`
 * still holds an open fd with cached `bytesRemaining` and buffered frames in
 * `SampleQueue`. Closing any matching player source before writing and
 * reopening it at the saved position afterwards prevents mid-track corruption
 * and buffering stalls.
 */
object LocalFileWriteGuard {

    data class Target(
        val mediaId: String? = null,
        val uri: String? = null,
        val path: String? = null,
    )

    interface Handler {
        suspend fun beforeWrite(target: Target): Any?
        suspend fun afterWrite(target: Target, token: Any?)
    }

    @Volatile
    var handler: Handler? = null

    private val writeMutex = Mutex()

    suspend fun <T> aroundWrite(target: Target, block: suspend () -> T): T {
        return writeMutex.withLock {
            val currentHandler = handler
            val token = currentHandler?.beforeWrite(target)
            try {
                block()
            } finally {
                if (currentHandler != null) {
                    withContext(NonCancellable) {
                        currentHandler.afterWrite(target, token)
                    }
                }
            }
        }
    }

    internal fun matches(
        target: Target,
        itemMediaId: String?,
        itemUri: String?,
        itemExtraLocalUri: String?,
        itemExtraLocalPath: String?,
    ): Boolean {
        val targetMediaId = target.mediaId?.trim()?.takeIf { it.isNotEmpty() }
        val candidateMediaId = itemMediaId?.trim()?.takeIf { it.isNotEmpty() }
        if (targetMediaId != null && candidateMediaId != null && targetMediaId == candidateMediaId) {
            return true
        }

        val targetUris = setOfNotNull(normalizeUri(target.uri))
        val candidateUris = setOfNotNull(
            normalizeUri(itemUri),
            normalizeUri(itemExtraLocalUri),
        )
        if (targetUris.any { it in candidateUris }) {
            return true
        }

        val targetPaths = setOfNotNull(
            normalizePath(target.path),
            filePathFromUri(target.uri),
        )
        val candidatePaths = setOfNotNull(
            normalizePath(itemExtraLocalPath),
            filePathFromUri(itemUri),
            filePathFromUri(itemExtraLocalUri),
        )
        return targetPaths.any { it in candidatePaths }
    }

    internal fun normalizeUri(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // MediaStore sometimes refers to the primary volume as `external_primary`
        // (e.g. MediaScannerConnection callbacks) while queries use `external`.
        return trimmed.replaceFirst(
            Regex("""^content://media/external_primary/"""),
            "content://media/external/",
        )
    }

    private fun filePathFromUri(rawUri: String?): String? {
        val trimmed = rawUri?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!trimmed.startsWith("file:", ignoreCase = true)) return null
        val path = runCatching {
            val parsed = URI(trimmed)
            if (parsed.scheme.equals("file", ignoreCase = true)) parsed.path else null
        }.getOrNull() ?: trimmed.removePrefix("file://").removePrefix("file:")
        return normalizePath(path)
    }

    internal fun normalizePath(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { File(trimmed).canonicalPath }
            .getOrElse { runCatching { File(trimmed).absolutePath }.getOrDefault(trimmed) }
            .replace('\\', '/')
    }
}
