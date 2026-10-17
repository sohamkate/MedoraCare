
package com.soham.medicinereminderapp.repository

import com.soham.medicinereminderapp.data.DoseLog
import com.soham.medicinereminderapp.data.Medicine
import com.soham.medicinereminderapp.data.MedicineDao
import com.soham.medicinereminderapp.data.MedicineScheduledDate
import com.soham.medicinereminderapp.data.MedicineTime
import com.soham.medicinereminderapp.data.MedicineWithTimes
import kotlinx.coroutines.flow.Flow

class MedicineRepository(private val dao: MedicineDao) {

    // Medicines
    suspend fun insertMedicine(medicine: Medicine) =
        dao.insertMedicine(medicine)

    suspend fun updateMedicine(medicine: Medicine) =
        dao.updateMedicine(medicine)

    suspend fun deleteMedicine(medicine: Medicine) =
        dao.deleteMedicine(medicine)

    fun getAllMedicines(): Flow<List<Medicine>> =
        dao.getAllMedicines()

    suspend fun getMedicineById(medicineId: Int): Medicine? =
        dao.getMedicineById(medicineId)

    fun searchMedicines(query: String): Flow<List<Medicine>> =
        dao.searchMedicines(query)

    // Medicines with reminder times
    fun getMedicinesWithTimes(): Flow<List<MedicineWithTimes>> =
        dao.getMedicinesWithTimes()

    suspend fun getMedicineWithTimes(
        medicineId: Int
    ): MedicineWithTimes? =
        dao.getMedicineWithTimes(medicineId)

    suspend fun getMedicinesWithTimesOnce(): List<MedicineWithTimes> =
        dao.getMedicinesWithTimesOnce()

    // Reminder times
    suspend fun insertMedicineTime(time: MedicineTime) =
        dao.insertMedicineTime(time)

    suspend fun updateMedicineTime(time: MedicineTime) =
        dao.updateMedicineTime(time)

    suspend fun deleteMedicineTime(time: MedicineTime) =
        dao.deleteMedicineTime(time)

    fun getTimesForMedicine(
        medicineId: Int
    ): Flow<List<MedicineTime>> =
        dao.getTimesForMedicine(medicineId)

    suspend fun getMedicineTimeById(timeId: Int): MedicineTime? =
        dao.getMedicineTimeById(timeId)

    suspend fun getAllEnabledMedicineTimes(): List<MedicineTime> =
        dao.getAllEnabledMedicineTimes()

    suspend fun setMedicineTimeEnabled(
        timeId: Int,
        enabled: Boolean
    ) = dao.setMedicineTimeEnabled(timeId, enabled)

    // Dose history
    suspend fun insertDoseLog(doseLog: DoseLog): Long =
        dao.insertDoseLog(doseLog)

    suspend fun insertDoseLogIfAbsent(doseLog: DoseLog): Long =
        dao.insertDoseLogIfAbsent(doseLog)

    suspend fun getDoseLogByReminderKey(
        reminderKey: String
    ): DoseLog? =
        dao.getDoseLogByReminderKey(reminderKey)

    suspend fun updateDoseLog(doseLog: DoseLog) =
        dao.updateDoseLog(doseLog)

    fun getAllDoseLogs(): Flow<List<DoseLog>> =
        dao.getAllDoseLogs()

    fun getDoseLogsForMedicine(
        medicineId: Int
    ): Flow<List<DoseLog>> =
        dao.getDoseLogsForMedicine(medicineId)

    suspend fun getDoseLogById(logId: Int): DoseLog? =
        dao.getDoseLogById(logId)

    suspend fun markDoseAsTaken(
        logId: Int,
        takenTime: Long = System.currentTimeMillis()
    ): Boolean =
        dao.markDoseAsTakenAndDeductStock(logId, takenTime)
    suspend fun markDoseAsNotTaken(logId: Int) =
        dao.markDoseAsNotTaken(logId)

    suspend fun markDoseAsMissed(logId: Int) =
        dao.markDoseAsMissed(logId)
    suspend fun addMedicineStock(
        medicineId: Int,
        quantityToAdd: Double
    ): Boolean {
        if (!quantityToAdd.isFinite() || quantityToAdd <= 0.0) {
            return false
        }

        return dao.addMedicineStock(
            medicineId,
            quantityToAdd
        ) == 1
    }
    fun getDoseLogsForDay(
        startOfDay: Long,
        endOfDay: Long
    ): Flow<List<DoseLog>> =
        dao.getDoseLogsForDay(startOfDay, endOfDay)

    suspend fun saveScheduledDates(
        medicineId: Int,
        dates: List<String>
    ) {
        dao.deleteScheduledDates(medicineId)

        val uniqueDates = dates.distinct().sorted()

        if (uniqueDates.isNotEmpty()) {
            dao.insertScheduledDates(
                uniqueDates.map { date ->
                    MedicineScheduledDate(
                        medicineId = medicineId,
                        date = date
                    )
                }
            )
        }
    }

    suspend fun getScheduledDates(
        medicineId: Int
    ): List<String> {
        return dao.getScheduledDateStrings(medicineId)
    }

}
