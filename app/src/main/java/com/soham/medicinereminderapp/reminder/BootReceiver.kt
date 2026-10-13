
package com.soham.medicinereminderapp.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.soham.medicinereminderapp.data.AppDatabase
import com.soham.medicinereminderapp.repository.MedicineRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao =
                    AppDatabase.getDatabase(appContext).medicineDao()

                val repository = MedicineRepository(dao)
                val medicines = repository.getMedicinesWithTimesOnce()

                for (item in medicines) {
                    val medicine = item.medicine

                    for (time in item.reminderTimes) {
                        if (time.isEnabled) {
                            ReminderScheduler.scheduleReminder(
                                context = appContext,
                                medicineTime = time,
                                medicineName = medicine.name,
                                dosage = medicine.dosage,
                                weekdays = medicine.weekdays
                            )
                        } else {
                            ReminderScheduler.cancelReminder(
                                appContext,
                                time.id
                            )
                        }
                    }
                }
            } catch (exception: Exception) {
                Log.e(
                    "BootReceiver",
                    "Failed to restore medicine reminders",
                    exception
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
