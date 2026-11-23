package app.roundly.ui.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview
import app.roundly.core.model.AfterLastStep
import app.roundly.core.model.Alarm
import app.roundly.core.model.AlarmLimits
import app.roundly.core.model.AlarmType
import app.roundly.core.model.AlertMode
import app.roundly.core.model.BuiltInPresets
import app.roundly.core.model.Step
import app.roundly.core.schedule.StepProgress
import app.roundly.ui.dashboard.AlarmItem
import app.roundly.ui.dashboard.DashboardUiState

/** Light and dark previews for every main screen. */
@Preview(name = "Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ThemePreviews

/** Sample content for previews only. */
object PreviewData {
    private const val MIN = AlarmLimits.MINUTE_MS
    private val now = System.currentTimeMillis()

    val water = Alarm(
        id = 1,
        label = "Drink water",
        type = AlarmType.FIXED,
        intervalMs = 30 * MIN,
        anchorAt = now - 10 * MIN,
        nextTriggerAt = now + 20 * MIN,
        runForMs = 4 * 60 * MIN,
        endsAt = now + 3 * 60 * MIN,
        createdAt = now,
    )

    val posture = Alarm(
        id = 2,
        label = "Posture check",
        type = AlarmType.IRREGULAR,
        windowMs = 60 * MIN,
        alertsPerWindow = 4,
        minGapMs = 5 * MIN,
        anchorAt = now,
        nextTriggerAt = now + 7 * MIN,
        alertMode = AlertMode.DISCREET_ESCALATION,
        createdAt = now,
    )

    val warmUp = Alarm(
        id = 3,
        label = "Training block",
        type = AlarmType.STEPPED,
        steps = listOf(Step(15 * MIN, 4), Step(30 * MIN, 2), Step(60 * MIN, 4)),
        afterLastStep = AfterLastStep.STOP,
        stepIndex = 1,
        repeatIndex = 0,
        anchorAt = now,
        nextTriggerAt = now + 42 * MIN,
        enabled = true,
        createdAt = now,
    )

    val tea = Alarm(
        id = 4,
        label = "Tea",
        type = AlarmType.ONE_OFF,
        fireAt = now - 30 * MIN,
        oneOffDurationMs = 4 * MIN,
        anchorAt = now,
        lastFiredAt = now - 30 * MIN,
        completedAt = now - 29 * MIN,
        createdAt = now,
    )

    val dashboard = DashboardUiState(
        loading = false,
        alarms = listOf(
            AlarmItem(water, null),
            AlarmItem(posture, null),
            AlarmItem(warmUp, StepProgress(2, 3, 1, 2, continuing = false)),
        ),
        recentTimers = listOf(tea),
        presets = BuiltInPresets.all,
    )
}
