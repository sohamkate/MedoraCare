package com.soham.medicinereminderapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicines")
data class Medicine(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val name: String,
    val dosage: String,
    val imagePath: String? = null,
    val notes: String? = null,

    // Calendar weekday numbers: Sunday = 1, Saturday = 7.
    // Default: every day.
    val weekdays: String = "1,2,3,4,5,6,7"
)