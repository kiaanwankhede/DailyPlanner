package app.roz.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** How many things the user has set up so far (deleted ones don't count). */
data class ItemCounts(val habits: Int, val chores: Int, val people: Int, val repeatingReminders: Int) {
    val total get() = habits + chores + people + repeatingReminders
}

/** What the weekly prompt suggests adding. */
enum class PromptKind(val word: String) { BIRTHDAY("birthday"), HABIT("habit"), CHORE("chore") }

/**
 * Where the weekly "add one recurring item" quota stands: [expected] is one per week since [start],
 * [added] is what the user has now, and [behind] is the gap that is left (SPEC.md §12).
 */
data class AddPrompt(val start: LocalDate, val expected: Int, val added: Int) {
    val behind get() = maxOf(0, expected - added)
}

object AddPrompts {
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    /** One item is due on [start] itself and one more every 7 days after, so 1–7 Jan is week 1. */
    fun expected(start: LocalDate, today: LocalDate): Int =
        if (today.isBefore(start)) 0 else (ChronoUnit.DAYS.between(start, today) / 7).toInt() + 1

    fun status(start: LocalDate, today: LocalDate, counts: ItemCounts) =
        AddPrompt(start, expected(start, today), counts.total)

    /** True on the first day of each 7-day week, when the weekly notification goes out. */
    fun isNewWeekDay(start: LocalDate, today: LocalDate): Boolean =
        !today.isBefore(start) && ChronoUnit.DAYS.between(start, today) % 7 == 0L

    /** Suggests the kind the user has the fewest of, so the list stays balanced. Ties go birthday, habit, chore. */
    fun suggest(counts: ItemCounts): PromptKind = listOf(
        PromptKind.BIRTHDAY to counts.people,
        PromptKind.HABIT to counts.habits,
        PromptKind.CHORE to counts.chores + counts.repeatingReminders,
    ).minBy { it.second }.first

    /** "33 to go · 1 a week since 1 Jan" */
    fun detail(p: AddPrompt): String = "${p.behind} to go · 1 a week since ${p.start.format(shortDate)}"

    fun title(p: AddPrompt): String = "Add 1 recurring item"

    fun notificationBody(p: AddPrompt, suggestion: PromptKind): String =
        "${p.behind} to go since ${p.start.format(shortDate)}. Suggestion: a ${suggestion.word}. Any habit, chore, birthday or repeating reminder counts."
}
