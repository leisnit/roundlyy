package app.roundly.wear.sync

import android.content.Context
import app.roundly.core.sync.DataLayerContract
import app.roundly.core.sync.SyncCodec
import app.roundly.core.sync.WatchCommand
import app.roundly.core.sync.WatchResponse
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watch side of the Bluetooth Data Layer link. Responses and commands are DataItems: they are
 * delivered at once when connected and queued by the Data Layer otherwise.
 */
@Singleton
class PhoneLink @Inject constructor(@ApplicationContext context: Context) {

    private val capabilityClient = Wearable.getCapabilityClient(context)
    private val dataClient = Wearable.getDataClient(context)

    /** True when the phone app is directly connected right now. */
    suspend fun isPhoneReachable(): Boolean = withTimeoutOrNull(REACHABILITY_TIMEOUT_MS) {
        runCatching {
            capabilityClient
                .getCapability(DataLayerContract.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
                .any { it.isNearby }
        }.getOrDefault(false)
    } ?: false

    suspend fun sendResponse(response: WatchResponse) {
        val path = DataLayerContract.PATH_WATCH_RESPONSE_PREFIX + safe(response.occurrenceId)
        put(path, SyncCodec.encode(WatchResponse.serializer(), response))
    }

    suspend fun sendCommand(command: WatchCommand) {
        val path = DataLayerContract.PATH_WATCH_COMMAND_PREFIX + safe(command.id)
        put(path, SyncCodec.encode(WatchCommand.serializer(), command))
    }

    private suspend fun put(path: String, bytes: ByteArray) {
        runCatching {
            val request = PutDataMapRequest.create(path).apply {
                dataMap.putByteArray(DataLayerContract.KEY_PAYLOAD, bytes)
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request).await()
        }
    }

    private fun safe(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private companion object {
        const val REACHABILITY_TIMEOUT_MS = 2_000L
    }
}
