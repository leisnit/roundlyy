package app.roundly.core.schedule

import java.security.SecureRandom

/** Injectable randomness so irregular schedules can be tested deterministically. */
fun interface RandomSource {
    /** Returns a uniformly distributed value in `[0, bound)`. [bound] must be positive. */
    fun nextLong(bound: Long): Long
}

/** Production source. Irregular alert times must not be predictable, so this uses [SecureRandom]. */
class SecureRandomSource(private val random: SecureRandom = SecureRandom()) : RandomSource {
    override fun nextLong(bound: Long): Long {
        require(bound > 0) { "bound must be positive" }
        // Rejection sampling over [0, Long.MAX_VALUE] avoids modulo bias.
        val limit = Long.MAX_VALUE - (Long.MAX_VALUE % bound)
        while (true) {
            val candidate = random.nextLong() ushr 1
            if (candidate < limit) return candidate % bound
        }
    }
}
