package app.roundly.core.schedule

import app.roundly.core.model.Alarm
import app.roundly.core.model.AlarmType
import app.roundly.core.model.StartMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FixedScheduleTest {

    private val clock = TestClock()
    private val calc = ScheduleCalculator(clock, seeded(1))

    private fun fixed(interval: Long, runFor: Long? = null, startMode: StartMode = StartMode.NOW, anchor: Long = T0) = Alarm(
        id = 1,
        label = "Water",
        type = AlarmType.FIXED,
        intervalMs = interval,
        startMode = startMode,
        anchorAt = anchor,
        runForMs = runFor,
        endsAt = runFor?.let { anchor + it },
        createdAt = T0,
    )

    @Test
    fun startNow_firstAlertIsOneIntervalAfterSaving() {
        assertEquals(T0 + 30 * MIN, calc.plan(fixed(30 * MIN)).trigger?.at)
    }

    @Test
    fun startAtTime_firstAlertIsAtTheStartTime() {
        val start = T0 + 2 * HOUR
        val plan = calc.plan(fixed(30 * MIN, startMode = StartMode.AT_TIME, anchor = start))
        assertEquals(start, plan.trigger?.at)
    }

    @Test
    fun lateDeliveryNeverDrifts() {
        var plan = calc.plan(fixed(30 * MIN))
        repeat(50) { i ->
            plan = calc.fire(clock, plan, lateBy = (i % 7) * 13_000L)
            val next = plan.trigger!!.at
            assertEquals("slot ${i + 2} must stay on the anchor grid", 0L, (next - T0) % (30 * MIN))
            assertEquals(T0 + (i + 2) * 30 * MIN, next)
        }
    }

    @Test
    fun nextIsNeverNowPlusInterval() {
        clock.set(T0 + 7 * MIN)
        assertEquals(T0 + 30 * MIN, calc.plan(fixed(30 * MIN)).trigger?.at)
    }

    @Test
    fun missedSlotsAreSkippedNotBurst() {
        val alarm = fixed(30 * MIN)
        clock.set(T0 + 5 * HOUR + 10 * MIN) // device was off for hours
        val plan = calc.plan(alarm)
        assertEquals(T0 + 5 * HOUR + 30 * MIN, plan.trigger?.at)
        val after = calc.fire(clock, plan)
        assertEquals(T0 + 6 * HOUR, after.trigger?.at)
    }

    @Test
    fun exactlyOnASlotSchedulesTheFollowingSlot() {
        clock.set(T0 + 60 * MIN)
        assertEquals(T0 + 90 * MIN, calc.plan(fixed(30 * MIN)).trigger?.at)
    }

    @Test
    fun snoozeIsDroppedWhenItWouldPassTheNextRegularTrigger() {
        val fired = calc.fire(clock, calc.plan(fixed(10 * MIN))) // fired at +10
        clock.set(T0 + 16 * MIN)
        val snoozed = calc.snooze(fired.alarm, occurrenceAt = T0 + 10 * MIN, snoozeMs = 5 * MIN) // +21 > +20
        assertEquals(TriggerKind.REGULAR, snoozed.trigger?.kind)
        assertEquals(T0 + 20 * MIN, snoozed.trigger?.at)
        assertNull(snoozed.alarm.snoozeUntil)
    }

    @Test
    fun snoozeIsDroppedWhenItReachesTheNextRegularTriggerExactly() {
        val fired = calc.fire(clock, calc.plan(fixed(10 * MIN)))
        clock.set(T0 + 15 * MIN)
        val snoozed = calc.snooze(fired.alarm, occurrenceAt = T0 + 10 * MIN, snoozeMs = 5 * MIN) // == +20
        assertEquals(TriggerKind.REGULAR, snoozed.trigger?.kind)
    }

    @Test
    fun snoozeBeforeTheNextTriggerIsKeptAndDoesNotMoveTheCadence() {
        val fired = calc.fire(clock, calc.plan(fixed(30 * MIN))) // fired at +30
        clock.set(T0 + 31 * MIN)
        val snoozed = calc.snooze(fired.alarm, occurrenceAt = T0 + 30 * MIN, snoozeMs = 5 * MIN)
        assertEquals(Trigger(T0 + 36 * MIN, TriggerKind.SNOOZE, occurrenceAt = T0 + 30 * MIN), snoozed.trigger)

        val afterSnooze = calc.fire(clock, snoozed)
        assertEquals(T0 + 60 * MIN, afterSnooze.trigger?.at)
        assertEquals(TriggerKind.REGULAR, afterSnooze.trigger?.kind)
    }

    @Test
    fun autoStop_neverSchedulesPastEndsAt() {
        val alarm = fixed(25 * MIN, runFor = HOUR)
        val times = calc.collect(clock, alarm, until = T0 + 10 * HOUR)
        assertEquals(listOf(T0 + 25 * MIN, T0 + 50 * MIN), times)

        clock.set(T0 + 50 * MIN)
        val last = calc.plan(alarm.copy(lastFiredAt = T0 + 50 * MIN)) // next slot +75 is past endsAt
        assertNull(last.trigger)
        assertEquals(T0 + HOUR, last.finishAt)
        assertEquals(FinishReason.RUN_FOR_ENDED, last.finishReason)
    }

    @Test
    fun autoStop_slotExactlyAtEndsAtStillFires() {
        val alarm = fixed(30 * MIN, runFor = HOUR)
        assertEquals(listOf(T0 + 30 * MIN, T0 + 60 * MIN), calc.collect(clock, alarm, until = T0 + 3 * HOUR))
    }

    @Test
    fun autoStop_alreadyPassedFinishesImmediately() {
        clock.set(T0 + 2 * HOUR)
        val plan = calc.plan(fixed(25 * MIN, runFor = HOUR))
        assertNull(plan.trigger)
        assertEquals(T0 + HOUR, plan.finishAt)
    }

    @Test
    fun autoStop_dropsASnoozePastEndsAt() {
        val alarm = fixed(25 * MIN, runFor = HOUR)
        clock.set(T0 + 58 * MIN)
        val plan = calc.snooze(alarm, occurrenceAt = T0 + 50 * MIN, snoozeMs = 5 * MIN) // +63 > +60
        assertNull(plan.trigger)
        assertEquals(T0 + HOUR, plan.finishAt)
    }

    @Test
    fun disabledOrPausedAlarmsHaveNoTrigger() {
        assertNull(calc.plan(fixed(30 * MIN).copy(enabled = false)).trigger)
        assertNull(calc.plan(fixed(30 * MIN).copy(pausedByMaster = true)).trigger)
    }

    @Test
    fun resumeKeepsTheCadenceWithoutCatchUp() {
        val alarm = fixed(30 * MIN)
        clock.set(T0 + 3 * HOUR + 5 * MIN)
        val resumed = calc.resume(alarm)
        assertEquals(T0 + 3 * HOUR + 30 * MIN, calc.plan(resumed).trigger?.at)
    }
}
