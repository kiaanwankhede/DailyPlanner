package app.roz.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class HabitMathTest {
    private fun d(s: String) = LocalDate.parse(s)
    private val today = d("2026-10-18") // a Sunday; the week is Mon 12 – Sun 18

    private fun daily(created: String, kind: HabitKind = HabitKind.YES_NO, target: Int = 1) =
        Habit(1, "Walk", kind, target, null, d(created))

    private fun doneOn(vararg dates: String, value: Int = 1) = dates.map { HabitDay(d(it), value) }

    private fun range(from: String, to: String, except: Set<String> = emptySet()): List<String> =
        generateSequence(d(from)) { it.plusDays(1) }.takeWhile { !it.isAfter(d(to)) }
            .map { it.toString() }.filter { it !in except }.toList()

    @Test
    fun `every day done gives a streak of all days`() {
        val habit = daily("2026-09-26")
        val stats = HabitMath.stats(habit, doneOn(*range("2026-09-26", "2026-10-18").toTypedArray()), today)
        assertEquals(23, stats.streak)
        assertTrue(stats.doneToday)
        assertTrue(stats.freeSkipLeft)
        assertEquals(List(7) { DayMark.DONE }, stats.week)
        assertEquals("23 days", HabitMath.streakLabel(stats))
    }

    @Test
    fun `today still open does not break the streak`() {
        val habit = daily("2026-10-01")
        val stats = HabitMath.stats(habit, doneOn(*range("2026-10-01", "2026-10-17").toTypedArray()), today)
        assertEquals(17, stats.streak)
        assertFalse(stats.doneToday)
        assertEquals(DayMark.PENDING, stats.week.last())
    }

    @Test
    fun `one miss a week is forgiven`() {
        val habit = daily("2026-10-05")
        val stats = HabitMath.stats(habit, doneOn(*range("2026-10-05", "2026-10-18", setOf("2026-10-14")).toTypedArray()), today)
        assertEquals(13, stats.streak)
        assertEquals(DayMark.FORGIVEN, stats.week[2])
        assertFalse(stats.freeSkipLeft)
    }

    @Test
    fun `a second miss in the same week breaks the streak`() {
        val habit = daily("2026-10-05")
        val days = range("2026-10-05", "2026-10-18", setOf("2026-10-13", "2026-10-15"))
        val stats = HabitMath.stats(habit, doneOn(*days.toTypedArray()), today)
        assertEquals(DayMark.FORGIVEN, stats.week[1])
        assertEquals(DayMark.MISSED, stats.week[3])
        assertEquals(3, stats.streak)
    }

    @Test
    fun `a skip on purpose uses the weekly allowance`() {
        val habit = daily("2026-10-05")
        val entries = doneOn(*range("2026-10-05", "2026-10-18", setOf("2026-10-13", "2026-10-16")).toTypedArray()) +
            HabitDay(d("2026-10-13"), 0, skipped = true)
        val stats = HabitMath.stats(habit, entries, today)
        assertEquals(DayMark.FORGIVEN, stats.week[1])
        assertEquals(DayMark.MISSED, stats.week[4])
        assertEquals(2, stats.streak)
    }

    @Test
    fun `strength drops a little after one miss, not to zero`() {
        val habit = daily("2026-09-01")
        val all = range("2026-09-01", "2026-10-18")
        val perfect = HabitMath.stats(habit, doneOn(*all.toTypedArray()), today).strength
        // Two misses in one week: one forgiven, one counted.
        val oneMiss = HabitMath.stats(habit, doneOn(*all.filter { it != "2026-10-13" && it != "2026-10-15" }.toTypedArray()), today).strength
        assertTrue(perfect in 85..100, "perfect was $perfect")
        assertTrue(oneMiss in (perfect - 10) until perfect, "after a miss was $oneMiss vs $perfect")
        val fresh = HabitMath.stats(daily("2026-10-16"), doneOn("2026-10-16", "2026-10-17", "2026-10-18"), today).strength
        assertTrue(fresh in 1..20, "three days in was $fresh")
    }

    @Test
    fun `times a week counts weeks that hit the target`() {
        val habit = Habit(2, "Gym", perWeek = 3, createdOn = d("2026-09-28"))
        val entries = doneOn(
            "2026-09-28", "2026-09-30", "2026-10-02", // week 1: 3
            "2026-10-05", "2026-10-07", "2026-10-09", "2026-10-10", // week 2: 4
            "2026-10-12", "2026-10-14", // this week: 2 so far
        )
        val stats = HabitMath.stats(habit, entries, today.minusDays(1)) // Saturday
        assertEquals(2, stats.streak)
        assertTrue(stats.streakInWeeks)
        assertEquals(2, stats.doneThisWeek)
        assertEquals("3 times a week · 2 of 3", HabitMath.targetLabel(habit, stats))
        assertEquals(DayMark.EMPTY, stats.week[1])
        val done = HabitMath.stats(habit, entries + doneOn("2026-10-18"), today)
        assertEquals(3, done.streak)
    }

    @Test
    fun `a short earlier week ends a weekly streak`() {
        val habit = Habit(2, "Gym", perWeek = 2, createdOn = d("2026-09-28"))
        val entries = doneOn("2026-09-28", "2026-09-29", "2026-10-05", "2026-10-12", "2026-10-13")
        assertEquals(1, HabitMath.stats(habit, entries, today).streak)
    }

    @Test
    fun `at most habits count empty past days as met`() {
        val habit = daily("2026-10-12", HabitKind.AT_MOST, 2)
        val entries = listOf(HabitDay(d("2026-10-13"), 1), HabitDay(d("2026-10-14"), 2), HabitDay(d("2026-10-18"), 1))
        val stats = HabitMath.stats(habit, entries, today)
        assertEquals(6, stats.streak)
        assertEquals(DayMark.PENDING, stats.week.last())
        assertFalse(stats.doneToday)
        val over = HabitMath.stats(habit, entries + HabitDay(d("2026-10-18"), 3), today)
        assertEquals(DayMark.FORGIVEN, over.week.last())
        assertEquals("At most 2 a day · Every day", HabitMath.targetLabel(habit))
    }

    @Test
    fun `counting habits are done when they reach the target`() {
        val habit = daily("2026-10-18", HabitKind.AT_LEAST, 8)
        val partial = HabitMath.stats(habit, doneOn("2026-10-18", value = 5), today)
        assertEquals(5, partial.todayValue)
        assertFalse(partial.doneToday)
        assertEquals(DayMark.PENDING, partial.week.last())
        assertTrue(HabitMath.stats(habit, doneOn("2026-10-18", value = 8), today).doneToday)
    }

    @Test
    fun `days before the habit existed are empty`() {
        val stats = HabitMath.stats(daily("2026-10-16"), doneOn("2026-10-16", "2026-10-17"), today)
        assertEquals(listOf(DayMark.EMPTY, DayMark.EMPTY, DayMark.EMPTY, DayMark.EMPTY, DayMark.DONE, DayMark.DONE, DayMark.PENDING), stats.week)
        assertEquals(2, stats.streak)
    }
}
