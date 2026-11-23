package app.roundly.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Roundly palette: neutral, slightly cool surfaces with one refined accent (deep teal).
 * No gradients. Depth comes from tonal surface containers, not shadows.
 */

// Accent: deep teal
internal val Teal10 = Color(0xFF00201E)
internal val Teal20 = Color(0xFF0B3633)
internal val Teal30 = Color(0xFF1D4F4B)
internal val Teal40 = Color(0xFF1F6F6B)
internal val Teal80 = Color(0xFF7FC4BD)
internal val Teal90 = Color(0xFFCDE8E4)
internal val TealDim = Color(0xFF5E9C96)
internal val TealDimContainer = Color(0xFF14302D)

// Neutrals
internal val Neutral0 = Color(0xFF000000)
internal val Neutral3 = Color(0xFF050606)
internal val Neutral5 = Color(0xFF0B0D0D)
internal val Neutral6 = Color(0xFF0F1211)
internal val Neutral8 = Color(0xFF141817)
internal val Neutral10 = Color(0xFF171B1A)
internal val Neutral12 = Color(0xFF1B1F1E)
internal val Neutral17 = Color(0xFF252A29)
internal val Neutral22 = Color(0xFF303534)
internal val Neutral30 = Color(0xFF424847)
internal val Neutral50 = Color(0xFF727876)
internal val Neutral60 = Color(0xFF8C9290)
internal val Neutral80 = Color(0xFFC2C8C6)
internal val Neutral87 = Color(0xFFD6DBD9)
internal val Neutral90 = Color(0xFFDFE4E2)
internal val Neutral92 = Color(0xFFE6EAE8)
internal val Neutral94 = Color(0xFFECEFEE)
internal val Neutral96 = Color(0xFFF1F4F3)
internal val Neutral97 = Color(0xFFF7F8F7)
internal val Neutral100 = Color(0xFFFFFFFF)

// Supporting
internal val Slate30 = Color(0xFF384B49)
internal val Slate40 = Color(0xFF4F6361)
internal val Slate80 = Color(0xFFB4CAC7)
internal val Slate90 = Color(0xFFD0E6E3)
internal val Error30 = Color(0xFF8C1D18)
internal val Error40 = Color(0xFFB3261E)
internal val Error80 = Color(0xFFF2B8B5)
internal val Error90 = Color(0xFFF9DEDC)
internal val Warning = Color(0xFFB26B00)
internal val WarningDark = Color(0xFFE6B566)

val LightColors: ColorScheme = lightColorScheme(
    primary = Teal40,
    onPrimary = Neutral100,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal20,
    inversePrimary = Teal80,
    secondary = Slate40,
    onSecondary = Neutral100,
    secondaryContainer = Slate90,
    onSecondaryContainer = Slate30,
    tertiary = Slate40,
    onTertiary = Neutral100,
    tertiaryContainer = Slate90,
    onTertiaryContainer = Slate30,
    background = Neutral97,
    onBackground = Neutral10,
    surface = Neutral97,
    onSurface = Neutral10,
    surfaceVariant = Neutral92,
    onSurfaceVariant = Neutral30,
    surfaceTint = Teal40,
    inverseSurface = Neutral17,
    inverseOnSurface = Neutral94,
    error = Error40,
    onError = Neutral100,
    errorContainer = Error90,
    onErrorContainer = Error30,
    outline = Neutral50,
    outlineVariant = Neutral80,
    scrim = Neutral0,
    surfaceBright = Neutral97,
    surfaceDim = Neutral90,
    surfaceContainerLowest = Neutral100,
    surfaceContainerLow = Neutral96,
    surfaceContainer = Neutral94,
    surfaceContainerHigh = Neutral92,
    surfaceContainerHighest = Neutral90,
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Teal80,
    onPrimary = Teal10,
    primaryContainer = Teal30,
    onPrimaryContainer = Teal90,
    inversePrimary = Teal40,
    secondary = Slate80,
    onSecondary = Slate30,
    secondaryContainer = Slate30,
    onSecondaryContainer = Slate90,
    tertiary = Slate80,
    onTertiary = Slate30,
    tertiaryContainer = Slate30,
    onTertiaryContainer = Slate90,
    background = Neutral6,
    onBackground = Neutral90,
    surface = Neutral6,
    onSurface = Neutral90,
    surfaceVariant = Neutral22,
    onSurfaceVariant = Neutral80,
    surfaceTint = Teal80,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral17,
    error = Error80,
    onError = Error30,
    errorContainer = Error30,
    onErrorContainer = Error90,
    outline = Neutral60,
    outlineVariant = Neutral30,
    scrim = Neutral0,
    surfaceBright = Neutral22,
    surfaceDim = Neutral6,
    surfaceContainerLowest = Neutral3,
    surfaceContainerLow = Neutral10,
    surfaceContainer = Neutral12,
    surfaceContainerHigh = Neutral17,
    surfaceContainerHighest = Neutral22,
)

/** Night use: near-black background, dimmed accent, and no pure white anywhere. */
val TrueDarkColors: ColorScheme = DarkColors.copy(
    primary = TealDim,
    onPrimary = Neutral3,
    primaryContainer = TealDimContainer,
    onPrimaryContainer = Teal80,
    secondary = Neutral60,
    background = Neutral3,
    onBackground = Neutral87,
    surface = Neutral3,
    onSurface = Neutral87,
    onSurfaceVariant = Neutral60,
    surfaceTint = TealDim,
    outline = Neutral50,
    outlineVariant = Neutral17,
    surfaceBright = Neutral17,
    surfaceDim = Neutral3,
    surfaceContainerLowest = Neutral0,
    surfaceContainerLow = Neutral5,
    surfaceContainer = Neutral8,
    surfaceContainerHigh = Neutral10,
    surfaceContainerHighest = Neutral12,
)

/** Semantic status colours for the permissions health cards. */
data class StatusColors(val ok: Color, val warning: Color)

val LightStatusColors = StatusColors(ok = Teal40, warning = Warning)
val DarkStatusColors = StatusColors(ok = Teal80, warning = WarningDark)
