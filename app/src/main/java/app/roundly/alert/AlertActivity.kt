package app.roundly.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.roundly.core.model.ThemeMode
import app.roundly.ui.alert.AlertScreen
import app.roundly.ui.alert.AlertViewModel
import app.roundly.ui.format.LocalTimeFormat
import app.roundly.ui.theme.RoundlyTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Full-screen alert, launched by the alarm notification's full-screen intent. Shows over the lock
 * screen and turns the screen on (no SYSTEM_ALERT_WINDOW). Volume keys and the power key count as
 * Snooze. Closes itself when no alert is left in the queue.
 */
@AndroidEntryPoint
class AlertActivity : ComponentActivity() {

    private val viewModel: AlertViewModel by viewModels()

    /** Power key while the alert is showing: the screen turning off means the user pressed it. */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) viewModel.snooze()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.state.collect { if (it.ready && it.occurrenceId == null) finish() }
            }
        }

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            // Night-friendly: in any dark theme the alert uses the true-dark palette, so a
            // screen that wakes in a dark room never flashes bright.
            val dark = when (state.settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK, ThemeMode.TRUE_DARK -> true
            }
            RoundlyTheme(
                themeMode = if (dark) ThemeMode.TRUE_DARK else ThemeMode.LIGHT,
                dynamicColor = state.settings.dynamicColor,
            ) {
                CompositionLocalProvider(LocalTimeFormat provides state.settings.timeFormat) {
                    AlertScreen(state = state, onDone = viewModel::done, onSnooze = viewModel::snooze)
                }
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        -> {
            if (event.repeatCount == 0) viewModel.snooze()
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        unregisterReceiver(screenOffReceiver)
        super.onDestroy()
    }
}
