package com.radiocompanion.service

import android.content.Context
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes

/** Remembers the last station so playback can resume after BYD's memory cleaner kills the app. */
class LastStationStore(context: Context) {

    private val prefs = context.getSharedPreferences("last_station", Context.MODE_PRIVATE)

    fun save(item: MediaItem) {
        val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri ?: return
        prefs.edit {
            putString(KEY_ID, item.mediaId)
            putString(KEY_URI, uri.toString())
            putString(KEY_NAME, (item.mediaMetadata.station ?: item.mediaMetadata.title)?.toString())
            putString(KEY_ART, item.mediaMetadata.artworkUri?.toString())
        }
    }

    fun load(): MediaItem? {
        val uri = prefs.getString(KEY_URI, null) ?: return null
        val name = prefs.getString(KEY_NAME, null).orEmpty()
        return MediaItem.Builder()
            .setMediaId(prefs.getString(KEY_ID, null) ?: uri)
            .setUri(uri)
            .setMimeType(if (isHlsUrl(uri)) MimeTypes.APPLICATION_M3U8 else null)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri.toUri()).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setStation(name)
                    .setArtworkUri(prefs.getString(KEY_ART, null)?.toUri())
                    .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build()
            )
            .build()
    }

    private companion object {
        const val KEY_ID = "id"
        const val KEY_URI = "uri"
        const val KEY_NAME = "name"
        const val KEY_ART = "art"
    }
}
