package app.roundly.core.schedule

import app.roundly.core.model.AlarmType
import app.roundly.core.model.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class OneOffAndValidationTest {

    private val clock = TestClock()
    private val calc = ScheduleCalculator(clock, seeded(7))

    private fun countdown(ms: Long) = AlarmFactory.create(
        AlarmDraft(type = AlarmType.ONE_OFF, oneOffMode = OneOffMode.COUNTDOWN, oneOffDurationMs = ms),
        clock.nowMs,
        ZoneOffset.UTC,
    ).copy(id = 9)

    @Test
    fun countdownFiresOnceAfterItsDuration() {
        val plan = calc.plan(countdown(30 * MIN))
        assertEquals(T0 + 30 * MIN, plan.trigger?.at)
        val fired = calc.fire(clock, plan)
        assertNull(fired.trigger)
        assertNull("a one-off is not 'finished' until acknowledged", fired.finishAt)
    }

    @Test
    fun clockTimeTimerUsesTheNextOccurrence() {
        val alarm = AlarmFactory.create(
            AlarmDraft(type = AlarmType.ONE_OFF, oneOffMode = OneOffMode.CLOCK_TIME, oneOffMinuteOfDay = 12 * 60 + 40),
            clock.nowMs,
            ZoneOffset.UTC,
        )
        assertEquals(T0 + 3 * HOUR + 40 * MIN, calc.plan(alarm).trigger?.at)
    }

    @Test
    fun timerDueWhileTheDeviceWasOffFiresLateOnce() {
        val alarm = countdown(15 * MIN)
        clock.set(T0 + 2 * HOUR)
        val plan = calc.plan(alarm)
        assertEquals(T0 + 15 * MIN, plan.trigger?.at)
        assertNull(calc.fire(clock, plan).trigger)
    }

    @Test
    fun snoozeWorksAsNormalForTimers() {
        val fired = calc.fire(clock, calc.plan(countdown(15 * MIN)))
        val snoozed = calc.snooze(fired.alarm, occurrenceAt = T0 + 15 * MIN, snoozeMs = 5 * MIN)
        assertEquals(Trigger(T0 + 20 * MIN, TriggerKind.SNOOZE, T0 + 15 * MIN), snoozed.trigger)
        assertNull(calc.fire(clock, snoozed).trigger)
    }

    @Test
    fun acknowledgedTimerIsARecentTimerWithNoTrigger() {
        val done = countdown(15 * MIN).copy(completedAt = T0 + 16 * MIN, lastFiredAt = T0 + 15 * MIN)
        assertTrue(done.isRecentTimer)
        assertNull(calc.plan(done).trigger)
    }

    @Test
    fun restartingARecentTimerRunsTheSameDurationFromNow() {
        val done = countdown(15 * MIN).copy(completedAt = T0 + 16 * MIN, lastFiredAt = T0 + 15 * MIN)
        clock.set(T0 + 3 * HOUR)
        val restarted = AlarmFactory.restartTimer(done, clock.nowMs, ZoneOffset.UTC)
        assertEquals(T0 + 3 * HOUR + 15 * MIN, calc.plan(restarted).trigger?.at)
        assertTrue(!restarted.isRecentTimer)
    }

    @Test
    fun draftValidation() {
        assertEquals(listOf(ScheduleProblem.IntervalOutOfRange), ScheduleValidation.validate(AlarmDraft(type = AlarmType.FIXED, intervalMs = 30_000)))
        assertEquals(listOf(ScheduleProblem.IntervalOutOfRange), ScheduleValidation.validate(AlarmDraft(type = AlarmType.FIXED, intervalMs = 24 * HOUR)))
        assertTrue(ScheduleValidation.validate(AlarmDraft(type = AlarmType.FIXED, intervalMs = 23 * HOUR + 59 * MIN)).isEmpty())
        assertEquals(listOf(ScheduleProblem.NoSteps), ScheduleValidation.validate(AlarmDraft(type = AlarmType.STEPPED, steps = emptyList())))
        assertEquals(
            listOf(ScheduleProblem.StepRepeatsOutOfRange(1)),
            ScheduleValidation.validate(AlarmDraft(type = AlarmType.STEPPED, steps = listOf(Step(10 * MIN, 2), Step(10 * MIN, 0)))),
        )
        assertEquals(
            listOf(ScheduleProblem.OneOffMissingTime),
            ScheduleValidation.validate(AlarmDraft(type = AlarmType.ONE_OFF, oneOffMode = OneOffMode.CLOCK_TIME, oneOffMinuteOfDay = null)),
        )
        assertTrue(
            ScheduleValidation.validate(
                AlarmDraft(type = AlarmType.IRREGULAR, windowMs = 30 * MIN, alertsPerWindow = 12, minGapMs = 5 * MIN),
            ).single() is ScheduleProblem.IrregularImpossible,
        )
    }
}
