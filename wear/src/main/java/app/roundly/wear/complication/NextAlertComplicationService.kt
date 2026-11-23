package app.roundly.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.roundly.core.schedule.NextAlert
import app.roundly.wear.MainActivity
import app.roundly.wear.R
import app.roundly.wear.scheduling.wearDeps
import java.time.Instant

/**
 * Short-text complication: time to the next alert. The watch face renders the countdown itself
 * (TimeDifferenceComplicationText), so this service is only asked again when the schedule changes.
 */
class NextAlertComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return NoDataComplicationData()
        val state = applicationContext.wearDeps().repository().current()
        val next = NextAlert.pick(state.alarms, state.pausedAll)
        return when {
            state.pausedAll -> shortText(getString(R.string.complication_paused), getString(R.string.alarm_state_paused))
            next == null -> shortText("--", getString(R.string.tile_nothing))
            !NextAlert.revealsTime(next) -> shortText(
                getString(R.string.complication_window),
                getString(R.string.within_window),
                title = next.label,
            )
            else -> {
                val at = next.nextTriggerAt ?: return NoDataComplicationData()
                val text = TimeDifferenceComplicationText.Builder(
                    TimeDifferenceStyle.SHORT_SINGLE_UNIT,
                    CountDownTimeReference(Instant.ofEpochMilli(at)),
                ).build()
                val description = PlainComplicationText.Builder(getString(R.string.complication_description, next.label)).build()
                ShortTextComplicationData.Builder(text, description)
                    .setTitle(PlainComplicationText.Builder(next.label.ifBlank { getString(R.string.app_name) }.take(8)).build())
                    .setTapAction(openApp())
                    .build()
            }
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return ShortTextComplicationData.Builder(
            PlainComplicationText.Builder("12m").build(),
            PlainComplicationText.Builder(getString(R.string.complication_label)).build(),
        ).setTitle(PlainComplicationText.Builder(getString(R.string.app_name)).build()).build()
    }

    private fun shortText(text: String, description: String, title: String? = null): ComplicationData {
        val builder = ShortTextComplicationData.Builder(
            PlainComplicationText.Builder(text).build(),
            PlainComplicationText.Builder(description).build(),
        ).setTapAction(openApp())
        title?.takeIf { it.isNotBlank() }?.let { builder.setTitle(PlainComplicationText.Builder(it.take(8)).build()) }
        return builder.build()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
