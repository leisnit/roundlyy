package app.roundly.core.schedule

import app.roundly.core.model.AfterLastStep
import app.roundly.core.model.Alarm
import app.roundly.core.model.AlarmLimits
import app.roundly.core.model.AlarmType
import app.roundly.core.model.AlertMode
import app.roundly.core.model.PresetConfig
import app.roundly.core.model.StartMode
import app.roundly.core.model.Step
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@Serializable
enum class OneOffMode {
    /** Fires after a duration (quick chips: 15 min, 30 min, 1 h, 2 h, custom). */
    COUNTDOWN,

    /** Fires at the next occurrence of a clock time. */
    CLOCK_TIME,
}

/**
 * What the editor produces. [AlarmFactory] turns it into an [Alarm] with fresh scheduling state.
 * Times of day are minutes since midnight in the device zone.
 */
@Serializable
data class AlarmDraft(
    val id: Long = 0,
    val label: String = "",
    val type: AlarmType = AlarmType.FIXED,
    val intervalMs: Long? = 30 * AlarmLimits.MINUTE_MS,
    val windowMs: Long? = AlarmLimits.HOUR_MS,
    val alertsPerWindow: Int? = 4,
    val minGapMs: Long? = AlarmLimits.DEFAULT_MIN_GAP_MS,
    val steps: List<Step> = listOf(Step(15 * AlarmLimits.MINUTE_MS, 4)),
    val afterLastStep: AfterLastStep = AfterLastStep.STOP,
    val oneOffMode: OneOffMode = OneOffMode.COUNTDOWN,
    val oneOffDurationMs: Long? = 30 * AlarmLimits.MINUTE_MS,
    val oneOffMinuteOfDay: Int? = null,
    val startMode: StartMode = StartMode.NOW,
    val startMinuteOfDay: Int? = null,
    val runForMs: Long? = null,
    val alertMode: AlertMode = AlertMode.SOUND_AND_VIBRATION,
    val watchOnly: Boolean = false,
    val ringtoneUri: String? = null,
    val enabled: Boolean = true,
    val createdAt: Long? = null,
) {
    companion object {
        fun fromAlarm(alarm: Alarm, zone: ZoneId): AlarmDraft = AlarmDraft(
            id = alarm.id,
            label = alarm.label,
            type = alarm.type,
            intervalMs = alarm.intervalMs ?: 30 * AlarmLimits.MINUTE_MS,
            windowMs = alarm.windowMs ?: AlarmLimits.HOUR_MS,
            alertsPerWindow = alarm.alertsPerWindow ?: 4,
            minGapMs = alarm.minGapMs ?: AlarmLimits.DEFAULT_MIN_GAP_MS,
            steps = alarm.steps.ifEmpty { listOf(Step(15 * AlarmLimits.MINUTE_MS, 4)) },
            afterLastStep = alarm.afterLastStep ?: AfterLastStep.STOP,
            oneOffMode = if (alarm.type == AlarmType.ONE_OFF && alarm.oneOffDurationMs == null) {
                OneOffMode.CLOCK_TIME
            } else {
                OneOffMode.COUNTDOWN
            },
            oneOffDurationMs = alarm.oneOffDurationMs ?: 30 * AlarmLimits.MINUTE_MS,
            oneOffMinuteOfDay = alarm.fireAt?.let { minuteOfDay(it, zone) },
            startMode = alarm.startMode,
            startMinuteOfDay = if (alarm.startMode == StartMode.AT_TIME) minuteOfDay(alarm.anchorAt, zone) else null,
            runForMs = alarm.runForMs,
            alertMode = alarm.alertMode,
            watchOnly = alarm.watchOnly,
            ringtoneUri = alarm.ringtoneUri,
            enabled = alarm.enabled,
            createdAt = alarm.createdAt,
        )

        fun fromPreset(type: AlarmType, config: PresetConfig, defaultAlertMode: AlertMode): AlarmDraft {
            val base = AlarmDraft(type = type, alertMode = config.alertMode ?: defaultAlertMode)
            return base.copy(
                label = config.label.orEmpty(),
                intervalMs = config.intervalMs ?: base.intervalMs,
                windowMs = config.windowMs ?: base.windowMs,
                alertsPerWindow = config.alertsPerWindow ?: base.alertsPerWindow,
                minGapMs = config.minGapMs ?: base.minGapMs,
                steps = config.steps.ifEmpty { base.steps },
                afterLastStep = config.afterLastStep ?: base.afterLastStep,
                oneOffDurationMs = config.oneOffDurationMs ?: base.oneOffDurationMs,
                runForMs = config.runForMs,
            )
        }

        private fun minuteOfDay(epochMs: Long, zone: ZoneId): Int {
            val t = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime()
            return t.hour * 60 + t.minute
        }
    }

    fun toPresetConfig(): PresetConfig = PresetConfig(
        label = label.ifBlank { null },
        intervalMs = intervalMs.takeIf { type == AlarmType.FIXED },
        windowMs = windowMs.takeIf { type == AlarmType.IRREGULAR },
        alertsPerWindow = alertsPerWindow.takeIf { type == AlarmType.IRREGULAR },
        minGapMs = minGapMs.takeIf { type == AlarmType.IRREGULAR },
        steps = if (type == AlarmType.STEPPED) steps else emptyList(),
        afterLastStep = afterLastStep.takeIf { type == AlarmType.STEPPED },
        oneOffDurationMs = oneOffDurationMs.takeIf { type == AlarmType.ONE_OFF },
        alertMode = alertMode,
        runForMs = runForMs,
    )
}

/** Clock-time helpers that are correct across DST transitions. */
object StartTimes {
    /**
     * Next occurrence of [minuteOfDay] strictly after [now]: today, or tomorrow if it has passed.
     * A time inside a DST gap (for example 02:30 on spring-forward day) resolves to the
     * equivalent instant just after the gap, as java.time does.
     */
    fun nextOccurrence(minuteOfDay: Int, now: Long, zone: ZoneId): Long {
        val time = LocalTime.of(minuteOfDay / 60 % 24, minuteOfDay % 60)
        val nowInstant = Instant.ofEpochMilli(now)
        val today = nowInstant.atZone(zone).toLocalDate()
        var candidate = ZonedDateTime.of(today, time, zone)
        if (!candidate.toInstant().isAfter(nowInstant)) {
            candidate = ZonedDateTime.of(today.plusDays(1), time, zone)
        }
        return candidate.toInstant().toEpochMilli()
    }
}

/** Builds alarms with fresh scheduling state from editor drafts. Pure and deterministic. */
object AlarmFactory {

    fun create(draft: AlarmDraft, now: Long, zone: ZoneId): Alarm {
        val anchor = when {
            draft.type == AlarmType.ONE_OFF -> now
            draft.startMode == StartMode.AT_TIME && draft.startMinuteOfDay != null ->
                StartTimes.nextOccurrence(draft.startMinuteOfDay, now, zone)
            else -> now
        }
        val startMode = if (draft.type != AlarmType.ONE_OFF && draft.startMode == StartMode.AT_TIME &&
            draft.startMinuteOfDay != null
        ) {
            StartMode.AT_TIME
        } else {
            StartMode.NOW
        }

        val fireAt = if (draft.type == AlarmType.ONE_OFF) oneOffFireAt(draft, now, zone) else null
        val runFor = draft.runForMs.takeIf { draft.type != AlarmType.ONE_OFF }

        return Alarm(
            id = draft.id,
            label = draft.label.trim().take(AlarmLimits.MAX_LABEL_LENGTH),
            type = draft.type,
            intervalMs = draft.intervalMs.takeIf { draft.type == AlarmType.FIXED },
            windowMs = draft.windowMs.takeIf { draft.type == AlarmType.IRREGULAR },
            alertsPerWindow = draft.alertsPerWindow.takeIf { draft.type == AlarmType.IRREGULAR },
            minGapMs = (draft.minGapMs ?: AlarmLimits.DEFAULT_MIN_GAP_MS).takeIf { draft.type == AlarmType.IRREGULAR },
            steps = if (draft.type == AlarmType.STEPPED) draft.steps else emptyList(),
            afterLastStep = draft.afterLastStep.takeIf { draft.type == AlarmType.STEPPED },
            fireAt = fireAt,
            oneOffDurationMs = draft.oneOffDurationMs.takeIf {
                draft.type == AlarmType.ONE_OFF && draft.oneOffMode == OneOffMode.COUNTDOWN
            },
            startMode = startMode,
            anchorAt = anchor,
            runForMs = runFor,
            endsAt = runFor?.let { anchor + it },
            enabled = draft.enabled,
            alertMode = draft.alertMode,
            watchOnly = draft.watchOnly,
            ringtoneUri = draft.ringtoneUri,
            createdAt = draft.createdAt ?: now,
        )
    }

    /** One-tap restart of a recent timer: same duration from now, or the same clock time next. */
    fun restartTimer(alarm: Alarm, now: Long, zone: ZoneId): Alarm {
        val duration = alarm.oneOffDurationMs
        val fireAt = if (duration != null) {
            now + duration
        } else {
            val previous = Instant.ofEpochMilli(alarm.fireAt ?: now).atZone(zone).toLocalTime()
            StartTimes.nextOccurrence(previous.hour * 60 + previous.minute, now, zone)
        }
        return alarm.copy(
            fireAt = fireAt,
            anchorAt = now,
            enabled = true,
            completedAt = null,
            lastFiredAt = null,
            snoozeUntil = null,
            snoozedScheduledAt = null,
            nextTriggerAt = null,
        )
    }

    private fun oneOffFireAt(draft: AlarmDraft, now: Long, zone: ZoneId): Long = when (draft.oneOffMode) {
        OneOffMode.COUNTDOWN -> now + (draft.oneOffDurationMs ?: AlarmLimits.MINUTE_MS)
        OneOffMode.CLOCK_TIME -> StartTimes.nextOccurrence(draft.oneOffMinuteOfDay ?: 0, now, zone)
    }
}
