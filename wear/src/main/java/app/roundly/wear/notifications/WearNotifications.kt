package app.roundly.wear.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.roundly.core.model.AlarmLimits
import app.roundly.core.model.TimeFormat
import app.roundly.core.sync.WatchAction
import app.roundly.wear.R
import app.roundly.wear.alert.WatchAlert
import app.roundly.wear.alert.WearAlertActivity
import app.roundly.wear.scheduling.WearActionReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearNotifications @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun label(alert: WatchAlert): String = alert.label.ifBlank { context.getString(R.string.alert_default_label) }

    fun time(epochMs: Long, format: TimeFormat): String {
        val is24 = when (format) {
            TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
            TimeFormat.H12 -> false
            TimeFormat.H24 -> true
        }
        val locale = Locale.getDefault()
        val pattern = DateFormat.getBestDateTimePattern(locale, if (is24) "Hm" else "hm")
        return DateTimeFormatter.ofPattern(pattern, locale).format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
    }

    /** Full-screen alert notification. Local only: it must never bridge back to the phone. */
    fun postAlert(alert: WatchAlert, timeFormat: TimeFormat, ringTimeoutMinutes: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }
        val screen = PendingIntent.getActivity(
            context,
            0,
            Intent(context, WearAlertActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, WearNotificationChannels.ALARMS)
            .setSmallIcon(R.drawable.ic_stat_roundly)
            .setContentTitle(label(alert))
            .setContentText(alert.detail ?: time(alert.scheduledAt, timeFormat))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setSilent(true)
            .setLocalOnly(true)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .setTimeoutAfter((ringTimeoutMinutes + 1) * AlarmLimits.MINUTE_MS)
            .addAction(0, context.getString(R.string.action_done), action(alert, WatchAction.DONE))
            .addAction(0, context.getString(R.string.action_snooze), action(alert, WatchAction.SNOOZE))
            .build()
        manager.notify(id(alert.occurrenceId), notification)
    }

    fun cancel(occurrenceId: String) = manager.cancel(id(occurrenceId))

    private fun action(alert: WatchAlert, action: WatchAction): PendingIntent = PendingIntent.getBroadcast(
        context,
        id(alert.occurrenceId) + action.ordinal,
        Intent(context, WearActionReceiver::class.java)
            .setAction(WearActionReceiver.ACTION_RESPOND)
            .setData("roundly-watch://alert/${Uri.encode(alert.occurrenceId)}/${action.name}".toUri())
            .putExtra(WearActionReceiver.EXTRA_OCCURRENCE_ID, alert.occurrenceId)
            .putExtra(WearActionReceiver.EXTRA_ACTION, action.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun id(occurrenceId: String): Int = ("watch-alert:$occurrenceId").hashCode()
}
