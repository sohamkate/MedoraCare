package com.soham.medicinereminderapp.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.soham.medicinereminderapp.data.AppDatabase

class AdherenceWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val cutoff = System.currentTimeMillis() -
                    TWO_HOURS_MILLIS

            AppDatabase.getDatabase(applicationContext)
                .medicineDao()
                .markPendingDosesMissedBefore(cutoff)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val TWO_HOURS_MILLIS =
            2 * 60 * 60 * 1000L
    }
}