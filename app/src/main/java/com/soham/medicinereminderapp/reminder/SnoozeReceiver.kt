
package com.soham.medicinereminderapp.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat

class SnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val timeId = intent.getIntExtra("medicineTimeId", -1)
        val medicineId = intent.getIntExtra("medicineId", -1)
        val medicineName =
            intent.getStringExtra("medicineName") ?: "Your medicine"
        val dosage = intent.getStringExtra("dosage") ?: ""

        if (timeId < 1 || medicineId < 1) {
            Log.e("SnoozeReceiver", "Invalid reminder information")
            return
        }

        // Dismiss the currently visible reminder.
        NotificationManagerCompat.from(context).cancel(timeId)

        ReminderScheduler.scheduleSnooze(
            context = context,
            timeId = timeId,
            medicineId = medicineId,
            medicineName = medicineName,
            dosage = dosage
        )

        Log.d(
            "SnoozeReceiver",
            "$medicineName snoozed for five minutes"
        )
        val scheduledTime = intent.getLongExtra("scheduledTime", 0L)

        ReminderScheduler.scheduleSnooze(
            context = context,
            timeId = timeId,
            medicineId = medicineId,
            medicineName = medicineName,
            dosage = dosage,
            scheduledTime = scheduledTime
        )
    }
}
