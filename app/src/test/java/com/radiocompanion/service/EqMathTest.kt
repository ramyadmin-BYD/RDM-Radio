package com.radiocompanion.service

import com.radiocompanion.service.audio.EqMath
import com.radiocompanion.service.audio.EqPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EqMathTest {

    private val flat = List(7) { 0f }
    // A typical 5-band head-unit equalizer (AOSP default centres) and range ±15 dB.
    private val fiveBand = listOf(60f, 230f, 910f, 3_600f, 14_000f)
    private val tenBand = listOf(31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f)
    private val range = -1500..1500

    private fun plan(
        gains: List<Float> = flat, enabled: Boolean = true, levelMatch: Float = 3f,
        loudness: Boolean = false, volume: Float? = 0.5f, centers: List<Float> = fiveBand,
        hwBass: Boolean = true, enhancer: Boolean = true,
    ) = EqMath.plan(enabled, gains, levelMatch, loudness, volume, centers, range, hwBass, enhancer)

    @Test fun interpolatesOnLogFrequency() {
        val g = listOf(0f, 6f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(6f, EqMath.interpolate(g, 150f), 0.001f)
        assertEquals(0f, EqMath.interpolate(g, 400f), 0.001f)
        val mid = EqMath.interpolate(g, 245f)   // ~geometric middle of 150 and 400 Hz
        assertTrue(mid in 2.8f..3.2f)
        assertEquals(0f, EqMath.interpolate(g, 20f), 0.001f)       // below range: flat extension
        assertEquals(0f, EqMath.interpolate(g, 20_000f), 0.001f)
    }

    @Test fun flatCurveWithEqOnGetsOnlyLevelMatch() {
        val p = plan()
        assertEquals(listOf(0, 0, 0, 0, 0), p.nativeLevelsMb)
        assertEquals(300, p.makeupGainMb)
        assertTrue(p.equalizerOn)
    }

    @Test fun eqOffMeansNoProcessing() {
        val p = plan(gains = EqPresets.byId("rock")!!.gainsDb, enabled = false)
        assertEquals(listOf(0, 0, 0, 0, 0), p.nativeLevelsMb)
        assertEquals(0, p.makeupGainMb)
        assertFalse(p.equalizerOn)
    }

    @Test fun cutsAreCompensatedWithMakeupGain() {
        val cuts = List(7) { -4f }
        val p = plan(gains = cuts)
        assertEquals(700, p.makeupGainMb)                // 3 dB level match + 4 dB lost to cuts
        assertTrue(p.nativeLevelsMb.all { it == -400 })
    }

    @Test fun boostsAreShiftedDownInsteadOfGettingLouder() {
        val boosts = List(7) { 5f }
        val p = plan(gains = boosts)
        assertTrue(p.nativeLevelsMb.all { it == 0 })     // +5 everywhere sounds like flat, louder → shifted to 0
        assertEquals(300, p.makeupGainMb)
    }

    @Test fun makeupIsCapped() {
        val p = plan(gains = List(7) { -12f }, levelMatch = 9f)
        assertEquals((EqMath.MAX_MAKEUP_DB * 100).toInt(), p.makeupGainMb)
    }

    @Test fun withoutLoudnessEnhancerMakeupGoesIntoBands() {
        val p = plan(enhancer = false)
        assertEquals(0, p.makeupGainMb)
        assertTrue(p.nativeLevelsMb.all { it == 300 })
    }

    @Test fun mapsOntoTenBandHardware() {
        val p = plan(gains = EqPresets.byId("bass")!!.gainsDb, centers = tenBand)
        assertEquals(10, p.nativeLevelsMb.size)
        assertTrue(p.nativeLevelsMb[0] > p.nativeLevelsMb[6])   // bass > 2 kHz
    }

    @Test fun levelsAreClampedToHardwareRange() {
        val p = EqMath.plan(true, List(7) { -12f }, 0f, false, null, fiveBand, -600..600, true, true)
        assertTrue(p.nativeLevelsMb.all { it == -600 })
    }

    @Test fun loudnessIsStrongerAtLowVolume() {
        assertTrue(EqMath.loudnessStrength(0.1f) > EqMath.loudnessStrength(0.9f))
        assertEquals(1000, EqMath.loudnessStrength(0f))
        assertEquals(300, EqMath.loudnessStrength(1f))
    }

    @Test fun loudnessUsesHardwareBassBoostWhenAvailable() {
        val p = plan(enabled = false, loudness = true, volume = 0.2f)
        assertTrue(p.bassBoostStrength > 0)
        assertFalse(p.equalizerOn)
    }

    @Test fun loudnessFallsBackToEqLowShelf() {
        val p = plan(enabled = false, loudness = true, volume = 0f, hwBass = false)
        assertEquals(0, p.bassBoostStrength)
        assertTrue(p.equalizerOn)
        assertEquals(600, p.nativeLevelsMb[0])          // 60 Hz: full +6 dB shelf
        assertEquals(0, p.nativeLevelsMb[2])            // 910 Hz: untouched
    }

    @Test fun presetsHaveSevenBands() {
        assertTrue(EqPresets.ALL.all { it.gainsDb.size == 7 })
    }
}
