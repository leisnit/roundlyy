package app.roundly.ui.format

import android.text.format.DateFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Stairs
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.roundly.R
import app.roundly.core.model.Alarm
import app.roundly.core.model.AlarmLimits
import app.roundly.core.model.AlarmType
import app.roundly.core.model.AlertMode
import app.roundly.core.model.ThemeMode
import app.roundly.core.model.TimeFormat
import app.roundly.core.schedule.ScheduleProblem
import app.roundly.core.schedule.StepProgress
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/** The user's 12/24-hour preference, provided at the root of each activity. */
val LocalTimeFormat = staticCompositionLocalOf { TimeFormat.SYSTEM }

/** Current time, ticking every [intervalMs] (aligned to the interval) while in composition. */
@Composable
fun rememberNow(intervalMs: Long = 1_000L): Long {
    val now by produceState(System.currentTimeMillis(), intervalMs) {
        while (true) {
            val t = System.currentTimeMillis()
            value = t
            delay(intervalMs - t % intervalMs)
        }
    }
    return now
}

@Composable
fun is24Hour(): Boolean {
    val context = LocalContext.current
    return when (LocalTimeFormat.current) {
        TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
        TimeFormat.H12 -> false
        TimeFormat.H24 -> true
    }
}

/** "12:40" or "12:40 PM", per the user's setting and locale. */
@Composable
fun formatClock(epochMs: Long): String {
    val is24 = is24Hour()
    val formatter = remember(is24) {
        val locale = Locale.getDefault()
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24) "Hm" else "hm"), locale)
    }
    return formatter.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
}

/** "45 min", "1 h", "1 h 30 min", "2 d 3 h". */
@Composable
fun durationText(ms: Long): String {
    val totalMinutes = (ms / AlarmLimits.MINUTE_MS).coerceAtLeast(0)
    val days = totalMinutes / (24 * 60)
    val hours = totalMinutes % (24 * 60) / 60
    val minutes = totalMinutes % 60
    return when {
        days > 0 && hours > 0 -> stringResource(R.string.duration_d_h, days, hours)
        days > 0 -> stringResource(R.string.duration_d, days)
        hours > 0 && minutes > 0 -> stringResource(R.string.duration_h_min, hours, minutes)
        hours > 0 -> stringResource(R.string.duration_h, hours)
        else -> stringResource(R.string.duration_min, minutes)
    }
}

/** "in 12 min" (rounded up so it never reads "in 0 min"). */
@Composable
fun relativeText(target: Long, now: Long): String {
    val diff = target - now
    if (diff < AlarmLimits.MINUTE_MS) return stringResource(R.string.relative_under_minute)
    val minutes = ceil(diff / AlarmLimits.MINUTE_MS.toDouble()).toLong()
    return stringResource(R.string.relative_in, durationText(minutes * AlarmLimits.MINUTE_MS))
}

/** Large countdown digits: "4:05" or "1:04:05". Plain digits, rendered with tabular figures. */
fun countdownText(remainingMs: Long): String {
    val totalSeconds = ((remainingMs + 999) / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = totalSeconds % 3600 / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", m, s)
    }
}

@Composable
fun alarmDisplayLabel(alarm: Alarm): String =
    alarm.label.ifBlank { typeLabel(alarm.type) }

/** "Every 30 min", "4x per hour, irregular", "Stepped · 2 of 3", "Timer · 12:40". */
@Composable
fun alarmSummary(alarm: Alarm, progress: StepProgress?): String = when (alarm.type) {
    AlarmType.FIXED -> stringResource(R.string.summary_fixed, durationText(alarm.intervalMs ?: 0))
    AlarmType.IRREGULAR -> {
        val alerts = alarm.alertsPerWindow ?: 0
        if (alarm.windowMs == AlarmLimits.HOUR_MS) {
            stringResource(R.string.summary_irregular_hour, alerts)
        } else {
            stringResource(R.string.summary_irregular, alerts, durationText(alarm.windowMs ?: 0))
        }
    }
    AlarmType.STEPPED -> stringResource(
        R.string.summary_stepped,
        progress?.stepNumber ?: 1,
        progress?.stepCount ?: alarm.steps.size,
    )
    AlarmType.ONE_OFF -> stringResource(R.string.summary_timer, alarm.fireAt?.let { formatClock(it) } ?: "")
}

@Composable
fun stepProgressText(progress: StepProgress): String = if (progress.continuing) {
    stringResource(R.string.alert_step_progress_continuing, progress.stepNumber, progress.stepCount, progress.alertNumber)
} else {
    stringResource(R.string.alert_step_progress, progress.stepNumber, progress.stepCount, progress.alertNumber, progress.alertCount)
}

@Composable
fun typeLabel(type: AlarmType): String = stringResource(
    when (type) {
        AlarmType.FIXED -> R.string.type_fixed
        AlarmType.IRREGULAR -> R.string.type_irregular
        AlarmType.STEPPED -> R.string.type_stepped
        AlarmType.ONE_OFF -> R.string.type_timer
    },
)

fun typeIcon(type: AlarmType): ImageVector = when (type) {
    AlarmType.FIXED -> Icons.Outlined.Repeat
    AlarmType.IRREGULAR -> Icons.Outlined.Shuffle
    AlarmType.STEPPED -> Icons.Outlined.Stairs
    AlarmType.ONE_OFF -> Icons.Outlined.Timer
}

@Composable
fun alertModeLabel(mode: AlertMode): String = stringResource(
    when (mode) {
        AlertMode.SOUND_AND_VIBRATION -> R.string.mode_sound_vibration
        AlertMode.SOUND_ONLY -> R.string.mode_sound
        AlertMode.VIBRATE_ONLY -> R.string.mode_vibrate
        AlertMode.DISCREET_ESCALATION -> R.string.mode_discreet
        AlertMode.SILENT_BANNER -> R.string.mode_silent
    },
)

@Composable
fun alertModeDescription(mode: AlertMode): String = stringResource(
    when (mode) {
        AlertMode.SOUND_AND_VIBRATION -> R.string.mode_sound_vibration_desc
        AlertMode.SOUND_ONLY -> R.string.mode_sound_desc
        AlertMode.VIBRATE_ONLY -> R.string.mode_vibrate_desc
        AlertMode.DISCREET_ESCALATION -> R.string.mode_discreet_desc
        AlertMode.SILENT_BANNER -> R.string.mode_silent_desc
    },
)

@Composable
fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.TRUE_DARK -> R.string.theme_true_dark
    },
)

/** A clear, actionable sentence for each validation problem. */
@Composable
fun problemText(problem: ScheduleProblem): String = when (problem) {
    ScheduleProblem.LabelTooLong -> stringResource(R.string.problem_label_too_long, AlarmLimits.MAX_LABEL_LENGTH)
    ScheduleProblem.IntervalOutOfRange -> stringResource(R.string.problem_interval_range)
    ScheduleProblem.WindowOutOfRange -> stringResource(R.string.problem_window_range)
    ScheduleProblem.AlertsPerWindowOutOfRange -> stringResource(R.string.problem_alerts_range)
    ScheduleProblem.NegativeGap -> stringResource(R.string.problem_negative_gap)
    is ScheduleProblem.IrregularImpossible -> stringResource(
        R.string.problem_irregular_impossible,
        problem.alerts,
        durationText(problem.minGapMs),
        durationText(problem.windowMs),
        durationText(problem.minimumWindowMs - 1),
    )
    ScheduleProblem.NoSteps -> stringResource(R.string.problem_no_steps)
    ScheduleProblem.TooManySteps -> stringResource(R.string.problem_too_many_steps, AlarmLimits.MAX_STEPS)
    is ScheduleProblem.StepIntervalOutOfRange -> stringResource(R.string.problem_step_interval, problem.index + 1)
    is ScheduleProblem.StepRepeatsOutOfRange -> stringResource(R.string.problem_step_repeats, problem.index + 1)
    ScheduleProblem.OneOffMissingTime -> stringResource(R.string.problem_timer_missing)
    ScheduleProblem.OneOffTooLong -> stringResource(R.string.problem_timer_too_long)
    ScheduleProblem.RunForTooShort -> stringResource(R.string.problem_run_for_short)
}
