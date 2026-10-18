package app.roz.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import app.roz.core.Habit
import app.roz.core.Reminder
import app.roz.core.Reminders
import app.roz.data.Prefs
import app.roz.data.Repository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Sets the alarms behind the morning summary, evening nudge, reminders and habit reminders. */
class Alarms(private val context: Context, private val repository: Repository, private val prefs: Prefs) {
    private val manager = context.getSystemService(AlarmManager::class.java)

    suspend fun rescheduleAll() {
        scheduleDaily(AlarmReceiver.MORNING, prefs.settings.morningMinute)
        scheduleDaily(AlarmReceiver.EVENING, prefs.settings.eveningMinute)
        repository.remindersNow().forEach { schedule(it.reminder) }
        repository.habitsNow().forEach { schedule(it) }
    }

    fun scheduleDaily(action: String, minute: Int) {
        val now = LocalDateTime.now()
        var at = LocalDate.now().atTime(LocalTime.of(minute / 60, minute % 60))
        if (!at.isAfter(now)) at = at.plusDays(1)
        set(at, action, 0)
    }

    fun schedule(reminder: Reminder) {
        val at = Reminders.ringAt(reminder)
        if (at == null || at.isBefore(LocalDateTime.now().minusMinutes(1))) cancel(AlarmReceiver.REMINDER, reminder.id)
        else set(at, AlarmReceiver.REMINDER, reminder.id)
    }

    fun schedule(habit: Habit, at: LocalDateTime? = null) {
        val minute = habit.reminderMinute ?: return cancel(AlarmReceiver.HABIT, habit.id)
        val time = at ?: LocalDate.now().atTime(LocalTime.of(minute / 60, minute % 60)).let {
            if (it.isAfter(LocalDateTime.now())) it else it.plusDays(1)
        }
        set(time, AlarmReceiver.HABIT, habit.id)
    }

    fun cancel(action: String, id: Long) = manager.cancel(pending(action, id))

    private fun set(at: LocalDateTime, action: String, id: Long) {
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = pending(action, id)
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } else {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        }
    }

    private fun pending(action: String, id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, 0, AlarmReceiver.intent(context, action, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
