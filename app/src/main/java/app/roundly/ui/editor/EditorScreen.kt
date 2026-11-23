@file:OptIn(ExperimentalMaterial3Api::class)

package app.roundly.ui.editor

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.roundly.R
import app.roundly.core.model.AfterLastStep
import app.roundly.core.model.AlarmLimits
import app.roundly.core.model.AlarmType
import app.roundly.core.model.AlertMode
import app.roundly.core.model.StartMode
import app.roundly.core.model.Step
import app.roundly.core.schedule.AlarmDraft
import app.roundly.core.schedule.OneOffMode
import app.roundly.core.schedule.ScheduleProblem
import app.roundly.ui.components.DurationPicker
import app.roundly.ui.components.NumberStepper
import app.roundly.ui.components.PrimaryButton
import app.roundly.ui.components.RoundlyCard
import app.roundly.ui.components.SectionHeader
import app.roundly.ui.components.SettingsRow
import app.roundly.ui.components.TextInputDialog
import app.roundly.ui.components.TimeOfDayDialog
import app.roundly.ui.format.alertModeDescription
import app.roundly.ui.format.alertModeLabel
import app.roundly.ui.format.durationText
import app.roundly.ui.format.is24Hour
import app.roundly.ui.format.problemText
import app.roundly.ui.format.typeIcon
import app.roundly.ui.format.typeLabel
import app.roundly.ui.preview.ThemePreviews
import app.roundly.ui.theme.Motion
import app.roundly.ui.theme.RoundlyTheme
import app.roundly.ui.theme.Spacing

private const val MIN = AlarmLimits.MINUTE_MS

@Composable
fun EditorRoute(onClose: () -> Unit, viewModel: EditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val presetSavedText = stringResource(R.string.snackbar_preset_saved)

    LaunchedEffect(state.saved) { if (state.saved) onClose() }
    LaunchedEffect(state.presetSaved) {
        if (state.presetSaved) {
            snackbar.showSnackbar(presetSavedText)
            viewModel.presetMessageShown()
        }
    }

    EditorScreen(
        state = state,
        snackbar = snackbar,
        onClose = onClose,
        onUpdate = viewModel::update,
        onSave = viewModel::save,
        onTest = viewModel::testAlert,
        onSaveAsPreset = viewModel::saveAsPreset,
    )
}

@Composable
fun EditorScreen(
    state: EditorUiState,
    snackbar: SnackbarHostState,
    onClose: () -> Unit,
    onUpdate: ((AlarmDraft) -> AlarmDraft) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onSaveAsPreset: (String) -> Unit,
) {
    var showPresetDialog by rememberSaveable { mutableStateOf(false) }
    val draft = state.draft

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.editor_title_new else R.string.editor_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                PrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = onSave,
                    enabled = state.canSave,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.loading) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .animateContentSize(Motion.medium()),
        ) {
            OutlinedTextField(
                value = draft.label,
                onValueChange = { text -> onUpdate { it.copy(label = text.take(AlarmLimits.MAX_LABEL_LENGTH)) } },
                label = { Text(stringResource(R.string.editor_label)) },
                placeholder = { Text(typeLabel(draft.type)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader(stringResource(R.string.editor_type))
            TypeSelector(selected = draft.type, onSelect = { type -> onUpdate { it.copy(type = type) } })

            AnimatedContent(
                targetState = draft.type,
                transitionSpec = { fadeIn(Motion.medium()) togetherWith fadeOut(Motion.short()) },
                label = "editor-type",
            ) { type ->
                Column {
                    when (type) {
                        AlarmType.FIXED -> FixedSection(draft, onUpdate)
                        AlarmType.IRREGULAR -> IrregularSection(draft, onUpdate)
                        AlarmType.STEPPED -> SteppedSection(draft, onUpdate)
                        AlarmType.ONE_OFF -> TimerSection(draft, onUpdate)
                    }
                }
            }

            ProblemList(state.problems)

            if (draft.type != AlarmType.ONE_OFF) {
                StartSection(draft, onUpdate)
                RunForSection(draft, onUpdate)
            }

            AlertSection(draft, state.watchAvailable, onUpdate)

            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = onTest, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(stringResource(R.string.action_test_alert))
                }
                OutlinedButton(
                    onClick = { showPresetDialog = true },
                    enabled = state.problems.isEmpty(),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text(stringResource(R.string.action_save_as_preset)) }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (showPresetDialog) {
        TextInputDialog(
            title = stringResource(R.string.action_save_as_preset),
            initial = draft.label,
            label = stringResource(R.string.preset_name),
            onConfirm = {
                onSaveAsPreset(it)
                showPresetDialog = false
            },
            onDismiss = { showPresetDialog = false },
        )
    }
}

@Composable
private fun TypeSelector(selected: AlarmType, onSelect: (AlarmType) -> Unit) {
    val types = AlarmType.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        types.forEachIndexed { index, type ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index, types.size),
                icon = { Icon(typeIcon(type), contentDescription = null, modifier = Modifier.padding(0.dp)) },
                label = { Text(typeLabel(type), maxLines = 1) },
            )
        }
    }
}

@Composable
private fun FixedSection(draft: AlarmDraft, onUpdate: ((AlarmDraft) -> AlarmDraft) -> Unit) {
    SectionHeader(stringResource(R.string.editor_every))
    RoundlyCard {
        DurationPicker(
            valueMs = draft.intervalMs ?: 30 * MIN,
            onValueChange = { ms -> onUpdate { it.copy(intervalMs = ms) } },
        )
    }
}

@Composable
private fun IrregularSection(draft: AlarmDraft, onUpdate: ((AlarmDraft) -> AlarmDraft) -> Unit) {
    SectionHeader(stringResource(R.string.editor_window))
    RoundlyCard {
        DurationPicker(
            valueMs = draft.windowMs ?: AlarmLimits.HOUR_MS,
            onValueChange = { ms -> onUpdate { it.copy(windowMs = ms) } },
            minMs = AlarmLimits.MIN_WINDOW_MS,
            maxMs = AlarmLimits.MAX_WINDOW_MS,
        )
    }
    Spacer(Modifier.height(Spacing.sm))
    RoundlyCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            NumberStepper(
                value = draft.alertsPerWindow ?: 4,
                onValueChange = { n -> onUpdate { it.copy(alertsPerWindow = n) } },
                range = AlarmLimits.ALERTS_PER_WINDOW,
                label = stringResource(R.string.editor_alerts_per_window),
            )
            val gapMinutes = ((draft.minGapMs ?: AlarmLimits.DEFAULT_MIN_GAP_MS) / MIN).toInt()
            NumberStepper(
                value = gapMinutes,
                onValueChange = { m -> onUpdate { it.copy(minGapMs = m * MIN) } },
                range = 0..120,
                label = stringResource(R.string.editor_min_gap),
                valueText = stringResource(R.string.duration_min, gapMinutes),
            )
        }
    }
    Spacer(Modifier.height(Spacing.sm))
    Text(
        stringResource(R.string.editor_irregular_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SteppedSection(draft: AlarmDraft, onUpdate: ((AlarmDraft) -> AlarmDraft) -> Unit) {
    var editingStep by rememberSaveable { mutableIntStateOf(-1) }
    SectionHeader(stringResource(R.string.editor_steps))
    RoundlyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.sm, horizontal = Spacing.md)) {
        draft.steps.forEachIndexed { index, step ->
            if (index > 0) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            StepRow(
                index = index,
                step = step,
                count = draft.steps.size,
                expanded = editingStep == index,
                onExpand = { editingStep = if (editingStep == index) -1 else index },
                onChange = { updated -> onUpdate { d -> d.copy(steps = d.steps.toMutableList().also { it[index] = updated }) } },
                onMove = { delta ->
                    onUpdate { d ->
                        val list = d.steps.toMutableList()
                        val target = (index + delta).coerceIn(0, list.lastIndex)
                        list.add(target, list.removeAt(index))
                        d.copy(steps = list)
                    }
                    if (editingStep == index) editingStep = index + delta
                },
                onRemove = {
                    onUpdate { d -> d.copy(steps = d.steps.toMutableList().also { it.removeAt(index) }) }
                    editingStep = -1
                },
            )
        }
        if (draft.steps.size < AlarmLimits.MAX_STEPS) {
            TextButton(
                onClick = {
                    onUpdate { d -> d.copy(steps = d.steps + (d.steps.lastOrNull() ?: Step(15 * MIN, 4)).copy()) }
                    editingStep = draft.steps.size
                },
                modifier = Modifier.height(48.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.editor_add_step))
            }
        }
    }

    SectionHeader(stringResource(R.string.editor_after_last_step))
    val options = listOf(AfterLastStep.STOP to R.string.after_stop, AfterLastStep.CONTINUE to R.string.after_continue)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = draft.afterLastStep == value,
                onClick = { onUpdate { it.copy(afterLastStep = value) } },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun StepRow(
    index: Int,
    step: Step,
    count: Int,
    expanded: Boolean,
    onExpand: () -> Unit,
    onChange: (Step) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.animateContentSize(Motion.medium())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onExpand, modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.step_summary, index + 1, durationText(step.intervalMs), step.repeats),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IconButton(onClick = { onMove(-1) }, enabled = index > 0) {
                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(R.string.cd_move_step_up, index + 1))
            }
            IconButton(onClick = { onMove(1) }, enabled = index < count - 1) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_move_step_down, index + 1))
            }
            IconButton(onClick = onRemove, enabled = count > 1) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.cd_remove_step, index + 1))
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(bottom = Spacing.md)) {
                DurationPicker(valueMs = step.intervalMs, onValueChange = { onChange(step.copy(intervalMs = it)) })
                Spacer(Modifier.height(Spacing.sm))
                NumberStepper(
                    value = step.repeats,
                    onValueChange = { onChange(step.copy(repeats = it)) },
                    range = AlarmLimits.STEP_REPEATS,
                    label = stringResource(R.string.editor_repeats),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimerSection(draft: AlarmDraft, onUpdate: ((AlarmDraft) -> AlarmDraft) -> Unit) {
    var pickTime by rememberSaveable { mutableStateOf(false) }
    SectionHeader(stringResource(R.string.editor_timer))
    val modes = listOf(OneOffMode.COUNTDOWN to R.string.timer_countdown, OneOffMode.CLOCK_TIME to R.string.timer_at_time)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        modes.forEachIndexed { i, (mode, label) ->
            SegmentedButton(
                selected = draft.oneOffMode == mode,
                onClick = {
                    onUpdate {
                        it.copy(oneOffMode = mode, oneOffMinuteOfDay = it.oneOffMinuteOfDay ?: defaultMinuteOfDay())
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                label = { Text(stringResource(label)) },
            )
        }
    }
    Spacer(Modifier.height(Spacing.md))
    when (draft.oneOffMode) {
        OneOffMode.COUNTDOWN -> {
            val quick = listOf(15 * MIN, 30 * MIN, 60 * MIN, 120 * MIN)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                quick.forEach { ms ->
                    FilterChip(
                        selected = draft.oneOffDurationMs == ms,
                        onClick = { onUpdate { it.copy(oneOffDurationMs = ms) } },
                        label = { Text(durationText(ms)) },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            RoundlyCard {
                Text(
                    stringResource(R.string.timer_custom),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DurationPicker(
                    valueMs = draft.oneOffDurationMs ?: 30 * MIN,
                    onValueChange = { ms -> onUpdate { it.copy(oneOffDurationMs = ms) } },
                    maxMs = AlarmLimits.MAX_INTERVAL_MS,
                )
            }
        }
        OneOffMode.CLOCK_TIME -> RoundlyCard(onClick = { pickTime = true }) {
            SettingsRow(
                title = stringResource(R.string.timer_fires_at),
                subtitle = minuteOfDayText(draft.oneOffMinuteOfDay ?: defaultMinuteOfDay()),
            )
        }
    }
    if (pickTime) {
        TimeOfDayDialog(
            initialMinuteOfDay = draft.oneOffMinuteOfDay ?: defaultMinuteOfDay(),
            is24Hour = is24Hour(),
            onConfirm = { minute ->
                onUpdate { it.copy(oneOffMinuteOfDay = minute) }
                pickTime = false
            },
            onDismiss = { pickTime = false },
        )
    }
}

@Composable
private fun StartSection(draft: AlarmDraft, onUpdate: ((AlarmDraft) -> AlarmDraft) -> Unit) {
    var pickTime by rememberSaveable { mutableStateOf(false) }
    SectionHeader(stringResource(R.string.editor_start))
    val options = listOf(StartMode.NOW to R.string.start_now, StartMode.AT_TIME to R.string.start_at_time)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (mode, label) ->
            SegmentedButton(
                selected = draft.startMode == mode,
                onClick = {
                    onUpdate { it.copy(startMode = mode, startMinuteOfDay = it.startMinuteOfDay ?: defaultMinuteOfDay()) }
                },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                label = { Text(stringResource(label)) },
            )
        }
    }
    AnimatedVisibility(visible = draft.startMode == StartMode.AT_TIME) {
        Column {
            Spacer(Modifier.height(Spacing.sm))
            RoundlyCard(onClick = { pickTime = true }) {
                SettingsRow(
                    title = stringResource(R.string.start_time),
                    subtitle = stringResource(
                        R.string.start_time_hint,
                        minuteOfDayText(draft.startMinuteOfDay ?: defaultMinuteOfDay()),
                    ),
                )
            }
        }
    }
    if (pickTime) {
        TimeOfDayDialog(
            initialMinuteOfDay = draft.startMinuteOfDay ?: defaultMinuteOfDay(),
            is24Hour = is24Hour(),
            onConfirm = { minute ->
                onUpdate { it.copy(startMinuteOfDay = minute) }
                pickTime = false
            },
            onDismiss = { pickTime = false },
        )
    }
}

@Composable
private fun RunForSection(draft: AlarmDraft, onUpdate: ((AlarmDraft) -> AlarmDraft) -> Unit) {
    SectionHeader(stringResource(R.string.editor_run_for))
    RoundlyCard {
        SettingsRow(
            title = stringResource(R.string.run_for_toggle),
            subtitle = draft.runForMs?.let { stringResource(R.string.run_for_summary, durationText(it)) }
                ?: stringResource(R.string.run_for_off),
            trailing = {
                Switch(
                    checked = draft.runForMs != null,
                    onCheckedChange = { on -> onUpdate { it.copy(runForMs = if (on) 2 * AlarmLimits.HOUR_MS else null) } },
                )
            },
        )
        AnimatedVisibility(visible = draft.runForMs != null) {
            DurationPicker(
                valueMs = draft.runForMs ?: 2 * AlarmLimits.HOUR_MS,
                onValueChange = { ms -> onUpdate { it.copy(runForMs = ms) } },
            )
        }
    }
}

@Composable
private fun AlertSection(draft: AlarmDraft, watchAvailable: Boolean, onUpdate: ((AlarmDraft) -> AlarmDraft) -> Unit) {
    val context = LocalContext.current
    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            onUpdate { d -> d.copy(ringtoneUri = uri?.toString()) }
        }
    }

    SectionHeader(stringResource(R.string.editor_alert))
    RoundlyCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.xs, horizontal = Spacing.md)) {
        Column(Modifier.selectableGroup()) {
            AlertMode.entries.forEach { mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = draft.alertMode == mode, role = Role.RadioButton) {
                            onUpdate { it.copy(alertMode = mode) }
                        }
                        .padding(vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = draft.alertMode == mode, onClick = null)
                    Spacer(Modifier.width(Spacing.md))
                    Column {
                        Text(alertModeLabel(mode), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            alertModeDescription(mode),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    val usesSound = draft.alertMode == AlertMode.SOUND_AND_VIBRATION ||
        draft.alertMode == AlertMode.SOUND_ONLY ||
        draft.alertMode == AlertMode.DISCREET_ESCALATION
    AnimatedVisibility(visible = usesSound) {
        Column {
            Spacer(Modifier.height(Spacing.sm))
            val ringtoneName = remember(draft.ringtoneUri) {
                draft.ringtoneUri?.let { uri ->
                    runCatching { RingtoneManager.getRingtone(context, uri.toUri())?.getTitle(context) }.getOrNull()
                }
            } ?: stringResource(R.string.ringtone_default)
            RoundlyCard(
                onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, draft.ringtoneUri?.toUri())
                    ringtoneLauncher.launch(intent)
                },
            ) {
                SettingsRow(title = stringResource(R.string.ringtone), subtitle = ringtoneName, leading = Icons.Outlined.MusicNote)
            }
        }
    }

    if (watchAvailable) {
        Spacer(Modifier.height(Spacing.sm))
        RoundlyCard {
            SettingsRow(
                title = stringResource(R.string.watch_only),
                subtitle = stringResource(R.string.watch_only_desc),
                leading = Icons.Outlined.Watch,
                trailing = {
                    Switch(checked = draft.watchOnly, onCheckedChange = { on -> onUpdate { it.copy(watchOnly = on) } })
                },
            )
        }
    }
}

@Composable
private fun ProblemList(problems: List<ScheduleProblem>) {
    AnimatedVisibility(visible = problems.isNotEmpty()) {
        Column(
            Modifier
                .padding(top = Spacing.md)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            problems.forEach {
                Text(problemText(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun minuteOfDayText(minuteOfDay: Int): String {
    val today = java.time.LocalDate.now()
    val epoch = today.atTime(minuteOfDay / 60, minuteOfDay % 60)
        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    return app.roundly.ui.format.formatClock(epoch)
}

/** Next full hour, as a sensible starting point for time pickers. */
private fun defaultMinuteOfDay(): Int = (java.time.LocalTime.now().hour + 1) % 24 * 60

// --- Previews ------------------------------------------------------------------------------

@Composable
private fun EditorPreviewContent(draft: AlarmDraft, problems: List<ScheduleProblem> = emptyList()) {
    RoundlyTheme {
        EditorScreen(
            state = EditorUiState(loading = false, draft = draft, problems = problems, watchAvailable = true),
            snackbar = remember { SnackbarHostState() },
            onClose = {}, onUpdate = {}, onSave = {}, onTest = {}, onSaveAsPreset = {},
        )
    }
}

@ThemePreviews
@Composable
private fun EditorFixedPreview() {
    EditorPreviewContent(AlarmDraft(label = "Drink water", type = AlarmType.FIXED, runForMs = 4 * AlarmLimits.HOUR_MS))
}

@ThemePreviews
@Composable
private fun EditorIrregularImpossiblePreview() {
    EditorPreviewContent(
        AlarmDraft(type = AlarmType.IRREGULAR, windowMs = 30 * MIN, alertsPerWindow = 12, minGapMs = 5 * MIN),
        problems = listOf(ScheduleProblem.IrregularImpossible(12, 5 * MIN, 30 * MIN, 60 * MIN + 1)),
    )
}

@ThemePreviews
@Composable
private fun EditorSteppedPreview() {
    EditorPreviewContent(
        AlarmDraft(
            label = "Training block",
            type = AlarmType.STEPPED,
            steps = listOf(Step(15 * MIN, 4), Step(30 * MIN, 2), Step(60 * MIN, 4)),
        ),
    )
}

@ThemePreviews
@Composable
private fun EditorTimerPreview() {
    EditorPreviewContent(AlarmDraft(label = "Tea", type = AlarmType.ONE_OFF, oneOffDurationMs = 15 * MIN))
}
