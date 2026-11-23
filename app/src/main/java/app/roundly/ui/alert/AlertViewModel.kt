package app.roundly.ui.alert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.roundly.alert.AlertAction
import app.roundly.alert.AlertController
import app.roundly.alert.AlertQueue
import app.roundly.core.model.AppSettings
import app.roundly.core.model.RespondedOn
import app.roundly.data.repository.SettingsRepository
import app.roundly.di.ApplicationScope
import app.roundly.notifications.AlertNotifications
import app.roundly.util.TimeFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertUiState(
    val occurrenceId: String? = null,
    val label: String = "",
    val time: String = "",
    val detail: String = "",
    val waiting: Int = 0,
    val snoozeMinutes: Int = AppSettings.DEFAULT_SNOOZE_MINUTES,
    val settings: AppSettings = AppSettings(),
    /** True once the queue has been read; an empty queue then closes the screen. */
    val ready: Boolean = false,
)

@HiltViewModel
class AlertViewModel @Inject constructor(
    queue: AlertQueue,
    settingsRepository: SettingsRepository,
    private val controller: AlertController,
    private val notifications: AlertNotifications,
    private val timeFormatter: TimeFormatter,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    val state: StateFlow<AlertUiState> = combine(queue.alerts, settingsRepository.settings) { alerts, settings ->
        val head = alerts.firstOrNull()
        if (head == null) {
            AlertUiState(settings = settings, ready = true)
        } else {
            AlertUiState(
                occurrenceId = head.occurrenceId,
                label = notifications.displayLabel(head),
                time = timeFormatter.time(head.scheduledAt, settings.timeFormat),
                detail = notifications.detailLine(head, settings),
                waiting = alerts.size - 1,
                snoozeMinutes = settings.snoozeMinutes,
                settings = settings,
                ready = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AlertUiState())

    fun done() = respond(AlertAction.DONE)

    fun snooze() = respond(AlertAction.SNOOZE)

    private fun respond(action: AlertAction) {
        val id = state.value.occurrenceId ?: return
        // Application scope: the response must complete even as this screen closes.
        appScope.launch { controller.respond(id, action, RespondedOn.PHONE) }
    }
}
