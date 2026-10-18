package app.roz.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Where a chore stands today. A missed chore is always one item, never stacked copies. */
data class ChoreStatus(
    /** The date it counts as due: the stored due date, or the latest scheduled date it rolled to. */
    val due: LocalDate,
    /** Days past [due]; 0 when due today or later. */
    val daysLate: Int,
    /** Scheduled dates that went by without it being done (fixed rules only). */
    val missed: Int,
) {
    val isOverdue get() = daysLate > 0
}

enum class ChoreGroup { OVERDUE, TODAY, THIS_WEEK, LATER }

/** The rules from SPEC.md §4, as plain functions of the repeat rule, the stored due date and today. */
object ChoreSchedule {

    /** The first due date for a new (or resumed-from-nothing) chore. */
    fun firstDue(rule: Repeat, today: LocalDate, lastDone: LocalDate? = null): LocalDate = when (rule) {
        is Repeat.Fixed -> rule.nextOnOrAfter(today)
        is Repeat.AfterDone -> lastDone?.let(rule::from) ?: today
    }

    fun status(rule: Repeat, due: LocalDate, today: LocalDate): ChoreStatus {
        if (!due.isBefore(today) || rule !is Repeat.Fixed) {
            return ChoreStatus(due, maxOf(0, ChronoUnit.DAYS.between(due, today).toInt()), 0)
        }
        // Overdue on a schedule: roll forward to the latest scheduled date that has arrived.
        var current = due
        var missed = 0
        while (true) {
            val next = rule.nextAfter(current)
            if (next.isAfter(today)) break
            current = next
            missed++
        }
        return ChoreStatus(current, ChronoUnit.DAYS.between(current, today).toInt(), missed)
    }

    /** The next due date after doing the chore on [doneOn]. */
    fun complete(rule: Repeat, due: LocalDate, doneOn: LocalDate): LocalDate = when (rule) {
        // Early counts for the slot it was due in; on time or late moves to the next date after today.
        is Repeat.Fixed -> rule.nextAfter(if (doneOn.isBefore(due)) due else doneOn)
        is Repeat.AfterDone -> rule.from(doneOn)
    }

    /** "Skip this time": move on without marking it done. */
    fun skip(rule: Repeat, due: LocalDate, today: LocalDate): LocalDate = when (rule) {
        is Repeat.Fixed -> rule.nextAfter(maxOf(due, today))
        is Repeat.AfterDone -> rule.from(maxOf(due, today))
    }

    /** Coming back from a pause: never due in the past. */
    fun resume(rule: Repeat, due: LocalDate, today: LocalDate): LocalDate = when {
        !due.isBefore(today) -> due
        rule is Repeat.Fixed -> rule.nextOnOrAfter(today)
        else -> today
    }

    fun group(status: ChoreStatus, today: LocalDate): ChoreGroup = when {
        status.isOverdue -> ChoreGroup.OVERDUE
        status.due == today -> ChoreGroup.TODAY
        !status.due.isAfter(today.plusDays(6)) -> ChoreGroup.THIS_WEEK
        else -> ChoreGroup.LATER
    }

    /** The short label on the right of a chore row. */
    fun dueLabel(status: ChoreStatus, today: LocalDate): String {
        val late = when (status.daysLate) {
            0 -> null
            1 -> "1 day late"
            else -> "${status.daysLate} days late"
        }
        val missed = if (status.missed > 0) "missed ${status.missed}" else null
        if (late != null || missed != null) {
            return listOfNotNull(late ?: "Today", missed).joinToString(" · ")
        }
        return dateLabel(status.due, today)
    }
}

private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** "Today", "Tomorrow", "Wed" within a week, else "15 Nov". */
fun dateLabel(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days in 2..6 -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        else -> date.format(shortDate)
    }
}

/** Plain-English description of a rule, as shown under a chore or reminder. */
object RepeatText {
    private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val fullDayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val weekOrdinals = mapOf(1 to "First", 2 to "Second", 3 to "Third", 4 to "Fourth", -1 to "Last")

    fun describe(rule: Repeat): String = when (rule) {
        is Repeat.Daily -> if (rule.everyDays == 1) "Every day" else "Every ${rule.everyDays} days"
        is Repeat.Weekly -> weekly(rule)
        is Repeat.MonthlyOnDate ->
            if (rule.everyMonths == 1) "${ordinal(rule.day)} of every month"
            else "Every ${rule.everyMonths} months on the ${ordinal(rule.day)}"
        is Repeat.MonthlyOnWeekday -> {
            val base = "${weekOrdinals[rule.week]} ${fullDayNames[rule.dayOfWeek - 1]}"
            if (rule.everyMonths == 1) "$base of the month" else "$base, every ${rule.everyMonths} months"
        }
        is Repeat.MonthlyLastDay ->
            if (rule.everyMonths == 1) "Last day of the month" else "Last day, every ${rule.everyMonths} months"
        is Repeat.Yearly -> "Every year on ${rule.day} ${monthNames[rule.month - 1]}"
        is Repeat.AfterDone -> {
            val unit = when (rule.unit) {
                Repeat.Unit.DAYS -> "day"
                Repeat.Unit.WEEKS -> "week"
                Repeat.Unit.MONTHS -> "month"
            }
            "${rule.amount} $unit${if (rule.amount == 1) "" else "s"} after last done"
        }
    }

    private fun weekly(rule: Repeat.Weekly): String {
        val days = rule.days.sorted()
        val names = when {
            days == (1..7).toList() -> return if (rule.everyWeeks == 1) "Every day" else "Every day, every ${rule.everyWeeks} weeks"
            days.size == 1 -> fullDayNames[days[0] - 1]
            else -> days.dropLast(1).joinToString(", ") { dayNames[it - 1] } + " & " + dayNames[days.last() - 1]
        }
        return if (rule.everyWeeks == 1) {
            if (days.size == 1) "Every $names" else names
        } else {
            "Every ${rule.everyWeeks} weeks on $names"
        }
    }

    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }
}
