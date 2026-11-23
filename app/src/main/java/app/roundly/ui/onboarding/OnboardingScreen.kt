package app.roundly.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.roundly.R
import app.roundly.data.repository.SettingsRepository
import app.roundly.ui.components.IconBadge
import app.roundly.ui.components.PrimaryButton
import app.roundly.ui.preview.ThemePreviews
import app.roundly.ui.theme.Motion
import app.roundly.ui.theme.RoundlyTheme
import app.roundly.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    fun complete(then: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingCompleted(true)
            then()
        }
    }
}

private data class OnboardingPage(val icon: ImageVector, val title: Int, val text: Int)

private val pages = listOf(
    OnboardingPage(Icons.Outlined.Repeat, R.string.onboarding_1_title, R.string.onboarding_1_text),
    OnboardingPage(Icons.Outlined.NotificationsActive, R.string.onboarding_2_title, R.string.onboarding_2_text),
    OnboardingPage(Icons.Outlined.BatteryChargingFull, R.string.onboarding_3_title, R.string.onboarding_3_text),
)

@Composable
fun OnboardingRoute(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    OnboardingScreen(
        onAllowNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onFinish = { viewModel.complete(onFinished) },
    )
}

@Composable
fun OnboardingScreen(onAllowNotifications: () -> Unit, onFinish: () -> Unit) {
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLast = pager.currentPage == pages.lastIndex

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!isLast) {
                TextButton(onClick = onFinish) { Text(stringResource(R.string.action_skip)) }
            } else {
                Spacer(Modifier.height(48.dp))
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            val page = pages[index]
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconBadge(page.icon, modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(Spacing.xl))
                Text(
                    stringResource(page.title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    stringResource(page.text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (index == 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Spacer(Modifier.height(Spacing.lg))
                    OutlinedButton(onClick = onAllowNotifications, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.action_allow_notifications))
                    }
                }
            }
        }
        PageDots(count = pages.size, current = pager.currentPage)
        Spacer(Modifier.height(Spacing.lg))
        PrimaryButton(
            text = stringResource(if (isLast) R.string.action_get_started else R.string.action_next),
            onClick = {
                if (isLast) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1, animationSpec = Motion.long()) }
            },
        )
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    val description = stringResource(R.string.cd_page, current + 1, count)
    Row(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = Spacing.xs)
                    .size(if (i == current) 8.dp else 6.dp)
                    .background(
                        if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        }
    }
}

@ThemePreviews
@Composable
private fun OnboardingPreview() {
    RoundlyTheme { OnboardingScreen(onAllowNotifications = {}, onFinish = {}) }
}
