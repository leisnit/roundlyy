package app.roundly.wear.sync

import app.roundly.core.sync.AlertCancelMessage
import app.roundly.core.sync.AlertFiredMessage
import app.roundly.core.sync.DataLayerContract
import app.roundly.core.sync.SyncCodec
import app.roundly.core.sync.WatchSyncPayload
import app.roundly.wear.alert.AlertOrigin
import app.roundly.wear.alert.WatchAlert
import app.roundly.wear.scheduling.wearDeps
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.launch

/**
 * Phone -> watch: the synced schedule (DataItem) and live alert events (messages).
 */
class WearDataLayerListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // Copy the newest state out before the buffer is released.
        val bytes = dataEvents
            .filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == DataLayerContract.PATH_STATE }
            .mapNotNull { DataMapItem.fromDataItem(it.dataItem).dataMap.getByteArray(DataLayerContract.KEY_PAYLOAD) }
            .lastOrNull()
            ?: return
        val payload = SyncCodec.decode(WatchSyncPayload.serializer(), bytes) ?: return

        val deps = applicationContext.wearDeps()
        deps.scope().launch {
            val removed = deps.repository().replaceFromPhone(payload, System.currentTimeMillis())
            deps.scheduler().rescheduleAll(removed)
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val deps = applicationContext.wearDeps()
        when (messageEvent.path) {
            DataLayerContract.MSG_ALERT_FIRED -> {
                val message = SyncCodec.decode(AlertFiredMessage.serializer(), messageEvent.data) ?: return
                deps.scope().launch {
                    deps.alertController().present(
                        WatchAlert(
                            occurrenceId = message.occurrenceId,
                            alarmId = message.alarmId,
                            scheduledAt = message.scheduledAt,
                            label = message.label,
                            detail = message.detail,
                            alertMode = message.alertMode,
                            origin = AlertOrigin.PHONE,
                            isSnoozeRepeat = message.isSnoozeRepeat,
                        ),
                    )
                }
            }
            DataLayerContract.MSG_ALERT_CANCEL -> {
                val message = SyncCodec.decode(AlertCancelMessage.serializer(), messageEvent.data) ?: return
                deps.alertController().cancelFromPhone(message.occurrenceId)
            }
        }
    }
}
