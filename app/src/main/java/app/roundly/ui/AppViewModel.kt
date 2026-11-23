package app.roundly.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.roundly.core.model.AppSettings
import app.roundly.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface AppUiState {
    data object Loading : AppUiState
    data class Ready(val settings: AppSettings, val onboardingCompleted: Boolean) : AppUiState
}

/** Theme, time format and the onboarding gate for the activity root. */
@HiltViewModel
class AppViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<AppUiState> = combine(settings.settings, settings.state) { s, st ->
        AppUiState.Ready(s, st.onboardingCompleted)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState.Loading)
}
