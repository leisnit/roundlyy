package app.roundly.core.model

import kotlinx.serialization.Serializable

/** 12/24-hour preference. [SYSTEM] follows the device setting (the default). */
@Serializable
enum class TimeFormat {
    SYSTEM,
    H12,
    H24,
}

/** User preferences. Included in Export / Import. */
@Serializable
data class AppSettings(
    val defaultAlertMode: AlertMode = AlertMode.SOUND_AND_VIBRATION,
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    val ringTimeoutMinutes: Int = DEFAULT_RING_TIMEOUT_MINUTES,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
) {
    companion object {
        const val DEFAULT_SNOOZE_MINUTES = 5
        const val DEFAULT_RING_TIMEOUT_MINUTES = 3
        val SNOOZE_RANGE = 1..30
        val RING_TIMEOUT_RANGE = 1..10
    }

    /** Clamps values (for example from a hand-edited import file) into supported ranges. */
    fun sanitized(): AppSettings = copy(
        snoozeMinutes = snoozeMinutes.coerceIn(SNOOZE_RANGE),
        ringTimeoutMinutes = ringTimeoutMinutes.coerceIn(RING_TIMEOUT_RANGE),
    )
}

/** Device-local app state. Not exported. */
data class AppState(
    val onboardingCompleted: Boolean = false,
    /** Pause all is active: pending triggers are cancelled until Resume all. */
    val pausedAll: Boolean = false,
)
