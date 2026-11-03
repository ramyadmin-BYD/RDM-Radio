package com.radiocompanion.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.radiocompanion.model.Station
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Query("SELECT * FROM stations ORDER BY isFavorite DESC, sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Station>>

    @Query("SELECT * FROM stations WHERE isFavorite = 1 ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeFavorites(): Flow<List<Station>>

    @Query("SELECT * FROM stations WHERE isFavorite = 1 ORDER BY sortOrder, name COLLATE NOCASE")
    suspend fun getFavorites(): List<Station>

    @Query("SELECT * FROM stations ORDER BY isFavorite DESC, sortOrder, name COLLATE NOCASE")
    suspend fun getAll(): List<Station>

    /** What goes into a backup: favorites plus anything the user added by hand. */
    @Query("SELECT * FROM stations WHERE isFavorite = 1 OR source IN ('CUSTOM', 'IMPORTED') ORDER BY sortOrder, name COLLATE NOCASE")
    suspend fun getExportable(): List<Station>

    @Query("SELECT * FROM stations WHERE id = :id")
    suspend fun getById(id: String): Station?

    @Query("SELECT * FROM stations WHERE source = :source")
    suspend fun getBySource(source: String): List<Station>

    @Query("SELECT id FROM stations WHERE isFavorite = 1")
    suspend fun favoriteIds(): List<String>

    @Query("SELECT COUNT(*) FROM stations WHERE isFavorite = 1")
    suspend fun favoriteCount(): Int

    @Upsert
    suspend fun upsert(stations: List<Station>)

    @Query("UPDATE stations SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("DELETE FROM stations WHERE id = :id")
    suspend fun delete(id: String)

    /** Drops the previous country's list but keeps anything the user favorited. */
    @Query("DELETE FROM stations WHERE source = :source AND isFavorite = 0")
    suspend fun deleteNonFavoritesFrom(source: String)

    @Query("SELECT COUNT(*) FROM stations")
    suspend fun count(): Int
}
