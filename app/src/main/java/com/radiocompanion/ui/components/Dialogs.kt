package com.radiocompanion.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.radiocompanion.data.repository.NewStation
import com.radiocompanion.model.Station
import com.radiocompanion.service.StreamLogEntry
import com.radiocompanion.ui.countryDisplayName
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a station", style = MaterialTheme.typography.headlineSmall) },
        text = {
            // Scrolls when the screen is in landscape and the keyboard is up.
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Icecast/Shoutcast, HLS (.m3u8), .pls or .m3u links all work.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Stream URL") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = backupUrl, onValueChange = { backupUrl = it },
                    label = { Text("Backup URL (optional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { pickCountry = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text("Country: ${countryDisplayName(country)}", style = MaterialTheme.typography.labelLarge)
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { official = !official },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("FM broadcaster", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Online stream of an FM station. Off = web-only radio",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = official, onCheckedChange = { official = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
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
                },
                enabled = valid,
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text("Add", style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        },
    )

    if (pickCountry) {
        CountryDialog(
            title = "Station country",
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
    title: String = "Home country",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            LazyColumn {
                items(COUNTRIES) { code ->
                    val selected = code == current
                    Text(
                        "${countryDisplayName(code)} ($code)",
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
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("Close", style = MaterialTheme.typography.labelLarge)
            }
        },
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
                    if (station.isOfficialFm) "Mark as web-only radio" else "Mark as FM broadcaster",
                ) { onSetOfficialFm(!station.isOfficialFm); onDismiss() }
                HorizontalDivider()
                ActionRow(
                    if (confirmDelete) "Tap again to remove" else "Remove station",
                    isDestructive = true,
                ) {
                    if (confirmDelete) { onDelete(); onDismiss() } else confirmDelete = true
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("Close", style = MaterialTheme.typography.labelLarge)
            }
        },
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
        title = { Text("Stream diagnostics", style = MaterialTheme.typography.headlineSmall) },
        text = {
            if (entries.isEmpty()) {
                Text("No stream problems recorded.", style = MaterialTheme.typography.bodyLarge)
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
                                    e.message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (e.isError) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.secondary,
                                )
                                e.url?.let {
                                    Spacer(Modifier.heightIn(min = 2.dp))
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("Close", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onClear, enabled = entries.isNotEmpty(), modifier = Modifier.heightIn(min = 56.dp)) {
                Text("Clear", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

/** ☰ → About: what the app does, version, and credits. */
@Composable
fun AboutDialog(versionName: String, onDismiss: () -> Unit) {
    val features = listOf(
        "Egyptian, regional (Arab world) and international stations, with Nogoum FM 100.6 built in",
        "Favorites, your own stream links, and station list import/export",
        "Automatic reconnect and backup stream addresses, with stream diagnostics",
        "7-band equalizer with presets, Loudness and volume compensation",
        "Background playback, steering-wheel controls, and pause for calls and navigation",
        "Designed for the BYD DiLink 5.0 screen: landscape, portrait and split-screen",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("RDM Radio", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Version $versionName",
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
                Text(
                    "Internet radio for the BYD Song L and other DiLink 5.0 head units.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                features.forEach { feature ->
                    Row {
                        Text("•  ", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        Text(feature, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Designed by", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Eng. Ramy Abdelaziz",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Station directory: radio-browser.info (community-run). Stream availability depends on each station.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("Close", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}
