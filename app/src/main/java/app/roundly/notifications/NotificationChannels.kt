package app.roundly.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import app.roundly.R

/**
 * Notification channels. Creating a channel that already exists is a no-op, so this is safe
 * to call on every process start.
 *
 * The alarm channel has no sound or vibration of its own. RingingService plays the per-alarm
 * sound (USAGE_ALARM) and vibration pattern, so the alert mode stays under the app's control.
 */
object NotificationChannels {
    const val ALARMS = "alarms"
    const val MISSED = "missed"
    const val STATUS = "status"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return

        val alarms = NotificationChannel(
            ALARMS,
            context.getString(R.string.channel_alarms_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alarms_description)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val missed = NotificationChannel(
            MISSED,
            context.getString(R.string.channel_missed_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_missed_description)
            setSound(null, null)
            enableVibration(false)
        }

        val status = NotificationChannel(
            STATUS,
            context.getString(R.string.channel_status_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_status_description)
            setShowBadge(false)
        }

        manager.createNotificationChannels(listOf(alarms, missed, status))
    }
}
