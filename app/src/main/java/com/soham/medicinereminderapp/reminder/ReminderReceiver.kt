
package com.soham.medicinereminderapp.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.soham.medicinereminderapp.MainActivity
import com.soham.medicinereminderapp.R
import com.soham.medicinereminderapp.data.AppDatabase
import com.soham.medicinereminderapp.data.DoseLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderReceiver"
        private const val CHANNEL_ID = "medicine_reminder_channel"
        private const val CHANNEL_NAME = "Medicine Reminders"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val timeId = intent.getIntExtra("medicineTimeId", -1)
        val medicineId = intent.getIntExtra("medicineId", -1)

        val isSnoozed =
            intent.action == ReminderScheduler.ACTION_SNOOZED_REMINDER

        if (timeId < 1 || medicineId < 1) {
            Log.e(TAG, "Invalid reminder identifiers")
            return
        }

        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao =
                    AppDatabase.getDatabase(appContext).medicineDao()

                // Confirm that the reminder still exists and is enabled.
                val savedTime = dao.getMedicineTimeById(timeId)
                    ?: return@launch

                if (!savedTime.isEnabled ||
                    savedTime.medicineId != medicineId
                ) {
                    Log.d(TAG, "Reminder is disabled or no longer valid")
                    return@launch
                }

                val medicine = dao.getMedicineById(medicineId)
                    ?: return@launch

                val scheduledTime = intent.getLongExtra(
                    "scheduledTime",
                    calculateScheduledTime(
                        savedTime.hour,
                        savedTime.minute
                    )
                )

                val reminderKey = "${timeId}_$scheduledTime"

                // A normal reminder creates a pending dose record.
                // Snoozing must not create a duplicate dose record.
                if (!isSnoozed) {
                    dao.insertDoseLogIfAbsent(
                        DoseLog(
                            medicineId = medicine.id,
                            medicineName = medicine.name,
                            dosage = medicine.dosage,
                            scheduledTime = scheduledTime,
                            status = "PENDING",
                            takenTime = null,
                            reminderTimeId = timeId,
                            reminderKey = reminderKey
                        )
                    )
                }

                showNotification(
                    context = appContext,
                    timeId = timeId,
                    medicineId = medicineId,
                    medicineName = medicine.name,
                    dosage = medicine.dosage,
                    scheduledTime = scheduledTime,
                    isSnoozed = isSnoozed
                )

                // Re-arm the next regular reminder.
                // The scheduler must determine eligible dates from
                // the medicine's saved calendar schedule.
                if (!isSnoozed) {
                    ReminderScheduler.scheduleReminder(
                        appContext,
                        savedTime,
                        medicine.name,
                        medicine.dosage
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process reminder", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun calculateScheduledTime(
        hour: Int,
        minute: Int
    ): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun showNotification(
        context: Context,
        timeId: Int,
        medicineId: Int,
        medicineName: String,
        dosage: String,
        scheduledTime: Long,
        isSnoozed: Boolean
    ) {
        createNotificationChannel(context)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Notification permission not granted")
            return
        }

        val openAppIntent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP

            putExtra("medicineId", medicineId)
            putExtra("medicineTimeId", timeId)
            putExtra("scheduledTime", scheduledTime)
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            timeId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(
            context,
            SnoozeReceiver::class.java
        ).apply {
            putExtra("medicineTimeId", timeId)
            putExtra("medicineId", medicineId)
            putExtra("medicineName", medicineName)
            putExtra("dosage", dosage)
            putExtra("scheduledTime", scheduledTime)
        }

        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            200000 + timeId,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val message = if (dosage.isBlank()) {
            "It's time to take $medicineName."
        } else {
            "It's time to take $medicineName ($dosage)."
        }

        val title = if (isSnoozed) {
            "Medicine Reminder — Snoozed"
        } else {
            "Medicine Reminder"
        }

        val notification = NotificationCompat.Builder(
            context,
            CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(message)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(
                0,
                "Snooze reminder",
                snoozePendingIntent
            )
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(timeId, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission denied", e)
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Scheduled medicine reminders"
            }

            val manager = context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

            manager.createNotificationChannel(channel)
        }
    }
}
