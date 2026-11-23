package app.roundly.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.roundly.ui.dashboard.DashboardRoute
import app.roundly.ui.editor.EditorRoute
import app.roundly.ui.onboarding.OnboardingRoute
import app.roundly.ui.permissions.PermissionsRoute
import app.roundly.ui.presets.PresetsRoute
import app.roundly.ui.settings.SettingsRoute
import app.roundly.ui.theme.Motion
import kotlinx.serialization.Serializable

@Serializable data object OnboardingDestination
@Serializable data object DashboardDestination
@Serializable data object SettingsDestination
@Serializable data object PermissionsDestination
@Serializable data object PresetsDestination

/** Editor for a new alarm (alarmId 0), an existing alarm, or pre-filled from a preset or type. */
@Serializable
data class EditorDestination(
    val alarmId: Long = 0,
    val presetId: Long = 0,
    val type: String? = null,
)

@Composable
fun RoundlyNavHost(onboardingCompleted: Boolean) {
    val nav = rememberNavController()
    // Decided once; finishing onboarding navigates explicitly instead of rebuilding the graph.
    val start: Any = remember { if (onboardingCompleted) DashboardDestination else OnboardingDestination }
    NavHost(
        navController = nav,
        startDestination = start,
        enterTransition = { fadeIn(Motion.medium()) },
        exitTransition = { fadeOut(Motion.short()) },
        popEnterTransition = { fadeIn(Motion.medium()) },
        popExitTransition = { fadeOut(Motion.short()) },
    ) {
        composable<OnboardingDestination> {
            OnboardingRoute(
                onFinished = {
                    nav.navigate(DashboardDestination) {
                        popUpTo(OnboardingDestination) { inclusive = true }
                    }
                },
            )
        }
        composable<DashboardDestination> {
            DashboardRoute(
                onCreate = { type -> nav.navigate(EditorDestination(type = type?.name)) },
                onEdit = { id -> nav.navigate(EditorDestination(alarmId = id)) },
                onPreset = { id -> nav.navigate(EditorDestination(presetId = id)) },
                onManagePresets = { nav.navigate(PresetsDestination) },
                onSettings = { nav.navigate(SettingsDestination) },
                onPermissions = { nav.navigate(PermissionsDestination) },
            )
        }
        composable<EditorDestination> {
            EditorRoute(onClose = { nav.popBackStack() })
        }
        composable<SettingsDestination> {
            SettingsRoute(
                onBack = { nav.popBackStack() },
                onPermissions = { nav.navigate(PermissionsDestination) },
                onPresets = { nav.navigate(PresetsDestination) },
            )
        }
        composable<PermissionsDestination> {
            PermissionsRoute(onBack = { nav.popBackStack() })
        }
        composable<PresetsDestination> {
            PresetsRoute(onBack = { nav.popBackStack() })
        }
    }
}
