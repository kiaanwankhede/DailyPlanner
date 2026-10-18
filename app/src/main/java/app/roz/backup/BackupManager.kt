package app.roz.backup

import android.content.Context
import android.net.Uri
import app.roz.core.BackupCodec
import app.roz.core.BackupPayload
import app.roz.core.BackupRetention
import app.roz.core.Exports
import app.roz.data.Prefs
import app.roz.data.Repository
import app.roz.data.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Writes the encrypted backup (SPEC.md §7): the newest copy to the file the user picked (for example
 * in Google Drive), and a rolling history of copies in the app's own storage.
 */
class BackupManager(
    private val context: Context,
    private val repository: Repository,
    private val prefs: Prefs,
    private val secrets: SecretStore,
) {
    private val historyDir get() = File(context.filesDir, "backups").apply { mkdirs() }

    suspend fun backUpNow(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val passphrase = secrets.passphrase() ?: error("Set up backup first")
            val code = secrets.recoveryCode() ?: error("Set up backup first")
            val payload = repository.snapshot(prefs.settings)
            val items = payload.itemCount()
            val bytes = BackupCodec.encode(payload, passphrase, code.toCharArray())
            passphrase.fill(' ')

            // Local history first, so a good copy always exists before anything is overwritten.
            val name = BackupRetention.fileName(LocalDateTime.now())
            File(historyDir, name).writeBytes(bytes)
            val files = historyDir.list()?.toList().orEmpty()
            BackupRetention.toDelete(files).forEach { File(historyDir, it).delete() }

            val uri = prefs.state.value.backupUri ?: error("Choose where to save the backup")
            // A wiped or reset phone must never replace a good backup with an empty one.
            val lastItems = prefs.state.value.lastBackupItems
            if (items == 0 && lastItems > 0) error("Nothing to back up — kept your last backup as it was")
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: error("Couldn't open the backup file. Choose the location again.")
            prefs.backupSucceeded(Instant.now(), items)
        }.onFailure { prefs.backupFailed(it.message) }
    }

    fun localCopies(): List<File> = historyDir.listFiles()?.filter { BackupRetention.parse(it.name) != null }
        ?.sortedByDescending { it.name }.orEmpty()

    suspend fun open(uri: Uri, secret: CharArray): Result<BackupPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Couldn't open that file")
            BackupCodec.decode(bytes, secret)
        }
    }

    suspend fun open(file: File, secret: CharArray): Result<BackupPayload> = withContext(Dispatchers.IO) {
        runCatching { BackupCodec.decode(file.readBytes(), secret) }
    }

    suspend fun restore(payload: BackupPayload) = withContext(Dispatchers.IO) {
        repository.restore(payload)
        prefs.setSettings(payload.settings)
    }

    suspend fun export(uri: Uri, kind: ExportKind): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = repository.snapshot(prefs.settings)
            val text = when (kind) {
                ExportKind.HABITS_CSV -> Exports.habitsCsv(payload.habits, payload.habitLog)
                ExportKind.CHORES_CSV -> Exports.choresCsv(payload.chores, payload.choreLog)
                ExportKind.BIRTHDAYS_ICS -> Exports.birthdaysIcs(payload.people, LocalDate.now())
            }
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: error("Couldn't open that file")
        }
    }
}

enum class ExportKind(val fileName: String, val mime: String) {
    HABITS_CSV("roz-habits.csv", "text/csv"),
    CHORES_CSV("roz-chores.csv", "text/csv"),
    BIRTHDAYS_ICS("roz-birthdays.ics", "text/calendar"),
}

fun BackupPayload.itemCount() = habits.size + chores.size + reminders.size + people.size
