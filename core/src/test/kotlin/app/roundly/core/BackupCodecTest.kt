package app.roundly.core

import app.roundly.core.backup.BackupCodec
import app.roundly.core.backup.BackupFormatException
import app.roundly.core.backup.RoundlyBackup
import app.roundly.core.model.AfterLastStep
import app.roundly.core.model.Alarm
import app.roundly.core.model.AlarmType
import app.roundly.core.model.AppSettings
import app.roundly.core.model.BuiltInPresets
import app.roundly.core.model.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val minute = 60_000L

    private val irregular = Alarm(
        id = 7,
        label = "Posture",
        type = AlarmType.IRREGULAR,
        windowMs = 60 * minute,
        alertsPerWindow = 4,
        minGapMs = 5 * minute,
        currentWindowStart = 1_000L,
        pendingRandomTimes = listOf(7_771_111L, 7_772_222L),
        snoozeUntil = 7_773_333L,
        nextTriggerAt = 7_771_111L,
        anchorAt = 1_000L,
        createdAt = 1_000L,
    )

    private val stepped = Alarm(
        id = 8,
        label = "Warm-up",
        type = AlarmType.STEPPED,
        steps = listOf(Step(15 * minute, 4), Step(30 * minute, 2)),
        afterLastStep = AfterLastStep.CONTINUE,
        anchorAt = 1_000L,
        createdAt = 1_000L,
    )

    private fun backup(vararg alarms: Alarm) = RoundlyBackup(
        exportedAt = 5_000L,
        alarms = alarms.toList(),
        presets = BuiltInPresets.all,
        settings = AppSettings(snoozeMinutes = 10),
    )

    @Test
    fun roundTripPreservesDefinitions() {
        val decoded = BackupCodec.decode(BackupCodec.encode(backup(irregular, stepped)))
        assertEquals(2, decoded.alarms.size)
        assertEquals(stepped.steps, decoded.alarms[1].steps)
        assertEquals(AfterLastStep.CONTINUE, decoded.alarms[1].afterLastStep)
        assertEquals(10, decoded.settings.snoozeMinutes)
        assertEquals(BuiltInPresets.all.size, decoded.presets.size)
    }

    @Test
    fun exportNeverContainsHiddenRandomTimes() {
        val json = BackupCodec.encode(backup(irregular))
        // The hidden times and the snooze must not appear anywhere in the file.
        assertFalse(json.contains("7771111"))
        assertFalse(json.contains("7772222"))
        assertFalse(json.contains("7773333"))
        val decoded = BackupCodec.decode(json).alarms.single()
        assertTrue(decoded.pendingRandomTimes.isEmpty())
        assertNull(decoded.currentWindowStart)
        assertNull(decoded.snoozeUntil)
        assertNull(decoded.nextTriggerAt)
    }

    @Test
    fun invalidAlarmsAreDropped() {
        val impossible = irregular.copy(id = 9, windowMs = 30 * minute, alertsPerWindow = 12, minGapMs = 5 * minute)
        val decoded = BackupCodec.decode(BackupCodec.encode(backup(impossible, stepped)))
        assertEquals(listOf("Warm-up"), decoded.alarms.map { it.label })
    }

    @Test(expected = BackupFormatException::class)
    fun rejectsNonBackupJson() {
        BackupCodec.decode("""{"hello": "world"}""")
    }

    @Test(expected = BackupFormatException::class)
    fun rejectsGarbage() {
        BackupCodec.decode("not json")
    }

    @Test(expected = BackupFormatException::class)
    fun rejectsNewerFormatVersion() {
        val newer = BackupCodec.encode(backup(stepped)).replace("\"formatVersion\": 1", "\"formatVersion\": 99")
        BackupCodec.decode(newer)
    }

    @Test
    fun settingsAreClampedOnImport() {
        val json = BackupCodec.encode(backup(stepped)).replace("\"snoozeMinutes\": 10", "\"snoozeMinutes\": 500")
        assertEquals(AppSettings.SNOOZE_RANGE.last, BackupCodec.decode(json).settings.snoozeMinutes)
    }
}
