
package com.soham.medicinereminderapp.data

import androidx.room.Embedded
import androidx.room.Relation

data class MedicineWithTimes(

    @Embedded
    val medicine: Medicine,

    @Relation(
        parentColumn = "id",
        entityColumn = "medicineId"
    )
    val reminderTimes: List<MedicineTime>
)
