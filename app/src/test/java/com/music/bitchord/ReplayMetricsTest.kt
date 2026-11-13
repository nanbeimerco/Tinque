package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.RankedSong
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.ReplaySummary
import com.music.bitchord.ui.replay.activeDaysCount
import com.music.bitchord.ui.replay.avgDailyMinutes
import com.music.bitchord.ui.replay.buildTimelineBars
import com.music.bitchord.ui.replay.concentrationMetrics
import com.music.bitchord.ui.replay.longestStreakDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class ReplayMetricsTest {

    private fun song(id: String): Song = Song(
        videoId = id,
        title = "Track $id",
        artist = "Artist $id",
        thumbnailUrl = null,
    )

    private fun baseSummary(
        period: ReplayPeriod = ReplayPeriod.THIS_YEAR,
        totalMs: Long = 0L,
        totalPlays: Int = 0,
        songs: List<RankedSong> = emptyList(),
        dailyMs: Map<String, Long> = emptyMap(),
        monthlyMs: Map<String, Long> = emptyMap(),
    ): ReplaySummary = ReplaySummary(
        period = period,
        label = "Test",
        totalMs = totalMs,
        totalPlays = totalPlays,
        songs = songs,
        artists = emptyList(),
        albums = emptyList(),
        genres = emptyList(),
        hourOfDay = List(24) { 0L },
        busiestDay = dailyMs.maxByOrNull { it.value }?.key,
        busiestDayMs = dailyMs.values.maxOrNull() ?: 0L,
        distinctSongs = songs.size,
        distinctArtists = songs.size,
        distinctAlbums = songs.size,
        since = "2026-01",
        dailyMs = dailyMs,
        monthlyMs = monthlyMs,
    )

    @Test
    fun `longestStreakDays ignores zero and invalid keys and returns longest consecutive run`() {
        val daily = mapOf(
            "2026-01-01" to 120_000L,
            "2026-01-02" to 180_000L,
            "2026-01-03" to 0L,
            "bad-date" to 500_000L,
            "2026-01-05" to 60_000L,
            "2026-01-06" to 90_000L,
            "2026-01-07" to 300_000L,
            "2026-01-08" to 40_000L,
        )
        assertEquals(4, longestStreakDays(daily))
        assertEquals(6, activeDaysCount(daily))
        assertEquals(0, longestStreakDays(mapOf("2026-01-01" to 0L)))
        assertEquals(0, longestStreakDays(emptyMap()))
    }

    @Test
    fun `avgDailyMinutes divides listening across active days`() {
        val daily = mapOf(
            "2026-02-01" to 30 * 60_000L,
            "2026-02-02" to 90 * 60_000L,
            "2026-02-03" to 0L,
        )
        assertEquals(60, avgDailyMinutes(totalMs = 120 * 60_000L, dailyMs = daily))
        assertEquals(0, avgDailyMinutes(totalMs = 0L, dailyMs = emptyMap()))
    }

    @Test
    fun `concentrationMetrics computes shares cleanly without exceeding 1`() {
        val songs = listOf(
            RankedSong(song("1"), ms = 400_000L, plays = 4),
            RankedSong(song("2"), ms = 200_000L, plays = 2),
            RankedSong(song("3"), ms = 100_000L, plays = 1),
            RankedSong(song("4"), ms = 300_000L, plays = 3),
        )
        val summary = baseSummary(totalMs = 1_000_000L, totalPlays = 10, songs = songs)
        val c = concentrationMetrics(summary)

        assertEquals(0.40f, c.topOneShare, 0.001f)
        assertEquals(0.30f, c.topTwoThreeShare, 0.001f)
        assertEquals(0.70f, c.topThreeShare, 0.001f)
        assertEquals(0.30f, c.othersShare, 0.001f)
        assertEquals(2.5f, c.avgPlaysPerSong, 0.001f)
    }

    @Test
    fun `buildTimelineBars produces daily bars for THIS_MONTH and highlights peak`() {
        val daily = mapOf(
            "2026-02-04" to 120_000L,
            "2026-02-14" to 600_000L,
        )
        val summary = baseSummary(period = ReplayPeriod.THIS_MONTH, totalMs = 720_000L, dailyMs = daily)
        val bars = buildTimelineBars(summary, today = LocalDate.of(2026, 2, 20), locale = Locale.US)

        assertEquals(28, bars.size)
        assertTrue(bars[13].isPeak)
        assertEquals(600_000L, bars[13].ms)
        assertFalse(bars[3].isPeak)
        assertFalse(bars[0].isPeak)
    }

    @Test
    fun `buildTimelineBars emits 12 months for THIS_YEAR and caps ALL_TIME at 12 contiguous months`() {
        val yearSummary = baseSummary(
            period = ReplayPeriod.THIS_YEAR,
            monthlyMs = mapOf("2026-03" to 300_000L, "2026-08" to 900_000L),
        )
        val yearBars = buildTimelineBars(yearSummary, today = LocalDate.of(2026, 10, 1), locale = Locale.US)
        assertEquals(12, yearBars.size)
        assertTrue(yearBars[7].isPeak)

        val manyMonths = (0..15).associate { i ->
            val date = LocalDate.of(2025, 1, 1).plusMonths(i.toLong())
            "%04d-%02d".format(Locale.ROOT, date.year, date.monthValue) to (i + 1) * 10_000L
        }
        val allSummary = baseSummary(period = ReplayPeriod.ALL_TIME, monthlyMs = manyMonths)
        val allBars = buildTimelineBars(allSummary, today = LocalDate.of(2026, 5, 1), locale = Locale.US)
        assertEquals(12, allBars.size)
        assertEquals("2025-05", allBars.first().key)
        assertEquals("2026-04", allBars.last().key)
        assertTrue(allBars.last().isPeak)
    }
}
