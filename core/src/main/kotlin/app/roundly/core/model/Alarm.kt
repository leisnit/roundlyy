package app.roundly.core.model

import kotlinx.serialization.Serializable

/**
 * An alarm definition plus its scheduling state. This is the single model shared by the phone
 * (Room), the watch (synced copy) and Export / Import.
 *
 * Type-specific fields are nullable and only meaningful for their [type]:
 * - FIXED: [intervalMs]
 * - IRREGULAR: [windowMs], [alertsPerWindow], [minGapMs], [currentWindowStart], [pendingRandomTimes]
 * - STEPPED: [steps], [afterLastStep], [stepIndex], [repeatIndex], [stepAnchorAt]
 * - ONE_OFF: [fireAt], [oneOffDurationMs]
 *
 * [pendingRandomTimes] is hidden state. It must never be shown in the UI or written to an export,
 * otherwise the irregular pattern becomes predictable.
 */
@Serializable
data class Alarm(
    val id: Long = 0,
    val label: String,
    val type: AlarmType,

    // Fixed interval
    val intervalMs: Long? = null,

    // Irregular interval
    val windowMs: Long? = null,
    val alertsPerWindow: Int? = null,
    val minGapMs: Long? = null,
    val currentWindowStart: Long? = null,
    val pendingRandomTimes: List<Long> = emptyList(),

    // Stepped schedule
    val steps: List<Step> = emptyList(),
    val afterLastStep: AfterLastStep? = null,
    /** Index of the step currently running. */
    val stepIndex: Int = 0,
    /** Alerts already fired (or skipped) in the current step. */
    val repeatIndex: Int = 0,
    /** Planned start of the current step. Each step anchors to the planned end of the previous one. */
    val stepAnchorAt: Long? = null,

    // One-off timer
    val fireAt: Long? = null,
    /** Countdown length, kept so a recent timer can be restarted with one tap. Null for clock-time timers. */
    val oneOffDurationMs: Long? = null,

    // Start and run-for
    val startMode: StartMode = StartMode.NOW,
    /** Cadence anchor: fixed slots are anchorAt + n * interval; irregular windows start here. */
    val anchorAt: Long,
    /** Run-for length as entered, so duplicates and restarts can recompute [endsAt]. */
    val runForMs: Long? = null,
    /** Absolute auto-stop time. No trigger is ever scheduled past it. */
    val endsAt: Long? = null,

    // State
    val enabled: Boolean = true,
    /** Set by Pause all on alarms that were enabled, so Resume all restores exactly those. */
    val pausedByMaster: Boolean = false,
    val alertMode: AlertMode = AlertMode.SOUND_AND_VIBRATION,
    val watchOnly: Boolean = false,
    val ringtoneUri: String? = null,
    val snoozeUntil: Long? = null,
    /** The occurrence being snoozed, so the repeat alert keeps the same occurrence id. */
    val snoozedScheduledAt: Long? = null,
    /** Cached next trigger, maintained by the scheduler for the dashboard, watch and tile. */
    val nextTriggerAt: Long? = null,
    val lastFiredAt: Long? = null,
    /** One-off timers: when the alert was acknowledged; the timer then shows under Recent timers. */
    val completedAt: Long? = null,
    val createdAt: Long,
) {
    /** True when the alarm should have a pending trigger. */
    val isActive: Boolean
        get() = enabled && !pausedByMaster && completedAt == null

    /** True for a one-off timer that has fired and been acknowledged. */
    val isRecentTimer: Boolean
        get() = type == AlarmType.ONE_OFF && completedAt != null

    /** Copy without the hidden or transient scheduling state, for Export. */
    fun forExport(): Alarm = copy(
        currentWindowStart = null,
        pendingRandomTimes = emptyList(),
        snoozeUntil = null,
        snoozedScheduledAt = null,
        nextTriggerAt = null,
        pausedByMaster = false,
    )

    /** Copy ready to insert as a new row (new id, fresh transient state). */
    fun asNew(): Alarm = forExport().copy(id = 0)
}
