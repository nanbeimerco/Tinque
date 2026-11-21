package com.music.bitchord.data.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * SpotiFLAC Mobile lyrics fetching engine for MusicBeat.
 *
 * Implements:
 * 1. Parallel candidate dispatch (parallelism = 8).
 * 2. Priority grace period (1000ms): higher-priority providers are prioritized without
 *    causing long blocking delays if one provider is slow.
 * 3. Provider health cooldowns (10 minutes on network/server failures).
 * 4. Singleflight in-flight request deduplication.
 * 5. Two-tier caching (24h success cache, 5m negative cache).
 * 6. Instrumental title heuristics and fallback searches.
 */
object LyricsFetchEngine {

    private const val PARALLELISM = 8
    private const val PRIORITY_GRACE_MS = 1800L
    private const val PLAIN_WAIT_FOR_SYNCED_MS = 5000L

    private val SYNCED_CAPABLE_IDS = setOf(
        "bini_lrc_red",
        "lrcapi",
        "netease",
        "lrclib",
        "lyricsplus",
        "kugou",
        "apple_music",
        "better_lyrics",
        "qqmusic",
        "musixmatch",
    )

    private val INSTRUMENTAL_PATTERN = Regex(
        "(?i)(?:^|[\\s\\[(\\\\-])(?:instrumental|inst\\.?)(?:[\\s\\])]|$)"
    )

    private val flightScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<LyricsRepository.Result?>>()

    /**
     * Executes lyrics retrieval for a song using SpotiFLAC Mobile's fetching logic.
     */
    suspend fun fetchLyrics(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
        sources: Set<LyricsSource>,
        order: List<LyricsSource>,
        prioritizeSyllableSync: Boolean,
        fetcher: suspend (source: LyricsSource, videoId: String, title: String, artist: String, durationMs: Long, album: String?) -> List<LyricLine>?,
    ): LyricsRepository.Result? {
        val flightKey = "${artist.lowercase().trim()}|${title.lowercase().trim()}|${durationMs / 1000}"

        // 1. Singleflight deduplication
        val deferred = inFlight.computeIfAbsent(flightKey) {
            flightScope.async {
                fetchLyricsInternal(
                    videoId = videoId,
                    title = title,
                    artist = artist,
                    durationMs = durationMs,
                    album = album,
                    sources = sources,
                    order = order,
                    prioritizeSyllableSync = prioritizeSyllableSync,
                    fetcher = fetcher,
                )
            }
        }

        return try {
            deferred.await()
        } finally {
            inFlight.remove(flightKey, deferred)
        }
    }

    private suspend fun fetchLyricsInternal(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
        sources: Set<LyricsSource>,
        order: List<LyricsSource>,
        prioritizeSyllableSync: Boolean,
        fetcher: suspend (source: LyricsSource, videoId: String, title: String, artist: String, durationMs: Long, album: String?) -> List<LyricLine>?,
    ): LyricsRepository.Result? {
        // 2. Check Instrumental heuristic
        if (INSTRUMENTAL_PATTERN.containsMatchIn(title)) {
            LyricsLog.i("Engine", "Track marked instrumental by title heuristic: \"$title\"")
            return null
        }

        // 3. Check Cache
        val (cachedResult, isNegativeHit) = LyricsFetchCache.get(title, artist, durationMs)
        if (isNegativeHit) {
            LyricsLog.i("Engine", "Negative cache hit (no lyrics previously found): \"$title\"")
            return null
        }
        if (cachedResult != null) {
            LyricsLog.s("Engine", "Cache hit for \"$title\" from ${cachedResult.source.label}")
            return cachedResult
        }

        // Filter and order active sources
        val activeSources = order.filter { it in sources }
        if (activeSources.isEmpty()) {
            LyricsLog.w("Engine", "No lyrics sources enabled")
            return null
        }

        // 4. Parallel Dispatch with Priority Grace Period
        val outcome = executeParallelSearch(
            videoId = videoId,
            title = title,
            artist = artist,
            durationMs = durationMs,
            album = album,
            activeSources = activeSources,
            prioritizeSyllableSync = prioritizeSyllableSync,
            fetcher = fetcher,
        )

        // 5. Update Cache (never negative-cache when a transient provider error occurred)
        if (outcome.result != null) {
            LyricsFetchCache.putSuccess(title, artist, durationMs, outcome.result)
        } else if (!outcome.hadProviderError) {
            LyricsFetchCache.putNegative(title, artist, durationMs)
        }

        return outcome.result
    }

    private data class SearchOutcome(
        val result: LyricsRepository.Result?,
        val hadProviderError: Boolean,
    )

    private data class CandidateResult(
        val index: Int,
        val source: LyricsSource,
        val lines: List<LyricLine>?,
        val error: Throwable?,
    )

    private fun isSyncedCapable(source: LyricsSource): Boolean =
        source.wordSynced || source.id.lowercase() in SYNCED_CAPABLE_IDS

    private suspend fun executeParallelSearch(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
        activeSources: List<LyricsSource>,
        prioritizeSyllableSync: Boolean,
        fetcher: suspend (source: LyricsSource, videoId: String, title: String, artist: String, durationMs: Long, album: String?) -> List<LyricLine>?,
    ): SearchOutcome = coroutineScope {
        val semaphore = Semaphore(PARALLELISM)
        val resultsChannel = Channel<CandidateResult>(activeSources.size)

        // Filter out sources in health cooldown
        val viableSourcesWithIndex = activeSources.mapIndexedNotNull { index, source ->
            if (LyricsProviderHealth.shouldSkip(source.id)) null else (index to source)
        }

        if (viableSourcesWithIndex.isEmpty()) {
            LyricsLog.w("Engine", "All active providers are currently in cooldown")
            return@coroutineScope SearchOutcome(result = null, hadProviderError = true)
        }

        // Launch candidate searches
        viableSourcesWithIndex.forEach { (index, source) ->
            launch(Dispatchers.IO) {
                semaphore.withPermit {
                    try {
                        val lines = fetchWithFallback(source, videoId, title, artist, durationMs, album, fetcher)
                        if (!lines.isNullOrEmpty()) {
                            LyricsProviderHealth.markAvailable(source.id)
                        }
                        resultsChannel.send(CandidateResult(index, source, lines, null))
                    } catch (e: Throwable) {
                        if (e !is CancellationException) {
                            LyricsProviderHealth.markUnavailable(source.id, e)
                        }
                        resultsChannel.send(CandidateResult(index, source, null, e))
                    }
                }
            }
        }

        val completed = mutableSetOf<Int>()
        var bestCandidate: CandidateResult? = null
        var pendingCount = viableSourcesWithIndex.size
        var graceStartedAt = 0L
        var hadProviderError = false

        fun hasPendingEarlier(targetIndex: Int): Boolean =
            viableSourcesWithIndex.any { (idx, _) -> idx < targetIndex && idx !in completed }

        fun hasPendingEarlierSynced(targetIndex: Int): Boolean =
            viableSourcesWithIndex.any { (idx, src) -> idx < targetIndex && idx !in completed && isSyncedCapable(src) }

        fun hasPendingSyncedCapable(): Boolean =
            viableSourcesWithIndex.any { (idx, src) -> idx !in completed && isSyncedCapable(src) }

        fun hasPendingWordSyncedCapable(targetIndex: Int): Boolean =
            viableSourcesWithIndex.any { (idx, src) ->
                idx !in completed && src.wordSynced && idx != targetIndex
            }

        while (pendingCount > 0) {
            bestCandidate?.let { best ->
                val bestLines = best.lines!!
                val bestIsWord = bestLines.any { it.isWordSynced }
                val bestIsLine = !bestIsWord && bestLines.any { it.timeMs > 0 }
                val canReturnImmediately = when {
                    bestIsWord -> !hasPendingEarlier(best.index)
                    bestIsLine -> if (prioritizeSyllableSync) {
                        !hasPendingEarlier(best.index) && !hasPendingWordSyncedCapable(best.index)
                    } else {
                        !hasPendingEarlierSynced(best.index)
                    }
                    else -> !hasPendingSyncedCapable() && !hasPendingEarlier(best.index)
                }
                if (canReturnImmediately) {
                    resultsChannel.close()
                    coroutineContext.cancelChildren()
                    return@coroutineScope SearchOutcome(LyricsRepository.Result(best.source, bestLines), hadProviderError)
                }
            }

            val remainingGrace = if (bestCandidate != null && graceStartedAt > 0) {
                val bestLines = bestCandidate!!.lines!!
                val bestIsSynced = bestLines.any { it.isWordSynced || it.timeMs > 0 }
                val graceWindow = if (!bestIsSynced && hasPendingSyncedCapable()) {
                    PLAIN_WAIT_FOR_SYNCED_MS
                } else {
                    PRIORITY_GRACE_MS
                }
                val elapsed = System.currentTimeMillis() - graceStartedAt
                (graceWindow - elapsed).coerceAtLeast(0L)
            } else {
                Long.MAX_VALUE
            }

            if (bestCandidate != null && remainingGrace == 0L) {
                LyricsLog.i("Engine", "Priority grace period expired, returning ${bestCandidate!!.source.label}")
                resultsChannel.close()
                coroutineContext.cancelChildren()
                return@coroutineScope SearchOutcome(LyricsRepository.Result(bestCandidate!!.source, bestCandidate!!.lines!!), hadProviderError)
            }

            // Await next result or timeout
            val candidate = if (bestCandidate != null) {
                kotlinx.coroutines.withTimeoutOrNull(remainingGrace) {
                    resultsChannel.receiveCatching().getOrNull()
                }
            } else {
                resultsChannel.receiveCatching().getOrNull()
            }

            if (candidate == null) {
                // Grace expired
                if (bestCandidate != null) {
                    LyricsLog.i("Engine", "Priority grace expired, returning ${bestCandidate!!.source.label}")
                    resultsChannel.close()
                    coroutineContext.cancelChildren()
                    return@coroutineScope SearchOutcome(LyricsRepository.Result(bestCandidate!!.source, bestCandidate!!.lines!!), hadProviderError)
                }
                break
            }

            completed.add(candidate.index)
            pendingCount--
            if (candidate.error != null && candidate.error !is CancellationException) {
                hadProviderError = true
            }

            val lines = candidate.lines
            if (!lines.isNullOrEmpty()) {
                val isWordSynced = lines.any { it.isWordSynced }
                val isLineSynced = lines.any { it.timeMs > 0 }
                val bestLines = bestCandidate?.lines
                val bestIsWord = bestLines?.any { it.isWordSynced } == true
                val bestIsLine = bestLines?.any { it.timeMs > 0 } == true

                // Determine if this candidate is better than current best:
                // Timed lyrics always beat plain text; word-synced beats line-synced when prioritizeSyllableSync is on
                // or when line-synced is only a fallback and word-synced wasn't already held at a higher priority.
                val isBetter = when {
                    bestCandidate == null -> true
                    prioritizeSyllableSync && isWordSynced && !bestIsWord -> true
                    prioritizeSyllableSync && !isWordSynced && bestIsWord -> false
                    isLineSynced && !bestIsLine -> true
                    !isLineSynced && bestIsLine -> false
                    else -> candidate.index < bestCandidate!!.index
                }

                if (isBetter) {
                    val upgradedSyncTier = (isWordSynced && !bestIsWord) || (isLineSynced && !bestIsLine)
                    bestCandidate = candidate
                    if (graceStartedAt == 0L || upgradedSyncTier) {
                        graceStartedAt = System.currentTimeMillis()
                    }
                }
            }
        }

        resultsChannel.close()
        coroutineContext.cancelChildren()
        SearchOutcome(bestCandidate?.let { LyricsRepository.Result(it.source, it.lines!!) }, hadProviderError)
    }

    private suspend fun fetchWithFallback(
        source: LyricsSource,
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
        fetcher: suspend (source: LyricsSource, videoId: String, title: String, artist: String, durationMs: Long, album: String?) -> List<LyricLine>?,
    ): List<LyricLine>? {
        suspend fun invokeOrPropagate(t: String, a: String): List<LyricLine>? {
            return try {
                fetcher(source, videoId, t, a, durationMs, album)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ProviderFailureException) {
                throw e
            } catch (_: Throwable) {
                null
            }
        }

        // 1. Direct query
        var lines = invokeOrPropagate(title, artist)
        if (!lines.isNullOrEmpty()) return lines

        // Native providers already normalize title/artist internally, so skip redundant retries
        if (com.music.bitchord.data.lyrics.providers.NativeLyricsProviders.isNativeProvider(source.id)) {
            return null
        }

        // 2. Simplified title fallback
        val cleanTitle = simplifyTitle(title)
        val cleanArtist = LrcParser.cleanArtistForSearch(artist)

        if (cleanTitle != title || cleanArtist != artist) {
            lines = invokeOrPropagate(cleanTitle, cleanArtist)
            if (!lines.isNullOrEmpty()) return lines
        }

        // 3. Primary artist fallback
        val primaryArtist = cleanArtist.split(",", "&", "/", "feat.", "ft.").firstOrNull()?.trim()
        if (!primaryArtist.isNullOrBlank() && primaryArtist != cleanArtist) {
            lines = invokeOrPropagate(cleanTitle, primaryArtist)
            if (!lines.isNullOrEmpty()) return lines
        }

        return null
    }

    private fun simplifyTitle(title: String): String {
        return title.replace(Regex("(?i)\\((?:feat\\.?|ft\\.?|official|video|audio|remix|from)[^)]*\\)"), " ")
            .replace(Regex("\\[[^\\]]*\\]"), " ")
            .replace(Regex("(?i)\\b(?:official (?:video|audio|music video)|lyric video|4k video)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
