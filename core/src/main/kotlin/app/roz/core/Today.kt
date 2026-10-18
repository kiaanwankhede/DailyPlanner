package app.roz.core

import java.time.LocalDate

/** Everything on the Today screen, worked out from the stored items (SPEC.md §2). */
data class TodayPlan(
    val habits: List<HabitToday>,
    val overdue: List<ChoreToday>,
    val choresToday: List<ChoreToday>,
    val remindersToday: List<Reminder>,
    val birthdays: List<Upcoming>,
    val done: Int,
    val total: Int,
)

data class HabitToday(val habit: Habit, val stats: HabitStats)

data class ChoreToday(val chore: Chore, val status: ChoreStatus)

object TodayPlanner {
    fun plan(
        today: LocalDate,
        habits: List<HabitToday>,
        chores: List<Chore>,
        reminders: List<Reminder>,
        people: List<Person>,
        choresDoneToday: Int,
        remindersDoneToday: Int,
    ): TodayPlan {
        val active = chores.filter { !it.paused }.map { ChoreToday(it, ChoreSchedule.status(it.rule, it.due, today)) }
        val overdue = active.filter { it.status.isOverdue }.sortedByDescending { it.status.daysLate }
        val dueToday = active.filter { !it.status.isOverdue && it.status.due == today }.sortedBy { it.chore.name }
        val remindersToday = reminders
            .filter { it.doneAt == null && !it.date.isAfter(today) }
            .sortedBy { Reminders.ringAt(it) }
        val birthdays = Occasions.upcoming(people, today).filter { it.daysAway <= 7 }

        val habitsDone = habits.count { it.stats.doneToday }
        // A weekly-target habit that already hit its target this week doesn't count against today.
        val habitsCounted = habits.count { h ->
            val perWeek = h.habit.perWeek
            h.stats.doneToday || perWeek == null || h.stats.doneThisWeek < perWeek
        }
        val done = habitsDone + choresDoneToday + remindersDoneToday
        val total = habitsCounted + overdue.size + dueToday.size + remindersToday.size + choresDoneToday + remindersDoneToday
        return TodayPlan(habits, overdue, dueToday, remindersToday, birthdays, done, total)
    }
}

/** Text of the morning summary and evening nudge (SPEC.md §9). */
object Summaries {
    data class Note(val title: String, val body: String)

    fun morning(plan: TodayPlan): Note? {
        val parts = buildList {
            if (plan.overdue.isNotEmpty()) add("${plan.overdue.size} overdue")
            val dueCount = plan.choresToday.size
            if (dueCount > 0) add("$dueCount chore${if (dueCount == 1) "" else "s"} due")
            val rem = plan.remindersToday.size
            if (rem > 0) add("$rem reminder${if (rem == 1) "" else "s"}")
            val habits = plan.habits.count { !it.stats.doneToday }
            if (habits > 0) add("$habits habit${if (habits == 1) "" else "s"}")
        }
        if (parts.isEmpty()) return null
        val names = (plan.overdue.map { it.chore.name } + plan.choresToday.map { it.chore.name } +
            plan.remindersToday.map { "${it.title} at ${Reminders.timeLabel(it.minute)}" }).take(4)
        return Note("Today: " + parts.joinToString(" · "), names.joinToString(", "))
    }

    fun evening(plan: TodayPlan): Note? {
        val open = plan.habits.filter { h ->
            val perWeek = h.habit.perWeek
            !h.stats.doneToday && h.habit.kind != HabitKind.AT_MOST && (perWeek == null || h.stats.doneThisWeek < perWeek)
        }
        if (open.isEmpty()) return null
        val title = if (open.size == 1) "One habit left today" else "${open.size} habits left today"
        return Note(title, open.joinToString(", ") { it.habit.name })
    }
}
