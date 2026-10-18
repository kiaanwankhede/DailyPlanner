package app.roz.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** CSV for Excel/Sheets and an .ics calendar of birthdays (SPEC.md §7). */
object Exports {

    fun habitsCsv(habits: List<Habit>, log: List<HabitLog>): String {
        val names = habits.associate { it.id to it.name }
        val rows = log.sortedWith(compareBy({ it.day.date }, { names[it.habitId] ?: "" })).map {
            val status = if (it.day.skipped) "skipped" else "logged"
            listOf(it.day.date.toString(), names[it.habitId] ?: "?", it.day.value.toString(), status)
        }
        return csv(listOf("date", "habit", "value", "status"), rows)
    }

    fun choresCsv(chores: List<Chore>, log: List<ChoreLogEntry>): String {
        val names = chores.associate { it.id to it.name }
        val rows = log.sortedBy { it.date }.map {
            listOf(it.date.toString(), names[it.choreId] ?: "?", it.action.name.lowercase())
        }
        return csv(listOf("date", "chore", "action"), rows)
    }

    private fun csv(header: List<String>, rows: List<List<String>>): String =
        (listOf(header) + rows).joinToString("\r\n", postfix = "\r\n") { row -> row.joinToString(",") { escape(it) } }

    private fun escape(value: String): String {
        // A leading = + - @ would run as a formula in a spreadsheet.
        val safe = if (value.firstOrNull() in setOf('=', '+', '-', '@') && value.toDoubleOrNull() == null) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private val icsDate = DateTimeFormatter.BASIC_ISO_DATE

    fun birthdaysIcs(people: List<Person>, stamp: LocalDate): String = buildString {
        append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Roz//Birthdays//EN\r\nCALSCALE:GREGORIAN\r\n")
        for (p in people) {
            // Start in 2000 (a leap year) so 29 February is a real date; a leap-day birthday repeats on
            // the last day of February.
            val year = p.year ?: 2000
            val start = LocalDate.of(year, p.month, minOf(p.day, YearMonth.of(year, p.month).lengthOfMonth()))
            val rule = if (p.month == 2 && p.day == 29) "FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=-1" else "FREQ=YEARLY"
            val title = when (p.kind) {
                OccasionKind.BIRTHDAY -> "${p.name}'s birthday"
                OccasionKind.ANNIVERSARY -> "${p.name}'s anniversary"
            }
            append("BEGIN:VEVENT\r\n")
            append("UID:roz-person-${p.id}@roz.app\r\n")
            append("DTSTAMP:${stamp.format(icsDate)}T000000Z\r\n")
            append("DTSTART;VALUE=DATE:${start.format(icsDate)}\r\n")
            append("RRULE:$rule\r\n")
            append("SUMMARY:${icsText(title)}\r\n")
            append("TRANSP:TRANSPARENT\r\n")
            append("END:VEVENT\r\n")
        }
        append("END:VCALENDAR\r\n")
    }

    private fun icsText(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
}
