package com.radiocompanion.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/** Where a station came from. */
@Serializable
enum class StationSource { BUNDLED, RADIO_BROWSER, CUSTOM, IMPORTED }

/**
 * One radio station: a Room row and also the JSON import/export format.
 *
 * JSON example:
 * {
 *   "id": "eg-nogoum-fm",
 *   "name": "Nogoum FM 100.6",
 *   "streamUrl": "https://audio.nrpstream.com/listen/nogoumfm/radio.mp3",
 *   "backupStreamUrls": [],
 *   "country": "EG",
 *   "isOfficialFm": true,
 *   "logoUrl": null
 * }
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@Entity(tableName = "stations")
data class Station(
    @PrimaryKey val id: String,
    val name: String,
    val streamUrl: String,
    /** Tried in order when [streamUrl] fails. Stations change servers often. */
    @ColumnInfo(defaultValue = "''")
    val backupStreamUrls: List<String> = emptyList(),
    val logoUrl: String? = null,
    val tags: String = "",
    /** ISO 3166-1 alpha-2 code, e.g. "EG". Older backups used "countryCode". */
    @SerialName("country") @JsonNames("countryCode")
    val countryCode: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val isHls: Boolean = false,
    /**
     * Station type for filtering: an established FM broadcaster's online stream
     * (true) vs. a web-only station (false). Playback is always internet streaming;
     * the app has no tuner.
     */
    @ColumnInfo(defaultValue = "0")
    val isOfficialFm: Boolean = false,
    val isFavorite: Boolean = false,
    val source: StationSource = StationSource.CUSTOM,
    val sortOrder: Int = 0,
)

/** Top-level shape of an exported station list file. */
@Serializable
data class StationBackup(
    val formatVersion: Int = 2,
    val exportedAtEpochMs: Long,
    val stations: List<Station>,
)
