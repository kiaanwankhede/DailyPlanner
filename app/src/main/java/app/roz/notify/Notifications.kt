package app.roz.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.roz.MainActivity
import app.roz.R
import app.roz.core.FocusView
import app.roz.core.Habit
import app.roz.core.Occasions
import app.roz.core.Reminder
import app.roz.core.Reminders
import app.roz.core.Summaries
import app.roz.core.Upcoming

/** Every notification Roz posts (SPEC.md §9). */
object Notifications {
    private const val SUMMARY = "summary"
    private const val REMINDERS = "reminders"
    private const val BIRTHDAYS = "birthdays"
    private const val HABITS = "habits"
    private const val FOCUS = "focus"

    const val ID_MORNING = 1
    const val ID_EVENING = 2
    const val ID_PASSPHRASE = 3
    const val ID_FOCUS = 4
    fun reminderId(id: Long) = 200_000 + id.toInt()
    fun habitId(id: Long) = 300_000 + id.toInt()
    private fun birthdayId(id: Long) = 100_000 + id.toInt()

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(SUMMARY, "Morning summary and evening nudge", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(BIRTHDAYS, "Birthdays and anniversaries", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(HABITS, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT),
                // Quiet and always on the lock screen: it is a pinned reminder, not an alert.
                NotificationChannel(FOCUS, "Top priority", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Your one top-priority item, pinned to the lock screen"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setShowBadge(false)
                },
            ),
        )
    }

    fun summary(context: Context, note: Summaries.Note, evening: Boolean) =
        post(context, if (evening) ID_EVENING else ID_MORNING, base(context, SUMMARY, note.title, note.body))

    fun birthday(context: Context, upcoming: Upcoming) =
        post(context, birthdayId(upcoming.person.id), base(context, BIRTHDAYS, Occasions.alertText(upcoming), Occasions.detail(upcoming)))

    fun passphraseCheck(context: Context) = post(
        context, ID_PASSPHRASE,
        base(context, SUMMARY, "Do you still know your backup passphrase?", "Open Roz to check it — it takes a few seconds."),
    )

    fun reminder(context: Context, reminder: Reminder) {
        val builder = base(context, REMINDERS, reminder.title, Reminders.timeLabel(reminder.minute))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, "Done", action(context, AlarmReceiver.REMINDER_DONE, reminder.id))
            .addAction(0, "In 1 hour", action(context, AlarmReceiver.REMINDER_HOUR, reminder.id))
            .addAction(0, "Tomorrow", action(context, AlarmReceiver.REMINDER_TOMORROW, reminder.id))
        post(context, reminderId(reminder.id), builder)
    }

    fun habit(context: Context, habit: Habit) {
        val builder = base(context, HABITS, habit.name, "Tap Done when you've done it today")
            .addAction(0, "Done", action(context, AlarmReceiver.HABIT_DONE, habit.id))
            .addAction(0, "In 1 hour", action(context, AlarmReceiver.HABIT_SNOOZE, habit.id))
        post(context, habitId(habit.id), builder)
    }

    /**
     * The pinned top-priority card. With [hideDetails] only "Top priority" shows while the phone is
     * locked; the item itself appears once it is unlocked.
     */
    fun focus(context: Context, view: FocusView, hideDetails: Boolean) {
        val builder = base(context, FOCUS, view.title, "TOP PRIORITY · ${view.detail}")
            .setSubText(view.kindLabel)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (view.canComplete) builder.addAction(0, view.doneLabel, action(context, AlarmReceiver.FOCUS_DONE, 0))
        builder.addAction(0, "Remove", action(context, AlarmReceiver.FOCUS_CLEAR, 0))
        if (hideDetails) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            builder.setPublicVersion(
                base(context, FOCUS, "Top priority", "Unlock to see it")
                    .setOngoing(true).setAutoCancel(false).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).build(),
            )
        }
        post(context, ID_FOCUS, builder)
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    private fun base(context: Context, channel: String, title: String, text: String) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_roz)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )

    private fun action(context: Context, action: String, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, action.hashCode() xor id.toInt(),
            AlarmReceiver.intent(context, action, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    @SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(context).notify(id, builder.build())
    }
}
