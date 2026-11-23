package app.roundly.data.db

import app.roundly.core.model.Alarm
import app.roundly.core.model.AlertLog
import app.roundly.core.model.Preset
import app.roundly.core.model.PresetConfig
import app.roundly.core.model.Step
import app.roundly.core.serialization.RoundlyJson
import app.roundly.data.db.entity.AlarmEntity
import app.roundly.data.db.entity.AlertLogEntity
import app.roundly.data.db.entity.PresetEntity
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

private val json = RoundlyJson.compact
private val stepsSerializer = ListSerializer(Step.serializer())
private val timesSerializer = ListSerializer(Long.serializer())

fun AlarmEntity.toModel(): Alarm = Alarm(
    id = id,
    label = label,
    type = type,
    intervalMs = intervalMs,
    windowMs = windowMs,
    alertsPerWindow = alertsPerWindow,
    minGapMs = minGapMs,
    currentWindowStart = currentWindowStart,
    pendingRandomTimes = pendingRandomTimesJson?.let { json.decodeFromString(timesSerializer, it) }.orEmpty(),
    steps = stepsJson?.let { json.decodeFromString(stepsSerializer, it) }.orEmpty(),
    afterLastStep = afterLastStep,
    stepIndex = stepIndex,
    repeatIndex = repeatIndex,
    stepAnchorAt = stepAnchorAt,
    fireAt = fireAt,
    oneOffDurationMs = oneOffDurationMs,
    startMode = startMode,
    anchorAt = anchorAt,
    runForMs = runForMs,
    endsAt = endsAt,
    enabled = enabled,
    pausedByMaster = pausedByMaster,
    alertMode = alertMode,
    watchOnly = watchOnly,
    ringtoneUri = ringtoneUri,
    snoozeUntil = snoozeUntil,
    snoozedScheduledAt = snoozedScheduledAt,
    nextTriggerAt = nextTriggerAt,
    lastFiredAt = lastFiredAt,
    completedAt = completedAt,
    createdAt = createdAt,
)

fun Alarm.toEntity(): AlarmEntity = AlarmEntity(
    id = id,
    label = label,
    type = type,
    intervalMs = intervalMs,
    windowMs = windowMs,
    alertsPerWindow = alertsPerWindow,
    minGapMs = minGapMs,
    currentWindowStart = currentWindowStart,
    pendingRandomTimesJson = pendingRandomTimes.takeIf { it.isNotEmpty() }
        ?.let { json.encodeToString(timesSerializer, it) },
    stepsJson = steps.takeIf { it.isNotEmpty() }?.let { json.encodeToString(stepsSerializer, it) },
    afterLastStep = afterLastStep,
    stepIndex = stepIndex,
    repeatIndex = repeatIndex,
    stepAnchorAt = stepAnchorAt,
    fireAt = fireAt,
    oneOffDurationMs = oneOffDurationMs,
    startMode = startMode,
    anchorAt = anchorAt,
    runForMs = runForMs,
    endsAt = endsAt,
    enabled = enabled,
    pausedByMaster = pausedByMaster,
    alertMode = alertMode,
    watchOnly = watchOnly,
    ringtoneUri = ringtoneUri,
    snoozeUntil = snoozeUntil,
    snoozedScheduledAt = snoozedScheduledAt,
    nextTriggerAt = nextTriggerAt,
    lastFiredAt = lastFiredAt,
    completedAt = completedAt,
    createdAt = createdAt,
)

fun PresetEntity.toModel(): Preset = Preset(
    id = id,
    name = name,
    type = type,
    config = json.decodeFromString(PresetConfig.serializer(), configJson),
    builtIn = builtIn,
    hidden = hidden,
    builtInKey = builtInKey,
    sortOrder = sortOrder,
)

fun Preset.toEntity(): PresetEntity = PresetEntity(
    id = id,
    name = name,
    type = type,
    configJson = json.encodeToString(PresetConfig.serializer(), config),
    builtIn = builtIn,
    hidden = hidden,
    builtInKey = builtInKey,
    sortOrder = sortOrder,
)

fun AlertLogEntity.toModel(): AlertLog = AlertLog(
    id = id,
    alarmId = alarmId,
    occurrenceId = occurrenceId,
    label = label,
    scheduledAt = scheduledAt,
    outcome = outcome,
    respondedAt = respondedAt,
    respondedOn = respondedOn,
)

fun AlertLog.toEntity(): AlertLogEntity = AlertLogEntity(
    id = id,
    alarmId = alarmId,
    occurrenceId = occurrenceId,
    label = label,
    scheduledAt = scheduledAt,
    outcome = outcome,
    respondedAt = respondedAt,
    respondedOn = respondedOn,
)
