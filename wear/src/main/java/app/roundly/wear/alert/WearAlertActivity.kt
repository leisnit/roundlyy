package app.roundly.wear.alert

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import app.roundly.core.sync.WatchAction
import app.roundly.wear.R
import app.roundly.wear.data.WatchRepository
import app.roundly.wear.di.WearAppScope
import app.roundly.wear.notifications.WearNotifications
import app.roundly.wear.ui.theme.RoundlyWearTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WearAlertUiState(
    val occurrenceId: String? = null,
    val label: String = "",
    val time: String = "",
    val detail: String? = null,
    val snoozeMinutes: Int = 5,
    val ready: Boolean = false,
)

@HiltViewModel
class WearAlertViewModel @Inject constructor(
    queue: WatchAlertQueue,
    repository: WatchRepository,
    notifications: WearNotifications,
    private val controller: WearAlertController,
    @WearAppScope private val appScope: CoroutineScope,
) : ViewModel() {

    val state: StateFlow<WearAlertUiState> = combine(queue.alerts, repository.state) { alerts, watch ->
        val head = alerts.firstOrNull()
        if (head == null) {
            WearAlertUiState(ready = true)
        } else {
            WearAlertUiState(
                occurrenceId = head.occurrenceId,
                label = notifications.label(head),
                time = notifications.time(head.scheduledAt, watch.timeFormat),
                detail = head.detail,
                snoozeMinutes = watch.snoozeMinutes,
                ready = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WearAlertUiState())

    fun respond(action: WatchAction) {
        val id = state.value.occurrenceId ?: return
        appScope.launch { controller.respond(id, action) }
    }
}

/** Full-screen watch alert with large Done and Snooze buttons. Haptics run in the controller. */
@AndroidEntryPoint
class WearAlertActivity : ComponentActivity() {

    private val viewModel: WearAlertViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.state.collect { if (it.ready && it.occurrenceId == null) finish() }
            }
        }

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            RoundlyWearTheme {
                WearAlertScreen(
                    state = state,
                    onDone = { viewModel.respond(WatchAction.DONE) },
                    onSnooze = { viewModel.respond(WatchAction.SNOOZE) },
                )
            }
        }
    }
}

@Composable
fun WearAlertScreen(state: WearAlertUiState, onDone: () -> Unit, onSnooze: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.label,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = state.time,
            style = MaterialTheme.typography.numeralSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        state.detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) { Text(stringResource(R.string.action_done), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        Spacer(Modifier.height(6.dp))
        FilledTonalButton(
            onClick = onSnooze,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            Text(
                stringResource(R.string.action_snooze_minutes, state.snoozeMinutes),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@WearPreviewDevices
@Composable
private fun WearAlertPreview() {
    RoundlyWearTheme {
        WearAlertScreen(
            state = WearAlertUiState(occurrenceId = "1@0", label = "Drink water", time = "12:40", detail = "Step 2 of 3", ready = true),
            onDone = {},
            onSnooze = {},
        )
    }
}
