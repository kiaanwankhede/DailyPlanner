package app.roz.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AddPromptsTest {
    private fun d(s: String) = LocalDate.parse(s)
    private val jan1 = d("2026-01-01")
    private fun counts(habits: Int = 0, chores: Int = 0, people: Int = 0, reminders: Int = 0) = ItemCounts(habits, chores, people, reminders)

    @Test
    fun `one item is expected per week counting from the start day`() {
        assertEquals(0, AddPrompts.expected(jan1, d("2025-12-31")))
        assertEquals(1, AddPrompts.expected(jan1, d("2026-01-01")))
        assertEquals(1, AddPrompts.expected(jan1, d("2026-01-07")))
        assertEquals(2, AddPrompts.expected(jan1, d("2026-01-08")))
        assertEquals(43, AddPrompts.expected(jan1, d("2026-10-26")))
    }

    @Test
    fun `the gap shrinks as items are added and never goes below zero`() {
        val today = d("2026-10-26")
        assertEquals(43, AddPrompts.status(jan1, today, counts()).behind)
        assertEquals(33, AddPrompts.status(jan1, today, counts(habits = 4, chores = 3, people = 2, reminders = 1)).behind)
        assertEquals(32, AddPrompts.status(jan1, today, counts(habits = 5, chores = 3, people = 2, reminders = 1)).behind)
        assertEquals(0, AddPrompts.status(jan1, today, counts(people = 43)).behind)
        assertEquals(0, AddPrompts.status(jan1, today, counts(people = 60)).behind)
    }

    @Test
    fun `a new week brings one more to add`() {
        val c = counts(habits = 43)
        assertEquals(0, AddPrompts.status(jan1, d("2026-10-28"), c).behind)
        assertEquals(1, AddPrompts.status(jan1, d("2026-10-29"), c).behind)
        assertEquals(1, AddPrompts.status(jan1, d("2026-11-04"), c).behind)
        assertEquals(2, AddPrompts.status(jan1, d("2026-11-05"), c).behind)
    }

    @Test
    fun `the weekly notification goes out on the first day of each week`() {
        assertTrue(AddPrompts.isNewWeekDay(jan1, d("2026-01-01")))
        assertFalse(AddPrompts.isNewWeekDay(jan1, d("2026-01-02")))
        assertTrue(AddPrompts.isNewWeekDay(jan1, d("2026-01-08")))
        assertTrue(AddPrompts.isNewWeekDay(jan1, d("2026-10-22")))
        assertFalse(AddPrompts.isNewWeekDay(jan1, d("2026-10-26")))
        assertFalse(AddPrompts.isNewWeekDay(jan1, d("2025-12-25")))
    }

    @Test
    fun `it suggests the kind you have the fewest of`() {
        assertEquals(PromptKind.BIRTHDAY, AddPrompts.suggest(counts()))
        assertEquals(PromptKind.HABIT, AddPrompts.suggest(counts(habits = 1, chores = 4, people = 5)))
        assertEquals(PromptKind.CHORE, AddPrompts.suggest(counts(habits = 3, chores = 1, people = 5)))
        assertEquals(PromptKind.CHORE, AddPrompts.suggest(counts(habits = 3, chores = 1, people = 2, reminders = 0)))
        // A repeating reminder counts as a recurring chore.
        assertEquals(PromptKind.BIRTHDAY, AddPrompts.suggest(counts(habits = 3, chores = 1, people = 2, reminders = 4)))
    }

    @Test
    fun `texts`() {
        val p = AddPrompts.status(jan1, d("2026-10-26"), counts(habits = 4, chores = 3, people = 2, reminders = 1))
        assertEquals("Add 1 recurring item", AddPrompts.title(p))
        assertEquals("33 to go · 1 a week since 1 Jan", AddPrompts.detail(p))
        assertEquals("33 to go since 1 Jan. Suggestion: a birthday. Any habit, chore, birthday or repeating reminder counts.", AddPrompts.notificationBody(p, PromptKind.BIRTHDAY))
    }

    @Test
    fun `the settings survive a backup`() {
        val settings = Settings(addPromptOn = false, addPromptStart = d("2026-03-01"))
        val payload = BackupPayload(createdAt = LocalDateTime.parse("2026-10-18T02:00"), settings = settings)
        val bytes = BackupCodec.encode(payload, "pass".toCharArray(), RecoveryCode.generate().toCharArray())
        assertEquals(settings, BackupCodec.decode(bytes, "pass".toCharArray()).settings)
        assertTrue(Settings().addPromptOn)
    }
}
