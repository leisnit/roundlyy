package app.roundly.scheduling

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms every alarm when pending triggers may have been lost or shifted:
 * reboot, app update, manual clock change, time-zone change, and exact-alarm permission changes.
 * Absolute trigger times are recomputed from now, so missed slots are skipped, never burst.
 */
class RescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        runAsync(context) {
            housekeeping().run()
            scheduler().rescheduleAll()
        }
    }

    private companion object {
        // Inlined String constants; matching them is safe on every API level.
        @SuppressLint("InlinedApi")
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
