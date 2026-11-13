package com.music.bitchord

import com.music.bitchord.data.stats.HeatScorer
import com.music.bitchord.data.stats.PlayOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatScorerTest {

    @Test
    fun halfLivesAndSteadyRatio_matchSpec() {
        assertEquals(3.0, HeatScorer.SHORT_HALF_LIFE_DAYS, 1e-6)
        assertEquals(14.0, HeatScorer.LONG_HALF_LIFE_DAYS, 1e-6)
        assertEquals(3.0 / 14.0, HeatScorer.STEADY_RATIO, 1e-6)
    }

    @Test
    fun steadyDailyPlay_hasNearZeroMomentum_andDoesNotSurge() {
        // At steady-state daily rate of 1 pt/day, long ~= 20.2, short ~= 4.33
        val longSteady = 20.20
        val shortSteady = longSteady * HeatScorer.STEADY_RATIO
        val mom = HeatScorer.momentum(shortSteady, longSteady, plays = 30)
        assertEquals(0f, mom, 0.05f)
        assertEquals(0, HeatScorer.trend(shortSteady, longSteady, plays = 30))
    }

    @Test
    fun highLevelLooping_producesUnboundedPositiveMomentumAndRisingTrend() {
        val shortLoop = 10.93
        val longLoop = 30.58
        val mom = HeatScorer.momentum(shortLoop, longLoop, plays = 40)
        assertTrue("Expected momentum >= 1.5, got $mom", mom >= 1.5f)
        assertEquals(1, HeatScorer.trend(shortLoop, longLoop, plays = 40))
    }

    @Test
    fun noveltyDamping_throttlesDayZeroPlay_whilePreservingVeteranSurge() {
        val now = 1_700_000_000_000L
        val rawMomentum = HeatScorer.momentum(
            shortScore = 3.3,
            longScore = 3.3,
            plays = 10,
            firstPlayedMs = now - 5L * 86_400_000L,
            now = now,
        )
        val newSongMomentum = HeatScorer.momentum(
            shortScore = 3.3,
            longScore = 3.3,
            plays = 2,
            firstPlayedMs = now,
            now = now,
        )
        assertEquals(rawMomentum * 0.5f, newSongMomentum, 0.02f)
    }

    @Test
    fun singleLowPlay_isGatedOutOfSurgingTrend() {
        assertEquals(0, HeatScorer.trend(shortScore = 1.7, longScore = 0.2, plays = 2))
        assertEquals(1, HeatScorer.trend(shortScore = 2.04, longScore = 0.8, plays = 5))
    }

    @Test
    fun coolingGap_returnsNegativeTrend() {
        assertEquals(-1, HeatScorer.trend(shortScore = 0.01, longScore = 5.0, plays = 10))
    }

    @Test
    fun listenScoreDeltas_appliesOriginFactorRepeatBeforeSameDay_and50PercentSkipShield() {
        val (pickedShort, pickedLong) = HeatScorer.listenScoreDeltas(
            base = 1.0,
            origin = PlayOrigin.PICKED,
            nthListenToday = 2,
            isRecentRepeat = true,
        )
        // (1.0 * 1.2 + 0.5) * 0.7 = 1.19
        assertEquals(1.19, pickedShort, 1e-6)
        assertEquals(1.19, pickedLong, 1e-6)

        val (seqShort, _) = HeatScorer.listenScoreDeltas(
            base = 1.0,
            origin = PlayOrigin.SEQUENTIAL,
            nthListenToday = 1,
            isRecentRepeat = false,
        )
        assertEquals(1.0, seqShort, 1e-6)

        val (skipShort, skipLong) = HeatScorer.listenScoreDeltas(
            base = -0.5,
            origin = PlayOrigin.SKIPPED_TO,
            nthListenToday = 1,
            isRecentRepeat = false,
        )
        assertEquals(-0.5, skipShort, 1e-6)
        assertEquals(-0.25, skipLong, 1e-6)
    }
}
