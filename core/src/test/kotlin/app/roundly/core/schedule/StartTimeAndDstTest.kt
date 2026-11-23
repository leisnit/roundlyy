package app.roundly.core.schedule

import app.roundly.core.model.Alarm
import app.roundly.core.model.AlarmType
import app.roundly.core.model.StartMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class StartTimeAndDstTest {

    private val newYork = ZoneId.of("America/New_York")
    private val london = ZoneId.of("Europe/London")

    private fun at(text: String): Long = ZonedDateTime.parse(text).toInstant().toEpochMilli()

    @Test
    fun startTimeLaterToday() {
        val now = at("2026-06-10T08:00-04:00[America/New_York]")
        assertEquals(at("2026-06-10T09:30-04:00[America/New_York]"), StartTimes.nextOccurrence(9 * 60 + 30, now, newYork))
    }

    @Test
    fun startTimeAlreadyPassedMovesToTomorrow() {
        val now = at("2026-06-10T10:00-04:00[America/New_York]")
        assertEquals(at("2026-06-11T09:30-04:00[America/New_York]"), StartTimes.nextOccurrence(9 * 60 + 30, now, newYork))
    }

    @Test
    fun startTimeEqualToNowCountsAsPassed() {
        val now = at("2026-06-10T09:30-04:00[America/New_York]")
        assertEquals(at("2026-06-11T09:30-04:00[America/New_York]"), StartTimes.nextOccurrence(9 * 60 + 30, now, newYork))
    }

    @Test
    fun startTimeInsideSpringForwardGapResolvesAfterTheGap() {
        // 2026-03-08: 02:00 EST jumps to 03:00 EDT, so 02:30 does not exist.
        val now = at("2026-03-08T00:00-05:00[America/New_York]")
        assertEquals(at("2026-03-08T03:30-04:00[America/New_York]"), StartTimes.nextOccurrence(2 * 60 + 30, now, newYork))
    }

    @Test
    fun startTimeInFallBackOverlapUsesTheFirstOccurrence() {
        // 2026-11-01: 01:30 happens twice; java.time picks the earlier (EDT) instant.
        val now = at("2026-11-01T00:00-04:00[America/New_York]")
        assertEquals(at("2026-11-01T01:30-04:00[America/New_York]"), StartTimes.nextOccurrence(90, now, newYork))
    }

    @Test
    fun startTimeTomorrowAcrossDstKeepsTheWallClockTime() {
        // Saturday 7 March 09:00 EST passed, Sunday 8 March 09:00 is EDT (23 h later).
        val now = at("2026-03-07T10:00-05:00[America/New_York]")
        val next = StartTimes.nextOccurrence(9 * 60, now, newYork)
        assertEquals(at("2026-03-08T09:00-04:00[America/New_York]"), next)
        assertEquals(9, Instant.ofEpochMilli(next).atZone(newYork).hour)
    }

    @Test
    fun fixedIntervalsAreElapsedTimeAcrossDst() {
        val clock = TestClock(at("2026-03-08T00:10-05:00[America/New_York]"), newYork)
        val calc = ScheduleCalculator(clock, seeded(3))
        val alarm = Alarm(
            id = 1,
            label = "Hourly",
            type = AlarmType.FIXED,
            intervalMs = HOUR,
            startMode = StartMode.AT_TIME,
            anchorAt = at("2026-03-08T00:30-05:00[America/New_York]"),
            createdAt = clock.nowMs,
        )
        val times = calc.collect(clock, alarm, until = at("2026-03-08T05:00-04:00[America/New_York]"))
        times.zipWithNext().forEach { (a, b) -> assertEquals(HOUR, b - a) }
        val localHours = times.map { Instant.ofEpochMilli(it).atZone(newYork).hour }
        assertEquals(listOf(0, 1, 3, 4), localHours) // 02:30 does not exist that night
    }

    @Test
    fun timeZoneChangeDoesNotMoveAbsoluteTriggers() {
        val clock = TestClock(T0, newYork)
        val calc = ScheduleCalculator(clock, seeded(4))
        val alarm = Alarm(id = 1, label = "X", type = AlarmType.FIXED, intervalMs = 45 * MIN, anchorAt = T0, createdAt = T0)
        val before = calc.plan(alarm).trigger
        clock.zoneId = london
        val after = calc.plan(alarm).trigger
        assertEquals(before, after)
    }

    @Test
    fun factoryAnchorsStartAtTimeAndRunForFromTheStart() {
        val now = at("2026-06-10T10:00-04:00[America/New_York]")
        val alarm = AlarmFactory.create(
            AlarmDraft(type = AlarmType.FIXED, intervalMs = 30 * MIN, startMode = StartMode.AT_TIME, startMinuteOfDay = 9 * 60, runForMs = 2 * HOUR),
            now,
            newYork,
        )
        val start = at("2026-06-11T09:00-04:00[America/New_York]")
        assertEquals(start, alarm.anchorAt)
        assertEquals(start + 2 * HOUR, alarm.endsAt)
    }
}
