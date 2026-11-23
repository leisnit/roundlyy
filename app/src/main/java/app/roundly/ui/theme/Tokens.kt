package app.roundly.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp

/** Spacing scale. Generous whitespace is the main design tool. */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    /** Horizontal screen gutter. */
    val gutter = 20.dp
}

/** Sizes that matter for accessibility and the thumb zone. */
object Sizes {
    val minTouchTarget = 48.dp
    val alertButtonHeight = 72.dp
    val primaryButtonHeight = 56.dp
    val iconBadge = 40.dp
    val divider = 0.5.dp
}

/**
 * Motion: subtle and quick (150-250 ms) on a standard decelerating curve.
 * Nothing springy or bouncy.
 */
object Motion {
    const val SHORT_MS = 150
    const val MEDIUM_MS = 200
    const val LONG_MS = 250

    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> short(): FiniteAnimationSpec<T> = tween(SHORT_MS, easing = Standard)
    fun <T> medium(): FiniteAnimationSpec<T> = tween(MEDIUM_MS, easing = Standard)
    fun <T> long(): FiniteAnimationSpec<T> = tween(LONG_MS, easing = Standard)
}
