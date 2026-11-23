package app.roundly.data

import app.roundly.data.repository.AlarmRepository
import app.roundly.data.repository.AlertLogRepository
import app.roundly.data.repository.PresetRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cheap maintenance run on process start (and, from Phase 3, after each alert):
 * seeds built-in presets, clears recent timers older than 7 days and alert history older
 * than 30 days. No background job is needed for this.
 */
@Singleton
class Housekeeping @Inject constructor(
    private val alarms: AlarmRepository,
    private val presets: PresetRepository,
    private val alertLog: AlertLogRepository,
) {
    suspend fun run() {
        presets.ensureBuiltIns()
        alarms.purgeExpiredRecentTimers()
        alertLog.purgeExpired()
    }
}
