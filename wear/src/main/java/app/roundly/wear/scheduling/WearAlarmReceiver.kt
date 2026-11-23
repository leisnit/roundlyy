package app.roundly.wear.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.roundly.core.schedule.TriggerKind

/**
 * Standalone fallback trigger. The local copy always advances; the watch alerts on its own only
 * when the phone is out of range (otherwise the phone sends its alert and nothing rings twice).
 */
class WearAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val alarmId = intent.data?.lastPathSegment?.toLongOrNull() ?: return
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { k -> TriggerKind.entries.firstOrNull { it.name == k } } ?: return
        val at = intent.getLongExtra(EXTRA_AT, 0L)
        val occurrenceAt = intent.getLongExtra(EXTRA_OCCURRENCE_AT, at)

        runAsync(context) {
            val alert = scheduler().onTriggerFired(alarmId, kind, at, occurrenceAt) ?: return@runAsync
            if (!phoneLink().isPhoneReachable()) alertController().present(alert)
        }
    }

    companion object {
        const val ACTION_FIRE = "app.roundly.wear.action.FIRE"
        const val EXTRA_KIND = "kind"
        const val EXTRA_AT = "at"
        const val EXTRA_OCCURRENCE_AT = "occurrence_at"
    }
}
