package com.music.bitchord.playback

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.lyrics.EmbeddedLyrics
import com.music.bitchord.data.lyrics.LrcParser
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsFetchCache
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AutoplaySource
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.data.stats.HeatInfo
import com.music.bitchord.data.stats.HeatStore
import com.music.bitchord.data.stats.heatKey
import com.music.bitchord.feature.homeai.LocalHomeAi
import com.music.bitchord.feature.lyricseditor.data.LocalLyricsManager
import com.music.bitchord.playback.smart.VocalDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/** Most AutoPlay-suggested tracks kept queued ahead of the current one at once. */
const val MAX_QUEUED_AUTOPLAY = 10

private const val MAX_VOCAL_PROBES_PER_BATCH = 6
private const val VOCAL_PROBE_TIMEOUT_MS = 2_000L
private const val MAX_ANALYZED_EMBEDDINGS_LOADED = 200
private const val SHORT_REPEAT_WINDOW_MS = 6 * 3600_000L
private const val MOOD_SIGMA = 0.28
private const val SKIP_MOOD_SIGMA = 0.24

/** Session playback & skip signals supplied by [PlaybackService] for AutoPlay scoring. */
data class AutoplaySessionContext(
    val recentPlayed: List<Song> = emptyList(),
    val skippedSongs: List<Song> = emptyList(),
)

/** Inputs for pure-Kotlin offline/unit-testable AutoPlay ranking. */
internal data class AutoplayScoringInput(
    val seed: Song,
    val candidates: List<Song>,
    val recentPlayedMoods: List<LocalHomeAi.TrackMoodFeatures> = emptyList(),
    val skippedMoods: List<LocalHomeAi.TrackMoodFeatures> = emptyList(),
    val skippedArtists: Set<String> = emptySet(),
    val featuresByKey: Map<String, LocalHomeAi.TrackMoodFeatures> = emptyMap(),
    val seedEmbedding: FloatArray? = null,
    val candidateEmbeddings: Map<String, FloatArray> = emptyMap(),
    val heatByKey: Map<String, HeatInfo> = emptyMap(),
    val excludeInstrumentals: Boolean = false,
    val seedIsInstrumental: Boolean = false,
    val instrumentalKeys: Set<String> = emptySet(),
    val nowMs: Long = System.currentTimeMillis(),
    val limit: Int = MAX_QUEUED_AUTOPLAY,
    val jitterFor: (Song) -> Double = { Random.nextDouble(0.0, 0.9) },
)

private val LOCAL_TAG_CACHE = ConcurrentHashMap<String, LocalTrackHints>()

private data class LocalTrackHints(
    val genre: String?,
    val sidecarLyrics: String?,
)

private val INSTRUMENTAL_TEXT_REGEX = Regex(
    "(?i)(?:" +
        "(?<![a-z0-9])(?:instrumental|off[\\s_-]?vocal|karaoke|minus[\\s_-]?one|backing[\\s_-]?track|" +
        "vocal[\\s_-]?less|vocalless|less[\\s_-]?vocal|no[\\s_-]?vocals?|without[\\s_-]?vocals?|" +
        "beats?[\\s_-]?only)(?![a-z0-9])|" +
        "(?:^|[\\s(\\[「【\\-_/|·])inst\\.?(?=$|[\\s)\\]」】\\-_/|·])|" +
        "[(\\[「【][^()\\[\\]「」【】]*(?<![a-z0-9])inst\\.?(?![a-z0-9])[^()\\[\\]「」【】]*[)\\]」】]|" +
        "(?:オフボーカル|オフボ|カラオケ|純音楽|演奏のみ|歌なし|ボーカルなし|" +
        "インスト(?:(?:ゥ?ルメンタル)|(?=$|[\\s)\\]」】\\-_/|·・]|ver|版|\\.|[^a-zA-Zア-ンー])))" +
        ")"
)

private val INSTRUMENTAL_GENRE_REGEX = Regex(
    "(?i)(?:\\binstrumental\\b|\\bkaraoke\\b|カラオケ|純音楽|(?:^|[\\s/])インスト(?:ゥ?ルメンタル)?(?=$|[\\s/]))"
)

private val PURE_INSTRUMENTAL_LINE_REGEX = Regex(
    "(?i)^[\\s\\(\\[\\{~〜・♪♫♩♬*\\-—]*(?:" +
        "instrumental(?:\\s+ver(?:sion)?)?|inst\\.?|off\\s*vocal|karaoke|music|" +
        "インスト(?:ゥ?ルメンタル)?|オフボーカル|カラオケ|純音楽|演奏(?:のみ)?|間奏" +
        ")[\\s\\)\\]\\}~〜・♪♫♩♬*\\-—]*$"
)

/**
 * Finds the YouTube id that should seed AutoPlay for a song. A YouTube track is
 * its own seed; anything else — a file on the device, a module track — is
 * matched on YouTube Music by its title and artist first.
 */
suspend fun youtubeSeedFor(song: Song): String? {
    val isYouTubeTrack = SourceRegistry.parseTrackKey(song.videoId) == null &&
        song.localUri == null && song.localPath == null && "://" !in song.videoId
    if (isYouTubeTrack) return song.videoId
    val target = TrackMatcher.targetOf(song)
    val query = TrackMatcher.queries(target).firstOrNull() ?: return null
    return YtMusicRepository.search(query, SearchFilter.SONGS)
        .getOrNull()
        ?.filterIsInstance<SearchResult.Track>()
        ?.map { it.song }
        ?.let { TrackMatcher.best(it, target) }
        ?.videoId
}

/**
 * Loads, de-duplicates, optionally filters instrumentals, and ranks one AutoPlay batch.
 */
suspend fun loadAutoplayTracks(
    existing: List<Song>,
    seedSong: Song,
    limit: Int = MAX_QUEUED_AUTOPLAY,
    source: AutoplaySource = AutoplaySource.YOUTUBE_MUSIC,
    context: Context? = null,
    excludeInstrumentals: Boolean = AppSettings.excludeInstrumentalsInAutoplay.value,
    sessionContext: AutoplaySessionContext = AutoplaySessionContext(),
): Result<List<Song>> {
    if (source == AutoplaySource.OFF) return Result.success(emptyList())

    val appContext = context?.applicationContext
    val seedArtists = QueueBuilder.artistSet(seedSong.artist)
    val skippedArtists = sessionContext.skippedSongs
        .flatMap { QueueBuilder.artistSet(it.artist) }
        .toSet() - seedArtists
    val heatByKey = HeatStore.info.value

    val recentPlayedMoods = if (appContext != null) {
        sessionContext.recentPlayed.mapNotNull { LocalHomeAi.cachedFeatures(appContext, it) }
    } else emptyList()
    val skippedMoods = if (appContext != null) {
        sessionContext.skippedSongs.mapNotNull { LocalHomeAi.cachedFeatures(appContext, it) }
    } else emptyList()

    val seedIsInstrumental = if (!excludeInstrumentals) {
        false
    } else {
        isSongInstrumentalNow(appContext, seedSong, allowVocalProbe = true)
    }

    val extra = when (source) {
        AutoplaySource.OFF -> return Result.success(emptyList())
        AutoplaySource.YOUTUBE_MUSIC -> {
            val seed = youtubeSeedFor(seedSong) ?: return Result.success(emptyList())
            val related = YtMusicRepository.radio(seed).getOrElse { return Result.failure(it) }
            val featuresByKey = appContext?.let {
                LocalHomeAi.cachedFeaturesByVideoId(it, related + seedSong)
            }.orEmpty()
            val tier1InstrumentalKeys = if (excludeInstrumentals && !seedIsInstrumental) {
                related.filter { isTier1OrCachedInstrumental(appContext, it) }.keysForLookup()
            } else emptySet()
            val ranked = rankOnlineRadioCandidates(
                related = related,
                seedFeatures = featuresByKey[seedSong.videoId],
                skippedMoods = skippedMoods,
                skippedArtists = skippedArtists,
                featuresByKey = featuresByKey,
                heatByKey = heatByKey,
                excludeInstrumentals = excludeInstrumentals,
                seedIsInstrumental = seedIsInstrumental,
                instrumentalKeys = tier1InstrumentalKeys,
            )
            val finalCandidates = if (excludeInstrumentals && !seedIsInstrumental && appContext != null) {
                resolveShortlistWithVocalProbe(appContext, existing, ranked, limit)
            } else {
                ranked
            }
            QueueBuilder.extend(existing, finalCandidates, limit)
        }
        AutoplaySource.DEVICE -> {
            val library = appContext?.let { DeviceLibraryCache.songs(it) }.orEmpty()
            val featuresByKey = appContext?.let {
                LocalHomeAi.cachedFeaturesByVideoId(it, library + seedSong + sessionContext.recentPlayed)
            }.orEmpty()
            val seedEmbedding = appContext?.let { LocalHomeAi.loadEmbedding(it, seedSong) }
            val candidateEmbeddings = if (appContext != null && seedEmbedding != null) {
                loadCandidateEmbeddings(appContext, library, featuresByKey, heatByKey)
            } else emptyMap()
            val tier1InstrumentalKeys = if (excludeInstrumentals && !seedIsInstrumental) {
                library.filter { isTier1OrCachedInstrumental(appContext, it) }.keysForLookup()
            } else emptySet()

            val ranked = rankAutoplayCandidates(
                AutoplayScoringInput(
                    seed = seedSong,
                    candidates = library,
                    recentPlayedMoods = recentPlayedMoods,
                    skippedMoods = skippedMoods,
                    skippedArtists = skippedArtists,
                    featuresByKey = featuresByKey,
                    seedEmbedding = seedEmbedding,
                    candidateEmbeddings = candidateEmbeddings,
                    heatByKey = heatByKey,
                    excludeInstrumentals = excludeInstrumentals,
                    seedIsInstrumental = seedIsInstrumental,
                    instrumentalKeys = tier1InstrumentalKeys,
                    limit = limit,
                )
            )
            val finalCandidates = if (excludeInstrumentals && !seedIsInstrumental && appContext != null) {
                resolveShortlistWithVocalProbe(appContext, existing, ranked, limit)
            } else {
                ranked
            }
            QueueBuilder.extend(existing, finalCandidates, limit)
        }
    }
    if (extra.isEmpty()) return Result.success(emptyList())
    return Result.success(extra.map {
        it.copy(fromAutoplay = true, radioName = seedSong.radioName)
    })
}

/** Checks Tier-1 metadata/cached lyrics/genre and cached [VocalDetector] before any audio decode. */
private fun isTier1OrCachedInstrumental(context: Context?, song: Song): Boolean {
    val memCachedLyrics = lyricsCacheText(song)
    val cachedHints = LOCAL_TAG_CACHE[song.heatKey()]
    if (isMetadataOrCachedInstrumental(song, cachedLyrics = memCachedLyrics ?: cachedHints?.sidecarLyrics, genre = cachedHints?.genre)) {
        return true
    }
    if (context != null && VocalDetector.cached(context, song)?.isInstrumental == true) {
        return true
    }
    return false
}

private suspend fun isSongInstrumentalNow(
    context: Context?,
    song: Song,
    allowVocalProbe: Boolean,
): Boolean {
    if (isTier1OrCachedInstrumental(context, song)) return true
    if (context == null) return false
    val diskHints = readLocalHintsOnce(context, song)
    if (isMetadataOrCachedInstrumental(song, cachedLyrics = lyricsCacheText(song) ?: diskHints.sidecarLyrics, genre = diskHints.genre)) {
        return true
    }
    VocalDetector.cached(context, song)?.let { return it.isInstrumental }
    if (!allowVocalProbe) return false
    val probed = withContext(Dispatchers.IO) {
        withTimeoutOrNull(VOCAL_PROBE_TIMEOUT_MS) {
            VocalDetector.classifyStandalone(context, song)
        }
    }
    return probed?.isInstrumental == true
}

/**
 * Walks top candidates in order (skipping items [QueueBuilder.extend] would reject anyway)
 * to inspect local tags/sidecars and probe at most [MAX_VOCAL_PROBES_PER_BATCH] unanalyzed
 * audio tracks with [VocalDetector]. Falls back to [ranked] if every track would be removed.
 */
private suspend fun resolveShortlistWithVocalProbe(
    context: Context,
    existing: List<Song>,
    ranked: List<Song>,
    limit: Int,
): List<Song> {
    if (ranked.isEmpty()) return ranked
    val accepted = mutableListOf<Song>()
    val taken = existing.toMutableList()
    val perArtist = mutableMapOf<String, Int>()
    val seedArtists = existing.lastOrNull()?.artist?.let(QueueBuilder::artistSet).orEmpty()
    var probesUsed = 0

    for (cand in ranked) {
        if (taken.any { QueueBuilder.isSameRecording(it, cand) }) continue
        val artists = QueueBuilder.artistSet(cand.artist)
        val artistKey = artists.minOrNull()
        if (artistKey != null) {
            val cap = if (artists.any { it in seedArtists }) 4 else 2
            val count = perArtist[artistKey] ?: 0
            if (count >= cap) continue
        }

        val hints = if (cand.localPath != null || cand.localUri != null) {
            readLocalHintsOnce(context, cand)
        } else {
            LocalTrackHints(null, null)
        }
        if (isMetadataOrCachedInstrumental(cand, cachedLyrics = lyricsCacheText(cand) ?: hints.sidecarLyrics, genre = hints.genre)) {
            continue
        }

        val vocal = VocalDetector.cached(context, cand) ?: if (probesUsed < MAX_VOCAL_PROBES_PER_BATCH) {
            probesUsed++
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(VOCAL_PROBE_TIMEOUT_MS) {
                    VocalDetector.classifyStandalone(context, cand)
                }
            }
        } else null

        if (vocal?.isInstrumental == true) continue

        accepted += cand
        taken += cand
        if (artistKey != null) perArtist[artistKey] = (perArtist[artistKey] ?: 0) + 1
        if (accepted.size >= limit) break
    }

    return accepted.ifEmpty { ranked }
}

private suspend fun readLocalHintsOnce(context: Context, song: Song): LocalTrackHints {
    val key = song.heatKey()
    LOCAL_TAG_CACHE[key]?.let { return it }
    val loaded = withContext(Dispatchers.IO) {
        var genre: String? = null
        if (!song.localUri.isNullOrBlank() || !song.localPath.isNullOrBlank()) {
            val retriever = MediaMetadataRetriever()
            try {
                if (!song.localUri.isNullOrBlank()) {
                    retriever.setDataSource(context, Uri.parse(song.localUri))
                    genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                } else if (!song.localPath.isNullOrBlank() && File(song.localPath).exists()) {
                    retriever.setDataSource(song.localPath)
                    genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                }
            } catch (_: Throwable) {
            } finally {
                runCatching { retriever.release() }
            }
        }
        val sidecar = EmbeddedLyrics.sidecar(song.localPath)
            ?: runCatching { LocalLyricsManager.findExistingLrcFile(context, song)?.readText() }.getOrNull()
        LocalTrackHints(
            genre = genre?.takeIf { it.isNotBlank() },
            sidecarLyrics = sidecar?.takeIf { it.isNotBlank() },
        )
    }
    if (LOCAL_TAG_CACHE.size > 1024) LOCAL_TAG_CACHE.clear()
    LOCAL_TAG_CACHE[key] = loaded
    return loaded
}

private fun lyricsCacheText(song: Song): String? {
    val (result, _) = LyricsFetchCache.get(song.title, song.artist, song.durationMillis())
    val lines = result?.lines ?: return null
    return if (isLyricsLinesInstrumentalOnly(lines)) "[Instrumental Only]" else null
}

private fun loadCandidateEmbeddings(
    context: Context,
    library: List<Song>,
    featuresByKey: Map<String, LocalHomeAi.TrackMoodFeatures>,
    heatByKey: Map<String, HeatInfo>,
): Map<String, FloatArray> {
    val analyzed = library
        .filter { featuresByKey.containsKey(it.videoId) }
        .sortedByDescending { heatByKey[it.heatKey()]?.level ?: 0f }
        .take(MAX_ANALYZED_EMBEDDINGS_LOADED)
    return buildMap(analyzed.size) {
        for (song in analyzed) {
            val vec = LocalHomeAi.loadEmbedding(context, song) ?: continue
            put(song.videoId, vec)
        }
    }
}

/**
 * Pure-Kotlin Tier-1 instrumental detection from title, filename, genre tag, or cached lyrics text.
 * Never returns true solely because lyrics are missing or null.
 */
internal fun isMetadataOrCachedInstrumental(
    song: Song,
    cachedLyrics: String? = null,
    genre: String? = null,
): Boolean {
    if (INSTRUMENTAL_TEXT_REGEX.containsMatchIn(song.title)) return true

    val fileName = extractFileNameWithoutExtension(song.localPath ?: song.localUri)
    if (!fileName.isNullOrBlank() && INSTRUMENTAL_TEXT_REGEX.containsMatchIn(fileName)) return true

    if (!genre.isNullOrBlank() && isInstrumentalGenre(genre)) return true

    if (!cachedLyrics.isNullOrBlank()) {
        val parsed = LrcParser.parseLrc(cachedLyrics)
        val lines = if (parsed.isNotEmpty()) {
            parsed
        } else {
            cachedLyrics.lines().map { LyricLine(timeMs = 0L, text = it.trim()) }
        }
        if (isLyricsLinesInstrumentalOnly(lines)) return true
    }
    return false
}

internal fun isInstrumentalGenre(genre: String): Boolean =
    INSTRUMENTAL_GENRE_REGEX.containsMatchIn(genre.trim())

internal fun isLyricsLinesInstrumentalOnly(lines: List<LyricLine>): Boolean {
    if (lines.isEmpty()) return false
    val nonBlankTexts = lines.asSequence()
        .filter { !it.isGap && it.text.isNotBlank() }
        .map { it.text.trim() }
        .toList()
    if (nonBlankTexts.isEmpty()) return false
    return nonBlankTexts.all { PURE_INSTRUMENTAL_LINE_REGEX.matches(it) }
}

internal fun <T> filterInstrumentalCandidates(
    candidates: List<T>,
    excludeInstrumentals: Boolean,
    seedIsInstrumental: Boolean,
    isInstrumental: (T) -> Boolean,
): List<T> {
    if (!excludeInstrumentals || seedIsInstrumental) return candidates
    val filtered = candidates.filterNot(isInstrumental)
    return filtered.ifEmpty { candidates }
}

/** Backward-compatible local library scorer delegating to [rankAutoplayCandidates]. */
internal fun rankDeviceCandidates(seed: Song, library: List<Song>): List<Song> =
    rankAutoplayCandidates(AutoplayScoringInput(seed = seed, candidates = library))

/**
 * Scores device candidates using V/A mood proximity + transition continuity + session context
 * centroid + MERT embedding cosine similarity + Heat completion/recency/skip signals + lowered
 * artist weight, and interleaves cooled high-peak revival tracks as a 10–20% serendipity budget.
 */
internal fun rankAutoplayCandidates(input: AutoplayScoringInput): List<Song> {
    val basePool = input.candidates.filter { it.videoId != input.seed.videoId && !it.isExternalPreview }
    if (basePool.isEmpty()) return emptyList()

    val afterInstrumentalFilter = filterInstrumentalCandidates(
        candidates = basePool,
        excludeInstrumentals = input.excludeInstrumentals,
        seedIsInstrumental = input.seedIsInstrumental,
    ) { song ->
        hasLookupMatch(input.instrumentalKeys, song) || isMetadataOrCachedInstrumental(song)
    }

    val seedFeatures = lookupFeature(input.featuresByKey, input.seed)
    val contextCentroid = computeMoodCentroid(seedFeatures, input.recentPlayedMoods)

    val seedArtists = QueueBuilder.artistSet(input.seed.artist)
    val seedAlbumArtist = input.seed.albumArtist?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
    val seedFolder = input.seed.localPath?.substringBeforeLast('/', "")?.takeIf { it.isNotEmpty() }
    val seedAlbum = input.seed.albumName?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

    val scored = afterInstrumentalFilter.map { song ->
        val candFeatures = lookupFeature(input.featuresByKey, song)
        val candEmbedding = lookupEmbedding(input.candidateEmbeddings, song)
        val heat = input.heatByKey[song.heatKey()]
        val hasAiSignal = (contextCentroid != null && candFeatures != null) ||
            (input.seedEmbedding != null && candEmbedding != null)

        var score = 0.0
        val candArtists = QueueBuilder.artistSet(song.artist)
        val artistBoost = if (hasAiSignal) 1.6 else 3.2
        val albumArtistBoost = if (hasAiSignal) 0.9 else 1.8
        val folderBoost = if (hasAiSignal) 0.8 else 1.8
        val albumBoost = if (hasAiSignal) 0.6 else 1.0

        if (candArtists.any { it in seedArtists }) score += artistBoost
        if (seedAlbumArtist != null && song.albumArtist?.trim()?.lowercase(Locale.ROOT) == seedAlbumArtist) {
            score += albumArtistBoost
        }
        if (seedFolder != null && song.localPath?.substringBeforeLast('/', "") == seedFolder) {
            score += folderBoost
        }
        if (seedAlbum != null && song.albumName?.trim()?.lowercase(Locale.ROOT) == seedAlbum) {
            score += albumBoost
        }
        if (input.skippedArtists.isNotEmpty() && candArtists.any { it in input.skippedArtists }) {
            score -= 2.6
        }

        if (contextCentroid != null && candFeatures != null) {
            val dv = (candFeatures.valence - contextCentroid.first).toDouble()
            val da = (candFeatures.arousal - contextCentroid.second).toDouble()
            val distSq = dv * dv + da * da
            score += 3.2 * exp(-distSq / (2.0 * MOOD_SIGMA * MOOD_SIGMA))

            if (seedFeatures != null) {
                val jumpA = abs(candFeatures.arousal - seedFeatures.arousal)
                val jumpV = abs(candFeatures.valence - seedFeatures.valence)
                if (jumpA > 0.45f) score -= 1.4 + (jumpA - 0.45f) * 3.0
                if (jumpV > 0.50f) score -= 1.1 + (jumpV - 0.50f) * 2.5
            }

            if (input.skippedMoods.isNotEmpty()) {
                val maxSkipPenalty = input.skippedMoods.maxOf { skipped ->
                    val sv = (candFeatures.valence - skipped.valence).toDouble()
                    val sa = (candFeatures.arousal - skipped.arousal).toDouble()
                    2.4 * exp(-(sv * sv + sa * sa) / (2.0 * SKIP_MOOD_SIGMA * SKIP_MOOD_SIGMA))
                }
                score -= maxSkipPenalty
            }
        }

        if (input.seedEmbedding != null && candEmbedding != null) {
            val cos = cosineSimilarityPure(input.seedEmbedding, candEmbedding)
            score += cos.coerceIn(0f, 1f) * 2.2
        }

        if (heat != null) {
            if (heat.recordedPlays >= 2) {
                val cr = heat.completionRate ?: 0.5f
                score += (cr - 0.5f) * 1.4
                if (heat.skips >= 2 && cr < 0.35f) score -= 1.2 * (1f - cr)
            }
            if (heat.lastPlayed > 0L) {
                val ageMs = input.nowMs - heat.lastPlayed
                if (ageMs in 0L until SHORT_REPEAT_WINDOW_MS) {
                    score -= 2.2
                } else if (sameEpochDay(heat.lastPlayed, input.nowMs)) {
                    score -= 1.1
                }
            }
            score += min(heat.level, 5f) * 0.14
        }

        song to (score + input.jitterFor(song))
    }.sortedByDescending { it.second }.map { it.first }

    return weaveRevivalSerendipity(
        ordered = scored,
        seedFeatures = seedFeatures,
        featuresByKey = input.featuresByKey,
        heatByKey = input.heatByKey,
        skippedArtists = input.skippedArtists,
        nowMs = input.nowMs,
        limit = input.limit,
    )
}

/**
 * Gently re-ranks online YouTube Music radio tracks using personal [HeatInfo], skip avoidance,
 * and cached mood compatibility while keeping YouTube's collaborative-filtering prior intact.
 */
internal fun rankOnlineRadioCandidates(
    related: List<Song>,
    seedFeatures: LocalHomeAi.TrackMoodFeatures? = null,
    skippedMoods: List<LocalHomeAi.TrackMoodFeatures> = emptyList(),
    skippedArtists: Set<String> = emptySet(),
    featuresByKey: Map<String, LocalHomeAi.TrackMoodFeatures> = emptyMap(),
    heatByKey: Map<String, HeatInfo> = emptyMap(),
    excludeInstrumentals: Boolean = false,
    seedIsInstrumental: Boolean = false,
    instrumentalKeys: Set<String> = emptySet(),
    nowMs: Long = System.currentTimeMillis(),
): List<Song> {
    if (related.isEmpty()) return emptyList()
    val pool = filterInstrumentalCandidates(
        candidates = related,
        excludeInstrumentals = excludeInstrumentals,
        seedIsInstrumental = seedIsInstrumental,
    ) { song ->
        hasLookupMatch(instrumentalKeys, song) || isMetadataOrCachedInstrumental(song)
    }

    val total = pool.size.coerceAtLeast(1)
    return pool.mapIndexed { index, song ->
        var score = ((total - index).toDouble() / total) * 1.8
        val candArtists = QueueBuilder.artistSet(song.artist)
        if (skippedArtists.isNotEmpty() && candArtists.any { it in skippedArtists }) {
            score -= 2.6
        }

        val candFeatures = lookupFeature(featuresByKey, song)
        if (seedFeatures != null && candFeatures != null) {
            val dist = hypot(
                (candFeatures.valence - seedFeatures.valence).toDouble(),
                (candFeatures.arousal - seedFeatures.arousal).toDouble(),
            )
            score += 1.6 * exp(-(dist * dist) / (2.0 * MOOD_SIGMA * MOOD_SIGMA))
            val jumpA = abs(candFeatures.arousal - seedFeatures.arousal)
            if (jumpA > 0.45f) score -= 1.0
        }
        if (candFeatures != null && skippedMoods.isNotEmpty()) {
            val maxSkip = skippedMoods.maxOf { skipped ->
                val sv = (candFeatures.valence - skipped.valence).toDouble()
                val sa = (candFeatures.arousal - skipped.arousal).toDouble()
                2.0 * exp(-(sv * sv + sa * sa) / (2.0 * SKIP_MOOD_SIGMA * SKIP_MOOD_SIGMA))
            }
            score -= maxSkip
        }

        val heat = heatByKey[song.heatKey()]
        if (heat != null) {
            if (heat.recordedPlays >= 2) {
                val cr = heat.completionRate ?: 0.5f
                score += (cr - 0.5f) * 1.1
                if (heat.skips >= 2 && cr < 0.35f) score -= 1.0
            }
            if (heat.lastPlayed > 0L && sameEpochDay(heat.lastPlayed, nowMs)) {
                score -= 1.0
            }
        }
        song to score
    }.sortedByDescending { it.second }.map { it.first }
}

private fun weaveRevivalSerendipity(
    ordered: List<Song>,
    seedFeatures: LocalHomeAi.TrackMoodFeatures?,
    featuresByKey: Map<String, LocalHomeAi.TrackMoodFeatures>,
    heatByKey: Map<String, HeatInfo>,
    skippedArtists: Set<String>,
    nowMs: Long,
    limit: Int,
): List<Song> {
    if (ordered.size < 5 || limit < 5) return ordered
    val revivalCount = (limit * 0.15).roundToInt().coerceIn(1, 2)
    val revivals = ordered.filter { song ->
        val heat = heatByKey[song.heatKey()] ?: return@filter false
        val isCooledPeak = heat.peak >= 2.5f && heat.level <= heat.peak - 1.0f
        if (!isCooledPeak && !heat.isRevival) return@filter false
        if (heat.lastPlayed > 0L && sameEpochDay(heat.lastPlayed, nowMs)) return@filter false
        if (QueueBuilder.artistSet(song.artist).any { it in skippedArtists }) return@filter false
        val candMood = lookupFeature(featuresByKey, song)
        if (seedFeatures != null && candMood != null) {
            if (abs(candMood.arousal - seedFeatures.arousal) > 0.48f) return@filter false
        }
        true
    }.sortedByDescending { heatByKey[it.heatKey()]?.peak ?: 0f }
        .take(revivalCount)

    if (revivals.isEmpty()) return ordered

    val revivalIds = revivals.map { it.videoId }.toHashSet()
    val remaining = ordered.filterNot { it.videoId in revivalIds }.toMutableList()
    val insertPositions = listOf(3, 8)
    revivals.forEachIndexed { idx, revival ->
        val pos = insertPositions.getOrElse(idx) { 3 + idx * 5 }.coerceAtMost(remaining.size)
        remaining.add(pos, revival)
    }
    return remaining
}

private fun computeMoodCentroid(
    seed: LocalHomeAi.TrackMoodFeatures?,
    recentPlayed: List<LocalHomeAi.TrackMoodFeatures>,
): Pair<Float, Float>? {
    val entries = buildList {
        if (seed != null) add(seed to 1.0f)
        val decayWeights = floatArrayOf(0.65f, 0.45f, 0.30f, 0.20f)
        recentPlayed.take(decayWeights.size).forEachIndexed { i, m ->
            if (i == 0 && m == seed) return@forEachIndexed
            add(m to decayWeights[i])
        }
    }
    if (entries.isEmpty()) return null
    var vSum = 0f
    var aSum = 0f
    var wSum = 0f
    for ((m, w) in entries) {
        vSum += m.valence * w
        aSum += m.arousal * w
        wSum += w
    }
    if (wSum <= 0f) return null
    return (vSum / wSum) to (aSum / wSum)
}

private fun cosineSimilarityPure(a: FloatArray, b: FloatArray): Float {
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

private fun extractFileNameWithoutExtension(rawPathOrUri: String?): String? {
    if (rawPathOrUri.isNullOrBlank()) return null
    val cleaned = rawPathOrUri.substringBefore('?').substringBefore('#')
    val tail = cleaned.substringAfterLast('/').substringAfterLast('\\')
    if (tail.isBlank()) return null
    val dot = tail.lastIndexOf('.')
    return if (dot > 0) tail.substring(0, dot) else tail
}

private fun sameEpochDay(aMs: Long, bMs: Long): Boolean {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(aMs).atZone(zone).toLocalDate() ==
        Instant.ofEpochMilli(bMs).atZone(zone).toLocalDate()
}

private fun List<Song>.keysForLookup(): Set<String> = buildSet(size * 2) {
    for (s in this@keysForLookup) {
        add(s.videoId)
        add(s.heatKey())
    }
}

private fun hasLookupMatch(keys: Set<String>, song: Song): Boolean =
    song.videoId in keys || song.heatKey() in keys

private fun <T> lookupFeature(map: Map<String, T>, song: Song): T? =
    map[song.videoId] ?: map[song.heatKey()]

private fun lookupEmbedding(map: Map<String, FloatArray>, song: Song): FloatArray? =
    map[song.videoId] ?: map[song.heatKey()]

/**
 * The device library for AutoPlay, scanned at most every few minutes rather
 * than on every track change: a full MediaStore pass is the slow part.
 */
private object DeviceLibraryCache {
    private const val MAX_AGE_MS = 5 * 60_000L
    private var cached: List<Song> = emptyList()
    private var scannedAt = 0L

    suspend fun songs(context: Context): List<Song> {
        val now = System.currentTimeMillis()
        if (cached.isEmpty() || now - scannedAt > MAX_AGE_MS) {
            cached = runCatching { LocalMediaRepository.getLocalMusic(context.applicationContext) }
                .getOrDefault(cached)
            scannedAt = now
        }
        return cached
    }
}
