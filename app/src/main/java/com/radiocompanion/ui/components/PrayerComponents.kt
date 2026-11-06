@file:OptIn(ExperimentalLayoutApi::class)

package com.radiocompanion.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Brightness3
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.radiocompanion.R
import com.radiocompanion.azan.AzanState
import com.radiocompanion.azan.labelRes
import com.radiocompanion.data.settings.RamadanThemeMode
import com.radiocompanion.prayer.AsrSchool
import com.radiocompanion.prayer.CalcMethod
import com.radiocompanion.prayer.Cities
import com.radiocompanion.prayer.Prayer
import com.radiocompanion.ui.PrayerUi
import com.radiocompanion.ui.rememberCountryName
import com.radiocompanion.ui.theme.CarDimens
import com.radiocompanion.ui.theme.Crescent
import com.radiocompanion.ui.theme.LocalRamadanTheme
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

// ------------------------------------------------------------------ formatting

/** "17:38" or "5:38 PM", following the head unit's 12/24-hour setting and language. */
@Composable
fun rememberTimeFormat(): DateTimeFormatter {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24 = DateFormat.is24HourFormat(context)
    return remember(locale, is24) { DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", locale) }
}

@Composable
private fun rememberDateFormat(pattern: String): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, pattern) { DateTimeFormatter.ofPattern(pattern, locale) }
}

private fun DateTimeFormatter.f(t: TemporalAccessor): String = format(t)

/** "in 2 h 10 min" / "in 5 min". */
@Composable
fun countdown(from: Instant, to: ZonedDateTime): String {
    val minutes = Duration.between(from, to.toInstant()).toMinutes().coerceAtLeast(0) + 1
    val h = (minutes / 60).toInt()
    val m = (minutes % 60).toInt()
    return if (h > 0) stringResource(R.string.countdown_hm, h, m) else stringResource(R.string.countdown_m, m)
}

@Composable
fun prayerName(p: Prayer): String = stringResource(p.labelRes())

@Composable
fun methodName(m: CalcMethod): String = stringResource(
    when (m) {
        CalcMethod.EGYPTIAN -> R.string.method_egyptian
        CalcMethod.UMM_AL_QURA -> R.string.method_umm_al_qura
        CalcMethod.MWL -> R.string.method_mwl
        CalcMethod.ISNA -> R.string.method_isna
        CalcMethod.KARACHI -> R.string.method_karachi
        CalcMethod.DUBAI -> R.string.method_dubai
        CalcMethod.KUWAIT -> R.string.method_kuwait
        CalcMethod.QATAR -> R.string.method_qatar
        CalcMethod.MOROCCO -> R.string.method_morocco
        CalcMethod.TURKEY -> R.string.method_turkey
    }
)

@Composable
private fun isArabic() = LocalConfiguration.current.locales[0].language == "ar"

@Composable
fun placeName(ui: PrayerUi): String {
    val city = ui.place.city.name(isArabic())
    return if (ui.place.fromGps) stringResource(R.string.prayer_place_gps, city) else city
}

// ------------------------------------------------------------------ main-screen pieces

/** Header button: next prayer and its time. Opens the prayer screen. */
@Composable
fun PrayerChip(ui: PrayerUi, onClick: () -> Unit) {
    val time = rememberTimeFormat()
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = CarDimens.MinTouch)) {
        Icon(Icons.Rounded.Brightness3, contentDescription = null, Modifier.size(26.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "${prayerName(ui.next.first)} ${time.f(ui.next.second)}",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
        )
    }
}

/** Slim card shown during Ramadan: day of the month, Imsak and Iftar. */
@Composable
fun RamadanBanner(ui: PrayerUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val row = ui.todayRow ?: return
    val time = rememberTimeFormat()
    val colors = MaterialTheme.colorScheme
    val iftar = ui.today[Prayer.MAGHRIB]
    val beforeIftar = ui.now.isBefore(iftar.toInstant())
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Crescent(colors.primary, diameter = 40.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.ramadan_banner_day, row.day),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.ramadan_banner_times, time.f(row.imsak), time.f(row.maghrib)),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (beforeIftar) {
                Text(
                    stringResource(R.string.ramadan_iftar_in, countdown(ui.now, iftar)),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** While the Azan plays: what's happening and a big Stop button. */
@Composable
fun AzanBanner(state: AzanState.Playing, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, Modifier.size(36.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    state.prayer?.let { stringResource(R.string.azan_banner_title, prayerName(it)) }
                        ?: stringResource(R.string.azan_test_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (state.radioWillResume) {
                    Text(stringResource(R.string.azan_notification_text), style = MaterialTheme.typography.labelLarge)
                }
            }
            FilledTonalButton(onClick = onStop, modifier = Modifier.heightIn(min = CarDimens.MinTouch)) {
                Text(stringResource(R.string.azan_stop), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

// ------------------------------------------------------------------ prayer screen

enum class PrayerTab { TODAY, RAMADAN, SETTINGS }

/** Actions from the prayer screen; the activity supplies permission/file-picker ones. */
class PrayerActions(
    val onAzanEnabled: (Boolean) -> Unit,
    val onTogglePrayer: (Prayer) -> Unit,
    val onPickCity: (String) -> Unit,
    val onUseCarLocation: () -> Unit,
    val onStopCarLocation: () -> Unit,
    val onMethod: (CalcMethod?) -> Unit,
    val onAsr: (AsrSchool) -> Unit,
    val onPickSoundFile: () -> Unit,
    val onSoundLink: (String?) -> Unit,
    val onTestAzan: () -> Unit,
    val onRamadanTheme: (RamadanThemeMode) -> Unit,
)

@Composable
fun PrayerTimesDialog(
    ui: PrayerUi,
    initialTab: PrayerTab,
    homeCountry: String,
    actions: PrayerActions,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 1100.dp).fillMaxHeight(0.92f),
        ) {
            // Header: where, which method, Ramadan crescent when it applies.
            Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (LocalRamadanTheme.current || ui.ramadanDay != null) {
                    Crescent(colors.primary, diameter = 44.dp)
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.prayer_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${placeName(ui)} · ${methodName(ui.place.method)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                DialogButton(stringResource(R.string.action_close), onClick = onDismiss)
            }
            TabRow(selectedTabIndex = tab.ordinal, containerColor = colors.surface) {
                PrayerTab.entries.forEach { t ->
                    Tab(
                        selected = t == tab,
                        onClick = { tab = t },
                        modifier = Modifier.heightIn(min = 64.dp),
                        text = {
                            Text(
                                when (t) {
                                    PrayerTab.TODAY -> stringResource(R.string.prayer_tab_today)
                                    PrayerTab.RAMADAN -> stringResource(R.string.prayer_tab_ramadan, ui.ramadan.hijriYear)
                                    PrayerTab.SETTINGS -> stringResource(R.string.prayer_tab_settings)
                                },
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                            )
                        },
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    PrayerTab.TODAY -> TodayTab(ui)
                    PrayerTab.RAMADAN -> RamadanTab(ui)
                    PrayerTab.SETTINGS -> SettingsTab(ui, homeCountry, actions)
                }
            }
        }
    }
}

@Composable
private fun TodayTab(ui: PrayerUi) {
    val time = rememberTimeFormat()
    val date = rememberDateFormat("EEEE d MMMM")
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            listOfNotNull(
                date.f(ui.today.date),
                ui.ramadanDay?.let { stringResource(R.string.ramadan_day_n, it) },
            ).joinToString(" · "),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ui.todayRow?.let { row ->
            Text(
                stringResource(R.string.ramadan_imsak_at, time.f(row.imsak)),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Prayer.entries.forEach { p ->
                val at = ui.today[p]
                val isNext = ui.next.first == p && ui.next.second.toLocalDate() == ui.today.date
                PrayerTile(
                    name = prayerName(p),
                    time = time.f(at),
                    sub = if (isNext) countdown(ui.now, at) else null,
                    highlighted = isNext,
                    passed = !isNext && at.toInstant().isBefore(ui.now),
                )
            }
        }
    }
}

@Composable
private fun PrayerTile(name: String, time: String, sub: String?, highlighted: Boolean, passed: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (highlighted) colors.primaryContainer else colors.surfaceVariant,
        contentColor = if (highlighted) colors.onPrimaryContainer else colors.onSurface,
        border = if (highlighted) BorderStroke(2.dp, colors.primary) else null,
        modifier = Modifier.width(200.dp).heightIn(min = 128.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.titleLarge, color = if (passed) colors.onSurfaceVariant else Color.Unspecified)
            Text(time, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            sub?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = colors.primary) }
        }
    }
}

@Composable
private fun RamadanTab(ui: PrayerUi) {
    val time = rememberTimeFormat()
    val dayMonth = rememberDateFormat("EEE d MMM")
    val full = rememberDateFormat("d MMMM yyyy")
    val colors = MaterialTheme.colorScheme
    val today = ui.today.date
    val period = ui.ramadan
    val listState = rememberLazyListState()
    LaunchedEffect(ui.ramadanDay) { ui.ramadanDay?.let { listState.scrollToItem((it - 1).coerceAtLeast(0)) } }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(
            stringResource(R.string.ramadan_period, full.f(period.first), full.f(period.last), period.days),
            style = MaterialTheme.typography.titleLarge,
        )
        val status = when {
            ui.ramadanDay != null -> stringResource(R.string.ramadan_day_of, ui.ramadanDay, period.days)
            today.isBefore(period.first) -> stringResource(
                R.string.ramadan_starts_in, (period.first.toEpochDay() - today.toEpochDay()).toInt(),
            )
            else -> stringResource(R.string.ramadan_eid, full.f(period.eid))
        }
        Text(status, style = MaterialTheme.typography.labelLarge, color = colors.primary)
        Text(stringResource(R.string.ramadan_note), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        // The table scrolls sideways when the window is narrow (portrait / split-screen).
        val hScroll = rememberScrollState()
        Column(Modifier.horizontalScroll(hScroll)) {
            ImsakiyyaLine(
                cells = listOf(
                    stringResource(R.string.ramadan_col_day), stringResource(R.string.ramadan_col_date),
                    stringResource(R.string.ramadan_col_imsak), prayerName(Prayer.FAJR), prayerName(Prayer.SUNRISE),
                    prayerName(Prayer.DHUHR), prayerName(Prayer.ASR), prayerName(Prayer.MAGHRIB), prayerName(Prayer.ISHA),
                ),
                header = true, highlighted = false,
            )
            HorizontalDivider()
            LazyColumn(state = listState) {
                items(ui.imsakiyya, key = { it.day }) { r ->
                    ImsakiyyaLine(
                        cells = listOf(r.day.toString(), dayMonth.f(r.date)) +
                            listOf(r.imsak, r.fajr, r.sunrise, r.dhuhr, r.asr, r.maghrib, r.isha).map { time.f(it) },
                        header = false,
                        highlighted = r.date == today,
                    )
                }
            }
        }
    }
}

private val IMSAKIYYA_WIDTHS = listOf(64, 150, 104, 104, 104, 104, 104, 112, 104)

@Composable
private fun ImsakiyyaLine(cells: List<String>, header: Boolean, highlighted: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .then(if (highlighted) Modifier.background(colors.primaryContainer, RoundedCornerShape(12.dp)) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { i, text ->
            // Imsak and Maghrib (Iftar) are the two times people look for.
            val key = i == 2 || i == 7
            Text(
                text,
                style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge,
                fontWeight = if (header || highlighted || key) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    highlighted -> colors.onPrimaryContainer
                    key -> colors.primary
                    header -> colors.onSurfaceVariant
                    else -> colors.onSurface
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.width(IMSAKIYYA_WIDTHS[i].dp).padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingsTab(ui: PrayerUi, homeCountry: String, a: PrayerActions) {
    val s = ui.place.settings
    var pickCity by rememberSaveable { mutableStateOf(false) }
    var pickMethod by rememberSaveable { mutableStateOf(false) }
    var editLink by rememberSaveable { mutableStateOf(false) }
    var pickRamadanTheme by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SwitchRow(stringResource(R.string.azan_enabled), stringResource(R.string.azan_enabled_hint), s.azanEnabled, a.onAzanEnabled)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val check: @Composable () -> Unit = { Icon(Icons.Rounded.Check, contentDescription = null) }
            Prayer.entries.filter { it.hasAzan }.forEach { p ->
                val on = p in s.azanPrayers
                FilterChip(
                    selected = on,
                    enabled = s.azanEnabled,
                    onClick = { a.onTogglePrayer(p) },
                    label = { Text(prayerName(p), style = MaterialTheme.typography.titleLarge) },
                    leadingIcon = if (on) check else null,
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }

        SettingsSection(stringResource(R.string.prayer_section_sound))
        Text(
            when {
                s.azanSoundUri == null -> stringResource(R.string.azan_sound_default)
                s.azanSoundUri.startsWith("http", ignoreCase = true) -> stringResource(R.string.azan_sound_link, s.azanSoundUri)
                else -> stringResource(R.string.azan_sound_file)
            },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsButton(stringResource(R.string.azan_test), Icons.AutoMirrored.Rounded.VolumeUp, a.onTestAzan)
            SettingsButton(stringResource(R.string.azan_sound_choose_file), null, a.onPickSoundFile)
            SettingsButton(stringResource(R.string.azan_sound_choose_link), null) { editLink = true }
            if (s.azanSoundUri != null) SettingsButton(stringResource(R.string.azan_sound_reset), null) { a.onSoundLink(null) }
        }

        SettingsSection(stringResource(R.string.prayer_section_location))
        Text(placeName(ui), style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsButton(stringResource(R.string.prayer_choose_city), Icons.Rounded.Place) { pickCity = true }
            if (ui.place.fromGps) {
                SettingsButton(stringResource(R.string.prayer_stop_gps), null, a.onStopCarLocation)
            }
            SettingsButton(
                stringResource(if (ui.place.fromGps) R.string.prayer_update_gps else R.string.prayer_use_gps),
                Icons.Rounded.MyLocation, a.onUseCarLocation,
            )
        }

        SettingsSection(stringResource(R.string.prayer_section_method))
        SettingsButton(
            if (s.method == null) stringResource(R.string.method_auto, methodName(ui.place.method)) else methodName(ui.place.method),
            null,
        ) { pickMethod = true }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsrSchool.entries.forEach { school ->
                FilterChip(
                    selected = s.asrSchool == school,
                    onClick = { a.onAsr(school) },
                    label = {
                        Text(
                            stringResource(if (school == AsrSchool.STANDARD) R.string.asr_standard else R.string.asr_hanafi),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }

        SettingsSection(stringResource(R.string.prayer_section_ramadan))
        SettingsButton(stringResource(R.string.ramadan_theme_label, ramadanThemeLabel(s.ramadanTheme)), Icons.Rounded.Brightness3) {
            pickRamadanTheme = true
        }
    }

    if (pickCity) CityDialog(current = ui.place.city.id.takeUnless { ui.place.fromGps }, homeCountry = homeCountry, onPick = a.onPickCity) { pickCity = false }
    if (pickMethod) {
        ChoiceDialog(
            title = stringResource(R.string.prayer_section_method),
            options = listOf<CalcMethod?>(null).plus(CalcMethod.entries).map { m ->
                m to (m?.let { methodName(it) } ?: stringResource(R.string.method_auto, methodName(CalcMethod.forCountry(ui.place.country))))
            },
            selected = s.method,
            onPick = a.onMethod,
            onDismiss = { pickMethod = false },
        )
    }
    if (pickRamadanTheme) {
        ChoiceDialog(
            title = stringResource(R.string.prayer_section_ramadan),
            options = RamadanThemeMode.entries.map { it to ramadanThemeLabel(it) },
            selected = s.ramadanTheme,
            onPick = a.onRamadanTheme,
            onDismiss = { pickRamadanTheme = false },
        )
    }
    if (editLink) AzanLinkDialog(initial = s.azanSoundUri?.takeIf { it.startsWith("http", true) }.orEmpty(), onSave = a.onSoundLink) { editLink = false }
}

@Composable
private fun ramadanThemeLabel(mode: RamadanThemeMode) = stringResource(
    when (mode) {
        RamadanThemeMode.AUTO -> R.string.ramadan_theme_auto
        RamadanThemeMode.ALWAYS -> R.string.ramadan_theme_always
        RamadanThemeMode.OFF -> R.string.ramadan_theme_off
    }
)

@Composable
private fun SettingsSection(title: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingsButton(label: String, icon: ImageVector?, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = CarDimens.MinTouch)) {
        icon?.let {
            Icon(it, contentDescription = null, Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(hint, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** All built-in cities, the home country's first. */
@Composable
private fun CityDialog(current: String?, homeCountry: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val arabic = isArabic()
    val cities = remember(homeCountry) {
        Cities.all.sortedBy { if (it.country.equals(homeCountry, true)) 0 else 1 }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prayer_choose_city), style = MaterialTheme.typography.headlineSmall) },
        text = {
            LazyColumn(Modifier.heightIn(max = 560.dp)) {
                items(cities, key = { it.id }) { city ->
                    val selected = city.id == current
                    Text(
                        "${city.name(arabic)} · ${rememberCountryName(city.country)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clickable { onPick(city.id); onDismiss() }
                            .padding(vertical = 16.dp, horizontal = 8.dp),
                    )
                }
            }
        },
        confirmButton = { DialogButton(stringResource(R.string.action_close), onClick = onDismiss) },
    )
}

/** For head units without a file picker: an Azan recording from the web. */
@Composable
private fun AzanLinkDialog(initial: String, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var url by rememberSaveable { mutableStateOf(initial) }
    val valid = url.trim().let { it.startsWith("http://", true) || it.startsWith("https://", true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.azan_sound_choose_link), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.azan_link_hint), style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    label = { Text(stringResource(R.string.azan_link_label)) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            DialogButton(stringResource(R.string.action_save), enabled = valid) { onSave(url.trim()); onDismiss() }
        },
        dismissButton = { DialogButton(stringResource(R.string.action_cancel), onClick = onDismiss) },
    )
}
