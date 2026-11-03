package com.radiocompanion.service.audio

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.radiocompanion.data.settings.SettingsStore
import com.radiocompanion.data.settings.StoredEq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the equalizer screen shows. */
data class EqState(
    val enabled: Boolean = false,
    val gainsDb: List<Float> = List(EqMath.BAND_COUNT) { 0f },
    val presetId: String = "flat",
    val loudness: Boolean = false,
    val levelMatchDb: Float = 3f,
    // What the head unit supports (known once playback has started).
    val attached: Boolean = false,
    val equalizerAvailable: Boolean = true,
    val hardwareBassBoost: Boolean = true,
    val makeupGainAvailable: Boolean = true,
    val nativeBandCount: Int = 0,
    // What is currently applied, for display.
    val appliedMakeupDb: Float = 0f,
)

/**
 * Owns the Android audio effects for the player's audio session:
 * - [Equalizer]: the 7-band curve, mapped onto the head unit's native bands.
 * - [LoudnessEnhancer]: makeup gain, so the EQ doesn't make the radio quieter.
 * - [BassBoost]: the "Loudness" switch, stronger at low volume. If the head unit
 *   has no BassBoost, a low-shelf boost is added to the EQ's lowest bands instead.
 *
 * Lives in the Application (same process as the playback service and the UI).
 * Every effect call is wrapped: some ROMs throw on effects they don't support.
 */
class AudioEffectsController(
    private val context: Context,
    private val settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var makeupGain: LoudnessEnhancer? = null
    private var nativeCentersHz: List<Float> = emptyList()
    private var nativeRangeMb: IntRange = -1500..1500
    private var loaded = false
    private var saveJob: Job? = null

    init {
        scope.launch {
            val saved = settings.loadEq()
            _state.update {
                it.copy(
                    enabled = saved.enabled,
                    gainsDb = saved.gainsDb.map(EqMath::snap),
                    presetId = saved.presetId,
                    loudness = saved.loudness,
                    levelMatchDb = saved.levelMatchDb,
                )
            }
            loaded = true
            apply()
        }
    }

    // ------------------------------------------------------------- session binding

    /** Called by the playback service with ExoPlayer's audio session id. */
    fun attach(audioSessionId: Int) {
        releaseEffects()
        if (audioSessionId <= 0) return

        equalizer = create("Equalizer") { Equalizer(PRIORITY, audioSessionId) }
        bassBoost = create("BassBoost") { BassBoost(PRIORITY, audioSessionId) }
            ?.takeIf { bb -> runCatching { bb.strengthSupported }.getOrDefault(false) || run { bb.release(); false } }
        makeupGain = create("LoudnessEnhancer") { LoudnessEnhancer(audioSessionId) }

        equalizer?.let { eq ->
            runCatching {
                val bands = eq.numberOfBands.toInt()
                nativeCentersHz = (0 until bands).map { eq.getCenterFreq(it.toShort()) / 1000f }
                val range = eq.bandLevelRange
                nativeRangeMb = range[0].toInt()..range[1].toInt()
            }.onFailure {
                Log.w(TAG, "Equalizer reported no bands", it)
                eq.release()
                equalizer = null
            }
        }

        // Another app (or the system) can take an effect away and give it back.
        listOfNotNull(equalizer, bassBoost, makeupGain).forEach { fx ->
            runCatching { fx.setControlStatusListener { _, granted -> if (granted) main.post { apply() } } }
        }

        _state.update {
            it.copy(
                attached = true,
                equalizerAvailable = equalizer != null,
                hardwareBassBoost = bassBoost != null,
                makeupGainAvailable = makeupGain != null,
                nativeBandCount = nativeCentersHz.size,
            )
        }
        registerVolumeObserver()
        apply()
    }

    fun detach() {
        unregisterVolumeObserver()
        releaseEffects()
        _state.update { it.copy(attached = false, appliedMakeupDb = 0f) }
    }

    // ------------------------------------------------------------- user actions

    fun setEnabled(on: Boolean) = change { it.copy(enabled = on) }

    fun setBandGain(index: Int, db: Float) = change { s ->
        val gains = s.gainsDb.toMutableList().also { it[index] = EqMath.snap(db) }
        s.copy(gainsDb = gains, presetId = EqPresets.CUSTOM_ID, enabled = true)
    }

    fun applyPreset(preset: EqPreset) = change { it.copy(gainsDb = preset.gainsDb, presetId = preset.id, enabled = true) }

    fun setLoudness(on: Boolean) = change { it.copy(loudness = on) }

    fun setLevelMatch(db: Float) = change { it.copy(levelMatchDb = db.coerceIn(0f, EqMath.MAX_LEVEL_MATCH_DB)) }

    fun reset() = change {
        it.copy(gainsDb = List(EqMath.BAND_COUNT) { 0f }, presetId = "flat", levelMatchDb = 3f)
    }

    private fun change(transform: (EqState) -> EqState) {
        _state.update(transform)
        apply()
        scheduleSave()
    }

    // ------------------------------------------------------------- apply

    private fun apply() {
        if (!loaded) return
        val s = _state.value
        val plan = EqMath.plan(
            eqEnabled = s.enabled,
            gainsDb = s.gainsDb,
            levelMatchDb = s.levelMatchDb,
            loudness = s.loudness,
            volumeFraction = volumeFraction(),
            nativeCentersHz = nativeCentersHz,
            nativeRangeMb = nativeRangeMb,
            hasHardwareBassBoost = bassBoost != null,
            hasMakeupGainEffect = makeupGain != null,
        )

        equalizer?.let { eq ->
            safely("Equalizer") {
                plan.nativeLevelsMb.forEachIndexed { band, mb -> eq.setBandLevel(band.toShort(), mb.toShort()) }
                eq.setEnabled(plan.equalizerOn)
            }
        }
        makeupGain?.let { fx ->
            safely("LoudnessEnhancer") {
                fx.setTargetGain(plan.makeupGainMb)
                fx.setEnabled(plan.makeupGainMb > 0)
            }
        }
        bassBoost?.let { bb ->
            safely("BassBoost") {
                bb.setStrength(plan.bassBoostStrength.toShort())
                bb.setEnabled(plan.bassBoostStrength > 0)
            }
        }

        val applied = if (makeupGain != null) plan.makeupGainDb else 0f
        if (applied != s.appliedMakeupDb) _state.update { it.copy(appliedMakeupDb = applied) }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)   // sliders fire many changes; save once they settle
            val s = _state.value
            settings.saveEq(StoredEq(s.enabled, s.gainsDb, s.presetId, s.loudness, s.levelMatchDb))
        }
    }

    // ------------------------------------------------------------- volume (for Loudness)

    private val volumeObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            if (_state.value.loudness) apply()
        }
    }
    private var observing = false

    private fun registerVolumeObserver() {
        if (observing) return
        observing = runCatching {
            context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver)
        }.isSuccess
    }

    private fun unregisterVolumeObserver() {
        if (!observing) return
        runCatching { context.contentResolver.unregisterContentObserver(volumeObserver) }
        observing = false
    }

    /**
     * Media volume 0..1. On some BYD units the amplifier owns the volume and
     * Android always reports the same value; Loudness then uses a fixed medium strength.
     */
    private fun volumeFraction(): Float? = runCatching {
        val am = audioManager ?: return null
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) null else am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }.getOrNull()

    // ------------------------------------------------------------- helpers

    private fun <T : AudioEffect> create(name: String, block: () -> T): T? =
        runCatching(block).onFailure { Log.w(TAG, "$name not available on this head unit", it) }.getOrNull()

    private inline fun safely(name: String, block: () -> Unit) {
        runCatching(block).onFailure { Log.w(TAG, "$name call failed", it) }
    }

    private fun releaseEffects() {
        listOfNotNull(equalizer, bassBoost, makeupGain).forEach { runCatching { it.release() } }
        equalizer = null
        bassBoost = null
        makeupGain = null
        nativeCentersHz = emptyList()
    }

    private companion object {
        const val TAG = "RdmAudioFx"
        /** Above the default 0 so we keep control of effects on our own session. */
        const val PRIORITY = 1
    }
}
