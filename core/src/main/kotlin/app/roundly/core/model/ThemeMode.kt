package app.roundly.core.model

import kotlinx.serialization.Serializable

/** App theme preference. [TRUE_DARK] uses a near-black background and dimmed accent for night use. */
@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    TRUE_DARK,
}
