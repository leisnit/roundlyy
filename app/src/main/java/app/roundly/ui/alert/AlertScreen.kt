package app.roundly.ui.alert

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.roundly.R
import app.roundly.core.model.ThemeMode
import app.roundly.ui.preview.ThemePreviews
import app.roundly.ui.theme.Motion
import app.roundly.ui.theme.RoundlyTheme
import app.roundly.ui.theme.Sizes
import app.roundly.ui.theme.Spacing

/**
 * Full-bleed, calm alert: large centred label, the time beneath, and two big buttons in the
 * thumb zone. Each queued alert crossfades in turn.
 */
@Composable
fun AlertScreen(state: AlertUiState, onDone: () -> Unit, onSnooze: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            AnimatedContent(
                targetState = state,
                contentKey = { it.occurrenceId },
                transitionSpec = { fadeIn(Motion.long()) togetherWith fadeOut(Motion.medium()) },
                label = "alert-content",
            ) { s ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                ) {
                    Text(
                        text = s.label,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = s.time,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = s.detail,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (s.waiting > 0) {
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            text = pluralStringResource(R.plurals.alert_waiting, s.waiting, s.waiting),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Button(
                    onClick = onDone,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.alertButtonHeight),
                ) { Text(stringResource(R.string.action_done), style = MaterialTheme.typography.titleLarge) }
                FilledTonalButton(
                    onClick = onSnooze,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.alertButtonHeight),
                ) {
                    Text(
                        stringResource(R.string.action_snooze_minutes, state.snoozeMinutes),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun AlertScreenPreview() {
    RoundlyTheme {
        AlertScreen(
            state = AlertUiState(
                occurrenceId = "1@0",
                label = "Drink water",
                time = "12:40",
                detail = "Step 2 of 3 · alert 1 of 2",
                waiting = 1,
                ready = true,
            ),
            onDone = {},
            onSnooze = {},
        )
    }
}

@ThemePreviews
@Composable
private fun AlertScreenNightPreview() {
    RoundlyTheme(themeMode = ThemeMode.TRUE_DARK) {
        AlertScreen(
            state = AlertUiState(occurrenceId = "1@0", label = "Posture check", time = "02:15", detail = "Due at 02:15", ready = true),
            onDone = {},
            onSnooze = {},
        )
    }
}
