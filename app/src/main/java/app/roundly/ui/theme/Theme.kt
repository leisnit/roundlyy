package app.roundly.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import app.roundly.core.model.ThemeMode

/** Extra theme facts that the Material ColorScheme does not carry. */
@Immutable
data class RoundlyExtras(
    val isDark: Boolean,
    val isTrueDark: Boolean,
    val status: StatusColors,
)

val LocalRoundlyExtras = staticCompositionLocalOf {
    RoundlyExtras(isDark = false, isTrueDark = false, status = LightStatusColors)
}

/**
 * Roundly theme.
 *
 * @param themeMode system / light / dark / true-dark.
 * @param dynamicColor Material You wallpaper colours (API 31+). Off by default so the designed
 *   palette shows first. In true-dark the dynamic scheme still gets the near-black surfaces.
 */
@Composable
fun RoundlyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.TRUE_DARK -> true
    }
    val isTrueDark = themeMode == ThemeMode.TRUE_DARK

    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    val colors: ColorScheme = when {
        useDynamic && isTrueDark -> dynamicDarkColorScheme(context).toTrueDark()
        useDynamic && isDark -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        isTrueDark -> TrueDarkColors
        isDark -> DarkColors
        else -> LightColors
    }

    val extras = RoundlyExtras(
        isDark = isDark,
        isTrueDark = isTrueDark,
        status = if (isDark) DarkStatusColors else LightStatusColors,
    )

    CompositionLocalProvider(LocalRoundlyExtras provides extras) {
        MaterialTheme(
            colorScheme = colors,
            typography = RoundlyTypography,
            shapes = RoundlyShapes,
            content = content,
        )
    }
}

private fun ColorScheme.toTrueDark(): ColorScheme = copy(
    background = TrueDarkColors.background,
    surface = TrueDarkColors.surface,
    onBackground = TrueDarkColors.onBackground,
    onSurface = TrueDarkColors.onSurface,
    surfaceDim = TrueDarkColors.surfaceDim,
    surfaceContainerLowest = TrueDarkColors.surfaceContainerLowest,
    surfaceContainerLow = TrueDarkColors.surfaceContainerLow,
    surfaceContainer = TrueDarkColors.surfaceContainer,
    surfaceContainerHigh = TrueDarkColors.surfaceContainerHigh,
    surfaceContainerHighest = TrueDarkColors.surfaceContainerHighest,
)

/** Convenience accessor: `RoundlyTheme.extras.isTrueDark`. */
object RoundlyTheme {
    val extras: RoundlyExtras
        @Composable get() = LocalRoundlyExtras.current
}
