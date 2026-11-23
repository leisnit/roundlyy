package app.roundly.wear.alert

import android.content.Context
import android.os.PowerManager
import androidx.core.content.getSystemService
import app.roundly.core.model.AlarmLimits
import app.roundly.core.sync.WatchAction
import app.roundly.core.sync.WatchResponse
import app.roundly.core.time.Clock
import app.roundly.wear.data.WatchRepository
import app.roundly.wear.di.WearAppScope
import app.roundly.wear.notifications.WearNotifications
import app.roundly.wear.scheduling.WatchScheduler
import app.roundly.wear.sync.PhoneLink
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shows watch alerts (from the phone or the local fallback), drives haptics for the head of the
 * queue, and sends every response to the phone. Deduplication: whichever device is answered first
 * wins; the phone cancels this one via a message, or reconciles a queued response on reconnect.
 */
@Singleton
class WearAlertController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: WatchAlertQueue,
    private val scheduler: WatchScheduler,
    private val repository: WatchRepository,
    private val phoneLink: PhoneLink,
    private val notifications: WearNotifications,
    private val clock: Clock,
    @WearAppScope private val scope: CoroutineScope,
) {
    private val haptics = WearHaptics(context, scope)
    private var timeoutJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    init {
        scope.launch {
            queue.alerts.map { it.firstOrNull() }.distinctUntilChanged().collect { head -> onHead(head) }
        }
    }

    suspend fun present(alert: WatchAlert) {
        if (!queue.add(alert)) return
        val state = repository.current()
        notifications.postAlert(alert, state.timeFormat, state.ringTimeoutMinutes)
    }

    /** Done or Snooze on the watch (alert screen or notification action). */
    suspend fun respond(occurrenceId: String, action: WatchAction) {
        val alert = queue.remove(occurrenceId)
        notifications.cancel(occurrenceId)
        alert ?: return
        phoneLink.sendResponse(WatchResponse(occurrenceId, action, clock.now()))
        if (alert.origin == AlertOrigin.LOCAL) scheduler.applyLocalResponse(alert.alarmId, alert.scheduledAt, action)
    }

    /** The phone answered (or timed out) first: stop here without responding again. */
    fun cancelFromPhone(occurrenceId: String) {
        queue.remove(occurrenceId)
        notifications.cancel(occurrenceId)
    }

    private suspend fun timeout(alert: WatchAlert) {
        if (queue.remove(alert.occurrenceId) == null) return
        notifications.cancel(alert.occurrenceId)
        // Phone alerts time out on the phone, which then cancels this one.
        if (alert.origin == AlertOrigin.LOCAL) {
            phoneLink.sendResponse(WatchResponse(alert.occurrenceId, WatchAction.MISSED, clock.now()))
            scheduler.applyLocalResponse(alert.alarmId, alert.scheduledAt, WatchAction.MISSED)
        }
    }

    private suspend fun onHead(head: WatchAlert?) {
        timeoutJob?.cancel()
        if (head == null) {
            haptics.stop()
            releaseWakeLock()
            return
        }
        haptics.start(head.alertMode)
        val timeoutMs = repository.current().ringTimeoutMinutes * AlarmLimits.MINUTE_MS
        acquireWakeLock(timeoutMs + 15_000)
        timeoutJob = scope.launch {
            // A phone alert gets a little longer, so the phone's own timeout normally cancels it first.
            delay(if (head.origin == AlertOrigin.PHONE) timeoutMs + 30_000 else timeoutMs)
            timeout(head)
        }
    }

    private fun acquireWakeLock(timeoutMs: Long) {
        releaseWakeLock()
        wakeLock = context.getSystemService<PowerManager>()
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "roundly:watch-alert")
            ?.apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
