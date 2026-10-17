package com.soham.medicinereminderapp.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.soham.medicinereminderapp.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(appContext).medicineDao()
                val medicines = dao.getMedicinesWithTimesOnce()

                medicines.forEach { item ->
                    item.reminderTimes.forEach { time ->
                        if (time.isEnabled) {
                            ReminderScheduler.scheduleReminder(
                                appContext,
                                time,
                                item.medicine.name,
                                item.medicine.dosage
                            )
                        } else {
                            ReminderScheduler.cancelReminder(
                                appContext,
                                time.id
                            )
                            ReminderScheduler.cancelSnooze(
                                appContext,
                                time.id
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to restore reminders", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}