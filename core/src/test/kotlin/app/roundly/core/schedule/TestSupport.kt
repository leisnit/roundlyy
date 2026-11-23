package app.roundly.core.schedule

import app.roundly.core.model.Alarm
import app.roundly.core.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Random

const val MIN = 60_000L
const val HOUR = 60 * MIN

/** 2026-03-02T09:00:00Z, a Monday with no DST transition nearby. */
val T0: Long = Instant.parse("2026-03-02T09:00:00Z").toEpochMilli()

class TestClock(var nowMs: Long = T0, var zoneId: ZoneId = ZoneOffset.UTC) : Clock {
    override fun now(): Long = nowMs
    override fun zone(): ZoneId = zoneId
    fun set(ms: Long) {
        nowMs = ms
    }
    fun advance(ms: Long) {
        nowMs += ms
    }
}

fun seeded(seed: Long): RandomSource {
    val random = Random(seed)
    return RandomSource { bound -> (random.nextLong() ushr 1) % bound }
}

/** Fires [plan]'s trigger at [lateBy] after its due time and returns the next plan. */
fun ScheduleCalculator.fire(clock: TestClock, plan: Plan, lateBy: Long = 0): Plan {
    val trigger = requireNotNull(plan.trigger) { "expected a trigger" }
    clock.set(trigger.at + lateBy)
    return onFired(plan.alarm, trigger)
}

/** Fires every trigger on time until [until] and returns their times. */
fun ScheduleCalculator.collect(clock: TestClock, alarm: Alarm, until: Long): List<Long> {
    val times = mutableListOf<Long>()
    var plan = plan(alarm)
    while (true) {
        val trigger = plan.trigger ?: break
        if (trigger.at > until) break
        times += trigger.at
        plan = fire(clock, plan)
    }
    return times
}
