package app.roundly.alert

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alerts due at the same time are queued and shown one at a time, oldest first.
 * The head is what rings and what AlertActivity shows.
 */
@Singleton
class AlertQueue @Inject constructor() {

    private val _alerts = MutableStateFlow<List<ActiveAlert>>(emptyList())
    val alerts: StateFlow<List<ActiveAlert>> = _alerts.asStateFlow()

    val head: Flow<ActiveAlert?> = _alerts.map { it.firstOrNull() }.distinctUntilChanged()

    fun current(): ActiveAlert? = _alerts.value.firstOrNull()

    fun get(occurrenceId: String): ActiveAlert? = _alerts.value.firstOrNull { it.occurrenceId == occurrenceId }

    /** Adds [alert] unless the same occurrence is already queued. Returns true if added. */
    fun add(alert: ActiveAlert): Boolean {
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

    fun remove(occurrenceId: String): ActiveAlert? {
        var removed: ActiveAlert? = null
        _alerts.update { list ->
            removed = list.firstOrNull { it.occurrenceId == occurrenceId }
            if (removed == null) list else list.filterNot { it.occurrenceId == occurrenceId }
        }
        return removed
    }

    /** Drops queued alerts of a deleted or disabled alarm. */
    fun removeAlarm(alarmId: Long): List<ActiveAlert> {
        var removed: List<ActiveAlert> = emptyList()
        _alerts.update { list ->
            removed = list.filter { it.alarmId == alarmId && !it.isTest }
            list - removed.toSet()
        }
        return removed
    }
}
