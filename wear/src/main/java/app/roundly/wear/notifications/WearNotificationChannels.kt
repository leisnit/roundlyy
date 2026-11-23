package app.roundly.wear.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import app.roundly.wear.R

/** Watch notification channels. Haptics are driven by the alert screen, not the channel. */
object WearNotificationChannels {
    const val ALARMS = "alarms"

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
        }
        manager.createNotificationChannel(alarms)
    }
}
