package app.roz.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.roz.RozApplication
import app.roz.core.AddPrompts
import app.roz.core.BackupHealth
import app.roz.core.FocusType
import app.roz.core.Occasions
import app.roz.core.Reminders
import app.roz.core.Summaries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** Handles alarms going off and the buttons on Roz's notifications. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val id = intent.data?.lastPathSegment?.toLongOrNull() ?: 0L
        val pending = goAsync()
        scope.launch {
            try {
                handle(context.applicationContext, action, id)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, action: String, id: Long) {
        val container = (context as RozApplication).container
        val repo = container.repository
        val alarms = container.alarms
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        when (action) {
            MORNING -> {
                container.refreshFocus()
                val plan = container.todayPlan(today)
                Summaries.morning(plan)?.let { Notifications.summary(context, it, evening = false) }
                Occasions.alertsFor(repo.peopleNow(), today).forEach { Notifications.birthday(context, it) }
                val state = container.prefs.state.value
                if (container.secrets.isSetUp() && BackupHealth.passphraseCheckDue(state.lastPassphraseCheck, today)) {
                    Notifications.passphraseCheck(context)
                }
                container.addPrompt(today)?.let { (prompt, suggestion) ->
                    val start = container.prefs.settings.addPromptStart ?: java.time.LocalDate.of(today.year, 1, 1)
                    if (AddPrompts.isNewWeekDay(start, today)) Notifications.addPrompt(context, prompt, suggestion)
                }
                alarms.scheduleDaily(MORNING, container.prefs.settings.morningMinute)
            }
            EVENING -> {
                Summaries.evening(container.todayPlan(today))?.let { Notifications.summary(context, it, evening = true) }
                alarms.scheduleDaily(EVENING, container.prefs.settings.eveningMinute)
            }
            REMINDER -> {
                val reminder = repo.reminder(id) ?: return
                val at = Reminders.ringAt(reminder) ?: return
                // Only ring if this is still the time it was set for (it may have been edited since).
                if (!at.isAfter(now.plusMinutes(1))) Notifications.reminder(context, reminder)
            }
            REMINDER_DONE, REMINDER_HOUR, REMINDER_TOMORROW -> {
                val stored = repo.remindersNow().firstOrNull { it.reminder.id == id } ?: return
                when (action) {
                    REMINDER_DONE -> repo.completeReminder(stored, now)
                    REMINDER_HOUR -> repo.updateReminder(stored, { Reminders.snoozeHour(it, now) })
                    else -> repo.updateReminder(stored, { Reminders.snoozeTomorrow(it, now) })
                }
                Notifications.cancel(context, Notifications.reminderId(id))
                repo.reminder(id)?.let { alarms.schedule(it) }
                if (action == REMINDER_DONE) container.clearFocusIf(FocusType.REMINDER, id) else container.refreshFocus()
            }
            HABIT -> {
                val habit = repo.habit(id) ?: return
                val plan = container.todayPlan(today)
                val done = plan.habits.firstOrNull { it.habit.id == id }?.stats?.doneToday == true
                if (!done) Notifications.habit(context, habit)
                alarms.schedule(habit)
            }
            HABIT_DONE -> {
                val habit = repo.habit(id) ?: return
                val plan = container.todayPlan(today)
                if (plan.habits.firstOrNull { it.habit.id == id }?.stats?.doneToday != true) repo.tickHabit(habit, today)
                Notifications.cancel(context, Notifications.habitId(id))
                container.refreshFocus()
            }
            HABIT_SNOOZE -> {
                val habit = repo.habit(id) ?: return
                Notifications.cancel(context, Notifications.habitId(id))
                alarms.schedule(habit, now.plusHours(1))
            }
            FOCUS_DONE -> container.completeFocus()
            FOCUS_CLEAR -> container.setFocus(null)
        }
    }

    companion object {
        const val MORNING = "app.roz.MORNING"
        const val EVENING = "app.roz.EVENING"
        const val REMINDER = "app.roz.REMINDER"
        const val REMINDER_DONE = "app.roz.REMINDER_DONE"
        const val REMINDER_HOUR = "app.roz.REMINDER_HOUR"
        const val REMINDER_TOMORROW = "app.roz.REMINDER_TOMORROW"
        const val HABIT = "app.roz.HABIT"
        const val HABIT_DONE = "app.roz.HABIT_DONE"
        const val HABIT_SNOOZE = "app.roz.HABIT_SNOOZE"
        const val FOCUS_DONE = "app.roz.FOCUS_DONE"
        const val FOCUS_CLEAR = "app.roz.FOCUS_CLEAR"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** The id goes in the data URI so each reminder and habit gets its own alarm. */
        fun intent(context: Context, action: String, id: Long): Intent =
            Intent(context, AlarmReceiver::class.java).setAction(action).setData(Uri.parse("roz://item/$id"))
    }
}

/** Puts every alarm back after a reboot, an app update or a clock change. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as RozApplication).container
                container.alarms.rescheduleAll()
                container.refreshFocus()
            } finally {
                pending.finish()
            }
        }
    }
}
