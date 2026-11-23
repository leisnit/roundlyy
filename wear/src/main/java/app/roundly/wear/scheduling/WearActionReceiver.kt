package app.roundly.wear.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.roundly.core.sync.WatchAction

/** Done / Snooze tapped on the watch alert notification. */
class WearActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RESPOND) return
        val occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID) ?: return
        val action = intent.getStringExtra(EXTRA_ACTION)
            ?.let { name -> WatchAction.entries.firstOrNull { it.name == name } }
            ?.takeIf { it != WatchAction.MISSED }
            ?: return
        runAsync(context) { alertController().respond(occurrenceId, action) }
    }

    companion object {
        const val ACTION_RESPOND = "app.roundly.wear.action.RESPOND"
        const val EXTRA_OCCURRENCE_ID = "occurrence_id"
        const val EXTRA_ACTION = "action"
    }
}
