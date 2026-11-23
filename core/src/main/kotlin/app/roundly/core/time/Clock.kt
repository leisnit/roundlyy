package app.roundly.core.time

import java.time.ZoneId

/**
 * Injectable time source. Production code uses [System]; tests supply a fixed or
 * steppable clock so scheduling is deterministic.
 */
interface Clock {
    /** Wall-clock time in epoch milliseconds. */
    fun now(): Long

    /** The device time zone, used for "start at a clock time" and DST handling. */
    fun zone(): ZoneId

    companion object {
        val System: Clock = object : Clock {
            override fun now(): Long = java.lang.System.currentTimeMillis()
            override fun zone(): ZoneId = ZoneId.systemDefault()
        }
    }
}
