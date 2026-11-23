package app.roundly.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.roundly.core.model.AlarmType

@Entity(
    tableName = "presets",
    indices = [Index(value = ["builtInKey"], unique = true)],
)
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AlarmType,
    /** PresetConfig as JSON. */
    @ColumnInfo(name = "config") val configJson: String,
    val builtIn: Boolean,
    val hidden: Boolean,
    val builtInKey: String?,
    val sortOrder: Int,
)
