package app.roz

import android.app.Application
import app.roz.backup.BackupManager
import app.roz.backup.BackupWorker
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
        }
    }
}

class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val prefs = Prefs(app)
    val secrets = SecretStore(app)
    val repository = Repository(RozDatabase.create(app).dao())
    val backup = BackupManager(app, repository, prefs, secrets)
    val alarms = Alarms(app, repository, prefs)

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
