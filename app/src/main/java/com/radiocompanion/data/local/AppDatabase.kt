package com.radiocompanion.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.radiocompanion.model.Station

@Database(entities = [Station::class], version = 3, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao

    companion object {
        const val NAME = "stations.db"

        /** v1 → v2: station categories (official FM vs online) and backup stream URLs. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stations ADD COLUMN backupStreamUrls TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE stations ADD COLUMN isOfficialFm INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stations ADD COLUMN frequency TEXT")
            }
        }

        /**
         * v2 → v3: drop the FM frequency column (streaming-only app, no tuner).
         * SQLite on Android 10–11 has no DROP COLUMN, so the table is rebuilt.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE stations_new (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        streamUrl TEXT NOT NULL,
                        backupStreamUrls TEXT NOT NULL DEFAULT '',
                        logoUrl TEXT,
                        tags TEXT NOT NULL,
                        countryCode TEXT NOT NULL,
                        codec TEXT NOT NULL,
                        bitrate INTEGER NOT NULL,
                        isHls INTEGER NOT NULL,
                        isOfficialFm INTEGER NOT NULL DEFAULT 0,
                        isFavorite INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
                val columns = "id, name, streamUrl, backupStreamUrls, logoUrl, tags, countryCode, " +
                    "codec, bitrate, isHls, isOfficialFm, isFavorite, source, sortOrder"
                db.execSQL("INSERT INTO stations_new ($columns) SELECT $columns FROM stations")
                db.execSQL("DROP TABLE stations")
                db.execSQL("ALTER TABLE stations_new RENAME TO stations")
            }
        }
    }
}
