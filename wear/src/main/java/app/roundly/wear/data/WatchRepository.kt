package app.roundly.wear.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.roundly.core.model.Alarm
import app.roundly.core.model.AppSettings
import app.roundly.core.model.TimeFormat
import app.roundly.core.serialization.RoundlyJson
import app.roundly.core.sync.WatchSyncPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The watch's copy of the schedule. The phone is the source of truth and replaces it on every
 * sync; while the phone is out of range the watch advances this copy itself.
 */
@Serializable
data class WatchState(
    val alarms: List<Alarm> = emptyList(),
    val pausedAll: Boolean = false,
    val snoozeMinutes: Int = AppSettings.DEFAULT_SNOOZE_MINUTES,
    val ringTimeoutMinutes: Int = AppSettings.DEFAULT_RING_TIMEOUT_MINUTES,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    /** When the last phone sync arrived; null if the phone has never synced. */
    val syncedAt: Long? = null,
)

@Singleton
class WatchRepository @Inject constructor(private val store: DataStore<Preferences>) {

    private val key = stringPreferencesKey("state_json")

    val state: Flow<WatchState> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[key]?.let(::decode) ?: WatchState() }
        .distinctUntilChanged()

    suspend fun current(): WatchState = state.first()

    suspend fun update(transform: (WatchState) -> WatchState): WatchState {
        var result = WatchState()
        store.edit { prefs ->
            result = transform(prefs[key]?.let(::decode) ?: WatchState())
            prefs[key] = RoundlyJson.compact.encodeToString(WatchState.serializer(), result)
        }
        return result
    }

    /** Replaces local state with the phone's. Returns the ids of alarms that no longer exist. */
    suspend fun replaceFromPhone(payload: WatchSyncPayload, receivedAt: Long): Set<Long> {
        var removed = emptySet<Long>()
        update { old ->
            removed = old.alarms.map { it.id }.toSet() - payload.alarms.map { it.id }.toSet()
            WatchState(
                alarms = payload.alarms,
                pausedAll = payload.pausedAll,
                snoozeMinutes = payload.snoozeMinutes,
                ringTimeoutMinutes = payload.ringTimeoutMinutes,
                timeFormat = payload.timeFormat,
                syncedAt = receivedAt,
            )
        }
        return removed
    }

    private fun decode(json: String): WatchState? =
        runCatching { RoundlyJson.compact.decodeFromString(WatchState.serializer(), json) }.getOrNull()
}
