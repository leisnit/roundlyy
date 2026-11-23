package app.roundly.core.schedule

import app.roundly.core.model.AfterLastStep
import app.roundly.core.model.Alarm
import app.roundly.core.model.AlarmType
import app.roundly.core.model.StartMode
import app.roundly.core.model.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteppedScheduleTest {

    private val clock = TestClock()
    private val calc = ScheduleCalculator(clock, seeded(2))

    /** Every 15 min x4, then every 30 min x2, then hourly x4. */
    private val steps = listOf(Step(15 * MIN, 4), Step(30 * MIN, 2), Step(HOUR, 4))

    private fun stepped(after: AfterLastStep = AfterLastStep.STOP, startMode: StartMode = StartMode.NOW, anchor: Long = T0) = Alarm(
        id = 5,
        label = "Warm-up",
        type = AlarmType.STEPPED,
        steps = steps,
        afterLastStep = after,
        startMode = startMode,
        anchorAt = anchor,
        createdAt = T0,
    )

    private val plannedOffsets = listOf(15, 30, 45, 60, 90, 120, 180, 240, 300, 360).map { it * MIN }

    @Test
    fun stepTransitionsFollowThePlan() {
        val times = calc.collect(clock, stepped(), until = T0 + 24 * HOUR)
        assertEquals(plannedOffsets.map { T0 + it }, times)
    }

    @Test
    fun progressReadsStepAndAlertNumbers() {
        var plan = calc.plan(stepped())
        val seen = mutableListOf<String>()
        while (plan.trigger != null) {
            val p = calc.stepProgress(plan.alarm)!!
            seen += "${p.stepNumber}/${p.stepCount}:${p.alertNumber}/${p.alertCount}"
            plan = calc.fire(clock, plan)
        }
        assertEquals(
            listOf("1/3:1/4", "1/3:2/4", "1/3:3/4", "1/3:4/4", "2/3:1/2", "2/3:2/2", "3/3:1/4", "3/3:2/4", "3/3:3/4", "3/3:4/4"),
            seen,
        )
    }

    @Test
    fun stopFinishesAfterTheLastStep() {
        var plan = calc.plan(stepped())
        repeat(10) { plan = calc.fire(clock, plan) }
        assertNull(plan.trigger)
        assertEquals(FinishReason.SCHEDULE_COMPLETED, plan.finishReason)
        assertEquals(T0 + 360 * MIN, plan.finishAt)
    }

    @Test
    fun continueKeepsTheLastInterval() {
        val times = calc.collect(clock, stepped(AfterLastStep.CONTINUE), until = T0 + 9 * HOUR)
        assertEquals(plannedOffsets.map { T0 + it } + listOf(T0 + 7 * HOUR, T0 + 8 * HOUR, T0 + 9 * HOUR), times)
    }

    @Test
    fun continuingProgressIsFlagged() {
        var plan = calc.plan(stepped(AfterLastStep.CONTINUE))
        repeat(11) { plan = calc.fire(clock, plan) }
        val progress = calc.stepProgress(plan.alarm)!!
        assertTrue(progress.continuing)
        assertEquals(3, progress.stepNumber)
        assertEquals(6, progress.alertNumber)
    }

    @Test
    fun lateFiringDoesNotDriftStepBoundaries() {
        var plan = calc.plan(stepped())
        val times = mutableListOf<Long>()
        while (plan.trigger != null) {
            times += plan.trigger!!.at
            plan = calc.fire(clock, plan, lateBy = 2 * MIN)
        }
        assertEquals(plannedOffsets.map { T0 + it }, times)
    }

    @Test
    fun skippedSlotsStillAdvanceTheCounters() {
        clock.set(T0 + 100 * MIN) // slots at 15..90 were missed
        val plan = calc.plan(stepped())
        assertEquals(T0 + 120 * MIN, plan.trigger?.at)
        val progress = calc.stepProgress(plan.alarm)!!
        assertEquals(2, progress.stepNumber)
        assertEquals(2, progress.alertNumber)
    }

    @Test
    fun skippingPastTheWholeScheduleFinishes() {
        clock.set(T0 + 20 * HOUR)
        val plan = calc.plan(stepped())
        assertNull(plan.trigger)
        assertEquals(FinishReason.SCHEDULE_COMPLETED, plan.finishReason)
    }

    @Test
    fun skippingPastTheScheduleWithContinueLandsOnTheLastInterval() {
        clock.set(T0 + 20 * HOUR + 10 * MIN)
        assertEquals(T0 + 21 * HOUR, calc.plan(stepped(AfterLastStep.CONTINUE)).trigger?.at)
    }

    @Test
    fun startAtTimeFiresTheFirstAlertAtTheStartTime() {
        val start = T0 + 3 * HOUR
        val times = calc.collect(clock, stepped(startMode = StartMode.AT_TIME, anchor = start), until = start + 2 * HOUR)
        // Step 1 (15 min x4) starts at the start time; step 2 (30 min) anchors to step 1's planned end.
        assertEquals(
            listOf(start, start + 15 * MIN, start + 30 * MIN, start + 45 * MIN, start + 75 * MIN, start + 105 * MIN),
            times,
        )
    }

    @Test
    fun resumeContinuesAtTheSameStep() {
        var plan = calc.plan(stepped())
        repeat(5) { plan = calc.fire(clock, plan) } // fired 15,30,45,60,90 -> next is step 2 alert 2
        val paused = plan.alarm.copy(pausedByMaster = true)

        clock.set(T0 + 10 * HOUR) // long pause, no catch-up wanted
        val resumed = calc.plan(calc.resume(paused.copy(pausedByMaster = false)))
        assertEquals(T0 + 10 * HOUR + 30 * MIN, resumed.trigger?.at)
        val progress = calc.stepProgress(resumed.alarm)!!
        assertEquals(2, progress.stepNumber)
        assertEquals(2, progress.alertNumber)
    }
}
