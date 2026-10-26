package app.roz

import android.app.Application
import app.roz.backup.BackupManager
import app.roz.backup.BackupWorker
import app.roz.core.Focus
import app.roz.core.FocusPick
import app.roz.core.FocusType
import app.roz.core.FocusView
import app.roz.core.HabitMath
import app.roz.core.HabitToday
import app.roz.core.TodayPlan
import app.roz.core.TodayPlanner
import app.roz.data.Prefs
import app.roz.data.Repository
import app.roz.data.RozDatabase
import app.roz.data.SecretStore
import app.roz.notify.Alarms
import app.roz.notify.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class RozApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        BackupWorker.scheduleNightly(this)
        container.scope.launch {
            container.repository.purgeOlderThan(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30))
            container.alarms.rescheduleAll()
            container.refreshFocus()
        }
    }
}

class AppContainer(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val prefs = Prefs(app)
    val secrets = SecretStore(app)
    val repository = Repository(RozDatabase.create(app).dao())
    val backup = BackupManager(app, repository, prefs, secrets)
    val alarms = Alarms(app, repository, prefs)

    // ---- top priority (SPEC.md §11) ----

    suspend fun focusView(today: LocalDate): FocusView? {
        val pick = prefs.settings.focus ?: return null
        val habits = todayPlan(today).habits
        return Focus.resolve(pick, today, habits, repository.choresNow(), repository.remindersNow().map { it.reminder }, repository.peopleNow())
    }

    /** Re-posts (or removes) the lock-screen card so it matches the item as it is right now. */
    suspend fun refreshFocus() {
        val view = focusView(LocalDate.now())
        if (view == null) {
            if (prefs.settings.focus != null) prefs.setFocus(null)
            Notifications.cancel(app, Notifications.ID_FOCUS)
        } else {
            Notifications.focus(app, view, prefs.settings.hideFocusOnLock)
        }
    }

    suspend fun setFocus(pick: FocusPick?) {
        prefs.setFocus(pick)
        refreshFocus()
    }

    suspend fun clearFocusIf(type: FocusType, id: Long) {
        val current = prefs.settings.focus
        if (current != null && current.type == type && current.id == id) setFocus(null) else refreshFocus()
    }

    /** The Done button on the lock-screen card. */
    suspend fun completeFocus() {
        val pick = prefs.settings.focus ?: return
        val today = LocalDate.now()
        when (pick.type) {
            FocusType.HABIT -> repository.habit(pick.id)?.let { habit ->
                val done = todayPlan(today).habits.firstOrNull { it.habit.id == habit.id }?.stats?.doneToday == true
                if (!done) repository.tickHabit(habit, today)
            }
            FocusType.CHORE -> repository.choresNow().firstOrNull { it.id == pick.id }?.let { repository.completeChore(it, today) }
            FocusType.REMINDER -> repository.remindersNow().firstOrNull { it.reminder.id == pick.id }?.let {
                repository.completeReminder(it, LocalDateTime.now())
                repository.reminder(pick.id)?.let { r -> alarms.schedule(r) }
            }
            FocusType.PERSON -> Unit
        }
        // Finishing a chore or reminder ends its time on top. A counting habit stays until it hits its target.
        if (pick.type == FocusType.CHORE || pick.type == FocusType.REMINDER) prefs.setFocus(null)
        refreshFocus()
    }

    /** The Today plan straight from the database, for notifications. */
    suspend fun todayPlan(today: LocalDate): TodayPlan {
        val days = repository.habitDaysNow()
        val habits = repository.habitsNow().map { HabitToday(it, HabitMath.stats(it, days[it.id].orEmpty(), today)) }
        val reminders = repository.remindersNow()
        return TodayPlanner.plan(
            today, habits, repository.choresNow(), reminders.map { it.reminder }, repository.peopleNow(),
            choresDoneToday = repository.choresDoneOn(today),
            remindersDoneToday = reminders.count { it.lastDoneOn == today.toString() },
        )
    }
}
