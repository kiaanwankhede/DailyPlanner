package app.roz.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

typealias Day = @Serializable(with = LocalDateSerializer::class) LocalDate

/**
 * How something repeats. [Fixed] rules sit on a calendar whether you are early or late;
 * [AfterDone] counts from the day you actually did it.
 *
 * Days of the week are ISO numbers: 1 = Monday … 7 = Sunday.
 */
@Serializable
sealed interface Repeat {

    @Serializable
    sealed interface Fixed : Repeat {
        fun occursOn(date: LocalDate): Boolean
    }

    /** Every day, or every [everyDays] days counted from [anchor]. */
    @Serializable
    @SerialName("daily")
    data class Daily(val everyDays: Int = 1, val anchor: Day) : Fixed {
        override fun occursOn(date: LocalDate) =
            Math.floorMod(ChronoUnit.DAYS.between(anchor, date), everyDays.toLong()) == 0L
    }

    /** On [days] of the week, in every [everyWeeks]th week counted from the week of [anchor]. */
    @Serializable
    @SerialName("weekly")
    data class Weekly(val days: List<Int>, val everyWeeks: Int = 1, val anchor: Day) : Fixed {
        override fun occursOn(date: LocalDate): Boolean {
            if (date.dayOfWeek.value !in days) return false
            val weeks = ChronoUnit.WEEKS.between(monday(anchor), monday(date))
            return Math.floorMod(weeks, everyWeeks.toLong()) == 0L
        }
    }

    /** On [day] of the month (the last day in shorter months), every [everyMonths] months from [anchor]. */
    @Serializable
    @SerialName("monthly_date")
    data class MonthlyOnDate(val day: Int, val everyMonths: Int = 1, val anchor: Day) : Fixed {
        override fun occursOn(date: LocalDate) =
            inMonthCycle(anchor, date, everyMonths) && date.dayOfMonth == minOf(day, date.lengthOfMonth())
    }

    /** On the [week]th (1–4, or -1 for last) [dayOfWeek] of the month, every [everyMonths] months. */
    @Serializable
    @SerialName("monthly_weekday")
    data class MonthlyOnWeekday(val week: Int, val dayOfWeek: Int, val everyMonths: Int = 1, val anchor: Day) : Fixed {
        override fun occursOn(date: LocalDate): Boolean {
            if (!inMonthCycle(anchor, date, everyMonths) || date.dayOfWeek.value != dayOfWeek) return false
            return if (week == -1) date.plusWeeks(1).month != date.month else (date.dayOfMonth - 1) / 7 + 1 == week
        }
    }

    /** On the last day of the month, every [everyMonths] months. */
    @Serializable
    @SerialName("monthly_last")
    data class MonthlyLastDay(val everyMonths: Int = 1, val anchor: Day) : Fixed {
        override fun occursOn(date: LocalDate) =
            inMonthCycle(anchor, date, everyMonths) && date.dayOfMonth == date.lengthOfMonth()
    }

    /** Once a year on [month]/[day]; 29 Feb falls on 28 Feb in other years. */
    @Serializable
    @SerialName("yearly")
    data class Yearly(val month: Int, val day: Int) : Fixed {
        override fun occursOn(date: LocalDate) =
            date.monthValue == month && date.dayOfMonth == minOf(day, date.lengthOfMonth())
    }

    /** [amount] [unit] after the day it was last done. */
    @Serializable
    @SerialName("after_done")
    data class AfterDone(val amount: Int, val unit: Unit) : Repeat {
        fun from(doneOn: LocalDate): LocalDate = when (unit) {
            Unit.DAYS -> doneOn.plusDays(amount.toLong())
            Unit.WEEKS -> doneOn.plusWeeks(amount.toLong())
            Unit.MONTHS -> doneOn.plusMonths(amount.toLong())
        }
    }

    enum class Unit { DAYS, WEEKS, MONTHS }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun encode(repeat: Repeat): String = json.encodeToString(serializer(), repeat)
        fun decode(text: String): Repeat = json.decodeFromString(serializer(), text)
    }
}

private fun inMonthCycle(anchor: LocalDate, date: LocalDate, every: Int): Boolean {
    val months = ChronoUnit.MONTHS.between(YearMonth.from(anchor), YearMonth.from(date))
    return Math.floorMod(months, every.toLong()) == 0L
}

/** Longest gap any rule can have (every 12 months, plus slack), so searches always end. */
private const val SEARCH_DAYS = 12 * 31 * 4L

/** The first date on or after [from] that the rule falls on. */
fun Repeat.Fixed.nextOnOrAfter(from: LocalDate): LocalDate {
    var d = from
    repeat(SEARCH_DAYS.toInt()) {
        if (occursOn(d)) return d
        d = d.plusDays(1)
    }
    error("No date found for $this after $from")
}

/** The first date strictly after [date] that the rule falls on. */
fun Repeat.Fixed.nextAfter(date: LocalDate): LocalDate = nextOnOrAfter(date.plusDays(1))
