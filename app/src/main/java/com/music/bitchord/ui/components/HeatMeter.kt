package com.music.bitchord.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.HeatColor
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.data.stats.HeatInfo
import com.music.bitchord.data.stats.HeatStore
import com.music.bitchord.data.stats.heatKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * The heat meter's look (option B' of the heat_meter mock):
 *
 *  - A line whose drawn length is the level out of 5, the rest a plain track.
 *  - The level is also told by how many crests the wave has over the meter's
 *    length — five steps, straight at 0. Counted per length rather than given
 *    in dp, so a meter drawn larger is the same shape scaled up, never a
 *    different-looking wave (the player's and its long-press details agree).
 *  - It does not move, except from 4.8 up, where it flows slowly.
 *  - Colour: grey that saturates towards 5, in one hue (orange, pink, or the
 *    album-art theme's). The same colour everywhere: the hue is worked out
 *    once, outside any glass surface whose own theme would hide the artwork's.
 */

/** Crests over the meter's whole length, per step (0 is straight). */
private val STEP_CRESTS = floatArrayOf(0f, 1.5f, 2.5f, 3.5f, 5f, 6.5f)

/** From here up the wave flows. */
const val HEAT_FLOW_FROM = 4.8f

/** Red-orange (2.6; orange was 22°). */
private const val ORANGE_HUE = 12f
private const val PINK_HUE = 335f

/** Height to width of every meter, so all of them are one shape at different sizes. */
private const val METER_ASPECT = 0.3f

private fun crests(level: Float): Float =
    STEP_CRESTS[floor(level.coerceIn(0f, 5f) + 1e-4f).toInt().coerceIn(0, 5)]

/**
 * The hue heat is drawn in. Call it where the app's own theme applies — not
 * inside a glass surface, whose theme swaps the colours for white/black — and
 * hand the result down.
 */
@Composable
fun rememberHeatHue(): Float {
    val choice by AppSettings.heatColor.collectAsStateWithLifecycle()
    val theme by AppSettings.themeMode.collectAsStateWithLifecycle()
    val primary = MaterialTheme.colorScheme.primary
    return remember(choice, theme, primary) {
        when (choice) {
            HeatColor.ORANGE -> ORANGE_HUE
            HeatColor.PINK -> PINK_HUE
            HeatColor.ARTWORK -> {
                if (theme != ThemeMode.ARTWORK) return@remember ORANGE_HUE
                val hsl = FloatArray(3)
                androidx.core.graphics.ColorUtils.colorToHSL(primary.toArgb(), hsl)
                // A grey cover has no hue worth taking: the meter would never colour.
                if (hsl[1] < 0.25f) ORANGE_HUE else hsl[0]
            }
        }
    }
}

/** Grey at 0, fully saturated at 5. */
fun heatColor(level: Float, hue: Float): Color {
    val t = (level / 5f).coerceIn(0f, 1f)
    val e = t.toDouble().pow(1.6).toFloat()
    return Color.hsl(hue, (e * 0.92f).coerceIn(0f, 1f), (0.70f - e * 0.08f).coerceIn(0f, 1f))
}

/**
 * One clock for every flowing heat line (meters and tile rings), so a screen
 * of them costs one frame callback rather than one animation each.
 *
 * It runs only while music plays and some line wants it. Lines read it while
 * *drawing*, never while composing, so a flowing line redraws itself and
 * nothing around it. A line on the pages ("gated") also holds still while the
 * player is raised over them: it is hidden there, and the glass would redraw
 * the page under the player's open/close for it (the 2.5.1 stutter).
 */
object HeatClock {
    internal val seconds = mutableFloatStateOf(0f)

    /** Set by the activity: music is playing. Lines are still (not straight) while paused. */
    var playing by mutableStateOf(false)

    private val pages = mutableStateOf(true)
    private var frozenAt = 0f

    /** Set by the activity: false while the player is raised over the pages. */
    var pagesMayMove: Boolean
        get() = pages.value
        set(value) {
            if (pages.value && !value) frozenAt = seconds.floatValue
            pages.value = value
        }

    internal var gatedLines by mutableIntStateOf(0)
    internal var openLines by mutableIntStateOf(0)

    internal val shouldRun: Boolean
        get() = playing && (openLines > 0 || (gatedLines > 0 && pages.value))

    /** The phase of a line flowing once per [periodMs]. Call while drawing. */
    internal fun phase(periodMs: Int, gated: Boolean): Float {
        val t = if (gated && !pages.value) frozenAt else seconds.floatValue
        return ((t * 1000f / periodMs) % 1f) * 2f * PI.toFloat()
    }
}

/** Runs [HeatClock]. Once, near the root. */
@Composable
fun HeatClockDriver() {
    LaunchedEffect(Unit) {
        snapshotFlow { HeatClock.shouldRun }
            .distinctUntilChanged()
            .collectLatest { run ->
                if (!run) return@collectLatest
                val base = HeatClock.seconds.floatValue
                var start = -1L
                while (true) {
                    withFrameNanos { now ->
                        if (start < 0) start = now
                        HeatClock.seconds.floatValue = (base + (now - start) / 1_000_000_000f) % 3600f
                    }
                }
            }
    }
}

/** Counts this line in [HeatClock] while it [flows]. */
@Composable
internal fun HeatFlowSubscription(flows: Boolean, gated: Boolean) {
    DisposableEffect(flows, gated) {
        if (flows) { if (gated) HeatClock.gatedLines++ else HeatClock.openLines++ }
        onDispose {
            if (flows) { if (gated) HeatClock.gatedLines-- else HeatClock.openLines-- }
        }
    }
}

/** Slow even at the top: a flow, not a wiggle. */
internal fun heatFlowPeriod(level: Float): Int {
    val over = ((level - HEAT_FLOW_FROM) / (5f - HEAT_FLOW_FROM)).coerceIn(0f, 1f)
    return (2600 - 1200 * over).toInt()
}

/**
 * The meter: [level] out of 5 across [width]; its height follows the width so
 * every meter is the same shape. [hue] from [rememberHeatHue] at the call site
 * when this is drawn on glass. [gated]: false for meters in the player, which
 * keep flowing while it is open.
 */
@Composable
fun HeatMeter(
    level: Float,
    modifier: Modifier = Modifier,
    width: Dp = 56.dp,
    hue: Float? = null,
    trackColor: Color = Color.White.copy(alpha = 0.18f),
    animate: Boolean = true,
    gated: Boolean = true,
    /** One colour instead of the heat's hue (the player's, so it does not fight the artwork). */
    tint: Color? = null,
) {
    val drawHue = hue ?: rememberHeatHue()
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val flows = animate && level >= HEAT_FLOW_FROM && !reduceAnimation
    HeatFlowSubscription(flows, gated)
    val period = heatFlowPeriod(level)
    val color = tint ?: heatColor(level, drawHue)
    Canvas(modifier.size(width, width * METER_ASPECT)) {
        val phase = if (flows) HeatClock.phase(period, gated) else 0f
        val h = size.height
        val stroke = h * 0.22f
        val amplitude = h * 0.18f
        val mid = h / 2f
        val start = stroke / 2f
        val end = size.width - stroke / 2f
        val fillEnd = (start + (end - start) * (level / 5f)).coerceIn(start, end)
        val gap = h * 0.36f
        if (fillEnd + gap < end) {
            drawLine(trackColor, Offset(fillEnd + gap, mid), Offset(end, mid), stroke, StrokeCap.Round)
        }
        if (level > 0.05f) {
            val c = crests(level)
            val wavelength = if (c == 0f) Float.POSITIVE_INFINITY else (end - start) / c
            val path = Path()
            val edge = h * 0.3f
            var x = start
            var first = true
            while (x <= fillEnd) {
                // The ends are eased flat so the line's caps never kink.
                val taper = min(1f, min((x - start) / edge, (fillEnd - x) / edge)).coerceAtLeast(0f)
                val y = if (wavelength.isInfinite()) mid else
                    mid + sin(((x - start) / wavelength) * 2f * PI.toFloat() - phase) * amplitude * taper
                if (first) path.moveTo(x, y) else path.lineTo(x, y)
                first = false
                x += 0.75f
            }
            drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            drawCircle(color, stroke / 2f, Offset(start, mid))
        }
    }
}

/** The mark a hot track gets in a list: the same meter, small. */
@Composable
fun HeatMark(level: Float, modifier: Modifier = Modifier) {
    // The track in the page's own ink, as the tiles' rings: on a light theme a
    // white track vanished, and a cold track is all that shows.
    HeatMeter(level = level, modifier = modifier, width = 30.dp, trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f))
}

/** "Today", "3 days ago", "Nov 2" — short enough for a small cell. */
@Composable
private fun relativeDay(ms: Long): String {
    if (ms <= 0) return "—"
    val day = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    val days = ChronoUnit.DAYS.between(day, today)
    return when {
        days <= 0L -> stringResource(R.string.heat_today)
        days == 1L -> stringResource(R.string.heat_yesterday)
        days < 7L -> stringResource(R.string.heat_days_ago, days.toInt())
        days < 35L -> stringResource(R.string.heat_weeks_ago, (days / 7).toInt())
        day.year == today.year -> "${day.monthValue}/${day.dayOfMonth}"
        else -> "${day.year}/${day.monthValue}/${day.dayOfMonth}"
    }
}

/**
 * What the meter's long-press shows: the meter, its value, its peak, and six
 * numbers. No prose — the setting explains the meter once.
 */
@Composable
fun HeatDetails(info: HeatInfo, hue: Float, modifier: Modifier = Modifier, song: Song? = null) {
    val content = LocalContentColor.current
    // "First": the earlier of heat's own first listen and the first month the
    // monthly records saw the song (they reach back three years) — and never
    // the date carried over in 2.5.0, which was the *last* play then.
    val firstMonth = androidx.compose.runtime.produceState<java.time.YearMonth?>(null, song?.videoId) {
        value = song?.videoId?.let { id ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.music.bitchord.data.stats.ListeningStats.firstMonthOf(id)
            }
        }
    }.value
    val heatFirst = info.firstPlayed.takeIf { it > 0 && !info.firstCarriedOver }
    val heatFirstMonth = heatFirst?.let { java.time.YearMonth.from(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }
    val firstLabel = when {
        heatFirst != null && (firstMonth == null || !firstMonth.isBefore(heatFirstMonth)) -> relativeDay(heatFirst)
        firstMonth != null -> stringResource(R.string.heat_first_month, firstMonth.year, firstMonth.monthValue)
        else -> "—"
    }
    fun one(v: Float) = String.format(Locale.ROOT, "%.1f", v)
    Column(modifier = modifier.width(272.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = one(info.level),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = content,
            )
            Text(text = " / 5", style = MaterialTheme.typography.titleMedium, color = content.copy(alpha = 0.55f))
            Spacer(Modifier.weight(1f))
            HeatMeter(level = info.level, width = 88.dp, hue = hue, trackColor = content.copy(alpha = 0.18f), gated = false)
        }
        if (info.peak > info.level + 0.05f) {
            Text(
                text = stringResource(R.string.heat_peak, one(info.peak)),
                style = MaterialTheme.typography.labelLarge,
                color = content.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.height(8.dp))
        val statusRes = when {
            info.isRevival -> R.string.heat_status_revival
            info.trend > 0 && info.momentum >= 1.5f -> R.string.heat_status_surging
            info.trend > 0 -> R.string.heat_status_rising
            info.trend < 0 -> R.string.heat_status_cooling
            else -> R.string.heat_status_steady
        }
        val activeStatus = info.isRevival || info.trend > 0
        val statusColor = when {
            activeStatus -> Color.hsl(hue, 0.78f, 0.56f)
            info.trend < 0 -> MaterialTheme.colorScheme.secondary
            else -> content.copy(alpha = 0.72f)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(statusRes),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (activeStatus) FontWeight.Bold else FontWeight.Medium,
                color = if (activeStatus) statusColor else content.copy(alpha = 0.85f),
                maxLines = 1,
                modifier = Modifier
                    .background(
                        if (activeStatus) statusColor.copy(alpha = 0.16f) else content.copy(alpha = 0.08f),
                        RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            val shortText = if (info.plays == 0 && info.shortLevel <= 0f) {
                "—"
            } else {
                one(info.shortLevel)
            }
            Text(
                text = stringResource(R.string.heat_short_label, shortText),
                style = MaterialTheme.typography.labelMedium,
                color = content.copy(alpha = 0.78f),
                maxLines = 1,
                modifier = Modifier
                    .background(content.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        val dash = "—"
        val cells = listOf(
            stringResource(R.string.heat_plays) to info.plays.toString(),
            stringResource(R.string.heat_completion) to (info.completionRate?.let { "${(it * 100).toInt()}%" } ?: dash),
            stringResource(R.string.heat_skips) to info.skips.toString(),
            stringResource(R.string.heat_first_played) to firstLabel,
            stringResource(R.string.heat_last_played) to relativeDay(info.lastPlayed),
            stringResource(R.string.heat_this_week) to (if (info.recordedPlays > 0) info.listensThisWeek.toString() else dash),
        )
        cells.chunked(3).forEachIndexed { row, three ->
            if (row > 0) Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                three.forEach { (label, value) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(content.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(value, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
                        Text(label, style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.6f), maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * A list row's heat mark, from 0 like the tiles' rings (2.6.1): a track never
 * played shows the bare track. Nothing with the meter off.
 */
@Composable
fun SongHeatMark(song: Song) {
    val enabled by AppSettings.heatMeterEnabled.collectAsStateWithLifecycle()
    if (!enabled) return
    val info by HeatStore.info.collectAsStateWithLifecycle()
    Spacer(Modifier.width(8.dp))
    HeatMark(info[song.heatKey()]?.level ?: 0f)
}

/** A group row's mark (album, artist, playlist): [level] from the remember*TileHeat helpers; null draws nothing. */
@Composable
fun GroupHeatMark(level: Float?, modifier: Modifier = Modifier) {
    if (level == null) return
    HeatMark(level, modifier)
}

/**
 * A tile's heat ring level: null with the meter off (no ring, full-size art),
 * otherwise the group's level — 0 for a group never played.
 */
@Composable
fun rememberTileHeat(songs: List<Song>): Float? {
    val enabled by AppSettings.heatMeterEnabled.collectAsStateWithLifecycle()
    val info by HeatStore.info.collectAsStateWithLifecycle()
    if (!enabled) return null
    return remember(songs, info) {
        val levels = songs.mapNotNull { info[it.heatKey()]?.level }
        if (levels.isEmpty()) 0f else HeatStore.groupLevel(levels + List(songs.size - levels.size) { 0f })
    }
}

/** As [rememberTileHeat] for one song. */
@Composable
fun rememberSongTileHeat(song: Song): Float? {
    val enabled by AppSettings.heatMeterEnabled.collectAsStateWithLifecycle()
    val info by HeatStore.info.collectAsStateWithLifecycle()
    if (!enabled) return null
    return info[song.heatKey()]?.level ?: 0f
}

/** As [rememberTileHeat] for a playlist ([com.music.bitchord.data.stats.PlaySource.playlistKey]). */
@Composable
fun rememberPlaylistTileHeat(key: String, songs: List<Song>): Float? {
    val enabled by AppSettings.heatMeterEnabled.collectAsStateWithLifecycle()
    val info by HeatStore.info.collectAsStateWithLifecycle()
    if (!enabled) return null
    return remember(key, songs, info) {
        HeatStore.playlistLevel(key, songs.map { info[it.heatKey()]?.level ?: 0f })
    }
}

/** Favourites' ring: what was heard from it, or its hottest tracks. */
@Composable
fun rememberFavoritesTileHeat(librarySongs: List<Song>): Float? {
    val ids by com.music.bitchord.feature.localmusic.data.LocalFavoritesStore.favoriteIds.collectAsStateWithLifecycle()
    val favorites = remember(ids, librarySongs) {
        librarySongs.filter { (it.localUri ?: it.videoId) in ids || it.videoId in ids }
    }
    return rememberPlaylistTileHeat(com.music.bitchord.data.stats.PlaySource.FAVORITES_KEY, favorites)
}

/** The heat of a group of songs (album, folder, playlist), or null when none of it has been played. */
@Composable
fun rememberGroupHeat(songs: List<Song>): Float? {
    val enabled by AppSettings.heatMeterEnabled.collectAsStateWithLifecycle()
    val info by HeatStore.info.collectAsStateWithLifecycle()
    if (!enabled) return null
    return remember(songs, info) {
        val levels = songs.mapNotNull { info[it.heatKey()]?.level }
        if (levels.isEmpty()) null else HeatStore.groupLevel(levels + List(songs.size - levels.size) { 0f })
    }
}
