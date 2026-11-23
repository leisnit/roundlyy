package app.roundly.wear.alert

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import app.roundly.core.model.AlertMode
import app.roundly.core.schedule.HapticPatterns
import app.roundly.core.schedule.Waveform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Strong, distinctive watch haptics: a waveform per alert mode (shared with the phone), escalating
 * to a stronger repeating pattern if the alert is not acknowledged within 30 s.
 */
class WearHaptics(context: Context, private val scope: CoroutineScope) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService<VibratorManager>()?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService()
    }
    private var escalation: Job? = null

    fun start(mode: AlertMode) {
        stop()
        val initial = when (mode) {
            AlertMode.SOUND_AND_VIBRATION, AlertMode.SOUND_ONLY -> HapticPatterns.Standard
            AlertMode.VIBRATE_ONLY -> HapticPatterns.MultiPulse
            AlertMode.DISCREET_ESCALATION, AlertMode.SILENT_BANNER -> HapticPatterns.GentleDouble
        }
        vibrate(initial)
        if (mode != AlertMode.SILENT_BANNER) {
            escalation = scope.launch {
                delay(HapticPatterns.ESCALATE_VIBRATION_AFTER_MS)
                vibrate(HapticPatterns.StrongRepeating)
            }
        }
    }

    fun stop() {
        escalation?.cancel()
        escalation = null
        vibrator?.cancel()
    }

    private fun vibrate(waveform: Waveform) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect = if (v.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(waveform.timings, waveform.amplitudes, waveform.repeatIndex)
        } else {
            VibrationEffect.createWaveform(waveform.timings, waveform.repeatIndex)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
        }
    }
}
