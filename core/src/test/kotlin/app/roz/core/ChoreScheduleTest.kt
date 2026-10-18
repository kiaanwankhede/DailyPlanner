package app.roz.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ChoreScheduleTest {
    private fun d(s: String) = LocalDate.parse(s)
    private val sunday = d("2026-10-18")
    private val everySunday = Repeat.Weekly(listOf(7), anchor = sunday)

    @Test
    fun `weekly on a day`() {
        assertEquals(d("2026-10-18"), everySunday.nextOnOrAfter(d("2026-10-18")))
        assertEquals(d("2026-10-25"), everySunday.nextAfter(d("2026-10-18")))
        assertEquals(d("2026-10-25"), everySunday.nextOnOrAfter(d("2026-10-19")))
    }

    @Test
    fun `several days a week`() {
        val monThu = Repeat.Weekly(listOf(1, 4), anchor = sunday)
        assertEquals(d("2026-10-19"), monThu.nextAfter(sunday))
        assertEquals(d("2026-10-22"), monThu.nextAfter(d("2026-10-19")))
        assertEquals(d("2026-10-26"), monThu.nextAfter(d("2026-10-22")))
    }

    @Test
    fun `every two weeks keeps to the anchor's weeks`() {
        val fortnightlyWed = Repeat.Weekly(listOf(3), everyWeeks = 2, anchor = d("2026-10-21"))
        assertEquals(d("2026-10-21"), fortnightlyWed.nextOnOrAfter(sunday))
        assertEquals(d("2026-11-04"), fortnightlyWed.nextAfter(d("2026-10-21")))
        assertEquals(d("2026-11-18"), fortnightlyWed.nextAfter(d("2026-11-04")))
    }

    @Test
    fun `monthly on a date`() {
        val on24 = Repeat.MonthlyOnDate(24, anchor = sunday)
        assertEquals(d("2026-10-24"), on24.nextOnOrAfter(sunday))
        assertEquals(d("2026-11-24"), on24.nextAfter(d("2026-10-24")))
    }

    @Test
    fun `31st falls on the last day of shorter months`() {
        val on31 = Repeat.MonthlyOnDate(31, anchor = d("2026-10-31"))
        assertEquals(d("2026-10-31"), on31.nextOnOrAfter(sunday))
        assertEquals(d("2026-11-30"), on31.nextAfter(d("2026-10-31")))
        assertEquals(d("2027-02-28"), on31.nextOnOrAfter(d("2027-02-01")))
        assertEquals(d("2028-02-29"), on31.nextOnOrAfter(d("2028-02-01")))
        assertEquals(d("2027-03-31"), on31.nextAfter(d("2027-02-28")))
    }

    @Test
    fun `every three months on the 10th`() {
        val quarterly = Repeat.MonthlyOnDate(10, everyMonths = 3, anchor = d("2026-10-10"))
        assertEquals(d("2027-01-10"), quarterly.nextOnOrAfter(sunday))
        assertEquals(d("2027-04-10"), quarterly.nextAfter(d("2027-01-10")))
    }

    @Test
    fun `first saturday and last friday`() {
        val firstSat = Repeat.MonthlyOnWeekday(1, 6, anchor = sunday)
        assertEquals(d("2026-11-07"), firstSat.nextOnOrAfter(sunday))
        val lastFri = Repeat.MonthlyOnWeekday(-1, 5, anchor = sunday)
        assertEquals(d("2026-10-30"), lastFri.nextOnOrAfter(sunday))
        assertEquals(d("2026-11-27"), lastFri.nextAfter(d("2026-10-30")))
    }

    @Test
    fun `last day of the month`() {
        val last = Repeat.MonthlyLastDay(anchor = sunday)
        assertEquals(d("2026-10-31"), last.nextOnOrAfter(sunday))
        assertEquals(d("2026-11-30"), last.nextAfter(d("2026-10-31")))
        assertEquals(d("2027-02-28"), last.nextAfter(d("2027-01-31")))
    }

    @Test
    fun `yearly and 29 February`() {
        val leapDay = Repeat.Yearly(2, 29)
        assertEquals(d("2027-02-28"), leapDay.nextOnOrAfter(sunday))
        assertEquals(d("2028-02-29"), leapDay.nextAfter(d("2027-02-28")))
    }

    @Test
    fun `a missed chore is one overdue item`() {
        val s = ChoreSchedule.status(everySunday, sunday, d("2026-10-20"))
        assertEquals(sunday, s.due)
        assertEquals(2, s.daysLate)
        assertEquals(0, s.missed)
        assertEquals("2 days late", ChoreSchedule.dueLabel(s, d("2026-10-20")))
        assertEquals(ChoreGroup.OVERDUE, ChoreSchedule.group(s, d("2026-10-20")))
    }

    @Test
    fun `done late on a schedule moves to the next date after the day it was done`() {
        assertEquals(d("2026-10-25"), ChoreSchedule.complete(everySunday, sunday, d("2026-10-20")))
    }

    @Test
    fun `done early counts for that slot`() {
        assertEquals(d("2026-10-25"), ChoreSchedule.complete(everySunday, sunday, d("2026-10-17")))
    }

    @Test
    fun `overdue past the next date rolls forward and counts the miss`() {
        val today = d("2026-10-26")
        val s = ChoreSchedule.status(everySunday, sunday, today)
        assertEquals(d("2026-10-25"), s.due)
        assertEquals(1, s.daysLate)
        assertEquals(1, s.missed)
        assertEquals("1 day late · missed 1", ChoreSchedule.dueLabel(s, today))
        // Doing it now still moves to the following Sunday, not back to the missed one.
        assertEquals(d("2026-11-01"), ChoreSchedule.complete(everySunday, sunday, today))
    }

    @Test
    fun `rolled onto today shows as today with the miss`() {
        val s = ChoreSchedule.status(everySunday, sunday, d("2026-10-25"))
        assertEquals(0, s.daysLate)
        assertEquals(1, s.missed)
        assertEquals("Today · missed 1", ChoreSchedule.dueLabel(s, d("2026-10-25")))
    }

    @Test
    fun `after done counts from the day it was done`() {
        val plants = Repeat.AfterDone(3, Repeat.Unit.DAYS)
        assertEquals(d("2026-10-21"), ChoreSchedule.complete(plants, sunday, sunday))
        assertEquals(d("2026-10-23"), ChoreSchedule.complete(plants, sunday, d("2026-10-20")))
        val s = ChoreSchedule.status(plants, sunday, d("2026-10-20"))
        assertEquals(2, s.daysLate)
        assertEquals(0, s.missed)
        val monthly = Repeat.AfterDone(2, Repeat.Unit.MONTHS)
        assertEquals(d("2026-12-18"), ChoreSchedule.complete(monthly, sunday, sunday))
    }

    @Test
    fun `skip moves on without counting as done`() {
        assertEquals(d("2026-10-25"), ChoreSchedule.skip(everySunday, sunday, d("2026-10-16")))
        assertEquals(d("2026-10-25"), ChoreSchedule.skip(everySunday, sunday, d("2026-10-20")))
        val plants = Repeat.AfterDone(3, Repeat.Unit.DAYS)
        assertEquals(d("2026-10-23"), ChoreSchedule.skip(plants, sunday, d("2026-10-20")))
    }

    @Test
    fun `resume never leaves it due in the past`() {
        assertEquals(d("2026-11-01"), ChoreSchedule.resume(everySunday, sunday, d("2026-10-27")))
        assertEquals(d("2026-10-27"), ChoreSchedule.resume(Repeat.AfterDone(3, Repeat.Unit.DAYS), sunday, d("2026-10-27")))
        assertEquals(d("2026-11-01"), ChoreSchedule.resume(everySunday, d("2026-11-01"), d("2026-10-27")))
    }

    @Test
    fun `groups and labels`() {
        val today = sunday
        fun group(due: String) = ChoreSchedule.group(ChoreSchedule.status(everySunday, d(due), today), today)
        assertEquals(ChoreGroup.TODAY, group("2026-10-18"))
        assertEquals(ChoreGroup.THIS_WEEK, group("2026-10-24"))
        assertEquals(ChoreGroup.LATER, group("2026-10-25"))
        assertEquals("Tomorrow", dateLabel(d("2026-10-19"), today))
        assertEquals("Wed", dateLabel(d("2026-10-21"), today))
        assertEquals("15 Nov", dateLabel(d("2026-11-15"), today))
    }

    @Test
    fun `first due`() {
        assertEquals(d("2026-10-25"), ChoreSchedule.firstDue(everySunday, d("2026-10-19")))
        assertEquals(d("2026-10-19"), ChoreSchedule.firstDue(Repeat.AfterDone(3, Repeat.Unit.DAYS), d("2026-10-19")))
        assertEquals(d("2026-10-18"), ChoreSchedule.firstDue(Repeat.AfterDone(3, Repeat.Unit.DAYS), d("2026-10-19"), d("2026-10-15")))
    }

    @Test
    fun `rule text`() {
        assertEquals("Every Sunday", RepeatText.describe(everySunday))
        assertEquals("Mon & Thu", RepeatText.describe(Repeat.Weekly(listOf(4, 1), anchor = sunday)))
        assertEquals("Mon, Wed & Fri", RepeatText.describe(Repeat.Weekly(listOf(1, 3, 5), anchor = sunday)))
        assertEquals("Every 2 weeks on Wednesday", RepeatText.describe(Repeat.Weekly(listOf(3), 2, sunday)))
        assertEquals("24th of every month", RepeatText.describe(Repeat.MonthlyOnDate(24, anchor = sunday)))
        assertEquals("Every 3 months on the 10th", RepeatText.describe(Repeat.MonthlyOnDate(10, 3, sunday)))
        assertEquals("First Saturday of the month", RepeatText.describe(Repeat.MonthlyOnWeekday(1, 6, anchor = sunday)))
        assertEquals("Last day of the month", RepeatText.describe(Repeat.MonthlyLastDay(anchor = sunday)))
        assertEquals("Every year on 1 Nov", RepeatText.describe(Repeat.Yearly(11, 1)))
        assertEquals("3 days after last done", RepeatText.describe(Repeat.AfterDone(3, Repeat.Unit.DAYS)))
        assertEquals("1 week after last done", RepeatText.describe(Repeat.AfterDone(1, Repeat.Unit.WEEKS)))
        assertEquals("Every day", RepeatText.describe(Repeat.Daily(anchor = sunday)))
        assertEquals("11th", RepeatText.ordinal(11))
        assertEquals("22nd", RepeatText.ordinal(22))
    }

    @Test
    fun `rules survive a round trip through JSON`() {
        val rules = listOf(
            everySunday,
            Repeat.Daily(2, sunday),
            Repeat.MonthlyOnDate(31, 1, sunday),
            Repeat.MonthlyOnWeekday(-1, 5, 1, sunday),
            Repeat.MonthlyLastDay(2, sunday),
            Repeat.Yearly(2, 29),
            Repeat.AfterDone(3, Repeat.Unit.WEEKS),
        )
        rules.forEach { assertEquals(it, Repeat.decode(Repeat.encode(it))) }
        assertTrue(Repeat.encode(everySunday).contains("\"weekly\""))
        assertFalse(Repeat.Daily(2, sunday).occursOn(d("2026-10-19")))
    }
}
