package app.roundly.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.roundly.alert.AlertController
import app.roundly.core.model.AlarmType
import app.roundly.core.model.Preset
import app.roundly.core.schedule.AlarmDraft
import app.roundly.core.schedule.AlarmFactory
import app.roundly.core.schedule.ScheduleProblem
import app.roundly.core.schedule.ScheduleValidation
import app.roundly.core.serialization.RoundlyJson
import app.roundly.core.time.Clock
import app.roundly.data.repository.AlarmRepository
import app.roundly.data.repository.PresetRepository
import app.roundly.data.repository.SettingsRepository
import app.roundly.scheduling.AlarmScheduler
import app.roundly.ui.navigation.EditorDestination
import app.roundly.watch.WatchBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val draft: AlarmDraft = AlarmDraft(),
    val problems: List<ScheduleProblem> = emptyList(),
    val watchAvailable: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val presetSaved: Boolean = false,
) {
    val canSave: Boolean get() = !loading && !saving && problems.isEmpty()
}

/**
 * Editor for all four alarm types. The draft is mirrored into SavedStateHandle, so edits survive
 * rotation and process death.
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val alarms: AlarmRepository,
    private val presets: PresetRepository,
    private val settings: SettingsRepository,
    private val scheduler: AlarmScheduler,
    private val alertController: AlertController,
    private val watch: WatchBridge,
    private val clock: Clock,
) : ViewModel() {

    private val route = savedState.toRoute<EditorDestination>()
    private val _state = MutableStateFlow(EditorUiState(isNew = route.alarmId == 0L))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val restored = savedState.get<String>(KEY_DRAFT)?.let {
                runCatching { RoundlyJson.compact.decodeFromString(AlarmDraft.serializer(), it) }.getOrNull()
            }
            val draft = restored ?: initialDraft()
            publish(draft, loading = false)
            _state.update { it.copy(watchAvailable = watch.isWatchReachable() || draft.watchOnly) }
        }
    }

    fun update(transform: (AlarmDraft) -> AlarmDraft) {
        publish(transform(_state.value.draft), loading = false)
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            scheduler.save(current.draft)
            _state.update { it.copy(saving = false, saved = true) }
        }
    }

    /** Rings the draft's alert mode right now, without saving or logging anything. */
    fun testAlert() {
        val draft = _state.value.draft
        viewModelScope.launch {
            alertController.test(AlarmFactory.create(draft, clock.now(), clock.zone()))
        }
    }

    fun saveAsPreset(name: String) {
        val draft = _state.value.draft
        if (name.isBlank()) return
        viewModelScope.launch {
            presets.saveCustom(Preset(name = name, type = draft.type, config = draft.toPresetConfig()))
            _state.update { it.copy(presetSaved = true) }
        }
    }

    fun presetMessageShown() {
        _state.update { it.copy(presetSaved = false) }
    }

    private suspend fun initialDraft(): AlarmDraft {
        val defaultMode = settings.current().defaultAlertMode
        if (route.alarmId != 0L) {
            alarms.getAlarm(route.alarmId)?.let { return AlarmDraft.fromAlarm(it, clock.zone()) }
        }
        if (route.presetId != 0L) {
            presets.getPreset(route.presetId)?.let { return AlarmDraft.fromPreset(it.type, it.config, defaultMode) }
        }
        val type = route.type?.let { name -> AlarmType.entries.firstOrNull { it.name == name } } ?: AlarmType.FIXED
        return AlarmDraft(type = type, alertMode = defaultMode)
    }

    private fun publish(draft: AlarmDraft, loading: Boolean) {
        savedState[KEY_DRAFT] = RoundlyJson.compact.encodeToString(AlarmDraft.serializer(), draft)
        _state.update { it.copy(loading = loading, draft = draft, problems = ScheduleValidation.validate(draft)) }
    }

    private companion object {
        const val KEY_DRAFT = "editor_draft"
    }
}
