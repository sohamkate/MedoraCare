
package com.soham.medicinereminderapp.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat

class SnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medicineTimeId =
            intent.getIntExtra("medicineTimeId", -1)

        val medicineId =
            intent.getIntExtra("medicineId", -1)

        val medicineName =
            intent.getStringExtra("medicineName") ?: "Your medicine"

        val dosage =
            intent.getStringExtra("dosage") ?: ""

        val scheduledTime =
            intent.getLongExtra("scheduledTime", 0L)

        if (medicineTimeId < 1 || medicineId < 1) {
            Log.e(
                "SnoozeReceiver",
                "Invalid reminder information"
            )
            return
        }

        // Dismiss the currently visible notification.
        NotificationManagerCompat.from(context)
            .cancel(medicineTimeId)

        // Schedule the reminder once, five minutes from now.
        ReminderScheduler.scheduleSnooze(
            context = context,
            medicineTimeId = medicineTimeId,
            medicineId = medicineId,
            medicineName = medicineName,
            dosage = dosage,
            scheduledTime = scheduledTime.takeIf { it > 0L }
        )

        Log.d(
            "SnoozeReceiver",
            "$medicineName snoozed for five minutes"
        )
    }
}
