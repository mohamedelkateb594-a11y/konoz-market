package com.konoz.market.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.konoz.market.data.KonozDatabase
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/** Turns the daily local backup on or off. WorkManager keeps the schedule across restarts and reboots. */
object BackupScheduler {
    private const val WORK_NAME = "konoz_auto_backup"

    fun setEnabled(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                .build()
            manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            manager.cancelUniqueWork(WORK_NAME)
        }
    }
}

/** Writes one scheduled backup into the app's private `backups` folder (the newest 7 are kept). */
class BackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = try {
        val db = KonozDatabase.get(applicationContext)
        runBlocking { BackupService(applicationContext, db).createAutoBackup() }
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
