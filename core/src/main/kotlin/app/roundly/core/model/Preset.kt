package app.roundly.core.model

import kotlinx.serialization.Serializable

/**
 * A saved starting point for the editor. Tapping a preset opens the editor pre-filled.
 * Built-in presets can be hidden but never deleted; custom presets can be renamed and deleted.
 */
@Serializable
data class Preset(
    val id: Long = 0,
    val name: String,
    val type: AlarmType,
    val config: PresetConfig,
    val builtIn: Boolean = false,
    val hidden: Boolean = false,
    /** Stable key for built-ins, so they are seeded once and survive renames of the display text. */
    val builtInKey: String? = null,
    val sortOrder: Int = 0,
)

/** The alarm template stored in a preset (PresetEntity.config, as JSON). */
@Serializable
data class PresetConfig(
    val label: String? = null,
    val intervalMs: Long? = null,
    val windowMs: Long? = null,
    val alertsPerWindow: Int? = null,
    val minGapMs: Long? = null,
    val steps: List<Step> = emptyList(),
    val afterLastStep: AfterLastStep? = null,
    val oneOffDurationMs: Long? = null,
    val alertMode: AlertMode? = null,
    val runForMs: Long? = null,
) {
    companion object {
        /** Builds a preset template from an existing alarm (used by "Save as preset"). */
        fun from(alarm: Alarm): PresetConfig = PresetConfig(
            label = alarm.label,
            intervalMs = alarm.intervalMs,
            windowMs = alarm.windowMs,
            alertsPerWindow = alarm.alertsPerWindow,
            minGapMs = alarm.minGapMs,
            steps = alarm.steps,
            afterLastStep = alarm.afterLastStep,
            oneOffDurationMs = alarm.oneOffDurationMs,
            alertMode = alarm.alertMode,
            runForMs = alarm.runForMs,
        )
    }
}

/** The five built-in fixed-interval presets: 15 min, 30 min, 1 h, 2 h, 4 h. */
object BuiltInPresets {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE

    val all: List<Preset> = listOf(
        builtIn("fixed_15m", "15 min", 15 * MINUTE, 0),
        builtIn("fixed_30m", "30 min", 30 * MINUTE, 1),
        builtIn("fixed_1h", "1 h", HOUR, 2),
        builtIn("fixed_2h", "2 h", 2 * HOUR, 3),
        builtIn("fixed_4h", "4 h", 4 * HOUR, 4),
    )

    private fun builtIn(key: String, name: String, intervalMs: Long, order: Int) = Preset(
        name = name,
        type = AlarmType.FIXED,
        config = PresetConfig(intervalMs = intervalMs),
        builtIn = true,
        builtInKey = key,
        sortOrder = order,
    )
}
