package app.roundly.core.sync

/**
 * Wearable Data Layer contract shared by phone and watch.
 * All traffic goes over the Bluetooth link between paired devices; no internet is used.
 *
 * - Phone -> watch state: one DataItem at [PATH_STATE] (synced and cached by the Data Layer,
 *   so the watch always has the latest schedule, even after reconnecting).
 * - Phone -> watch events: messages [MSG_ALERT_FIRED] and [MSG_ALERT_CANCEL] (only when connected).
 * - Watch -> phone: one DataItem per response or command under [PATH_WATCH_RESPONSE_PREFIX] and
 *   [PATH_WATCH_COMMAND_PREFIX]. DataItems queue while disconnected and sync on reconnect; the
 *   phone applies them, then deletes them.
 */
object DataLayerContract {
    /** Capability advertised by the watch app (wear/src/main/res/values/wear.xml). */
    const val CAPABILITY_WATCH = "interval_alarm_watch"

    /** Capability advertised by the phone app (app/src/main/res/values/wear.xml). */
    const val CAPABILITY_PHONE = "interval_alarm_phone"

    const val PATH_STATE = "/roundly/state"
    const val KEY_PAYLOAD = "payload"

    const val PATH_WATCH_RESPONSE_PREFIX = "/roundly/watch/response/"
    const val PATH_WATCH_COMMAND_PREFIX = "/roundly/watch/command/"

    const val MSG_ALERT_FIRED = "/roundly/alert/fired"
    const val MSG_ALERT_CANCEL = "/roundly/alert/cancel"
}
