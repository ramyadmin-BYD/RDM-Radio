package com.radiocompanion.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.radiocompanion.R
import com.radiocompanion.data.repository.NewStation
import com.radiocompanion.model.Station
import com.radiocompanion.service.StreamLogEntry
import com.radiocompanion.ui.rememberCountryName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Countries offered in the pickers. Radio-Browser accepts any ISO 3166-1 alpha-2 code. */
private val COUNTRIES = listOf(
    "EG", "SA", "AE", "KW", "QA", "BH", "OM", "JO", "LB", "IQ", "PS", "SY", "YE",
    "MA", "DZ", "TN", "LY", "SD", "TR", "GB", "US", "DE", "FR", "IT", "ES", "CN",
)

private fun isHttpUrl(text: String) =
    text.trim().let { it.startsWith("http://", true) || it.startsWith("https://", true) }

@Composable
private fun DialogButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 56.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun AddStationDialog(
    homeCountry: String,
    onDismiss: () -> Unit,
    onAdd: (NewStation) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var backupUrl by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf(homeCountry) }
    var official by rememberSaveable { mutableStateOf(false) }
    var pickCountry by rememberSaveable { mutableStateOf(false) }

    val valid = isHttpUrl(url) && (backupUrl.isBlank() || isHttpUrl(backupUrl))
    // URLs are always left-to-right, also when the screen is in Arabic.
    val ltr = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_title), style = MaterialTheme.typography.headlineSmall) },
        text = {
            // Scrolls when the screen is in landscape and the keyboard is up.
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.add_hint), style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.add_name)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text(stringResource(R.string.add_url)) }, singleLine = true,
                    textStyle = ltr,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = backupUrl, onValueChange = { backupUrl = it },
                    label = { Text(stringResource(R.string.add_backup_url)) }, singleLine = true,
                    textStyle = ltr,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { pickCountry = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text(stringResource(R.string.add_country, rememberCountryName(country)), style = MaterialTheme.typography.labelLarge)
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { official = !official },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.add_official), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.add_official_hint),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = official, onCheckedChange = { official = it })
                }
            }
        },
        confirmButton = {
            DialogButton(stringResource(R.string.action_add), enabled = valid) {
                onAdd(
                    NewStation(
                        name = name,
                        streamUrl = url,
                        backupUrl = backupUrl,
                        countryCode = country,
                        isOfficialFm = official,
                    )
                )
                onDismiss()
            }
        },
        dismissButton = { DialogButton(stringResource(R.string.action_cancel), onClick = onDismiss) },
    )

    if (pickCountry) {
        CountryDialog(
            title = stringResource(R.string.country_title_station),
            current = country,
            onDismiss = { pickCountry = false },
            onPick = { country = it },
        )
    }
}

@Composable
fun CountryDialog(
    current: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    title: String = stringResource(R.string.country_title_home),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            LazyColumn {
                items(COUNTRIES) { code ->
                    val selected = code == current
                    Text(
                        stringResource(R.string.country_item, rememberCountryName(code), code),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clickable { onPick(code); onDismiss() }
                            .padding(vertical = 16.dp, horizontal = 8.dp),
                    )
                }
            }
        },
        confirmButton = { DialogButton(stringResource(R.string.action_close), onClick = onDismiss) },
    )
}

/** Single-choice list (theme, language). Picking an option applies it and closes the dialog. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clickable { onDismiss(); onPick(value) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(label, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        },
        confirmButton = { DialogButton(stringResource(R.string.action_close), onClick = onDismiss) },
    )
}

/** Long-press on a station: change its type or remove it. */
@Composable
fun StationActionsDialog(
    station: Station,
    onDismiss: () -> Unit,
    onSetOfficialFm: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(station.name, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                ActionRow(
                    stringResource(if (station.isOfficialFm) R.string.actions_mark_online else R.string.actions_mark_fm),
                ) { onSetOfficialFm(!station.isOfficialFm); onDismiss() }
                HorizontalDivider()
                ActionRow(
                    stringResource(if (confirmDelete) R.string.actions_remove_confirm else R.string.actions_remove),
                    isDestructive = true,
                ) {
                    if (confirmDelete) { onDelete(); onDismiss() } else confirmDelete = true
                }
            }
        },
        confirmButton = { DialogButton(stringResource(R.string.action_close), onClick = onDismiss) },
    )
}

@Composable
private fun ActionRow(text: String, isDestructive: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 22.dp, horizontal = 8.dp),
    )
}

/** Recent stream problems, readable in the car without a computer. */
@Composable
fun DiagnosticsDialog(
    entries: List<StreamLogEntry>,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diag_title), style = MaterialTheme.typography.headlineSmall) },
        text = {
            if (entries.isEmpty()) {
                Text(stringResource(R.string.diag_empty), style = MaterialTheme.typography.bodyLarge)
            } else {
                SelectionContainer {
                    LazyColumn(Modifier.heightIn(max = 520.dp)) {
                        items(entries) { e ->
                            Column(Modifier.padding(vertical = 10.dp)) {
                                Text(
                                    "${timeFormat.format(Date(e.timeMs))}  ${e.station}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    e.message.resolve(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (e.isError) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.secondary,
                                )
                                e.url?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { DialogButton(stringResource(R.string.action_close), onClick = onDismiss) },
        dismissButton = { DialogButton(stringResource(R.string.action_clear), enabled = entries.isNotEmpty(), onClick = onClear) },
    )
}

/** ☰ → About: what the app does, version, and credits. */
@Composable
fun AboutDialog(versionName: String, onDismiss: () -> Unit) {
    val features = listOf(
        R.string.about_feature_stations,
        R.string.about_feature_favorites,
        R.string.about_feature_autoplay,
        R.string.about_feature_reliability,
        R.string.about_feature_eq,
        R.string.about_feature_ui,
        R.string.about_feature_car,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
                Text(
                    stringResource(R.string.about_version, versionName),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.about_intro), style = MaterialTheme.typography.bodyLarge)
                features.forEach { feature ->
                    Row {
                        Text("•  ", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(feature), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    stringResource(R.string.about_designed_by),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.about_designer),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.about_credits),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { DialogButton(stringResource(R.string.action_close), onClick = onDismiss) },
    )
}
