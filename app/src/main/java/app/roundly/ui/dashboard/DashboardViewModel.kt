package app.roundly.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.roundly.alert.AlertController
import app.roundly.core.model.Alarm
import app.roundly.core.model.AlarmType
import app.roundly.core.model.Preset
import app.roundly.core.schedule.ScheduleCalculator
import app.roundly.core.schedule.StepProgress
import app.roundly.data.repository.AlarmRepository
import app.roundly.data.repository.PresetRepository
import app.roundly.data.repository.SettingsRepository
import app.roundly.permissions.PermissionStatus
import app.roundly.scheduling.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PermissionIssue { NOTIFICATIONS, EXACT_ALARMS, FULL_SCREEN }

data class AlarmItem(val alarm: Alarm, val stepProgress: StepProgress?)

data class DashboardUiState(
    val loading: Boolean = true,
    val alarms: List<AlarmItem> = emptyList(),
    val recentTimers: List<Alarm> = emptyList(),
    val presets: List<Preset> = emptyList(),
    val pausedAll: Boolean = false,
    val issues: Set<PermissionIssue> = emptySet(),
) {
    val isEmpty: Boolean get() = !loading && alarms.isEmpty() && recentTimers.isEmpty()

    /**
     * The hero shows the soonest upcoming alert. Irregular alarms never reveal their next time,
     * so they only take the hero when no other alarm is running (it then reads "within window").
     */
    val hero: AlarmItem?
        get() {
            val upcoming = alarms.filter { it.alarm.isActive && it.alarm.nextTriggerAt != null }
            return upcoming.filter { it.alarm.type != AlarmType.IRREGULAR || it.alarm.snoozeUntil != null }
                .minByOrNull { it.alarm.nextTriggerAt!! }
                ?: upcoming.firstOrNull()
        }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    alarmRepository: AlarmRepository,
    presetRepository: PresetRepository,
    settingsRepository: SettingsRepository,
    private val scheduler: AlarmScheduler,
    private val alertController: AlertController,
    private val calculator: ScheduleCalculator,
    private val permissions: PermissionStatus,
) : ViewModel() {

    private val issues = MutableStateFlow(currentIssues())

    val state: StateFlow<DashboardUiState> = combine(
        alarmRepository.observeAlarms(),
        presetRepository.observePresets(),
        settingsRepository.state,
        issues,
    ) { alarms, presets, appState, issues ->
        DashboardUiState(
            loading = false,
            alarms = alarms.filterNot { it.isRecentTimer }.map { AlarmItem(it, calculator.stepProgress(it)) },
            recentTimers = alarms.filter { it.isRecentTimer }.sortedByDescending { it.completedAt },
            presets = presets.filterNot { it.hidden },
            pausedAll = appState.pausedAll,
            issues = issues,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    /** Called on every resume: the user may have changed a permission in system settings. */
    fun refreshPermissions() {
        issues.value = currentIssues()
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            scheduler.setEnabled(id, enabled)
            if (!enabled) alertController.dismissAlarm(id)
        }
    }

    /** Deletes and returns the alarm so the screen can offer Undo. */
    suspend fun delete(id: Long): Alarm? = viewModelScope.async {
        alertController.dismissAlarm(id)
        scheduler.delete(id)
    }.await()

    fun undoDelete(alarm: Alarm) {
        viewModelScope.launch { scheduler.restore(alarm) }
    }

    fun duplicate(id: Long, suffix: String) {
        viewModelScope.launch { scheduler.duplicate(id, suffix) }
    }

    fun togglePauseAll() {
        viewModelScope.launch {
            if (state.value.pausedAll) scheduler.resumeAll() else scheduler.pauseAll()
        }
    }

    fun restartTimer(id: Long) {
        viewModelScope.launch { scheduler.restartTimer(id) }
    }

    private fun currentIssues(): Set<PermissionIssue> = buildSet {
        if (!permissions.canPostNotifications()) add(PermissionIssue.NOTIFICATIONS)
        if (!permissions.canScheduleExactAlarms()) add(PermissionIssue.EXACT_ALARMS)
        if (!permissions.canUseFullScreenIntent()) add(PermissionIssue.FULL_SCREEN)
    }
}
