package app.roundly.wear.tile

import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.TypeBuilders
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInt32
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import app.roundly.core.model.Alarm
import app.roundly.core.schedule.NextAlert
import app.roundly.core.sync.WatchCommand
import app.roundly.core.sync.WatchCommandType
import app.roundly.wear.MainActivity
import app.roundly.wear.R
import app.roundly.wear.data.WatchState
import app.roundly.wear.scheduling.wearDeps
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import java.util.UUID

/**
 * Tile: the next alert (label and a live countdown) and a Pause all / Resume all button.
 * The countdown is a platform-evaluated dynamic expression, so the tile never polls; it is
 * re-requested only when the schedule changes.
 */
class NextAlertTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.Default) {
            val deps = applicationContext.wearDeps()
            if (requestParams.currentState.lastClickableId == ID_TOGGLE_PAUSE) {
                val paused = deps.repository().current().pausedAll
                deps.scheduler().setPausedAllLocally(!paused)
                deps.phoneLink().sendCommand(
                    WatchCommand(
                        id = UUID.randomUUID().toString(),
                        type = if (paused) WatchCommandType.RESUME_ALL else WatchCommandType.PAUSE_ALL,
                        issuedAt = System.currentTimeMillis(),
                    ),
                )
            }
            val state = deps.repository().current()
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(state)))
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.Default) {
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
        }

    private fun layout(state: WatchState): LayoutElementBuilders.LayoutElement {
        val next = NextAlert.pick(state.alarms, state.pausedAll)
        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text(getString(R.string.tile_next_alert), 12f, MUTED))
            .addContent(spacer(4f))

        when {
            state.syncedAt == null -> column.addContent(text(getString(R.string.tile_open_phone), 14f, INK))
            state.pausedAll -> column.addContent(text(getString(R.string.alarm_state_paused), 20f, INK))
            next == null -> column.addContent(text(getString(R.string.tile_nothing), 16f, INK))
            else -> {
                column.addContent(text(next.label.ifBlank { getString(R.string.alert_default_label) }, 16f, INK, maxLines = 2))
                column.addContent(spacer(2f))
                column.addContent(countdown(next))
            }
        }

        if (state.syncedAt != null && state.alarms.isNotEmpty()) {
            column.addContent(spacer(12f))
            column.addContent(pauseButton(state.pausedAll))
        }

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId(ID_OPEN_APP)
                            .setOnClick(
                                ActionBuilders.LaunchAction.Builder()
                                    .setAndroidActivity(
                                        ActionBuilders.AndroidActivity.Builder()
                                            .setPackageName(packageName)
                                            .setClassName(MainActivity::class.java.name)
                                            .build(),
                                    )
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .addContent(column.build())
            .build()
    }

    /** "in 12 min" / "in 1 h 05 min", recomputed by the system every minute without waking the app. */
    private fun countdown(alarm: Alarm): LayoutElementBuilders.LayoutElement {
        if (!NextAlert.revealsTime(alarm)) return text(getString(R.string.within_window), 14f, ACCENT, maxLines = 2)
        val at = alarm.nextTriggerAt ?: return text(getString(R.string.tile_nothing), 14f, INK)

        val duration = DynamicInstant.platformTimeWithSecondsPrecision()
            .durationUntil(DynamicInstant.withSecondsPrecision(Instant.ofEpochMilli(at)))
        val totalMinutes = duration.toIntMinutes()
        val twoDigits = DynamicInt32.IntFormatter.Builder().setMinIntegerDigits(2).build()
        val prefix = DynamicString.constant(getString(R.string.tile_in_prefix))
        val hoursText = prefix
            .concat(duration.toIntHours().format())
            .concat(DynamicString.constant(getString(R.string.tile_hours_infix)))
            .concat(duration.getMinutesPart().format(twoDigits))
            .concat(DynamicString.constant(getString(R.string.tile_minutes_suffix)))
        val minutesText = prefix
            .concat(totalMinutes.format())
            .concat(DynamicString.constant(getString(R.string.tile_minutes_suffix)))
        val dynamic = DynamicString.onCondition(totalMinutes.gte(60)).use(hoursText)
            .elseUse(
                DynamicString.onCondition(totalMinutes.gte(1)).use(minutesText)
                    .elseUse(getString(R.string.tile_now)),
            )

        val staticMinutes = ((at - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
        val fallback = getString(R.string.tile_in_prefix) + staticMinutes + getString(R.string.tile_minutes_suffix)

        return LayoutElementBuilders.Text.Builder()
            .setText(TypeBuilders.StringProp.Builder(fallback).setDynamicValue(dynamic).build())
            .setLayoutConstraintsForDynamicText(
                TypeBuilders.StringLayoutConstraint.Builder(getString(R.string.tile_countdown_widest)).build(),
            )
            .setFontStyle(font(22f, ACCENT, bold = false))
            .build()
    }

    private fun pauseButton(paused: Boolean): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(wrap())
            .setHeight(dp(40f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(if (paused) ACCENT else CONTAINER))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(20f)).build())
                            .build(),
                    )
                    .setPadding(ModifiersBuilders.Padding.Builder().setStart(dp(18f)).setEnd(dp(18f)).build())
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId(ID_TOGGLE_PAUSE)
                            .setOnClick(ActionBuilders.LoadAction.Builder().build())
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                text(
                    getString(if (paused) R.string.action_resume_all else R.string.action_pause_all),
                    14f,
                    if (paused) ON_ACCENT else INK,
                ),
            )
            .build()

    private fun text(value: String, size: Float, color: Int, maxLines: Int = 1): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setMaxLines(maxLines)
            .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
            .setFontStyle(font(size, color, bold = size >= 16f))
            .build()

    private fun font(size: Float, color: Int, bold: Boolean) = LayoutElementBuilders.FontStyle.Builder()
        .setSize(sp(size))
        .setColor(argb(color))
        .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
        .build()

    private fun spacer(height: Float) = LayoutElementBuilders.Spacer.Builder().setHeight(dp(height)).build()

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val ID_TOGGLE_PAUSE = "toggle_pause"
        const val ID_OPEN_APP = "open_app"
        const val INK = 0xFFDFE4E2.toInt()
        const val MUTED = 0xFFA9B0AE.toInt()
        const val ACCENT = 0xFF7FC4BD.toInt()
        const val ON_ACCENT = 0xFF00201E.toInt()
        const val CONTAINER = 0xFF1B1F1E.toInt()
    }
}
