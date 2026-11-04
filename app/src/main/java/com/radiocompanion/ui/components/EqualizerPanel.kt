package com.radiocompanion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.radiocompanion.R
import com.radiocompanion.service.audio.EqMath
import com.radiocompanion.service.audio.EqPreset
import com.radiocompanion.service.audio.EqPresets
import com.radiocompanion.service.audio.EqState
import java.util.Locale

private fun formatDb(db: Float): String =
    if (db == 0f) "0 dB" else String.format(Locale.US, "%+.1f dB", db)

/** Compact value above each band slider (10 sliders must fit across the screen). */
private fun formatDbShort(db: Float): String = when {
    db == 0f -> "0"
    db % 1f == 0f -> String.format(Locale.US, "%+d", db.toInt())
    else -> String.format(Locale.US, "%+.1f", db)
}

@Composable
private fun presetLabel(id: String): String = stringResource(
    when (id) {
        "flat" -> R.string.preset_flat
        "car" -> R.string.preset_car
        "bass" -> R.string.preset_bass
        "vocal" -> R.string.preset_vocal
        "arabic" -> R.string.preset_arabic
        "pop" -> R.string.preset_pop
        "rock" -> R.string.preset_rock
        "classical" -> R.string.preset_classical
        "treble" -> R.string.preset_treble
        else -> R.string.eq_custom
    }
)

/**
 * Full-width 10-band equalizer for the widescreen DiLink display. Also fits portrait
 * and split-screen: the header scrolls sideways and the 10 sliders share the width.
 */
@Composable
fun EqualizerDialog(
    state: EqState,
    onDismiss: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onLoudness: (Boolean) -> Unit,
    onPreset: (EqPreset) -> Unit,
    onBandChange: (index: Int, db: Float) -> Unit,
    onLevelMatch: (Float) -> Unit,
    onReset: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f),
        ) {
            Column(Modifier.fillMaxSize().padding(24.dp)) {
                EqHeader(state, onEnabled, onLoudness, onDismiss)
                Spacer(Modifier.heightIn(min = 12.dp))
                PresetRow(state.presetId, onPreset)
                Spacer(Modifier.heightIn(min = 12.dp))
                EqStatusLine(state)
                // Low → high frequencies run left to right in every language (not mirrored in Arabic).
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        EqMath.BAND_LABELS.forEachIndexed { i, label ->
                            EqBandSlider(
                                valueDb = state.gainsDb[i],
                                label = label,
                                active = state.enabled,
                                onValueChange = { onBandChange(i, it) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
                LevelMatchRow(state, onLevelMatch, onReset)
            }
        }
    }
}

@Composable
private fun EqHeader(
    state: EqState,
    onEnabled: (Boolean) -> Unit,
    onLoudness: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text(stringResource(R.string.eq_title), style = MaterialTheme.typography.headlineSmall)
            LabeledSwitch(stringResource(R.string.eq_power), state.enabled, onEnabled)
            LabeledSwitch(stringResource(R.string.eq_loudness), state.loudness, onLoudness)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(76.dp)) {
            Icon(Icons.Rounded.Close, stringResource(R.string.action_close), Modifier.size(36.dp))
        }
    }
}

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .heightIn(min = 64.dp)
            .clickable { onChange(!checked) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PresetRow(selectedId: String, onPreset: (EqPreset) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EqPresets.ALL.forEach { preset ->
            FilterChip(
                selected = preset.id == selectedId,
                onClick = { onPreset(preset) },
                label = { Text(presetLabel(preset.id), style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.heightIn(min = 56.dp),
            )
        }
        if (selectedId == EqPresets.CUSTOM_ID) {
            FilterChip(
                selected = true,
                onClick = {},
                label = { Text(stringResource(R.string.eq_custom), style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.heightIn(min = 56.dp),
            )
        }
    }
}

/** Tells the user what the head unit actually supports. */
@Composable
private fun EqStatusLine(state: EqState) {
    val mapped = stringResource(R.string.eq_status_mapped, state.nativeBandCount)
    val softBass = stringResource(R.string.eq_status_soft_bass)
    val comp = stringResource(R.string.eq_status_comp, formatDb(state.appliedMakeupDb))
    val compBands = stringResource(R.string.eq_status_comp_bands)
    val (text, isWarning) = when {
        !state.attached -> stringResource(R.string.eq_status_inactive) to false
        !state.equalizerAvailable -> stringResource(R.string.eq_status_unavailable) to true
        else -> buildString {
            if (state.nativeBandCount != EqMath.BAND_COUNT) append(mapped).append(' ')
            if (state.loudness && !state.hardwareBassBoost) append(softBass).append(' ')
            if (state.enabled) append(if (state.makeupGainAvailable) comp else compBands)
        }.trim() to false
    }
    if (text.isNotEmpty()) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

@Composable
private fun LevelMatchRow(state: EqState, onLevelMatch: (Float) -> Unit, onReset: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.widthIn(min = 160.dp)) {
            Text(stringResource(R.string.eq_level_match), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.eq_level_match_hint),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Slider(
            value = state.levelMatchDb,
            onValueChange = { onLevelMatch(EqMath.snap(it)) },
            valueRange = 0f..EqMath.MAX_LEVEL_MATCH_DB,
            steps = (EqMath.MAX_LEVEL_MATCH_DB / EqMath.STEP_DB).toInt() - 1,
            modifier = Modifier.weight(1f).heightIn(min = 64.dp),
        )
        Text(
            formatDb(state.levelMatchDb),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.widthIn(min = 110.dp).padding(start = 12.dp),
        )
        TextButton(onClick = onReset, modifier = Modifier.heightIn(min = 64.dp)) {
            Text(stringResource(R.string.eq_reset), style = MaterialTheme.typography.titleLarge)
        }
    }
}

/**
 * Vertical band slider with a large touch area: drag or tap anywhere in the
 * column; double-tap resets to 0 dB. The fill grows from the 0 dB line.
 */
@Composable
fun EqBandSlider(
    valueDb: Float,
    label: String,
    active: Boolean,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val accent = if (active) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.6f)
    val track = colors.surfaceVariant
    val thumbInner = colors.surface
    val latestOnChange by rememberUpdatedState(onValueChange)
    val min = EqMath.UI_MIN_DB
    val max = EqMath.UI_MAX_DB

    val bandDescription = stringResource(R.string.eq_band_cd, label)
    Column(modifier.widthIn(min = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            formatDbShort(valueDb),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
        )
        Spacer(Modifier.heightIn(min = 8.dp))
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .semantics {
                    contentDescription = bandDescription
                    stateDescription = formatDb(valueDb)
                    progressBarRangeInfo = ProgressBarRangeInfo(valueDb, min..max)
                    setProgress { v -> latestOnChange(EqMath.snap(v)); true }
                }
                .pointerInput(Unit) {
                    val thumbR = 20.dp.toPx()
                    fun yToDb(y: Float): Float {
                        val usable = (size.height - 2 * thumbR).coerceAtLeast(1f)
                        val fraction = 1f - ((y - thumbR) / usable).coerceIn(0f, 1f)
                        return EqMath.snap(min + fraction * (max - min))
                    }
                    detectTapGestures(
                        onDoubleTap = { latestOnChange(0f) },
                        onTap = { latestOnChange(yToDb(it.y)) },
                    )
                }
                .pointerInput(Unit) {
                    val thumbR = 20.dp.toPx()
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        val usable = (size.height - 2 * thumbR).coerceAtLeast(1f)
                        val fraction = 1f - ((change.position.y - thumbR) / usable).coerceIn(0f, 1f)
                        latestOnChange(EqMath.snap(min + fraction * (max - min)))
                    }
                },
        ) {
            val thumbR = 20.dp.toPx()
            val cx = size.width / 2f
            val top = thumbR
            val bottom = size.height - thumbR
            fun dbToY(db: Float) = bottom - (db - min) / (max - min) * (bottom - top)
            val zeroY = dbToY(0f)
            val valueY = dbToY(valueDb)

            // Track
            drawLine(track, Offset(cx, top), Offset(cx, bottom), strokeWidth = 10.dp.toPx(), cap = StrokeCap.Round)
            // 0 dB tick
            drawLine(track, Offset(cx - 22.dp.toPx(), zeroY), Offset(cx + 22.dp.toPx(), zeroY), strokeWidth = 3.dp.toPx())
            // Fill from 0 dB to value
            drawLine(accent, Offset(cx, zeroY), Offset(cx, valueY), strokeWidth = 10.dp.toPx(), cap = StrokeCap.Round)
            // Thumb
            drawCircle(accent, radius = thumbR, center = Offset(cx, valueY))
            drawCircle(thumbInner, radius = thumbR * 0.4f, center = Offset(cx, valueY))
        }
        Spacer(Modifier.heightIn(min = 8.dp))
        Text(label, style = MaterialTheme.typography.titleLarge, maxLines = 1)
    }
}
