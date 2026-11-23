package app.roundly.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.roundly.core.model.AlertOutcome
import app.roundly.core.model.RespondedOn

/**
 * Alert history. No foreign key to alarms: history (and "Missed" notices) outlive a deleted alarm.
 * occurrenceId is unique so phone and watch responses for the same occurrence reconcile to one row.
 */
@Entity(
    tableName = "alert_log",
    indices = [
        Index(value = ["occurrenceId"], unique = true),
        Index(value = ["alarmId"]),
        Index(value = ["scheduledAt"]),
    ],
)
data class AlertLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long,
    val occurrenceId: String,
    val label: String,
    val scheduledAt: Long,
    val outcome: AlertOutcome,
    val respondedAt: Long?,
    val respondedOn: RespondedOn?,
)
