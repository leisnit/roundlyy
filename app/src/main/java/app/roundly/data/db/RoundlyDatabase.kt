package app.roundly.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import app.roundly.data.db.dao.AlarmDao
import app.roundly.data.db.dao.AlertLogDao
import app.roundly.data.db.dao.PresetDao
import app.roundly.data.db.entity.AlarmEntity
import app.roundly.data.db.entity.AlertLogEntity
import app.roundly.data.db.entity.PresetEntity

/**
 * Local-only database. The schema JSON for every version is exported to app/schemas and must be
 * committed; see [Migrations] for the upgrade policy.
 */
@Database(
    entities = [AlarmEntity::class, PresetEntity::class, AlertLogEntity::class],
    version = RoundlyDatabase.VERSION,
    exportSchema = true,
)
abstract class RoundlyDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun presetDao(): PresetDao
    abstract fun alertLogDao(): AlertLogDao

    companion object {
        const val NAME = "roundly.db"
        const val VERSION = 1
    }
}
