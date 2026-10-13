
package com.soham.medicinereminderapp.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dose_logs",
    foreignKeys = [
        ForeignKey(
            entity = Medicine::class,
            parentColumns = ["id"],
            childColumns = ["medicineId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["medicineId"]),
        Index(value = ["reminderKey"], unique = true)
    ]
)
data class DoseLog(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val medicineId: Int?,

    val medicineName: String,

    val dosage: String,

    // Scheduled date/time in milliseconds
    val scheduledTime: Long,

    // PENDING, TAKEN, NOT_TAKEN or MISSED
    val status: String = "PENDING",

    // Actual time marked as taken
    val takenTime: Long? = null,

    // Reminder that generated this dose
    val reminderTimeId: Int? = null,

    // Unique key for this scheduled occurrence
    val reminderKey: String? = null
)
