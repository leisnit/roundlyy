package app.roundly.data.repository

import androidx.room.withTransaction
import app.roundly.core.model.AlarmLimits
import app.roundly.core.model.Alarm
import app.roundly.core.time.Clock
import app.roundly.data.db.RoundlyDatabase
import app.roundly.data.db.dao.AlarmDao
import app.roundly.data.db.toEntity
import app.roundly.data.db.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alarm definitions and their scheduling state. Repositories only persist. Arming and
 * cancelling AlarmManager triggers is the scheduler's job (Phase 3), which calls into here.
 */
interface AlarmRepository {
    fun observeAlarms(): Flow<List<Alarm>>
    fun observeAlarm(id: Long): Flow<Alarm?>
    suspend fun getAlarms(): List<Alarm>
    suspend fun getAlarm(id: Long): Alarm?
    suspend fun getActiveAlarms(): List<Alarm>

    /** Inserts a new alarm (id 0) or replaces an existing one. Returns the id. */
    suspend fun save(alarm: Alarm): Long
    suspend fun saveAll(alarms: List<Alarm>)
    suspend fun setEnabled(id: Long, enabled: Boolean)
    suspend fun setNextTrigger(id: Long, nextTriggerAt: Long?)

    /** Deletes and returns the alarm, so the UI can offer Undo via [restore]. */
    suspend fun delete(id: Long): Alarm?

    /** Re-inserts a deleted alarm with its original id (keeps its PendingIntent request code). */
    suspend fun restore(alarm: Alarm)

    /** Pause all. Returns the number of alarms paused. */
    suspend fun markAllPaused(): Int

    /** Resume all: clears the master pause flag. The scheduler then recalculates from now. */
    suspend fun clearAllPaused(): Int

    /** Replaces every alarm (used by Import). Returns the new rows. */
    suspend fun replaceAll(alarms: List<Alarm>): List<Alarm>

    /** Removes recent timers acknowledged more than 7 days ago. */
    suspend fun purgeExpiredRecentTimers(): Int
}

@Singleton
class RoomAlarmRepository @Inject constructor(
    private val db: RoundlyDatabase,
    private val dao: AlarmDao,
    private val clock: Clock,
) : AlarmRepository {

    override fun observeAlarms(): Flow<List<Alarm>> =
        dao.observeAll().map { rows -> rows.map { it.toModel() } }.distinctUntilChanged()

    override fun observeAlarm(id: Long): Flow<Alarm?> =
        dao.observeById(id).map { it?.toModel() }.distinctUntilChanged()

    override suspend fun getAlarms(): List<Alarm> = dao.getAll().map { it.toModel() }

    override suspend fun getAlarm(id: Long): Alarm? = dao.getById(id)?.toModel()

    override suspend fun getActiveAlarms(): List<Alarm> = dao.getActive().map { it.toModel() }

    override suspend fun save(alarm: Alarm): Long {
        val clean = alarm.copy(label = alarm.label.trim().take(AlarmLimits.MAX_LABEL_LENGTH))
        return if (clean.id == 0L) {
            dao.upsert(clean.toEntity())
        } else {
            dao.update(clean.toEntity())
            clean.id
        }
    }

    override suspend fun saveAll(alarms: List<Alarm>) {
        if (alarms.isNotEmpty()) dao.updateAll(alarms.map { it.toEntity() })
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    override suspend fun setNextTrigger(id: Long, nextTriggerAt: Long?) =
        dao.setNextTrigger(id, nextTriggerAt)

    override suspend fun delete(id: Long): Alarm? = db.withTransaction {
        val existing = dao.getById(id)?.toModel()
        if (existing != null) dao.deleteById(id)
        existing
    }

    override suspend fun restore(alarm: Alarm) {
        dao.upsert(alarm.toEntity())
    }

    override suspend fun markAllPaused(): Int = dao.pauseAllEnabled()

    override suspend fun clearAllPaused(): Int = dao.clearMasterPause()

    override suspend fun replaceAll(alarms: List<Alarm>): List<Alarm> = db.withTransaction {
        dao.deleteAll()
        val fresh = alarms.map { it.asNew() }
        val ids = dao.insertAll(fresh.map { it.toEntity() })
        fresh.zip(ids) { alarm, id -> alarm.copy(id = id) }
    }

    override suspend fun purgeExpiredRecentTimers(): Int =
        dao.deleteRecentTimersCompletedBefore(clock.now() - AlarmLimits.RECENT_TIMER_RETENTION_MS)
}
