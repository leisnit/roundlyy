package app.roundly.core.sync

import app.roundly.core.model.Alarm
import app.roundly.core.model.AlarmType
import app.roundly.core.model.TimeFormat
import app.roundly.core.schedule.NextAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncCodecTest {

    private fun alarm(id: Long, type: AlarmType, next: Long?) = Alarm(
        id = id,
        label = "A$id",
        type = type,
        intervalMs = 60_000,
        anchorAt = 0,
        nextTriggerAt = next,
        createdAt = 0,
    )

    @Test
    fun payloadRoundTripKeepsHiddenStateForTheWatch() {
        val payload = WatchSyncPayload(
            alarms = listOf(alarm(1, AlarmType.IRREGULAR, 500).copy(pendingRandomTimes = listOf(500, 900))),
            pausedAll = false,
            snoozeMinutes = 5,
            ringTimeoutMinutes = 3,
            timeFormat = TimeFormat.H24,
            sentAt = 42,
        )
        val decoded = SyncCodec.decode(WatchSyncPayload.serializer(), SyncCodec.encode(WatchSyncPayload.serializer(), payload))
        assertEquals(payload, decoded)
    }

    @Test
    fun garbageDecodesToNull() {
        assertNull(SyncCodec.decode(WatchResponse.serializer(), "nope".toByteArray()))
    }

    @Test
    fun nextAlertPrefersAlarmsWhoseTimeMayBeShown() {
        val irregular = alarm(1, AlarmType.IRREGULAR, 100)
        val fixed = alarm(2, AlarmType.FIXED, 900)
        assertEquals(fixed, NextAlert.pick(listOf(irregular, fixed), pausedAll = false))
        assertEquals(irregular, NextAlert.pick(listOf(irregular), pausedAll = false))
        assertNull(NextAlert.pick(listOf(fixed), pausedAll = true))
    }
}
