package app.roundly.alert

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.roundly.core.model.Alarm
import app.roundly.core.model.OccurrenceId
import app.roundly.core.model.RespondedOn
import app.roundly.core.time.Clock
import app.roundly.data.repository.SettingsRepository
import app.roundly.notifications.AlertNotifications
import app.roundly.ringing.RingingService
import app.roundly.scheduling.AlarmScheduler
import app.roundly.watch.WatchBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Presents alerts and routes responses. Ringing itself happens in [RingingService], which
 * follows the head of [AlertQueue]; this class only changes the queue and the schedule.
 */
@Singleton
class AlertController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: AlertQueue,
    private val scheduler: AlarmScheduler,
    private val notifications: AlertNotifications,
    private val settings: SettingsRepository,
    private val watch: WatchBridge,
    private val clock: Clock,
) {

    /** Queues [alert] and makes sure the ringing service is running. */
    suspend fun present(alert: ActiveAlert) {
        val watchHandling = !alert.isTest && watch.isWatchReachable() && watch.sendAlert(alert)
        val queued = alert.copy(watchHandling = watchHandling)
        if (!queue.add(queued)) return
        startRinging(queued)
    }

    /** Editor "Test alert": rings with the draft's settings; never logged or scheduled. */
    suspend fun test(alarm: Alarm) {
        val now = clock.now()
        present(
            ActiveAlert(
                alarmId = TEST_ALARM_ID,
                occurrenceId = OccurrenceId.of(TEST_ALARM_ID, now).value,
                scheduledAt = now,
                label = alarm.label,
                type = alarm.type,
                alertMode = alarm.alertMode,
                ringtoneUri = alarm.ringtoneUri,
                watchOnly = false,
                stepProgress = null,
                isSnoozeRepeat = false,
                firedAt = now,
                isTest = true,
            ),
        )
    }

    /**
     * Done or Snooze, from the alert screen, a notification action, a hardware key or the watch.
     * Works even when this device is no longer ringing (for example, a watch response that was
     * queued while disconnected): the schedule's "first response wins" rule decides.
     */
    suspend fun respond(
        occurrenceId: String,
        action: AlertAction,
        on: RespondedOn,
        respondedAt: Long = clock.now(),
    ) {
        val alert = queue.remove(occurrenceId)
        notifications.cancelAlert(occurrenceId)
        if (alert?.isTest == true) return

        val (alarmId, scheduledAt) = alert?.let { it.alarmId to it.scheduledAt }
            ?: OccurrenceId.parse(occurrenceId)
            ?: return
        val applied = scheduler.respond(alarmId, scheduledAt, alert?.label.orEmpty(), action, on, respondedAt)

        when (on) {
            RespondedOn.PHONE -> if (alert?.watchHandling == true) watch.sendCancel(occurrenceId)
            // Answered on the watch: if the phone had already rung out, drop its Missed notice.
            RespondedOn.WATCH -> if (applied) notifications.cancelMissed(occurrenceId)
        }
    }

    /** The other device answered first: stop ringing here without recording anything twice. */
    fun cancelLocally(occurrenceId: String) {
        queue.remove(occurrenceId)
        notifications.cancelAlert(occurrenceId)
    }

    /** Ring timeout reached: stop, log Missed, keep the schedule and leave a Missed notice. */
    suspend fun timeout(occurrenceId: String) {
        val alert = queue.remove(occurrenceId) ?: return
        notifications.cancelAlert(occurrenceId)
        if (alert.isTest) return
        scheduler.onMissed(alert)
        notifications.postMissed(alert, settings.current())
        if (alert.watchHandling) watch.sendCancel(occurrenceId)
    }

    /** An alarm was deleted or switched off: stop its queued alerts. */
    fun dismissAlarm(alarmId: Long) {
        queue.removeAlarm(alarmId).forEach { notifications.cancelAlert(it.occurrenceId) }
    }

    private suspend fun startRinging(alert: ActiveAlert) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, RingingService::class.java))
        } catch (_: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) extends IllegalStateException.
            // Happens only when the trigger was not an exact alarm (permission revoked): the alert
            // is still shown, without sound, with its Done and Snooze actions.
            notifications.postAlertWithoutService(alert, settings.current())
        }
    }

    companion object {
        /** Reserved id for test alerts; Room ids start at 1. */
        const val TEST_ALARM_ID = -1L
    }
}
