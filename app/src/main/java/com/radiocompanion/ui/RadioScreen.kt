package com.radiocompanion.ui

import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Wifi
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radiocompanion.BuildConfig
import com.radiocompanion.R
import com.radiocompanion.data.settings.ThemeMode
import com.radiocompanion.model.Station
import com.radiocompanion.service.PlayerConnection
import com.radiocompanion.service.StreamDiagnostics
import com.radiocompanion.ui.components.AboutDialog
import com.radiocompanion.ui.components.AddStationDialog
import com.radiocompanion.ui.components.ChoiceDialog
import com.radiocompanion.ui.components.CountryDialog
import com.radiocompanion.ui.components.DiagnosticsDialog
import com.radiocompanion.ui.components.EqualizerDialog
import com.radiocompanion.ui.components.MiniPlayerBar
import com.radiocompanion.ui.components.NowPlayingPanel
import com.radiocompanion.ui.components.StationActionsDialog
import com.radiocompanion.ui.components.StationGrid
import com.radiocompanion.ui.components.UpdateDialog
import com.radiocompanion.ui.theme.CarDimens
import com.radiocompanion.update.UpdateState
import kotlinx.coroutines.launch

/**
 * Picked from the window size, not the device orientation, so the same logic
 * covers the Song L's rotating screen AND DiLink split-screen.
 *
 * Rough sizes on a 15.6" screen: landscape ~1280x720dp, portrait ~720x1280dp,
 * docked in split-screen ~600x720dp.
 */
private enum class LayoutMode { Landscape, Portrait, Docked }

/** Everything the side menu can trigger. */
private sealed interface DrawerAction {
    data object Favorites : DrawerAction
    data object AllStations : DrawerAction
    data class Type(val type: TypeFilter) : DrawerAction
    data object HomeCountry : DrawerAction
    data object Refresh : DrawerAction
    data object Equalizer : DrawerAction
    data object Theme : DrawerAction
    data object Language : DrawerAction
    data object CheckUpdates : DrawerAction
    data object AddStation : DrawerAction
    data object Diagnostics : DrawerAction
    data object Import : DrawerAction
    data object Export : DrawerAction
    data object About : DrawerAction
}

/** Language tags offered in-app. "" = follow the system. */
private val LANGUAGE_TAGS = listOf("", "en", "ar")

/** Country name in the screen's current language. */
@Composable
fun rememberCountryName(code: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(code, locale) { countryDisplayName(code, locale) }
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
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val autoPlay by vm.autoPlayLastStation.collectAsStateWithLifecycle()
    val now by player.state.collectAsStateWithLifecycle()
    val nowFavorite by vm.nowPlayingFavorite.collectAsStateWithLifecycle()
    LaunchedEffect(now.stationId) { vm.setNowPlayingStation(now.stationId) }
    val diagnostics by StreamDiagnostics.entries.collectAsStateWithLifecycle()
    val eqState by vm.audioEffects.state.collectAsStateWithLifecycle()
    val updateState by vm.updates.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it.resolve(context)) } }
    LaunchedEffect(Unit) { vm.updates.messages.collect { snackbar.showSnackbar(it.resolve(context)) } }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showCountry by rememberSaveable { mutableStateOf(false) }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    var showEqualizer by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showTheme by rememberSaveable { mutableStateOf(false) }
    var showLanguage by rememberSaveable { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Station?>(null) }

    val onDrawerAction: (DrawerAction) -> Unit = { action ->
        scope.launch { drawerState.close() }
        when (action) {
            DrawerAction.Favorites -> vm.showFavorites()
            DrawerAction.AllStations -> vm.showAll()
            is DrawerAction.Type -> vm.selectType(action.type)
            DrawerAction.HomeCountry -> showCountry = true
            DrawerAction.Refresh -> vm.refresh()
            DrawerAction.Equalizer -> showEqualizer = true
            DrawerAction.Theme -> showTheme = true
            DrawerAction.Language -> showLanguage = true
            DrawerAction.CheckUpdates -> vm.updates.checkNow()
            DrawerAction.AddStation -> showAdd = true
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
                themeMode = themeMode,
                autoPlay = autoPlay,
                onAutoPlay = vm::setAutoPlayLastStation,
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
                            now, nowFavorite, vm::toggleNowPlayingFavorite,
                            player::previous, player::togglePlayPause, player::next,
                            Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    LayoutMode.Portrait -> Column(verticalArrangement = Arrangement.spacedBy(CarDimens.Gutter)) {
                        NowPlayingPanel(
                            now, nowFavorite, vm::toggleNowPlayingFavorite,
                            player::previous, player::togglePlayPause, player::next,
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

    // ------------------------------------------------------------------ dialogs

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
    if (showTheme) {
        ChoiceDialog(
            title = stringResource(R.string.theme_title),
            options = ThemeMode.entries.map { it to themeLabel(it) },
            selected = themeMode,
            onPick = vm::setThemeMode,
            onDismiss = { showTheme = false },
        )
    }
    if (showLanguage) {
        ChoiceDialog(
            title = stringResource(R.string.language_title),
            options = LANGUAGE_TAGS.map { it to languageLabel(it) },
            selected = currentLanguageTag(),
            onPick = { tag ->
                // Recreates the activity in the new language; remembered across restarts.
                AppCompatDelegate.setApplicationLocales(
                    if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
                )
            },
            onDismiss = { showLanguage = false },
        )
    }
    if (showAbout) {
        AboutDialog(versionName = BuildConfig.VERSION_NAME, onDismiss = { showAbout = false })
    }
    if (updateState !is UpdateState.Idle) {
        val activity = LocalContext.current as? android.app.Activity
        UpdateDialog(
            state = updateState,
            currentVersion = BuildConfig.VERSION_NAME,
            onUpdate = vm.updates::startUpdate,
            onOpenSettings = { activity?.let(vm.updates::openInstallPermissionSettings) },
            onLater = vm.updates::dismiss,
        )
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

private fun currentLanguageTag(): String {
    val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    return LANGUAGE_TAGS.firstOrNull { it.isNotEmpty() && tags.startsWith(it) } ?: ""
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.SYSTEM -> R.string.theme_system
    }
)

@Composable
private fun languageLabel(tag: String): String = stringResource(
    when (tag) {
        "en" -> R.string.language_english
        "ar" -> R.string.language_arabic
        else -> R.string.language_system
    }
)

/** Short description of what the list currently shows, e.g. "Egypt · Official FM". */
@Composable
private fun viewSubtitle(filter: StationFilter, region: RegionFilter, type: TypeFilter, country: String): String {
    val base = when {
        filter == StationFilter.FAVORITES -> stringResource(R.string.view_favorites)
        region == RegionFilter.LOCAL -> rememberCountryName(country)
        else -> stringResource(R.string.view_all)
    }
    val typeLabel = when (type) {
        TypeFilter.ALL -> null
        TypeFilter.OFFICIAL_FM -> stringResource(R.string.view_type_fm)
        TypeFilter.ONLINE -> stringResource(R.string.view_type_online)
    }
    return listOfNotNull(base, typeLabel).joinToString(stringResource(R.string.list_separator))
}

// ------------------------------------------------------------------ header

/**
 * Clean header: menu button, the two quick-access buttons, a small label of what
 * is shown, and the equalizer button. Categories and settings live in the side menu.
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
            Icon(Icons.Rounded.Menu, stringResource(R.string.cd_open_menu), Modifier.size(40.dp))
        }
        Spacer(Modifier.width(12.dp))
        QuickButton(stringResource(R.string.nav_favorites), Icons.Rounded.Favorite, favoritesSelected, onFavorites)
        Spacer(Modifier.width(12.dp))
        QuickButton(rememberCountryName(country), Icons.Rounded.Home, homeSelected, onHomeCountry)
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
                Icons.Rounded.GraphicEq, stringResource(R.string.cd_equalizer), Modifier.size(36.dp),
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
    themeMode: ThemeMode,
    autoPlay: Boolean,
    onAutoPlay: (Boolean) -> Unit,
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
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            }

            Section(stringResource(R.string.nav_section_stations))
            Item(stringResource(R.string.nav_favorites), Icons.Rounded.Favorite, filter == StationFilter.FAVORITES) {
                onAction(DrawerAction.Favorites)
            }
            Item(stringResource(R.string.nav_all_stations), Icons.Rounded.LibraryMusic, filter == StationFilter.ALL && region == RegionFilter.ALL) {
                onAction(DrawerAction.AllStations)
            }

            Section(stringResource(R.string.nav_section_type))
            Item(stringResource(R.string.type_all), Icons.Rounded.Apps, type == TypeFilter.ALL) {
                onAction(DrawerAction.Type(TypeFilter.ALL))
            }
            Item(stringResource(R.string.type_official_fm), Icons.Rounded.Podcasts, type == TypeFilter.OFFICIAL_FM) {
                onAction(DrawerAction.Type(TypeFilter.OFFICIAL_FM))
            }
            Item(stringResource(R.string.type_online), Icons.Rounded.Wifi, type == TypeFilter.ONLINE) {
                onAction(DrawerAction.Type(TypeFilter.ONLINE))
            }

            Section(stringResource(R.string.nav_section_settings))
            Item(stringResource(R.string.nav_home_country, rememberCountryName(country)), Icons.Rounded.Public, false) {
                onAction(DrawerAction.HomeCountry)
            }
            Item(stringResource(if (refreshing) R.string.nav_refreshing else R.string.nav_refresh), Icons.Rounded.Refresh, false) {
                if (!refreshing) onAction(DrawerAction.Refresh)
            }
            Item(stringResource(if (eqEnabled) R.string.nav_equalizer_on else R.string.nav_equalizer), Icons.Rounded.GraphicEq, false) {
                onAction(DrawerAction.Equalizer)
            }
            Item(stringResource(R.string.nav_theme, themeLabel(themeMode)), Icons.Rounded.DarkMode, false) {
                onAction(DrawerAction.Theme)
            }
            Item(stringResource(R.string.nav_language, languageLabel(currentLanguageTag())), Icons.Rounded.Translate, false) {
                onAction(DrawerAction.Language)
            }
            Item(
                stringResource(R.string.nav_auto_play), Icons.Rounded.PlayCircle, false,
                badge = { Switch(checked = autoPlay, onCheckedChange = null) },
            ) { onAutoPlay(!autoPlay) }

            Section(stringResource(R.string.nav_section_tools))
            Item(stringResource(R.string.nav_add_station), Icons.Rounded.Add, false) { onAction(DrawerAction.AddStation) }
            Item(stringResource(R.string.nav_import), Icons.Rounded.Download, false) { onAction(DrawerAction.Import) }
            Item(stringResource(R.string.nav_export), Icons.Rounded.Upload, false) { onAction(DrawerAction.Export) }
            Item(stringResource(R.string.nav_diagnostics), Icons.Rounded.BugReport, false) { onAction(DrawerAction.Diagnostics) }

            Section(stringResource(R.string.nav_section_app))
            Item(stringResource(R.string.nav_check_updates), Icons.Rounded.SystemUpdate, false) { onAction(DrawerAction.CheckUpdates) }
            Item(stringResource(R.string.nav_about), Icons.Rounded.Info, false) { onAction(DrawerAction.About) }
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
private fun Item(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badge: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = { Icon(icon, contentDescription = null, Modifier.size(28.dp)) },
        selected = selected,
        onClick = onClick,
        badge = badge,
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
                hiddenByFilters -> Triple(R.string.empty_no_match, R.string.empty_clear_filters, onClearFilters)
                filter == StationFilter.FAVORITES -> Triple(R.string.empty_no_favorites, R.string.empty_browse, onBrowse)
                else -> Triple(R.string.empty_no_stations, R.string.empty_load, onRefresh)
            }
            Text(stringResource(text), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Button(onClick = action, modifier = Modifier.heightIn(min = CarDimens.MinTouch)) {
                Text(stringResource(button), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}
