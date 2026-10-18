package app.roz.data

import app.roz.core.BackupPayload
import app.roz.core.Chore
import app.roz.core.ChoreAction
import app.roz.core.ChoreLogEntry
import app.roz.core.ChoreSchedule
import app.roz.core.GiftIdea
import app.roz.core.Habit
import app.roz.core.HabitDay
import app.roz.core.HabitKind
import app.roz.core.HabitLog
import app.roz.core.OccasionKind
import app.roz.core.Person
import app.roz.core.Reminder
import app.roz.core.Reminders
import app.roz.core.Repeat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime

/** Undoes the change it was returned from. */
typealias Undo = suspend () -> Unit

/**
 * The only place that reads and writes the database. Everything going in and out is a :core model;
 * every change that the user might regret returns an [Undo].
 */
class Repository(private val dao: RozDao) {
    private val json = Json { ignoreUnknownKeys = true }
    private val giftsSerializer = ListSerializer(GiftIdea.serializer())

    // ---- reading ----

    val habits: Flow<List<Habit>> = dao.habits().map { list -> list.map { it.toModel() } }
    val habitDays: Flow<Map<Long, List<HabitDay>>> = dao.habitDays().map { list ->
        list.groupBy({ it.habitId }, { HabitDay(LocalDate.parse(it.date), it.value, it.skipped) })
    }
    val chores: Flow<List<Chore>> = dao.chores().map { list -> list.map { it.toModel() } }
    val choreLog: Flow<List<ChoreLogEntry>> = dao.choreLog().map { list -> list.map { it.toModel() } }
    val reminders: Flow<List<StoredReminder>> = dao.reminders().map { list -> list.map { it.toStored() } }
    val people: Flow<List<Person>> = dao.people().map { list -> list.map { it.toModel() } }
    val deleted: Flow<DeletedItems> = kotlinx.coroutines.flow.combine(
        dao.deletedHabits(), dao.deletedChores(), dao.deletedReminders(), dao.deletedPeople(),
    ) { h, c, r, p ->
        DeletedItems(
            h.map { DeletedItem(DeletedItem.Type.HABIT, it.id, it.name, it.deletedAt ?: 0) } +
                c.map { DeletedItem(DeletedItem.Type.CHORE, it.id, it.name, it.deletedAt ?: 0) } +
                r.map { DeletedItem(DeletedItem.Type.REMINDER, it.id, it.title, it.deletedAt ?: 0) } +
                p.map { DeletedItem(DeletedItem.Type.PERSON, it.id, it.name, it.deletedAt ?: 0) },
        )
    }

    suspend fun habit(id: Long): Habit? = dao.habit(id)?.takeIf { it.deletedAt == null }?.toModel()
    suspend fun chore(id: Long): Chore? = dao.chore(id)?.takeIf { it.deletedAt == null }?.toModel()
    suspend fun reminder(id: Long): Reminder? = dao.reminder(id)?.takeIf { it.deletedAt == null }?.toModel()
    suspend fun person(id: Long): Person? = dao.person(id)?.takeIf { it.deletedAt == null }?.toModel()
    suspend fun habitsNow(): List<Habit> = dao.habitsNow().map { it.toModel() }
    suspend fun habitDaysNow(): Map<Long, List<HabitDay>> = dao.habitDaysNow()
        .groupBy({ it.habitId }, { HabitDay(LocalDate.parse(it.date), it.value, it.skipped) })
    suspend fun choresNow(): List<Chore> = dao.choresNow().map { it.toModel() }
    suspend fun remindersNow(): List<StoredReminder> = dao.remindersNow().map { it.toStored() }
    suspend fun peopleNow(): List<Person> = dao.peopleNow().map { it.toModel() }
    suspend fun choresDoneOn(date: LocalDate): Int =
        dao.choreLogNow().count { it.date == date.toString() && it.action == ChoreAction.DONE.name }

    // ---- habits ----

    suspend fun saveHabit(habit: Habit): Long = dao.upsertHabit(habit.toEntity()).let { if (it == -1L) habit.id else it }

    /** Adds one (counting habits) or marks done (yes/no); tapping a done yes/no habit clears it. */
    suspend fun tickHabit(habit: Habit, date: LocalDate): Undo {
        val before = dao.habitDay(habit.id, date.toString())
        val value = before?.takeIf { !it.skipped }?.value ?: 0
        val next = when (habit.kind) {
            HabitKind.YES_NO -> if (value >= 1) 0 else 1
            else -> value + 1
        }
        writeHabitDay(habit.id, date, next, false)
        return { restoreHabitDay(habit.id, date, before) }
    }

    /** Takes one off a counting habit. */
    suspend fun untickHabit(habit: Habit, date: LocalDate): Undo {
        val before = dao.habitDay(habit.id, date.toString())
        writeHabitDay(habit.id, date, maxOf(0, (before?.value ?: 0) - 1), false)
        return { restoreHabitDay(habit.id, date, before) }
    }

    suspend fun skipHabit(habit: Habit, date: LocalDate): Undo {
        val before = dao.habitDay(habit.id, date.toString())
        writeHabitDay(habit.id, date, 0, true)
        return { restoreHabitDay(habit.id, date, before) }
    }

    private suspend fun writeHabitDay(habitId: Long, date: LocalDate, value: Int, skipped: Boolean) {
        if (value == 0 && !skipped) dao.deleteHabitDay(habitId, date.toString())
        else dao.upsertHabitDay(HabitDayEntity(habitId, date.toString(), value, skipped))
    }

    private suspend fun restoreHabitDay(habitId: Long, date: LocalDate, before: HabitDayEntity?) {
        if (before == null) dao.deleteHabitDay(habitId, date.toString()) else dao.upsertHabitDay(before)
    }

    // ---- chores ----

    suspend fun saveChore(chore: Chore): Long = dao.upsertChore(chore.toEntity()).let { if (it == -1L) chore.id else it }

    suspend fun completeChore(chore: Chore, today: LocalDate): Undo {
        val before = chore.toEntity()
        val status = ChoreSchedule.status(chore.rule, chore.due, today)
        val logIds = mutableListOf<Long>()
        // Scheduled dates that went by unnoticed are kept in the history as misses.
        if (status.missed > 0) {
            logIds += dao.insertChoreLog(ChoreLogEntity(choreId = chore.id, date = today.toString(), action = "${ChoreAction.MISSED.name}:${status.missed}"))
        }
        logIds += dao.insertChoreLog(ChoreLogEntity(choreId = chore.id, date = today.toString(), action = ChoreAction.DONE.name))
        dao.upsertChore(chore.copy(due = ChoreSchedule.complete(chore.rule, chore.due, today), lastDone = today).toEntity())
        return {
            logIds.forEach { dao.deleteChoreLog(it) }
            dao.upsertChore(before)
        }
    }

    suspend fun skipChore(chore: Chore, today: LocalDate): Undo {
        val before = chore.toEntity()
        val logId = dao.insertChoreLog(ChoreLogEntity(choreId = chore.id, date = today.toString(), action = ChoreAction.SKIPPED.name))
        dao.upsertChore(chore.copy(due = ChoreSchedule.skip(chore.rule, chore.due, today)).toEntity())
        return {
            dao.deleteChoreLog(logId)
            dao.upsertChore(before)
        }
    }

    suspend fun setChorePaused(chore: Chore, paused: Boolean, today: LocalDate): Undo {
        val before = chore.toEntity()
        val due = if (paused) chore.due else ChoreSchedule.resume(chore.rule, chore.due, today)
        dao.upsertChore(chore.copy(paused = paused, due = due).toEntity())
        return { dao.upsertChore(before) }
    }

    // ---- reminders ----

    suspend fun saveReminder(reminder: Reminder): Long =
        dao.upsertReminder(reminder.toEntity(dao.reminder(reminder.id)?.lastDoneOn)).let { if (it == -1L) reminder.id else it }

    suspend fun updateReminder(stored: StoredReminder, change: (Reminder) -> Reminder, doneOn: LocalDate? = null): Undo {
        val before = stored.reminder.toEntity(stored.lastDoneOn)
        dao.upsertReminder(change(stored.reminder).toEntity(doneOn?.toString() ?: stored.lastDoneOn))
        return { dao.upsertReminder(before) }
    }

    suspend fun completeReminder(stored: StoredReminder, now: LocalDateTime): Undo =
        updateReminder(stored, { Reminders.complete(it, now) }, now.toLocalDate())

    // ---- people ----

    suspend fun savePerson(person: Person): Long = dao.upsertPerson(person.toEntity()).let { if (it == -1L) person.id else it }

    // ---- deleting: kept for 30 days in Recently deleted ----

    suspend fun delete(type: DeletedItem.Type, id: Long): Undo {
        setDeleted(type, id, System.currentTimeMillis())
        return { setDeleted(type, id, null) }
    }

    suspend fun restoreDeleted(item: DeletedItem) = setDeleted(item.type, item.id, null)

    /** Marks it as deleted long ago, so the purge removes it now. */
    suspend fun deleteForever(item: DeletedItem) {
        setDeleted(item.type, item.id, 1L)
        purgeOlderThan(2L)
    }

    suspend fun purgeOlderThan(beforeMillis: Long) = dao.purgeDeletedBefore(beforeMillis)

    private suspend fun setDeleted(type: DeletedItem.Type, id: Long, at: Long?) {
        when (type) {
            DeletedItem.Type.HABIT -> dao.habit(id)?.let { dao.upsertHabit(it.copy(deletedAt = at)) }
            DeletedItem.Type.CHORE -> dao.chore(id)?.let { dao.upsertChore(it.copy(deletedAt = at)) }
            DeletedItem.Type.REMINDER -> dao.reminder(id)?.let { dao.upsertReminder(it.copy(deletedAt = at)) }
            DeletedItem.Type.PERSON -> dao.person(id)?.let { dao.upsertPerson(it.copy(deletedAt = at)) }
        }
    }

    // ---- backup ----

    suspend fun snapshot(settings: app.roz.core.Settings): BackupPayload = BackupPayload(
        createdAt = LocalDateTime.now(),
        habits = dao.habitsNow().map { it.toModel() },
        habitLog = dao.habitDaysNow().map { HabitLog(it.habitId, HabitDay(LocalDate.parse(it.date), it.value, it.skipped)) },
        chores = dao.choresNow().map { it.toModel() },
        choreLog = dao.choreLogNow().map { it.toModel() },
        reminders = dao.remindersNow().map { it.toModel() },
        people = dao.peopleNow().map { it.toModel() },
        settings = settings,
    )

    suspend fun restore(payload: BackupPayload) = dao.replaceAll(
        habits = payload.habits.map { it.toEntity() },
        habitDays = payload.habitLog.map { HabitDayEntity(it.habitId, it.day.date.toString(), it.day.value, it.day.skipped) },
        chores = payload.chores.map { it.toEntity() },
        choreLog = payload.choreLog.map { ChoreLogEntity(choreId = it.choreId, date = it.date.toString(), action = it.action.name) },
        reminders = payload.reminders.map { it.toEntity(null) },
        people = payload.people.map { it.toEntity() },
    )

    // ---- mapping ----

    private fun HabitEntity.toModel() = Habit(
        id, name, runCatching { HabitKind.valueOf(kind) }.getOrDefault(HabitKind.YES_NO), target, perWeek,
        LocalDate.parse(createdOn), reminderMinute,
    )

    private fun Habit.toEntity() = HabitEntity(id, name, kind.name, target, perWeek, createdOn.toString(), reminderMinute)

    private fun ChoreEntity.toModel() = Chore(
        id, name, area, Repeat.decode(rule), LocalDate.parse(due), lastDone?.let(LocalDate::parse), paused, inSummary,
    )

    private fun Chore.toEntity() = ChoreEntity(
        id, name, area, Repeat.encode(rule), due.toString(), lastDone?.toString(), paused, inMorningSummary,
    )

    private fun ChoreLogEntity.toModel(): ChoreLogEntry {
        val kind = action.substringBefore(':')
        return ChoreLogEntry(choreId, LocalDate.parse(date), runCatching { ChoreAction.valueOf(kind) }.getOrDefault(ChoreAction.DONE))
    }

    private fun ReminderEntity.toModel() = Reminder(
        id, title, LocalDate.parse(date), minute, rule?.let { Repeat.decode(it) as? Repeat.Fixed },
        snoozedUntil?.let(LocalDateTime::parse), doneAt?.let(LocalDateTime::parse),
    )

    private fun ReminderEntity.toStored() = StoredReminder(toModel(), lastDoneOn)

    private fun Reminder.toEntity(lastDoneOn: String?) = ReminderEntity(
        id, title, date.toString(), minute, rule?.let { Repeat.encode(it) }, snoozedUntil?.toString(), doneAt?.toString(), lastDoneOn,
    )

    private fun PersonEntity.toModel() = Person(
        id, name, relation, runCatching { OccasionKind.valueOf(kind) }.getOrDefault(OccasionKind.BIRTHDAY), month, day, year, phone,
        runCatching { json.decodeFromString(giftsSerializer, gifts) }.getOrDefault(emptyList()),
    )

    private fun Person.toEntity() = PersonEntity(
        id, name, relation, kind.name, month, day, year, phone, json.encodeToString(giftsSerializer, gifts),
    )
}

/** A reminder plus the last day one of its occurrences was done. */
data class StoredReminder(val reminder: Reminder, val lastDoneOn: String?)

data class DeletedItem(val type: Type, val id: Long, val name: String, val deletedAt: Long) {
    enum class Type { HABIT, CHORE, REMINDER, PERSON }
}

data class DeletedItems(val items: List<DeletedItem>)
