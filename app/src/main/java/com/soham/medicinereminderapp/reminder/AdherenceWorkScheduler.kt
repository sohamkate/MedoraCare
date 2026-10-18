package com.soham.medicinereminderapp.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AdherenceWorkScheduler {

    private const val WORK_NAME =
        "medora_adherence_missed_dose_check"

    fun schedule(context: Context) {
        val request =
            PeriodicWorkRequestBuilder<AdherenceWorker>(
                15, TimeUnit.MINUTES
            ).build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }
}