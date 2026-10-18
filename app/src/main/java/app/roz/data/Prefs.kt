package app.roz.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import app.roz.core.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDate

/** Small settings and backup bookkeeping. [state] lets screens react when any of it changes. */
class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("roz", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<PrefsState> = _state

    private fun read() = PrefsState(
        settings = Settings(prefs.getInt("morning", 8 * 60), prefs.getInt("evening", 20 * 60 + 30)),
        backupUri = prefs.getString("backup_uri", null)?.let(Uri::parse),
        lastBackup = prefs.getLong("last_backup", 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        lastBackupError = prefs.getString("last_backup_error", null),
        lastPassphraseCheck = prefs.getString("passphrase_check", null)?.let(LocalDate::parse),
        lastBackupItems = prefs.getInt("last_backup_items", 0),
    )

    private fun update(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit(commit = true, action = block)
        _state.value = read()
    }

    val settings: Settings get() = _state.value.settings

    fun setMorning(minute: Int) = update { putInt("morning", minute) }
    fun setEvening(minute: Int) = update { putInt("evening", minute) }
    fun setSettings(settings: Settings) = update {
        putInt("morning", settings.morningMinute)
        putInt("evening", settings.eveningMinute)
    }

    fun setBackupUri(uri: Uri?) = update { putString("backup_uri", uri?.toString()) }

    fun backupSucceeded(at: Instant, items: Int) = update {
        putLong("last_backup", at.toEpochMilli())
        putInt("last_backup_items", items)
        remove("last_backup_error")
    }

    fun backupFailed(message: String?) = update { putString("last_backup_error", message ?: "Backup failed") }

    fun passphraseChecked(on: LocalDate) = update { putString("passphrase_check", on.toString()) }
}

data class PrefsState(
    val settings: Settings,
    val backupUri: Uri?,
    val lastBackup: Instant?,
    val lastBackupError: String?,
    val lastPassphraseCheck: LocalDate?,
    val lastBackupItems: Int,
)
