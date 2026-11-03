package com.radiocompanion.service

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.radiocompanion.model.Station

fun isHlsUrl(url: String): Boolean = url.substringBefore('?').lowercase().endsWith(".m3u8")

fun Station.toMediaItem(): MediaItem {
    val uri = streamUrl.toUri()
    val subtitle = buildList {
        addAll(tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(3))
        if (bitrate > 0) add("$bitrate kbps")
    }.joinToString(" · ")

    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setMimeType(if (isHls || isHlsUrl(streamUrl)) MimeTypes.APPLICATION_M3U8 else null)
        // The URI can be stripped when an item crosses from controller to session,
        // so it's also kept in requestMetadata.
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setStation(name)
                .setArtist(subtitle.ifEmpty { null })
                .setArtworkUri(logoUrl?.takeIf { it.isNotBlank() }?.toUri())
                .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build()
        )
        .build()
}
