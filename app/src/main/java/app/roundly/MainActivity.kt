package app.roundly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.roundly.ui.AppUiState
import app.roundly.ui.AppViewModel
import app.roundly.ui.format.LocalTimeFormat
import app.roundly.ui.navigation.RoundlyNavHost
import app.roundly.ui.theme.RoundlyTheme
import dagger.hilt.android.AndroidEntryPoint

/** Single-activity host for onboarding, dashboard, editor, settings and presets. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val state by appViewModel.state.collectAsStateWithLifecycle()
            when (val s = state) {
                AppUiState.Loading -> RoundlyTheme {
                    Box(Modifier.fillMaxSize())
                }
                is AppUiState.Ready -> RoundlyTheme(themeMode = s.settings.themeMode, dynamicColor = s.settings.dynamicColor) {
                    CompositionLocalProvider(LocalTimeFormat provides s.settings.timeFormat) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            RoundlyNavHost(onboardingCompleted = s.onboardingCompleted)
                        }
                    }
                }
            }
        }
    }
}
