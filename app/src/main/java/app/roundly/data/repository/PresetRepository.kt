package app.roundly.data.repository

import androidx.room.withTransaction
import app.roundly.core.model.BuiltInPresets
import app.roundly.core.model.Preset
import app.roundly.data.db.RoundlyDatabase
import app.roundly.data.db.dao.PresetDao
import app.roundly.data.db.toEntity
import app.roundly.data.db.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface PresetRepository {
    /** All presets, built-ins first. Includes hidden ones; the UI filters for the chip row. */
    fun observePresets(): Flow<List<Preset>>
    suspend fun getPresets(): List<Preset>
    suspend fun getPreset(id: Long): Preset?

    /** Seeds the five built-in presets if missing. Safe to call on every start. */
    suspend fun ensureBuiltIns()

    /** Saves a new custom preset. Returns its id. */
    suspend fun saveCustom(preset: Preset): Long
    suspend fun rename(id: Long, name: String): Boolean
    suspend fun delete(id: Long): Boolean
    suspend fun setHidden(id: Long, hidden: Boolean)

    /** Import: replaces custom presets and applies the built-ins' hidden flags. */
    suspend fun replaceFromBackup(presets: List<Preset>)
}

@Singleton
class RoomPresetRepository @Inject constructor(
    private val db: RoundlyDatabase,
    private val dao: PresetDao,
) : PresetRepository {

    override fun observePresets(): Flow<List<Preset>> =
        dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun getPresets(): List<Preset> = dao.getAll().map { it.toModel() }

    override suspend fun getPreset(id: Long): Preset? = dao.getById(id)?.toModel()

    override suspend fun ensureBuiltIns() {
        dao.insertIgnore(BuiltInPresets.all.map { it.toEntity() })
    }

    override suspend fun saveCustom(preset: Preset): Long {
        val order = dao.maxCustomSortOrder() + 1
        return dao.insert(
            preset.copy(
                id = 0,
                name = preset.name.trim().take(MAX_NAME),
                builtIn = false,
                builtInKey = null,
                hidden = false,
                sortOrder = order,
            ).toEntity(),
        )
    }

    override suspend fun rename(id: Long, name: String): Boolean {
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty()) return false
        return dao.renameCustom(id, clean) > 0
    }

    override suspend fun delete(id: Long): Boolean = dao.deleteCustom(id) > 0

    override suspend fun setHidden(id: Long, hidden: Boolean) = dao.setHidden(id, hidden)

    override suspend fun replaceFromBackup(presets: List<Preset>) = db.withTransaction {
        dao.insertIgnore(BuiltInPresets.all.map { it.toEntity() })
        presets.filter { it.builtIn }.forEach { preset ->
            preset.builtInKey?.let { dao.setBuiltInHidden(it, preset.hidden) }
        }
        dao.deleteAllCustom()
        val custom = presets.filterNot { it.builtIn }.mapIndexed { index, preset ->
            preset.copy(id = 0, builtInKey = null, sortOrder = index + 1).toEntity()
        }
        if (custom.isNotEmpty()) dao.insertAll(custom)
    }

    private companion object {
        const val MAX_NAME = 40
    }
}
