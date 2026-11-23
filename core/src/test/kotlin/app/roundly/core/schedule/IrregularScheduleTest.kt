package app.roundly.core.schedule

import app.roundly.core.model.Alarm
import app.roundly.core.model.AlarmType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IrregularScheduleTest {

    private fun irregular(window: Long = HOUR, alerts: Int = 4, gap: Long = 5 * MIN, runFor: Long? = null) = Alarm(
        id = 3,
        label = "Posture",
        type = AlarmType.IRREGULAR,
        windowMs = window,
        alertsPerWindow = alerts,
        minGapMs = gap,
        anchorAt = T0,
        runForMs = runFor,
        endsAt = runFor?.let { T0 + it },
        createdAt = T0,
    )

    @Test
    fun everyAlertStaysInsideItsWindowWithTheMinimumGap() {
        repeat(300) { seed ->
            val clock = TestClock()
            val calc = ScheduleCalculator(clock, seeded(seed.toLong()))
            val times = calc.collect(clock, irregular(), until = T0 + 3 * HOUR - 1)

            val perWindow = times.groupBy { (it - T0) / HOUR }
            assertEquals("seed $seed: 3 windows", setOf(0L, 1L, 2L), perWindow.keys)
            perWindow.forEach { (index, inWindow) ->
                assertEquals("seed $seed window $index count", 4, inWindow.size)
                inWindow.forEach { t ->
                    assertTrue(t > T0 + index * HOUR)
                    assertTrue(t < T0 + (index + 1) * HOUR)
                }
            }
            times.zipWithNext().forEach { (a, b) ->
                assertTrue("seed $seed: gap ${(b - a) / 1000}s across windows too", b - a >= 5 * MIN)
            }
        }
    }

    @Test
    fun tightButPossibleConfigurationStillWorks() {
        repeat(100) { seed ->
            val clock = TestClock()
            val calc = ScheduleCalculator(clock, seeded(seed.toLong()))
            // 11 x 5 min = 55 min < 60 min.
            val times = calc.collect(clock, irregular(alerts = 11), until = T0 + 2 * HOUR - 1)
            assertEquals(22, times.size)
            times.zipWithNext().forEach { (a, b) -> assertTrue(b - a >= 5 * MIN) }
        }
    }

    @Test
    fun impossibleConfigurationsAreRejectedWithAMinimumWindow() {
        val problem = ScheduleValidation.validateIrregular(30 * MIN, 12, 5 * MIN)
        assertTrue(problem is ScheduleProblem.IrregularImpossible)
        assertEquals(60 * MIN + 1, (problem as ScheduleProblem.IrregularImpossible).minimumWindowMs)

        assertTrue(ScheduleValidation.validateIrregular(HOUR, 12, 5 * MIN) is ScheduleProblem.IrregularImpossible)
        assertNull(ScheduleValidation.validateIrregular(HOUR, 11, 5 * MIN))
        assertTrue(ScheduleValidation.validateIrregular(HOUR, 13, 1 * MIN) is ScheduleProblem.AlertsPerWindowOutOfRange)
        assertTrue(ScheduleValidation.validateIrregular(HOUR, 0, 1 * MIN) is ScheduleProblem.AlertsPerWindowOutOfRange)
    }

    @Test
    fun calculatorNeverSchedulesAnImpossibleConfiguration() {
        val calc = ScheduleCalculator(TestClock(), seeded(1))
        assertNull(calc.plan(irregular(window = 30 * MIN, alerts = 12)).trigger)
    }

    @Test
    fun generatedTimesAreStableAcrossRestart() {
        val clock = TestClock()
        val first = ScheduleCalculator(clock, seeded(11)).plan(irregular())
        val persisted = first.alarm

        // Process death: a new calculator with a different random source must reuse the stored times.
        val restarted = ScheduleCalculator(clock, seeded(999)).plan(persisted)
        assertEquals(first.trigger, restarted.trigger)
        assertEquals(persisted.pendingRandomTimes, restarted.alarm.pendingRandomTimes)
        assertEquals(persisted.currentWindowStart, restarted.alarm.currentWindowStart)
    }

    @Test
    fun startNowBeginsTheFirstWindowAtSaveTime() {
        val plan = ScheduleCalculator(TestClock(), seeded(5)).plan(irregular())
        assertEquals(T0, plan.alarm.currentWindowStart)
        assertEquals(4, plan.alarm.pendingRandomTimes.size)
        assertTrue(plan.alarm.pendingRandomTimes.all { it > T0 && it < T0 + HOUR })
        assertEquals(plan.alarm.pendingRandomTimes.first(), plan.trigger?.at)
    }

    @Test
    fun onlyTheNextTimeIsScheduled() {
        val plan = ScheduleCalculator(TestClock(), seeded(6)).plan(irregular())
        assertNotNull(plan.trigger)
        assertEquals(TriggerKind.REGULAR, plan.trigger!!.kind)
        assertEquals(plan.alarm.pendingRandomTimes.min(), plan.trigger!!.at)
    }

    @Test
    fun windowsMissedWhileOffAreSkipped() {
        val clock = TestClock()
        val calc = ScheduleCalculator(clock, seeded(8))
        val first = calc.plan(irregular()).alarm

        clock.set(T0 + 5 * HOUR + 20 * MIN)
        val plan = calc.plan(first)
        val next = plan.trigger!!.at
        assertTrue(next > clock.nowMs)
        assertTrue("next is in the current or following window", next < T0 + 7 * HOUR)
        assertTrue(plan.alarm.currentWindowStart!! >= T0 + 5 * HOUR)
    }

    @Test
    fun runForStopsIrregularAlarms() {
        val clock = TestClock()
        val calc = ScheduleCalculator(clock, seeded(9))
        val times = calc.collect(clock, irregular(runFor = 2 * HOUR), until = T0 + 10 * HOUR)
        assertTrue(times.all { it <= T0 + 2 * HOUR })
        assertEquals(8, times.size)
    }

    @Test
    fun resumeStartsAFreshWindowNow() {
        val clock = TestClock()
        val calc = ScheduleCalculator(clock, seeded(10))
        val running = calc.plan(irregular()).alarm
        clock.set(T0 + 3 * HOUR + 7 * MIN)
        val resumed = calc.plan(calc.resume(running))
        assertEquals(clock.nowMs, resumed.alarm.currentWindowStart)
        assertTrue(resumed.alarm.pendingRandomTimes.all { it > clock.nowMs && it < clock.nowMs + HOUR })
    }

    @Test
    fun secureRandomSourceStaysInBounds() {
        val source = SecureRandomSource()
        repeat(10_000) {
            val v = source.nextLong(37)
            assertTrue(v in 0 until 37)
        }
    }
}
