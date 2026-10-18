package app.roz.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class BackupAndTodayTest {
    private fun d(s: String) = LocalDate.parse(s)
    private val today = d("2026-10-18")

    private val habit = Habit(1, "Walk 8,000 steps", createdOn = d("2026-10-01"), reminderMinute = 7 * 60)
    private val chore = Chore(1, "Clean bathroom", "Bathroom", Repeat.Weekly(listOf(6), anchor = today), d("2026-10-17"))
    private val plants = Chore(2, "Water the plants", "Plants", Repeat.AfterDone(3, Repeat.Unit.DAYS), today, d("2026-10-15"))
    private val paused = Chore(3, "Wash car", "Car", Repeat.Weekly(listOf(3), 2, today), d("2026-10-01"), paused = true)
    private val reminder = Reminder(1, "Pay electricity bill", today, 19 * 60, Repeat.MonthlyOnDate(18, anchor = today))
    private val person = Person(1, "Riya", "Niece", month = 10, day = 20, year = 2017, gifts = listOf(GiftIdea("Art kit", true)))

    private val payload = BackupPayload(
        createdAt = LocalDateTime.parse("2026-10-12T02:15"),
        habits = listOf(habit),
        habitLog = listOf(HabitLog(1, HabitDay(today, 1)), HabitLog(1, HabitDay(d("2026-10-17"), 0, true))),
        chores = listOf(chore, plants),
        choreLog = listOf(ChoreLogEntry(2, d("2026-10-15"), ChoreAction.DONE)),
        reminders = listOf(reminder),
        people = listOf(person),
        settings = Settings(morningMinute = 7 * 60 + 30),
    )

    @Test
    fun `backup opens with the passphrase or the recovery code`() {
        val code = RecoveryCode.generate()
        val bytes = BackupCodec.encode(payload, "chai at 4pm".toCharArray(), code.toCharArray())
        assertEquals(payload, BackupCodec.decode(bytes, "chai at 4pm".toCharArray()))
        assertEquals(payload, BackupCodec.decode(bytes, code.lowercase().replace("-", " ").toCharArray()))
        assertThrows(WrongPassphraseException::class.java) { BackupCodec.decode(bytes, "wrong".toCharArray()) }
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("Riya"))
    }

    @Test
    fun `not a backup and tampered files are refused`() {
        assertThrows(NotABackupException::class.java) { BackupCodec.decode("hello".toByteArray(), "x".toCharArray()) }
        val bytes = BackupCodec.encode(payload, "pass".toCharArray(), RecoveryCode.generate().toCharArray())
        bytes[bytes.size - 3] = (bytes[bytes.size - 3] + 1).toByte()
        assertThrows(WrongPassphraseException::class.java) { BackupCodec.decode(bytes, "pass".toCharArray()) }
    }

    @Test
    fun `preview`() {
        assertEquals("Backup from 12 Oct 2026, 2:15 AM: 1 habit, 2 chores, 1 reminder, 1 birthday", payload.preview())
    }

    @Test
    fun `recovery codes`() {
        val code = RecoveryCode.generate()
        assertTrue(Regex("[0-9A-Z]{5}(-[0-9A-Z]{5}){3}").matches(code))
        assertTrue(RecoveryCode.looksValid(code.lowercase()))
        assertFalse(RecoveryCode.looksValid("too short"))
        assertEquals("0111", String(RecoveryCode.normalize("o-I l".toCharArray()) + '1'))
    }

    @Test
    fun `keeps the last 7 backups and the newest of each month`() {
        val names = listOf(
            "roz-backup-2026-08-03-0200.rozb", "roz-backup-2026-08-30-0200.rozb",
            "roz-backup-2026-09-15-0200.rozb", "roz-backup-2026-09-29-0200.rozb",
        ) + (10..18).map { "roz-backup-2026-10-%02d-0200.rozb".format(it) } + listOf("notes.txt")
        val delete = BackupRetention.toDelete(names)
        assertEquals(
            listOf("roz-backup-2026-10-11-0200.rozb", "roz-backup-2026-10-10-0200.rozb", "roz-backup-2026-09-15-0200.rozb", "roz-backup-2026-08-03-0200.rozb"),
            delete,
        )
        assertEquals("roz-backup-2026-10-18-0215.rozb", BackupRetention.fileName(LocalDateTime.parse("2026-10-18T02:15")))
    }

    @Test
    fun `stale backup warning`() {
        val now = Instant.parse("2026-10-18T10:00:00Z")
        assertTrue(BackupHealth.isStale(null, now))
        assertFalse(BackupHealth.isStale(Instant.parse("2026-10-16T02:00:00Z"), now))
        assertTrue(BackupHealth.isStale(Instant.parse("2026-10-15T02:00:00Z"), now))
        assertTrue(BackupHealth.passphraseCheckDue(d("2026-09-18"), today))
        assertFalse(BackupHealth.passphraseCheckDue(d("2026-10-01"), today))
    }

    @Test
    fun `today plan`() {
        val stats = HabitMath.stats(habit, listOf(HabitDay(today, 1)), today)
        val plan = TodayPlanner.plan(today, listOf(HabitToday(habit, stats)), listOf(chore, plants, paused), listOf(reminder), listOf(person), 1, 0)
        assertEquals(listOf("Clean bathroom"), plan.overdue.map { it.chore.name })
        assertEquals(listOf("Water the plants"), plan.choresToday.map { it.chore.name })
        assertEquals(1, plan.remindersToday.size)
        assertEquals(listOf("Riya"), plan.birthdays.map { it.person.name })
        assertEquals(2, plan.done)
        assertEquals(5, plan.total)
        val morning = Summaries.morning(plan)
        assertNotNull(morning)
        assertEquals("Today: 1 overdue · 1 chore due · 1 reminder", morning!!.title)
        assertEquals("Clean bathroom, Water the plants, Pay electricity bill at 7:00 PM", morning.body)
        assertNull(Summaries.evening(plan))
    }

    @Test
    fun `evening nudge lists open habits`() {
        val stats = HabitMath.stats(habit, emptyList(), today)
        val plan = TodayPlanner.plan(today, listOf(HabitToday(habit, stats)), emptyList(), emptyList(), emptyList(), 0, 0)
        assertEquals(Summaries.Note("One habit left today", "Walk 8,000 steps"), Summaries.evening(plan))
    }

    @Test
    fun `exports`() {
        val csv = Exports.choresCsv(listOf(chore, plants), payload.choreLog)
        assertEquals("date,chore,action\r\n2026-10-15,Water the plants,done\r\n", csv)
        val habits = Exports.habitsCsv(listOf(habit.copy(name = "Walk, run")), payload.habitLog)
        assertTrue(habits.contains("2026-10-17,\"Walk, run\",0,skipped"))
        val ics = Exports.birthdaysIcs(listOf(person, Person(9, "Leela", month = 2, day = 29)), today)
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20171020"))
        assertTrue(ics.contains("RRULE:FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=-1"))
        assertTrue(ics.contains("SUMMARY:Riya's birthday"))
    }
}
