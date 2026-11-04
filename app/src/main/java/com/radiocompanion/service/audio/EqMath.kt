package com.radiocompanion.service.audio

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure equalizer math (no Android types, so it is unit-tested on the JVM).
 *
 * The UI always shows 10 bands (ISO octave centres, 31 Hz – 16 kHz). Head units
 * report their own native band count (often 5: 60 / 230 / 910 / 3.6k / 14k Hz,
 * sometimes 10). Each native band takes the value of the 10-band curve at its
 * centre frequency, interpolated on a log-frequency axis, so any native layout
 * follows the curve the user drew.
 */
object EqMath {

    /** UI band centres (Hz): standard 10-band octave layout. */
    val BAND_FREQUENCIES_HZ = floatArrayOf(
        31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f,
    )
    val BAND_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    const val BAND_COUNT = 10

    /** The 7-band layout used by version 1.0.x, for converting saved settings. */
    val LEGACY_7_BAND_HZ = floatArrayOf(60f, 150f, 400f, 1_000f, 2_500f, 6_000f, 15_000f)

    const val UI_MIN_DB = -12f
    const val UI_MAX_DB = 12f
    const val STEP_DB = 0.5f

    /** Never add more than this much makeup gain (protects speakers and ears). */
    const val MAX_MAKEUP_DB = 12f
    const val MAX_LEVEL_MATCH_DB = 9f

    /** Low-shelf boost used when the head unit has no BassBoost effect. */
    const val SOFTWARE_BASS_MAX_DB = 6f

    /**
     * How much each band contributes to perceived loudness (ear is most sensitive
     * around 1–4 kHz). Used to estimate how much louder/quieter the EQ curve sounds.
     */
    private val PERCEPTUAL_WEIGHTS = floatArrayOf(0.4f, 0.6f, 0.8f, 0.9f, 1.0f, 1.2f, 1.2f, 1.1f, 0.9f, 0.6f)

    fun snap(db: Float): Float =
        ((db / STEP_DB).roundToInt() * STEP_DB).coerceIn(UI_MIN_DB, UI_MAX_DB)

    /** Value of the 10-band curve at [freqHz]; flat beyond the outermost bands. */
    fun interpolate(gainsDb: List<Float>, freqHz: Float): Float {
        require(gainsDb.size == BAND_COUNT) { "Expected $BAND_COUNT gains" }
        return interpolateCurve(BAND_FREQUENCIES_HZ, gainsDb, freqHz)
    }

    /** Converts saved 7-band gains (v1.0.x) to the 10-band layout, keeping the curve's shape. */
    fun fromLegacy7Band(gainsDb: List<Float>): List<Float> {
        require(gainsDb.size == LEGACY_7_BAND_HZ.size)
        return BAND_FREQUENCIES_HZ.map { snap(interpolateCurve(LEGACY_7_BAND_HZ, gainsDb, it)) }
    }

    private fun interpolateCurve(freqs: FloatArray, gainsDb: List<Float>, freqHz: Float): Float {
        if (freqHz <= freqs.first()) return gainsDb.first()
        if (freqHz >= freqs.last()) return gainsDb.last()
        val i = (0 until freqs.size - 1).first { freqHz < freqs[it + 1] }
        val t = (ln(freqHz) - ln(freqs[i])) / (ln(freqs[i + 1]) - ln(freqs[i]))
        return gainsDb[i] + (gainsDb[i + 1] - gainsDb[i]) * t
    }

    /** Estimated loudness change of the curve, in dB (negative = curve sounds quieter). */
    fun perceivedDeltaDb(gainsDb: List<Float>): Float {
        var sum = 0f
        var weights = 0f
        gainsDb.forEachIndexed { i, g ->
            sum += g * PERCEPTUAL_WEIGHTS[i]
            weights += PERCEPTUAL_WEIGHTS[i]
        }
        return sum / weights
    }

    /**
     * Loudness (Fletcher–Munson) compensation: more bass boost at low volume,
     * less at high volume. [volumeFraction] is 0..1, or null when unknown.
     * Returns BassBoost strength 0..1000.
     */
    fun loudnessStrength(volumeFraction: Float?): Int {
        val v = (volumeFraction ?: 0.5f).coerceIn(0f, 1f)
        return (1000f * (0.3f + 0.7f * (1f - v))).roundToInt().coerceIn(0, 1000)
    }

    /** Software low-shelf: full boost below 100 Hz, fading to 0 dB at 400 Hz. */
    fun softwareBassDb(freqHz: Float, strength: Int): Float {
        if (strength <= 0) return 0f
        val full = SOFTWARE_BASS_MAX_DB * (strength / 1000f)
        return when {
            freqHz <= 100f -> full
            freqHz >= 400f -> 0f
            else -> full * (1f - (ln(freqHz) - ln(100f)) / (ln(400f) - ln(100f)))
        }
    }

    /** What to send to the audio effects. */
    data class Plan(
        /** Band levels for the native Equalizer, in millibels, one per native band. */
        val nativeLevelsMb: List<Int>,
        /** Makeup gain for LoudnessEnhancer, in millibels (0 = off). */
        val makeupGainMb: Int,
        /** BassBoost strength 0..1000 (0 = off). */
        val bassBoostStrength: Int,
        /** Whether the native Equalizer must be enabled. */
        val equalizerOn: Boolean,
    ) {
        val makeupGainDb: Float get() = makeupGainMb / 100f
    }

    /**
     * Gain compensation:
     * 1. Estimate the curve's perceived loudness change (Δ).
     * 2. If the curve sounds LOUDER (Δ > 0), lower every band by Δ. Same loudness as
     *    EQ off, and more headroom against clipping.
     * 3. Makeup gain = level match (the head unit's own EQ headroom drop, calibrated
     *    by the user; default +3 dB) + the loss from cuts (−Δ when Δ < 0).
     *    Applied by LoudnessEnhancer, which has a built-in limiter, so the
     *    extra gain doesn't clip.
     * 4. If LoudnessEnhancer isn't available, the makeup goes into the EQ bands instead.
     */
    fun plan(
        eqEnabled: Boolean,
        gainsDb: List<Float>,
        levelMatchDb: Float,
        loudness: Boolean,
        volumeFraction: Float?,
        nativeCentersHz: List<Float>,
        nativeRangeMb: IntRange,
        hasHardwareBassBoost: Boolean,
        hasMakeupGainEffect: Boolean,
    ): Plan {
        val curve = if (eqEnabled) gainsDb else List(BAND_COUNT) { 0f }
        val delta = if (eqEnabled) perceivedDeltaDb(gainsDb) else 0f
        val shiftDown = max(0f, delta)
        val makeupDb = if (eqEnabled) {
            (levelMatchDb.coerceIn(0f, MAX_LEVEL_MATCH_DB) + max(0f, -delta)).coerceIn(0f, MAX_MAKEUP_DB)
        } else 0f

        val strength = if (loudness) loudnessStrength(volumeFraction) else 0
        val softwareBass = loudness && !hasHardwareBassBoost
        val makeupInBands = if (hasMakeupGainEffect) 0f else makeupDb

        val levels = nativeCentersHz.map { hz ->
            var db = interpolate(curve, hz) - shiftDown + makeupInBands
            if (softwareBass) db += softwareBassDb(hz, strength)
            (db * 100f).roundToInt().coerceIn(nativeRangeMb.first, nativeRangeMb.last)
        }

        return Plan(
            nativeLevelsMb = levels,
            makeupGainMb = if (hasMakeupGainEffect) (makeupDb * 100f).roundToInt() else 0,
            bassBoostStrength = if (hasHardwareBassBoost) strength else 0,
            equalizerOn = eqEnabled || softwareBass,
        )
    }
}
