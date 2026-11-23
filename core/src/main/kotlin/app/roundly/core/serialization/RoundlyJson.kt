package app.roundly.core.serialization

import kotlinx.serialization.json.Json

/** Shared JSON configurations. Lenient on read (unknown keys ignored) so older apps accept newer files. */
object RoundlyJson {
    /** Compact form for Room JSON columns and Data Layer payloads. */
    val compact: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    /** Human-readable form for Export files. */
    val pretty: Json = Json(from = compact) {
        prettyPrint = true
        prettyPrintIndent = "  "
    }
}
