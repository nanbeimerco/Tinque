package com.music.bitchord.data.lyrics.providers

import android.util.Base64
import com.music.bitchord.data.Http
import com.music.bitchord.data.lyrics.ProviderFailureException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater
import kotlin.math.abs

/**
 * Result item returned by native provider searches.
 */
data class NativeSearchCandidate(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSeconds: Int = 0,
    val provider: String,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
) {
    val bestRaw: String?
        get() = syncedLyrics?.takeIf { it.isNotBlank() } ?: plainLyrics?.takeIf { it.isNotBlank() }
}

internal object NativeLyricsHttp {
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    val fastClient by lazy {
        Http.client.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(7, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    val paxClient by lazy {
        Http.client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(9, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    val neteaseClient by lazy {
        Http.client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    val directKugouClient by lazy {
        Http.client.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(4500, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Executes [request] on [client]. Returns response body on 2xx, `null` on 404 (normal miss),
     * and throws [ProviderFailureException] on 401/403/429/5xx or network/timeout errors so
     * [com.music.bitchord.data.lyrics.LyricsProviderHealth] can cool down broken providers.
     */
    fun executeOrThrow(client: OkHttpClient, request: Request, providerName: String): String? {
        try {
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    return resp.body?.string()
                }
                if (resp.code == 404) {
                    return null
                }
                throw ProviderFailureException(resp.code, "$providerName HTTP ${resp.code}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProviderFailureException) {
            throw e
        } catch (e: IOException) {
            throw ProviderFailureException(null, "$providerName network error: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    fun normalizeForMatch(text: String): String {
        val nfkc = Normalizer.normalize(cleanQueryText(text), Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
        val sb = StringBuilder(nfkc.length)
        for (ch in nfkc) {
            // Normalize Katakana (ァ..ヶ) to Hiragana (ぁ..ゖ) so e.g. 丸ノ内 == 丸の内
            val normalizedChar = if (ch in '\u30A1'..'\u30F6') (ch.code - 0x60).toChar() else ch
            if (normalizedChar.isLetterOrDigit()) sb.append(normalizedChar)
        }
        return sb.toString()
    }

    /**
     * Scores candidate metadata against target track metadata.
     * Returns `< 0` when a non-empty candidate title clearly conflicts with [targetTitle].
     */
    fun scoreCandidate(
        candTitle: String,
        candArtist: String,
        candDurationSec: Int,
        targetTitle: String,
        targetArtist: String,
        targetDurationSec: Int,
    ): Int {
        val normTargetTitle = normalizeForMatch(targetTitle)
        val normCandTitle = normalizeForMatch(candTitle)
        var score = 0
        if (normTargetTitle.isNotEmpty() && normCandTitle.isNotEmpty()) {
            score += when {
                normCandTitle == normTargetTitle -> 100
                normCandTitle.contains(normTargetTitle) || normTargetTitle.contains(normCandTitle) -> 55
                else -> -120
            }
        }
        val normTargetArtist = normalizeForMatch(primaryArtist(targetArtist))
        val normCandArtist = normalizeForMatch(candArtist)
        if (normTargetArtist.isNotEmpty() && normCandArtist.isNotEmpty()) {
            if (normCandArtist == normTargetArtist || normCandArtist.contains(normTargetArtist) || normTargetArtist.contains(normCandArtist)) {
                score += 40
            }
        }
        if (targetDurationSec > 0 && candDurationSec > 0) {
            val diff = abs(candDurationSec - targetDurationSec)
            score += when {
                diff <= 3 -> 45
                diff <= 8 -> 25
                diff <= 15 -> 10
                diff > 25 -> -40
                else -> 0
            }
        }
        return score
    }

    fun urlEncode(value: String): String =
        URLEncoder.encode(value.trim(), "UTF-8")

    fun cleanQueryText(text: String): String =
        text.replace(Regex("""(?i)\s*[\(\[（【](?:feat\.?|ft\.?|official|video|audio|mv|lyric|remaster|live|ver\.?|version|inst)[^)\]）】]*[\)\]）】]"""), "")
            .replace(Regex("""(?i)\s*-\s*(?:remastered|single|ep|live|version|ver\.).*$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    fun primaryArtist(artist: String): String =
        artist.split(Regex("""[,;&/、]|(?i)\b(?:feat\.?|ft\.?|cv[:.：]?)\b"""))
            .firstOrNull()
            ?.trim()
            ?.ifBlank { artist.trim() }
            ?: artist.trim()

    fun formatLineStamp(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val min = (clamped / 60000).toString().padStart(2, '0')
        val sec = ((clamped % 60000) / 1000).toString().padStart(2, '0')
        val cs = ((clamped % 1000) / 10).toString().padStart(2, '0')
        return "[$min:$sec.$cs]"
    }

    fun formatWordStamp(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val min = (clamped / 60000).toString().padStart(2, '0')
        val sec = ((clamped % 60000) / 1000).toString().padStart(2, '0')
        val cs = ((clamped % 1000) / 10).toString().padStart(2, '0')
        return "<$min:$sec.$cs>"
    }

    /**
     * Merges a synced LRC and a translated LRC so translation lines share the exact
     * `[mm:ss.xx]` timestamp immediately after the original line (which Tinque's
     * `ProviderLyrics.markTranslations` detects as `timingSource`).
     * Matches nearest unused translation line within ±500 ms so small sub-frame
     * timestamp offsets between LRC/YRC and tlyric still pair properly.
     */
    fun mergeLrcWithTranslation(mainLrc: String, transLrc: String?): String {
        if (transLrc.isNullOrBlank()) return mainLrc
        val timeRegex = Regex("""^\[(\d{2,}:\d{2}[.:]\d{2,3})](.*)$""")
        data class TransEntry(val timeMs: Long, val text: String, var used: Boolean = false)

        fun parseLineMs(stamp: String): Long? {
            val parts = stamp.split(':', '.')
            if (parts.size < 3) return null
            val min = parts[0].toLongOrNull() ?: return null
            val sec = parts[1].toLongOrNull() ?: return null
            val fracRaw = parts[2]
            val fracMs = when (fracRaw.length) {
                2 -> (fracRaw.toLongOrNull() ?: 0L) * 10L
                3 -> (fracRaw.toLongOrNull() ?: 0L)
                else -> (fracRaw.take(2).toLongOrNull() ?: 0L) * 10L
            }
            return min * 60_000L + sec * 1_000L + fracMs
        }

        val transEntries = ArrayList<TransEntry>()
        for (rawLine in transLrc.lineSequence()) {
            val trimmed = rawLine.trim()
            if (trimmed.startsWith("{")) continue
            val m = timeRegex.find(trimmed) ?: continue
            val ms = parseLineMs(m.groupValues[1]) ?: continue
            val text = m.groupValues[2].trim()
            if (text.isNotEmpty() && text != "//" && !text.startsWith("作词") && !text.startsWith("作曲") && !text.startsWith("编曲")) {
                transEntries += TransEntry(ms, text)
            }
        }
        if (transEntries.isEmpty()) return mainLrc

        val sb = StringBuilder()
        for (rawLine in mainLrc.lineSequence()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            sb.append(trimmed).append('\n')
            val m = timeRegex.find(trimmed) ?: continue
            val lineMs = parseLineMs(m.groupValues[1]) ?: continue
            val bodyText = m.groupValues[2].replace(Regex("""<[^>]+>"""), "").trim()
            if (bodyText.isEmpty()) continue
            val nearest = transEntries
                .filter { !it.used && abs(it.timeMs - lineMs) <= 500L }
                .minByOrNull { abs(it.timeMs - lineMs) }
            if (nearest != null) {
                nearest.used = true
                if (!nearest.text.equals(bodyText, ignoreCase = false)) {
                    sb.append('[').append(m.groupValues[1]).append(']').append(nearest.text).append('\n')
                }
            }
        }
        return sb.toString().trimEnd()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. Apple Music (PaxSenix + iTunes Zero-Auth Search)
// ─────────────────────────────────────────────────────────────────────────────
internal object AppleMusicPaxClient {
    private const val PAX_BASE = "https://lyrics.paxsenix.org"

    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): String? = withContext(Dispatchers.IO) {
        val tracks = searchTracks(title, artist, durationSec)
        for (track in tracks.take(2)) {
            val content = fetchLyricsById(track.id)
            if (!content.isNullOrBlank()) return@withContext content
        }
        null
    }

    suspend fun searchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): List<NativeSearchCandidate> = coroutineScope {
        val tracks = withContext(Dispatchers.IO) { searchTracks(title, artist, durationSec) }
        tracks.take(3).map { track ->
            async(Dispatchers.IO) {
                val lrc = fetchLyricsById(track.id) ?: return@async null
                NativeSearchCandidate(
                    id = "apple_music_${track.id}",
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationSeconds = (track.durationMs / 1000).toInt(),
                    provider = "Apple Music",
                    syncedLyrics = lrc,
                )
            }
        }.awaitAll().filterNotNull()
    }

    private data class AppleTrack(
        val id: String,
        val title: String,
        val artist: String,
        val album: String?,
        val durationMs: Long,
    )

    private fun searchTracks(title: String, artist: String, durationSec: Int): List<AppleTrack> {
        val cleanTitle = NativeLyricsHttp.cleanQueryText(title)
        val cleanArtist = NativeLyricsHttp.primaryArtist(artist)
        val query = "$cleanTitle $cleanArtist".trim()
        var paxFailure: ProviderFailureException? = null
        val paxTracks = try {
            searchPaxTracks(query)
        } catch (e: ProviderFailureException) {
            paxFailure = e
            emptyList()
        }
        val candidates = if (paxTracks.isNotEmpty()) paxTracks else searchItunesTracks(query)
        if (candidates.isEmpty()) {
            if (paxFailure != null) throw paxFailure
            return emptyList()
        }

        val targetMs = durationSec * 1000L
        return candidates.sortedByDescending { track ->
            var score = 0
            if (track.title.equals(cleanTitle, ignoreCase = true)) score += 50
            else if (track.title.contains(cleanTitle, ignoreCase = true)) score += 25
            if (cleanArtist.isNotBlank() && track.artist.contains(cleanArtist, ignoreCase = true)) score += 30
            if (targetMs > 0 && track.durationMs > 0) {
                val diff = abs(track.durationMs - targetMs)
                if (diff <= 3000) score += 30
                else if (diff <= 8000) score += 15
                else if (diff > 25000) score -= 30
            }
            score
        }
    }

    private fun searchPaxTracks(query: String): List<AppleTrack> {
        val url = "$PAX_BASE/apple-music/search?q=${NativeLyricsHttp.urlEncode(query)}"
        val req = Request.Builder().url(url).header("User-Agent", NativeLyricsHttp.USER_AGENT).build()
        val body = NativeLyricsHttp.executeOrThrow(NativeLyricsHttp.paxClient, req, "Apple Music Pax") ?: return emptyList()
        return runCatching {
            val arr = JSONArray(body)
            val list = ArrayList<AppleTrack>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id").ifBlank { obj.optString("trackId") }
                if (id.isBlank()) continue
                list += AppleTrack(
                    id = id,
                    title = obj.optString("songName").ifBlank { obj.optString("trackName", query) },
                    artist = obj.optString("artistName"),
                    album = obj.optString("albumName").takeIf { it.isNotBlank() },
                    durationMs = obj.optLong("duration", obj.optLong("durationInMillis", 0L)),
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun searchItunesTracks(query: String): List<AppleTrack> = runCatching {
        val url = "https://itunes.apple.com/search?term=${NativeLyricsHttp.urlEncode(query)}&entity=song&limit=6"
        val req = Request.Builder().url(url).header("User-Agent", NativeLyricsHttp.USER_AGENT).build()
        val body = NativeLyricsHttp.executeOrThrow(NativeLyricsHttp.fastClient, req, "iTunes") ?: return emptyList()
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        val list = ArrayList<AppleTrack>(results.length())
        for (i in 0 until results.length()) {
            val obj = results.optJSONObject(i) ?: continue
            val id = obj.optLong("trackId", 0L)
            if (id <= 0L) continue
            list += AppleTrack(
                id = id.toString(),
                title = obj.optString("trackName"),
                artist = obj.optString("artistName"),
                album = obj.optString("collectionName").takeIf { it.isNotBlank() },
                durationMs = obj.optLong("trackTimeMillis", 0L),
            )
        }
        list
    }.getOrDefault(emptyList())

    private fun fetchLyricsById(id: String): String? {
        val url = "$PAX_BASE/apple-music/lyrics?id=${NativeLyricsHttp.urlEncode(id)}"
        val req = Request.Builder().url(url).header("User-Agent", NativeLyricsHttp.USER_AGENT).build()
        val body = NativeLyricsHttp.executeOrThrow(NativeLyricsHttp.paxClient, req, "Apple Music Pax")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        if (body.contains("<tt", ignoreCase = true)) return body
        return parsePaxAppleJson(body)
    }

    internal fun parsePaxAppleJson(rawJson: String): String? = runCatching {
        val trimmed = rawJson.trim()
        if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            val ttml = root.optString("ttmlContent").ifBlank { root.optString("ttml") }.ifBlank { root.optString("lyrics") }
            if (ttml.contains("<tt", ignoreCase = true) || ttml.contains("[0")) return ttml
            val contentArr = root.optJSONArray("content") ?: root.optJSONArray("data")
            if (contentArr != null) return parsePaxLinesArray(contentArr)
        } else if (trimmed.startsWith("[")) {
            return parsePaxLinesArray(JSONArray(trimmed))
        }
        null
    }.getOrNull()

    private fun parsePaxLinesArray(arr: JSONArray): String? {
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val lineObj = arr.optJSONObject(i) ?: continue
            val lineTime = lineObj.optLong("timestamp", lineObj.optLong("time", -1L))
            if (lineTime < 0L) continue
            sb.append(NativeLyricsHttp.formatLineStamp(lineTime))
            val wordsArr = lineObj.optJSONArray("text") ?: lineObj.optJSONArray("syllabus")
            if (wordsArr != null && wordsArr.length() > 0) {
                var lastEnd = lineTime
                for (j in 0 until wordsArr.length()) {
                    val w = wordsArr.optJSONObject(j) ?: continue
                    val wText = w.optString("text")
                    val wStart = w.optLong("timestamp", w.optLong("time", lineTime))
                    val wEnd = w.optLong("endtime", wStart + w.optLong("duration", 200L))
                    sb.append(NativeLyricsHttp.formatWordStamp(wStart)).append(wText)
                    if (!w.optBoolean("part", false) && j < wordsArr.length() - 1) {
                        sb.append(' ')
                    }
                    lastEnd = maxOf(lastEnd, wEnd)
                }
                sb.append(NativeLyricsHttp.formatWordStamp(lastEnd))
            } else {
                sb.append(lineObj.optString("text"))
            }
            sb.append('\n')
        }
        return sb.toString().trim().takeIf { it.isNotEmpty() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. LyricsPlus (YouLy+ KPOE Server)
// ─────────────────────────────────────────────────────────────────────────────
internal object LyricsPlusClient {
    private val SERVERS = listOf(
        "https://lyricsplus.prjktla.my.id",
    )

    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): String? = withContext(Dispatchers.IO) {
        val cleanTitle = NativeLyricsHttp.cleanQueryText(title)
        val cleanArtist = NativeLyricsHttp.primaryArtist(artist)
        var lastFailure: ProviderFailureException? = null
        for (base in SERVERS) {
            try {
                val candidate = fetchFromMirror(base, cleanTitle, cleanArtist, album, durationSec)
                if (!candidate.isNullOrBlank()) return@withContext candidate
            } catch (e: ProviderFailureException) {
                lastFailure = e
            }
        }
        if (lastFailure != null) throw lastFailure
        null
    }

    suspend fun searchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): List<NativeSearchCandidate> {
        val lrc = getLyrics(title, artist, album, durationSec) ?: return emptyList()
        return listOf(
            NativeSearchCandidate(
                id = "lyricsplus_${title.hashCode()}",
                title = title,
                artist = artist,
                album = album,
                durationSeconds = durationSec,
                provider = "LyricsPlus",
                syncedLyrics = lrc,
            )
        )
    }

    private fun fetchFromMirror(
        baseUrl: String,
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): String? {
        val urlBuilder = "$baseUrl/v2/lyrics/get".toHttpUrlOrNull()?.newBuilder() ?: return null
        urlBuilder.addQueryParameter("title", title)
        urlBuilder.addQueryParameter("artist", artist)
        if (!album.isNullOrBlank()) urlBuilder.addQueryParameter("album", album)
        if (durationSec > 0) urlBuilder.addQueryParameter("duration", durationSec.toString())
        urlBuilder.addQueryParameter("source", "apple,lyricsplus,musixmatch,spotify,musixmatch-word")

        val req = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", NativeLyricsHttp.USER_AGENT)
            .header("Accept", "application/json")
            .build()

        val body = NativeLyricsHttp.executeOrThrow(NativeLyricsHttp.fastClient, req, "LyricsPlus") ?: return null
        return parseKpoeJson(body)
    }

    internal fun parseKpoeJson(rawJson: String): String? = runCatching {
        val root = JSONObject(rawJson)
        val type = root.optString("type", "Line")
        val lyricsArray = root.optJSONArray("lyrics") ?: return null
        if (lyricsArray.length() == 0) return null

        val isWord = type.equals("Word", ignoreCase = true)
        val sb = StringBuilder()

        for (i in 0 until lyricsArray.length()) {
            val lineObj = lyricsArray.optJSONObject(i) ?: continue
            val startTimeMs = lineObj.optLong("time", -1L)
            val lineDurMs = lineObj.optLong("duration", 0L)
            if (startTimeMs < 0L) continue

            sb.append(NativeLyricsHttp.formatLineStamp(startTimeMs))
            val syllabus = lineObj.optJSONArray("syllabus")
            if (isWord && syllabus != null && syllabus.length() > 0) {
                var lastEnd = startTimeMs + lineDurMs
                for (j in 0 until syllabus.length()) {
                    val syl = syllabus.optJSONObject(j) ?: continue
                    val sylText = syl.optString("text")
                    if (sylText.isEmpty()) continue
                    val sylTime = syl.optLong("time", startTimeMs)
                    val sylDur = syl.optLong("duration", 150L)
                    sb.append(NativeLyricsHttp.formatWordStamp(sylTime)).append(sylText)
                    lastEnd = maxOf(lastEnd, sylTime + sylDur)
                }
                sb.append(NativeLyricsHttp.formatWordStamp(lastEnd))
            } else {
                val text = lineObj.optString("text")
                if (text.isBlank()) continue
                sb.append(text)
            }
            sb.append('\n')
        }
        sb.toString().trim().takeIf { it.isNotEmpty() }
    }.getOrNull()
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. BetterLyrics (lyrics-api.boidu.dev + Portato QQ)
// ─────────────────────────────────────────────────────────────────────────────
internal object BetterLyricsClient {
    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): String? = coroutineScope {
        val mainDeferred = async(Dispatchers.IO) {
            runCatching {
                queryEndpoint("https://lyrics-api.boidu.dev/getLyrics", title, artist, album, durationSec)
            }
        }
        val qqDeferred = async(Dispatchers.IO) {
            runCatching {
                queryEndpoint("https://lyrics-api.boidu.dev/qq/getLyrics", title, artist, album, durationSec)
            }
        }
        val mainOutcome = mainDeferred.await()
        val mainRes = mainOutcome.getOrNull()
        if (!mainRes.isNullOrBlank()) {
            qqDeferred.cancel()
            return@coroutineScope mainRes
        }
        val qqOutcome = qqDeferred.await()
        val qqRes = qqOutcome.getOrNull()
        if (!qqRes.isNullOrBlank()) return@coroutineScope qqRes

        val failure = mainOutcome.exceptionOrNull() as? ProviderFailureException
            ?: qqOutcome.exceptionOrNull() as? ProviderFailureException
        if (failure != null) throw failure
        null
    }

    suspend fun searchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): List<NativeSearchCandidate> = coroutineScope {
        val mainDeferred = async(Dispatchers.IO) {
            runCatching {
                queryEndpoint("https://lyrics-api.boidu.dev/getLyrics", title, artist, album, durationSec)
            }
        }
        val qqDeferred = async(Dispatchers.IO) {
            runCatching {
                queryEndpoint("https://lyrics-api.boidu.dev/qq/getLyrics", title, artist, album, durationSec)
            }
        }
        val out = mutableListOf<NativeSearchCandidate>()
        val mainOutcome = mainDeferred.await()
        mainOutcome.getOrNull()?.takeIf { it.isNotBlank() }?.let {
            out += NativeSearchCandidate(
                id = "better_lyrics_main_${title.hashCode()}",
                title = title,
                artist = artist,
                album = album,
                durationSeconds = durationSec,
                provider = "BetterLyrics",
                syncedLyrics = it,
            )
        }
        val qqOutcome = qqDeferred.await()
        qqOutcome.getOrNull()?.takeIf { it.isNotBlank() }?.let {
            out += NativeSearchCandidate(
                id = "better_lyrics_qq_${title.hashCode()}",
                title = title,
                artist = artist,
                album = album,
                durationSeconds = durationSec,
                provider = "BetterLyrics (Portato QQ)",
                syncedLyrics = it,
            )
        }
        if (out.isEmpty()) {
            val failure = mainOutcome.exceptionOrNull() as? ProviderFailureException
                ?: qqOutcome.exceptionOrNull() as? ProviderFailureException
            if (failure != null) throw failure
        }
        out
    }

    internal fun queryEndpoint(
        endpoint: String,
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): String? {
        val urlBuilder = endpoint.toHttpUrlOrNull()?.newBuilder() ?: return null
        urlBuilder.addQueryParameter("s", NativeLyricsHttp.cleanQueryText(title))
        urlBuilder.addQueryParameter("a", NativeLyricsHttp.primaryArtist(artist))
        if (!album.isNullOrBlank()) urlBuilder.addQueryParameter("al", album)
        if (durationSec > 0) urlBuilder.addQueryParameter("d", durationSec.toString())

        val req = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", NativeLyricsHttp.USER_AGENT)
            .build()

        val body = NativeLyricsHttp.executeOrThrow(NativeLyricsHttp.fastClient, req, "BetterLyrics")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        if (body.startsWith("<tt", ignoreCase = true) || body.startsWith("<?xml", ignoreCase = true)) {
            return body
        }
        return runCatching {
            val json = JSONObject(body)
            json.optString("ttml").takeIf { it.isNotBlank() }
                ?: json.optString("lyrics").takeIf { it.isNotBlank() }
                ?: json.optString("lrc").takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. BiniLyrics & lrc.red (TTML & ISRC Word-Sync)
// ─────────────────────────────────────────────────────────────────────────────
internal object BiniAndLrcRedClient {
    internal data class HitMeta(
        val id: String,
        val title: String,
        val artist: String,
        val album: String?,
        val durationSec: Int,
        val lyricsUrl: String?,
        val isrc: String?,
        val isWord: Boolean,
        val directLyrics: String?,
        val score: Int,
    )

    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): String? = coroutineScope {
        val biniDeferred = async(Dispatchers.IO) { runCatching { queryBini(title, artist, album, durationSec) } }
        val lrcRedDeferred = async(Dispatchers.IO) { runCatching { queryLrcRed(title, artist, durationSec) } }

        val biniResult = biniDeferred.await()
        val bini = biniResult.getOrNull()
        if (!bini.isNullOrBlank()) {
            lrcRedDeferred.cancel()
            return@coroutineScope bini
        }
        val lrcRedResult = lrcRedDeferred.await()
        val lrcRed = lrcRedResult.getOrNull()
        if (!lrcRed.isNullOrBlank()) return@coroutineScope lrcRed

        val failure = biniResult.exceptionOrNull() as? ProviderFailureException
            ?: lrcRedResult.exceptionOrNull() as? ProviderFailureException
        if (failure != null) throw failure
        null
    }

    suspend fun searchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): List<NativeSearchCandidate> = coroutineScope {
        val biniDeferred = async(Dispatchers.IO) { runCatching { searchBiniCandidates(title, artist, album, durationSec) } }
        val lrcRedDeferred = async(Dispatchers.IO) { runCatching { searchLrcRedCandidates(title, artist, durationSec) } }

        val list = mutableListOf<NativeSearchCandidate>()
        val biniRes = biniDeferred.await()
        val lrcRedRes = lrcRedDeferred.await()
        biniRes.getOrNull()?.let { list += it }
        lrcRedRes.getOrNull()?.let { list += it }
        if (list.isEmpty()) {
            val failure = biniRes.exceptionOrNull() as? ProviderFailureException
                ?: lrcRedRes.exceptionOrNull() as? ProviderFailureException
            if (failure != null) throw failure
        }
        list
    }

    internal fun parseBiniHits(
        body: String,
        targetTitle: String,
        targetArtist: String,
        targetDurationSec: Int,
    ): List<HitMeta> = runCatching {
        val trimmed = body.trim()
        if (trimmed.startsWith("<tt", ignoreCase = true) || trimmed.startsWith("<?xml", ignoreCase = true)) {
            return@runCatching listOf(
                HitMeta(
                    id = "bini_direct",
                    title = targetTitle,
                    artist = targetArtist,
                    album = null,
                    durationSec = targetDurationSec,
                    lyricsUrl = null,
                    isrc = null,
                    isWord = true,
                    directLyrics = trimmed,
                    score = 100,
                )
            )
        }
        val json = JSONObject(trimmed)
        val out = ArrayList<HitMeta>()

        val resultsArr = json.optJSONArray("results")
        if (resultsArr != null) {
            for (i in 0 until resultsArr.length()) {
                val item = resultsArr.optJSONObject(i) ?: continue
                val cTitle = item.optString("track_name").ifBlank { item.optString("title", targetTitle) }
                val cArtist = item.optString("artist_name").ifBlank { item.optString("artist", targetArtist) }
                val cAlbum = item.optString("album_name").ifBlank { item.optString("album") }.takeIf { it.isNotBlank() && it != "null" }
                val cDur = item.optDouble("duration", 0.0).let { if (it.isFinite() && it > 0.0) it.toInt() else 0 }
                val isrc = item.optString("isrc").takeIf { it.isNotBlank() && it != "null" }
                val id = item.optString("id").ifBlank { isrc ?: i.toString() }
                val lyricsUrl = item.optString("lyricsUrl")
                    .ifBlank { item.optString("ttmlUrl") }
                    .ifBlank { item.optString("url") }
                    .takeIf { it.startsWith("http") }
                val direct = item.optString("ttml").ifBlank { item.optString("lyrics") }.ifBlank { item.optString("lrc") }
                    .takeIf { it.isNotBlank() && it != "null" }
                val isWord = item.optString("timing_type").equals("word", ignoreCase = true)
                val baseScore = NativeLyricsHttp.scoreCandidate(
                    candTitle = cTitle,
                    candArtist = cArtist,
                    candDurationSec = cDur,
                    targetTitle = targetTitle,
                    targetArtist = targetArtist,
                    targetDurationSec = targetDurationSec,
                )
                val syncBoost = if (isWord) 20 else 0
                out += HitMeta(
                    id = id,
                    title = cTitle,
                    artist = cArtist,
                    album = cAlbum,
                    durationSec = cDur,
                    lyricsUrl = lyricsUrl,
                    isrc = isrc,
                    isWord = isWord,
                    directLyrics = direct,
                    score = baseScore + syncBoost,
                )
            }
        }

        if (out.isEmpty()) {
            val ttmlUrl = json.optString("ttmlUrl").ifBlank { json.optString("lyricsUrl") }.ifBlank { json.optString("url") }
                .takeIf { it.startsWith("http") }
            val direct = json.optString("ttml").ifBlank { json.optString("lyrics") }.ifBlank { json.optString("lrc") }
                .takeIf { it.isNotBlank() && it != "null" }
            val isrc = json.optString("isrc").takeIf { it.isNotBlank() && it != "null" }
            if (ttmlUrl != null || direct != null || isrc != null) {
                out += HitMeta(
                    id = isrc ?: "bini_root",
                    title = targetTitle,
                    artist = targetArtist,
                    album = null,
                    durationSec = targetDurationSec,
                    lyricsUrl = ttmlUrl,
                    isrc = isrc,
                    isWord = true,
                    directLyrics = direct,
                    score = 100,
                )
            }
        }
        out.sortedByDescending { it.score }
    }.getOrDefault(emptyList())

    internal fun parseLrcRedHits(
        body: String,
        targetTitle: String,
        targetArtist: String,
        targetDurationSec: Int,
    ): List<HitMeta> = runCatching {
        val trimmed = body.trim()
        val arr = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> {
                val root = JSONObject(trimmed)
                root.optJSONArray("hits") ?: root.optJSONArray("results")
            }
        } ?: return@runCatching emptyList()

        val out = ArrayList<HitMeta>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val isrc = item.optString("isrc").ifBlank { item.optString("id") }.takeIf { it.isNotBlank() && it != "null" }
                ?: continue
            val cTitle = item.optString("title").ifBlank { item.optString("track_name", targetTitle) }
            val cArtist = item.optString("artist").ifBlank { item.optString("artist_name", targetArtist) }
            val cAlbum = item.optString("album").ifBlank { item.optString("album_name") }.takeIf { it.isNotBlank() && it != "null" }
            val cDur = item.optDouble("duration", 0.0).let { if (it.isFinite() && it > 0.0) it.toInt() else 0 }
            val baseScore = NativeLyricsHttp.scoreCandidate(
                candTitle = cTitle,
                candArtist = cArtist,
                candDurationSec = cDur,
                targetTitle = targetTitle,
                targetArtist = targetArtist,
                targetDurationSec = targetDurationSec,
            )
            out += HitMeta(
                id = isrc,
                title = cTitle,
                artist = cArtist,
                album = cAlbum,
                durationSec = cDur,
                lyricsUrl = "https://lrc.red/s/$isrc.ttml",
                isrc = isrc,
                isWord = true,
                directLyrics = null,
                score = baseScore,
            )
        }
        out.sortedByDescending { it.score }
    }.getOrDefault(emptyList())

    private fun resolveHitLyrics(hit: HitMeta): String? {
        if (!hit.directLyrics.isNullOrBlank()) return hit.directLyrics
        if (!hit.lyricsUrl.isNullOrBlank()) {
            val req = Request.Builder().url(hit.lyricsUrl).header("User-Agent", NativeLyricsHttp.USER_AGENT).build()
            val body = runCatching { NativeLyricsHttp.executeOrThrow(NativeLyricsHttp.fastClient, req, "Bini/lrc.red") }
                .getOrNull()
                ?.trim()
            if (!body.isNullOrBlank() && (body.contains("<tt", ignoreCase = true) || body.contains("[0"))) {
                return body
            }
        }
        if (!hit.isrc.isNullOrBlank()) {
            return fetchLrcRedByIsrc(hit.isrc)
        }
        return null
    }

    private fun queryBini(title: String, artist: String, album: String?, durationSec: Int): String? {
        val hits = fetchBiniRawHits(title, artist, album, durationSec)
        for (hit in hits.filter { it.score >= 0 }.take(2)) {
            val res = resolveHitLyrics(hit)
            if (!res.isNullOrBlank()) return res
        }
        return null
    }

    private fun searchBiniCandidates(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): List<NativeSearchCandidate> {
        val hits = fetchBiniRawHits(title, artist, album, durationSec)
        return hits.take(3).mapNotNull { hit ->
            val raw = resolveHitLyrics(hit) ?: return@mapNotNull null
            NativeSearchCandidate(
                id = "bini_${hit.id}",
                title = hit.title,
                artist = hit.artist,
                album = hit.album ?: album,
                durationSeconds = if (hit.durationSec > 0) hit.durationSec else durationSec,
                provider = "BiniLyrics",
                syncedLyrics = raw,
            )
        }
    }

    private fun fetchBiniRawHits(title: String, artist: String, album: String?, durationSec: Int): List<HitMeta> {
        val urlBuilder = "https://lyrics-api.binimum.org/".toHttpUrlOrNull()?.newBuilder() ?: return emptyList()
        urlBuilder.addQueryParameter("title", NativeLyricsHttp.cleanQueryText(title))
        urlBuilder.addQueryParameter("artist", NativeLyricsHttp.primaryArtist(artist))
        if (!album.isNullOrBlank()) urlBuilder.addQueryParameter("album", album)
        if (durationSec > 0) urlBuilder.addQueryParameter("duration", durationSec.toString())

        val req = Request.Builder().url(urlBuilder.build()).header("User-Agent", NativeLyricsHttp.USER_AGENT).build()
        val body = NativeLyricsHttp.executeOrThrow(NativeLyricsHttp.fastClient, req, "BiniLyrics") ?: return emptyList()
        return parseBiniHits(body, title, artist, durationSec)
    }

    private fun queryLrcRed(title: String, artist: String, durationSec: Int): String? {
        val hits = fetchLrcRedRawHits(title, artist, durationSec)
        for (hit in hits.filter { it.score >= 0 }.take(2)) {
            val res = resolveHitLyrics(hit)
            if (!res.isNullOrBlank()) return res
        }
        return null
    }

    private fun searchLrcRedCandidates(
        title: String,
        artist: String,
        durationSec: Int,
    ): List<NativeSearchCandidate> {
        val hits = fetchLrcRedRawHits(title, artist, durationSec)
        return hits.take(3).mapNotNull { hit ->
            val raw = resolveHitLyrics(hit) ?: return@mapNotNull null
            NativeSearchCandidate(
                id = "lrcred_${hit.id}",
                title = hit.title,
                artist = hit.artist,
                album = hit.album,
                durationSeconds = if (hit.durationSec > 0) hit.durationSec else durationSec,
                provider = "lrc.red",
                syncedLyrics = raw,
            )
        }
    }

    private fun fetchLrcRedRawHits(title: String, artist: String, durationSec: Int): List<HitMeta> {
        val q = "${NativeLyricsHttp.cleanQueryText(title)} ${NativeLyricsHttp.primaryArtist(artist)}".trim()
        val searchUrl = "https://lrc.red/search.json?q=${NativeLyricsHttp.urlEncode(q)}"
        val req = Request.Builder().url(searchUrl).header("User-Agent", NativeLyricsHttp.USER_AGENT).build()
        val body = NativeLyricsHttp.executeOrThrow(NativeLyricsHttp.fastClient, req, "lrc.red") ?: return emptyList()
        return parseLrcRedHits(body, title, artist, durationSec)
    }

    private fun fetchLrcRedByIsrc(isrc: String): String? {
        val urls = listOf(
            "https://lrc.red/s/$isrc.ttml",
            "https://lrc.red/api/lyrics?id=$isrc&type=isrc",
        )
        for (url in urls) {
            val res = runCatching {
                val req = Request.Builder().url(url).header("User-Agent", NativeLyricsHttp.USER_AGENT).build()
                NativeLyricsHttp.executeOrThrow(NativeLyricsHttp.fastClient, req, "lrc.red")?.trim()
            }.getOrNull()
            if (!res.isNullOrBlank() && (res.contains("<tt", ignoreCase = true) || res.contains("[0"))) {
                return res
            }
        }
        return null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4b. LrcAPI (api.lrc.cx - Apple Word TTML + KuGou / NetEase Synced LRC)
// ─────────────────────────────────────────────────────────────────────────────
internal object LrcApiClient {
    internal data class LrcApiItem(
        val id: String,
        val title: String,
        val artist: String,
        val album: String?,
        val durationSec: Int,
        val ttml: String?,
        val lrc: String?,
        val hash: String?,
        val score: Int,
    )

    private const val CACHE_TTL_MS = 30_000L
    private const val MAX_CACHE = 24
    private data class CachedQuery(val items: List<LrcApiItem>, val expiresAtMs: Long)
    private val cacheLock = Any()
    private val queryCache = LinkedHashMap<String, CachedQuery>(MAX_CACHE, 0.75f, true)

    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): String? = withContext(Dispatchers.IO) {
        val items = fetchScoredItems(title, artist, durationSec)
        val best = items.firstOrNull { it.score >= 0 && (!it.ttml.isNullOrBlank() || !it.lrc.isNullOrBlank()) }
            ?: return@withContext null
        best.ttml?.takeIf { it.contains("<tt", ignoreCase = true) } ?: best.lrc
    }

    suspend fun searchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): List<NativeSearchCandidate> = withContext(Dispatchers.IO) {
        val items = fetchScoredItems(title, artist, durationSec)
        val out = ArrayList<NativeSearchCandidate>()
        for (item in items.take(6)) {
            if (!item.ttml.isNullOrBlank() && item.ttml.contains("<tt", ignoreCase = true)) {
                out += NativeSearchCandidate(
                    id = "lrcapi_ttml_${item.id}",
                    title = item.title,
                    artist = item.artist,
                    album = item.album ?: album,
                    durationSeconds = if (item.durationSec > 0) item.durationSec else durationSec,
                    provider = "LrcAPI (TTML)",
                    syncedLyrics = item.ttml,
                )
            }
            if (!item.lrc.isNullOrBlank()) {
                val isSynced = Regex("""\[\d{1,3}:\d{2}[.:]\d{2,3}]""").containsMatchIn(item.lrc)
                out += NativeSearchCandidate(
                    id = "lrcapi_lrc_${item.id}",
                    title = item.title,
                    artist = item.artist,
                    album = item.album ?: album,
                    durationSeconds = if (item.durationSec > 0) item.durationSec else durationSec,
                    provider = if (!item.hash.isNullOrBlank()) "LrcAPI (KuGou/NetEase)" else "LrcAPI",
                    syncedLyrics = if (isSynced) item.lrc else null,
                    plainLyrics = if (!isSynced) item.lrc else null,
                )
            }
        }
        out
    }

    /**
     * Provides KuGou / NetEase-derived synced LRC via LrcAPI when direct China KuGou CDN is geo-blocked.
     */
    fun getKugouFallbackLyrics(title: String, artist: String, durationSec: Int): String? {
        val items = fetchScoredItems(title, artist, durationSec)
        return items
            .filter { it.score >= 0 && !it.lrc.isNullOrBlank() }
            .sortedByDescending { item ->
                val isSynced = Regex("""\[\d{1,3}:\d{2}[.:]\d{2,3}]""").containsMatchIn(item.lrc!!)
                item.score + (if (!item.hash.isNullOrBlank()) 40 else 0) + (if (isSynced) 30 else 0)
            }
            .firstOrNull()
            ?.lrc
    }

    fun searchKugouFallback(title: String, artist: String, album: String?, durationSec: Int): List<NativeSearchCandidate> {
        val items = fetchScoredItems(title, artist, durationSec)
        return items
            .filter { !it.lrc.isNullOrBlank() }
            .sortedByDescending { item -> item.score + if (!item.hash.isNullOrBlank()) 40 else 0 }
            .take(4)
            .map { item ->
                val raw = item.lrc!!
                val isSynced = Regex("""\[\d{1,3}:\d{2}[.:]\d{2,3}]""").containsMatchIn(raw)
                NativeSearchCandidate(
                    id = "kugou_lrcapi_${item.id}",
                    title = item.title,
                    artist = item.artist,
                    album = item.album ?: album,
                    durationSeconds = if (item.durationSec > 0) item.durationSec else durationSec,
                    provider = "KuGou (via LrcAPI)",
                    syncedLyrics = if (isSynced) raw else null,
                    plainLyrics = if (!isSynced) raw else null,
                )
            }
    }

    private fun fetchScoredItems(title: String, artist: String, durationSec: Int): List<LrcApiItem> {
        val cleanTitle = NativeLyricsHttp.cleanQueryText(title)
        val cleanArtist = NativeLyricsHttp.primaryArtist(artist)
        val cacheKey = "${cleanTitle.lowercase(Locale.ROOT)}|${cleanArtist.lowercase(Locale.ROOT)}"
        val now = System.currentTimeMillis()
        synchronized(cacheLock) {
            val cached = queryCache[cacheKey]
            if (cached != null && now < cached.expiresAtMs) {
                return rescoreItems(cached.items, title, artist, durationSec)
            }
        }

        val builder = "https://api.lrc.cx/jsonapi".toHttpUrlOrNull()?.newBuilder() ?: return emptyList()
        builder.addQueryParameter("title", cleanTitle)
        if (cleanArtist.isNotBlank()) builder.addQueryParameter("artist", cleanArtist)

        val req = Request.Builder()
            .url(builder.build())
            .header("User-Agent", NativeLyricsHttp.USER_AGENT)
            .header("Accept", "application/json")
            .build()

        val body = NativeLyricsHttp.executeOrThrow(NativeLyricsHttp.neteaseClient, req, "LrcAPI") ?: return emptyList()
        val parsed = parseJsonApiItems(body, title, artist, durationSec)
        synchronized(cacheLock) {
            if (queryCache.size >= MAX_CACHE) {
                val eldestKey = queryCache.keys.firstOrNull()
                if (eldestKey != null) queryCache.remove(eldestKey)
            }
            queryCache[cacheKey] = CachedQuery(parsed, System.currentTimeMillis() + CACHE_TTL_MS)
        }
        return parsed
    }

    private fun rescoreItems(items: List<LrcApiItem>, title: String, artist: String, durationSec: Int): List<LrcApiItem> =
        items.map { item ->
            item.copy(score = computeItemScore(item.title, item.artist, item.durationSec, item.ttml, item.lrc, title, artist, durationSec))
        }.sortedByDescending { it.score }

    internal fun parseJsonApiItems(
        body: String,
        targetTitle: String,
        targetArtist: String,
        targetDurationSec: Int,
    ): List<LrcApiItem> = runCatching {
        val trimmed = body.trim()
        if (!trimmed.startsWith("[")) return@runCatching emptyList()
        val arr = JSONArray(trimmed)
        val out = ArrayList<LrcApiItem>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val ttml = obj.optString("lrc_ttml").takeIf { it.isNotBlank() && it != "null" && it.contains("<tt", ignoreCase = true) }
            val lrc = obj.optString("lrc").takeIf { it.isNotBlank() && it != "null" }
            if (ttml == null && lrc == null) continue

            val cTitle = obj.optString("title", targetTitle).ifBlank { targetTitle }
            val cArtist = obj.optString("artist", targetArtist).ifBlank { targetArtist }
            val cAlbum = obj.optString("album").takeIf { it.isNotBlank() && it != "null" }
            val cDur = obj.optDouble("duration", 0.0).let { if (it.isFinite() && it > 0.0) it.toInt() else 0 }
            val hash = obj.optString("hash").takeIf { it.isNotBlank() && it != "null" }
            val id = obj.optString("id").ifBlank { i.toString() }

            out += LrcApiItem(
                id = id,
                title = cTitle,
                artist = cArtist,
                album = cAlbum,
                durationSec = cDur,
                ttml = ttml,
                lrc = lrc,
                hash = hash,
                score = computeItemScore(cTitle, cArtist, cDur, ttml, lrc, targetTitle, targetArtist, targetDurationSec),
            )
        }
        out.sortedByDescending { it.score }
    }.getOrDefault(emptyList())

    private fun computeItemScore(
        cTitle: String,
        cArtist: String,
        cDur: Int,
        ttml: String?,
        lrc: String?,
        targetTitle: String,
        targetArtist: String,
        targetDurationSec: Int,
    ): Int {
        val base = NativeLyricsHttp.scoreCandidate(cTitle, cArtist, cDur, targetTitle, targetArtist, targetDurationSec)
        val syncBonus = when {
            !ttml.isNullOrBlank() -> 60
            !lrc.isNullOrBlank() && Regex("""\[\d{1,3}:\d{2}[.:]\d{2,3}]""").containsMatchIn(lrc) -> 25
            else -> 0
        }
        return base + syncBonus
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. NetEase Cloud Music (網易雲音楽 - YRC Word-Sync + LRC + Translation)
// ─────────────────────────────────────────────────────────────────────────────
internal object NetEaseLyricsClient {
    private const val FAKE_CN_IP = "116.25.146.177"
    private const val NETEASE_COOKIE = "os=pc; appver=2.9.7"

    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): String? = withContext(Dispatchers.IO) {
        val songs = searchSongs(title, artist, durationSec)
        for (song in songs.take(2)) {
            if (song.score < 0) continue
            val lrc = fetchLyricsBySongId(song.id)
            if (!lrc.isNullOrBlank()) return@withContext lrc
        }
        null
    }

    suspend fun searchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): List<NativeSearchCandidate> = coroutineScope {
        val songs = withContext(Dispatchers.IO) { searchSongs(title, artist, durationSec) }
        songs.take(4).map { song ->
            async(Dispatchers.IO) {
                val lrc = runCatching { fetchLyricsBySongId(song.id) }.getOrNull() ?: return@async null
                NativeSearchCandidate(
                    id = "netease_${song.id}",
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    durationSeconds = (song.durationMs / 1000).toInt(),
                    provider = "NetEase",
                    syncedLyrics = lrc,
                )
            }
        }.awaitAll().filterNotNull()
    }

    private data class NetEaseSong(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String?,
        val durationMs: Long,
        val score: Int = 0,
    )

    private fun searchSongs(title: String, artist: String, durationSec: Int): List<NetEaseSong> {
        val cleanTitle = NativeLyricsHttp.cleanQueryText(title)
        val cleanArtist = NativeLyricsHttp.primaryArtist(artist)
        val primaryKeyword = "$cleanTitle $cleanArtist".trim()
        val firstPass = querySearchEndpoints(primaryKeyword, title, artist, durationSec)
        if (firstPass.any { it.score >= 40 }) {
            return firstPass
        }
        // Fallback: NetEase often stores Simplified Chinese artist names (e.g. 高桥洋子 / 米津玄师);
        // searching by title alone + duration scoring recovers those tracks reliably.
        if (cleanArtist.isNotBlank() && cleanTitle.isNotBlank()) {
            val secondPass = runCatching { querySearchEndpoints(cleanTitle, title, artist, durationSec) }
                .getOrDefault(emptyList())
            if (secondPass.isNotEmpty()) {
                return (firstPass + secondPass)
                    .distinctBy { it.id }
                    .sortedByDescending { it.score }
            }
        }
        return firstPass
    }

    private fun querySearchEndpoints(
        keyword: String,
        targetTitle: String,
        targetArtist: String,
        targetDurationSec: Int,
    ): List<NetEaseSong> {
        if (keyword.isBlank()) return emptyList()
        val formBody = FormBody.Builder()
            .add("s", keyword)
            .add("type", "1")
            .add("offset", "0")
            .add("limit", "8")
            .build()

        val requests = listOf(
            Request.Builder()
                .url("https://music.163.com/api/cloudsearch/pc")
                .post(formBody)
                .header("User-Agent", NativeLyricsHttp.USER_AGENT)
                .header("Referer", "https://music.163.com/")
                .header("X-Real-IP", FAKE_CN_IP)
                .header("Cookie", NETEASE_COOKIE)
                .build(),
            Request.Builder()
                .url("https://interface.music.163.com/api/cloudsearch/pc")
                .post(formBody)
                .header("User-Agent", NativeLyricsHttp.USER_AGENT)
                .header("Referer", "https://music.163.com/")
                .header("X-Real-IP", FAKE_CN_IP)
                .header("Cookie", NETEASE_COOKIE)
                .build(),
            Request.Builder()
                .url("https://music.163.com/api/search/get?s=${NativeLyricsHttp.urlEncode(keyword)}&type=1&limit=8")
                .get()
                .header("User-Agent", NativeLyricsHttp.USER_AGENT)
                .header("Referer", "https://music.163.com/")
                .header("X-Real-IP", FAKE_CN_IP)
                .header("Cookie", NETEASE_COOKIE)
                .build(),
        )

        var lastFailure: ProviderFailureException? = null
        for (req in requests) {
            val body = try {
                NativeLyricsHttp.executeOrThrow(NativeLyricsHttp.neteaseClient, req, "NetEase")
            } catch (e: ProviderFailureException) {
                lastFailure = e
                continue
            } ?: continue

            val json = runCatching { JSONObject(body) }.getOrNull() ?: continue
            val code = json.optInt("code", 200)
            if (code in setOf(-460, -462, 400, 406)) {
                lastFailure = ProviderFailureException(code, "NetEase blocked ($code)")
                continue
            }
            val songsArr = json.optJSONObject("result")?.optJSONArray("songs") ?: return emptyList()
            val list = ArrayList<NetEaseSong>(songsArr.length())
            for (i in 0 until songsArr.length()) {
                val s = songsArr.optJSONObject(i) ?: continue
                val id = s.optLong("id", 0L)
                if (id <= 0L) continue
                val arArr = s.optJSONArray("ar") ?: s.optJSONArray("artists")
                val artistNames = buildString {
                    if (arArr != null) {
                        for (j in 0 until arArr.length()) {
                            val name = arArr.optJSONObject(j)?.optString("name").orEmpty()
                            if (name.isNotBlank()) {
                                if (isNotEmpty()) append(", ")
                                append(name)
                            }
                        }
                    }
                }
                val sTitle = s.optString("name")
                val albumName = (s.optJSONObject("al") ?: s.optJSONObject("album"))?.optString("name")
                val dt = s.optLong("dt", s.optLong("duration", 0L))
                val cDurSec = (dt / 1000L).toInt()
                val score = NativeLyricsHttp.scoreCandidate(
                    candTitle = sTitle,
                    candArtist = artistNames,
                    candDurationSec = cDurSec,
                    targetTitle = targetTitle,
                    targetArtist = targetArtist,
                    targetDurationSec = targetDurationSec,
                )
                list += NetEaseSong(id, sTitle, artistNames, albumName, dt, score)
            }
            return list.sortedByDescending { it.score }
        }
        if (lastFailure != null) throw lastFailure
        return emptyList()
    }

    private fun fetchLyricsBySongId(songId: Long): String? {
        val urls = listOf(
            "https://music.163.com/api/song/lyric/v1?id=$songId&lv=1&kv=1&tv=-1&yv=1",
            "https://interface.music.163.com/api/song/lyric/v1?id=$songId&lv=1&kv=1&tv=-1&yv=1",
        )
        var lastFailure: ProviderFailureException? = null
        for (url in urls) {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", NativeLyricsHttp.USER_AGENT)
                .header("Referer", "https://music.163.com/")
                .header("X-Real-IP", FAKE_CN_IP)
                .header("Cookie", NETEASE_COOKIE)
                .build()

            val body = try {
                NativeLyricsHttp.executeOrThrow(NativeLyricsHttp.neteaseClient, req, "NetEase")
            } catch (e: ProviderFailureException) {
                lastFailure = e
                continue
            } ?: continue

            val json = runCatching { JSONObject(body) }.getOrNull() ?: continue
            val code = json.optInt("code", 200)
            if (code in setOf(-460, -462, 400, 406)) {
                lastFailure = ProviderFailureException(code, "NetEase lyric blocked ($code)")
                continue
            }
            val yrcRaw = json.optJSONObject("yrc")?.optString("lyric")?.takeIf { it.isNotBlank() }
            val lrcRaw = cleanNetEaseLrc(json.optJSONObject("lrc")?.optString("lyric"))
            val transRaw = cleanNetEaseLrc(json.optJSONObject("tlyric")?.optString("lyric"))

            val mainLrc = if (!yrcRaw.isNullOrBlank()) {
                convertYrcToEnhancedLrc(yrcRaw) ?: lrcRaw
            } else {
                lrcRaw
            } ?: return null

            return NativeLyricsHttp.mergeLrcWithTranslation(mainLrc, transRaw)
        }
        if (lastFailure != null) throw lastFailure
        return null
    }

    /** Strips NetEase's inline `{ "t": 0, "c": [...] }` credit JSON lines while keeping LRC/plain lines. */
    internal fun cleanNetEaseLrc(rawLrc: String?): String? {
        if (rawLrc.isNullOrBlank()) return null
        return rawLrc.lineSequence()
            .filterNot { it.trimStart().startsWith("{") }
            .joinToString("\n")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    private val YRC_LINE_REGEX = Regex("""^\[(\d+),(\d+)](.+)$""")
    private val YRC_WORD_REGEX = Regex("""\((\d+),(\d+),\d+\)([^(]*)""")

    internal fun convertYrcToEnhancedLrc(yrc: String): String? {
        val sb = StringBuilder()
        var count = 0
        for (rawLine in yrc.lineSequence()) {
            val trimmed = rawLine.trim()
            if (trimmed.startsWith("{")) continue // JSON metadata header line in YRC
            val lineMatch = YRC_LINE_REGEX.matchEntire(trimmed) ?: continue
            val lineStart = lineMatch.groupValues[1].toLongOrNull() ?: continue
            val lineDur = lineMatch.groupValues[2].toLongOrNull() ?: 0L
            val content = lineMatch.groupValues[3]

            val words = YRC_WORD_REGEX.findAll(content).toList()
            if (words.isEmpty()) continue

            sb.append(NativeLyricsHttp.formatLineStamp(lineStart))
            var lastEnd = lineStart + lineDur
            for (w in words) {
                val wStart = w.groupValues[1].toLongOrNull() ?: lineStart
                val wDur = w.groupValues[2].toLongOrNull() ?: 150L
                val wText = w.groupValues[3]
                sb.append(NativeLyricsHttp.formatWordStamp(wStart)).append(wText)
                lastEnd = maxOf(lastEnd, wStart + wDur)
            }
            sb.append(NativeLyricsHttp.formatWordStamp(lastEnd)).append('\n')
            count++
        }
        return if (count > 0) sb.toString().trimEnd() else null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. QQ Music (QQ音楽 - Portato QQ Word-Sync + Smartbox & Base64 LRC + Translation)
// ─────────────────────────────────────────────────────────────────────────────
internal object QqMusicLyricsClient {
    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): String? = coroutineScope {
        // Race Portato QQ (word-synced TTML/LRC) and direct QQ Music API
        val portatoDeferred = async(Dispatchers.IO) {
            runCatching {
                BetterLyricsClient.queryEndpoint("https://lyrics-api.boidu.dev/qq/getLyrics", title, artist, album, durationSec)
            }
        }
        val directDeferred = async(Dispatchers.IO) {
            runCatching {
                val tracks = searchSongs(title, artist)
                tracks.firstOrNull()?.let { fetchLyricsByMid(it.songMid) }
            }
        }

        val portatoRes = portatoDeferred.await()
        val portato = portatoRes.getOrNull()
        if (!portato.isNullOrBlank()) {
            directDeferred.cancel()
            return@coroutineScope portato
        }
        val directRes = directDeferred.await()
        val direct = directRes.getOrNull()
        if (!direct.isNullOrBlank()) return@coroutineScope direct

        val failure = portatoRes.exceptionOrNull() as? ProviderFailureException
            ?: directRes.exceptionOrNull() as? ProviderFailureException
        if (failure != null) throw failure
        null
    }

    suspend fun searchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): List<NativeSearchCandidate> = coroutineScope {
        val tracks = withContext(Dispatchers.IO) { searchSongs(title, artist) }
        val jobs = tracks.take(4).map { track ->
            async(Dispatchers.IO) {
                val lrc = fetchLyricsByMid(track.songMid) ?: return@async null
                NativeSearchCandidate(
                    id = "qqmusic_${track.songMid}",
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationSeconds = durationSec,
                    provider = "QQ Music",
                    syncedLyrics = lrc,
                )
            }
        }
        jobs.awaitAll().filterNotNull()
    }

    private data class QqSong(
        val songMid: String,
        val title: String,
        val artist: String,
        val album: String? = null,
    )

    private fun searchSongs(title: String, artist: String): List<QqSong> {
        val cleanTitle = NativeLyricsHttp.cleanQueryText(title)
        val cleanArtist = NativeLyricsHttp.primaryArtist(artist)
        val key = "$cleanTitle $cleanArtist".trim()
        val url = "https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg?format=json&inCharset=utf-8&outCharset=utf-8&key=${NativeLyricsHttp.urlEncode(key)}"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", NativeLyricsHttp.USER_AGENT)
            .header("Referer", "https://y.qq.com/")
            .build()

        val body = NativeLyricsHttp.executeOrThrow(NativeLyricsHttp.directKugouClient, req, "QQ Music")
            ?: return emptyList()
        return runCatching {
            val itemlist = JSONObject(body)
                .optJSONObject("data")
                ?.optJSONObject("song")
                ?.optJSONArray("itemlist") ?: return@runCatching emptyList()

            val list = ArrayList<QqSong>(itemlist.length())
            for (i in 0 until itemlist.length()) {
                val item = itemlist.optJSONObject(i) ?: continue
                val mid = item.optString("mid")
                if (mid.isBlank()) continue
                list += QqSong(
                    songMid = mid,
                    title = item.optString("name", cleanTitle),
                    artist = item.optString("singer", cleanArtist),
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun fetchLyricsByMid(songMid: String): String? = runCatching {
        val url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=${NativeLyricsHttp.urlEncode(songMid)}&format=json&nobase64=0&g_tk=5381"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", NativeLyricsHttp.USER_AGENT)
            .header("Referer", "https://y.qq.com/")
            .build()

        NativeLyricsHttp.fastClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            var body = resp.body?.string()?.trim() ?: return null
            if (body.startsWith("MusicJsonCallback(") && body.endsWith(")")) {
                body = body.removePrefix("MusicJsonCallback(").removeSuffix(")")
            }
            val json = JSONObject(body)
            val lyricB64 = json.optString("lyric")
            if (lyricB64.isBlank()) return null
            val mainLrc = String(Base64.decode(lyricB64, Base64.DEFAULT), Charsets.UTF_8).trim()
            if (mainLrc.isBlank()) return null

            val transB64 = json.optString("trans")
            val transLrc = if (transB64.isNotBlank()) {
                runCatching { String(Base64.decode(transB64, Base64.DEFAULT), Charsets.UTF_8).trim() }.getOrNull()
            } else null

            NativeLyricsHttp.mergeLrcWithTranslation(mainLrc, transLrc)
        }
    }.getOrNull()
}

// ─────────────────────────────────────────────────────────────────────────────
// 7. KuGou Music (酷狗音楽 - KRC Decrypt Word-Sync + LrcAPI Overseas Fallback)
// ─────────────────────────────────────────────────────────────────────────────
internal object KugouLyricsClient {
    private val KRC_XOR_KEY = intArrayOf(
        64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105
    )

    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): String? = withContext(Dispatchers.IO) {
        var directFailure: ProviderFailureException? = null
        val candidates = try {
            searchCandidates(title, artist, durationSec)
        } catch (e: ProviderFailureException) {
            directFailure = e
            emptyList()
        }
        for (cand in candidates.take(2)) {
            val lrc = runCatching { downloadKrcOrLrc(cand.id, cand.accessKey) }.getOrNull()
            if (!lrc.isNullOrBlank()) return@withContext lrc
        }
        // Direct KuGou CDN is geo-blocked outside mainland China; fall back to LrcAPI's KuGou/NetEase LRC index.
        val fallback = try {
            LrcApiClient.getKugouFallbackLyrics(title, artist, durationSec)
        } catch (e: ProviderFailureException) {
            if (directFailure != null) throw directFailure else throw e
        }
        if (!fallback.isNullOrBlank()) return@withContext fallback
        if (directFailure != null) throw directFailure
        null
    }

    suspend fun searchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): List<NativeSearchCandidate> = coroutineScope {
        var directFailure: ProviderFailureException? = null
        val candidates = withContext(Dispatchers.IO) {
            try {
                searchCandidates(title, artist, durationSec)
            } catch (e: ProviderFailureException) {
                directFailure = e
                emptyList()
            }
        }
        val directResults = candidates.take(4).map { cand ->
            async(Dispatchers.IO) {
                val lrc = runCatching { downloadKrcOrLrc(cand.id, cand.accessKey) }.getOrNull() ?: return@async null
                NativeSearchCandidate(
                    id = "kugou_${cand.id}",
                    title = cand.song,
                    artist = cand.singer,
                    album = album,
                    durationSeconds = (cand.durationMs / 1000).toInt(),
                    provider = "KuGou",
                    syncedLyrics = lrc,
                )
            }
        }.awaitAll().filterNotNull()
        if (directResults.isNotEmpty()) return@coroutineScope directResults

        val fallback = withContext(Dispatchers.IO) {
            try {
                LrcApiClient.searchKugouFallback(title, artist, album, durationSec)
            } catch (e: ProviderFailureException) {
                if (directFailure != null) throw directFailure else throw e
            }
        }
        if (fallback.isNotEmpty()) return@coroutineScope fallback
        if (directFailure != null) throw directFailure
        emptyList()
    }

    private data class KugouCandidate(
        val id: String,
        val accessKey: String,
        val song: String,
        val singer: String,
        val durationMs: Long,
    )

    private fun searchCandidates(title: String, artist: String, durationSec: Int): List<KugouCandidate> {
        val cleanTitle = NativeLyricsHttp.cleanQueryText(title)
        val cleanArtist = NativeLyricsHttp.primaryArtist(artist)
        val keyword = if (cleanArtist.isNotBlank()) "$cleanArtist - $cleanTitle" else cleanTitle
        val durMs = if (durationSec > 0) durationSec * 1000 else 200000
        val url = "https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=${NativeLyricsHttp.urlEncode(keyword)}&duration=$durMs&hash="
        val req = Request.Builder().url(url).header("User-Agent", NativeLyricsHttp.USER_AGENT).build()

        val body = NativeLyricsHttp.executeOrThrow(NativeLyricsHttp.directKugouClient, req, "KuGou") ?: return emptyList()
        return runCatching {
            val arr = JSONObject(body).optJSONArray("candidates") ?: return emptyList()
            val list = ArrayList<KugouCandidate>(arr.length())
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val id = c.optString("id")
                val key = c.optString("accesskey")
                if (id.isBlank() || key.isBlank()) continue
                list += KugouCandidate(
                    id = id,
                    accessKey = key,
                    song = c.optString("song", cleanTitle),
                    singer = c.optString("singer", cleanArtist),
                    durationMs = c.optLong("duration", durMs.toLong()),
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun downloadKrcOrLrc(id: String, accessKey: String): String? {
        // 1. Try KRC (Word-Synced)
        val krcContent = runCatching {
            val url = "https://lyrics.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=krc&charset=utf8"
            val req = Request.Builder().url(url).header("User-Agent", NativeLyricsHttp.USER_AGENT).build()
            val body = NativeLyricsHttp.executeOrThrow(NativeLyricsHttp.directKugouClient, req, "KuGou") ?: return@runCatching null
            val b64 = JSONObject(body).optString("content")
            if (b64.isBlank()) return@runCatching null
            val rawBytes = Base64.decode(b64, Base64.DEFAULT)
            val decrypted = decryptKrcBytes(rawBytes) ?: return@runCatching null
            convertKrcToEnhancedLrc(decrypted)
        }.getOrNull()

        if (!krcContent.isNullOrBlank()) return krcContent

        // 2. Fallback to LRC (Line-Synced)
        return runCatching {
            val url = "https://lyrics.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=lrc&charset=utf8"
            val req = Request.Builder().url(url).header("User-Agent", NativeLyricsHttp.USER_AGENT).build()
            val body = NativeLyricsHttp.executeOrThrow(NativeLyricsHttp.directKugouClient, req, "KuGou") ?: return@runCatching null
            val b64 = JSONObject(body).optString("content")
            if (b64.isBlank()) return@runCatching null
            String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    internal fun decryptKrcBytes(raw: ByteArray): String? = runCatching {
        if (raw.size <= 4) return null
        if (raw[0] != 'k'.code.toByte() || raw[1] != 'r'.code.toByte() || raw[2] != 'c'.code.toByte() || raw[3] != '1'.code.toByte()) {
            return null
        }
        val payload = ByteArray(raw.size - 4)
        for (i in payload.indices) {
            payload[i] = (raw[i + 4].toInt() xor KRC_XOR_KEY[i % 16]).toByte()
        }
        val inflater = Inflater()
        inflater.setInput(payload)
        val out = ByteArrayOutputStream(payload.size * 3)
        val buf = ByteArray(4096)
        while (!inflater.finished()) {
            val count = inflater.inflate(buf)
            if (count <= 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) break
            } else {
                out.write(buf, 0, count)
            }
        }
        inflater.end()
        out.toString("UTF-8")
    }.getOrNull()

    private val KRC_LINE_REGEX = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val KRC_WORD_REGEX = Regex("""<(\d+),(\d+),\d+>([^<]*)""")

    internal fun convertKrcToEnhancedLrc(krcText: String): String? {
        val sb = StringBuilder()
        var lineCount = 0
        for (rawLine in krcText.lineSequence()) {
            val trimmed = rawLine.trim()
            val m = KRC_LINE_REGEX.matchEntire(trimmed) ?: continue
            val lineStart = m.groupValues[1].toLongOrNull() ?: continue
            val lineDur = m.groupValues[2].toLongOrNull() ?: 0L
            val content = m.groupValues[3]

            val words = KRC_WORD_REGEX.findAll(content).toList()
            sb.append(NativeLyricsHttp.formatLineStamp(lineStart))
            if (words.isNotEmpty()) {
                var lastEnd = lineStart + lineDur
                for (w in words) {
                    val wOffset = w.groupValues[1].toLongOrNull() ?: 0L
                    val wDur = w.groupValues[2].toLongOrNull() ?: 150L
                    val wText = w.groupValues[3]
                    val wStart = lineStart + wOffset
                    sb.append(NativeLyricsHttp.formatWordStamp(wStart)).append(wText)
                    lastEnd = maxOf(lastEnd, wStart + wDur)
                }
                sb.append(NativeLyricsHttp.formatWordStamp(lastEnd))
            } else {
                sb.append(content)
            }
            sb.append('\n')
            lineCount++
        }
        return if (lineCount > 0) sb.toString().trimEnd() else null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 8. PetitLyrics (プチリリ - p1 SDK API with WSY Character Sync + Full Text Fallback)
// ─────────────────────────────────────────────────────────────────────────────
internal object PetitLyricsClient {
    private const val SDK_URL = "https://p1.petitlyrics.com/api/GetPetitLyricsData.php"
    private const val CLIENT_APP_ID = "p1110417"

    private data class PetitMeta(
        val id: String,
        val title: String,
        val artist: String,
        val album: String?,
        val durationSec: Int,
        val lyricsType: Int,
        val availableLyricsType: Int,
        val rawLyricsData: String,
        val score: Int,
    )

    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): String? = withContext(Dispatchers.IO) {
        val cleanTitle = NativeLyricsHttp.cleanQueryText(title)
        val cleanArtist = NativeLyricsHttp.primaryArtist(artist)
        val hits = querySdkRaw(
            form = FormBody.Builder()
                .add("key_title", cleanTitle)
                .add("key_artist", cleanArtist)
                .apply { if (!album.isNullOrBlank()) add("key_album", album) }
                .add("clientAppId", CLIENT_APP_ID)
                .add("terminalType", "10")
                .add("lyricsType", "3")
                .add("maxCount", "5")
                .build(),
            targetTitle = cleanTitle,
            targetArtist = cleanArtist,
            targetDurationSec = durationSec,
            throwOnNetworkError = true,
        )
        if (hits.isEmpty()) return@withContext null

        val best = hits.maxByOrNull { it.score } ?: return@withContext null
        resolveHitLyrics(best)?.bestRaw
    }

    suspend fun searchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): List<NativeSearchCandidate> = coroutineScope {
        val cleanTitle = NativeLyricsHttp.cleanQueryText(title)
        val cleanArtist = NativeLyricsHttp.primaryArtist(artist)
        val hits = withContext(Dispatchers.IO) {
            querySdkRaw(
                form = FormBody.Builder()
                    .add("key_title", cleanTitle)
                    .add("key_artist", cleanArtist)
                    .apply { if (!album.isNullOrBlank()) add("key_album", album) }
                    .add("clientAppId", CLIENT_APP_ID)
                    .add("terminalType", "10")
                    .add("lyricsType", "3")
                    .add("maxCount", "5")
                    .build(),
                targetTitle = cleanTitle,
                targetArtist = cleanArtist,
                targetDurationSec = durationSec,
                throwOnNetworkError = true,
            )
        }
        hits.sortedByDescending { it.score }.take(3).map { hit ->
            async(Dispatchers.IO) { resolveHitLyrics(hit) }
        }.awaitAll().filterNotNull()
    }

    private fun resolveHitLyrics(hit: PetitMeta): NativeSearchCandidate? {
        // 1. Check inline lyricsData first when type=3 WSY or plain text type=1
        decodePayload(hit.rawLyricsData, hit.lyricsType)?.let { (synced, plain) ->
            return NativeSearchCandidate(
                id = "petitlyrics_${hit.id}",
                title = hit.title,
                artist = hit.artist,
                album = hit.album,
                durationSeconds = hit.durationSec,
                provider = "プチリリ (PetitLyrics)",
                syncedLyrics = synced,
                plainLyrics = plain,
            )
        }
        // 2. Fetch by key_lyricsId (WSY if availableLyricsType==3, otherwise full UTF-8 plain lyricsType=1)
        val wantedType = if (hit.availableLyricsType == 3 && hit.lyricsType != 3) 3 else 1
        val fetched = querySdkById(hit.id, wantedType, hit)
            ?: if (wantedType != 1) querySdkById(hit.id, 1, hit) else null
        return fetched
    }

    private fun querySdkById(lyricsId: String, lyricsType: Int, meta: PetitMeta): NativeSearchCandidate? {
        val hits = runCatching {
            querySdkRaw(
                form = FormBody.Builder()
                    .add("key_lyricsId", lyricsId)
                    .add("clientAppId", CLIENT_APP_ID)
                    .add("terminalType", "10")
                    .add("lyricsType", lyricsType.toString())
                    .add("maxCount", "1")
                    .build(),
                targetTitle = meta.title,
                targetArtist = meta.artist,
                targetDurationSec = meta.durationSec,
                throwOnNetworkError = false,
            )
        }.getOrNull().orEmpty()
        val first = hits.firstOrNull() ?: return null
        val (synced, plain) = decodePayload(first.rawLyricsData, first.lyricsType) ?: return null
        return NativeSearchCandidate(
            id = "petitlyrics_${first.id.ifBlank { lyricsId }}",
            title = first.title.ifBlank { meta.title },
            artist = first.artist.ifBlank { meta.artist },
            album = first.album ?: meta.album,
            durationSeconds = if (first.durationSec > 0) first.durationSec else meta.durationSec,
            provider = "プチリリ (PetitLyrics)",
            syncedLyrics = synced,
            plainLyrics = plain,
        )
    }

    private fun querySdkRaw(
        form: FormBody,
        targetTitle: String,
        targetArtist: String,
        targetDurationSec: Int,
        throwOnNetworkError: Boolean,
    ): List<PetitMeta> {
        val req = Request.Builder()
            .url(SDK_URL)
            .post(form)
            .header("User-Agent", "PetitLyricsAndroid/2.7")
            .build()
        val xml = if (throwOnNetworkError) {
            NativeLyricsHttp.executeOrThrow(NativeLyricsHttp.fastClient, req, "PetitLyrics") ?: return emptyList()
        } else {
            runCatching {
                NativeLyricsHttp.fastClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            }.getOrNull() ?: return emptyList()
        }
        return parsePetitMetaXml(xml, targetTitle, targetArtist, targetDurationSec)
    }

    private val SONG_BLOCK_REGEX = Regex("""<song>([\s\S]*?)</song>""")
    private val WSY_LINE_REGEX = Regex("""<line>([\s\S]*?)</line>""")
    private val WSY_WORD_REGEX = Regex("""<word>([\s\S]*?)</word>""")

    private fun extractTagRaw(block: String, tag: String): String =
        Regex("""<$tag>([\s\S]*?)</$tag>""").find(block)?.groupValues?.get(1).orEmpty()

    private fun extractTag(block: String, tag: String): String =
        WebScrapeHtmlUtils.decodeHtmlEntities(extractTagRaw(block, tag).trim())

    private fun parsePetitMetaXml(
        xml: String,
        targetTitle: String,
        targetArtist: String,
        targetDurationSec: Int,
    ): List<PetitMeta> {
        val out = ArrayList<PetitMeta>()
        for ((idx, m) in SONG_BLOCK_REGEX.findAll(xml).withIndex()) {
            val block = m.groupValues[1]
            val id = extractTag(block, "lyricsId").ifBlank { idx.toString() }
            val songTitle = extractTag(block, "title").ifBlank { targetTitle }
            val songArtist = extractTag(block, "artist").ifBlank { targetArtist }
            val songAlbum = extractTag(block, "album").takeIf { it.isNotBlank() }
            val durationMs = extractTag(block, "duration").toLongOrNull() ?: 0L
            val durationSec = if (durationMs > 0L) (durationMs / 1000L).toInt() else 0
            val lyricsType = extractTag(block, "lyricsType").toIntOrNull() ?: 1
            val availableType = extractTag(block, "availableLyricsType").toIntOrNull() ?: lyricsType
            val b64 = extractTag(block, "lyricsData")
            val decoded = if (b64.isNotBlank()) {
                runCatching {
                    String(java.util.Base64.getMimeDecoder().decode(b64), Charsets.UTF_8).trim()
                }.getOrNull().orEmpty()
            } else {
                ""
            }
            val baseScore = NativeLyricsHttp.scoreCandidate(
                candTitle = songTitle,
                candArtist = songArtist,
                candDurationSec = durationSec,
                targetTitle = targetTitle,
                targetArtist = targetArtist,
                targetDurationSec = targetDurationSec,
            )
            val syncBonus = if (availableType == 3) 40 else if (availableType == 2) 10 else 0
            out += PetitMeta(
                id = id,
                title = songTitle,
                artist = songArtist,
                album = songAlbum,
                durationSec = durationSec,
                lyricsType = lyricsType,
                availableLyricsType = availableType,
                rawLyricsData = decoded,
                score = baseScore + syncBonus,
            )
        }
        return out
    }

    private fun decodePayload(raw: String, lyricsType: Int): Pair<String?, String?>? {
        if (raw.isBlank()) return null
        // Binary LSY payloads start with "MHDROBJT" — skip cleanly so caller fetches lyricsType=1.
        if (raw.startsWith("MHDROBJT")) return null
        if (lyricsType == 3 || raw.contains("<wsy", ignoreCase = true)) {
            val enhanced = convertWsyToEnhancedLrc(raw) ?: return null
            return enhanced to null
        }
        val cleanPlain = raw.replace("\r\n", "\n").trim()
        if (cleanPlain.isBlank()) return null
        val isLrc = Regex("""\[\d{1,3}:\d{2}[.:]\d{2,3}]""").containsMatchIn(cleanPlain)
        return if (isLrc) cleanPlain to null else null to cleanPlain
    }

    /**
     * Parses PetitLyrics' official WSY XML format (base64-decoded `<wsy><line>...</line></wsy>`)
     * into Enhanced A2 LRC (`[mm:ss.xx]<mm:ss.xx>word...<mm:ss.xx>`), merging consecutive
     * Latin/ASCII letters into whole words while retaining per-character Japanese karaoke sync.
     */
    internal fun convertWsyToEnhancedLrc(wsyXml: String): String? {
        val sb = StringBuilder()
        var lineCount = 0

        for (lineMatch in WSY_LINE_REGEX.findAll(wsyXml)) {
            val lineBlock = lineMatch.groupValues[1]
            val lineString = extractTag(lineBlock, "linestring")
            if (lineString.isBlank()) continue

            data class RawWord(val startMs: Long, val endMs: Long, val text: String)
            val rawWords = ArrayList<RawWord>()
            for (wordMatch in WSY_WORD_REGEX.findAll(lineBlock)) {
                val wb = wordMatch.groupValues[1]
                val startMs = extractTag(wb, "starttime").toLongOrNull() ?: continue
                val endMs = extractTag(wb, "endtime").toLongOrNull() ?: startMs
                // Preserve interior spaces inside <wordstring> (do not trim).
                val wordText = WebScrapeHtmlUtils.decodeHtmlEntities(extractTagRaw(wb, "wordstring"))
                if (wordText.isEmpty()) continue
                rawWords += RawWord(startMs, maxOf(startMs, endMs), wordText)
            }
            if (rawWords.isEmpty()) continue

            // Merge consecutive ASCII/Latin word characters into whole words while emitting spaces cleanly.
            data class MergedToken(val startMs: Long, val endMs: Long, var text: String, var spaceAfter: Boolean = false)
            val tokens = ArrayList<MergedToken>(rawWords.size)
            for (rw in rawWords) {
                if (rw.text.isBlank()) {
                    if (tokens.isNotEmpty()) {
                        tokens[tokens.lastIndex].spaceAfter = true
                    }
                    continue
                }
                val isAsciiWordPiece = rw.text.all {
                    it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '\'' || it == '\u2019' || it == '-'
                }
                val prev = tokens.lastOrNull()
                if (isAsciiWordPiece && prev != null && !prev.spaceAfter && prev.text.all {
                        it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '\'' || it == '\u2019' || it == '-'
                    }
                ) {
                    prev.text += rw.text
                    tokens[tokens.lastIndex] = prev.copy(endMs = maxOf(prev.endMs, rw.endMs))
                } else {
                    tokens += MergedToken(rw.startMs, rw.endMs, rw.text)
                }
            }
            if (tokens.isEmpty()) continue

            val lineStartMs = tokens.first().startMs
            sb.append(NativeLyricsHttp.formatLineStamp(lineStartMs))
            var prevEnd = lineStartMs
            for (i in tokens.indices) {
                val t = tokens[i]
                val s = maxOf(t.startMs, prevEnd)
                sb.append(NativeLyricsHttp.formatWordStamp(s)).append(t.text)
                if (i != tokens.lastIndex && t.spaceAfter) sb.append(' ')
                prevEnd = maxOf(s, t.endMs)
            }
            sb.append(NativeLyricsHttp.formatWordStamp(prevEnd)).append('\n')
            lineCount++
        }
        return if (lineCount > 0) sb.toString().trimEnd() else null
    }

    internal fun parsePetitXml(xml: String, defaultTitle: String, defaultArtist: String): List<NativeSearchCandidate> {
        val out = mutableListOf<NativeSearchCandidate>()
        for (meta in parsePetitMetaXml(xml, defaultTitle, defaultArtist, 0)) {
            val (synced, plain) = decodePayload(meta.rawLyricsData, meta.lyricsType) ?: continue
            out += NativeSearchCandidate(
                id = "petitlyrics_${meta.id}",
                title = meta.title,
                artist = meta.artist,
                album = meta.album,
                durationSeconds = meta.durationSec,
                provider = "プチリリ (PetitLyrics)",
                syncedLyrics = synced,
                plainLyrics = plain,
            )
        }
        return out
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 9. Musixmatch (Desktop RichSync + Subtitles + PaxSenix Fallback)
// ─────────────────────────────────────────────────────────────────────────────
internal object MusixmatchLyricsClient {
    private const val MXM_BASE = "https://apic-desktop.musixmatch.com/ws/1.1"
    private val tokenMutex = Mutex()
    @Volatile
    private var cachedToken: String? = null
    @Volatile
    private var tokenExpiresAtMs: Long = 0L

    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): String? = coroutineScope {
        val paxDeferred = async(Dispatchers.IO) { runCatching { queryPaxMusixmatch(title, artist, durationSec) } }
        val desktopDeferred = async(Dispatchers.IO) { runCatching { queryDesktopMusixmatch(title, artist, durationSec) } }

        val paxRes = paxDeferred.await()
        val pax = paxRes.getOrNull()
        if (!pax.isNullOrBlank()) {
            desktopDeferred.cancel()
            return@coroutineScope pax
        }
        val desktopRes = desktopDeferred.await()
        val desktop = desktopRes.getOrNull()
        if (!desktop.isNullOrBlank()) return@coroutineScope desktop

        val failure = paxRes.exceptionOrNull() as? ProviderFailureException
            ?: desktopRes.exceptionOrNull() as? ProviderFailureException
        if (failure != null) throw failure
        null
    }

    suspend fun searchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): List<NativeSearchCandidate> = coroutineScope {
        val lrc = getLyrics(title, artist, album, durationSec) ?: return@coroutineScope emptyList()
        val isSynced = Regex("""\[\d{2}:\d{2}[.:]\d{2,3}]""").containsMatchIn(lrc)
        listOf(
            NativeSearchCandidate(
                id = "musixmatch_${title.hashCode()}",
                title = title,
                artist = artist,
                album = album,
                durationSeconds = durationSec,
                provider = "Musixmatch",
                syncedLyrics = if (isSynced) lrc else null,
                plainLyrics = if (!isSynced) lrc else null,
            )
        )
    }

    private fun queryPaxMusixmatch(title: String, artist: String, durationSec: Int): String? {
        val urlBuilder = "https://lyrics.paxsenix.org/musixmatch/lyrics".toHttpUrlOrNull()?.newBuilder() ?: return null
        urlBuilder.addQueryParameter("t", NativeLyricsHttp.cleanQueryText(title))
        urlBuilder.addQueryParameter("a", NativeLyricsHttp.primaryArtist(artist))
        if (durationSec > 0) urlBuilder.addQueryParameter("d", durationSec.toString())
        urlBuilder.addQueryParameter("type", "word")
        urlBuilder.addQueryParameter("format", "lrc")

        val req = Request.Builder().url(urlBuilder.build()).header("User-Agent", NativeLyricsHttp.USER_AGENT).build()
        val body = NativeLyricsHttp.executeOrThrow(NativeLyricsHttp.paxClient, req, "Musixmatch Pax")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        if (body.startsWith("[0")) return body
        return runCatching {
            val json = JSONObject(body)
            json.optString("synced").takeIf { it.isNotBlank() }
                ?: json.optString("lyrics").takeIf { it.isNotBlank() }
                ?: json.optString("lrc").takeIf { it.isNotBlank() }
                ?: json.optString("plain").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private suspend fun queryDesktopMusixmatch(title: String, artist: String, durationSec: Int): String? {
        val token = getOrFetchToken() ?: return null
        return runCatching {
            val cleanTitle = NativeLyricsHttp.cleanQueryText(title)
            val cleanArtist = NativeLyricsHttp.primaryArtist(artist)
            val urlBuilder = "$MXM_BASE/macro.subtitles.get".toHttpUrlOrNull()?.newBuilder() ?: return null
            urlBuilder.addQueryParameter("format", "json")
            urlBuilder.addQueryParameter("namespace", "lyrics_richsynched")
            urlBuilder.addQueryParameter("subtitle_format", "lrc")
            urlBuilder.addQueryParameter("app_id", "web-desktop-app-v1.0")
            urlBuilder.addQueryParameter("q_track", cleanTitle)
            urlBuilder.addQueryParameter("q_artist", cleanArtist)
            if (durationSec > 0) urlBuilder.addQueryParameter("q_duration", durationSec.toString())
            urlBuilder.addQueryParameter("usertoken", token)

            val req = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", NativeLyricsHttp.USER_AGENT)
                .header("Cookie", "x-mxm-token-guid=1")
                .build()

            NativeLyricsHttp.fastClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val calls = JSONObject(body)
                    .optJSONObject("message")
                    ?.optJSONObject("body")
                    ?.optJSONObject("macro_calls") ?: return null

                // 1. Check RichSync (Word-level)
                val richSyncBody = calls.optJSONObject("track.richsync.get")
                    ?.optJSONObject("message")
                    ?.optJSONObject("body")
                    ?.optJSONObject("richsync")
                    ?.optString("richsync_body")
                if (!richSyncBody.isNullOrBlank()) {
                    val enhanced = parseRichSyncJson(richSyncBody)
                    if (!enhanced.isNullOrBlank()) return enhanced
                }

                // 2. Check Subtitle (Line-level LRC)
                val subtitleBody = calls.optJSONObject("track.subtitles.get")
                    ?.optJSONObject("message")
                    ?.optJSONObject("body")
                    ?.optJSONArray("subtitle_list")
                    ?.optJSONObject(0)
                    ?.optJSONObject("subtitle")
                    ?.optString("subtitle_body")
                if (!subtitleBody.isNullOrBlank()) return subtitleBody.trim()

                // 3. Check Plain Lyrics
                calls.optJSONObject("track.lyrics.get")
                    ?.optJSONObject("message")
                    ?.optJSONObject("body")
                    ?.optJSONObject("lyrics")
                    ?.optString("lyrics_body")
                    ?.lineSequence()
                    ?.filterNot { it.contains("******* This Lyrics is NOT for Commercial use") }
                    ?.joinToString("\n")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    private fun parseRichSyncJson(rawJson: String): String? = runCatching {
        val arr = JSONArray(rawJson)
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val lineObj = arr.optJSONObject(i) ?: continue
            val tsSec = lineObj.optDouble("ts", -1.0)
            val teSec = lineObj.optDouble("te", tsSec + 2.0)
            if (tsSec < 0) continue
            val lineStartMs = (tsSec * 1000.0).toLong()
            val lineEndMs = (teSec * 1000.0).toLong()
            sb.append(NativeLyricsHttp.formatLineStamp(lineStartMs))
            val words = lineObj.optJSONArray("l")
            if (words != null && words.length() > 0) {
                for (j in 0 until words.length()) {
                    val w = words.optJSONObject(j) ?: continue
                    val ch = w.optString("c")
                    if (ch.isEmpty()) continue
                    val offsetSec = w.optDouble("o", 0.0)
                    val wStartMs = lineStartMs + (offsetSec * 1000.0).toLong()
                    sb.append(NativeLyricsHttp.formatWordStamp(wStartMs)).append(ch)
                }
                sb.append(NativeLyricsHttp.formatWordStamp(lineEndMs))
            } else {
                sb.append(lineObj.optString("x"))
            }
            sb.append('\n')
        }
        sb.toString().trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

    private suspend fun getOrFetchToken(): String? = tokenMutex.withLock {
        val now = System.currentTimeMillis()
        if (!cachedToken.isNullOrBlank() && now < tokenExpiresAtMs) {
            return cachedToken
        }
        val token = runCatching {
            val url = "$MXM_BASE/token.get?app_id=web-desktop-app-v1.0&format=json"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", NativeLyricsHttp.USER_AGENT)
                .header("Cookie", "x-mxm-token-guid=1")
                .build()
            NativeLyricsHttp.fastClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string() ?: return@runCatching null
                JSONObject(body)
                    .optJSONObject("message")
                    ?.optJSONObject("body")
                    ?.optString("user_token")
                    ?.takeIf { token ->
                        token.isNotBlank() &&
                            token != "UpgradeOnlyUpgradeOnlyUpgradeOnlyUpgradeOnly" &&
                            !token.all { it == '0' }
                    }
            }
        }.getOrNull()
        if (!token.isNullOrBlank()) {
            cachedToken = token
            tokenExpiresAtMs = now + 45 * 60 * 1000L
        }
        token
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 10. LRCLIB (Native Direct Get + Search Fallback + Duration-Scored Ranking)
// ─────────────────────────────────────────────────────────────────────────────
internal object LrcLibClient {
    private const val LRCLIB_UA = "Tinque/2.9 (https://github.com/bitchord)"

    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): String? = withContext(Dispatchers.IO) {
        val cleanTitle = NativeLyricsHttp.cleanQueryText(title)
        val cleanArtist = NativeLyricsHttp.primaryArtist(artist)
        // Stage 1: Exact /api/get lookup when duration is known (with album, then without album)
        if (durationSec > 0 && cleanArtist.isNotBlank()) {
            if (!album.isNullOrBlank()) {
                queryDirectGet(cleanTitle, cleanArtist, album, durationSec)?.let { cand ->
                    cand.syncedLyrics?.let { return@withContext it }
                }
            }
            queryDirectGet(cleanTitle, cleanArtist, null, durationSec)?.let { cand ->
                cand.syncedLyrics?.let { return@withContext it }
            }
        }
        // Stage 2: Scored search across track+artist (or ?q= fallback)
        val candidates = searchLyrics(title, artist, album, durationSec)
        val bestSynced = candidates.firstOrNull { !it.syncedLyrics.isNullOrBlank() }?.syncedLyrics
        if (!bestSynced.isNullOrBlank()) return@withContext bestSynced
        if (durationSec > 0 && cleanArtist.isNotBlank()) {
            queryDirectGet(cleanTitle, cleanArtist, null, durationSec)?.plainLyrics?.let { return@withContext it }
        }
        candidates.firstOrNull()?.bestRaw
    }

    suspend fun searchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSec: Int,
    ): List<NativeSearchCandidate> = withContext(Dispatchers.IO) {
        val cleanTitle = NativeLyricsHttp.cleanQueryText(title)
        val cleanArtist = NativeLyricsHttp.primaryArtist(artist)
        val merged = ArrayList<Pair<NativeSearchCandidate, Int>>()
        val seenIds = HashSet<String>()

        fun addAllFrom(url: okhttp3.HttpUrl?) {
            if (url == null) return
            val req = Request.Builder().url(url).header("User-Agent", LRCLIB_UA).build()
            val body = NativeLyricsHttp.executeOrThrow(NativeLyricsHttp.fastClient, req, "LRCLIB") ?: return
            for ((cand, score) in parseSearchArray(body, title, artist, durationSec)) {
                if (seenIds.add(cand.id)) {
                    merged += cand to score
                }
            }
        }

        // Exact /api/get match first (great for studio tracks)
        if (durationSec > 0 && cleanArtist.isNotBlank()) {
            runCatching { queryDirectGet(cleanTitle, cleanArtist, album, durationSec) }
                .getOrNull()
                ?.let { cand ->
                    if (seenIds.add(cand.id)) merged += cand to 300
                }
        }

        val trackArtistUrl = "https://lrclib.net/api/search".toHttpUrlOrNull()?.newBuilder()?.apply {
            addQueryParameter("track_name", cleanTitle)
            if (cleanArtist.isNotBlank()) addQueryParameter("artist_name", cleanArtist)
        }?.build()
        addAllFrom(trackArtistUrl)

        if (merged.none { !it.first.syncedLyrics.isNullOrBlank() }) {
            val freeQuery = "$cleanTitle $cleanArtist".trim()
            if (freeQuery.isNotBlank()) {
                val qUrl = "https://lrclib.net/api/search".toHttpUrlOrNull()?.newBuilder()
                    ?.addQueryParameter("q", freeQuery)
                    ?.build()
                addAllFrom(qUrl)
            }
        }

        merged.sortedByDescending { it.second }.map { it.first }
    }

    private fun queryDirectGet(
        cleanTitle: String,
        cleanArtist: String,
        album: String?,
        durationSec: Int,
    ): NativeSearchCandidate? {
        val urlBuilder = "https://lrclib.net/api/get".toHttpUrlOrNull()?.newBuilder() ?: return null
        urlBuilder.addQueryParameter("track_name", cleanTitle)
        urlBuilder.addQueryParameter("artist_name", cleanArtist)
        if (!album.isNullOrBlank()) urlBuilder.addQueryParameter("album_name", album)
        if (durationSec > 0) urlBuilder.addQueryParameter("duration", durationSec.toString())

        val req = Request.Builder().url(urlBuilder.build()).header("User-Agent", LRCLIB_UA).build()
        val body = NativeLyricsHttp.executeOrThrow(NativeLyricsHttp.fastClient, req, "LRCLIB") ?: return null
        return runCatching {
            val obj = JSONObject(body)
            val synced = obj.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
            val plain = obj.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
            if (synced == null && plain == null) return@runCatching null
            NativeSearchCandidate(
                id = "lrclib_${obj.optLong("id", 0L)}",
                title = obj.optString("trackName", cleanTitle),
                artist = obj.optString("artistName", cleanArtist),
                album = obj.optString("albumName").takeIf { it.isNotBlank() && it != "null" },
                durationSeconds = obj.optDouble("duration", durationSec.toDouble()).toInt(),
                provider = "LRCLIB",
                syncedLyrics = synced,
                plainLyrics = plain,
            )
        }.getOrNull()
    }

    internal fun parseSearchArray(
        body: String,
        targetTitle: String,
        targetArtist: String,
        targetDurationSec: Int,
    ): List<Pair<NativeSearchCandidate, Int>> = runCatching {
        val arr = JSONArray(body)
        val out = ArrayList<Pair<NativeSearchCandidate, Int>>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val synced = obj.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
            val plain = obj.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
            if (synced == null && plain == null) continue
            val cTitle = obj.optString("trackName", targetTitle)
            val cArtist = obj.optString("artistName", targetArtist)
            val cDur = obj.optDouble("duration", 0.0).let { if (it.isFinite() && it > 0.0) it.toInt() else 0 }
            val baseScore = NativeLyricsHttp.scoreCandidate(
                candTitle = cTitle,
                candArtist = cArtist,
                candDurationSec = cDur,
                targetTitle = targetTitle,
                targetArtist = targetArtist,
                targetDurationSec = targetDurationSec,
            )
            val syncBonus = if (synced != null) 120 else 0
            val candidate = NativeSearchCandidate(
                id = "lrclib_${obj.optLong("id", i.toLong())}",
                title = cTitle,
                artist = cArtist,
                album = obj.optString("albumName").takeIf { it.isNotBlank() && it != "null" },
                durationSeconds = cDur,
                provider = "LRCLIB",
                syncedLyrics = synced,
                plainLyrics = plain,
            )
            out += candidate to (baseScore + syncBonus)
        }
        out
    }.getOrDefault(emptyList())
}
