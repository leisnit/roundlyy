package app.roundly.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.roundly.core.model.AfterLastStep
import app.roundly.core.model.AlarmType
import app.roundly.core.model.AlertMode
import app.roundly.core.model.StartMode

/**
 * Room row for an alarm. The id doubles as the stable PendingIntent request code.
 * [stepsJson] and [pendingRandomTimesJson] hold JSON (see data/db/Mappers.kt).
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val type: AlarmType,
    val intervalMs: Long?,
    val windowMs: Long?,
    val alertsPerWindow: Int?,
    val minGapMs: Long?,
    val currentWindowStart: Long?,
    /** Hidden: the generated random times for the current irregular window. */
    @ColumnInfo(name = "pendingRandomTimes") val pendingRandomTimesJson: String?,
    @ColumnInfo(name = "steps") val stepsJson: String?,
    val afterLastStep: AfterLastStep?,
    val stepIndex: Int,
    val repeatIndex: Int,
    val stepAnchorAt: Long?,
    val fireAt: Long?,
    val oneOffDurationMs: Long?,
    val startMode: StartMode,
    val anchorAt: Long,
    val runForMs: Long?,
    val endsAt: Long?,
    val enabled: Boolean,
    val pausedByMaster: Boolean,
    val alertMode: AlertMode,
    val watchOnly: Boolean,
    val ringtoneUri: String?,
    val snoozeUntil: Long?,
    val snoozedScheduledAt: Long?,
    val nextTriggerAt: Long?,
    val lastFiredAt: Long?,
    val completedAt: Long?,
    val createdAt: Long,
)
