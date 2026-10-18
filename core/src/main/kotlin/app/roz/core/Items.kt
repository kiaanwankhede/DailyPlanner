package app.roz.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDateTime = LocalDateTime.parse(decoder.decodeString())
}

typealias DateTime = @Serializable(with = LocalDateTimeSerializer::class) LocalDateTime

@Serializable
data class Chore(
    val id: Long,
    val name: String,
    val area: String = "",
    val rule: Repeat,
    /** The date it is (or was) due; see [ChoreSchedule.status] for how overdue ones roll forward. */
    val due: Day,
    val lastDone: Day? = null,
    val paused: Boolean = false,
    val inMorningSummary: Boolean = true,
)

enum class ChoreAction { DONE, SKIPPED, MISSED }

@Serializable
data class ChoreLogEntry(val choreId: Long, val date: Day, val action: ChoreAction)

@Serializable
data class Reminder(
    val id: Long,
    val title: String,
    /** The date of the current occurrence. */
    val date: Day,
    /** Minutes after midnight. */
    val minute: Int,
    /** null = once. */
    val rule: Repeat.Fixed? = null,
    /** Set by "In 1 hour" / "Tomorrow"; rings then instead. Cleared when the occurrence moves on. */
    val snoozedUntil: DateTime? = null,
    /** Set when a one-off reminder is done. */
    val doneAt: DateTime? = null,
)

/** Exact-time reminders (SPEC.md §5). */
object Reminders {
    fun time(r: Reminder): LocalTime = LocalTime.of(r.minute / 60, r.minute % 60)

    fun scheduledAt(r: Reminder): LocalDateTime = r.date.atTime(time(r))

    /** When it should ring next, or null once a one-off is done. */
    fun ringAt(r: Reminder): LocalDateTime? = if (r.doneAt != null) null else r.snoozedUntil ?: scheduledAt(r)

    /** Mark the current occurrence done: a one-off is finished, a repeating one moves to its next date. */
    fun complete(r: Reminder, now: LocalDateTime): Reminder {
        val rule = r.rule ?: return r.copy(doneAt = now, snoozedUntil = null)
        // If it was missed for days, move past today rather than to another day in the past.
        return r.copy(date = rule.nextAfter(maxOf(r.date, now.toLocalDate())), snoozedUntil = null)
    }

    fun snoozeHour(r: Reminder, now: LocalDateTime): Reminder = r.copy(snoozedUntil = now.plusHours(1))

    fun snoozeTomorrow(r: Reminder, now: LocalDateTime): Reminder =
        r.copy(snoozedUntil = now.toLocalDate().plusDays(1).atTime(time(r)))

    /** The next occurrence for a new reminder starting on [date] at [minute]. */
    fun firstDate(rule: Repeat.Fixed?, date: LocalDate): LocalDate = rule?.nextOnOrAfter(date) ?: date

    fun timeLabel(minute: Int): String {
        val h = minute / 60
        val m = minute % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
    }
}
