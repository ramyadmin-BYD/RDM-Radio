package com.radiocompanion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radiocompanion.model.Station
import com.radiocompanion.service.PlayerConnection
import com.radiocompanion.service.StreamDiagnostics
import com.radiocompanion.BuildConfig
import com.radiocompanion.ui.components.AboutDialog
import com.radiocompanion.ui.components.AddStationDialog
import com.radiocompanion.ui.components.CountryDialog
import com.radiocompanion.ui.components.DiagnosticsDialog
import com.radiocompanion.ui.components.EqualizerDialog
import com.radiocompanion.ui.components.MiniPlayerBar
import com.radiocompanion.ui.components.NowPlayingPanel
import com.radiocompanion.ui.components.StationActionsDialog
import com.radiocompanion.ui.components.StationGrid
import com.radiocompanion.ui.theme.CarDimens
import kotlinx.coroutines.launch

/**
 * Picked from the window size, not the device orientation, so the same logic
 * covers the Song L's rotating screen AND DiLink split-screen.
 *
 * Rough sizes on a 15.6" screen: landscape ~1280x720dp, portrait ~720x1280dp,
 * docked in split-screen ~600x720dp.
 */
private enum class LayoutMode { Landscape, Portrait, Docked }

/** Everything the drawer can trigger. */
private sealed interface DrawerAction {
    data object Favorites : DrawerAction
    data object AllStations : DrawerAction
    data class Region(val region: RegionFilter) : DrawerAction
    data class Type(val type: TypeFilter) : DrawerAction
    data object HomeCountry : DrawerAction
    data object Refresh : DrawerAction
    data object AddStation : DrawerAction
    data object Equalizer : DrawerAction
    data object Diagnostics : DrawerAction
    data object Import : DrawerAction
    data object Export : DrawerAction
    data object About : DrawerAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    vm: RadioViewModel,
    player: PlayerConnection,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    val stations by vm.stations.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val country by vm.country.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val region by vm.region.collectAsStateWithLifecycle()
    val type by vm.type.collectAsStateWithLifecycle()
    val hiddenByFilters by vm.hiddenByFilters.collectAsStateWithLifecycle()
    val now by player.state.collectAsStateWithLifecycle()
    val diagnostics by StreamDiagnostics.entries.collectAsStateWithLifecycle()
    val eqState by vm.audioEffects.state.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showCountry by rememberSaveable { mutableStateOf(false) }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    var showEqualizer by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Station?>(null) }

    val onDrawerAction: (DrawerAction) -> Unit = { action ->
        scope.launch { drawerState.close() }
        when (action) {
            DrawerAction.Favorites -> vm.showFavorites()
            DrawerAction.AllStations -> vm.showAll()
            is DrawerAction.Region -> vm.selectRegion(action.region)
            is DrawerAction.Type -> vm.selectType(action.type)
            DrawerAction.HomeCountry -> showCountry = true
            DrawerAction.Refresh -> vm.refresh()
            DrawerAction.AddStation -> showAdd = true
            DrawerAction.Equalizer -> showEqualizer = true
            DrawerAction.Diagnostics -> showDiagnostics = true
            DrawerAction.Import -> onImport()
            DrawerAction.Export -> onExport()
            DrawerAction.About -> showAbout = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SideMenu(
                filter = filter,
                region = region,
                type = type,
                country = country,
                refreshing = refreshing,
                eqEnabled = eqState.enabled,
                onAction = onDrawerAction,
            )
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(CarDimens.Gutter)
            ) {
                val mode = when {
                    maxWidth >= 900.dp && maxWidth > maxHeight -> LayoutMode.Landscape
                    maxHeight > maxWidth * 1.2f && maxWidth >= 560.dp -> LayoutMode.Portrait
                    else -> LayoutMode.Docked
                }

                val stationsPane: @Composable (Modifier) -> Unit = { modifier ->
                    Column(modifier) {
                        CleanHeader(
                            favoritesSelected = filter == StationFilter.FAVORITES && region == RegionFilter.ALL,
                            homeSelected = filter == StationFilter.ALL && region == RegionFilter.LOCAL,
                            country = country,
                            subtitle = viewSubtitle(filter, region, type, country),
                            onMenu = { scope.launch { drawerState.open() } },
                            onFavorites = vm::showFavorites,
                            onHomeCountry = vm::showHomeCountry,
                            eqEnabled = eqState.enabled,
                            onEqualizer = { showEqualizer = true },
                        )
                        Spacer(Modifier.height(16.dp))
                        when {
                            stations.isEmpty() && refreshing -> CenteredBox {
                                CircularProgressIndicator(Modifier.size(64.dp))
                            }
                            stations.isEmpty() -> EmptyState(
                                filter = filter,
                                hiddenByFilters = hiddenByFilters,
                                onBrowse = vm::showHomeCountry,
                                onRefresh = vm::refresh,
                                onClearFilters = vm::clearFilters,
                            )
                            else -> StationGrid(
                                stations = stations,
                                currentId = now.stationId,
                                onSelect = { index -> player.play(stations, index) },
                                onFavorite = vm::toggleFavorite,
                                onLongPress = { actionsFor = it },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                when (mode) {
                    LayoutMode.Landscape -> Row(horizontalArrangement = Arrangement.spacedBy(CarDimens.Gutter)) {
                        stationsPane(Modifier.weight(1.5f).fillMaxHeight())
                        NowPlayingPanel(
                            now, player::previous, player::togglePlayPause, player::next,
                            Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    LayoutMode.Portrait -> Column(verticalArrangement = Arrangement.spacedBy(CarDimens.Gutter)) {
                        NowPlayingPanel(
                            now, player::previous, player::togglePlayPause, player::next,
                            Modifier.fillMaxWidth().weight(0.42f),
                        )
                        stationsPane(Modifier.fillMaxWidth().weight(0.58f))
                    }
                    LayoutMode.Docked -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        stationsPane(Modifier.fillMaxWidth().weight(1f))
                        MiniPlayerBar(now, player::previous, player::togglePlayPause, player::next)
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddStationDialog(homeCountry = country, onDismiss = { showAdd = false }, onAdd = vm::addCustom)
    }
    if (showCountry) {
        CountryDialog(current = country, onDismiss = { showCountry = false }, onPick = vm::setCountry)
    }
    if (showDiagnostics) {
        DiagnosticsDialog(
            entries = diagnostics,
            onClear = StreamDiagnostics::clear,
            onDismiss = { showDiagnostics = false },
        )
    }
    if (showEqualizer) {
        val fx = vm.audioEffects
        EqualizerDialog(
            state = eqState,
            onDismiss = { showEqualizer = false },
            onEnabled = fx::setEnabled,
            onLoudness = fx::setLoudness,
            onPreset = fx::applyPreset,
            onBandChange = fx::setBandGain,
            onLevelMatch = fx::setLevelMatch,
            onReset = fx::reset,
        )
    }
    if (showAbout) {
        AboutDialog(versionName = BuildConfig.VERSION_NAME, onDismiss = { showAbout = false })
    }
    actionsFor?.let { station ->
        StationActionsDialog(
            station = station,
            onDismiss = { actionsFor = null },
            onSetOfficialFm = { vm.setOfficialFm(station, it) },
            onDelete = { vm.delete(station) },
        )
    }
}

/** Short description of what the list currently shows, e.g. "Regional · Web-only radio". */
private fun viewSubtitle(filter: StationFilter, region: RegionFilter, type: TypeFilter, country: String): String {
    val base = when {
        filter == StationFilter.FAVORITES -> "Favorites"
        region == RegionFilter.LOCAL -> countryDisplayName(country)
        region == RegionFilter.REGIONAL -> "Regional"
        region == RegionFilter.INTERNATIONAL -> "International"
        else -> "All stations"
    }
    val typeLabel = when (type) {
        TypeFilter.ALL -> null
        TypeFilter.OFFICIAL_FM -> "FM broadcasters"
        TypeFilter.ONLINE -> "Web-only radio"
    }
    return listOfNotNull(base, typeLabel).joinToString(" · ")
}

// ------------------------------------------------------------------ header

/**
 * Clean header: menu button, the two quick-access buttons, a small label of what
 * is shown, and the equalizer button. Categories and filters live in the side menu.
 */
@Composable
private fun CleanHeader(
    favoritesSelected: Boolean,
    homeSelected: Boolean,
    country: String,
    subtitle: String,
    onMenu: () -> Unit,
    onFavorites: () -> Unit,
    onHomeCountry: () -> Unit,
    eqEnabled: Boolean,
    onEqualizer: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenu, modifier = Modifier.size(CarDimens.MinTouch)) {
            Icon(Icons.Rounded.Menu, "Open menu", Modifier.size(40.dp))
        }
        Spacer(Modifier.width(12.dp))
        QuickButton("Favorites", Icons.Rounded.Favorite, favoritesSelected, onFavorites)
        Spacer(Modifier.width(12.dp))
        QuickButton(countryDisplayName(country), Icons.Rounded.Home, homeSelected, onHomeCountry)
        Spacer(Modifier.width(16.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        // Equalizer: lit in the accent colour while it is on.
        IconButton(onClick = onEqualizer, modifier = Modifier.size(CarDimens.MinTouch)) {
            Icon(
                Icons.Rounded.GraphicEq, "Equalizer", Modifier.size(36.dp),
                tint = if (eqEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun QuickButton(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        Icon(icon, contentDescription = null, Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleLarge, maxLines = 1)
    }
    val modifier = Modifier.heightIn(min = CarDimens.MinTouch)
    if (selected) {
        FilledTonalButton(onClick = onClick, modifier = modifier) { content() }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { content() }
    }
}

// ------------------------------------------------------------------ side menu

@Composable
private fun SideMenu(
    filter: StationFilter,
    region: RegionFilter,
    type: TypeFilter,
    country: String,
    refreshing: Boolean,
    eqEnabled: Boolean,
    onAction: (DrawerAction) -> Unit,
) {
    ModalDrawerSheet(Modifier.width(380.dp)) {
        Column(
            Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, bottom = 12.dp)) {
                Icon(Icons.Rounded.Radio, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("RDM Radio", style = MaterialTheme.typography.headlineSmall)
            }

            Section("Stations")
            Item("Favorites", Icons.Rounded.Favorite, filter == StationFilter.FAVORITES) {
                onAction(DrawerAction.Favorites)
            }
            Item("All stations", Icons.Rounded.LibraryMusic, filter == StationFilter.ALL && region == RegionFilter.ALL) {
                onAction(DrawerAction.AllStations)
            }

            Section("Region")
            Item(countryDisplayName(country), Icons.Rounded.Home, filter == StationFilter.ALL && region == RegionFilter.LOCAL) {
                onAction(DrawerAction.Region(RegionFilter.LOCAL))
            }
            Item("Regional (Arab world)", Icons.Rounded.Flag, filter == StationFilter.ALL && region == RegionFilter.REGIONAL) {
                onAction(DrawerAction.Region(RegionFilter.REGIONAL))
            }
            Item("International", Icons.Rounded.Public, filter == StationFilter.ALL && region == RegionFilter.INTERNATIONAL) {
                onAction(DrawerAction.Region(RegionFilter.INTERNATIONAL))
            }

            Section("Station type")
            Item("All types", Icons.Rounded.Apps, type == TypeFilter.ALL) {
                onAction(DrawerAction.Type(TypeFilter.ALL))
            }
            Item("FM broadcasters (online stream)", Icons.Rounded.Podcasts, type == TypeFilter.OFFICIAL_FM) {
                onAction(DrawerAction.Type(TypeFilter.OFFICIAL_FM))
            }
            Item("Web-only radio", Icons.Rounded.Wifi, type == TypeFilter.ONLINE) {
                onAction(DrawerAction.Type(TypeFilter.ONLINE))
            }

            Section("Settings")
            Item("Home country: ${countryDisplayName(country)}", Icons.Rounded.Language, false) {
                onAction(DrawerAction.HomeCountry)
            }
            Item(if (refreshing) "Refreshing…" else "Refresh station list", Icons.Rounded.Refresh, false) {
                if (!refreshing) onAction(DrawerAction.Refresh)
            }
            Item(if (eqEnabled) "Equalizer (on)" else "Equalizer", Icons.Rounded.GraphicEq, false) {
                onAction(DrawerAction.Equalizer)
            }

            Section("Tools")
            Item("Add a station", Icons.Rounded.Add, false) { onAction(DrawerAction.AddStation) }
            Item("Import station list", Icons.Rounded.Download, false) { onAction(DrawerAction.Import) }
            Item("Export station list", Icons.Rounded.Upload, false) { onAction(DrawerAction.Export) }
            Item("Stream diagnostics", Icons.Rounded.BugReport, false) { onAction(DrawerAction.Diagnostics) }

            Section("App")
            Item("About RDM Radio", Icons.Rounded.Info, false) { onAction(DrawerAction.About) }
        }
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun Item(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = { Icon(icon, contentDescription = null, Modifier.size(28.dp)) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.heightIn(min = 64.dp),
    )
}

// ------------------------------------------------------------------ empty state

@Composable
private fun EmptyState(
    filter: StationFilter,
    hiddenByFilters: Boolean,
    onBrowse: () -> Unit,
    onRefresh: () -> Unit,
    onClearFilters: () -> Unit,
) {
    CenteredBox {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Rounded.Radio, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
            val (text, button, action) = when {
                hiddenByFilters -> Triple("No stations match this selection.", "Clear filters", onClearFilters)
                filter == StationFilter.FAVORITES ->
                    Triple("No favorites yet. Tap ♥ on any station.", "Browse stations", onBrowse)
                else -> Triple("No stations loaded yet.", "Load stations", onRefresh)
            }
            Text(text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Button(onClick = action, modifier = Modifier.heightIn(min = CarDimens.MinTouch)) {
                Text(button, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}
