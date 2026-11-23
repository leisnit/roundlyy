package app.roundly.watch

import android.net.Uri
import app.roundly.alert.AlertAction
import app.roundly.core.model.OccurrenceId
import app.roundly.core.model.RespondedOn
import app.roundly.core.sync.DataLayerContract
import app.roundly.core.sync.SyncCodec
import app.roundly.core.sync.WatchAction
import app.roundly.core.sync.WatchCommand
import app.roundly.core.sync.WatchCommandType
import app.roundly.core.sync.WatchResponse
import app.roundly.scheduling.receiverDeps
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Receives watch responses and commands. They arrive as DataItems, which the Data Layer queues
 * while the devices are apart and delivers on reconnect. Each one is applied (the alert log's
 * "first answer wins" rules make duplicates harmless) and then deleted.
 */
class PhoneDataLayerListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // Copy out before returning: the buffer is released when this method returns.
        val items = dataEvents
            .filter { it.type == DataEvent.TYPE_CHANGED }
            .mapNotNull { event ->
                val path = event.dataItem.uri.path ?: return@mapNotNull null
                if (!path.startsWith(WATCH_PREFIX)) return@mapNotNull null
                val bytes = DataMapItem.fromDataItem(event.dataItem).dataMap.getByteArray(DataLayerContract.KEY_PAYLOAD)
                    ?: return@mapNotNull null
                Triple(event.dataItem.uri, path, bytes)
            }
        if (items.isEmpty()) return

        val deps = applicationContext.receiverDeps()
        val dataClient = Wearable.getDataClient(applicationContext)
        deps.scope().launch {
            val responses = items.filter { it.second.startsWith(DataLayerContract.PATH_WATCH_RESPONSE_PREFIX) }
                .mapNotNull { (uri, _, bytes) -> SyncCodec.decode(WatchResponse.serializer(), bytes)?.let { uri to it } }
                .sortedBy { it.second.respondedAt }
            val commands = items.filter { it.second.startsWith(DataLayerContract.PATH_WATCH_COMMAND_PREFIX) }
                .mapNotNull { (uri, _, bytes) -> SyncCodec.decode(WatchCommand.serializer(), bytes)?.let { uri to it } }
                .sortedBy { it.second.issuedAt }

            responses.forEach { (uri, response) ->
                handleResponse(response)
                delete(dataClient, uri)
            }
            commands.forEach { (uri, command) ->
                handleCommand(command)
                delete(dataClient, uri)
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        if (capabilityInfo.name != DataLayerContract.CAPABILITY_WATCH) return
        if (capabilityInfo.nodes.none { it.isNearby }) return
        val deps = applicationContext.receiverDeps()
        deps.scope().launch {
            (deps.watchBridge() as? DataLayerWatchBridge)?.invalidate()
            deps.watchBridge().syncSchedule()
        }
    }

    private suspend fun handleResponse(response: WatchResponse) {
        val deps = applicationContext.receiverDeps()
        when (response.action) {
            WatchAction.DONE -> deps.alertController().respond(
                response.occurrenceId, AlertAction.DONE, RespondedOn.WATCH, response.respondedAt,
            )
            WatchAction.SNOOZE -> deps.alertController().respond(
                response.occurrenceId, AlertAction.SNOOZE, RespondedOn.WATCH, response.respondedAt,
            )
            WatchAction.MISSED -> {
                val (alarmId, scheduledAt) = OccurrenceId.parse(response.occurrenceId) ?: return
                deps.scheduler().onMissed(alarmId, scheduledAt, label = "")
            }
        }
    }

    private suspend fun handleCommand(command: WatchCommand) {
        val deps = applicationContext.receiverDeps()
        when (command.type) {
            WatchCommandType.SET_ENABLED -> {
                val id = command.alarmId ?: return
                val enabled = command.enabled ?: return
                deps.scheduler().setEnabled(id, enabled)
                if (!enabled) deps.alertController().dismissAlarm(id)
            }
            WatchCommandType.PAUSE_ALL -> deps.scheduler().pauseAll()
            WatchCommandType.RESUME_ALL -> deps.scheduler().resumeAll()
        }
    }

    private suspend fun delete(client: com.google.android.gms.wearable.DataClient, uri: Uri) {
        runCatching { client.deleteDataItems(uri).await() }
    }

    private companion object {
        const val WATCH_PREFIX = "/roundly/watch/"
    }
}
