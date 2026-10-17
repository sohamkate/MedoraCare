
package com.soham.medicinereminderapp.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.soham.medicinereminderapp.MainActivity
import com.soham.medicinereminderapp.R
import com.soham.medicinereminderapp.RefillMedicineActivity
import com.soham.medicinereminderapp.data.AppDatabase
import java.util.Locale

class LowStockWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val dao = AppDatabase
                .getDatabase(applicationContext)
                .medicineDao()

            val medicines = dao.getMedicinesWithTimesOnce()

            medicines.forEach { item ->
                val medicine = item.medicine
                val stock = medicine.currentStock
                val unitsPerDose = medicine.unitsPerDose

                // Stock tracking is optional.
                if (
                    stock == null ||
                    unitsPerDose == null ||
                    unitsPerDose <= 0.0
                ) {
                    if (medicine.lowStockAlertActive) {
                        dao.updateMedicine(
                            medicine.copy(lowStockAlertActive = false)
                        )
                    }
                    return@forEach
                }

                val enabledTimes = item.reminderTimes
                    .filter { it.isEnabled }

                val selectedDays = medicine.weekdays
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 1..7 }
                    .distinct()

                if (enabledTimes.isEmpty() || selectedDays.isEmpty()) {
                    return@forEach
                }

                // Estimate average consumption per calendar day.
                val dosesPerDay =
                    enabledTimes.size.toDouble() * selectedDays.size / 7.0

                val unitsPerDay = dosesPerDay * unitsPerDose

                if (unitsPerDay <= 0.0) {
                    return@forEach
                }

                val daysRemaining = stock / unitsPerDay

                val isLowStock =
                    stock <= 0.0 ||
                            daysRemaining <= medicine.lowStockDays

                if (isLowStock && !medicine.lowStockAlertActive) {
                    // Only activate the alert state if notification posting
                    // succeeds. Otherwise, a later worker run can retry.
                    val notificationPosted = showLowStockNotification(
                        medicineId = medicine.id,
                        medicineName = medicine.name,
                        stock = stock,
                        unit = medicine.stockUnit,
                        daysRemaining = daysRemaining
                    )

                    if (notificationPosted) {
                        dao.updateMedicine(
                            medicine.copy(lowStockAlertActive = true)
                        )
                    }
                } else if (!isLowStock && medicine.lowStockAlertActive) {
                    // Reset after stock returns above the low-stock threshold.
                    dao.updateMedicine(
                        medicine.copy(lowStockAlertActive = false)
                    )
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun showLowStockNotification(
        medicineId: Int,
        medicineName: String,
        stock: Double,
        unit: String,
        daysRemaining: Double
    ): Boolean {
        val context = applicationContext
        val channelId = "low_stock_channel"

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val manager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Medicine Stock Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description =
                        "Alerts when medicine stock is running low"
                }
            )

            // The user may have disabled this notification channel.
            val channel = manager.getNotificationChannel(channelId)
            if (
                channel == null ||
                channel.importance == NotificationManager.IMPORTANCE_NONE
            ) {
                return false
            }
        }

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }

        val openAppIntent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            910000 + medicineId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        // Open the refill screen for this specific medicine.
        val refillIntent = Intent(
            context,
            RefillMedicineActivity::class.java
        ).apply {
            putExtra("medicineId", medicineId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val refillPendingIntent = PendingIntent.getActivity(
            context,
            920000 + medicineId,
            refillIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val stockText = if (stock % 1.0 == 0.0) {
            stock.toLong().toString()
        } else {
            String.format(Locale.getDefault(), "%.2f", stock)
        }

        val message = if (stock <= 0.0) {
            "$medicineName is out of recorded stock. Check the physical quantity."
        } else {
            "$medicineName: $stockText $unit remaining, approximately " +
                    "${String.format(Locale.getDefault(), "%.1f", daysRemaining)} " +
                    "days of supply. Consider arranging a refill."
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Low medicine stock")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(
                R.drawable.ic_notification,
                "Refill",
                refillPendingIntent
            )
            .build()

        return try {
            NotificationManagerCompat.from(context)
                .notify(910000 + medicineId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
