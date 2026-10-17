
package com.soham.medicinereminderapp.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.soham.medicinereminderapp.data.AppDatabase
import com.soham.medicinereminderapp.data.MedicineTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ReminderScheduler {

    private const val TAG = "ReminderScheduler"

    private const val ACTION_MEDICINE_REMINDER =
        "com.soham.medicinereminderapp.MEDICINE_REMINDER"

    const val ACTION_SNOOZED_REMINDER =
        "com.soham.medicinereminderapp.SNOOZED_REMINDER"

    private const val SNOOZE_REQUEST_CODE_OFFSET = 100000

    suspend fun scheduleReminder(
        context: Context,
        medicineTime: MedicineTime,
        medicineName: String,
        dosage: String
    ) = withContext(Dispatchers.IO) {

        val appContext = context.applicationContext
        val dao = AppDatabase.getDatabase(appContext).medicineDao()

        if (!medicineTime.isEnabled) {
            cancelReminder(appContext, medicineTime.id)
            cancelSnooze(appContext, medicineTime.id)
            return@withContext
        }

        val medicine = dao.getMedicineById(medicineTime.medicineId)
            ?: return@withContext

        val selectedDates = if (
            medicine.scheduleType == MedicineSchedule.SELECTED_DATES
        ) {
            dao.getScheduledDateStrings(medicine.id).toSet()
        } else {
            emptySet()
        }

        val triggerAt = MedicineSchedule.nextOccurrence(
            hour = medicineTime.hour,
            minute = medicineTime.minute,
            scheduleType = medicine.scheduleType,
            startDate = medicine.startDate,
            endDate = medicine.endDate,
            selectedDates = selectedDates
        )

        if (triggerAt == null) {
            cancelReminder(appContext, medicineTime.id)
            Log.d(TAG, "No future eligible date for ${medicine.name}")
            return@withContext
        }

        val intent = Intent(
            appContext,
            ReminderReceiver::class.java
        ).apply {
            action = ACTION_MEDICINE_REMINDER

            putExtra("medicineTimeId", medicineTime.id)
            putExtra("medicineId", medicine.id)
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

        scheduleAlarm(appContext, triggerAt, pendingIntent)

        Log.d(
            TAG,
            "Scheduled ${medicine.name} for $triggerAt"
        )
    }

    fun scheduleSnooze(
        context: Context,
        medicineTimeId: Int,
        medicineId: Int,
        medicineName: String,
        dosage: String,
        scheduledTime: Long? = null
    ) {
        val appContext = context.applicationContext
        val triggerAt = System.currentTimeMillis() + 5 * 60 * 1000L

        val intent = Intent(
            appContext,
            ReminderReceiver::class.java
        ).apply {
            action = ACTION_SNOOZED_REMINDER

            putExtra("medicineTimeId", medicineTimeId)
            putExtra("medicineId", medicineId)
            putExtra("medicineName", medicineName)
            putExtra("dosage", dosage)
            putExtra(
                "scheduledTime",
                scheduledTime ?: triggerAt
            )
        }

        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            medicineTimeId + SNOOZE_REQUEST_CODE_OFFSET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        scheduleAlarm(appContext, triggerAt, pendingIntent)
    }

    fun cancelReminder(
        context: Context,
        medicineTimeId: Int
    ) {
        val appContext = context.applicationContext

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
            PendingIntent.FLAG_NO_CREATE or
                    PendingIntent.FLAG_IMMUTABLE
        )

        pendingIntent?.let {
            val alarmManager = appContext.getSystemService(
                Context.ALARM_SERVICE
            ) as AlarmManager

            alarmManager.cancel(it)
            it.cancel()
        }
    }

    fun cancelSnooze(
        context: Context,
        medicineTimeId: Int
    ) {
        val appContext = context.applicationContext

        val intent = Intent(
            appContext,
            ReminderReceiver::class.java
        ).apply {
            action = ACTION_SNOOZED_REMINDER
        }

        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            medicineTimeId + SNOOZE_REQUEST_CODE_OFFSET,
            intent,
            PendingIntent.FLAG_NO_CREATE or
                    PendingIntent.FLAG_IMMUTABLE
        )

        pendingIntent?.let {
            val alarmManager = appContext.getSystemService(
                Context.ALARM_SERVICE
            ) as AlarmManager

            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun scheduleAlarm(
        context: Context,
        triggerAt: Long,
        pendingIntent: PendingIntent
    ) {
        val alarmManager = context.getSystemService(
            Context.ALARM_SERVICE
        ) as AlarmManager

        try {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm scheduling failed", e)

            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } catch (fallbackException: Exception) {
                Log.e(
                    TAG,
                    "Fallback alarm scheduling failed",
                    fallbackException
                )
            }
        }
    }
}
