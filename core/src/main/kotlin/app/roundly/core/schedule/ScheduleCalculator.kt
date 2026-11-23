package app.roundly.core.schedule

import app.roundly.core.model.AfterLastStep
import app.roundly.core.model.Alarm
import app.roundly.core.model.AlarmLimits
import app.roundly.core.model.AlarmType
import app.roundly.core.model.StartMode
import app.roundly.core.time.Clock

enum class TriggerKind {
    /** A scheduled alert from the alarm's cadence. */
    REGULAR,

    /** The repeat of a snoozed alert. */
    SNOOZE,
}

/**
 * The single next AlarmManager trigger for an alarm.
 * [occurrenceAt] identifies the occurrence (with the alarm id); for a snooze it is the
 * original alert's scheduled time, so phone and watch agree on the occurrence id.
 */
data class Trigger(val at: Long, val kind: TriggerKind, val occurrenceAt: Long)

enum class FinishReason {
    /** The run-for limit (endsAt) was reached. */
    RUN_FOR_ENDED,

    /** A stepped schedule set to stop has fired its final alert. */
    SCHEDULE_COMPLETED,
}

/**
 * Result of a calculation.
 *
 * @property alarm the alarm with updated scheduling state. Persist it.
 * @property trigger the single next trigger to arm, or null.
 * @property finishAt set only when [trigger] is null and the alarm will finish (or, if in the
 *   past, has already finished): disable it then and post the quiet "Finished" notice.
 */
data class Plan(
    val alarm: Alarm,
    val trigger: Trigger?,
    val finishAt: Long? = null,
    val finishReason: FinishReason? = null,
)

/** Progress through a stepped schedule, for "Step 2 of 3 · alert 1 of 2". */
data class StepProgress(
    val stepNumber: Int,
    val stepCount: Int,
    val alertNumber: Int,
    val alertCount: Int,
    /** Past the final step and repeating its interval (after-last-step = CONTINUE). */
    val continuing: Boolean,
)

/**
 * Pure scheduling logic shared by phone and watch. No Android dependencies; time and randomness
 * are injected so every rule can be unit tested.
 *
 * Rules:
 * - Only the single next trigger is ever computed.
 * - Fixed: the first `anchorAt + n x interval` after now. Missed slots are skipped, never burst.
 * - Irregular: random times for the current window are generated lazily, persisted (hidden) and
 *   reused until the window passes, so they are stable across process death and reboot.
 * - Stepped: each step anchors to the planned end of the previous one, so nothing drifts;
 *   skipped slots still advance the counters.
 * - Snooze: dropped if it would reach or pass the next regular trigger (or the run-for end).
 * - Run-for: no trigger is ever planned past endsAt.
 */
class ScheduleCalculator(
    private val clock: Clock,
    private val random: RandomSource,
) {

    /** Plans the next trigger from the alarm's current state. */
    fun plan(alarm: Alarm): Plan = planAfter(alarm, clock.now())

    /**
     * Call when [trigger] has fired. Consumes that occurrence and plans the next trigger right
     * away, so the cadence continues while the alert rings.
     */
    fun onFired(alarm: Alarm, trigger: Trigger): Plan {
        val now = clock.now()
        return when (trigger.kind) {
            TriggerKind.SNOOZE -> planAfter(
                alarm.copy(snoozeUntil = null, snoozedScheduledAt = null),
                now,
            )
            TriggerKind.REGULAR -> planAfter(
                alarm.copy(lastFiredAt = trigger.at),
                maxOf(now, trigger.at),
            )
        }
    }

    /**
     * Snoozes the occurrence at [occurrenceAt]. The underlying cadence does not move.
     * [requestedAt] is when Snooze was pressed (earlier than now for a response that was queued on
     * the watch while disconnected); a snooze that would already have elapsed is not applied.
     */
    fun snooze(alarm: Alarm, occurrenceAt: Long, snoozeMs: Long, requestedAt: Long = clock.now()): Plan {
        val now = clock.now()
        val until = requestedAt + snoozeMs
        if (until <= now) return planAfter(alarm.copy(snoozeUntil = null, snoozedScheduledAt = null), now)
        return planAfter(alarm.copy(snoozeUntil = until, snoozedScheduledAt = occurrenceAt), now)
    }

    /**
     * Prepares an alarm that is being resumed (Resume all or switched back on). Recalculates from
     * now with no catch-up bursts:
     * - fixed keeps its cadence anchor (missed slots are skipped by [plan]);
     * - irregular starts a fresh window now;
     * - stepped stays on its current step and alert, with the next alert one interval from now.
     */
    fun resume(alarm: Alarm): Alarm {
        val now = clock.now()
        val cleared = alarm.copy(snoozeUntil = null, snoozedScheduledAt = null, nextTriggerAt = null)
        return when (alarm.type) {
            AlarmType.FIXED, AlarmType.ONE_OFF -> cleared
            AlarmType.IRREGULAR -> cleared.copy(
                anchorAt = now,
                startMode = StartMode.NOW,
                currentWindowStart = null,
                pendingRandomTimes = emptyList(),
            )
            AlarmType.STEPPED -> {
                if (alarm.steps.isEmpty()) return cleared
                val index = alarm.stepIndex.coerceIn(0, alarm.steps.lastIndex)
                val interval = alarm.steps[index].intervalMs
                cleared.copy(stepIndex = index, stepAnchorAt = now - alarm.repeatIndex.toLong() * interval)
            }
        }
    }

    /** Progress of the next (upcoming) stepped alert, or null for other types. */
    fun stepProgress(alarm: Alarm): StepProgress? {
        if (alarm.type != AlarmType.STEPPED || alarm.steps.isEmpty()) return null
        val index = alarm.stepIndex.coerceIn(0, alarm.steps.lastIndex)
        val step = alarm.steps[index]
        val isLast = index == alarm.steps.lastIndex
        val continuing = isLast && alarm.repeatIndex >= step.repeats
        return StepProgress(
            stepNumber = index + 1,
            stepCount = alarm.steps.size,
            alertNumber = if (continuing) alarm.repeatIndex + 1 else (alarm.repeatIndex + 1).coerceAtMost(step.repeats),
            alertCount = step.repeats,
            continuing = continuing,
        )
    }

    // ---------------------------------------------------------------------------------------

    private fun planAfter(alarm: Alarm, after: Long): Plan {
        if (!alarm.isActive) return Plan(alarm.copy(nextTriggerAt = null), trigger = null)

        var completed = false
        val (advanced, regularNext) = when (alarm.type) {
            AlarmType.FIXED -> alarm to fixedNext(alarm, after)
            AlarmType.IRREGULAR -> irregularNext(alarm, after)
            AlarmType.STEPPED -> {
                val result = steppedNext(alarm, after)
                completed = result.completed
                result.alarm to result.next
            }
            AlarmType.ONE_OFF -> alarm to oneOffNext(alarm)
        }

        var state = advanced
        var next = regularNext
        var finishAt: Long? = null
        var reason: FinishReason? = null

        val endsAt = state.endsAt
        if (endsAt != null && (next == null || next > endsAt) && state.type != AlarmType.ONE_OFF) {
            if (next != null || !completed) {
                next = null
                finishAt = endsAt
                reason = FinishReason.RUN_FOR_ENDED
            }
        }
        if (next == null && finishAt == null && completed) {
            finishAt = after
            reason = FinishReason.SCHEDULE_COMPLETED
        }

        val snoozeUntil = state.snoozeUntil
        if (snoozeUntil != null) {
            val collides = next != null && snoozeUntil >= next
            val pastEnd = endsAt != null && snoozeUntil > endsAt
            if (collides || pastEnd) state = state.copy(snoozeUntil = null, snoozedScheduledAt = null)
        }

        val trigger = state.snoozeUntil?.let { Trigger(it, TriggerKind.SNOOZE, state.snoozedScheduledAt ?: it) }
            ?: next?.let { Trigger(it, TriggerKind.REGULAR, it) }

        return Plan(
            alarm = state.copy(nextTriggerAt = trigger?.at),
            trigger = trigger,
            finishAt = if (trigger == null) finishAt else null,
            finishReason = if (trigger == null) reason else null,
        )
    }

    // --- Fixed --------------------------------------------------------------------------------

    private fun fixedNext(alarm: Alarm, after: Long): Long? {
        val interval = alarm.intervalMs?.takeIf { it > 0 } ?: return null
        // "Start now": first alert one interval after saving. "Start at": first alert at that time.
        val firstN = if (alarm.startMode == StartMode.NOW) 1L else 0L
        val first = alarm.anchorAt + firstN * interval
        if (after < first) return first
        val n = (after - alarm.anchorAt) / interval + 1
        return alarm.anchorAt + n * interval
    }

    // --- One-off ------------------------------------------------------------------------------

    /** Fires once. A timer that came due while the device was off fires late, once. */
    private fun oneOffNext(alarm: Alarm): Long? =
        if (alarm.lastFiredAt == null && alarm.completedAt == null) alarm.fireAt else null

    // --- Irregular ----------------------------------------------------------------------------

    private fun irregularNext(alarm: Alarm, after: Long): Pair<Alarm, Long?> {
        val window = alarm.windowMs?.takeIf { it > 0 } ?: return alarm to null
        val alerts = alarm.alertsPerWindow ?: return alarm to null
        val gap = alarm.minGapMs ?: AlarmLimits.DEFAULT_MIN_GAP_MS
        if (ScheduleValidation.validateIrregular(window, alerts, gap) != null) return alarm to null

        val windowIndex = if (after < alarm.anchorAt) 0L else (after - alarm.anchorAt) / window
        val targetStart = alarm.anchorAt + windowIndex * window

        var windowStart = alarm.currentWindowStart
        var times = alarm.pendingRandomTimes
        // Regenerate when there is no window yet, the stored window has passed, or the clock
        // moved backwards by more than a window.
        if (windowStart == null || windowStart < targetStart || windowStart > targetStart + window) {
            windowStart = targetStart
            times = generateWindow(windowStart, window, alerts, gap, previousAlertAt = alarm.lastFiredAt)
        }

        repeat(MAX_WINDOW_HOPS) {
            val upcoming = times.filter { it > after }
            if (upcoming.isNotEmpty()) {
                return alarm.copy(currentWindowStart = windowStart, pendingRandomTimes = upcoming) to upcoming.first()
            }
            val previousLast = times.maxOrNull() ?: alarm.lastFiredAt
            windowStart += window
            times = generateWindow(windowStart, window, alerts, gap, previousAlertAt = previousLast)
        }
        return alarm.copy(currentWindowStart = null, pendingRandomTimes = emptyList()) to null
    }

    /**
     * [alerts] uniformly random times in `(start, start + window)`, sorted, each at least [gap]
     * after the previous one, and at least [gap] after [previousAlertAt] (the last alert of the
     * previous window) so the gap also holds across window boundaries.
     *
     * Method: draw `alerts` points in `[0, span)`, sort them, then add `i x gap` to the i-th.
     * This is uniform over all valid arrangements.
     */
    private fun generateWindow(start: Long, window: Long, alerts: Int, gap: Long, previousAlertAt: Long?): List<Long> {
        val fromPrevious = previousAlertAt?.let { it + gap - start } ?: 0L
        val offset = fromPrevious.coerceIn(1L, maxOf(1L, gap))
        val span = window - offset - (alerts - 1) * gap
        if (span <= 0) return emptyList()
        return List(alerts) { random.nextLong(span) }
            .sorted()
            .mapIndexed { i, p -> start + offset + p + i * gap }
    }

    // --- Stepped ------------------------------------------------------------------------------

    private class SteppedResult(val alarm: Alarm, val next: Long?, val completed: Boolean)

    private fun steppedNext(alarm: Alarm, after: Long): SteppedResult {
        val steps = alarm.steps
        if (steps.isEmpty()) return SteppedResult(alarm, null, completed = true)
        val continueAfterLast = alarm.afterLastStep == AfterLastStep.CONTINUE

        var index = alarm.stepIndex.coerceIn(0, steps.lastIndex)
        var repeatsDone = alarm.repeatIndex.coerceAtLeast(0)
        var anchor = alarm.stepAnchorAt ?: initialStepAnchor(alarm)

        // Bounded: at most one pass per step plus one skip pass per step.
        repeat(steps.size * 2 + 2) {
            val step = steps[index]
            val interval = step.intervalMs
            val isLast = index == steps.lastIndex
            val continuing = isLast && continueAfterLast

            if (repeatsDone >= step.repeats && !continuing) {
                if (isLast) {
                    return SteppedResult(
                        alarm.copy(stepIndex = index, repeatIndex = step.repeats, stepAnchorAt = anchor),
                        next = null,
                        completed = true,
                    )
                }
                // The next step starts at the planned end of this one, not when it actually fired.
                anchor += step.repeats.toLong() * interval
                index += 1
                repeatsDone = 0
                return@repeat
            }

            // Slots at or before `after` are consumed (fired or skipped); counters still advance.
            val passed = if (after < anchor) 0L else (after - anchor) / interval
            if (passed > repeatsDone) {
                val capped = passed.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                repeatsDone = if (continuing) capped else minOf(capped, step.repeats)
                return@repeat
            }

            val next = anchor + (repeatsDone + 1L) * interval
            return SteppedResult(
                alarm.copy(stepIndex = index, repeatIndex = repeatsDone, stepAnchorAt = anchor),
                next = next,
                completed = false,
            )
        }
        return SteppedResult(alarm, null, completed = false)
    }

    /** "Start now": first alert one interval after saving. "Start at": first alert at that time. */
    private fun initialStepAnchor(alarm: Alarm): Long =
        if (alarm.startMode == StartMode.AT_TIME) alarm.anchorAt - alarm.steps.first().intervalMs else alarm.anchorAt

    private companion object {
        const val MAX_WINDOW_HOPS = 4
    }
}
