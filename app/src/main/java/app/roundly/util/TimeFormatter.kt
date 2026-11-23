package app.roundly.util

import android.content.Context
import android.text.format.DateFormat
import app.roundly.core.model.TimeFormat
import app.roundly.core.time.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Formats clock times honouring the 12/24-hour setting (SYSTEM follows the device). */
@Singleton
class TimeFormatter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    fun is24Hour(format: TimeFormat): Boolean = when (format) {
        TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
        TimeFormat.H12 -> false
        TimeFormat.H24 -> true
    }

    fun time(epochMs: Long, format: TimeFormat): String {
        val locale = Locale.getDefault()
        val skeleton = if (is24Hour(format)) "Hm" else "hm"
        val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
        return DateTimeFormatter.ofPattern(pattern, locale)
            .format(Instant.ofEpochMilli(epochMs).atZone(clock.zone()))
    }
}
