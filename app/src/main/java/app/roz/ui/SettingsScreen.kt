package app.roz.ui

import androidx.compose.material3.Switch
import androidx.compose.foundation.clickable
import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.roz.backup.ExportKind
import app.roz.core.BackupPayload
import app.roz.core.NewerBackupException
import app.roz.core.NotABackupException
import app.roz.core.Person
import app.roz.core.RecoveryCode
import app.roz.core.Reminders
import app.roz.core.WrongPassphraseException
import app.roz.core.preview
import app.roz.data.ContactBirthdays
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(state: UiState, vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = state.prefs
    var setUp by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoreFile by remember { mutableStateOf<File?>(null) }
    var pickLocal by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<ExportKind?>(null) }

    val chooseLocation = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            vm.setBackupLocation(uri)
        }
    }
    val pickRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) restoreUri = uri }
    val exportTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val kind = pendingExport
        if (uri != null && kind != null) vm.export(uri, kind)
        pendingExport = null
    }
    LaunchedEffect(pendingExport) { pendingExport?.let { exportTo.launch(it.fileName) } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
        item {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = palette.ink) }
                Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink)
            }
        }

        item {
            SectionLabel("Backup")
            ItemCard(contentPadding = PaddingValues(14.dp)) {
                val last = prefs?.lastBackup?.atZone(ZoneId.systemDefault())
                    ?.format(DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH))
                Text(
                    when {
                        !state.backupSetUp -> "Not set up yet. Your backup is encrypted with a passphrase only you know."
                        last != null -> "Last backup: $last"
                        else -> "No backup yet"
                    },
                    color = palette.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
                prefs?.lastBackupError?.let { Text(it, color = palette.late, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
                Text(
                    "Backs up every night while charging on Wi-Fi, and a few minutes after big changes. " +
                        "The phone also keeps the last 7 copies plus one from each month.",
                    color = palette.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(10.dp))
                if (!state.backupSetUp) {
                    WideButton(if (prefs?.backupUri == null) "Set up backup" else "Finish setting up backup") { setUp = true }
                } else {
                    WideButton("Back up now") { vm.backUpNow() }
                    WideButton("Change where it's saved") { chooseLocation.launch("roz-backup.rozb") }
                }
                WideButton("Restore from a backup file") { pickRestore.launch(arrayOf("*/*")) }
                WideButton("Restore a copy on this phone") { pickLocal = true }
            }
        }

        item {
            SectionLabel("Notifications")
            ItemCard(contentPadding = PaddingValues(14.dp)) {
                val s = prefs?.settings
                SettingRow("Morning summary", s?.let { Reminders.timeLabel(it.morningMinute) } ?: "") {
                    pickTime(context, s?.morningMinute ?: (8 * 60)) { vm.setMorning(it) }
                }
                SettingRow("Evening nudge for open habits", s?.let { Reminders.timeLabel(it.eveningMinute) } ?: "") {
                    pickTime(context, s?.eveningMinute ?: (20 * 60 + 30)) { vm.setEvening(it) }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clickable { vm.setHideFocusOnLock(!state.hideFocusOnLock) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Hide top priority on lock screen", color = palette.ink, fontSize = 15.sp)
                        Text(
                            "The lock screen then only says “Top priority”. The details show once you unlock.",
                            color = palette.muted, fontSize = 12.sp,
                        )
                    }
                    Switch(checked = state.hideFocusOnLock, onCheckedChange = { vm.setHideFocusOnLock(it) })
                }
            }
        }

        item {
            SectionLabel("Export")
            ItemCard(contentPadding = PaddingValues(14.dp)) {
                WideButton("Habit history (CSV)") { pendingExport = ExportKind.HABITS_CSV }
                WideButton("Chore log (CSV)") { pendingExport = ExportKind.CHORES_CSV }
                WideButton("Birthdays for Google Calendar (.ics)") { pendingExport = ExportKind.BIRTHDAYS_ICS }
            }
        }

        item { SectionLabel("Recently deleted") }
        if (state.deleted.isEmpty()) {
            item { Text("Nothing deleted in the last 30 days.", color = palette.muted, fontSize = 13.sp) }
        }
        items(state.deleted, key = { "${it.type}${it.id}" }) { item ->
            ItemCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, color = palette.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(item.type.name.lowercase().replaceFirstChar { it.uppercase() }, color = palette.muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { vm.restoreDeleted(item) }) { Text("Restore", color = palette.habit) }
                    TextButton(onClick = { vm.deleteForever(item) }) { Text("Delete", color = palette.late) }
                }
            }
            SmallGap()
        }
        item {
            Spacer(Modifier.height(24.dp))
            Text("Roz · everything stays on this phone except your encrypted backup.", color = palette.muted, fontSize = 12.sp)
        }
    }

    if (setUp) {
        BackupSetupDialog(
            vm, needsSecrets = !state.secretsSaved,
            onDismiss = { setUp = false },
            onReady = {
                setUp = false
                chooseLocation.launch("roz-backup.rozb")
            },
        )
    }
    restoreUri?.let { uri -> RestoreDialog(vm, open = { vm.openBackup(uri, it) }, onDismiss = { restoreUri = null }) }
    restoreFile?.let { file -> RestoreDialog(vm, open = { vm.openBackup(file, it) }, onDismiss = { restoreFile = null }) }
    if (pickLocal) {
        val copies = remember { vm.localCopies() }
        AlertDialog(
            onDismissRequest = { pickLocal = false },
            title = { Text("Copies on this phone") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (copies.isEmpty()) Text("No copies yet.")
                    copies.forEach { f ->
                        TextButton(onClick = { pickLocal = false; restoreFile = f }) {
                            Text(f.name.removePrefix("roz-backup-").removeSuffix(".rozb"), color = palette.ink)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickLocal = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun WideButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(46.dp), shape = RoundedCornerShape(12.dp)) {
        Text(text, color = palette.ink, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), color = palette.ink, fontSize = 15.sp)
        TextButton(onClick = onClick) { Text(value, color = palette.habit, fontWeight = FontWeight.Bold) }
    }
}

/** Passphrase twice, then the recovery code to write down, then the file picker. */
@Composable
private fun BackupSetupDialog(vm: AppViewModel, needsSecrets: Boolean, onDismiss: () -> Unit, onReady: () -> Unit) {
    var step by remember { mutableStateOf(if (needsSecrets) 0 else 2) }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val code = remember { RecoveryCode.generate() }
    val tooShort = first.length < 8
    val mismatch = second.isNotEmpty() && first != second

    when (step) {
        0 -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Choose a backup passphrase") },
            text = {
                Column {
                    Text("Your backup is encrypted with it. Pick something you'll remember — at least 8 characters.", fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    SecretField(first, "Passphrase") { first = it }
                    Spacer(Modifier.height(8.dp))
                    SecretField(second, "Type it again") { second = it }
                    if (mismatch) Text("They don't match yet.", color = palette.late, fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { step = 1 }, enabled = !tooShort && first == second) { Text("Next") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        1 -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Write down your recovery code") },
            text = {
                Column {
                    Text("If you ever forget the passphrase, this code also opens your backup. It's shown only once.", fontSize = 14.sp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        code,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.ground).padding(14.dp),
                        fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = palette.ink,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveSecrets(first.toCharArray(), code)
                    onReady()
                }) { Text("I've written it down") }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("Back") } },
        )
        else -> LaunchedEffect(Unit) { onReady() }
    }
}

@Composable
private fun SecretField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
}

/** Asks for the passphrase or recovery code, shows what's in the backup, then asks before replacing. */
@Composable
private fun RestoreDialog(vm: AppViewModel, open: suspend (CharArray) -> Result<BackupPayload>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var secret by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var payload by remember { mutableStateOf<BackupPayload?>(null) }

    val p = payload
    if (p == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Open backup") },
            text = {
                Column {
                    Text("Type your passphrase or your recovery code.", fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    SecretField(secret, "Passphrase or recovery code") { secret = it; error = null }
                    error?.let { Text(it, color = palette.late, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
                }
            },
            confirmButton = {
                TextButton(enabled = secret.isNotEmpty() && !busy, onClick = {
                    busy = true
                    scope.launch {
                        open(secret.toCharArray())
                            .onSuccess { payload = it }
                            .onFailure {
                                error = when (it) {
                                    is WrongPassphraseException -> "That doesn't open this backup."
                                    is NotABackupException -> "This file isn't a Roz backup."
                                    is NewerBackupException -> "This backup is from a newer version of Roz. Update the app first."
                                    else -> it.message ?: "Couldn't open it."
                                }
                            }
                        busy = false
                    }
                }) { Text(if (busy) "Opening…" else "Open") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Replace what's on this phone?") },
            text = { Text(p.preview() + ".\n\nEverything currently in Roz on this phone will be replaced.", fontSize = 14.sp) },
            confirmButton = { TextButton(onClick = { vm.restore(p); onDismiss() }) { Text("Replace", color = palette.late) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}

/** The monthly "do you still know it?" check. */
@Composable
fun PassphraseCheckDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var attempt by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var ok by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (ok) "All good" else "Check your backup passphrase") },
        text = {
            if (ok) {
                Text("You still know it. We'll ask again in a month.")
            } else {
                Column {
                    Text("Type it to make sure you'd be able to restore your backup.", fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    SecretField(attempt, "Passphrase") { attempt = it; wrong = false }
                    if (wrong) {
                        Text(
                            "That's not it. If you've forgotten it, your recovery code still works — or set up backup again in Settings.",
                            color = palette.late, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (ok) {
                TextButton(onClick = onDismiss) { Text("Done") }
            } else {
                TextButton(onClick = { if (vm.checkPassphrase(attempt.toCharArray())) ok = true else wrong = true }) { Text("Check") }
            }
        },
        dismissButton = { if (!ok) TextButton(onClick = onDismiss) { Text("Later") } },
    )
}

/** Asks for contacts permission, then offers the birthdays it found that aren't in Roz yet. */
@Composable
fun ImportContactsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = vm.state.value
    var found by remember { mutableStateOf<List<Person>?>(null) }
    var denied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scope.launch {
                val all = withContext(Dispatchers.IO) { ContactBirthdays.read(context) }
                val existing = state.upcoming.map { Triple(it.person.name.lowercase(), it.person.month, it.person.day) }.toSet()
                found = all.filter { Triple(it.name.lowercase(), it.month, it.day) !in existing }
            }
        } else {
            denied = true
        }
    }
    LaunchedEffect(Unit) { ask.launch(Manifest.permission.READ_CONTACTS) }

    val list = found
    when {
        denied -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("No access to contacts") },
            text = { Text("You can still add birthdays by hand with ＋.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        list != null -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (list.isEmpty()) "Nothing new found" else "Found ${list.size} in your contacts") },
            text = {
                Text(
                    if (list.isEmpty()) "Your contacts have no birthdays that aren't in Roz already."
                    else list.take(8).joinToString("\n") { it.name } + if (list.size > 8) "\n…and ${list.size - 8} more" else "",
                )
            },
            confirmButton = {
                if (list.isNotEmpty()) TextButton(onClick = { vm.importContacts(list); onDismiss() }) { Text("Add all") }
                else TextButton(onClick = onDismiss) { Text("OK") }
            },
            dismissButton = { if (list.isNotEmpty()) TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}
