package app.roz.ui

import app.roz.core.ChoreAction
import app.roz.core.HabitStats
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.roz.RozApplication
import app.roz.backup.BackupWorker
import app.roz.backup.ExportKind
import app.roz.core.BackupHealth
import app.roz.core.BackupPayload
import app.roz.core.Chore
import app.roz.core.ChoreLogEntry
import app.roz.core.ChoreSchedule
import app.roz.core.ChoreToday
import app.roz.core.Focus
import app.roz.core.FocusPick
import app.roz.core.FocusType
import app.roz.core.FocusView
import app.roz.core.Habit
import app.roz.core.HabitDay
import app.roz.core.HabitMath
import app.roz.core.HabitToday
import app.roz.core.Occasions
import app.roz.core.Person
import app.roz.core.Reminder
import app.roz.core.Reminders
import app.roz.core.TodayPlan
import app.roz.core.TodayPlanner
import app.roz.core.Upcoming
import app.roz.data.DeletedItem
import app.roz.data.PrefsState
import app.roz.data.StoredReminder
import app.roz.data.Undo
import app.roz.notify.AlarmReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

data class UiState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val plan: TodayPlan = TodayPlan(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0, 0),
    val habits: List<HabitRow> = emptyList(),
    val chores: List<ChoreToday> = emptyList(),
    val choreLog: List<ChoreLogEntry> = emptyList(),
    val reminders: List<StoredReminder> = emptyList(),
    val upcoming: List<Upcoming> = emptyList(),
    val deleted: List<DeletedItem> = emptyList(),
    val prefs: PrefsState? = null,
    val backupSetUp: Boolean = false,
    val secretsSaved: Boolean = false,
    val backupStale: Boolean = false,
    val passphraseCheckDue: Boolean = false,
    val areas: List<String> = emptyList(),
    /** The top-priority item as it looks today, or null if none is set (or it no longer applies). */
    val focus: FocusView? = null,
    val focusPick: FocusPick? = null,
    val hideFocusOnLock: Boolean = false,
)

data class HabitRow(val habit: Habit, val stats: HabitStats, val days: List<HabitDay>)

data class UiMessage(val text: String, val undo: Undo? = null)

private data class Items(
    val habits: List<Habit>,
    val habitDays: Map<Long, List<HabitDay>>,
    val chores: List<Chore>,
    val choreLog: List<ChoreLogEntry>,
    val reminders: List<StoredReminder>,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as RozApplication).container
    private val repo = container.repository
    private val alarms = container.alarms
    private val today = MutableStateFlow(LocalDate.now())
    private val secretsVersion = MutableStateFlow(0)

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    init {
        // Roll over to a new day while the app stays open.
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                today.value = LocalDate.now()
            }
        }
    }

    fun refreshDay() {
        today.value = LocalDate.now()
    }

    private val items = combine(repo.habits, repo.habitDays, repo.chores, repo.choreLog, repo.reminders) { h, d, c, l, r ->
        Items(h, d, c, l, r)
    }

    val state: StateFlow<UiState> = combine(items, repo.people, repo.deleted, container.prefs.state, combine(today, secretsVersion) { t, _ -> t }) { items, people, deleted, prefs, day ->
        val habitRows = items.habits.map { HabitRow(it, HabitMath.stats(it, items.habitDays[it.id].orEmpty(), day), items.habitDays[it.id].orEmpty()) }
        val plan = TodayPlanner.plan(
            day,
            habitRows.map { HabitToday(it.habit, it.stats) },
            items.chores,
            items.reminders.map { it.reminder },
            people,
            choresDoneToday = items.choreLog.count { it.date == day && it.action == ChoreAction.DONE },
            remindersDoneToday = items.reminders.count { it.lastDoneOn == day.toString() },
        )
        val setUp = container.secrets.isSetUp()
        val focusView = Focus.resolve(
            prefs.settings.focus, day, habitRows.map { HabitToday(it.habit, it.stats) },
            items.chores, items.reminders.map { it.reminder }, people,
        )
        UiState(
            loaded = true,
            today = day,
            plan = plan,
            habits = habitRows,
            chores = items.chores.map { ChoreToday(it, ChoreSchedule.status(it.rule, it.due, day)) },
            choreLog = items.choreLog,
            reminders = items.reminders,
            upcoming = Occasions.upcoming(people, day),
            deleted = deleted.items.sortedByDescending { it.deletedAt },
            prefs = prefs,
            backupSetUp = setUp && prefs.backupUri != null,
            secretsSaved = setUp,
            backupStale = BackupHealth.isStale(prefs.lastBackup, Instant.now()),
            passphraseCheckDue = setUp && BackupHealth.passphraseCheckDue(prefs.lastPassphraseCheck, day),
            areas = items.chores.map { it.area }.filter { it.isNotBlank() }.distinct().sorted(),
            focus = focusView,
            focusPick = focusView?.pick,
            hideFocusOnLock = prefs.settings.hideFocusOnLock,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private fun act(message: String? = null, block: suspend () -> Undo?) {
        viewModelScope.launch {
            val undo = block()
            container.refreshFocus()
            val wrapped: Undo? = undo?.let { u ->
                val again: Undo = { u(); container.refreshFocus() }
                again
            }
            if (message != null) _messages.emit(UiMessage(message, wrapped))
        }
    }

    // ---- habits ----

    fun tickHabit(habit: Habit) = act { repo.tickHabit(habit, today.value) }
    fun untickHabit(habit: Habit) = act { repo.untickHabit(habit, today.value) }
    fun skipHabit(habit: Habit) = act("Skipped today: ${habit.name}") { repo.skipHabit(habit, today.value) }

    fun saveHabit(habit: Habit, priority: Boolean) = act {
        val id = repo.saveHabit(habit)
        repo.habit(id)?.let { alarms.schedule(it) }
        applyPriority(FocusType.HABIT, id, priority && Focus.canBePriority(habit))
        null
    }

    // ---- chores ----

    fun completeChore(chore: Chore) = act("Done: ${chore.name}") {
        val undo = repo.completeChore(chore, today.value)
        container.clearFocusIf(FocusType.CHORE, chore.id)
        undo
    }
    fun skipChore(chore: Chore) = act("Skipped this time: ${chore.name}") { repo.skipChore(chore, today.value) }
    fun pauseChore(chore: Chore, paused: Boolean) =
        act(if (paused) "Paused: ${chore.name}" else "Resumed: ${chore.name}") { repo.setChorePaused(chore, paused, today.value) }

    fun saveChore(chore: Chore, priority: Boolean) = act {
        val id = repo.saveChore(chore)
        applyPriority(FocusType.CHORE, id, priority)
        null
    }

    // ---- reminders ----

    private fun rescheduleReminder(id: Long) = viewModelScope.launch { repo.reminder(id)?.let { alarms.schedule(it) } }

    fun completeReminder(stored: StoredReminder) = act("Done: ${stored.reminder.title}") {
        val undo = repo.completeReminder(stored, LocalDateTime.now())
        rescheduleReminder(stored.reminder.id)
        container.clearFocusIf(FocusType.REMINDER, stored.reminder.id)
        val wrapped: Undo = { undo(); rescheduleReminder(stored.reminder.id) }
        wrapped
    }

    fun snoozeReminder(stored: StoredReminder, tomorrow: Boolean) =
        act(if (tomorrow) "Moved to tomorrow" else "Snoozed for an hour") {
            val now = LocalDateTime.now()
            val undo = repo.updateReminder(stored, { if (tomorrow) Reminders.snoozeTomorrow(it, now) else Reminders.snoozeHour(it, now) })
            rescheduleReminder(stored.reminder.id)
            val wrapped: Undo = { undo(); rescheduleReminder(stored.reminder.id) }
            wrapped
        }

    fun saveReminder(reminder: Reminder, priority: Boolean) = act {
        val id = repo.saveReminder(reminder)
        rescheduleReminder(id)
        applyPriority(FocusType.REMINDER, id, priority)
        null
    }

    // ---- people ----

    fun savePerson(person: Person, priority: Boolean) = act {
        val id = repo.savePerson(person)
        applyPriority(FocusType.PERSON, id, priority)
        null
    }

    // ---- top priority ----

    /** Marks one item as the top priority; the previous one (if any) is replaced. */
    fun setFocus(type: FocusType, id: Long) = act {
        applyPriority(type, id, on = true)
        null
    }

    fun clearFocus() = act {
        container.setFocus(null)
        null
    }

    /** The Done button on the Today card. */
    fun completeFocus() {
        val title = state.value.focus?.title
        act(title?.let { "Done: $it" }) {
            container.completeFocus()
            null
        }
    }

    fun setHideFocusOnLock(hide: Boolean) {
        container.prefs.setHideFocusOnLock(hide)
        viewModelScope.launch { container.refreshFocus() }
    }

    private suspend fun applyPriority(type: FocusType, id: Long, on: Boolean) {
        val current = container.prefs.settings.focus
        if (on) {
            // A birthday stays on top until its day is over.
            val until = if (type == FocusType.PERSON) repo.person(id)?.let { Occasions.next(it, LocalDate.now()).date } else null
            container.setFocus(FocusPick(type, id, until))
        } else if (current != null && current.type == type && current.id == id) {
            container.setFocus(null)
        }
    }

    fun toggleGift(person: Person, index: Int) = act {
        val gifts = person.gifts.toMutableList()
        gifts[index] = gifts[index].copy(done = !gifts[index].done)
        repo.savePerson(person.copy(gifts = gifts))
        null
    }

    // ---- deleting ----

    fun delete(type: DeletedItem.Type, id: Long, name: String) = act("Deleted: $name") {
        val undo = repo.delete(type, id)
        when (type) {
            DeletedItem.Type.REMINDER -> alarms.cancel(AlarmReceiver.REMINDER, id)
            DeletedItem.Type.HABIT -> alarms.cancel(AlarmReceiver.HABIT, id)
            else -> Unit
        }
        BackupWorker.scheduleSoon(getApplication())
        val wrapped: Undo = { undo(); alarms.rescheduleAll() }
        wrapped
    }

    fun restoreDeleted(item: DeletedItem) = act("Restored: ${item.name}") {
        repo.restoreDeleted(item)
        alarms.rescheduleAll()
        null
    }

    fun deleteForever(item: DeletedItem) = act { repo.deleteForever(item); null }

    // ---- settings ----

    fun setMorning(minute: Int) {
        container.prefs.setMorning(minute)
        alarms.scheduleDaily(AlarmReceiver.MORNING, minute)
    }

    fun setEvening(minute: Int) {
        container.prefs.setEvening(minute)
        alarms.scheduleDaily(AlarmReceiver.EVENING, minute)
    }

    // ---- backup ----

    fun saveSecrets(passphrase: CharArray, recoveryCode: String) {
        container.secrets.save(passphrase, recoveryCode)
        container.prefs.passphraseChecked(LocalDate.now())
        secretsVersion.value++
    }

    fun setBackupLocation(uri: Uri) {
        container.prefs.setBackupUri(uri)
        backUpNow()
    }

    fun backUpNow() = viewModelScope.launch {
        val result = container.backup.backUpNow()
        _messages.emit(UiMessage(if (result.isSuccess) "Backed up" else result.exceptionOrNull()?.message ?: "Backup failed"))
    }

    fun checkPassphrase(attempt: CharArray): Boolean {
        val ok = container.secrets.matches(attempt)
        if (ok) container.prefs.passphraseChecked(LocalDate.now())
        return ok
    }

    fun localCopies(): List<File> = container.backup.localCopies()

    suspend fun openBackup(uri: Uri, secret: CharArray): Result<BackupPayload> = container.backup.open(uri, secret)

    suspend fun openBackup(file: File, secret: CharArray): Result<BackupPayload> = container.backup.open(file, secret)

    fun restore(payload: BackupPayload) = viewModelScope.launch {
        container.backup.restore(payload)
        alarms.rescheduleAll()
        container.refreshFocus()
        BackupWorker.scheduleSoon(getApplication())
        _messages.emit(UiMessage("Restored your backup"))
    }

    fun export(uri: Uri, kind: ExportKind) = viewModelScope.launch {
        val result = container.backup.export(uri, kind)
        _messages.emit(UiMessage(if (result.isSuccess) "Exported" else result.exceptionOrNull()?.message ?: "Export failed"))
    }

    fun importContacts(people: List<Person>) = act("Added ${people.size} birthdays from contacts") {
        people.forEach { repo.savePerson(it) }
        null
    }
}
