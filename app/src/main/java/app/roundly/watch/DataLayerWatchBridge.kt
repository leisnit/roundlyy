package app.roundly.watch

import android.content.Context
import app.roundly.alert.ActiveAlert
import app.roundly.core.sync.AlertCancelMessage
import app.roundly.core.sync.AlertFiredMessage
import app.roundly.core.sync.DataLayerContract
import app.roundly.core.sync.SyncCodec
import app.roundly.core.sync.WatchSyncPayload
import app.roundly.core.time.Clock
import app.roundly.data.repository.AlarmRepository
import app.roundly.data.repository.SettingsRepository
import app.roundly.notifications.AlertNotifications
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phone side of the Wearable Data Layer. Bluetooth only: a watch counts as reachable only when it
 * is a nearby (directly connected) node that advertises the Roundly watch capability.
 * Every call fails soft: without Play services or a watch, alerts simply stay on the phone.
 */
@Singleton
class DataLayerWatchBridge @Inject constructor(
    @ApplicationContext context: Context,
    private val alarms: AlarmRepository,
    private val settings: SettingsRepository,
    private val notifications: AlertNotifications,
    private val clock: Clock,
) : WatchBridge {

    private val capabilityClient = Wearable.getCapabilityClient(context)
    private val messageClient = Wearable.getMessageClient(context)
    private val dataClient = Wearable.getDataClient(context)

    private val syncMutex = Mutex()
    private var lastSyncedFingerprint: Int? = null

    override suspend fun isWatchReachable(): Boolean = nearbyWatches().isNotEmpty()

    override suspend fun sendAlert(alert: ActiveAlert): Boolean {
        val nodes = nearbyWatches()
        if (nodes.isEmpty()) return false
        val current = settings.current()
        val message = AlertFiredMessage(
            occurrenceId = alert.occurrenceId,
            alarmId = alert.alarmId,
            scheduledAt = alert.scheduledAt,
            label = notifications.displayLabel(alert),
            alertMode = alert.alertMode,
            detail = notifications.detailLine(alert.copy(watchOnly = false), current),
            isSnoozeRepeat = alert.isSnoozeRepeat,
            phoneSilent = alert.watchOnly,
        )
        val bytes = SyncCodec.encode(AlertFiredMessage.serializer(), message)
        return nodes.map { send(it, DataLayerContract.MSG_ALERT_FIRED, bytes) }.any { it }
    }

    override suspend fun sendCancel(occurrenceId: String) {
        val bytes = SyncCodec.encode(AlertCancelMessage.serializer(), AlertCancelMessage(occurrenceId))
        nearbyWatches().forEach { send(it, DataLayerContract.MSG_ALERT_CANCEL, bytes) }
    }

    /**
     * Publishes the schedule as a DataItem. The Data Layer caches it and delivers it when the watch
     * next connects. Identical state is not re-sent, to avoid needless Bluetooth wake-ups.
     */
    override suspend fun syncSchedule() {
        syncMutex.withLock {
            runCatching {
                val current = settings.current()
                val payload = WatchSyncPayload(
                    alarms = alarms.getAlarms().filterNot { it.isRecentTimer },
                    pausedAll = settings.currentState().pausedAll,
                    snoozeMinutes = current.snoozeMinutes,
                    ringTimeoutMinutes = current.ringTimeoutMinutes,
                    timeFormat = current.timeFormat,
                    sentAt = 0,
                )
                val fingerprint = payload.hashCode()
                if (fingerprint == lastSyncedFingerprint) return@runCatching

                val request = PutDataMapRequest.create(DataLayerContract.PATH_STATE).apply {
                    dataMap.putByteArray(
                        DataLayerContract.KEY_PAYLOAD,
                        SyncCodec.encode(WatchSyncPayload.serializer(), payload.copy(sentAt = clock.now())),
                    )
                }.asPutDataRequest().setUrgent()
                dataClient.putDataItem(request).await()
                lastSyncedFingerprint = fingerprint
            }
        }
    }

    /** Forces the next [syncSchedule] to publish, for example after the watch reconnects. */
    fun invalidate() {
        lastSyncedFingerprint = null
    }

    private suspend fun nearbyWatches(): List<Node> = withTimeoutOrNull(REACHABILITY_TIMEOUT_MS) {
        runCatching {
            capabilityClient
                .getCapability(DataLayerContract.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
                .filter { it.isNearby }
        }.getOrDefault(emptyList())
    } ?: emptyList()

    private suspend fun send(node: Node, path: String, bytes: ByteArray): Boolean = runCatching {
        withTimeoutOrNull(SEND_TIMEOUT_MS) { messageClient.sendMessage(node.id, path, bytes).await() } != null
    }.getOrDefault(false)

    private companion object {
        const val REACHABILITY_TIMEOUT_MS = 2_000L
        const val SEND_TIMEOUT_MS = 3_000L
    }
}
