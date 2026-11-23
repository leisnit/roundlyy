package app.roundly.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.roundly.data.db.entity.AlertLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertLogDao {

    @Query("SELECT * FROM alert_log ORDER BY scheduledAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AlertLogEntity>>

    @Query("SELECT * FROM alert_log WHERE occurrenceId = :occurrenceId LIMIT 1")
    suspend fun getByOccurrence(occurrenceId: String): AlertLogEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: AlertLogEntity): Long

    @Update
    suspend fun update(entry: AlertLogEntity)

    @Query("DELETE FROM alert_log WHERE scheduledAt < :before")
    suspend fun deleteOlderThan(before: Long): Int
}
