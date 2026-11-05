package com.radiocompanion.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.radiocompanion.R
import com.radiocompanion.model.Station
import com.radiocompanion.service.NowPlaying
import com.radiocompanion.ui.theme.CarDimens

@Composable
fun StationGrid(
    stations: List<Station>,
    currentId: String?,
    onSelect: (Int) -> Unit,
    onFavorite: (Station) -> Unit,
    onLongPress: (Station) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 260.dp),   // ~4 columns landscape, 2 portrait/docked
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(stations, key = { _, s -> s.id }) { index, station ->
            StationTile(
                station = station,
                selected = station.id == currentId,
                onClick = { onSelect(index) },
                onFavorite = { onFavorite(station) },
                onLongPress = { onLongPress(station) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationTile(
    station: Station,
    selected: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onLongPress: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = shape,
        color = if (selected) colors.surfaceVariant else colors.surface,
        border = if (selected) BorderStroke(3.dp, colors.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .height(CarDimens.TileHeight)
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            StationLogo(station.logoUrl, size = 72.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    station.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypeBadge(station)
                    val kbps = if (station.bitrate > 0) stringResource(R.string.bitrate_kbps, station.bitrate) else null
                    val subtitle = buildList {
                        addAll(station.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(2))
                        if (kbps != null) add(kbps)
                    }.joinToString(" · ")
                    if (subtitle.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            IconButton(onClick = onFavorite, modifier = Modifier.size(CarDimens.MinTouch)) {
                Icon(
                    if (station.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(if (station.isFavorite) R.string.cd_remove_favorite else R.string.cd_add_favorite),
                    tint = if (station.isFavorite) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

/** Station type: "FM" = an FM broadcaster's online stream, "WEB" = web-only radio. */
@Composable
fun TypeBadge(station: Station) {
    val official = station.isOfficialFm
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (official) colors.primary else colors.surfaceVariant,
        contentColor = if (official) colors.onPrimary else colors.onSurfaceVariant,
    ) {
        Text(
            stringResource(if (official) R.string.badge_fm else R.string.badge_online),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun StationLogo(url: String?, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 5))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Radio, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size * 0.5f),
        )
        if (!url.isNullOrBlank()) {
            // Drawn over the placeholder; broken logos simply leave the radio icon visible.
            AsyncImage(
                model = url, contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun statusLine(now: NowPlaying): String? = when {
    now.hasError && now.wantsToPlay -> stringResource(R.string.np_reconnecting)
    now.hasError -> stringResource(
        R.string.np_unavailable,
        now.errorDetail?.resolve() ?: stringResource(R.string.np_stream_failed),
    )
    now.isBuffering -> stringResource(R.string.np_buffering)
    else -> now.streamTitle
}

/**
 * Big player shown beside (landscape) or above (portrait) the station list.
 * [isFavorite] is null when nothing bookmarkable is playing; the heart is hidden then.
 */
@Composable
fun NowPlayingPanel(
    now: NowPlaying,
    isFavorite: Boolean?,
    onToggleFavorite: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                StationLogo(now.artworkUri?.toString(), size = 150.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        now.stationName.ifBlank { stringResource(R.string.np_pick_station) },
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        // Keeps long names clear of the heart in the corner.
                        modifier = Modifier.padding(horizontal = 56.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        statusLine(now) ?: " ",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (now.hasError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                TransportControls(now, onPrevious, onPlayPause, onNext, large = true)
            }
            if (isFavorite != null) {
                FavoriteToggle(
                    isFavorite = isFavorite,
                    onToggle = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                )
            }
        }
    }
}

/** Large heart for bookmarking while driving: full car touch target, filled when saved. */
@Composable
fun FavoriteToggle(isFavorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (isFavorite) 1.1f else 1f, label = "heartScale")
    IconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggle()
        },
        modifier = modifier.size(CarDimens.MinTouch),
    ) {
        Icon(
            if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = stringResource(if (isFavorite) R.string.cd_remove_favorite else R.string.cd_add_favorite),
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp).scale(scale),
        )
    }
}

@Composable
fun MiniPlayerBar(
    now: NowPlaying,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth().height(112.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            StationLogo(now.artworkUri?.toString(), size = 72.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    now.stationName.ifBlank { stringResource(R.string.np_pick_station) },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                statusLine(now)?.let {
                    Text(
                        it, style = MaterialTheme.typography.labelLarge,
                        color = if (now.hasError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TransportControls(now, onPrevious, onPlayPause, onNext, large = false)
        }
    }
}

@Composable
fun TransportControls(
    now: NowPlaying,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    large: Boolean,
) {
    val side = if (large) 88.dp else CarDimens.MinTouch
    val main = if (large) CarDimens.PlayButton else 88.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (large) 28.dp else 4.dp),
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(side)) {
            Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.cd_previous), Modifier.size(side * 0.55f))
        }
        FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(main), shape = CircleShape) {
            if (now.isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(main * 0.45f),
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    if (now.wantsToPlay) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (now.wantsToPlay) R.string.cd_pause else R.string.cd_play),
                    modifier = Modifier.size(main * 0.55f),
                )
            }
        }
        IconButton(onClick = onNext, modifier = Modifier.size(side)) {
            Icon(Icons.Rounded.SkipNext, stringResource(R.string.cd_next), Modifier.size(side * 0.55f))
        }
    }
}
