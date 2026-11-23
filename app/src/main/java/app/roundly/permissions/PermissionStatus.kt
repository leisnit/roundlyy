package app.roundly.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Live permission checks used by the scheduler, notifications and the Permissions health screen. */
@Singleton
class PermissionStatus @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun canPostNotifications(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val enabled = context.getSystemService<NotificationManager>()?.areNotificationsEnabled() ?: false
        return granted && enabled
    }

    /** API 31+ only; USE_EXACT_ALARM (33+) is granted at install, SCHEDULE_EXACT_ALARM (31-32) can be revoked. */
    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() ?: false
    }

    /** API 34+ lets users turn off full-screen intents; earlier versions always allow them. */
    fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return context.getSystemService<NotificationManager>()?.canUseFullScreenIntent() ?: false
    }

    fun isIgnoringBatteryOptimizations(): Boolean =
        context.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(context.packageName) ?: false
}
