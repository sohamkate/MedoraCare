
package com.soham.medicinereminderapp.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medicine_times",
    foreignKeys = [
        ForeignKey(
            entity = Medicine::class,
            parentColumns = ["id"],
            childColumns = ["medicineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["medicineId"])
    ]
)
data class MedicineTime(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val medicineId: Int,

    val hour: Int,

    val minute: Int,

    val isEnabled: Boolean = true
)
