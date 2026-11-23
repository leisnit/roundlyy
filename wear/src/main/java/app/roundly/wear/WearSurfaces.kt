package app.roundly.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.roundly.wear.complication.NextAlertComplicationService
import app.roundly.wear.tile.NextAlertTileService

/**
 * The tile and complication only re-render when the schedule changes (their countdowns are
 * evaluated by the system), so there is no periodic refresh draining the battery.
 */
object WearSurfaces {
    fun refresh(context: Context) {
        runCatching { TileService.getUpdater(context).requestUpdate(NextAlertTileService::class.java) }
        runCatching {
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, NextAlertComplicationService::class.java))
                .requestUpdateAll()
        }
    }
}
