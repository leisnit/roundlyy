@file:OptIn(ExperimentalMaterial3Api::class)

package app.roundly.ui.settings

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.roundly.BuildConfig
import app.roundly.R
import app.roundly.core.model.AlertMode
import app.roundly.core.model.AppSettings
import app.roundly.core.model.ThemeMode
import app.roundly.core.model.TimeFormat
import app.roundly.data.backup.BackupManager
import app.roundly.data.repository.SettingsRepository
import app.roundly.scheduling.AlarmScheduler
import app.roundly.ui.components.NumberStepper
import app.roundly.ui.components.RoundlyCard
import app.roundly.ui.components.SectionHeader
import app.roundly.ui.components.SettingsRow
import app.roundly.ui.format.alertModeLabel
import app.roundly.ui.format.themeModeLabel
import app.roundly.ui.preview.ThemePreviews
import app.roundly.ui.theme.RoundlyTheme
import app.roundly.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SettingsMessage {
    data class Exported(val alarms: Int) : SettingsMessage
    data class Imported(val alarms: Int, val presets: Int) : SettingsMessage
    data class Failed(val reason: String?) : SettingsMessage
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scheduler: AlarmScheduler,
    private val backup: BackupManager,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _messages = Channel<SettingsMessage>(Channel.BUFFERED)
    val messages: Flow<SettingsMessage> = _messages.receiveAsFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun exportFileName(): String = backup.suggestedFileName()

    fun export(uri: Uri) {
        viewModelScope.launch {
            backup.export(uri).fold(
                onSuccess = { _messages.send(SettingsMessage.Exported(it)) },
                onFailure = { _messages.send(SettingsMessage.Failed(it.message)) },
            )
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            scheduler.importBackup(uri).fold(
                onSuccess = { _messages.send(SettingsMessage.Imported(it.alarms, it.customPresets)) },
                onFailure = { _messages.send(SettingsMessage.Failed(it.message)) },
            )
        }
    }
}

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onPermissions: () -> Unit,
    onPresets: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var confirmImport by rememberSaveable { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is SettingsMessage.Exported ->
                    resources.getQuantityString(R.plurals.export_done, message.alarms, message.alarms)
                is SettingsMessage.Imported ->
                    resources.getQuantityString(R.plurals.import_done, message.alarms, message.alarms, message.presets)
                is SettingsMessage.Failed -> message.reason ?: resources.getString(R.string.backup_failed)
            }
            snackbar.showSnackbar(text)
        }
    }

    SettingsScreen(
        settings = settings,
        snackbar = snackbar,
        onBack = onBack,
        onUpdate = viewModel::update,
        onPermissions = onPermissions,
        onPresets = onPresets,
        onExport = { exportLauncher.launch(viewModel.exportFileName()) },
        onImport = { confirmImport = true },
    )

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = { Text(stringResource(R.string.import_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }) { Text(stringResource(R.string.action_choose_file)) }
            },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onPermissions: () -> Unit,
    onPresets: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    var modeDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        ) {
            SectionHeader(stringResource(R.string.settings_alerts))
            RoundlyCard {
                SettingsRow(
                    title = stringResource(R.string.settings_default_mode),
                    subtitle = alertModeLabel(settings.defaultAlertMode),
                    onClick = { modeDialog = true },
                )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(Spacing.sm))
                Row(Modifier.fillMaxWidth()) {
                    NumberStepper(
                        value = settings.snoozeMinutes,
                        onValueChange = { v -> onUpdate { it.copy(snoozeMinutes = v) } },
                        range = AppSettings.SNOOZE_RANGE,
                        label = stringResource(R.string.settings_snooze),
                        valueText = stringResource(R.string.duration_min, settings.snoozeMinutes),
                        modifier = Modifier.weight(1f),
                    )
                    NumberStepper(
                        value = settings.ringTimeoutMinutes,
                        onValueChange = { v -> onUpdate { it.copy(ringTimeoutMinutes = v) } },
                        range = AppSettings.RING_TIMEOUT_RANGE,
                        label = stringResource(R.string.settings_ring_timeout),
                        valueText = stringResource(R.string.duration_min, settings.ringTimeoutMinutes),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SectionHeader(stringResource(R.string.settings_appearance))
            RoundlyCard {
                Text(stringResource(R.string.settings_time_format), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(Spacing.sm))
                val formats = listOf(TimeFormat.SYSTEM to R.string.time_format_system, TimeFormat.H12 to R.string.time_format_12, TimeFormat.H24 to R.string.time_format_24)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    formats.forEachIndexed { i, (format, label) ->
                        SegmentedButton(
                            selected = settings.timeFormat == format,
                            onClick = { onUpdate { it.copy(timeFormat = format) } },
                            shape = SegmentedButtonDefaults.itemShape(i, formats.size),
                            label = { Text(stringResource(label), maxLines = 1) },
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
                Column(Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = settings.themeMode == mode, role = Role.RadioButton) {
                                    onUpdate { it.copy(themeMode = mode) }
                                }
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.themeMode == mode, onClick = null)
                            Spacer(Modifier.width(Spacing.md))
                            Text(themeModeLabel(mode), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = stringResource(R.string.settings_dynamic_color_desc),
                        trailing = {
                            Switch(
                                checked = settings.dynamicColor,
                                onCheckedChange = { on -> onUpdate { it.copy(dynamicColor = on) } },
                            )
                        },
                    )
                }
            }

            SectionHeader(stringResource(R.string.settings_reliability))
            RoundlyCard {
                SettingsRow(
                    title = stringResource(R.string.permissions_title),
                    subtitle = stringResource(R.string.settings_permissions_desc),
                    leading = Icons.Outlined.Shield,
                    onClick = onPermissions,
                    trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                )
                SettingsRow(
                    title = stringResource(R.string.presets_title),
                    subtitle = stringResource(R.string.settings_presets_desc),
                    leading = Icons.Outlined.Tune,
                    onClick = onPresets,
                    trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                )
            }

            SectionHeader(stringResource(R.string.settings_data))
            RoundlyCard {
                SettingsRow(
                    title = stringResource(R.string.settings_export),
                    subtitle = stringResource(R.string.settings_export_desc),
                    leading = Icons.Outlined.FileUpload,
                    onClick = onExport,
                )
                SettingsRow(
                    title = stringResource(R.string.settings_import),
                    subtitle = stringResource(R.string.settings_import_desc),
                    leading = Icons.Outlined.FileDownload,
                    onClick = onImport,
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            Text(
                stringResource(R.string.settings_privacy_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.xl),
            )
        }
    }

    if (modeDialog) {
        AlertDialog(
            onDismissRequest = { modeDialog = false },
            title = { Text(stringResource(R.string.settings_default_mode)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    AlertMode.entries.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = settings.defaultAlertMode == mode, role = Role.RadioButton) {
                                    onUpdate { it.copy(defaultAlertMode = mode) }
                                    modeDialog = false
                                }
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.defaultAlertMode == mode, onClick = null)
                            Spacer(Modifier.width(Spacing.md))
                            Text(alertModeLabel(mode))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { modeDialog = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

@ThemePreviews
@Composable
private fun SettingsPreview() {
    RoundlyTheme {
        SettingsScreen(
            settings = AppSettings(),
            snackbar = remember { SnackbarHostState() },
            onBack = {}, onUpdate = {}, onPermissions = {}, onPresets = {}, onExport = {}, onImport = {},
        )
    }
}
