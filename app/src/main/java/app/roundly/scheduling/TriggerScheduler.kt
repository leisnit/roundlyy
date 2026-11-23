package app.roundly.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import app.roundly.MainActivity
import app.roundly.core.schedule.Trigger
import app.roundly.permissions.PermissionStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over AlarmManager. Each alarm owns exactly one PendingIntent, keyed by its Room id
 * (request code and data URI), so arming a new trigger always replaces the previous one.
 *
 * Alerts use setAlarmClock(), which is exempt from Doze throttling (unlike
 * setExactAndAllowWhileIdle, limited to roughly one trigger per 9 minutes per app). The quiet
 * "Finished" wake-up is not an alert, so it uses an inexact, battery-friendly alarm instead.
 */
@Singleton
class TriggerScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissions: PermissionStatus,
) {
    private val alarmManager: AlarmManager = requireNotNull(context.getSystemService())

    fun arm(alarmId: Long, trigger: Trigger) {
        val operation = operation(alarmId, trigger.kind.name, trigger.at, trigger.occurrenceAt)
        if (permissions.canScheduleExactAlarms()) {
            try {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(trigger.at, showIntent(alarmId)), operation)
                return
            } catch (_: SecurityException) {
                // Permission revoked between the check and the call; fall through.
            }
        }
        // Without exact-alarm access the alert may be late. Permissions health shows a warning.
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.at, operation)
    }

    fun armFinish(alarmId: Long, at: Long) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation(alarmId, KIND_FINISH, at, at))
    }

    fun cancel(alarmId: Long) {
        alarmManager.cancel(operation(alarmId, KIND_FINISH, 0, 0))
    }

    private fun operation(alarmId: Long, kind: String, at: Long, occurrenceAt: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .setData("roundly://alarm/$alarmId".toUri())
            .putExtra(AlarmReceiver.EXTRA_KIND, kind)
            .putExtra(AlarmReceiver.EXTRA_AT, at)
            .putExtra(AlarmReceiver.EXTRA_OCCURRENCE_AT, occurrenceAt)
        return PendingIntent.getBroadcast(
            context,
            requestCode(alarmId),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Opened when the user taps the system's "next alarm" indicator. */
    private fun showIntent(alarmId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode(alarmId),
        Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun requestCode(alarmId: Long): Int = (alarmId and 0x7FFF_FFFF).toInt()

    companion object {
        const val KIND_FINISH = "FINISH"
    }
}
