package app.roundly.alert

import app.roundly.core.model.AlarmType
import app.roundly.core.model.AlertMode
import app.roundly.core.schedule.StepProgress

/** An alert that is ringing (or queued to ring) right now. Lives in memory only. */
data class ActiveAlert(
    val alarmId: Long,
    val occurrenceId: String,
    /** The occurrence's scheduled time (the original time, for a snooze repeat). */
    val scheduledAt: Long,
    val label: String,
    val type: AlarmType,
    val alertMode: AlertMode,
    val ringtoneUri: String?,
    val watchOnly: Boolean,
    val stepProgress: StepProgress?,
    val isSnoozeRepeat: Boolean,
    val firedAt: Long,
    /** Editor "Test alert": rings normally but is never logged and never touches the schedule. */
    val isTest: Boolean = false,
    /** The watch app received this alert and is alerting too. */
    val watchHandling: Boolean = false,
) {
    /** Watch only: the phone stays silent and the watch alerts. */
    val phoneSilent: Boolean
        get() = watchOnly && watchHandling
}

enum class AlertAction { DONE, SNOOZE }
