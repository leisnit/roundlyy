package app.roundly.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/*
 * Same calm language as the phone: black background, one teal accent, softened white text.
 * Wear Material 3 typography already uses tabular numerals for its numeral styles.
 */
private val Teal = Color(0xFF7FC4BD)
private val TealDim = Color(0xFF5E9C96)
private val TealContainer = Color(0xFF1D4F4B)
private val OnTeal = Color(0xFF00201E)
private val Ink = Color(0xFFDFE4E2)
private val InkVariant = Color(0xFFA9B0AE)

val RoundlyWearColors = ColorScheme(
    primary = Teal,
    primaryDim = TealDim,
    primaryContainer = TealContainer,
    onPrimary = OnTeal,
    onPrimaryContainer = Color(0xFFCDE8E4),
    secondary = Color(0xFFB4CAC7),
    secondaryContainer = Color(0xFF2A3735),
    onSecondaryContainer = Color(0xFFD0E6E3),
    background = Color.Black,
    onBackground = Ink,
    surfaceContainerLow = Color(0xFF111414),
    surfaceContainer = Color(0xFF1B1F1E),
    surfaceContainerHigh = Color(0xFF252A29),
    onSurface = Ink,
    onSurfaceVariant = InkVariant,
    outline = Color(0xFF727876),
    outlineVariant = Color(0xFF303534),
)

@Composable
fun RoundlyWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RoundlyWearColors, content = content)
}
