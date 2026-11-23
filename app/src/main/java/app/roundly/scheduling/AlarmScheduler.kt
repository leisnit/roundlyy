package app.roundly.scheduling

import android.net.Uri
import app.roundly.alert.ActiveAlert
import app.roundly.alert.AlertAction
import app.roundly.core.model.Alarm
import app.roundly.core.model.AlarmLimits
import app.roundly.core.model.AlarmType
import app.roundly.core.model.AlertLog
import app.roundly.core.model.AlertOutcome
import app.roundly.core.model.OccurrenceId
import app.roundly.core.model.RespondedOn
import app.roundly.core.model.StartMode
import app.roundly.core.schedule.AlarmDraft
import app.roundly.core.schedule.AlarmFactory
import app.roundly.core.schedule.FinishReason
import app.roundly.core.schedule.Plan
import app.roundly.core.schedule.ScheduleCalculator
import app.roundly.core.schedule.Trigger
import app.roundly.core.schedule.TriggerKind
import app.roundly.core.time.Clock
import app.roundly.data.Housekeeping
import app.roundly.data.backup.BackupManager
import app.roundly.data.backup.ImportSummary
import app.roundly.data.repository.AlarmRepository
import app.roundly.data.repository.AlertLogRepository
import app.roundly.data.repository.SettingsRepository
import app.roundly.notifications.AlertNotifications
import app.roundly.watch.WatchBridge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single entry point for every change that affects when alarms fire. Each operation:
 * computes a [Plan] with the pure [ScheduleCalculator], persists the new state, then arms exactly
 * one AlarmManager trigger per alarm (or none). Nothing runs in the background between triggers.
 *
 * All state changes run under one mutex, so a trigger firing while the user edits is safe.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    private val alarms: AlarmRepository,
    private val alertLog: AlertLogRepository,
    private val settings: SettingsRepository,
    private val calculator: ScheduleCalculator,
    private val triggers: TriggerScheduler,
    private val notifications: AlertNotifications,
    private val watch: WatchBridge,
    private val housekeeping: Housekeeping,
    private val backup: BackupManager,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    // --- Editing ------------------------------------------------------------------------------

    /** Creates or replaces an alarm from an editor draft (scheduling state starts fresh). Returns its id. */
    suspend fun save(draft: AlarmDraft): Long {
        val id = mutex.withLock {
            val existing = if (draft.id != 0L) alarms.getAlarm(draft.id) else null
            var alarm = AlarmFactory.create(
                draft.copy(createdAt = existing?.createdAt ?: draft.createdAt),
                clock.now(),
                clock.zone(),
            )
            if (alarm.enabled && settings.currentState().pausedAll) alarm = alarm.copy(pausedByMaster = true)
            val id = alarms.save(alarm)
            commit(calculator.plan(alarm.copy(id = id)))
            id
        }
        watch.syncSchedule()
        return id
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        mutex.withLock {
            val alarm = alarms.getAlarm(id) ?: return
            if (enabled) {
                commit(calculator.plan(prepareForEnable(alarm)))
            } else {
                triggers.cancel(id)
                alarms.save(alarm.copy(enabled = false, snoozeUntil = null, snoozedScheduledAt = null, nextTriggerAt = null))
            }
        }
        watch.syncSchedule()
    }

    /** Deletes an alarm and returns it for Undo. */
    suspend fun delete(id: Long): Alarm? {
        val deleted = mutex.withLock {
            triggers.cancel(id)
            alarms.delete(id)
        }
        watch.syncSchedule()
        return deleted
    }

    /** Undo for [delete]: restores the alarm with its original id and recalculates from now. */
    suspend fun restore(alarm: Alarm) {
        mutex.withLock {
            alarms.restore(alarm)
            commit(calculator.plan(alarm))
        }
        watch.syncSchedule()
    }

    /** Copies an alarm as a new one that starts fresh now. Returns the new id. */
    suspend fun duplicate(id: Long, copySuffix: String): Long? {
        val source = alarms.getAlarm(id) ?: return null
        val draft = AlarmDraft.fromAlarm(source, clock.zone()).copy(
            id = 0,
            label = "${source.label} $copySuffix".trim().take(AlarmLimits.MAX_LABEL_LENGTH),
            enabled = true,
            createdAt = null,
        )
        return save(draft)
    }

    /** One tap on a recent timer: same duration from now (or the same clock time next). */
    suspend fun restartTimer(id: Long) {
        mutex.withLock {
            val alarm = alarms.getAlarm(id)?.takeIf { it.type == AlarmType.ONE_OFF } ?: return
            var restarted = AlarmFactory.restartTimer(alarm, clock.now(), clock.zone())
            if (settings.currentState().pausedAll) restarted = restarted.copy(pausedByMaster = true)
            commit(calculator.plan(restarted))
        }
        watch.syncSchedule()
    }

    // --- Session controls ---------------------------------------------------------------------

    /** Pause all: cancels every pending trigger and remembers which alarms were running. */
    suspend fun pauseAll() {
        mutex.withLock {
            alarms.getActiveAlarms().forEach { triggers.cancel(it.id) }
            alarms.markAllPaused()
            settings.setPausedAll(true)
        }
        watch.syncSchedule()
    }

    /** Resume all: recalculates from now with no catch-up bursts; stepped alarms keep their step. */
    suspend fun resumeAll() {
        mutex.withLock {
            settings.setPausedAll(false)
            alarms.getAlarms().filter { it.pausedByMaster }.forEach { paused ->
                commit(calculator.plan(prepareForEnable(paused.copy(pausedByMaster = false))))
            }
        }
        watch.syncSchedule()
    }

    /** Re-arms everything: boot, app update, clock or time-zone change, exact-alarm permission change. */
    suspend fun rescheduleAll() {
        mutex.withLock { rescheduleAllLocked() }
        watch.syncSchedule()
    }

    /** Export / Import. Pending triggers of the old alarms are cancelled before they are replaced. */
    suspend fun importBackup(uri: Uri): Result<ImportSummary> {
        val result = mutex.withLock {
            alarms.getAlarms().forEach { triggers.cancel(it.id) }
            val imported = backup.import(uri)
            rescheduleAllLocked()
            imported
        }
        watch.syncSchedule()
        return result
    }

    // --- Triggers -----------------------------------------------------------------------------

    /**
     * A trigger fired. Consumes the occurrence, arms the next trigger immediately (so the cadence
     * keeps going while this alert rings) and returns the alert to present, or null if stale.
     */
    suspend fun onTriggerFired(alarmId: Long, kind: TriggerKind, at: Long, occurrenceAt: Long): ActiveAlert? {
        val alert = mutex.withLock {
            val alarm = alarms.getAlarm(alarmId)
            if (alarm == null) {
                triggers.cancel(alarmId)
                return@withLock null
            }
            if (!alarm.isActive) return@withLock null
            if (alarm.nextTriggerAt != at) {
                // Stale delivery (the alarm was edited or rescheduled). Re-arm what is current.
                commit(calculator.plan(alarm))
                return@withLock null
            }
            val progress = if (kind == TriggerKind.REGULAR) calculator.stepProgress(alarm) else null
            commit(calculator.onFired(alarm, Trigger(at, kind, occurrenceAt)))
            ActiveAlert(
                alarmId = alarm.id,
                occurrenceId = OccurrenceId.of(alarm.id, occurrenceAt).value,
                scheduledAt = occurrenceAt,
                label = alarm.label,
                type = alarm.type,
                alertMode = alarm.alertMode,
                ringtoneUri = alarm.ringtoneUri,
                watchOnly = alarm.watchOnly,
                stepProgress = progress,
                isSnoozeRepeat = kind == TriggerKind.SNOOZE,
                firedAt = clock.now(),
            )
        }
        housekeeping.run()
        watch.syncSchedule()
        return alert
    }

    /** The run-for end (or a completed stepped schedule) came due. */
    suspend fun onFinishDue(alarmId: Long) {
        mutex.withLock {
            val alarm = alarms.getAlarm(alarmId) ?: return
            commit(calculator.plan(alarm))
        }
        watch.syncSchedule()
    }

    // --- Responses ----------------------------------------------------------------------------

    /**
     * Applies Done or Snooze for an occurrence, from the phone or the watch. The first response
     * wins (see AlertLogRepository.record); a later duplicate returns false and changes nothing.
     *  - Done keeps the cadence (it already advanced when the alert fired); a timer becomes a
     *    recent timer.
     *  - Snooze sets snoozeUntil without moving the schedule; it is dropped if it would collide.
     */
    suspend fun respond(
        alarmId: Long,
        scheduledAt: Long,
        label: String,
        action: AlertAction,
        on: RespondedOn,
        respondedAt: Long = clock.now(),
    ): Boolean {
        val now = clock.now()
        val recorded = alertLog.record(
            AlertLog(
                alarmId = alarmId,
                occurrenceId = OccurrenceId.of(alarmId, scheduledAt).value,
                label = label.ifBlank { alarms.getAlarm(alarmId)?.label.orEmpty() },
                scheduledAt = scheduledAt,
                outcome = if (action == AlertAction.DONE) AlertOutcome.DONE else AlertOutcome.SNOOZED,
                respondedAt = respondedAt,
                respondedOn = on,
            ),
        )
        if (!recorded) return false

        mutex.withLock {
            val alarm = alarms.getAlarm(alarmId) ?: return@withLock
            when (action) {
                AlertAction.DONE -> {
                    val cleared = if (alarm.snoozedScheduledAt == scheduledAt) {
                        alarm.copy(snoozeUntil = null, snoozedScheduledAt = null)
                    } else {
                        alarm
                    }
                    val done = if (alarm.type == AlarmType.ONE_OFF) cleared.copy(completedAt = now) else cleared
                    commit(calculator.plan(done))
                }
                AlertAction.SNOOZE -> {
                    val snoozeMs = settings.current().snoozeMinutes * AlarmLimits.MINUTE_MS
                    commit(calculator.snooze(alarm, scheduledAt, snoozeMs, requestedAt = respondedAt))
                }
            }
        }
        watch.syncSchedule()
        return true
    }

    /** Ring timeout: logged as Missed; the schedule keeps running. A missed timer becomes recent. */
    suspend fun onMissed(alert: ActiveAlert) = onMissed(alert.alarmId, alert.scheduledAt, alert.label)

    /** Missed on this phone or (for a standalone watch alert) on the watch. */
    suspend fun onMissed(alarmId: Long, scheduledAt: Long, label: String) {
        val recorded = alertLog.record(
            AlertLog(
                alarmId = alarmId,
                occurrenceId = OccurrenceId.of(alarmId, scheduledAt).value,
                label = label.ifBlank { alarms.getAlarm(alarmId)?.label.orEmpty() },
                scheduledAt = scheduledAt,
                outcome = AlertOutcome.MISSED,
            ),
        )
        if (!recorded) return
        mutex.withLock {
            val alarm = alarms.getAlarm(alarmId) ?: return@withLock
            if (alarm.type == AlarmType.ONE_OFF && alarm.snoozeUntil == null) {
                commit(calculator.plan(alarm.copy(completedAt = clock.now())))
            }
        }
        watch.syncSchedule()
    }

    // --- Internals ----------------------------------------------------------------------------

    private suspend fun rescheduleAllLocked() {
        alarms.getAlarms().forEach { alarm ->
            if (alarm.isActive) {
                commit(calculator.plan(alarm))
            } else {
                triggers.cancel(alarm.id)
                if (alarm.nextTriggerAt != null) alarms.setNextTrigger(alarm.id, null)
            }
        }
    }

    /**
     * Switching an alarm back on (or Resume all) recalculates from now. A run that already ended
     * or a completed stepped schedule starts over; a stale timer runs its duration again.
     */
    private suspend fun prepareForEnable(alarm: Alarm): Alarm {
        val now = clock.now()
        var prepared = calculator.resume(alarm.copy(enabled = true))

        val endsAt = prepared.endsAt
        val runFor = prepared.runForMs
        if (endsAt != null && endsAt <= now && runFor != null) {
            prepared = prepared.copy(endsAt = now + runFor)
        }
        if (prepared.type == AlarmType.ONE_OFF) {
            val fireAt = prepared.fireAt
            if (prepared.completedAt != null || prepared.lastFiredAt != null || (fireAt != null && fireAt <= now)) {
                prepared = AlarmFactory.restartTimer(prepared, now, clock.zone())
            }
        }
        if (prepared.type == AlarmType.STEPPED &&
            calculator.plan(prepared).finishReason == FinishReason.SCHEDULE_COMPLETED
        ) {
            prepared = prepared.copy(
                stepIndex = 0,
                repeatIndex = 0,
                stepAnchorAt = null,
                anchorAt = now,
                startMode = StartMode.NOW,
                endsAt = runFor?.let { now + it },
            )
        }
        if (settings.currentState().pausedAll) prepared = prepared.copy(pausedByMaster = true)
        return prepared
    }

    /** Persists the plan's state and arms its single trigger (or the finish wake-up). */
    private suspend fun commit(plan: Plan) {
        var alarm = plan.alarm
        val trigger = plan.trigger
        val finishAt = plan.finishAt
        when {
            trigger != null -> triggers.arm(alarm.id, trigger)
            finishAt != null && finishAt <= clock.now() -> {
                triggers.cancel(alarm.id)
                alarm = alarm.copy(enabled = false, snoozeUntil = null, snoozedScheduledAt = null, nextTriggerAt = null)
                notifications.postFinished(alarm, plan.finishReason ?: FinishReason.RUN_FOR_ENDED)
            }
            finishAt != null -> triggers.armFinish(alarm.id, finishAt)
            else -> triggers.cancel(alarm.id)
        }
        alarms.save(alarm)
    }
}
