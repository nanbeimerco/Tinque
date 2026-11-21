package com.music.bitchord.data.lyrics

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Two-tier + disk-persistent cache for lyrics lookups.
 *
 * - Successful lyrics lookups are cached in RAM (24h) and persisted to disk (30d)
 *   so previously fetched synced lyrics keep working across restarts or outages.
 * - Negative lookups (no lyrics found across any provider with no transient errors)
 *   are cached in memory only for 5 minutes.
 */
object LyricsFetchCache {

    private const val SUCCESS_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours in memory
    private const val DISK_SUCCESS_TTL_MS = 30L * 24 * 60 * 60 * 1000L // 30 days on disk
    private const val NEGATIVE_TTL_MS = 5 * 60 * 1000L      // 5 minutes
    private const val MAX_CACHE_ENTRIES = 500
    private const val MAX_DISK_FILES = 500

    private data class CacheEntry(
        val result: LyricsRepository.Result?,
        val expiresAt: Long,
        val isNegative: Boolean,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val diskLock = Any()
    @Volatile
    private var diskCacheDir: File? = null

    fun init(context: Context) {
        val dir = File(context.applicationContext.noBackupFilesDir, "lyrics_cache")
        runCatching { dir.mkdirs() }
        diskCacheDir = dir
    }

    private fun cacheKey(title: String, artist: String, durationMs: Long): String {
        val roundedSec = (durationMs / 10000L) * 10L // round to nearest 10s
        return "${artist.lowercase(Locale.ROOT).trim()}|${title.lowercase(Locale.ROOT).trim()}|$roundedSec"
    }

    private fun keyDigest(key: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(Locale.ROOT, it) }

    /**
     * Looks up cached lyrics for a track.
     * Returns a Pair: (foundResult, isNegativeCacheHit)
     */
    fun get(title: String, artist: String, durationMs: Long): Pair<LyricsRepository.Result?, Boolean> {
        val key = cacheKey(title, artist, durationMs)
        val entry = cache[key]
        val now = System.currentTimeMillis()

        if (entry != null) {
            if (now <= entry.expiresAt) {
                return if (entry.isNegative) Pair(null, true) else Pair(entry.result, false)
            }
            cache.remove(key)
        }

        val diskResult = readFromDisk(key, now)
        if (diskResult != null) {
            evictIfFull()
            cache[key] = CacheEntry(
                result = diskResult,
                expiresAt = now + SUCCESS_TTL_MS,
                isNegative = false,
            )
            return Pair(diskResult, false)
        }

        return Pair(null, false)
    }

    /**
     * Stores a successful lyrics result in memory and on disk.
     */
    fun putSuccess(title: String, artist: String, durationMs: Long, result: LyricsRepository.Result) {
        if (result.lines.isEmpty()) return
        evictIfFull()
        val key = cacheKey(title, artist, durationMs)
        cache[key] = CacheEntry(
            result = result,
            expiresAt = System.currentTimeMillis() + SUCCESS_TTL_MS,
            isNegative = false,
        )
        writeToDisk(key, result)
    }

    /**
     * Stores a negative result in memory cache for 5 minutes.
     */
    fun putNegative(title: String, artist: String, durationMs: Long) {
        evictIfFull()
        val key = cacheKey(title, artist, durationMs)
        cache[key] = CacheEntry(
            result = null,
            expiresAt = System.currentTimeMillis() + NEGATIVE_TTL_MS,
            isNegative = true,
        )
    }

    /**
     * Invalidates both memory and disk cache entries for the given track (used by manual reload).
     */
    fun invalidate(title: String, artist: String, durationMs: Long) {
        val key = cacheKey(title, artist, durationMs)
        cache.remove(key)
        val dir = diskCacheDir ?: return
        synchronized(diskLock) {
            runCatching { File(dir, "${keyDigest(key)}.json").delete() }
        }
    }

    /**
     * Clears all cached entries (memory and disk).
     */
    fun clearAll() {
        cache.clear()
        val dir = diskCacheDir ?: return
        synchronized(diskLock) {
            runCatching { dir.listFiles()?.forEach { it.delete() } }
        }
    }

    private fun readFromDisk(key: String, now: Long): LyricsRepository.Result? {
        val dir = diskCacheDir ?: return null
        val file = File(dir, "${keyDigest(key)}.json")
        if (!file.isFile) return null
        return runCatching {
            val rawJson = synchronized(diskLock) {
                if (!file.isFile) return null
                file.readText(Charsets.UTF_8)
            }
            val obj = JSONObject(rawJson)
            val savedAt = obj.optLong("savedAt", 0L)
            if (savedAt <= 0L || now - savedAt > DISK_SUCCESS_TTL_MS) {
                runCatching { file.delete() }
                return null
            }
            val sourceId = obj.optString("sourceId")
            val sourceLabel = obj.optString("sourceLabel").ifBlank { sourceId }
            val raw = obj.optString("raw")
            if (sourceId.isBlank() || raw.isBlank()) return null
            val lines = ProviderLyrics.parse(raw)
                ?.map { it.withDecodedEntities() }
                ?.withBackgroundVocals()
                ?.takeIf { parsed -> parsed.any { it.text.isNotBlank() } }
                ?: return null
            val isWordSynced = obj.optBoolean("wordSynced", lines.any { it.isWordSynced })
            LyricsRepository.Result(
                source = LyricsSource(id = sourceId, label = sourceLabel, wordSynced = isWordSynced),
                lines = lines,
            )
        }.getOrNull()
    }

    private fun writeToDisk(key: String, result: LyricsRepository.Result) {
        val dir = diskCacheDir ?: return
        val serialized = if (result.lines.any { it.isWordSynced || it.background?.isWordSynced == true }) {
            result.lines.toEnhancedLrc().ifBlank { result.lines.toLrc() }
        } else {
            result.lines.toLrc()
        }
        if (serialized.isBlank()) return

        val payload = JSONObject()
            .put("sourceId", result.source.id)
            .put("sourceLabel", result.source.label)
            .put("wordSynced", result.source.wordSynced || result.lines.any { it.isWordSynced })
            .put("savedAt", System.currentTimeMillis())
            .put("raw", serialized)
            .toString()

        synchronized(diskLock) {
            runCatching {
                if (!dir.exists()) dir.mkdirs()
                val files = dir.listFiles()
                if (files != null && files.size >= MAX_DISK_FILES) {
                    files.sortedBy { it.lastModified() }
                        .take(files.size - MAX_DISK_FILES + 20)
                        .forEach { it.delete() }
                }
                val target = File(dir, "${keyDigest(key)}.json")
                val tmp = File(dir, "${target.name}.tmp")
                tmp.writeText(payload, Charsets.UTF_8)
                if (!tmp.renameTo(target)) {
                    target.writeText(payload, Charsets.UTF_8)
                    tmp.delete()
                }
            }
        }
    }

    private fun evictIfFull() {
        if (cache.size >= MAX_CACHE_ENTRIES) {
            val now = System.currentTimeMillis()
            val expired = cache.filter { it.value.expiresAt < now }.keys
            expired.forEach { cache.remove(it) }

            if (cache.size >= MAX_CACHE_ENTRIES) {
                // Evict oldest 50 entries
                val oldestKeys = cache.entries.sortedBy { it.value.expiresAt }.take(50).map { it.key }
                oldestKeys.forEach { cache.remove(it) }
            }
        }
    }
}
