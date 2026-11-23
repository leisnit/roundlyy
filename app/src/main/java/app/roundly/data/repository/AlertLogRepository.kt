package app.roundly.data.repository

import androidx.room.withTransaction
import app.roundly.core.model.AlarmLimits
import app.roundly.core.model.AlertLog
import app.roundly.data.db.RoundlyDatabase
import app.roundly.data.db.dao.AlertLogDao
import app.roundly.data.db.toEntity
import app.roundly.data.db.toModel
import app.roundly.core.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface AlertLogRepository {
    fun observeRecent(limit: Int = 100): Flow<List<AlertLog>>
    suspend fun get(occurrenceId: String): AlertLog?

    /**
     * Records the outcome of an occurrence, reconciling phone and watch (see AlertLog.acceptsUpdate):
     * the first Done wins, a real answer replaces Missed, and identical repeats are ignored.
     * Returns true if this call changed the log, false if it was a duplicate or late.
     */
    suspend fun record(entry: AlertLog): Boolean

    /** Deletes entries older than 30 days. */
    suspend fun purgeExpired(): Int
}

@Singleton
class RoomAlertLogRepository @Inject constructor(
    private val db: RoundlyDatabase,
    private val dao: AlertLogDao,
    private val clock: Clock,
) : AlertLogRepository {

    override fun observeRecent(limit: Int): Flow<List<AlertLog>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toModel() } }

    override suspend fun get(occurrenceId: String): AlertLog? =
        dao.getByOccurrence(occurrenceId)?.toModel()

    override suspend fun record(entry: AlertLog): Boolean = db.withTransaction {
        val existing = dao.getByOccurrence(entry.occurrenceId)?.toModel()
        when {
            existing == null -> {
                dao.insert(entry.copy(id = 0).toEntity())
                true
            }
            !existing.acceptsUpdate(entry) -> false
            else -> {
                dao.update(entry.copy(id = existing.id).toEntity())
                true
            }
        }
    }

    override suspend fun purgeExpired(): Int =
        dao.deleteOlderThan(clock.now() - AlarmLimits.ALERT_LOG_RETENTION_MS)
}
