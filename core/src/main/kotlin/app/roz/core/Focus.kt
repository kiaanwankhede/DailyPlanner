package app.roz.core

import kotlinx.serialization.Serializable
import java.time.LocalDate

enum class FocusType { HABIT, CHORE, REMINDER, PERSON }

/**
 * The one item marked as top priority. It shows first on Today and stays on the lock screen.
 * [until] is the last day a birthday or anniversary stays on top (the day itself).
 */
@Serializable
data class FocusPick(val type: FocusType, val id: Long, val until: Day? = null)

/** What the top-priority card and the lock-screen notification show. */
data class FocusView(
    val pick: FocusPick,
    val title: String,
    val detail: String,
    val kindLabel: String,
    /** Birthdays and anniversaries can only be removed, not ticked. */
    val canComplete: Boolean,
    val doneLabel: String,
)

/** Works out what the top-priority item looks like today, or null once it no longer applies (SPEC.md §11). */
object Focus {
    fun resolve(
        pick: FocusPick?,
        today: LocalDate,
        habits: List<HabitToday>,
        chores: List<Chore>,
        reminders: List<Reminder>,
        people: List<Person>,
    ): FocusView? {
        if (pick == null) return null
        return when (pick.type) {
            FocusType.HABIT -> habit(pick, habits)
            FocusType.CHORE -> chore(pick, today, chores)
            FocusType.REMINDER -> reminder(pick, today, reminders)
            FocusType.PERSON -> person(pick, today, people)
        }
    }

    /** "At most" habits never finish until the day ends, so they can't be the top priority. */
    fun canBePriority(habit: Habit) = habit.kind != HabitKind.AT_MOST

    private fun habit(pick: FocusPick, habits: List<HabitToday>): FocusView? {
        val h = habits.firstOrNull { it.habit.id == pick.id } ?: return null
        val habit = h.habit
        if (!canBePriority(habit) || h.stats.doneToday) return null
        val perWeek = habit.perWeek
        val progress = when {
            habit.kind == HabitKind.AT_LEAST -> "${h.stats.todayValue} of ${habit.target} today"
            perWeek != null -> "${minOf(h.stats.doneThisWeek, perWeek)} of $perWeek this week"
            else -> "Not done yet today"
        }
        val streak = when {
            h.stats.streak == 0 -> ""
            h.stats.streakInWeeks -> " · ${h.stats.streak}-week streak"
            else -> " · ${h.stats.streak}-day streak"
        }
        return FocusView(
            pick, habit.name, progress + streak, "Habit", canComplete = true,
            doneLabel = if (habit.kind == HabitKind.AT_LEAST) "Add one" else "Done",
        )
    }

    private fun chore(pick: FocusPick, today: LocalDate, chores: List<Chore>): FocusView? {
        val c = chores.firstOrNull { it.id == pick.id && !it.paused } ?: return null
        val status = ChoreSchedule.status(c.rule, c.due, today)
        val `when` = if (status.isOverdue) {
            ChoreSchedule.dueLabel(status, today)
        } else {
            val label = dateLabel(status.due, today)
            "Due " + if (label == "Today" || label == "Tomorrow") label.lowercase() else label
        }
        val detail = listOfNotNull(`when`, c.area.ifBlank { null }).joinToString(" · ")
        return FocusView(pick, c.name, detail, "Chore", canComplete = true, doneLabel = "Done")
    }

    private fun reminder(pick: FocusPick, today: LocalDate, reminders: List<Reminder>): FocusView? {
        val r = reminders.firstOrNull { it.id == pick.id && it.doneAt == null } ?: return null
        val at = Reminders.ringAt(r) ?: return null
        val day = at.toLocalDate()
        val time = Reminders.timeLabel(at.hour * 60 + at.minute)
        val detail = if (day.isBefore(today)) "Was due ${dateLabel(day, today)} at $time" else "${dateLabel(day, today)} · $time"
        return FocusView(pick, r.title, detail, "Reminder", canComplete = true, doneLabel = "Done")
    }

    private fun person(pick: FocusPick, today: LocalDate, people: List<Person>): FocusView? {
        val p = people.firstOrNull { it.id == pick.id } ?: return null
        val until = pick.until
        if (until != null && today.isAfter(until)) return null
        val u = Occasions.next(p, today)
        val away = Occasions.daysAwayLabel(u.daysAway).let { if (u.daysAway > 1) "in $it" else it }
        val detail = listOfNotNull(away, p.relation.ifBlank { null }).joinToString(" · ")
        val kind = if (p.kind == OccasionKind.BIRTHDAY) "Birthday" else "Anniversary"
        return FocusView(pick, Occasions.headline(u), detail, kind, canComplete = false, doneLabel = "")
    }
}
