@file:OptIn(ExperimentalMaterial3Api::class)

package app.roundly.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.roundly.R
import app.roundly.core.model.Alarm
import app.roundly.core.model.AlarmType
import app.roundly.core.model.Preset
import app.roundly.core.model.ThemeMode
import app.roundly.ui.components.IconBadge
import app.roundly.ui.components.PrimaryButton
import app.roundly.ui.components.SectionHeader
import app.roundly.ui.format.alarmDisplayLabel
import app.roundly.ui.format.alarmSummary
import app.roundly.ui.format.countdownText
import app.roundly.ui.format.durationText
import app.roundly.ui.format.formatClock
import app.roundly.ui.format.relativeText
import app.roundly.ui.format.rememberNow
import app.roundly.ui.format.stepProgressText
import app.roundly.ui.format.typeIcon
import app.roundly.ui.format.typeLabel
import app.roundly.ui.preview.PreviewData
import app.roundly.ui.preview.ThemePreviews
import app.roundly.ui.theme.RoundlyTheme
import app.roundly.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun DashboardRoute(
    onCreate: (AlarmType?) -> Unit,
    onEdit: (Long) -> Unit,
    onPreset: (Long) -> Unit,
    onManagePresets: () -> Unit,
    onSettings: () -> Unit,
    onPermissions: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedText = stringResource(R.string.snackbar_deleted)
    val undoText = stringResource(R.string.action_undo)
    val copySuffix = stringResource(R.string.copy_suffix)

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }

    DashboardScreen(
        state = state,
        snackbar = snackbar,
        onCreate = { onCreate(null) },
        onEdit = onEdit,
        onPreset = onPreset,
        onManagePresets = onManagePresets,
        onSettings = onSettings,
        onPermissions = onPermissions,
        onToggle = viewModel::setEnabled,
        onDuplicate = { viewModel.duplicate(it, copySuffix) },
        onTogglePauseAll = viewModel::togglePauseAll,
        onRestartTimer = viewModel::restartTimer,
        onDelete = { id ->
            scope.launch {
                val deleted = viewModel.delete(id) ?: return@launch
                val result = snackbar.showSnackbar(deletedText, actionLabel = undoText, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(deleted)
            }
        },
    )
}

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    snackbar: SnackbarHostState,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onPreset: (Long) -> Unit,
    onManagePresets: () -> Unit,
    onSettings: () -> Unit,
    onPermissions: () -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onDuplicate: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onTogglePauseAll: () -> Unit,
    onRestartTimer: (Long) -> Unit,
) {
    val now = rememberNow()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    if (state.alarms.isNotEmpty()) {
                        IconButton(onClick = onTogglePauseAll) {
                            Icon(
                                if (state.pausedAll) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                                contentDescription = stringResource(if (state.pausedAll) R.string.action_resume_all else R.string.action_pause_all),
                            )
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = !state.isEmpty && !state.loading, enter = fadeIn(), exit = fadeOut()) {
                ExtendedFloatingActionButton(
                    onClick = onCreate,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_new_alarm)) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Crossfade(targetState = state.isEmpty, label = "dashboard-empty") { empty ->
            if (empty) {
                EmptyState(onCreate = onCreate, modifier = Modifier.padding(padding))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = Spacing.gutter,
                        end = Spacing.gutter,
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.issues.isNotEmpty()) {
                        item(key = "banner") { PermissionBanner(onClick = onPermissions) }
                    }
                    item(key = "hero") {
                        HeroCard(hero = state.hero, pausedAll = state.pausedAll, now = now, onResume = onTogglePauseAll)
                    }
                    if (state.presets.isNotEmpty()) {
                        item(key = "presets") {
                            PresetRow(presets = state.presets, onPreset = onPreset, onManage = onManagePresets)
                        }
                    }
                    if (state.alarms.isNotEmpty()) {
                        item(key = "alarms-header") { SectionHeader(stringResource(R.string.section_alarms)) }
                        items(state.alarms, key = { it.alarm.id }) { item ->
                            SwipeToDelete(onDelete = { onDelete(item.alarm.id) }) {
                                AlarmRow(
                                    item = item,
                                    now = now,
                                    pausedAll = state.pausedAll,
                                    onClick = { onEdit(item.alarm.id) },
                                    onToggle = { onToggle(item.alarm.id, it) },
                                    onDuplicate = { onDuplicate(item.alarm.id) },
                                    onDelete = { onDelete(item.alarm.id) },
                                )
                            }
                        }
                    }
                    if (state.recentTimers.isNotEmpty()) {
                        item(key = "recent-header") { SectionHeader(stringResource(R.string.section_recent_timers)) }
                        items(state.recentTimers, key = { "recent-${it.id}" }) { timer ->
                            SwipeToDelete(onDelete = { onDelete(timer.id) }) {
                                RecentTimerRow(timer = timer, now = now, onRestart = { onRestartTimer(timer.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = RoundlyTheme.extras.status.warning)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.banner_permissions_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.banner_permissions_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HeroCard(hero: AlarmItem?, pausedAll: Boolean, now: Long, onResume: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg)
                .animateContentSize(),
        ) {
            Text(stringResource(R.string.hero_next_alert), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(Spacing.sm))
            when {
                pausedAll -> {
                    Text(stringResource(R.string.hero_paused), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(Spacing.md))
                    FilledTonalButton(onClick = onResume, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.action_resume_all))
                    }
                }
                hero == null -> Text(
                    stringResource(R.string.hero_none),
                    style = MaterialTheme.typography.headlineMedium,
                )
                hero.alarm.type == AlarmType.IRREGULAR && hero.alarm.snoozeUntil == null -> {
                    Text(stringResource(R.string.within_window), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(alarmDisplayLabel(hero.alarm), style = MaterialTheme.typography.bodyLarge)
                }
                else -> {
                    val at = hero.alarm.nextTriggerAt ?: now
                    val label = alarmDisplayLabel(hero.alarm)
                    val description = stringResource(R.string.cd_hero, label, relativeText(at, now), formatClock(at))
                    Column(Modifier.clearAndSetSemantics { contentDescription = description }) {
                        Text(countdownText(at - now), style = MaterialTheme.typography.displayLarge)
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            stringResource(R.string.hero_label_at, label, formatClock(at)),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetRow(presets: List<Preset>, onPreset: (Long) -> Unit, onManage: () -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.section_quick_start))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(presets, key = { it.id }) { preset ->
                SuggestionChip(onClick = { onPreset(preset.id) }, label = { Text(preset.name) })
            }
            item(key = "manage") {
                AssistChip(
                    onClick = onManage,
                    label = { Text(stringResource(R.string.action_edit_presets)) },
                    leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@Composable
private fun AlarmRow(
    item: AlarmItem,
    now: Long,
    pausedAll: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val alarm = item.alarm
    val label = alarmDisplayLabel(alarm)
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .clickable(onClickLabel = stringResource(R.string.action_edit), onClick = onClick)
                .padding(start = Spacing.md, top = Spacing.md, bottom = Spacing.md, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(typeIcon(alarm.type), contentDescription = typeLabel(alarm.type))
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f).animateContentSize()) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    alarmSummary(alarm, item.stepProgress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    nextLine(alarm, now, pausedAll),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (alarm.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val progress = item.stepProgress
                if (progress != null && alarm.isActive) {
                    Text(stepProgressText(progress), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val endsAt = alarm.endsAt
                if (endsAt != null && alarm.isActive && endsAt > now) {
                    Text(
                        stringResource(R.string.ends_in, durationText(endsAt - now)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = label },
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onClick() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_duplicate)) },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        onClick = { menuOpen = false; onDuplicate() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun nextLine(alarm: Alarm, now: Long, pausedAll: Boolean): String {
    val next = alarm.nextTriggerAt
    val snooze = alarm.snoozeUntil
    return when {
        !alarm.enabled -> stringResource(R.string.alarm_state_off)
        pausedAll || alarm.pausedByMaster -> stringResource(R.string.alarm_state_paused)
        snooze != null -> stringResource(R.string.alarm_state_snoozed_until, formatClock(snooze))
        alarm.type == AlarmType.IRREGULAR -> stringResource(R.string.within_window)
        next == null -> stringResource(R.string.alarm_state_finished)
        else -> stringResource(R.string.next_at, formatClock(next), relativeText(next, now))
    }
}

@Composable
private fun RecentTimerRow(timer: Alarm, now: Long, onRestart: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(typeIcon(AlarmType.ONE_OFF))
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(alarmDisplayLabel(timer), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val duration = timer.oneOffDurationMs
                Text(
                    if (duration != null) durationText(duration) else timer.fireAt?.let { formatClock(it) }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRestart) {
                Icon(Icons.Outlined.Replay, contentDescription = stringResource(R.string.cd_restart_timer, alarmDisplayLabel(timer)))
            }
        }
    }
}

@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            state.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.lg),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        },
    ) { content() }
}

@Composable
private fun EmptyState(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(typeIcon(AlarmType.FIXED))
        Spacer(Modifier.height(Spacing.lg))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            stringResource(R.string.empty_text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        PrimaryButton(text = stringResource(R.string.action_create_alarm), onClick = onCreate)
    }
}

// --- Previews ------------------------------------------------------------------------------

@Composable
private fun DashboardPreviewContent(state: DashboardUiState, theme: ThemeMode) {
    RoundlyTheme(themeMode = theme) {
        DashboardScreen(
            state = state,
            snackbar = remember { SnackbarHostState() },
            onCreate = {}, onEdit = {}, onPreset = {}, onManagePresets = {}, onSettings = {}, onPermissions = {},
            onToggle = { _, _ -> }, onDuplicate = {}, onDelete = {}, onTogglePauseAll = {}, onRestartTimer = {},
        )
    }
}

@ThemePreviews
@Composable
private fun DashboardPreview() {
    DashboardPreviewContent(PreviewData.dashboard, ThemeMode.SYSTEM)
}

@ThemePreviews
@Composable
private fun DashboardEmptyPreview() {
    DashboardPreviewContent(DashboardUiState(loading = false), ThemeMode.SYSTEM)
}

@ThemePreviews
@Composable
private fun DashboardBannerPreview() {
    DashboardPreviewContent(PreviewData.dashboard.copy(issues = setOf(PermissionIssue.FULL_SCREEN)), ThemeMode.SYSTEM)
}
