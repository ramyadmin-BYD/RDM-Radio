package com.radiocompanion.ui

import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.radiocompanion.RadioCompanionApp
import com.radiocompanion.data.remote.PlaylistResolver
import com.radiocompanion.data.repository.NewStation
import com.radiocompanion.R
import com.radiocompanion.data.settings.ThemeMode
import com.radiocompanion.model.Station
import com.radiocompanion.model.UiText
import com.radiocompanion.model.StationRegion
import com.radiocompanion.model.StationType
import com.radiocompanion.model.region
import com.radiocompanion.model.type
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class StationFilter { FAVORITES, ALL }

/** Category 1 filter. ALL = no filter. */
enum class RegionFilter { ALL, LOCAL, REGIONAL, INTERNATIONAL }

/** Category 2 filter. ALL = no filter. */
enum class TypeFilter { ALL, OFFICIAL_FM, ONLINE }

/** Country name in the given language (the UI passes the screen's locale, so it follows Arabic/English). */
fun countryDisplayName(code: String, inLocale: Locale = Locale.getDefault()): String =
    runCatching { Locale.Builder().setRegion(code).build().getDisplayCountry(inLocale) }
        .getOrNull()?.ifBlank { null } ?: code

@OptIn(ExperimentalCoroutinesApi::class)
class RadioViewModel(private val app: RadioCompanionApp) : ViewModel() {

    private val repo = app.repository

    /** Equalizer controls (the UI talks to the controller directly). */
    val audioEffects = app.audioEffects
    private val settings = app.settings

    private val _filter = MutableStateFlow(StationFilter.FAVORITES)
    val filter: StateFlow<StationFilter> = _filter.asStateFlow()

    val country: StateFlow<String> =
        settings.countryCode.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)

    val autoPlayLastStation: StateFlow<Boolean> =
        settings.autoPlayLastStation.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** In-app updates (the UI shows the prompt and forwards button taps). */
    val updates = app.updates

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setAutoPlayLastStation(on: Boolean) {
        viewModelScope.launch { settings.setAutoPlayLastStation(on) }
    }

    private val _region = MutableStateFlow(RegionFilter.ALL)
    val region: StateFlow<RegionFilter> = _region.asStateFlow()

    private val _type = MutableStateFlow(TypeFilter.ALL)
    val type: StateFlow<TypeFilter> = _type.asStateFlow()

    private val baseList: Flow<List<Station>> = _filter
        .flatMapLatest { if (it == StationFilter.FAVORITES) repo.observeFavorites() else repo.observeAll() }

    /** True when the current list has stations but the region/type filters hide all of them. */
    private val _hiddenByFilters = MutableStateFlow(false)
    val hiddenByFilters: StateFlow<Boolean> = _hiddenByFilters.asStateFlow()

    val stations: StateFlow<List<Station>> =
        combine(baseList, _region, _type, settings.countryCode) { list, regionFilter, typeFilter, home ->
            val filtered = list.filter { s ->
                val regionOk = when (regionFilter) {
                    RegionFilter.ALL -> true
                    RegionFilter.LOCAL -> s.region(home) == StationRegion.LOCAL
                    RegionFilter.REGIONAL -> s.region(home) == StationRegion.REGIONAL
                    RegionFilter.INTERNATIONAL -> s.region(home) == StationRegion.INTERNATIONAL
                }
                val typeOk = when (typeFilter) {
                    TypeFilter.ALL -> true
                    TypeFilter.OFFICIAL_FM -> s.type == StationType.OFFICIAL_FM
                    TypeFilter.ONLINE -> s.type == StationType.ONLINE
                }
                regionOk && typeOk
            }
            _hiddenByFilters.value = list.isNotEmpty() && filtered.isEmpty()
            filtered
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            repo.syncBundledStations(settings.bundledStationsHash())?.let { settings.setBundledStationsHash(it) }
            if (repo.favoriteCount() == 0) showHomeCountry()
            if (settings.isRefreshDue()) refreshNow(settings.countryCode.first(), quiet = true)
        }
    }

    // ---- Navigation: header quick buttons + side drawer

    /** Header button: favorites from every region. */
    fun showFavorites() {
        _filter.value = StationFilter.FAVORITES
        _region.value = RegionFilter.ALL
    }

    /** Header button: all stations of the home country (Egypt by default). */
    fun showHomeCountry() {
        _filter.value = StationFilter.ALL
        _region.value = RegionFilter.LOCAL
    }

    /** Drawer: every station. */
    fun showAll() {
        _filter.value = StationFilter.ALL
        _region.value = RegionFilter.ALL
    }

    /** Drawer: a region (home country / regional / international). */
    fun selectRegion(region: RegionFilter) {
        _filter.value = StationFilter.ALL
        _region.value = region
    }

    /** Drawer: station type, combined with whatever list is shown. */
    fun selectType(type: TypeFilter) {
        _type.value = type
    }

    fun clearFilters() {
        _region.value = RegionFilter.ALL
        _type.value = TypeFilter.ALL
    }

    fun setOfficialFm(station: Station, official: Boolean) {
        viewModelScope.launch {
            repo.setOfficialFm(station.id, official)
            message(UiText(if (official) R.string.msg_type_fm else R.string.msg_type_online, station.name))
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshNow(settings.countryCode.first()) }
    }

    fun setCountry(code: String) {
        viewModelScope.launch {
            settings.setCountryCode(code)
            _filter.value = StationFilter.ALL
            _region.value = RegionFilter.LOCAL
            refreshNow(code)
        }
    }

    private suspend fun refreshNow(code: String, quiet: Boolean = false) {
        if (_refreshing.value) return
        _refreshing.value = true
        try {
            val count = repo.refreshFromRadioBrowser(code)
            settings.markRefreshed()
            if (!quiet) message(UiText(R.string.msg_loaded, count))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message(UiText(R.string.msg_load_failed))
        } finally {
            _refreshing.value = false
        }
    }

    fun toggleFavorite(station: Station) {
        viewModelScope.launch { repo.setFavorite(station.id, !station.isFavorite) }
    }

    fun delete(station: Station) {
        viewModelScope.launch {
            repo.delete(station.id)
            message(UiText(R.string.msg_removed, station.name))
        }
    }

    fun addCustom(input: NewStation) {
        viewModelScope.launch {
            try {
                val s = repo.addCustom(input)
                message(UiText(R.string.msg_added, s.name))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message(UiText(R.string.msg_add_failed))
            }
        }
    }

    // ------------------------------------------------------------ import / export

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                val text = repo.exportJson()
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
                        ?: error("Can't open file")
                }
            }.isSuccess
            message(UiText(if (ok) R.string.msg_exported else R.string.msg_export_failed))
        }
    }

    /** Fallback for head units without a file picker: saves to the public Downloads folder. */
    fun exportToDownloads() {
        viewModelScope.launch {
            val name = "radio-stations-${System.currentTimeMillis() / 1000}.json"
            val ok = runCatching {
                val text = repo.exportJson()
                withContext(Dispatchers.IO) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("Can't create file")
                    app.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
                        ?: error("Can't open file")
                }
            }.isSuccess
            message(if (ok) UiText(R.string.msg_saved_downloads, name) else UiText(R.string.msg_export_failed))
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val text = readText(uri) ?: return@launch message(UiText(R.string.msg_read_failed))
            importText(text)
        }
    }

    /** "Open with" from a file manager or browser: a JSON backup, a playlist, or a stream URL. */
    fun handleIncoming(uri: Uri, mimeType: String?) {
        viewModelScope.launch {
            when (uri.scheme?.lowercase()) {
                "http", "https" -> addCustom(quickStation(nameFrom(uri), uri.toString()))
                else -> {
                    val text = readText(uri) ?: return@launch message(UiText(R.string.msg_read_failed))
                    val trimmed = text.trimStart()
                    if (mimeType == "application/json" || trimmed.startsWith("{") || trimmed.startsWith("[")) {
                        importText(text)
                    } else {
                        val stream = PlaylistResolver.parse(text)
                        if (stream != null) addCustom(quickStation(nameFrom(uri), stream))
                        else message(UiText(R.string.msg_no_stream_in_file))
                    }
                }
            }
        }
    }

    fun message(text: UiText) {
        _messages.trySend(text)
    }

    private suspend fun importText(text: String) {
        try {
            val count = repo.importJson(text)
            showFavorites()
            message(UiText(R.string.msg_imported, count))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message(UiText(R.string.msg_invalid_list))
        }
    }

    private suspend fun readText(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
    }

    /** Station opened from a link/file: assume home country and online radio; editable later. */
    private fun quickStation(name: String, url: String) = NewStation(
        name = name,
        streamUrl = url,
        countryCode = country.value,
        isOfficialFm = false,
    )

    private fun nameFrom(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
            ?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: uri.host ?: "Custom station"

    companion object {
        val Factory = viewModelFactory {
            initializer { RadioViewModel(this[APPLICATION_KEY] as RadioCompanionApp) }
        }
    }
}
