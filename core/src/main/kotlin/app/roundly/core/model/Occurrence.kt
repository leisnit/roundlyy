package app.roundly.core.model

/**
 * Stable identity of a single alert occurrence, shared by phone and watch so
 * that whichever device is acknowledged first wins and the other can cancel.
 */
@JvmInline
value class OccurrenceId(val value: String) {
    companion object {
        private const val SEPARATOR = "@"

        fun of(alarmId: Long, scheduledAt: Long): OccurrenceId =
            OccurrenceId("$alarmId$SEPARATOR$scheduledAt")

        /** Parses an id produced by [of]; returns null if it is malformed. */
        fun parse(raw: String): Pair<Long, Long>? {
            val parts = raw.split(SEPARATOR)
            if (parts.size != 2) return null
            val alarmId = parts[0].toLongOrNull() ?: return null
            val scheduledAt = parts[1].toLongOrNull() ?: return null
            return alarmId to scheduledAt
        }
    }
}
