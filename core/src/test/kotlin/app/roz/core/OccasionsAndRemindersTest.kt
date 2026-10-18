package app.roz.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class OccasionsAndRemindersTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun t(s: String) = LocalDateTime.parse(s)
    private val today = d("2026-10-18")

    private val riya = Person(1, "Riya", "Niece", month = 10, day = 20, year = 2017)
    private val parents = Person(2, "Aai & Baba", kind = OccasionKind.ANNIVERSARY, month = 10, day = 24, year = 1991)
    private val sameer = Person(3, "Sameer", "College friend", month = 11, day = 3, year = 1992)
    private val leap = Person(4, "Leela", month = 2, day = 29)
    private val past = Person(5, "Nani", month = 1, day = 5, year = 1948)

    @Test
    fun `next birthday this year or next`() {
        val r = Occasions.next(riya, today)
        assertEquals(d("2026-10-20"), r.date)
        assertEquals(2, r.daysAway)
        assertEquals(9, r.turning)
        assertEquals("Riya turns 9", Occasions.headline(r))
        assertEquals("Turns 9 · Niece", Occasions.detail(r))
        val n = Occasions.next(past, today)
        assertEquals(d("2027-01-05"), n.date)
        assertEquals(79, n.turning)
    }

    @Test
    fun `leap day birthday falls on 28 February`() {
        assertEquals(d("2027-02-28"), Occasions.next(leap, today).date)
        assertEquals(d("2028-02-29"), Occasions.next(leap, d("2027-03-01")).date)
        assertNull(Occasions.next(leap, today).turning)
        assertEquals("Leela's birthday", Occasions.headline(Occasions.next(leap, today)))
    }

    @Test
    fun `anniversaries`() {
        val a = Occasions.next(parents, today)
        assertEquals("Aai & Baba · 35th anniversary", Occasions.headline(a))
        assertEquals("35th anniversary", Occasions.detail(a))
    }

    @Test
    fun `alerts 7 days before, 1 day before and on the day`() {
        val people = listOf(riya, parents, sameer)
        assertEquals(emptyList<String>(), Occasions.alertsFor(people, today).map { it.person.name })
        assertEquals(listOf("Riya"), Occasions.alertsFor(people, d("2026-10-13")).map { it.person.name })
        assertEquals(listOf("Riya"), Occasions.alertsFor(people, d("2026-10-19")).map { it.person.name })
        assertEquals(listOf("Riya"), Occasions.alertsFor(people, d("2026-10-20")).map { it.person.name })
        assertEquals(listOf("Aai & Baba"), Occasions.alertsFor(people, d("2026-10-17")).map { it.person.name })
        assertTrue(Occasions.alertText(Occasions.next(riya, d("2026-10-13"))).startsWith("In 7 days: Riya turns 9"))
    }

    @Test
    fun `upcoming is sorted by days away`() {
        val list = Occasions.upcoming(listOf(sameer, past, riya, parents), today)
        assertEquals(listOf("Riya", "Aai & Baba", "Sameer", "Nani"), list.map { it.person.name })
    }

    private val bill = Reminder(1, "Pay electricity bill", d("2026-10-18"), 19 * 60, Repeat.MonthlyOnDate(18, anchor = d("2026-10-18")))
    private val once = Reminder(2, "Call bank", d("2026-10-19"), 11 * 60)

    @Test
    fun `one-off reminders finish when done`() {
        assertEquals(t("2026-10-19T11:00"), Reminders.ringAt(once))
        val done = Reminders.complete(once, t("2026-10-19T11:05"))
        assertNull(Reminders.ringAt(done))
    }

    @Test
    fun `repeating reminders move to their next date`() {
        val next = Reminders.complete(bill, t("2026-10-18T19:10"))
        assertEquals(d("2026-11-18"), next.date)
        assertEquals(t("2026-11-18T19:00"), Reminders.ringAt(next))
    }

    @Test
    fun `a reminder missed for days moves past today`() {
        val vitamin = Reminder(3, "Vitamin D", d("2026-10-15"), 21 * 60, Repeat.Daily(anchor = d("2026-10-01")))
        assertEquals(d("2026-10-19"), Reminders.complete(vitamin, t("2026-10-18T08:00")).date)
    }

    @Test
    fun `snoozing`() {
        val hour = Reminders.snoozeHour(bill, t("2026-10-18T19:00"))
        assertEquals(t("2026-10-18T20:00"), Reminders.ringAt(hour))
        val tomorrow = Reminders.snoozeTomorrow(bill, t("2026-10-18T19:00"))
        assertEquals(t("2026-10-19T19:00"), Reminders.ringAt(tomorrow))
        // Doing it after a snooze clears the snooze and moves to the next month.
        assertEquals(t("2026-11-18T19:00"), Reminders.ringAt(Reminders.complete(hour, t("2026-10-18T20:00"))))
    }

    @Test
    fun `time labels`() {
        assertEquals("7:00 PM", Reminders.timeLabel(19 * 60))
        assertEquals("12:05 AM", Reminders.timeLabel(5))
        assertEquals("12:30 PM", Reminders.timeLabel(12 * 60 + 30))
    }
}
