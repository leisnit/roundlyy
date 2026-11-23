package app.roundly.core.schedule

import app.roundly.core.model.AlertMode

/**
 * A vibration waveform as plain data, so phone and watch share the same distinctive patterns.
 * [timings] alternate off/on in ms starting with "off"; [amplitudes] are 0-255 per segment.
 * [repeatIndex] is -1 for one shot, otherwise the index to loop from.
 */
class Waveform(
    val timings: LongArray,
    val amplitudes: IntArray,
    val repeatIndex: Int,
) {
    init {
        require(timings.size == amplitudes.size) { "timings and amplitudes must match" }
    }
}

object HapticPatterns {
    /** Steady alarm buzz used with sound. */
    val Standard = Waveform(
        timings = longArrayOf(0, 700, 500),
        amplitudes = intArrayOf(0, 200, 0),
        repeatIndex = 0,
    )

    /** Vibrate-only: a distinctive triple pulse, then a pause. */
    val MultiPulse = Waveform(
        timings = longArrayOf(0, 140, 90, 140, 90, 380, 900),
        amplitudes = intArrayOf(0, 230, 0, 230, 0, 255, 0),
        repeatIndex = 0,
    )

    /** Discreet escalation, stage 1: two gentle pulses, once. */
    val GentleDouble = Waveform(
        timings = longArrayOf(0, 90, 160, 90),
        amplitudes = intArrayOf(0, 70, 0, 70),
        repeatIndex = -1,
    )

    /** Discreet escalation, stage 2 (after 30 s): a stronger repeating vibration. */
    val StrongRepeating = Waveform(
        timings = longArrayOf(0, 450, 250, 450, 1100),
        amplitudes = intArrayOf(0, 255, 0, 255, 0),
        repeatIndex = 0,
    )

    /** Pattern a mode starts with; null when the mode never vibrates. */
    fun initialFor(mode: AlertMode): Waveform? = when (mode) {
        AlertMode.SOUND_AND_VIBRATION -> Standard
        AlertMode.SOUND_ONLY -> null
        AlertMode.VIBRATE_ONLY -> MultiPulse
        AlertMode.DISCREET_ESCALATION -> GentleDouble
        AlertMode.SILENT_BANNER -> null
    }

    /** Discreet escalation timings. */
    const val ESCALATE_VIBRATION_AFTER_MS = 30_000L
    const val ESCALATE_SOUND_AFTER_MS = 60_000L
    const val SOUND_RAMP_MS = 30_000L
}
