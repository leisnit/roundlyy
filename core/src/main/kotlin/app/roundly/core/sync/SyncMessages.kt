package app.roundly.core.sync

import app.roundly.core.model.Alarm
import app.roundly.core.model.AlertMode
import app.roundly.core.model.TimeFormat
import app.roundly.core.serialization.RoundlyJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * The phone's state as the watch needs it. Includes each alarm's scheduling state (including the
 * hidden irregular times) so the watch can keep alerting with the same schedule when the phone is
 * out of range. The watch never displays the hidden times.
 */
@Serializable
data class WatchSyncPayload(
    val alarms: List<Alarm>,
    val pausedAll: Boolean,
    val snoozeMinutes: Int,
    val ringTimeoutMinutes: Int,
    val timeFormat: TimeFormat,
    val sentAt: Long,
)

/** Phone -> watch: an alert is ringing on the phone; show it on the watch. */
@Serializable
data class AlertFiredMessage(
    val occurrenceId: String,
    val alarmId: Long,
    val scheduledAt: Long,
    val label: String,
    val alertMode: AlertMode,
    val detail: String,
    val isSnoozeRepeat: Boolean,
    /** Watch only: the phone is silent, so the watch is the only device alerting. */
    val phoneSilent: Boolean,
)

/** Phone -> watch: this occurrence was answered elsewhere; stop ringing. */
@Serializable
data class AlertCancelMessage(val occurrenceId: String)

@Serializable
enum class WatchAction { DONE, SNOOZE, MISSED }

/** Watch -> phone: how an occurrence was answered on the watch. */
@Serializable
data class WatchResponse(
    val occurrenceId: String,
    val action: WatchAction,
    val respondedAt: Long,
)

@Serializable
enum class WatchCommandType { SET_ENABLED, PAUSE_ALL, RESUME_ALL }

/** Watch -> phone: a change made on the watch UI or tile. */
@Serializable
data class WatchCommand(
    val id: String,
    val type: WatchCommandType,
    val alarmId: Long? = null,
    val enabled: Boolean? = null,
    val issuedAt: Long,
)

/** Byte encoding for Data Layer payloads. */
object SyncCodec {
    fun <T> encode(serializer: KSerializer<T>, value: T): ByteArray =
        RoundlyJson.compact.encodeToString(serializer, value).toByteArray(Charsets.UTF_8)

    fun <T> decode(serializer: KSerializer<T>, bytes: ByteArray): T? = runCatching {
        RoundlyJson.compact.decodeFromString(serializer, bytes.toString(Charsets.UTF_8))
    }.getOrNull()
}
