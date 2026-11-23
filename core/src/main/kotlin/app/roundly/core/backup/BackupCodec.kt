package app.roundly.core.backup

import app.roundly.core.model.Alarm
import app.roundly.core.model.AlarmType
import app.roundly.core.model.AppSettings
import app.roundly.core.model.Preset
import app.roundly.core.schedule.ScheduleValidation
import app.roundly.core.serialization.RoundlyJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable

/** The Export / Import file. Contains no alert history and no hidden irregular times. */
@Serializable
data class RoundlyBackup(
    val format: String = FORMAT_ID,
    val formatVersion: Int = CURRENT_VERSION,
    val exportedAt: Long,
    val alarms: List<Alarm>,
    /** Custom presets, plus built-ins only so their hidden flag is preserved. */
    val presets: List<Preset>,
    val settings: AppSettings,
) {
    companion object {
        const val FORMAT_ID = "roundly-backup"
        const val CURRENT_VERSION = 1
    }
}

/** Thrown when an import file is not a valid Roundly backup. [message] is user-presentable. */
class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupCodec {

    fun encode(backup: RoundlyBackup): String = RoundlyJson.pretty.encodeToString(
        RoundlyBackup.serializer(),
        backup.copy(alarms = backup.alarms.map { it.forExport() }),
    )

    /**
     * Parses and validates a backup. Invalid individual alarms are dropped rather than failing
     * the whole import; a file that is not a Roundly backup at all is rejected.
     */
    fun decode(text: String): RoundlyBackup {
        val backup = try {
            RoundlyJson.compact.decodeFromString(RoundlyBackup.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupFormatException("This file is not a Roundly backup.", e)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("This file is not a Roundly backup.", e)
        }
        if (backup.format != RoundlyBackup.FORMAT_ID) {
            throw BackupFormatException("This file is not a Roundly backup.")
        }
        if (backup.formatVersion > RoundlyBackup.CURRENT_VERSION) {
            throw BackupFormatException("This backup was made by a newer version of Roundly. Update the app and try again.")
        }
        return backup.copy(
            alarms = backup.alarms.filter(::isStructurallyValid).map { it.forExport() },
            presets = backup.presets.filter { it.name.isNotBlank() },
            settings = backup.settings.sanitized(),
        )
    }

    /** Structural checks so a hand-edited file cannot create an unschedulable alarm. */
    fun isStructurallyValid(alarm: Alarm): Boolean = when (alarm.type) {
        AlarmType.FIXED -> ScheduleValidation.intervalInRange(alarm.intervalMs)
        AlarmType.IRREGULAR ->
            ScheduleValidation.validateIrregular(alarm.windowMs, alarm.alertsPerWindow, alarm.minGapMs) == null
        AlarmType.STEPPED -> ScheduleValidation.validateSteps(alarm.steps).isEmpty() &&
            alarm.stepIndex in 0..alarm.steps.size &&
            alarm.repeatIndex >= 0
        AlarmType.ONE_OFF -> alarm.fireAt != null || alarm.oneOffDurationMs != null
    }
}
