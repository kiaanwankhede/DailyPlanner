package app.roz.core

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.Month
import java.time.MonthDay
import java.time.temporal.ChronoUnit

enum class OccasionKind { BIRTHDAY, ANNIVERSARY }

@Serializable
data class GiftIdea(val text: String, val done: Boolean = false)

@Serializable
data class Person(
    val id: Long,
    val name: String,
    val relation: String = "",
    val kind: OccasionKind = OccasionKind.BIRTHDAY,
    val month: Int,
    val day: Int,
    /** Year of birth or wedding, if known. */
    val year: Int? = null,
    val phone: String? = null,
    val gifts: List<GiftIdea> = emptyList(),
)

data class Upcoming(val person: Person, val date: LocalDate, val daysAway: Int, val turning: Int?)

/** Next birthdays and anniversaries, and when to alert about them (SPEC.md §6). */
object Occasions {
    /** Days before the date on which an alert goes out. */
    val ALERT_DAYS = setOf(7, 1, 0)

    fun next(person: Person, today: LocalDate): Upcoming {
        var date = onYear(person, today.year)
        if (date.isBefore(today)) date = onYear(person, today.year + 1)
        return Upcoming(
            person = person,
            date = date,
            daysAway = ChronoUnit.DAYS.between(today, date).toInt(),
            turning = person.year?.let { date.year - it }?.takeIf { it > 0 },
        )
    }

    /** 29 February falls on 28 February in other years. */
    private fun onYear(person: Person, year: Int): LocalDate {
        val md = MonthDay.of(person.month, minOf(person.day, Month.of(person.month).maxLength()))
        return if (md.isValidYear(year)) md.atYear(year) else LocalDate.of(year, person.month, 28)
    }

    fun upcoming(people: Collection<Person>, today: LocalDate): List<Upcoming> =
        people.map { next(it, today) }.sortedWith(compareBy({ it.daysAway }, { it.person.name }))

    fun alertsFor(people: Collection<Person>, today: LocalDate): List<Upcoming> =
        upcoming(people, today).filter { it.daysAway in ALERT_DAYS }

    /** "Riya turns 9", "Aai & Baba · 35th anniversary", "Sameer's birthday". */
    fun headline(u: Upcoming): String = when (u.person.kind) {
        OccasionKind.BIRTHDAY -> u.turning?.let { "${u.person.name} turns $it" } ?: "${u.person.name}'s birthday"
        OccasionKind.ANNIVERSARY -> u.turning?.let { "${u.person.name} · ${RepeatText.ordinal(it)} anniversary" }
            ?: "${u.person.name}'s anniversary"
    }

    /** "Turns 34 · College friend", "35th wedding anniversary". */
    fun detail(u: Upcoming): String {
        val what = when (u.person.kind) {
            OccasionKind.BIRTHDAY -> u.turning?.let { "Turns $it" }
            OccasionKind.ANNIVERSARY -> u.turning?.let { "${RepeatText.ordinal(it)} anniversary" } ?: "Anniversary"
        }
        return listOfNotNull(what, u.person.relation.ifBlank { null }).joinToString(" · ")
    }

    fun daysAwayLabel(daysAway: Int): String = when (daysAway) {
        0 -> "Today"
        1 -> "Tomorrow"
        else -> "$daysAway days"
    }

    /** The alert text for one occasion on [today]. */
    fun alertText(u: Upcoming): String = when (u.daysAway) {
        0 -> "Today: ${headline(u)}"
        1 -> "Tomorrow: ${headline(u)}"
        else -> "In ${u.daysAway} days: ${headline(u)}" +
            if (u.person.gifts.any { !it.done }) " · gift ideas saved" else " · time to plan a gift"
    }

    /** A ready-to-send wish. */
    fun wish(u: Upcoming): String = when (u.person.kind) {
        OccasionKind.BIRTHDAY -> "Happy birthday, ${u.person.name}! 🎂 Wishing you a wonderful year ahead."
        OccasionKind.ANNIVERSARY -> "Happy anniversary, ${u.person.name}! Wishing you many more happy years together."
    }
}
