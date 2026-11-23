package app.roundly.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.roundly.data.db.entity.PresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {

    @Query("SELECT * FROM presets ORDER BY builtIn DESC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets ORDER BY builtIn DESC, sortOrder ASC, id ASC")
    suspend fun getAll(): List<PresetEntity>

    @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun getById(id: Long): PresetEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM presets WHERE builtIn = 0")
    suspend fun maxCustomSortOrder(): Int

    /** Seeds built-ins; existing rows (matched by the unique builtInKey) are left untouched. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(presets: List<PresetEntity>): List<Long>

    @Insert
    suspend fun insert(preset: PresetEntity): Long

    @Insert
    suspend fun insertAll(presets: List<PresetEntity>): List<Long>

    /** Only custom presets can be renamed. */
    @Query("UPDATE presets SET name = :name WHERE id = :id AND builtIn = 0")
    suspend fun renameCustom(id: Long, name: String): Int

    /** Only custom presets can be deleted; built-ins can only be hidden. */
    @Query("DELETE FROM presets WHERE id = :id AND builtIn = 0")
    suspend fun deleteCustom(id: Long): Int

    @Query("DELETE FROM presets WHERE builtIn = 0")
    suspend fun deleteAllCustom()

    @Query("UPDATE presets SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("UPDATE presets SET hidden = :hidden WHERE builtInKey = :key")
    suspend fun setBuiltInHidden(key: String, hidden: Boolean)
}
