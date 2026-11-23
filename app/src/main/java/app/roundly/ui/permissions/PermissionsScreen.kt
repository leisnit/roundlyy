@file:OptIn(ExperimentalMaterial3Api::class)

package app.roundly.ui.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.roundly.R
import app.roundly.permissions.PermissionStatus
import app.roundly.ui.components.RoundlyCard
import app.roundly.ui.preview.ThemePreviews
import app.roundly.ui.theme.RoundlyTheme
import app.roundly.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionsUiState(
    val notifications: Boolean = true,
    val exactAlarms: Boolean = true,
    val fullScreen: Boolean = true,
    val battery: Boolean = false,
    val notificationRuntimePermission: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
    val exactAlarmsApplicable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    val fullScreenApplicable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
)

@HiltViewModel
class PermissionsViewModel @Inject constructor(private val status: PermissionStatus) : ViewModel() {
    private val _state = MutableStateFlow(read())
    val state: StateFlow<PermissionsUiState> = _state.asStateFlow()

    fun refresh() {
        _state.value = read()
    }

    private fun read() = PermissionsUiState(
        notifications = status.canPostNotifications(),
        exactAlarms = status.canScheduleExactAlarms(),
        fullScreen = status.canUseFullScreenIntent(),
        battery = status.isIgnoringBatteryOptimizations(),
    )
}

/**
 * System screens for each permission. Falls back to the app's details page if one is missing.
 * Each intent is only offered on the API level that has it (see PermissionsUiState).
 */
@SuppressLint("InlinedApi")
object PermissionIntents {
    fun notificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun exactAlarms(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())

    fun fullScreen(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri())

    /** The list screen only. Roundly never requests REQUEST_IGNORE_BATTERY_OPTIMIZATIONS. */
    fun batterySettings(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

@SuppressLint("InlinedApi") // POST_NOTIFICATIONS is only requested on API 33+.
@Composable
fun PermissionsRoute(onBack: () -> Unit, viewModel: PermissionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    PermissionsScreen(
        state = state,
        onBack = onBack,
        onNotifications = {
            if (state.notificationRuntimePermission && !askedNotifications) {
                askedNotifications = true
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                PermissionIntents.open(context, PermissionIntents.notificationSettings(context))
            }
        },
        onExactAlarms = { PermissionIntents.open(context, PermissionIntents.exactAlarms(context)) },
        onFullScreen = { PermissionIntents.open(context, PermissionIntents.fullScreen(context)) },
        onBattery = { PermissionIntents.open(context, PermissionIntents.batterySettings()) },
    )
}

@Composable
fun PermissionsScreen(
    state: PermissionsUiState,
    onBack: () -> Unit,
    onNotifications: () -> Unit,
    onExactAlarms: () -> Unit,
    onFullScreen: () -> Unit,
    onBattery: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.permissions_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                stringResource(R.string.permissions_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            StatusCard(
                ok = state.notifications,
                title = stringResource(R.string.perm_notifications_title),
                text = stringResource(if (state.notifications) R.string.perm_notifications_ok else R.string.perm_notifications_missing),
                action = stringResource(if (state.notifications) R.string.action_open_settings else R.string.action_allow),
                onAction = onNotifications,
            )
            if (state.exactAlarmsApplicable && !state.exactAlarms) {
                StatusCard(
                    ok = false,
                    title = stringResource(R.string.perm_exact_title),
                    text = stringResource(R.string.perm_exact_missing),
                    action = stringResource(R.string.action_allow),
                    onAction = onExactAlarms,
                )
            }
            if (state.fullScreenApplicable) {
                StatusCard(
                    ok = state.fullScreen,
                    title = stringResource(R.string.perm_full_screen_title),
                    text = stringResource(if (state.fullScreen) R.string.perm_full_screen_ok else R.string.perm_full_screen_missing),
                    action = stringResource(if (state.fullScreen) R.string.action_open_settings else R.string.action_allow),
                    onAction = onFullScreen,
                )
            }
            StatusCard(
                ok = state.battery,
                optional = true,
                title = stringResource(R.string.perm_battery_title),
                text = stringResource(if (state.battery) R.string.perm_battery_ok else R.string.perm_battery_optional),
                action = stringResource(R.string.action_open_settings),
                onAction = onBattery,
            )
        }
    }
}

@Composable
private fun StatusCard(
    ok: Boolean,
    title: String,
    text: String,
    action: String,
    onAction: () -> Unit,
    optional: Boolean = false,
) {
    val status = RoundlyTheme.extras.status
    val stateText = stringResource(
        when {
            ok -> R.string.perm_state_ok
            optional -> R.string.perm_state_optional
            else -> R.string.perm_state_attention
        },
    )
    RoundlyCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = when {
                    ok -> status.ok
                    optional -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> status.warning
                },
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { stateDescription = stateText },
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.md))
                FilledTonalButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun PermissionsPreview() {
    RoundlyTheme {
        PermissionsScreen(
            state = PermissionsUiState(notifications = true, exactAlarms = false, fullScreen = false, battery = false),
            onBack = {}, onNotifications = {}, onExactAlarms = {}, onFullScreen = {}, onBattery = {},
        )
    }
}
