package app.roundly.wear.alert

import app.roundly.core.model.AlertMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

enum class AlertOrigin {
    /** Sent by the connected phone; the phone owns its schedule and timeout. */
    PHONE,

    /** Raised by the watch's own fallback schedule while the phone is out of range. */
    LOCAL,
}

data class WatchAlert(
    val occurrenceId: String,
    val alarmId: Long,
    val scheduledAt: Long,
    val label: String,
    val detail: String?,
    val alertMode: AlertMode,
    val origin: AlertOrigin,
    val isSnoozeRepeat: Boolean,
)

/** Alerts shown one at a time, deduplicated by occurrence id (phone and local copies of one alert). */
@Singleton
class WatchAlertQueue @Inject constructor() {
    private val _alerts = MutableStateFlow<List<WatchAlert>>(emptyList())
    val alerts: StateFlow<List<WatchAlert>> = _alerts.asStateFlow()

    fun add(alert: WatchAlert): Boolean {
        var added = false
        _alerts.update { list ->
            if (list.any { it.occurrenceId == alert.occurrenceId }) {
                list
            } else {
                added = true
                list + alert
            }
        }
        return added
    }

    fun remove(occurrenceId: String): WatchAlert? {
        var removed: WatchAlert? = null
        _alerts.update { list ->
            removed = list.firstOrNull { it.occurrenceId == occurrenceId }
            list.filterNot { it.occurrenceId == occurrenceId }
        }
        return removed
    }
}
