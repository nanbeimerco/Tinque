package com.music.bitchord.data.stats

import android.content.Context
import android.util.AtomicFile
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * The heat meter: how much a track is being listened to *now*.
 *
 * It fills with listening and drains with time. Unlike a play count it is not
 * one number that only goes up: each listen is judged on how far it got, a
 * skip takes a little off, a track played over and over in one day counts for
 * less each time, and every point decays with a half-life — so a track that
 * was everything three months ago and has not been played since reads as
 * cold, which is exactly what the Home page's "rediscover" shelf is for.
 *
 * Only listening goes in. Favourites and playlists are the listener's own
 * statements and live in the Library; mixing them in would make the meter
 * repeat what the star already says.
 *
 * Like [ListeningStats], this keeps a running state per track rather than a
 * log of plays: two decaying scores (a short and a long half-life, whose ratio
 * gives the trend) and a few counters. A large library is a few hundred KB.
 */
object HeatScorer {
    /** Half-lives of the two scores, in days. */
    const val SHORT_HALF_LIFE_DAYS = 7.0
    const val LONG_HALF_LIFE_DAYS = 30.0

    /** How quickly the meter fills: score at which it reads about 3.2 of 5. */
    private const val SCALE = 10.0

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Points for one listen, before the same-day damping. */
    fun points(playedMs: Long, durationMs: Long, userSkipped: Boolean): Double {
        val completion = if (durationMs > 0) playedMs.toDouble() / durationMs else 0.0
        return when {
            completion >= 0.9 -> 1.0
            completion >= 0.5 -> 0.5
            playedMs >= 30_000L -> 0.2
            userSkipped && playedMs < 10_000L -> -0.5
            userSkipped -> -0.3
            else -> 0.0
        }
    }

    /** The n-th listen of a day (1-based) counts for less, so a binge does not max the meter. */
    fun sameDayFactor(nth: Int): Double = when (nth) {
        1 -> 1.0
        2 -> 0.7
        3 -> 0.5
        4 -> 0.35
        else -> 0.25
    }

    fun decay(score: Double, elapsedMs: Long, halfLifeDays: Double): Double {
        if (elapsedMs <= 0) return score
        val days = elapsedMs.toDouble() / DAY_MS
        return score * 0.5.pow(days / halfLifeDays)
    }

    /** 0..5 from the long score, filling quickly at first and slowly near the top. */
    fun level(longScore: Double): Float =
        (5.0 * (1.0 - exp(-longScore.coerceAtLeast(0.0) / SCALE))).toFloat().coerceIn(0f, 5f)

    /**
     * Rising (1), steady (0) or cooling (-1): the short score scaled to the
     * long one's steady state, against the long score.
     */
    fun trend(shortScore: Double, longScore: Double): Int {
        if (longScore < 0.5) return if (shortScore > 0.5) 1 else 0
        val steady = SHORT_HALF_LIFE_DAYS / LONG_HALF_LIFE_DAYS
        val ratio = (shortScore / steady) / longScore
        return when {
            ratio > 1.25 -> 1
            ratio < 0.6 -> -1
            else -> 0
        }
    }

    /** Score a steady habit of [plays] full listens would settle at after [days] — for seeding. */
    fun seedScore(plays: Int, lastPlayedMs: Long, now: Long, halfLifeDays: Double): Double {
        if (plays <= 0) return 0.0
        // Spread the known plays as if they ended at the last play, then decay to now.
        val base = plays.toDouble().coerceAtMost(halfLifeDays / ln(2.0) * 2)
        return decay(base, now - lastPlayedMs, halfLifeDays)
    }
}

/** One track's heat as the UI reads it — already decayed to the moment it was computed. */
data class HeatInfo(
    val level: Float,
    val trend: Int,
    val plays: Int,
    val fullListens: Int,
    val skips: Int,
    val listensThisWeek: Int,
    val firstPlayed: Long,
    val lastPlayed: Long,
    /** The highest level of the last 8 weeks (at least the current one). */
    val peak: Float,
    /** Plays heard since heat began recording — the seeded ones say nothing about how far they got. */
    val recordedPlays: Int,
    /** Short score against the long one's steady state: above 1 and climbing, below 1 and cooling. */
    val momentum: Float = 1f,
    /** Listens by time of day: morning, daytime, evening, night (see [HeatStore.slotOf]). Empty before 2.6. */
    val slots: List<Int> = emptyList(),
    /**
     * [firstPlayed] came from the play counts carried over in 2.5.0, which only
     * knew the *last* play: it is not a first play at all.
     */
    val firstCarriedOver: Boolean = false,
) {
    /** Share of recorded plays heard to the end; null until there is a recorded play to judge. */
    val completionRate: Float? get() = if (recordedPlays > 0) fullListens.toFloat() / recordedPlays else null
}

@Serializable
data class HeatEntry(
    val key: String,
    var shortScore: Double = 0.0,
    var longScore: Double = 0.0,
    var updatedAt: Long = 0L,
    var plays: Int = 0,
    /** Of [plays], how many came from the play counts kept before heat (no detail behind them). */
    var seededPlays: Int = 0,
    var fullListens: Int = 0,
    var skips: Int = 0,
    var firstPlayed: Long = 0L,
    var lastPlayed: Long = 0L,
    var dayKey: Long = 0L,
    var listensToday: Int = 0,
    /** Epoch day of each counted listen in the last 7 days, for "this week". */
    var recentDays: MutableList<Long> = mutableListOf(),
    /** Level at each week boundary (epoch week → level), the last 8 kept. */
    var weekly: MutableMap<Long, Float> = mutableMapOf(),
    /** Listens by time of day (morning, daytime, evening, night); empty until the first one in 2.6. */
    var slots: MutableList<Int> = mutableListOf(),
)

@Serializable
private data class HeatFile(
    val version: Int = 1,
    val seeded: Boolean = false,
    val entries: List<HeatEntry> = emptyList(),
)

/** The key heat is filed under: the file path where there is one (a content URI changes on rescan). */
fun Song.heatKey(): String = localPath?.takeIf { it.isNotBlank() } ?: videoId

object HeatStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var file: AtomicFile
    private val lock = Any()
    private val entries = HashMap<String, HeatEntry>()
    private var seeded = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    private val _info = MutableStateFlow<Map<String, HeatInfo>>(emptyMap())
    /** Every track with any heat, decayed to the last change (refreshed on start and on each listen). */
    val info: StateFlow<Map<String, HeatInfo>> = _info.asStateFlow()

    /** The last finished listen, for the repeat bonus. */
    private var lastFinishedKey: String? = null
    private var lastFinishedAt: Long = 0L

    fun init(context: Context) {
        file = AtomicFile(File(context.filesDir, "heat.json"))
        // Read in place: a few hundred KB at most, and the seeding below has to
        // know whether it has already happened before anything else asks.
        val loaded = runCatching {
            file.openRead().bufferedReader().use { json.decodeFromString(HeatFile.serializer(), it.readText()) }
        }.getOrNull()
        synchronized(lock) {
            loaded?.entries?.forEach { entry ->
                // 2.5.0 seeded entries without saying so. A seeded entry never
                // had a week recorded; give its plays back to the seed.
                if (entry.seededPlays == 0 && entry.plays > 0 && entry.weekly.isEmpty()) {
                    entry.seededPlays = entry.plays
                }
                entries[entry.key] = entry
            }
            seeded = loaded?.seeded == true
        }
        publish()
    }

    /** Whether [seedOnce] still has work to do. */
    val needsSeed: Boolean get() = ready && !seeded

    private val ready get() = this::file.isInitialized

    private fun epochDay(ms: Long): Long =
        java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    private fun epochWeek(ms: Long): Long = Math.floorDiv(epochDay(ms) + 3, 7L)

    /**
     * One listen has ended: [playedMs] of real playback, [userSkipped] when the
     * listener moved on by hand rather than the track running out.
     */
    fun finishListen(song: Song, playedMs: Long, durationMs: Long, userSkipped: Boolean) {
        if (!ready) return
        val key = song.heatKey()
        if (key.isBlank()) return
        val now = System.currentTimeMillis()
        val length = durationMs.takeIf { it > 0 } ?: song.durationMillis()
        var points = HeatScorer.points(playedMs, length, userSkipped)
        if (points == 0.0) return
        synchronized(lock) {
            val entry = entries.getOrPut(key) { HeatEntry(key = key) }
            ageTo(entry, now)
            val today = epochDay(now)
            if (points > 0) {
                if (entry.dayKey != today) {
                    entry.dayKey = today
                    entry.listensToday = 0
                }
                entry.listensToday++
                points *= HeatScorer.sameDayFactor(entry.listensToday)
                if (lastFinishedKey == key && now - lastFinishedAt < REPEAT_WINDOW_MS) points += 0.5
                entry.plays++
                if (length > 0 && playedMs >= length * 0.9) entry.fullListens++
                if (entry.firstPlayed == 0L) entry.firstPlayed = now
                entry.lastPlayed = now
                entry.recentDays.add(today)
                while (entry.slots.size < 4) entry.slots.add(0)
                entry.slots[slotOf(now)]++
            } else {
                entry.skips++
            }
            entry.recentDays.removeAll { it < today - 6 }
            entry.shortScore = (entry.shortScore + points).coerceAtLeast(0.0)
            entry.longScore = (entry.longScore + points).coerceAtLeast(0.0)
            recordWeek(entry, now)
            if (points > 0) {
                lastFinishedKey = key
                lastFinishedAt = now
            }
            // The playlist (or Favourites) this was played from warms too.
            val base = HeatScorer.points(playedMs, length, userSkipped)
            if (base > 0) PlaySource.creditFor(song)?.let { collection -> creditCollection(collection, base, now) }
        }
        publish()
        scheduleSave()
    }

    /**
     * A listen heard from a playlist, filed under the playlist's own key
     * ([PlaySource.playlistKey]). A playlist is many tracks in one sitting, so
     * each counts for a fraction and nothing is damped by day or repeat: an
     * hour of it every day fills the meter in about a week.
     */
    private fun creditCollection(key: String, base: Double, now: Long) {
        val entry = entries.getOrPut(key) { HeatEntry(key = key) }
        ageTo(entry, now)
        val points = base * COLLECTION_SHARE
        entry.plays++
        if (entry.firstPlayed == 0L) entry.firstPlayed = now
        entry.lastPlayed = now
        val today = epochDay(now)
        entry.recentDays.add(today)
        entry.recentDays.removeAll { it < today - 6 }
        entry.shortScore += points
        entry.longScore += points
        recordWeek(entry, now)
    }

    /** 0 morning (5–11), 1 daytime (11–17), 2 evening (17–22), 3 night (22–5). */
    fun slotOf(ms: Long): Int {
        val hour = java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).hour
        return when (hour) {
            in 5..10 -> 0
            in 11..16 -> 1
            in 17..21 -> 2
            else -> 3
        }
    }

    /**
     * Fills the meter from the play counts kept before heat existed, once, so
     * an update does not open on an empty meter. [ids] maps each play-count id
     * to the song it belongs to.
     */
    fun seedOnce(counts: List<Triple<Song, Int, Long>>) {
        if (!ready) return
        synchronized(lock) {
            if (seeded) return
            val now = System.currentTimeMillis()
            counts.forEach { (song, plays, last) ->
                if (plays <= 0 || last <= 0) return@forEach
                val key = song.heatKey()
                if (entries.containsKey(key)) return@forEach
                entries[key] = HeatEntry(
                    key = key,
                    shortScore = HeatScorer.seedScore(plays, last, now, HeatScorer.SHORT_HALF_LIFE_DAYS),
                    longScore = HeatScorer.seedScore(plays, last, now, HeatScorer.LONG_HALF_LIFE_DAYS),
                    updatedAt = now,
                    plays = plays,
                    seededPlays = plays,
                    firstPlayed = last,
                    lastPlayed = last,
                )
            }
            seeded = true
        }
        publish()
        scheduleSave()
    }

    fun reset() {
        if (!ready) return
        synchronized(lock) { entries.clear() }
        publish()
        scheduleSave()
    }

    private fun ageTo(entry: HeatEntry, now: Long) {
        if (entry.updatedAt > 0) {
            val elapsed = now - entry.updatedAt
            entry.shortScore = HeatScorer.decay(entry.shortScore, elapsed, HeatScorer.SHORT_HALF_LIFE_DAYS)
            entry.longScore = HeatScorer.decay(entry.longScore, elapsed, HeatScorer.LONG_HALF_LIFE_DAYS)
        }
        entry.updatedAt = now
    }

    private fun recordWeek(entry: HeatEntry, now: Long) {
        val week = epochWeek(now)
        entry.weekly[week] = HeatScorer.level(entry.longScore)
        val keep = entry.weekly.keys.sortedDescending().take(8).toSet()
        entry.weekly.keys.retainAll(keep)
    }

    /** Recomputes what the UI shows, decayed to now. */
    fun publish() {
        val now = System.currentTimeMillis()
        val today = epochDay(now)
        val thisWeek = epochWeek(now)
        val out = synchronized(lock) {
            entries.values.associate { e ->
                val elapsed = now - e.updatedAt
                val long = HeatScorer.decay(e.longScore, elapsed, HeatScorer.LONG_HALF_LIFE_DAYS)
                val short = HeatScorer.decay(e.shortScore, elapsed, HeatScorer.SHORT_HALF_LIFE_DAYS)
                val level = HeatScorer.level(long)
                val peak = maxOf(level, e.weekly.filterKeys { it >= thisWeek - 7 }.values.maxOrNull() ?: 0f)
                e.key to HeatInfo(
                    level = level,
                    trend = HeatScorer.trend(short, long),
                    plays = e.plays,
                    fullListens = e.fullListens,
                    skips = e.skips,
                    listensThisWeek = e.recentDays.count { it >= today - 6 },
                    firstPlayed = e.firstPlayed,
                    lastPlayed = e.lastPlayed,
                    peak = peak,
                    recordedPlays = (e.plays - e.seededPlays).coerceAtLeast(0),
                    momentum = if (long < 0.3) (if (short > 0.3) 2f else 1f) else
                        ((short / (HeatScorer.SHORT_HALF_LIFE_DAYS / HeatScorer.LONG_HALF_LIFE_DAYS)) / long).toFloat(),
                    slots = e.slots.toList(),
                    firstCarriedOver = e.seededPlays > 0,
                )
            }
        }
        _info.value = out
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(2_000)
            save()
        }
    }

    private fun save() {
        val snapshot = synchronized(lock) { HeatFile(seeded = seeded, entries = entries.values.map { it.copy() }) }
        runCatching {
            val out = file.startWrite()
            try {
                out.write(json.encodeToString(HeatFile.serializer(), snapshot).toByteArray())
                file.finishWrite(out)
            } catch (t: Throwable) {
                file.failWrite(out)
                throw t
            }
        }
    }

    fun exportAll(): List<HeatEntry> = synchronized(lock) { entries.values.map { it.copy() } }

    fun importAll(incoming: List<HeatEntry>) {
        if (!ready || incoming.isEmpty()) return
        synchronized(lock) {
            incoming.forEach { entries[it.key] = it }
            seeded = true
        }
        publish()
        scheduleSave()
    }

    /** Average heat of a group (an album, a folder, a playlist), weighting its hottest tracks most. */
    fun groupLevel(levels: List<Float>): Float {
        if (levels.isEmpty()) return 0f
        val sorted = levels.sortedDescending()
        var sum = 0f
        var weight = 0f
        sorted.forEachIndexed { i, v ->
            val w = when (i) { 0 -> 3f; 1 -> 2f; 2 -> 1.5f; else -> 0.4f }
            sum += v * w
            weight += w
        }
        return (sum / weight).coerceIn(0f, 5f)
    }

    /**
     * A playlist's heat: what was heard from it ([PlaySource]) or, until that
     * has built up, its hottest tracks — the top fifth (3 to 10 of them), so a
     * long playlist is not watered down by everything it holds.
     */
    fun playlistLevel(key: String, levels: List<Float>): Float {
        val recorded = _info.value[key]?.level ?: 0f
        if (levels.isEmpty()) return recorded
        val n = (levels.size / 5).coerceIn(3, 10).coerceAtMost(levels.size)
        val top = levels.sortedDescending().take(n).average().toFloat()
        return maxOf(recorded, top).coerceIn(0f, 5f)
    }

    private const val COLLECTION_SHARE = 0.3
    private const val REPEAT_WINDOW_MS = 10L * 60 * 1000

    @Suppress("unused")
    private fun todayLocal(): LocalDate = LocalDate.now()
}
