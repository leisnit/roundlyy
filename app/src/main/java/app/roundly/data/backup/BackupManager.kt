package app.roundly.data.backup

import android.content.Context
import android.net.Uri
import app.roundly.core.backup.BackupCodec
import app.roundly.core.backup.BackupFormatException
import app.roundly.core.backup.RoundlyBackup
import app.roundly.core.model.Alarm
import app.roundly.core.time.Clock
import app.roundly.data.repository.AlarmRepository
import app.roundly.data.repository.PresetRepository
import app.roundly.data.repository.SettingsRepository
import app.roundly.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of an import, for the confirmation message in Settings. */
data class ImportSummary(
    val alarms: Int,
    val customPresets: Int,
    /** The newly inserted alarms (with their new ids), so the scheduler can arm them. */
    val importedAlarms: List<Alarm>,
)

/**
 * Manual Export / Import through the Storage Access Framework. The UI obtains a document [Uri]
 * with ActivityResultContracts.CreateDocument("application/json") or OpenDocument, and this
 * class reads or writes it. Nothing is sent anywhere else.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarms: AlarmRepository,
    private val presets: PresetRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun suggestedFileName(): String {
        val date = DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(clock.now()).atZone(clock.zone()))
        return "roundly-backup-$date.json"
    }

    /** Writes a backup to [uri]. Returns the number of alarms exported. */
    suspend fun export(uri: Uri): Result<Int> = withContext(io) {
        runCatching {
            val backup = RoundlyBackup(
                exportedAt = clock.now(),
                alarms = alarms.getAlarms(),
                presets = presets.getPresets(),
                settings = settings.current(),
            )
            val text = BackupCodec.encode(backup)
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Could not open the file for writing.")
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
            backup.alarms.size
        }
    }

    /**
     * Reads [uri] and replaces all alarms, custom presets and settings with its contents.
     * Before calling this, the caller must cancel every pending trigger for the current alarms
     * (their ids are about to disappear), then arm the returned alarms afterwards.
     */
    suspend fun import(uri: Uri): Result<ImportSummary> = withContext(io) {
        runCatching {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open the file.")
            val text = stream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(8 * 1024)
                val out = StringBuilder()
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    out.append(buffer, 0, read)
                    if (out.length > MAX_FILE_CHARS) throw BackupFormatException("This file is too large to be a Roundly backup.")
                }
                out.toString()
            }
            val backup = BackupCodec.decode(text)
            val inserted = alarms.replaceAll(backup.alarms)
            presets.replaceFromBackup(backup.presets)
            settings.replace(backup.settings)
            settings.setPausedAll(false)
            ImportSummary(
                alarms = inserted.size,
                customPresets = backup.presets.count { !it.builtIn },
                importedAlarms = inserted,
            )
        }
    }

    private companion object {
        /** 2 MB of text is far beyond any realistic backup. */
        const val MAX_FILE_CHARS = 2 * 1024 * 1024
    }
}
