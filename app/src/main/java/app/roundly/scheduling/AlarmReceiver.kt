package app.roundly.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.roundly.core.schedule.TriggerKind

/**
 * Receives each AlarmManager trigger. It advances the schedule (arming the next trigger at once)
 * and hands the alert to the controller, which starts the short-lived ringing service.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val alarmId = intent.data?.lastPathSegment?.toLongOrNull() ?: return
        val kind = intent.getStringExtra(EXTRA_KIND) ?: return
        val at = intent.getLongExtra(EXTRA_AT, 0L)
        val occurrenceAt = intent.getLongExtra(EXTRA_OCCURRENCE_AT, at)

        runAsync(context) {
            if (kind == TriggerScheduler.KIND_FINISH) {
                scheduler().onFinishDue(alarmId)
                return@runAsync
            }
            val triggerKind = TriggerKind.entries.firstOrNull { it.name == kind } ?: return@runAsync
            scheduler().onTriggerFired(alarmId, triggerKind, at, occurrenceAt)
                ?.let { alertController().present(it) }
        }
    }

    companion object {
        const val ACTION_FIRE = "app.roundly.action.FIRE"
        const val EXTRA_KIND = "kind"
        const val EXTRA_AT = "at"
        const val EXTRA_OCCURRENCE_AT = "occurrence_at"
    }
}
