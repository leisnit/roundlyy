package app.roundly.core.schedule

import app.roundly.core.model.Alarm
import app.roundly.core.model.AlarmType

/**
 * Picks the alarm to feature as "next" (dashboard hero, watch tile, complication).
 * Irregular alarms never reveal their next time, so they are chosen only when nothing else is
 * running; callers must then show "within this window" instead of a time.
 */
object NextAlert {
    fun pick(alarms: List<Alarm>, pausedAll: Boolean): Alarm? {
        if (pausedAll) return null
        val upcoming = alarms.filter { it.isActive && it.nextTriggerAt != null }
        return upcoming
            .filter { it.type != AlarmType.IRREGULAR || it.snoozeUntil != null }
            .minByOrNull { it.nextTriggerAt!! }
            ?: upcoming.firstOrNull()
    }

    /** True if [alarm]'s next time may be shown. */
    fun revealsTime(alarm: Alarm): Boolean = alarm.type != AlarmType.IRREGULAR || alarm.snoozeUntil != null
}
