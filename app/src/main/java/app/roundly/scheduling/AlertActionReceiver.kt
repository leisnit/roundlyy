package app.roundly.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.roundly.alert.AlertAction
import app.roundly.core.model.RespondedOn

/** Done / Snooze tapped on the alert notification. */
class AlertActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RESPOND) return
        val occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID) ?: return
        val action = intent.getStringExtra(EXTRA_ACTION)
            ?.let { name -> AlertAction.entries.firstOrNull { it.name == name } }
            ?: return
        runAsync(context) {
            alertController().respond(occurrenceId, action, RespondedOn.PHONE)
        }
    }

    companion object {
        const val ACTION_RESPOND = "app.roundly.action.RESPOND"
        const val EXTRA_OCCURRENCE_ID = "occurrence_id"
        const val EXTRA_ACTION = "action"
    }
}
