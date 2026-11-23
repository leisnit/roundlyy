package app.roundly.watch

import app.roundly.alert.ActiveAlert

/**
 * Phone side of the phone-watch link. All traffic uses the Wearable Data Layer over Bluetooth.
 */
interface WatchBridge {
    /** True when a watch with the Roundly app is connected right now. */
    suspend fun isWatchReachable(): Boolean

    /** Asks the watch to show its full-screen alert for [alert]. Returns true if delivered. */
    suspend fun sendAlert(alert: ActiveAlert): Boolean

    /** Tells the watch an occurrence was answered here, so it stops ringing. */
    suspend fun sendCancel(occurrenceId: String)

    /** Pushes alarm definitions and the upcoming schedule to the watch. */
    suspend fun syncSchedule()
}
