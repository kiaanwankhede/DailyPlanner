package app.roz.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.roz.RozApplication
import java.util.concurrent.TimeUnit

/** Nightly backup while charging on Wi-Fi, and a quick one a few minutes after big changes. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as RozApplication).container
        if (container.prefs.state.value.backupUri == null || !container.secrets.isSetUp()) return Result.success()
        return if (container.backup.backUpNow().isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val NIGHTLY = "nightly-backup"
        private const val SOON = "backup-after-change"

        fun scheduleNightly(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresCharging(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NIGHTLY, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** After deletes, restores and other big changes: back up in a few minutes (repeated calls wait again). */
        fun scheduleSoon(context: Context) {
            val request = OneTimeWorkRequestBuilder<BackupWorker>()
                .setInitialDelay(3, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(SOON, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
