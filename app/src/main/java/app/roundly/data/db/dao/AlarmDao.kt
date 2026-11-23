package app.roundly.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.roundly.data.db.entity.AlarmEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarms ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    fun observeById(id: Long): Flow<AlarmEntity?>

    @Query("SELECT * FROM alarms ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Long): AlarmEntity?

    /** Alarms that should currently hold a pending trigger. */
    @Query("SELECT * FROM alarms WHERE enabled = 1 AND pausedByMaster = 0 AND completedAt IS NULL")
    suspend fun getActive(): List<AlarmEntity>

    /** Insert, or replace when re-inserting a deleted alarm with its original id (Undo). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: AlarmEntity): Long

    @Insert
    suspend fun insertAll(alarms: List<AlarmEntity>): List<Long>

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Update
    suspend fun updateAll(alarms: List<AlarmEntity>)

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE alarms SET nextTriggerAt = :nextTriggerAt WHERE id = :id")
    suspend fun setNextTrigger(id: Long, nextTriggerAt: Long?)

    /** Pause all: flag every running alarm so Resume all restores exactly those. */
    @Query("UPDATE alarms SET pausedByMaster = 1, snoozeUntil = NULL, snoozedScheduledAt = NULL, nextTriggerAt = NULL WHERE enabled = 1 AND completedAt IS NULL")
    suspend fun pauseAllEnabled(): Int

    @Query("UPDATE alarms SET pausedByMaster = 0 WHERE pausedByMaster = 1")
    suspend fun clearMasterPause(): Int

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM alarms")
    suspend fun deleteAll()

    /** Recent timers older than [before] are auto-cleared. */
    @Query("DELETE FROM alarms WHERE type = 'ONE_OFF' AND completedAt IS NOT NULL AND completedAt < :before")
    suspend fun deleteRecentTimersCompletedBefore(before: Long): Int
}
