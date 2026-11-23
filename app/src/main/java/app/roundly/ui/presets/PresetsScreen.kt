@file:OptIn(ExperimentalMaterial3Api::class)

package app.roundly.ui.presets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.roundly.R
import app.roundly.core.model.AlarmType
import app.roundly.core.model.BuiltInPresets
import app.roundly.core.model.Preset
import app.roundly.core.model.PresetConfig
import app.roundly.data.repository.PresetRepository
import app.roundly.ui.components.RoundlyCard
import app.roundly.ui.components.SectionHeader
import app.roundly.ui.components.SettingsRow
import app.roundly.ui.components.TextInputDialog
import app.roundly.ui.format.typeIcon
import app.roundly.ui.format.typeLabel
import app.roundly.ui.preview.ThemePreviews
import app.roundly.ui.theme.RoundlyTheme
import app.roundly.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PresetsViewModel @Inject constructor(private val repository: PresetRepository) : ViewModel() {
    val presets: StateFlow<List<Preset>> =
        repository.observePresets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rename(id: Long, name: String) = viewModelScope.launch { repository.rename(id, name) }
    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }
    fun setHidden(id: Long, hidden: Boolean) = viewModelScope.launch { repository.setHidden(id, hidden) }
}

@Composable
fun PresetsRoute(onBack: () -> Unit, viewModel: PresetsViewModel = hiltViewModel()) {
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    PresetsScreen(
        presets = presets,
        onBack = onBack,
        onRename = { id, name -> viewModel.rename(id, name) },
        onDelete = { viewModel.delete(it) },
        onSetHidden = { id, hidden -> viewModel.setHidden(id, hidden) },
    )
}

@Composable
fun PresetsScreen(
    presets: List<Preset>,
    onBack: () -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onSetHidden: (Long, Boolean) -> Unit,
) {
    var renaming by rememberSaveable { mutableLongStateOf(0L) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.presets_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        ) {
            SectionHeader(stringResource(R.string.presets_built_in))
            RoundlyCard {
                presets.filter { it.builtIn }.forEach { preset ->
                    SettingsRow(
                        title = preset.name,
                        subtitle = stringResource(if (preset.hidden) R.string.preset_hidden else R.string.preset_shown),
                        leading = typeIcon(preset.type),
                        trailing = {
                            Switch(checked = !preset.hidden, onCheckedChange = { show -> onSetHidden(preset.id, !show) })
                        },
                    )
                }
            }

            SectionHeader(stringResource(R.string.presets_custom))
            val custom = presets.filterNot { it.builtIn }
            RoundlyCard {
                if (custom.isEmpty()) {
                    Text(
                        stringResource(R.string.presets_custom_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                custom.forEach { preset ->
                    SettingsRow(
                        title = preset.name,
                        subtitle = typeLabel(preset.type),
                        leading = typeIcon(preset.type),
                        trailing = {
                            Row {
                                IconButton(onClick = { renaming = preset.id }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.cd_rename, preset.name))
                                }
                                IconButton(onClick = { onDelete(preset.id) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.cd_delete, preset.name))
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    val target = presets.firstOrNull { it.id == renaming }
    if (target != null) {
        TextInputDialog(
            title = stringResource(R.string.preset_rename),
            initial = target.name,
            label = stringResource(R.string.preset_name),
            onConfirm = {
                onRename(target.id, it)
                renaming = 0L
            },
            onDismiss = { renaming = 0L },
        )
    }
}

@ThemePreviews
@Composable
private fun PresetsPreview() {
    RoundlyTheme {
        PresetsScreen(
            presets = BuiltInPresets.all.mapIndexed { i, p -> p.copy(id = i + 1L, hidden = i == 4) } +
                Preset(id = 10, name = "Training block", type = AlarmType.STEPPED, config = PresetConfig()),
            onBack = {}, onRename = { _, _ -> }, onDelete = {}, onSetHidden = { _, _ -> },
        )
    }
}
