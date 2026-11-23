package app.roundly.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.roundly.core.model.AppSettings
import app.roundly.core.model.AppState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface SettingsRepository {
    val settings: Flow<AppSettings>
    val state: Flow<AppState>

    suspend fun current(): AppSettings
    suspend fun currentState(): AppState

    suspend fun update(transform: (AppSettings) -> AppSettings)
    suspend fun replace(settings: AppSettings)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setPausedAll(paused: Boolean)
}

/** Preferences DataStore. Missing or unreadable values fall back to the defaults in [AppSettings]. */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val DefaultAlertMode = stringPreferencesKey("default_alert_mode")
        val SnoozeMinutes = intPreferencesKey("snooze_minutes")
        val RingTimeoutMinutes = intPreferencesKey("ring_timeout_minutes")
        val TimeFormat = stringPreferencesKey("time_format")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val DynamicColor = booleanPreferencesKey("dynamic_color")
        val OnboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val PausedAll = booleanPreferencesKey("paused_all")
    }

    private val prefs: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val settings: Flow<AppSettings> = prefs.map { it.toSettings() }.distinctUntilChanged()

    override val state: Flow<AppState> = prefs.map {
        AppState(
            onboardingCompleted = it[Keys.OnboardingCompleted] ?: false,
            pausedAll = it[Keys.PausedAll] ?: false,
        )
    }.distinctUntilChanged()

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun currentState(): AppState = state.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings()).sanitized()) }
    }

    override suspend fun replace(settings: AppSettings) {
        store.edit { prefs -> prefs.write(settings.sanitized()) }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        store.edit { it[Keys.OnboardingCompleted] = completed }
    }

    override suspend fun setPausedAll(paused: Boolean) {
        store.edit { it[Keys.PausedAll] = paused }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            defaultAlertMode = enumOr(this[Keys.DefaultAlertMode], defaults.defaultAlertMode),
            snoozeMinutes = this[Keys.SnoozeMinutes] ?: defaults.snoozeMinutes,
            ringTimeoutMinutes = this[Keys.RingTimeoutMinutes] ?: defaults.ringTimeoutMinutes,
            timeFormat = enumOr(this[Keys.TimeFormat], defaults.timeFormat),
            themeMode = enumOr(this[Keys.ThemeMode], defaults.themeMode),
            dynamicColor = this[Keys.DynamicColor] ?: defaults.dynamicColor,
        ).sanitized()
    }

    private fun MutablePreferences.write(s: AppSettings) {
        this[Keys.DefaultAlertMode] = s.defaultAlertMode.name
        this[Keys.SnoozeMinutes] = s.snoozeMinutes
        this[Keys.RingTimeoutMinutes] = s.ringTimeoutMinutes
        this[Keys.TimeFormat] = s.timeFormat.name
        this[Keys.ThemeMode] = s.themeMode.name
        this[Keys.DynamicColor] = s.dynamicColor
    }

    private inline fun <reified E : Enum<E>> enumOr(raw: String?, fallback: E): E =
        raw?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: fallback
}
