package app.roundly.ringing

import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.roundly.alert.ActiveAlert
import app.roundly.alert.AlertController
import app.roundly.alert.AlertQueue
import app.roundly.core.model.AlarmLimits
import app.roundly.core.model.AppSettings
import app.roundly.data.repository.SettingsRepository
import app.roundly.notifications.AlertNotifications
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Short-lived foreground service (type systemExempted: an alarm app holding the exact-alarm
 * permission, delivering an alarm) that exists only while an alert rings.
 * It follows the head of [AlertQueue]: rings it, enforces the ring timeout, moves on to the next
 * queued alert, and stops itself when the queue is empty. No service runs between alerts.
 */
@AndroidEntryPoint
class RingingService : LifecycleService() {

    @Inject lateinit var queue: AlertQueue
    @Inject lateinit var controller: AlertController
    @Inject lateinit var notifications: AlertNotifications
    @Inject lateinit var settingsRepository: SettingsRepository

    private lateinit var player: AlertPlayer
    private var wakeLock: PowerManager.WakeLock? = null
    private var timeoutJob: Job? = null
    private var ringingId: String? = null
    private var observing = false
    private var settings = AppSettings()

    override fun onCreate() {
        super.onCreate()
        player = AlertPlayer(this, lifecycleScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Enter the foreground immediately on every start, as startForegroundService requires.
        val head = queue.current()
        goForeground(head?.let { notifications.buildAlert(it, settings) } ?: notifications.buildIdle())
        if (head == null) {
            shutDown()
            return START_NOT_STICKY
        }
        if (!observing) {
            observing = true
            lifecycleScope.launch {
                combine(queue.head, settingsRepository.settings) { alert, s -> alert to s }
                    .collect { (alert, s) ->
                        settings = s
                        onHead(alert)
                    }
            }
        }
        return START_NOT_STICKY
    }

    private fun onHead(alert: ActiveAlert?) {
        if (alert == null) {
            shutDown()
            return
        }
        if (alert.occurrenceId == ringingId) return
        ringingId = alert.occurrenceId

        goForeground(notifications.buildAlert(alert, settings))
        if (alert.phoneSilent) player.stop() else player.start(alert.alertMode, alert.ringtoneUri)

        val timeoutMs = settings.ringTimeoutMinutes * AlarmLimits.MINUTE_MS
        acquireWakeLock(timeoutMs + WAKE_LOCK_GRACE_MS)
        timeoutJob?.cancel()
        timeoutJob = lifecycleScope.launch {
            delay(timeoutMs)
            controller.timeout(alert.occurrenceId)
        }
    }

    private fun goForeground(notification: Notification) {
        // The systemExempted type exists from Android 14 (when types became mandatory). Earlier
        // versions do not enforce service types, so no type is passed there.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        } else {
            0
        }
        ServiceCompat.startForeground(this, AlertNotifications.RINGING_NOTIFICATION_ID, notification, type)
    }

    private fun shutDown() {
        timeoutJob?.cancel()
        ringingId = null
        player.stop()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock(timeoutMs: Long) {
        releaseWakeLock()
        wakeLock = getSystemService<PowerManager>()
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "roundly:ringing")
            ?.apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        timeoutJob?.cancel()
        player.stop()
        releaseWakeLock()
        super.onDestroy()
    }

    private companion object {
        const val WAKE_LOCK_GRACE_MS = 15_000L
    }
}
