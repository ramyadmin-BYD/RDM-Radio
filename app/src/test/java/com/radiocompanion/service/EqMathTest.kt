package com.radiocompanion.service

import com.radiocompanion.service.audio.EqMath
import com.radiocompanion.service.audio.EqPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class EqMathTest {

    private val flat = List(10) { 0f }
    // A typical 5-band head-unit equalizer (AOSP default centres) and range ±15 dB.
    private val fiveBand = listOf(60f, 230f, 910f, 3_600f, 14_000f)
    private val tenBand = EqMath.BAND_FREQUENCIES_HZ.toList()
    private val range = -1500..1500

    private fun plan(
        gains: List<Float> = flat, enabled: Boolean = true, levelMatch: Float = 3f,
        loudness: Boolean = false, volume: Float? = 0.5f, centers: List<Float> = fiveBand,
        hwBass: Boolean = true, enhancer: Boolean = true,
    ) = EqMath.plan(enabled, gains, levelMatch, loudness, volume, centers, range, hwBass, enhancer)

    @Test fun hasTenBands() {
        assertEquals(10, EqMath.BAND_COUNT)
        assertEquals(10, EqMath.BAND_FREQUENCIES_HZ.size)
        assertEquals(10, EqMath.BAND_LABELS.size)
    }

    @Test fun interpolatesOnLogFrequency() {
        val g = listOf(0f, 6f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)   // +6 dB at 62 Hz
        assertEquals(6f, EqMath.interpolate(g, 62f), 0.001f)
        assertEquals(0f, EqMath.interpolate(g, 125f), 0.001f)
        val mid = EqMath.interpolate(g, 88f)   // ~geometric middle of 62 and 125 Hz
        assertTrue(mid in 2.8f..3.2f)
        assertEquals(0f, EqMath.interpolate(g, 20f), 0.001f)       // below range: flat extension
        assertEquals(0f, EqMath.interpolate(g, 20_000f), 0.001f)
    }

    @Test fun convertsSavedSevenBandSettings() {
        val old = listOf(6f, 0f, 0f, 0f, 0f, 0f, 0f)   // v1.0.x: +6 dB at 60 Hz
        val converted = EqMath.fromLegacy7Band(old)
        assertEquals(10, converted.size)
        assertEquals(6f, converted[0], 0.001f)        // 31 Hz: below 60 → same as 60 Hz
        assertTrue(converted[1] in 5f..6f)            // 62 Hz: almost the old 60 Hz value
        assertEquals(0f, converted[5], 0.001f)        // 1 kHz untouched
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
        val p = plan(gains = List(10) { -4f })
        assertEquals(700, p.makeupGainMb)                // 3 dB level match + 4 dB lost to cuts
        assertTrue(p.nativeLevelsMb.all { it == -400 })
    }

    @Test fun boostsAreShiftedDownInsteadOfGettingLouder() {
        val p = plan(gains = List(10) { 5f })
        assertTrue(p.nativeLevelsMb.all { it == 0 })
        assertEquals(300, p.makeupGainMb)
    }

    @Test fun makeupIsCapped() {
        val p = plan(gains = List(10) { -12f }, levelMatch = 9f)
        assertEquals((EqMath.MAX_MAKEUP_DB * 100).toInt(), p.makeupGainMb)
    }

    @Test fun withoutLoudnessEnhancerMakeupGoesIntoBands() {
        val p = plan(enhancer = false)
        assertEquals(0, p.makeupGainMb)
        assertTrue(p.nativeLevelsMb.all { it == 300 })
    }

    @Test fun tenBandHardwareGetsTheCurveExactly() {
        val bass = EqPresets.byId("bass")!!.gainsDb
        val p = plan(gains = bass, centers = tenBand, levelMatch = 0f)
        val shift = EqMath.perceivedDeltaDb(bass).coerceAtLeast(0f)
        assertEquals(bass.map { ((it - shift) * 100).roundToInt() }, p.nativeLevelsMb)
    }

    @Test fun levelsAreClampedToHardwareRange() {
        val p = EqMath.plan(true, List(10) { -12f }, 0f, false, null, fiveBand, -600..600, true, true)
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

    @Test fun presetsHaveTenBands() {
        assertTrue(EqPresets.ALL.all { it.gainsDb.size == 10 })
    }
}
