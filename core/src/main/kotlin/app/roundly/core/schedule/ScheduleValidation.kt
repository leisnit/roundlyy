package app.roundly.core.schedule

import app.roundly.core.model.AlarmLimits
import app.roundly.core.model.AlarmType
import app.roundly.core.model.Step

/**
 * Problems the editor reports before saving. The UI turns each into a clear sentence; the data
 * carried here (for example [IrregularImpossible.minimumWindowMs]) lets it suggest a fix.
 */
sealed interface ScheduleProblem {
    data object LabelTooLong : ScheduleProblem
    data object IntervalOutOfRange : ScheduleProblem
    data object WindowOutOfRange : ScheduleProblem
    data object AlertsPerWindowOutOfRange : ScheduleProblem
    data object NegativeGap : ScheduleProblem

    /**
     * N alerts with a minimum gap G cannot fit in the window. Gaps are also kept across window
     * boundaries, so the requirement is N x G < window.
     */
    data class IrregularImpossible(
        val alerts: Int,
        val minGapMs: Long,
        val windowMs: Long,
        val minimumWindowMs: Long,
    ) : ScheduleProblem

    data object NoSteps : ScheduleProblem
    data object TooManySteps : ScheduleProblem
    data class StepIntervalOutOfRange(val index: Int) : ScheduleProblem
    data class StepRepeatsOutOfRange(val index: Int) : ScheduleProblem

    data object OneOffMissingTime : ScheduleProblem
    data object OneOffTooLong : ScheduleProblem
    data object RunForTooShort : ScheduleProblem
}

object ScheduleValidation {

    fun intervalInRange(ms: Long?): Boolean =
        ms != null && ms in AlarmLimits.MIN_INTERVAL_MS..AlarmLimits.MAX_INTERVAL_MS

    /** Smallest window that can hold [alerts] alerts with [minGapMs] between every pair. */
    fun minimumWindowMs(alerts: Int, minGapMs: Long): Long = alerts * minGapMs + 1

    fun validateIrregular(windowMs: Long?, alerts: Int?, minGapMs: Long?): ScheduleProblem? {
        val gap = minGapMs ?: AlarmLimits.DEFAULT_MIN_GAP_MS
        return when {
            windowMs == null || windowMs !in AlarmLimits.MIN_WINDOW_MS..AlarmLimits.MAX_WINDOW_MS ->
                ScheduleProblem.WindowOutOfRange
            alerts == null || alerts !in AlarmLimits.ALERTS_PER_WINDOW -> ScheduleProblem.AlertsPerWindowOutOfRange
            gap < 0 -> ScheduleProblem.NegativeGap
            alerts * gap >= windowMs -> ScheduleProblem.IrregularImpossible(
                alerts = alerts,
                minGapMs = gap,
                windowMs = windowMs,
                minimumWindowMs = minimumWindowMs(alerts, gap),
            )
            else -> null
        }
    }

    fun validateSteps(steps: List<Step>): List<ScheduleProblem> = buildList {
        if (steps.isEmpty()) add(ScheduleProblem.NoSteps)
        if (steps.size > AlarmLimits.MAX_STEPS) add(ScheduleProblem.TooManySteps)
        steps.forEachIndexed { index, step ->
            if (!intervalInRange(step.intervalMs)) add(ScheduleProblem.StepIntervalOutOfRange(index))
            if (step.repeats !in AlarmLimits.STEP_REPEATS) add(ScheduleProblem.StepRepeatsOutOfRange(index))
        }
    }

    /** Validates a draft before it becomes an alarm. An empty list means it can be saved. */
    fun validate(draft: AlarmDraft): List<ScheduleProblem> = buildList {
        if (draft.label.trim().length > AlarmLimits.MAX_LABEL_LENGTH) add(ScheduleProblem.LabelTooLong)
        when (draft.type) {
            AlarmType.FIXED -> if (!intervalInRange(draft.intervalMs)) add(ScheduleProblem.IntervalOutOfRange)
            AlarmType.IRREGULAR -> validateIrregular(draft.windowMs, draft.alertsPerWindow, draft.minGapMs)?.let(::add)
            AlarmType.STEPPED -> addAll(validateSteps(draft.steps))
            AlarmType.ONE_OFF -> when (draft.oneOffMode) {
                OneOffMode.COUNTDOWN -> {
                    val d = draft.oneOffDurationMs
                    if (d == null || d < AlarmLimits.MINUTE_MS) add(ScheduleProblem.OneOffMissingTime)
                    else if (d > AlarmLimits.MAX_ONE_OFF_MS) add(ScheduleProblem.OneOffTooLong)
                }
                OneOffMode.CLOCK_TIME -> if (draft.oneOffMinuteOfDay == null) add(ScheduleProblem.OneOffMissingTime)
            }
        }
        val runFor = draft.runForMs
        if (draft.type != AlarmType.ONE_OFF && runFor != null && runFor < AlarmLimits.MINUTE_MS) {
            add(ScheduleProblem.RunForTooShort)
        }
    }
}
