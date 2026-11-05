package com.radiocompanion.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.radiocompanion.data.local.AppDatabase
import com.radiocompanion.data.remote.PlaylistResolver
import com.radiocompanion.data.remote.RadioBrowserApi
import com.radiocompanion.data.remote.RbStation
import com.radiocompanion.data.remote.toStation
import com.radiocompanion.model.Station
import com.radiocompanion.model.StationBackup
import com.radiocompanion.model.StationSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** What the user fills in when adding a station by hand. */
data class NewStation(
    val name: String,
    val streamUrl: String,
    val backupUrl: String = "",
    val countryCode: String,
    val isOfficialFm: Boolean,
)

class StationRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val api: RadioBrowserApi,
    private val resolver: PlaylistResolver,
    private val json: Json,
) {
    private val dao = db.stationDao()

    fun observeAll(): Flow<List<Station>> = dao.observeAll()
    fun observeFavorites(): Flow<List<Station>> = dao.observeFavorites()

    suspend fun favoriteCount(): Int = dao.favoriteCount()
    suspend fun getById(id: String): Station? = dao.getById(id)
    fun observeById(id: String): Flow<Station?> = dao.observeById(id)

    /**
     * Merges the built-in stations from assets/stations.json (e.g. Nogoum FM).
     * Runs only when that file changed since the last merge ([lastHash]), so a
     * station the user deleted stays deleted until the bundled list is updated.
     * Existing rows keep the user's favorite flag; URL/name/logo come from the file.
     * Returns the new hash to store, or null if nothing was done.
     */
    suspend fun syncBundledStations(lastHash: String?): String? = withContext(Dispatchers.IO) {
        val text = runCatching {
            context.assets.open(BUNDLED_FILE).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return@withContext null
        val hash = Integer.toHexString(text.hashCode())
        if (hash == lastHash) return@withContext null

        val bundled = decodeStations(text).mapIndexed { i, s ->
            s.copy(source = StationSource.BUNDLED, sortOrder = -1_000 + i, isHls = s.isHls || isHls(s.streamUrl))
        }
        val merged = bundled.map { b ->
            val existing = dao.getById(b.id) ?: return@map b
            b.copy(isFavorite = existing.isFavorite)
        }
        dao.upsert(merged)
        hash
    }

    /**
     * Reloads the directory: home country, Arabic-language (regional) and worldwide
     * top stations, fetched in parallel. One group failing doesn't lose the others.
     * Keeps favorites and the user's own edits (official/online flag, working URLs).
     */
    suspend fun refreshFromRadioBrowser(homeCountry: String): Int = coroutineScope {
        val groups = listOf(
            async { runCatching { api.byCountry(homeCountry, 200) } },
            async { runCatching { api.byLanguage("arabic", 200) } },
            async { runCatching { api.topWorldwide(150) } },
        ).map { it.await() }
        if (groups.all { it.isFailure }) throw groups.first().exceptionOrNull()!!

        val fetched: List<RbStation> = groups
            .flatMap { it.getOrDefault(emptyList()) }
            .filter { it.playableUrl.startsWith("http", ignoreCase = true) }
            .distinctBy { it.uuid }

        db.withTransaction {
            val existing = dao.getBySource(StationSource.RADIO_BROWSER.name).associateBy { it.id }
            val merged = fetched.mapIndexed { i, rb ->
                val fresh = rb.toStation(i)
                val old = existing[fresh.id] ?: return@mapIndexed fresh
                fresh.copy(
                    isFavorite = old.isFavorite,
                    isOfficialFm = old.isOfficialFm,
                    // Directory URL first (it follows station moves), then everything that worked before.
                    backupStreamUrls = (fresh.backupStreamUrls + old.streamUrl + old.backupStreamUrls)
                        .filter { it != fresh.streamUrl }.distinct().take(MAX_BACKUPS),
                )
            }
            dao.deleteNonFavoritesFrom(StationSource.RADIO_BROWSER.name)
            dao.upsert(merged)
            merged.size
        }
    }

    suspend fun addCustom(input: NewStation): Station {
        val resolved = resolver.resolve(input.streamUrl.trim())
        val backup = input.backupUrl.trim().takeIf { it.startsWith("http", ignoreCase = true) }
            ?.let { resolver.resolve(it) }
        val station = Station(
            id = "custom:" + Integer.toHexString(resolved.hashCode()),
            name = input.name.trim().ifBlank { resolved.substringAfter("://").substringBefore('/') },
            streamUrl = resolved,
            backupStreamUrls = listOfNotNull(backup).filter { it != resolved },
            countryCode = input.countryCode.uppercase(),
            isHls = isHls(resolved),
            isOfficialFm = input.isOfficialFm,
            isFavorite = true,
            source = StationSource.CUSTOM,
            sortOrder = 1_000,
        )
        dao.upsert(listOf(station))
        return station
    }

    suspend fun setFavorite(id: String, favorite: Boolean) = dao.setFavorite(id, favorite)

    suspend fun setOfficialFm(id: String, official: Boolean) {
        val s = dao.getById(id) ?: return
        dao.upsert(listOf(s.copy(isOfficialFm = official)))
    }

    /** A backup URL played fine: make it the main one so next time starts instantly. */
    suspend fun promoteWorkingUrl(id: String, url: String) {
        val s = dao.getById(id) ?: return
        if (s.streamUrl == url) return
        dao.upsert(
            listOf(
                s.copy(
                    streamUrl = url,
                    isHls = isHls(url),
                    backupStreamUrls = (listOf(s.streamUrl) + s.backupStreamUrls)
                        .filter { it != url }.distinct().take(MAX_BACKUPS),
                )
            )
        )
    }

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun exportJson(): String {
        val backup = StationBackup(
            exportedAtEpochMs = System.currentTimeMillis(),
            stations = dao.getExportable(),
        )
        return json.encodeToString(StationBackup.serializer(), backup)
    }

    /** Accepts a full backup object or a bare list of stations. Returns how many were imported. */
    suspend fun importJson(text: String): Int {
        val stations = decodeStations(text)
            .filter { it.streamUrl.startsWith("http", ignoreCase = true) }
            .map {
                val withSource = if (it.source == StationSource.RADIO_BROWSER) it else it.copy(source = StationSource.IMPORTED)
                withSource.copy(isHls = it.isHls || isHls(it.streamUrl))
            }
        require(stations.isNotEmpty()) { "No stations found in file" }
        dao.upsert(stations)
        return stations.size
    }

    private fun decodeStations(text: String): List<Station> =
        runCatching { json.decodeFromString(StationBackup.serializer(), text).stations }
            .recoverCatching { json.decodeFromString(ListSerializer(Station.serializer()), text) }
            .getOrThrow()

    private fun isHls(url: String) = url.substringBefore('?').lowercase().endsWith(".m3u8")

    private companion object {
        const val BUNDLED_FILE = "stations.json"
        const val MAX_BACKUPS = 5
    }
}
