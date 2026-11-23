package app.roundly.core.model

/** Input limits shared by the editor, import validation and the scheduler. */
object AlarmLimits {
    const val MINUTE_MS = 60_000L
    const val HOUR_MS = 60 * MINUTE_MS
    const val DAY_MS = 24 * HOUR_MS

    /** Fixed and stepped intervals: 1 min to 23 h 59 min. */
    const val MIN_INTERVAL_MS = MINUTE_MS
    const val MAX_INTERVAL_MS = 23 * HOUR_MS + 59 * MINUTE_MS

    /** Irregular windows. */
    const val MIN_WINDOW_MS = 5 * MINUTE_MS
    const val MAX_WINDOW_MS = MAX_INTERVAL_MS
    val ALERTS_PER_WINDOW = 1..12
    const val DEFAULT_MIN_GAP_MS = 5 * MINUTE_MS

    /** Stepped schedules. */
    const val MAX_STEPS = 20
    val STEP_REPEATS = 1..99

    /** One-off timers: up to 7 days ahead. */
    const val MAX_ONE_OFF_MS = 7 * DAY_MS

    /** Recent timers are auto-cleared after this long. */
    const val RECENT_TIMER_RETENTION_MS = 7 * DAY_MS

    /** Alert history retention. */
    const val ALERT_LOG_RETENTION_MS = 30 * DAY_MS

    const val MAX_LABEL_LENGTH = 60
}
