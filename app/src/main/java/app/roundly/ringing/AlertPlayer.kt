package app.roundly.ringing

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import app.roundly.core.model.AlertMode
import app.roundly.core.schedule.HapticPatterns
import app.roundly.core.schedule.Waveform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays one alert's sound and vibration, including discreet escalation:
 * two gentle pulses -> after 30 s a stronger repeating vibration -> after 60 s a chime whose
 * volume ramps up. Sound always uses USAGE_ALARM so it follows the alarm volume.
 */
class AlertPlayer(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val audioManager: AudioManager? = context.getSystemService()
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService<VibratorManager>()?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService()
    }

    private val alarmAudio: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var escalation: Job? = null
    private var ramp: Job? = null

    fun start(mode: AlertMode, ringtoneUri: String?) {
        stop()
        when (mode) {
            AlertMode.SOUND_AND_VIBRATION -> {
                playSound(ringtoneUri, rampUp = false)
                vibrate(HapticPatterns.Standard)
            }
            AlertMode.SOUND_ONLY -> playSound(ringtoneUri, rampUp = false)
            AlertMode.VIBRATE_ONLY -> vibrate(HapticPatterns.MultiPulse)
            AlertMode.DISCREET_ESCALATION -> {
                vibrate(HapticPatterns.GentleDouble)
                escalation = scope.launch {
                    delay(HapticPatterns.ESCALATE_VIBRATION_AFTER_MS)
                    vibrate(HapticPatterns.StrongRepeating)
                    delay(HapticPatterns.ESCALATE_SOUND_AFTER_MS - HapticPatterns.ESCALATE_VIBRATION_AFTER_MS)
                    playSound(ringtoneUri, rampUp = true)
                }
            }
            AlertMode.SILENT_BANNER -> Unit
        }
    }

    fun stop() {
        escalation?.cancel()
        escalation = null
        ramp?.cancel()
        ramp = null
        player?.let {
            runCatching { if (it.isPlaying) it.stop() }
            it.release()
        }
        player = null
        vibrator?.cancel()
        abandonFocus()
    }

    // --- Sound --------------------------------------------------------------------------------

    private fun playSound(ringtoneUri: String?, rampUp: Boolean) {
        val candidates = listOfNotNull(
            ringtoneUri?.toUri(),
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
            Settings.System.DEFAULT_ALARM_ALERT_URI,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        ).distinct()
        requestFocus()
        tryPlay(candidates, 0, rampUp)
    }

    private fun tryPlay(candidates: List<Uri>, index: Int, rampUp: Boolean) {
        if (index >= candidates.size) return
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(alarmAudio)
            mp.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            mp.isLooping = true
            mp.setDataSource(context, candidates[index])
            mp.setOnPreparedListener {
                if (player !== it) return@setOnPreparedListener
                if (rampUp) startRamp(it) else it.setVolume(1f, 1f)
                it.start()
            }
            mp.setOnErrorListener { failed, _, _ ->
                failed.release()
                if (player === failed) {
                    player = null
                    tryPlay(candidates, index + 1, rampUp)
                }
                true
            }
            mp.prepareAsync()
        } catch (_: Exception) {
            mp.release()
            if (player === mp) player = null
            tryPlay(candidates, index + 1, rampUp)
        }
    }

    private fun startRamp(mp: MediaPlayer) {
        mp.setVolume(RAMP_START, RAMP_START)
        ramp = scope.launch {
            val steps = 30
            val stepMs = HapticPatterns.SOUND_RAMP_MS / steps
            for (i in 1..steps) {
                delay(stepMs)
                if (!isActive || player !== mp) return@launch
                val v = RAMP_START + (1f - RAMP_START) * i / steps
                runCatching { mp.setVolume(v, v) }
            }
        }
    }

    private fun requestFocus() {
        val am = audioManager ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(alarmAudio)
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = request
        am.requestAudioFocus(request)
    }

    private fun abandonFocus() {
        val request = focusRequest ?: return
        audioManager?.abandonAudioFocusRequest(request)
        focusRequest = null
    }

    // --- Vibration ----------------------------------------------------------------------------

    private fun vibrate(waveform: Waveform) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = if (v.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(waveform.timings, waveform.amplitudes, waveform.repeatIndex)
        } else {
            VibrationEffect.createWaveform(waveform.timings, waveform.repeatIndex)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, alarmAudio)
        }
    }

    private companion object {
        const val RAMP_START = 0.05f
    }
}
