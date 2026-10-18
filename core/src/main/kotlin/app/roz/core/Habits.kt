package app.roz.core

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.pow
import kotlin.math.roundToInt

enum class HabitKind {
    /** Done or not ("Meditate"). */
    YES_NO,

    /** Reach at least [Habit.target] ("8 glasses of water"); each tick adds one. */
    AT_LEAST,

    /** Stay at or under [Habit.target] ("at most 2 cups of chai"); an empty day counts once it ends. */
    AT_MOST,
}

@Serializable
data class Habit(
    val id: Long,
    val name: String,
    val kind: HabitKind = HabitKind.YES_NO,
    val target: Int = 1,
    /** null = every day; otherwise this many days per Monday–Sunday week. */
    val perWeek: Int? = null,
    val createdOn: Day,
    /** Minutes after midnight for its own reminder, if any. */
    val reminderMinute: Int? = null,
)

/** What was logged for a habit on one day. */
@Serializable
data class HabitDay(val date: Day, val value: Int = 0, val skipped: Boolean = false)

enum class DayMark {
    DONE,

    /** Skipped on purpose or the week's free miss: does not break the streak. */
    FORGIVEN,
    MISSED,

    /** Today, not finished yet: never breaks anything. */
    PENDING,

    /** A non-target day of an N-times-a-week habit, or before the habit existed. */
    EMPTY,
}

data class HabitStats(
    val streak: Int,
    /** True when [streak] counts weeks (N-times-a-week habits). */
    val streakInWeeks: Boolean,
    /** 0–100. */
    val strength: Int,
    val todayValue: Int,
    val doneToday: Boolean,
    /** Days done so far this week, for N-times-a-week habits. */
    val doneThisWeek: Int,
    val freeSkipLeft: Boolean,
    /** Monday … Sunday of the current week. */
    val week: List<DayMark>,
)

/** Streak, free skip and strength score, as described in SPEC.md §3. */
object HabitMath {
    /** Strength halves its distance to the new value in about two weeks. */
    private val DECAY = 0.5.pow(1.0 / 14)

    fun isMet(habit: Habit, value: Int): Boolean = when (habit.kind) {
        HabitKind.YES_NO, HabitKind.AT_LEAST -> value >= habit.target
        HabitKind.AT_MOST -> value <= habit.target
    }

    /** Marks from the habit's first day to [today], one per day. */
    fun marks(habit: Habit, days: Map<LocalDate, HabitDay>, today: LocalDate): Map<LocalDate, DayMark> {
        val out = LinkedHashMap<LocalDate, DayMark>()
        var date = habit.createdOn
        var forgivenWeek: LocalDate? = null
        while (!date.isAfter(today)) {
            val entry = days[date]
            val value = entry?.value ?: 0
            val met = isMet(habit, value) && entry?.skipped != true
            val isToday = date == today
            out[date] = if (habit.perWeek != null) {
                when {
                    met && !(isToday && habit.kind == HabitKind.AT_MOST) -> DayMark.DONE
                    isToday -> DayMark.PENDING
                    else -> DayMark.EMPTY
                }
            } else {
                val failedToday = entry?.skipped == true || (habit.kind == HabitKind.AT_MOST && value > habit.target)
                when {
                    isToday && !failedToday && (habit.kind == HabitKind.AT_MOST || !met) -> DayMark.PENDING
                    met -> DayMark.DONE
                    forgivenWeek != monday(date) -> {
                        forgivenWeek = monday(date)
                        DayMark.FORGIVEN
                    }
                    else -> DayMark.MISSED
                }
            }
            date = date.plusDays(1)
        }
        return out
    }

    fun stats(habit: Habit, entries: Collection<HabitDay>, today: LocalDate): HabitStats {
        val days = entries.associateBy { it.date }
        val marks = marks(habit, days, today)
        val thisMonday = monday(today)
        val week = (0..6L).map { offset ->
            val date = thisMonday.plusDays(offset)
            marks[date] ?: DayMark.EMPTY
        }
        val todayValue = days[today]?.value ?: 0
        val doneThisWeek = week.count { it == DayMark.DONE }
        val perWeek = habit.perWeek
        return HabitStats(
            streak = if (perWeek != null) weekStreak(marks, perWeek, today) else dayStreak(marks, today),
            streakInWeeks = perWeek != null,
            strength = strength(habit, marks, today),
            todayValue = todayValue,
            doneToday = marks[today] == DayMark.DONE || (habit.kind != HabitKind.AT_MOST && isMet(habit, todayValue)),
            doneThisWeek = doneThisWeek,
            freeSkipLeft = perWeek == null && week.none { it == DayMark.FORGIVEN },
            week = week,
        )
    }

    private fun dayStreak(marks: Map<LocalDate, DayMark>, today: LocalDate): Int {
        var streak = 0
        var date = today
        while (true) {
            when (marks[date] ?: return streak) {
                DayMark.DONE -> streak++
                DayMark.FORGIVEN, DayMark.PENDING, DayMark.EMPTY -> Unit
                DayMark.MISSED -> return streak
            }
            date = date.minusDays(1)
        }
    }

    private fun weekStreak(marks: Map<LocalDate, DayMark>, perWeek: Int, today: LocalDate): Int {
        val byWeek = marks.entries.groupBy({ monday(it.key) }, { it.value })
        var streak = 0
        var week = monday(today)
        while (true) {
            val done = byWeek[week]?.count { it == DayMark.DONE } ?: return streak
            if (done >= perWeek) {
                streak++
            } else if (week != monday(today)) {
                // The current week is still in progress; any earlier short week ends the streak.
                return streak
            }
            week = week.minusWeeks(1)
        }
    }

    private fun strength(habit: Habit, marks: Map<LocalDate, DayMark>, today: LocalDate): Int {
        var score = 0.0
        val perWeek = habit.perWeek
        val done = marks.filterValues { it == DayMark.DONE }.keys
        for ((date, mark) in marks) {
            val value = if (perWeek != null) {
                if (date == today && mark != DayMark.DONE) continue
                val last7 = (0..6L).count { date.minusDays(it) in done }
                minOf(1.0, last7.toDouble() / perWeek)
            } else when (mark) {
                DayMark.DONE -> 1.0
                DayMark.MISSED -> 0.0
                else -> continue
            }
            score = score * DECAY + value * (1 - DECAY)
        }
        return (score * 100).roundToInt()
    }

    /** "23 days", "6 wks". */
    fun streakLabel(stats: HabitStats): String =
        if (stats.streakInWeeks) "${stats.streak} wk${if (stats.streak == 1) "" else "s"}"
        else "${stats.streak} day${if (stats.streak == 1) "" else "s"}"

    /** "Every day", "4 times a week · 3 of 4", "8 a day", "At most 2 a day". */
    fun targetLabel(habit: Habit, stats: HabitStats? = null): String {
        val amount = when (habit.kind) {
            HabitKind.YES_NO -> null
            HabitKind.AT_LEAST -> "${habit.target} a day"
            HabitKind.AT_MOST -> "At most ${habit.target} a day"
        }
        val perWeek = habit.perWeek
        val schedule = if (perWeek == null) "Every day" else {
            val base = "$perWeek times a week"
            if (stats != null) "$base · ${minOf(stats.doneThisWeek, perWeek)} of $perWeek" else base
        }
        return listOfNotNull(amount, schedule).joinToString(" · ")
    }

    fun daysSince(from: LocalDate, to: LocalDate) = ChronoUnit.DAYS.between(from, to)
}

internal fun monday(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
