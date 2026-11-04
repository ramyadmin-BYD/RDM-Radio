package com.radiocompanion.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.radiocompanion.service.audio.EqMath
import java.util.concurrent.TimeUnit

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Saved equalizer settings. */
data class StoredEq(
    val enabled: Boolean = false,
    val gainsDb: List<Float> = List(EqMath.BAND_COUNT) { 0f },
    val presetId: String = "flat",
    val loudness: Boolean = false,
    val levelMatchDb: Float = 3f,
)

/** App theme choice. DARK is the default: best for night driving. */
enum class ThemeMode { DARK, LIGHT, SYSTEM }

class SettingsStore(private val context: Context) {

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { p ->
        p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[THEME] = mode.name }
    }

    /** Start the last station automatically when the app opens. On by default. */
    val autoPlayLastStation: Flow<Boolean> = context.dataStore.data.map { it[AUTO_PLAY] ?: true }

    suspend fun setAutoPlayLastStation(on: Boolean) {
        context.dataStore.edit { it[AUTO_PLAY] = on }
    }

    /** At most one automatic update check every 12 hours (GitHub allows 60 requests/hour per IP). */
    suspend fun isUpdateCheckDue(): Boolean {
        val last = context.dataStore.data.first()[LAST_UPDATE_CHECK] ?: 0L
        return System.currentTimeMillis() - last > TimeUnit.HOURS.toMillis(12)
    }

    suspend fun markUpdateChecked() {
        context.dataStore.edit { it[LAST_UPDATE_CHECK] = System.currentTimeMillis() }
    }

    suspend fun loadEq(): StoredEq {
        val p = context.dataStore.data.first()
        val saved = p[EQ_GAINS]?.split(',')?.mapNotNull { it.toFloatOrNull() }
        val gains = when (saved?.size) {
            EqMath.BAND_COUNT -> saved
            EqMath.LEGACY_7_BAND_HZ.size -> EqMath.fromLegacy7Band(saved)   // settings from v1.0.x
            else -> null
        }
        val d = StoredEq()
        return StoredEq(
            enabled = p[EQ_ENABLED] ?: d.enabled,
            gainsDb = gains ?: d.gainsDb,
            presetId = p[EQ_PRESET] ?: d.presetId,
            loudness = p[EQ_LOUDNESS] ?: d.loudness,
            levelMatchDb = p[EQ_LEVEL_MATCH] ?: d.levelMatchDb,
        )
    }

    suspend fun saveEq(eq: StoredEq) {
        context.dataStore.edit {
            it[EQ_ENABLED] = eq.enabled
            it[EQ_GAINS] = eq.gainsDb.joinToString(",")
            it[EQ_PRESET] = eq.presetId
            it[EQ_LOUDNESS] = eq.loudness
            it[EQ_LEVEL_MATCH] = eq.levelMatchDb
        }
    }

    val countryCode: Flow<String> = context.dataStore.data.map { it[COUNTRY] ?: defaultCountry() }

    suspend fun setCountryCode(code: String) {
        context.dataStore.edit { it[COUNTRY] = code.uppercase() }
    }

    /** True on first launch and once a week, so the station list stays fresh. */
    suspend fun isRefreshDue(): Boolean {
        val last = context.dataStore.data.first()[LAST_REFRESH] ?: 0L
        return System.currentTimeMillis() - last > TimeUnit.DAYS.toMillis(7)
    }

    suspend fun markRefreshed() {
        context.dataStore.edit { it[LAST_REFRESH] = System.currentTimeMillis() }
    }

    /** Fingerprint of the last assets/stations.json merged into the database. */
    suspend fun bundledStationsHash(): String? = context.dataStore.data.first()[BUNDLED_HASH]

    suspend fun setBundledStationsHash(hash: String) {
        context.dataStore.edit { it[BUNDLED_HASH] = hash }
    }

    suspend fun batteryPromptShown(): Boolean = context.dataStore.data.first()[BATTERY_ASKED] ?: false

    suspend fun markBatteryPromptShown() {
        context.dataStore.edit { it[BATTERY_ASKED] = true }
    }

    private fun defaultCountry(): String =
        DEFAULT_COUNTRY

    private companion object {
        val COUNTRY = stringPreferencesKey("country_code")
        val LAST_REFRESH = longPreferencesKey("last_refresh_ms")
        val BATTERY_ASKED = booleanPreferencesKey("battery_prompt_shown")
        const val DEFAULT_COUNTRY = "EG"
        val BUNDLED_HASH = stringPreferencesKey("bundled_stations_hash")
        val THEME = stringPreferencesKey("theme_mode")
        val AUTO_PLAY = booleanPreferencesKey("auto_play_last_station")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_ms")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_GAINS = stringPreferencesKey("eq_gains_db")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_LOUDNESS = booleanPreferencesKey("eq_loudness")
        val EQ_LEVEL_MATCH = floatPreferencesKey("eq_level_match_db")
    }
}
