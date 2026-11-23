package app.roundly

import android.app.Application
import app.roundly.data.Housekeeping
import app.roundly.data.repository.SettingsRepository
import app.roundly.di.ApplicationScope
import app.roundly.notifications.NotificationChannels
import app.roundly.watch.WatchBridge
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RoundlyApp : Application() {

    @Inject lateinit var housekeeping: Housekeeping
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var watchBridge: WatchBridge

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        appScope.launch { housekeeping.run() }
        // Settings the watch uses (snooze, ring timeout, time format, pause state) follow changes.
        // Unchanged state is never re-sent, so this costs nothing when idle.
        appScope.launch {
            combine(settings.settings, settings.state) { s, st -> s to st.pausedAll }
                .collect { watchBridge.syncSchedule() }
        }
    }
}
