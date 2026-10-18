package app.roz.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Dates are ISO strings (2026-10-18), repeat rules and gift lists are JSON; the mapping to the
// plain-Kotlin models in :core lives in Repository.kt.

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val target: Int,
    val perWeek: Int?,
    val createdOn: String,
    val reminderMinute: Int?,
    val deletedAt: Long? = null,
)

@Entity(tableName = "habit_days", primaryKeys = ["habitId", "date"])
data class HabitDayEntity(
    val habitId: Long,
    val date: String,
    val value: Int,
    val skipped: Boolean,
)

@Entity(tableName = "chores")
data class ChoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val area: String,
    val rule: String,
    val due: String,
    val lastDone: String?,
    val paused: Boolean,
    val inSummary: Boolean,
    val deletedAt: Long? = null,
)

@Entity(tableName = "chore_log")
data class ChoreLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val choreId: Long,
    val date: String,
    val action: String,
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: String,
    val minute: Int,
    val rule: String?,
    val snoozedUntil: String?,
    val doneAt: String?,
    /** The last day an occurrence was done, for Today's progress ring. */
    val lastDoneOn: String? = null,
    val deletedAt: Long? = null,
)

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val relation: String,
    val kind: String,
    val month: Int,
    val day: Int,
    val year: Int?,
    val phone: String?,
    val gifts: String,
    val deletedAt: Long? = null,
)

@Dao
interface RozDao {
    @Query("SELECT * FROM habits WHERE deletedAt IS NULL ORDER BY id")
    fun habits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_days")
    fun habitDays(): Flow<List<HabitDayEntity>>

    @Query("SELECT * FROM chores WHERE deletedAt IS NULL ORDER BY name")
    fun chores(): Flow<List<ChoreEntity>>

    @Query("SELECT * FROM chore_log ORDER BY date DESC, id DESC")
    fun choreLog(): Flow<List<ChoreLogEntity>>

    @Query("SELECT * FROM reminders WHERE deletedAt IS NULL ORDER BY date, minute")
    fun reminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM people WHERE deletedAt IS NULL ORDER BY name")
    fun people(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM habits WHERE deletedAt IS NOT NULL")
    fun deletedHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM chores WHERE deletedAt IS NOT NULL")
    fun deletedChores(): Flow<List<ChoreEntity>>

    @Query("SELECT * FROM reminders WHERE deletedAt IS NOT NULL")
    fun deletedReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM people WHERE deletedAt IS NOT NULL")
    fun deletedPeople(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM habits WHERE deletedAt IS NULL")
    suspend fun habitsNow(): List<HabitEntity>

    @Query("SELECT * FROM habit_days")
    suspend fun habitDaysNow(): List<HabitDayEntity>

    @Query("SELECT * FROM chores WHERE deletedAt IS NULL")
    suspend fun choresNow(): List<ChoreEntity>

    @Query("SELECT * FROM chore_log")
    suspend fun choreLogNow(): List<ChoreLogEntity>

    @Query("SELECT * FROM reminders WHERE deletedAt IS NULL")
    suspend fun remindersNow(): List<ReminderEntity>

    @Query("SELECT * FROM people WHERE deletedAt IS NULL")
    suspend fun peopleNow(): List<PersonEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun habit(id: Long): HabitEntity?

    @Query("SELECT * FROM habit_days WHERE habitId = :habitId AND date = :date")
    suspend fun habitDay(habitId: Long, date: String): HabitDayEntity?

    @Query("SELECT * FROM chores WHERE id = :id")
    suspend fun chore(id: Long): ChoreEntity?

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun reminder(id: Long): ReminderEntity?

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun person(id: Long): PersonEntity?

    @Upsert
    suspend fun upsertHabit(habit: HabitEntity): Long

    @Upsert
    suspend fun upsertHabitDay(day: HabitDayEntity)

    @Query("DELETE FROM habit_days WHERE habitId = :habitId AND date = :date")
    suspend fun deleteHabitDay(habitId: Long, date: String)

    @Upsert
    suspend fun upsertChore(chore: ChoreEntity): Long

    @Insert
    suspend fun insertChoreLog(entry: ChoreLogEntity): Long

    @Query("DELETE FROM chore_log WHERE id = :id")
    suspend fun deleteChoreLog(id: Long)

    @Upsert
    suspend fun upsertReminder(reminder: ReminderEntity): Long

    @Upsert
    suspend fun upsertPerson(person: PersonEntity): Long

    @Query("DELETE FROM habits WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeHabits(before: Long)

    @Query("DELETE FROM habit_days WHERE habitId NOT IN (SELECT id FROM habits)")
    suspend fun purgeOrphanHabitDays()

    @Query("DELETE FROM chores WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeChores(before: Long)

    @Query("DELETE FROM chore_log WHERE choreId NOT IN (SELECT id FROM chores)")
    suspend fun purgeOrphanChoreLog()

    @Query("DELETE FROM reminders WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeReminders(before: Long)

    @Query("DELETE FROM people WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgePeople(before: Long)

    @Transaction
    suspend fun purgeDeletedBefore(before: Long) {
        purgeHabits(before)
        purgeOrphanHabitDays()
        purgeChores(before)
        purgeOrphanChoreLog()
        purgeReminders(before)
        purgePeople(before)
    }

    @Query("DELETE FROM habits")
    suspend fun clearHabits()

    @Query("DELETE FROM habit_days")
    suspend fun clearHabitDays()

    @Query("DELETE FROM chores")
    suspend fun clearChores()

    @Query("DELETE FROM chore_log")
    suspend fun clearChoreLog()

    @Query("DELETE FROM reminders")
    suspend fun clearReminders()

    @Query("DELETE FROM people")
    suspend fun clearPeople()

    @Insert
    suspend fun insertHabits(items: List<HabitEntity>)

    @Insert
    suspend fun insertHabitDays(items: List<HabitDayEntity>)

    @Insert
    suspend fun insertChores(items: List<ChoreEntity>)

    @Insert
    suspend fun insertChoreLogs(items: List<ChoreLogEntity>)

    @Insert
    suspend fun insertReminders(items: List<ReminderEntity>)

    @Insert
    suspend fun insertPeople(items: List<PersonEntity>)

    @Transaction
    suspend fun replaceAll(
        habits: List<HabitEntity>,
        habitDays: List<HabitDayEntity>,
        chores: List<ChoreEntity>,
        choreLog: List<ChoreLogEntity>,
        reminders: List<ReminderEntity>,
        people: List<PersonEntity>,
    ) {
        clearHabits()
        clearHabitDays()
        clearChores()
        clearChoreLog()
        clearReminders()
        clearPeople()
        insertHabits(habits)
        insertHabitDays(habitDays)
        insertChores(chores)
        insertChoreLogs(choreLog)
        insertReminders(reminders)
        insertPeople(people)
    }
}

@Database(
    entities = [
        HabitEntity::class,
        HabitDayEntity::class,
        ChoreEntity::class,
        ChoreLogEntity::class,
        ReminderEntity::class,
        PersonEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class RozDatabase : RoomDatabase() {
    abstract fun dao(): RozDao

    companion object {
        fun create(context: Context): RozDatabase =
            Room.databaseBuilder(context, RozDatabase::class.java, "roz.db").build()
    }
}
