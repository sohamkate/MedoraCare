
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

    // Legacy field retained to preserve existing data.
    val weekdays: String = "1,2,3,4,5,6,7",

    // NEW: DATE_RANGE or SELECTED_DATES
    val scheduleType: String = "DATE_RANGE",

    // NEW: Inclusive date range, stored as yyyy-MM-dd.
    val startDate: String? = null,
    val endDate: String? = null,

    // Stock tracking fields
    val currentStock: Double? = null,
    val unitsPerDose: Double? = null,
    val stockUnit: String = "tablets",
    val lowStockDays: Int = 2,
    val lowStockAlertActive: Boolean = false
)
