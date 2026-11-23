package app.roundly.core.sync

import app.roundly.core.model.Alarm
import app.roundly.core.model.AlarmType
import app.roundly.core.model.AlertLog
import app.roundly.core.model.AlertOutcome
import app.roundly.core.model.RespondedOn
import app.roundly.core.schedule.HOUR
import app.roundly.core.schedule.MIN
import app.roundly.core.schedule.ScheduleCalculator
import app.roundly.core.schedule.T0
import app.roundly.core.schedule.TestClock
import app.roundly.core.schedule.TriggerKind
import app.roundly.core.schedule.seeded
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconciliationTest {

    private fun log(outcome: AlertOutcome, on: RespondedOn?) =
        AlertLog(alarmId = 1, occurrenceId = "1@100", label = "", scheduledAt = 100, outcome = outcome, respondedOn = on)

    @Test
    fun firstDoneWins() {
        assertFalse(log(AlertOutcome.DONE, RespondedOn.PHONE).acceptsUpdate(log(AlertOutcome.DONE, RespondedOn.WATCH)))
        assertFalse(log(AlertOutcome.DONE, RespondedOn.WATCH).acceptsUpdate(log(AlertOutcome.SNOOZED, RespondedOn.PHONE)))
    }

    @Test
    fun aRealAnswerReplacesMissed() {
        // Phone rang out in a locker while the watch user tapped Done.
        assertTrue(log(AlertOutcome.MISSED, null).acceptsUpdate(log(AlertOutcome.DONE, RespondedOn.WATCH)))
        assertFalse(log(AlertOutcome.MISSED, null).acceptsUpdate(log(AlertOutcome.MISSED, null)))
    }

    @Test
    fun snoozeCanResolveButIdenticalRepeatsAreIgnored() {
        val snoozed = log(AlertOutcome.SNOOZED, RespondedOn.WATCH)
        assertTrue(snoozed.acceptsUpdate(log(AlertOutcome.DONE, RespondedOn.PHONE)))
        assertTrue(snoozed.acceptsUpdate(log(AlertOutcome.MISSED, null)))
        assertFalse(snoozed.acceptsUpdate(log(AlertOutcome.SNOOZED, RespondedOn.WATCH)))
    }

    @Test
    fun queuedWatchSnoozeThatAlreadyElapsedIsNotApplied() {
        val clock = TestClock(T0 + 2 * HOUR)
        val calc = ScheduleCalculator(clock, seeded(1))
        val alarm = Alarm(id = 1, label = "x", type = AlarmType.FIXED, intervalMs = HOUR, anchorAt = T0, createdAt = T0)
        val plan = calc.snooze(alarm, occurrenceAt = T0 + HOUR, snoozeMs = 5 * MIN, requestedAt = T0 + HOUR + MIN)
        assertNull(plan.alarm.snoozeUntil)
        assertEquals(TriggerKind.REGULAR, plan.trigger?.kind)
    }

    @Test
    fun queuedWatchSnoozeStillRunningKeepsItsOriginalEnd() {
        val clock = TestClock(T0 + HOUR + 2 * MIN)
        val calc = ScheduleCalculator(clock, seeded(1))
        val alarm = Alarm(id = 1, label = "x", type = AlarmType.FIXED, intervalMs = HOUR, anchorAt = T0, createdAt = T0)
        val plan = calc.snooze(alarm, occurrenceAt = T0 + HOUR, snoozeMs = 5 * MIN, requestedAt = T0 + HOUR)
        assertEquals(T0 + HOUR + 5 * MIN, plan.trigger?.at)
    }
}
