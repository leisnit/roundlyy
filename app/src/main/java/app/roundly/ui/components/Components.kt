@file:OptIn(ExperimentalMaterial3Api::class)

package app.roundly.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.roundly.R
import app.roundly.core.model.AlarmLimits
import app.roundly.ui.format.durationText
import app.roundly.ui.theme.Sizes
import app.roundly.ui.theme.Spacing
import app.roundly.ui.theme.tabular
import kotlinx.coroutines.delay

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(top = Spacing.lg, bottom = Spacing.sm)
            .semantics { heading() },
    )
}

/** A calm tonal card: 20 dp corners, no shadow. */
@Composable
fun RoundlyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shapeModifier = modifier.fillMaxWidth()
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = shapeModifier,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) { Column(Modifier.padding(contentPadding).animateContentSize(), content = content) }
    } else {
        Surface(
            modifier = shapeModifier,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) { Column(Modifier.padding(contentPadding).animateContentSize(), content = content) }
    }
}

/** A row in a settings-style list, at least 56 dp tall. */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Spacing.md))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.md))
            trailing()
        }
    }
}

/** Full-width primary action in the thumb zone. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.primaryButtonHeight),
        shape = MaterialTheme.shapes.large,
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Icon button that repeats while held, for quick stepping. */
@Composable
fun RepeatingIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val latest by rememberUpdatedState(onClick)
    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled) {
            delay(450)
            while (true) {
                latest()
                delay(70)
            }
        }
    }
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = Modifier.size(Sizes.minTouchTarget),
    ) { Icon(icon, contentDescription = contentDescription) }
}

/** "- 12 +" stepper with a caption. Announces its value to TalkBack. */
@Composable
fun NumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: String,
    modifier: Modifier = Modifier,
    valueText: String = value.toString(),
    step: Int = 1,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RepeatingIconButton(
                icon = Icons.Outlined.Remove,
                contentDescription = stringResource(R.string.cd_decrease, label),
                enabled = value > range.first,
                onClick = { onValueChange((value - step).coerceIn(range)) },
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.headlineMedium.tabular(),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 64.dp)
                    .semantics {
                        contentDescription = label
                        stateDescription = valueText
                    },
            )
            RepeatingIconButton(
                icon = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.cd_increase, label),
                enabled = value < range.last,
                onClick = { onValueChange((value + step).coerceIn(range)) },
            )
        }
    }
}

/**
 * Hours and minutes duration picker built from steppers, plus a numeric keyboard option.
 * (The Material TimePicker picks a clock time with AM/PM, which is wrong for durations.)
 */
@Composable
fun DurationPicker(
    valueMs: Long,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    minMs: Long = AlarmLimits.MIN_INTERVAL_MS,
    maxMs: Long = AlarmLimits.MAX_INTERVAL_MS,
) {
    var showKeyboard by rememberSaveable { mutableStateOf(false) }
    val totalMinutes = (valueMs / AlarmLimits.MINUTE_MS).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val maxHours = (maxMs / AlarmLimits.HOUR_MS).toInt()

    fun emit(h: Int, m: Int) {
        val ms = (h * 60L + m) * AlarmLimits.MINUTE_MS
        onValueChange(ms.coerceIn(minMs, maxMs))
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (maxHours > 0) {
            NumberStepper(
                value = hours,
                onValueChange = { emit(it, minutes) },
                range = 0..maxHours,
                label = stringResource(R.string.unit_hours),
            )
        }
        NumberStepper(
            value = minutes,
            onValueChange = { emit(hours, it) },
            range = 0..59,
            label = stringResource(R.string.unit_minutes),
            valueText = minutes.toString().padStart(2, '0'),
        )
        IconButton(onClick = { showKeyboard = true }, modifier = Modifier.size(Sizes.minTouchTarget)) {
            Icon(Icons.Outlined.Keyboard, contentDescription = stringResource(R.string.cd_type_duration))
        }
    }

    if (showKeyboard) {
        DurationKeyboardDialog(
            initialMs = valueMs,
            minMs = minMs,
            maxMs = maxMs,
            onConfirm = {
                onValueChange(it)
                showKeyboard = false
            },
            onDismiss = { showKeyboard = false },
        )
    }
}

@Composable
private fun DurationKeyboardDialog(
    initialMs: Long,
    minMs: Long,
    maxMs: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMinutes = (initialMs / AlarmLimits.MINUTE_MS).toInt()
    var hoursText by rememberSaveable { mutableStateOf((initialMinutes / 60).toString()) }
    var minutesText by rememberSaveable { mutableStateOf((initialMinutes % 60).toString()) }
    val ms = ((hoursText.toIntOrNull() ?: 0) * 60L + (minutesText.toIntOrNull() ?: 0)) * AlarmLimits.MINUTE_MS
    val valid = ms in minMs..maxMs && (minutesText.toIntOrNull() ?: 0) < 60

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.duration_dialog_title)) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = { hoursText = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.unit_hours)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { minutesText = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.unit_minutes)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.size(Spacing.sm))
                Text(
                    text = stringResource(R.string.duration_dialog_range, durationText(minMs), durationText(maxMs)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(ms) }, enabled = valid) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Clock-time picker (for "start at" and timer clock times; never for durations). */
@Composable
fun TimeOfDayDialog(
    initialMinuteOfDay: Int,
    is24Hour: Boolean,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinuteOfDay / 60,
        initialMinute = initialMinuteOfDay % 60,
        is24Hour = is24Hour,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
    )
}

/** Round icon badge used for alarm types. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Surface(
        modifier = modifier.size(Sizes.iconBadge),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Single-line text input dialog (rename, save as preset). */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    label: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    maxLength: Int = 40,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(maxLength) },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
