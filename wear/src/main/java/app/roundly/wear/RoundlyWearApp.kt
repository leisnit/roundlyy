package app.roundly.wear

import android.app.Application
import app.roundly.wear.notifications.WearNotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RoundlyWearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WearNotificationChannels.ensureCreated(this)
    }
}
