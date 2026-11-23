package app.roundly.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import app.roundly.MainActivity
import app.roundly.R
import app.roundly.alert.ActiveAlert
import app.roundly.alert.AlertAction
import app.roundly.alert.AlertActivity
import app.roundly.core.model.Alarm
import app.roundly.core.model.AlertMode
import app.roundly.core.model.AppSettings
import app.roundly.core.schedule.FinishReason
import app.roundly.permissions.PermissionStatus
import app.roundly.scheduling.AlertActionReceiver
import app.roundly.util.TimeFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Builds and posts every notification Roundly shows. All notifications stay on this device. */
@Singleton
class AlertNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissions: PermissionStatus,
    private val timeFormatter: TimeFormatter,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun displayLabel(alert: ActiveAlert): String = when {
        alert.isTest -> alert.label.ifBlank { context.getString(R.string.alert_test_label) }
        else -> alert.label.ifBlank { context.getString(R.string.alert_default_label) }
    }

    /** Secondary line: step progress, or the due time. */
    fun detailLine(alert: ActiveAlert, settings: AppSettings): String {
        val progress = alert.stepProgress
        val time = timeFormatter.time(alert.scheduledAt, settings.timeFormat)
        return when {
            alert.phoneSilent -> context.getString(R.string.alert_on_watch)
            alert.isSnoozeRepeat -> context.getString(R.string.alert_snoozed_from, time)
            progress != null && progress.continuing -> context.getString(
                R.string.alert_step_progress_continuing,
                progress.stepNumber,
                progress.stepCount,
                progress.alertNumber,
            )
            progress != null -> context.getString(
                R.string.alert_step_progress,
                progress.stepNumber,
                progress.stepCount,
                progress.alertNumber,
                progress.alertCount,
            )
            else -> context.getString(R.string.alert_due_at, time)
        }
    }

    /**
     * The ringing notification: alarm category on the dedicated channel, Done and Snooze actions,
     * and a full-screen intent to AlertActivity when allowed. On Android 14+ without full-screen
     * permission (or in silent-banner mode) it falls back to a heads-up notification with actions.
     */
    fun buildAlert(alert: ActiveAlert, settings: AppSettings): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_ALERT_ACTIVITY,
            Intent(context, AlertActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, NotificationChannels.ALARMS)
            .setSmallIcon(R.drawable.ic_stat_roundly)
            .setContentTitle(displayLabel(alert))
            .setContentText(detailLine(alert, settings))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setShowWhen(true)
            .setWhen(alert.scheduledAt)
            .setContentIntent(contentIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // The watch app shows its own alert; do not also bridge this notification to it.
            .setLocalOnly(alert.watchHandling)
            .addAction(0, context.getString(R.string.action_done), actionIntent(alert, AlertAction.DONE))
            .addAction(
                0,
                context.getString(R.string.action_snooze_minutes, settings.snoozeMinutes),
                actionIntent(alert, AlertAction.SNOOZE),
            )

        if (alert.alertMode != AlertMode.SILENT_BANNER && permissions.canUseFullScreenIntent()) {
            builder.setFullScreenIntent(contentIntent, true)
        }
        return builder.build()
    }

    /** Used when the ringing service cannot start: the alert still appears, with its actions. */
    @SuppressLint("MissingPermission")
    fun postAlertWithoutService(alert: ActiveAlert, settings: AppSettings) {
        if (!permissions.canPostNotifications()) return
        manager.notify(alertId(alert.occurrenceId), buildAlert(alert, settings))
    }

    fun cancelAlert(occurrenceId: String) {
        manager.cancel(alertId(occurrenceId))
    }

    /** Minimal notification for the instant the ringing service starts with nothing queued. */
    fun buildIdle(): Notification = NotificationCompat.Builder(context, NotificationChannels.STATUS)
        .setSmallIcon(R.drawable.ic_stat_roundly)
        .setContentTitle(context.getString(R.string.ringing_service_idle))
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()

    /** "Missed: [label] at [time]". The schedule keeps running. */
    @SuppressLint("MissingPermission")
    fun postMissed(alert: ActiveAlert, settings: AppSettings) {
        if (!permissions.canPostNotifications()) return
        val notification = NotificationCompat.Builder(context, NotificationChannels.MISSED)
            .setSmallIcon(R.drawable.ic_stat_roundly)
            .setContentTitle(
                context.getString(
                    R.string.missed_title,
                    displayLabel(alert),
                    timeFormatter.time(alert.scheduledAt, settings.timeFormat),
                ),
            )
            .setContentText(context.getString(R.string.missed_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        manager.notify(("missed:" + alert.occurrenceId).hashCode(), notification)
    }

    fun cancelMissed(occurrenceId: String) {
        manager.cancel(("missed:$occurrenceId").hashCode())
    }

    /** Quiet notice when an alarm stops on its own. */
    @SuppressLint("MissingPermission")
    fun postFinished(alarm: Alarm, reason: FinishReason) {
        if (!permissions.canPostNotifications()) return
        val label = alarm.label.ifBlank { context.getString(R.string.alert_default_label) }
        val text = when (reason) {
            FinishReason.RUN_FOR_ENDED -> context.getString(R.string.finished_run_for)
            FinishReason.SCHEDULE_COMPLETED -> context.getString(R.string.finished_completed)
        }
        val notification = NotificationCompat.Builder(context, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_stat_roundly)
            .setContentTitle(context.getString(R.string.finished_title, label))
            .setContentText(text)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        manager.notify(("finished:" + alarm.id).hashCode(), notification)
    }

    private fun actionIntent(alert: ActiveAlert, action: AlertAction): PendingIntent {
        val intent = Intent(context, AlertActionReceiver::class.java)
            .setAction(AlertActionReceiver.ACTION_RESPOND)
            .setData("roundly://alert/${Uri.encode(alert.occurrenceId)}/${action.name}".toUri())
            .putExtra(AlertActionReceiver.EXTRA_OCCURRENCE_ID, alert.occurrenceId)
            .putExtra(AlertActionReceiver.EXTRA_ACTION, action.name)
        return PendingIntent.getBroadcast(
            context,
            alertId(alert.occurrenceId),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN_APP,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun alertId(occurrenceId: String): Int = ("alert:$occurrenceId").hashCode()

    companion object {
        /** The ringing service's foreground notification. */
        const val RINGING_NOTIFICATION_ID = 1001
        private const val REQUEST_ALERT_ACTIVITY = 1
        private const val REQUEST_OPEN_APP = 2
    }
}
