package app.roundly.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import app.roundly.wear.ui.AlarmListScreen
import app.roundly.wear.ui.WearMainViewModel
import app.roundly.wear.ui.theme.RoundlyWearTheme
import dagger.hilt.android.AndroidEntryPoint

/** Watch entry point: the synced alarm list with toggles and Pause all. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: WearMainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            RoundlyWearTheme {
                AppScaffold {
                    AlarmListScreen(
                        state = state,
                        formatTime = viewModel::formatTime,
                        onToggle = viewModel::setEnabled,
                        onTogglePauseAll = viewModel::togglePauseAll,
                    )
                }
            }
        }
    }
}
