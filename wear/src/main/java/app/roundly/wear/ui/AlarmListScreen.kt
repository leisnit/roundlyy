package app.roundly.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import app.roundly.core.model.Alarm
import app.roundly.core.model.AlarmType
import app.roundly.core.schedule.NextAlert
import app.roundly.core.sync.WatchCommand
import app.roundly.core.sync.WatchCommandType
import app.roundly.core.time.Clock
import app.roundly.wear.R
import app.roundly.wear.data.WatchRepository
import app.roundly.wear.data.WatchState
import app.roundly.wear.notifications.WearNotifications
import app.roundly.wear.scheduling.WatchScheduler
import app.roundly.wear.sync.PhoneLink
import app.roundly.wear.ui.theme.RoundlyWearTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class WearMainViewModel @Inject constructor(
    repository: WatchRepository,
    private val scheduler: WatchScheduler,
    private val phoneLink: PhoneLink,
    private val notifications: WearNotifications,
    private val clock: Clock,
) : ViewModel() {

    val state: StateFlow<WatchState?> = repository.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun formatTime(epochMs: Long): String = notifications.time(epochMs, state.value?.timeFormat ?: app.roundly.core.model.TimeFormat.SYSTEM)

    /** Applied locally at once, and sent to the phone (queued by the Data Layer if it is away). */
    fun setEnabled(alarmId: Long, enabled: Boolean) {
        viewModelScope.launch {
            scheduler.setEnabledLocally(alarmId, enabled)
            phoneLink.sendCommand(
                WatchCommand(UUID.randomUUID().toString(), WatchCommandType.SET_ENABLED, alarmId, enabled, clock.now()),
            )
        }
    }

    fun togglePauseAll() {
        val paused = state.value?.pausedAll ?: false
        viewModelScope.launch {
            scheduler.setPausedAllLocally(!paused)
            phoneLink.sendCommand(
                WatchCommand(
                    id = UUID.randomUUID().toString(),
                    type = if (paused) WatchCommandType.RESUME_ALL else WatchCommandType.PAUSE_ALL,
                    issuedAt = clock.now(),
                ),
            )
        }
    }
}

/** Watch list: alarms with on/off toggles and a one-tap Pause all. Editing is phone-only. */
@Composable
fun AlarmListScreen(
    state: WatchState?,
    formatTime: (Long) -> String,
    onToggle: (Long, Boolean) -> Unit,
    onTogglePauseAll: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(R.string.app_name)) } }
            val alarms = state?.alarms.orEmpty()
            if (state == null || state.syncedAt == null || alarms.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_alarms),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                item {
                    Button(
                        onClick = onTogglePauseAll,
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (state.pausedAll) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                        label = {
                            Text(stringResource(if (state.pausedAll) R.string.action_resume_all else R.string.action_pause_all))
                        },
                    )
                }
                items(alarms, key = { it.id }) { alarm ->
                    SwitchButton(
                        checked = alarm.enabled,
                        onCheckedChange = { onToggle(alarm.id, it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                alarm.label.ifBlank { stringResource(R.string.alert_default_label) },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        secondaryLabel = { Text(nextText(alarm, state.pausedAll, formatTime), maxLines = 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun nextText(alarm: Alarm, pausedAll: Boolean, formatTime: (Long) -> String): String {
    val next = alarm.nextTriggerAt
    return when {
        !alarm.enabled -> stringResource(R.string.alarm_state_off)
        pausedAll || alarm.pausedByMaster -> stringResource(R.string.alarm_state_paused)
        !NextAlert.revealsTime(alarm) -> stringResource(R.string.within_window)
        next == null -> stringResource(R.string.alarm_state_finished)
        else -> stringResource(R.string.next_at, formatTime(next))
    }
}

@WearPreviewDevices
@Composable
private fun AlarmListPreview() {
    val now = System.currentTimeMillis()
    RoundlyWearTheme {
        AlarmListScreen(
            state = WatchState(
                alarms = listOf(
                    Alarm(id = 1, label = "Drink water", type = AlarmType.FIXED, intervalMs = 1_800_000, anchorAt = now, nextTriggerAt = now + 600_000, createdAt = now),
                    Alarm(id = 2, label = "Posture", type = AlarmType.IRREGULAR, windowMs = 3_600_000, alertsPerWindow = 4, anchorAt = now, nextTriggerAt = now + 60_000, createdAt = now),
                ),
                syncedAt = now,
            ),
            formatTime = { "12:40" },
            onToggle = { _, _ -> },
            onTogglePauseAll = {},
        )
    }
}
