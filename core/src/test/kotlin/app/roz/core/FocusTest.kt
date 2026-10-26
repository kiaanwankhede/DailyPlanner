package app.roz.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class FocusTest {
    private fun d(s: String) = LocalDate.parse(s)
    private val today = d("2026-10-18")

    private val walk = Habit(1, "Walk 8,000 steps", createdOn = d("2026-10-01"))
    private val water = Habit(2, "Water", HabitKind.AT_LEAST, 8, createdOn = d("2026-10-01"))
    private val chai = Habit(3, "Chai", HabitKind.AT_MOST, 2, createdOn = d("2026-10-01"))
    private val gym = Habit(4, "Gym", perWeek = 3, createdOn = d("2026-10-01"))
    private fun habitToday(h: Habit, vararg days: HabitDay) = HabitToday(h, HabitMath.stats(h, days.toList(), today))
    private fun done(date: String) = HabitDay(d(date), 1)

    private val bathroom = Chore(1, "Clean bathroom", "Bathroom", Repeat.Weekly(listOf(6), anchor = today), d("2026-10-17"))
    private val plants = Chore(2, "Water the plants", "", Repeat.AfterDone(3, Repeat.Unit.DAYS), d("2026-10-19"))
    private val bill = Reminder(1, "Pay electricity bill", today, 19 * 60, Repeat.MonthlyOnDate(18, anchor = today))
    private val riya = Person(1, "Riya", "Niece", month = 10, day = 20, year = 2017)

    private fun resolve(pick: FocusPick?, habits: List<HabitToday> = emptyList(), chores: List<Chore> = emptyList(),
                        reminders: List<Reminder> = emptyList(), people: List<Person> = emptyList(), on: LocalDate = today) =
        Focus.resolve(pick, on, habits, chores, reminders, people)

    @Test
    fun `nothing picked shows nothing`() {
        assertNull(resolve(null, chores = listOf(bathroom)))
    }

    @Test
    fun `a habit shows progress and streak until it is done`() {
        val pending = habitToday(walk, done("2026-10-16"), done("2026-10-17"))
        val view = resolve(FocusPick(FocusType.HABIT, 1), habits = listOf(pending))!!
        assertEquals("Walk 8,000 steps", view.title)
        assertEquals("Not done yet today · 2-day streak", view.detail)
        assertEquals("Habit", view.kindLabel)
        assertEquals("Done", view.doneLabel)
        assertTrue(view.canComplete)
        val done = habitToday(walk, done("2026-10-17"), done("2026-10-18"))
        assertNull(resolve(FocusPick(FocusType.HABIT, 1), habits = listOf(done)))
    }

    @Test
    fun `counting and weekly habits`() {
        val glasses = resolve(FocusPick(FocusType.HABIT, 2), habits = listOf(habitToday(water, HabitDay(today, 5))))!!
        assertEquals("5 of 8 today", glasses.detail)
        assertEquals("Add one", glasses.doneLabel)
        val weekly = resolve(FocusPick(FocusType.HABIT, 4), habits = listOf(habitToday(gym, done("2026-10-13"), done("2026-10-15"))))!!
        assertEquals("2 of 3 this week", weekly.detail)
    }

    @Test
    fun `an at-most habit can't be the top priority`() {
        assertFalse(Focus.canBePriority(chai))
        assertNull(resolve(FocusPick(FocusType.HABIT, 3), habits = listOf(habitToday(chai))))
    }

    @Test
    fun `a chore says how late or when it is due`() {
        val late = resolve(FocusPick(FocusType.CHORE, 1), chores = listOf(bathroom))!!
        assertEquals("1 day late · Bathroom", late.detail)
        assertEquals("Chore", late.kindLabel)
        val soon = resolve(FocusPick(FocusType.CHORE, 2), chores = listOf(plants))!!
        assertEquals("Due tomorrow", soon.detail)
        val onThe = resolve(FocusPick(FocusType.CHORE, 2), chores = listOf(plants.copy(due = d("2026-10-21"))))!!
        assertEquals("Due Wed", onThe.detail)
    }

    @Test
    fun `a paused or deleted chore drops off`() {
        assertNull(resolve(FocusPick(FocusType.CHORE, 1), chores = listOf(bathroom.copy(paused = true))))
        assertNull(resolve(FocusPick(FocusType.CHORE, 9), chores = listOf(bathroom)))
    }

    @Test
    fun `a reminder shows its time and drops off when done`() {
        val view = resolve(FocusPick(FocusType.REMINDER, 1), reminders = listOf(bill))!!
        assertEquals("Today · 7:00 PM", view.detail)
        val late = resolve(FocusPick(FocusType.REMINDER, 1), reminders = listOf(bill.copy(date = d("2026-10-16"))))!!
        assertEquals("Was due 16 Oct at 7:00 PM", late.detail)
        val done = bill.copy(rule = null, doneAt = LocalDateTime.parse("2026-10-18T19:05"))
        assertNull(resolve(FocusPick(FocusType.REMINDER, 1), reminders = listOf(done)))
    }

    @Test
    fun `a birthday stays on top until the day is over`() {
        val pick = FocusPick(FocusType.PERSON, 1, until = d("2026-10-20"))
        val view = resolve(pick, people = listOf(riya))!!
        assertEquals("Riya turns 9", view.title)
        assertEquals("in 2 days · Niece", view.detail)
        assertEquals("Birthday", view.kindLabel)
        assertFalse(view.canComplete)
        assertEquals("Today", resolve(pick, people = listOf(riya), on = d("2026-10-20"))!!.detail.substringBefore(" ·"))
        assertNull(resolve(pick, people = listOf(riya), on = d("2026-10-21")))
    }

    @Test
    fun `the pick and the lock-screen setting survive a backup`() {
        val settings = Settings(focus = FocusPick(FocusType.PERSON, 1, d("2026-10-20")), hideFocusOnLock = true)
        val payload = BackupPayload(createdAt = LocalDateTime.parse("2026-10-18T02:00"), settings = settings)
        val bytes = BackupCodec.encode(payload, "pass".toCharArray(), RecoveryCode.generate().toCharArray())
        assertEquals(settings, BackupCodec.decode(bytes, "pass".toCharArray()).settings)
    }
}
