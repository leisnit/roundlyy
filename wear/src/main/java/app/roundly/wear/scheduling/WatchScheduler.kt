package app.roundly.wear.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import app.roundly.core.model.Alarm
import app.roundly.core.model.AlarmLimits
import app.roundly.core.model.AlarmType
import app.roundly.core.model.OccurrenceId
import app.roundly.core.schedule.Plan
import app.roundly.core.schedule.ScheduleCalculator
import app.roundly.core.schedule.Trigger
import app.roundly.core.schedule.TriggerKind
import app.roundly.core.sync.WatchAction
import app.roundly.core.time.Clock
import app.roundly.wear.MainActivity
import app.roundly.wear.R
import app.roundly.wear.WearSurfaces
import app.roundly.wear.alert.AlertOrigin
import app.roundly.wear.alert.WatchAlert
import app.roundly.wear.data.WatchRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Standalone fallback: the watch keeps its own single next trigger per alarm (setAlarmClock),
 * computed with the same ScheduleCalculator as the phone. When a trigger fires while the phone is
 * reachable, the phone's alert message is used instead, so nothing rings twice.
 */
@Singleton
class WatchScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: WatchRepository,
    private val calculator: ScheduleCalculator,
    private val clock: Clock,
) {
    private val alarmManager: AlarmManager = requireNotNull(context.getSystemService())
    private val mutex = Mutex()

    /** Re-arms everything from the current local copy (after a phone sync, boot or clock change). */
    suspend fun rescheduleAll(removedIds: Set<Long> = emptySet()) {
        mutex.withLock {
            removedIds.forEach(::cancel)
            val state = repository.current()
            val updated = state.alarms.map { alarm ->
                if (state.pausedAll || !alarm.isActive) {
                    cancel(alarm.id)
                    alarm
                } else {
                    commit(calculator.plan(alarm))
                }
            }
            repository.update { it.copy(alarms = updated) }
        }
        WearSurfaces.refresh(context)
    }

    /** A local trigger fired. Advances the local copy and returns the alert to show, or null. */
    suspend fun onTriggerFired(alarmId: Long, kind: TriggerKind, at: Long, occurrenceAt: Long): WatchAlert? {
        val alert = mutex.withLock {
            val state = repository.current()
            val alarm = state.alarms.firstOrNull { it.id == alarmId }
            if (alarm == null || state.pausedAll || !alarm.isActive) {
                cancel(alarmId)
                return@withLock null
            }
            if (alarm.nextTriggerAt != at) {
                save(commit(calculator.plan(alarm)))
                return@withLock null
            }
            val progress = if (kind == TriggerKind.REGULAR) calculator.stepProgress(alarm) else null
            save(commit(calculator.onFired(alarm, Trigger(at, kind, occurrenceAt))))
            WatchAlert(
                occurrenceId = OccurrenceId.of(alarm.id, occurrenceAt).value,
                alarmId = alarm.id,
                scheduledAt = occurrenceAt,
                label = alarm.label,
                detail = progress?.let {
                    context.getString(R.string.alert_step_progress, it.stepNumber, it.stepCount, it.alertNumber, it.alertCount)
                },
                alertMode = alarm.alertMode,
                origin = AlertOrigin.LOCAL,
                isSnoozeRepeat = kind == TriggerKind.SNOOZE,
            )
        }
        WearSurfaces.refresh(context)
        return alert
    }

    /** Applies a response to a standalone (local) alert. The phone reconciles it later. */
    suspend fun applyLocalResponse(alarmId: Long, scheduledAt: Long, action: WatchAction) {
        mutex.withLock {
            val state = repository.current()
            val alarm = state.alarms.firstOrNull { it.id == alarmId } ?: return@withLock
            val now = clock.now()
            val plan = when (action) {
                WatchAction.SNOOZE ->
                    calculator.snooze(alarm, scheduledAt, state.snoozeMinutes * AlarmLimits.MINUTE_MS)
                WatchAction.DONE, WatchAction.MISSED -> {
                    val cleared = if (alarm.snoozedScheduledAt == scheduledAt) {
                        alarm.copy(snoozeUntil = null, snoozedScheduledAt = null)
                    } else {
                        alarm
                    }
                    calculator.plan(if (alarm.type == AlarmType.ONE_OFF) cleared.copy(completedAt = now) else cleared)
                }
            }
            save(commit(plan))
        }
        WearSurfaces.refresh(context)
    }

    /** Toggle from the watch UI. The phone applies the same command when it receives it. */
    suspend fun setEnabledLocally(alarmId: Long, enabled: Boolean) {
        mutex.withLock {
            val alarm = repository.current().alarms.firstOrNull { it.id == alarmId } ?: return@withLock
            if (enabled) {
                save(commit(calculator.plan(calculator.resume(alarm.copy(enabled = true)))))
            } else {
                cancel(alarmId)
                save(alarm.copy(enabled = false, nextTriggerAt = null, snoozeUntil = null, snoozedScheduledAt = null))
            }
        }
        WearSurfaces.refresh(context)
    }

    suspend fun setPausedAllLocally(paused: Boolean) {
        repository.update { it.copy(pausedAll = paused) }
        if (paused) {
            mutex.withLock { repository.current().alarms.forEach { cancel(it.id) } }
            WearSurfaces.refresh(context)
        } else {
            mutex.withLock {
                val resumed = repository.current().alarms.map { alarm ->
                    if (alarm.isActive) commit(calculator.plan(calculator.resume(alarm))) else alarm
                }
                repository.update { it.copy(alarms = resumed) }
            }
            WearSurfaces.refresh(context)
        }
    }

    // --- Internals ----------------------------------------------------------------------------

    private suspend fun save(alarm: Alarm) {
        repository.update { state -> state.copy(alarms = state.alarms.map { if (it.id == alarm.id) alarm else it }) }
    }

    /** Arms the plan's trigger. The phone owns "Finished" notices; the watch only stops locally. */
    private fun commit(plan: Plan): Alarm {
        val alarm = plan.alarm
        val trigger = plan.trigger
        val finishAt = plan.finishAt
        return when {
            trigger != null -> {
                arm(alarm.id, trigger)
                alarm
            }
            finishAt != null && finishAt <= clock.now() -> {
                cancel(alarm.id)
                alarm.copy(enabled = false, nextTriggerAt = null)
            }
            else -> {
                cancel(alarm.id)
                alarm
            }
        }
    }

    private fun arm(alarmId: Long, trigger: Trigger) {
        val operation = operation(alarmId, trigger)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (exact) {
            try {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(trigger.at, showIntent()), operation)
                return
            } catch (_: SecurityException) {
                // Fall through to an inexact alarm.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.at, operation)
    }

    private fun cancel(alarmId: Long) {
        alarmManager.cancel(operation(alarmId, Trigger(0, TriggerKind.REGULAR, 0)))
    }

    private fun operation(alarmId: Long, trigger: Trigger): PendingIntent {
        val intent = Intent(context, WearAlarmReceiver::class.java)
            .setAction(WearAlarmReceiver.ACTION_FIRE)
            .setData("roundly-watch://alarm/$alarmId".toUri())
            .putExtra(WearAlarmReceiver.EXTRA_KIND, trigger.kind.name)
            .putExtra(WearAlarmReceiver.EXTRA_AT, trigger.at)
            .putExtra(WearAlarmReceiver.EXTRA_OCCURRENCE_AT, trigger.occurrenceAt)
        return PendingIntent.getBroadcast(
            context,
            (alarmId and 0x7FFF_FFFF).toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
