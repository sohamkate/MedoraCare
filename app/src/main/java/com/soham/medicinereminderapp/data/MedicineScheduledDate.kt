package com.soham.medicinereminderapp.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "medicine_scheduled_dates",
    primaryKeys = ["medicineId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = Medicine::class,
            parentColumns = ["id"],
            childColumns = ["medicineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["medicineId"])]
)
data class MedicineScheduledDate(
    val medicineId: Int,
    val date: String
)