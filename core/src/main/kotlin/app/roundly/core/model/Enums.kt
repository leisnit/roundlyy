package app.roundly.core.model

import kotlinx.serialization.Serializable

/** The four kinds of alarm Roundly supports. */
@Serializable
enum class AlarmType {
    /** Repeats every fixed interval, anchored so it never drifts. */
    FIXED,

    /** N alerts per window at unpredictable (SecureRandom) times. */
    IRREGULAR,

    /** Ordered list of `interval × repeats` steps. */
    STEPPED,

    /** Fires exactly once, after a countdown or at a clock time. */
    ONE_OFF,
}

/** How an alarm alerts the user. */
@Serializable
enum class AlertMode {
    SOUND_AND_VIBRATION,
    SOUND_ONLY,
    VIBRATE_ONLY,

    /** Pocket mode: gentle pulses -> strong vibration after 30 s -> ramping chime after 60 s. */
    DISCREET_ESCALATION,

    /** Notification banner only, no sound or vibration. */
    SILENT_BANNER,
}

/** When a fixed, irregular or stepped alarm begins. */
@Serializable
enum class StartMode {
    /** First alert one interval after saving (irregular: first window starts now). */
    NOW,

    /** Starts at a clock time today, or tomorrow if that time has already passed. */
    AT_TIME,
}

/** What a stepped schedule does after its final step. */
@Serializable
enum class AfterLastStep {
    STOP,
    CONTINUE,
}

/** How an individual alert occurrence ended. */
@Serializable
enum class AlertOutcome {
    DONE,
    SNOOZED,
    MISSED,
}

/** Which device the user responded on. */
@Serializable
enum class RespondedOn {
    PHONE,
    WATCH,
}

/** One step of a stepped schedule: fire every [intervalMs], [repeats] times. */
@Serializable
data class Step(
    val intervalMs: Long,
    val repeats: Int,
)
