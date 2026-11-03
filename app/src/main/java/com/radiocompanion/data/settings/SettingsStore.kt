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
import java.util.concurrent.TimeUnit

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Saved equalizer settings. */
data class StoredEq(
    val enabled: Boolean = false,
    val gainsDb: List<Float> = List(7) { 0f },
    val presetId: String = "flat",
    val loudness: Boolean = false,
    val levelMatchDb: Float = 3f,
)

class SettingsStore(private val context: Context) {

    suspend fun loadEq(): StoredEq {
        val p = context.dataStore.data.first()
        val gains = p[EQ_GAINS]?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 7 }
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
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_GAINS = stringPreferencesKey("eq_gains_db")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_LOUDNESS = booleanPreferencesKey("eq_loudness")
        val EQ_LEVEL_MATCH = floatPreferencesKey("eq_level_match_db")
    }
}
