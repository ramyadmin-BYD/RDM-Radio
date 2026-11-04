package com.radiocompanion.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.radiocompanion.R
import com.radiocompanion.update.UpdateState

/**
 * "Update available" prompt. Shows download progress, the install-permission step
 * (Android asks once to allow RDM Radio to install apps) and errors.
 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    currentVersion: String,
    onUpdate: () -> Unit,
    onOpenSettings: () -> Unit,
    onLater: () -> Unit,
) {
    val update = when (state) {
        is UpdateState.Available -> state.update
        is UpdateState.Downloading -> state.update
        is UpdateState.NeedsPermission -> state.update
        is UpdateState.Installing -> state.update
        is UpdateState.Failed -> state.update
        UpdateState.Idle -> return
    }
    val busy = state is UpdateState.Downloading || state is UpdateState.Installing

    AlertDialog(
        onDismissRequest = { if (!busy) onLater() },
        title = { Text(stringResource(R.string.update_title), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.update_message, update.version, currentVersion),
                    style = MaterialTheme.typography.bodyLarge,
                )
                when (state) {
                    is UpdateState.Downloading -> {
                        Text(stringResource(R.string.update_downloading, state.percent), style = MaterialTheme.typography.titleLarge)
                        LinearProgressIndicator(
                            progress = { state.percent / 100f },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 8.dp),
                        )
                    }
                    is UpdateState.Installing ->
                        Text(stringResource(R.string.update_installing), style = MaterialTheme.typography.titleLarge)
                    is UpdateState.NeedsPermission ->
                        Text(
                            stringResource(R.string.update_permission),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    is UpdateState.Failed ->
                        Text(
                            state.reason.resolve(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    else -> Unit
                }
                if (update.notes.isNotBlank() && !busy) {
                    Text(stringResource(R.string.update_whats_new), style = MaterialTheme.typography.titleLarge)
                    Text(update.notes, style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.NeedsPermission ->
                    // Settings first; after allowing, "Update now" re-checks the permission.
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 64.dp)) {
                            Text(stringResource(R.string.update_open_settings), style = MaterialTheme.typography.titleLarge)
                        }
                        Button(onClick = onUpdate, modifier = Modifier.heightIn(min = 64.dp)) {
                            Text(stringResource(R.string.update_now), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                is UpdateState.Available, is UpdateState.Failed ->
                    Button(onClick = onUpdate, modifier = Modifier.heightIn(min = 64.dp)) {
                        Text(stringResource(R.string.update_now), style = MaterialTheme.typography.titleLarge)
                    }
                else -> Unit
            }
        },
        dismissButton = {
            if (!busy) {
                TextButton(onClick = onLater, modifier = Modifier.heightIn(min = 64.dp)) {
                    Text(stringResource(R.string.update_later), style = MaterialTheme.typography.titleLarge)
                }
            }
        },
    )
}
