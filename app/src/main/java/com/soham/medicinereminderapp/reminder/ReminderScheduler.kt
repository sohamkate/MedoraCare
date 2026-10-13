
package com.soham.medicinereminderapp.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.soham.medicinereminderapp.data.MedicineTime
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ReminderScheduler {

    private const val TAG = "ReminderScheduler"

    private const val ACTION_MEDICINE_REMINDER =
        "com.soham.medicinereminderapp.MEDICINE_REMINDER"

    const val ACTION_SNOOZED_REMINDER =
        "com.soham.medicinereminderapp.SNOOZED_REMINDER"

    private const val SNOOZE_REQUEST_CODE_OFFSET = 100000

    private const val ALL_DAYS = "1,2,3,4,5,6,7"

    fun scheduleReminder(
        context: Context,
        medicineTime: MedicineTime,
        medicineName: String,
        dosage: String,
        weekdays: String = ALL_DAYS
    ) {
        if (!medicineTime.isEnabled) {
            Log.d(TAG, "Reminder ${medicineTime.id} is disabled")
            cancelReminder(context, medicineTime.id)
            return
        }

        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(
            Context.ALARM_SERVICE
        ) as AlarmManager

        val selectedDays = weekdays.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in Calendar.SUNDAY..Calendar.SATURDAY }
            .toSet()

        if (selectedDays.isEmpty()) {
            Log.e(TAG, "No valid weekdays for ${medicineTime.id}")
            cancelReminder(appContext, medicineTime.id)
            return
        }

        val triggerAt = nextOccurrence(
            medicineTime.hour,
            medicineTime.minute,
            selectedDays
        )

        val intent = Intent(
            appContext,
            ReminderReceiver::class.java
        ).apply {
            action = ACTION_MEDICINE_REMINDER
            putExtra("medicineTimeId", medicineTime.id)
            putExtra("medicineId", medicineTime.medicineId)
            putExtra("medicineName", medicineName)
            putExtra("dosage", dosage)
            putExtra("hour", medicineTime.hour)
            putExtra("minute", medicineTime.minute)
            putExtra("scheduledTime", triggerAt)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            medicineTime.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        Log.d(
            TAG,
            "Reminder ${medicineTime.id} scheduled for ${
                SimpleDateFormat(
                    "EEE yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()
                ).format(Date(triggerAt))
            }"
        )

        scheduleAlarm(alarmManager, triggerAt, pendingIntent)
    }

    private fun nextOccurrence(
        hour: Int,
        minute: Int,
        selectedDays: Set<Int>
    ): Long {
        val now = Calendar.getInstance()

        for (dayOffset in 0..7) {
            val candidate = now.clone() as Calendar

            candidate.add(Calendar.DAY_OF_YEAR, dayOffset)
            candidate.set(Calendar.HOUR_OF_DAY, hour)
            candidate.set(Calendar.MINUTE, minute)
            candidate.set(Calendar.SECOND, 0)
            candidate.set(Calendar.MILLISECOND, 0)

            val isSelectedDay =
                candidate.get(Calendar.DAY_OF_WEEK) in selectedDays

            if (isSelectedDay &&
                candidate.timeInMillis > now.timeInMillis
            ) {
                return candidate.timeInMillis
            }
        }

        // A valid non-empty weekday set should always return above.
        throw IllegalStateException("Unable to calculate next reminder")
    }

    fun scheduleSnooze(
        context: Context,
        timeId: Int,
        medicineId: Int,
        medicineName: String,
        dosage: String,
        scheduledTime: Long = 0L
    ) {
        if (timeId < 1 || medicineId < 1) return

        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(
            Context.ALARM_SERVICE
        ) as AlarmManager

        val intent = Intent(
            appContext,
            ReminderReceiver::class.java
        ).apply {
            action = ACTION_SNOOZED_REMINDER
            putExtra("medicineTimeId", timeId)
            putExtra("medicineId", medicineId)
            putExtra("medicineName", medicineName)
            putExtra("dosage", dosage)

            if (scheduledTime > 0L) {
                putExtra("scheduledTime", scheduledTime)
            }
        }

        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            SNOOZE_REQUEST_CODE_OFFSET + timeId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = System.currentTimeMillis() + 5 * 60 * 1000L

        scheduleAlarm(alarmManager, triggerAt, pendingIntent)

        Log.d(TAG, "Snooze scheduled for $medicineName")
    }

    private fun scheduleAlarm(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm permission unavailable", e)

            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun cancelReminder(
        context: Context,
        medicineTimeId: Int
    ) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(
            Context.ALARM_SERVICE
        ) as AlarmManager

        val intent = Intent(
            appContext,
            ReminderReceiver::class.java
        ).apply {
            action = ACTION_MEDICINE_REMINDER
        }

        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            medicineTimeId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun cancelSnooze(
        context: Context,
        medicineTimeId: Int
    ) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(
            Context.ALARM_SERVICE
        ) as AlarmManager

        val intent = Intent(
            appContext,
            ReminderReceiver::class.java
        ).apply {
            action = ACTION_SNOOZED_REMINDER
        }

        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            SNOOZE_REQUEST_CODE_OFFSET + medicineTimeId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
