
package com.soham.medicinereminderapp.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicine(medicine: Medicine): Long

    @Update
    suspend fun updateMedicine(medicine: Medicine)

    @Delete
    suspend fun deleteMedicine(medicine: Medicine)

    @Query("SELECT * FROM medicines ORDER BY name ASC")
    fun getAllMedicines(): Flow<List<Medicine>>

    @Query("SELECT * FROM medicines WHERE id = :medicineId LIMIT 1")
    suspend fun getMedicineById(medicineId: Int): Medicine?

    @Query(
        """
        SELECT * FROM medicines
        WHERE name LIKE '%' || :searchQuery || '%'
        ORDER BY name ASC
        """
    )
    fun searchMedicines(searchQuery: String): Flow<List<Medicine>>

    @Transaction
    @Query("SELECT * FROM medicines ORDER BY name ASC")
    fun getMedicinesWithTimes(): Flow<List<MedicineWithTimes>>

    @Transaction
    @Query("SELECT * FROM medicines WHERE id = :medicineId LIMIT 1")
    suspend fun getMedicineWithTimes(
        medicineId: Int
    ): MedicineWithTimes?

    @Transaction
    @Query("SELECT * FROM medicines ORDER BY name ASC")
    suspend fun getMedicinesWithTimesOnce(): List<MedicineWithTimes>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicineTime(medicineTime: MedicineTime): Long

    @Update
    suspend fun updateMedicineTime(medicineTime: MedicineTime)

    @Delete
    suspend fun deleteMedicineTime(medicineTime: MedicineTime)

    @Query(
        """
        DELETE FROM medicine_times
        WHERE medicineId = :medicineId
        """
    )
    suspend fun deleteMedicineTimesForMedicine(medicineId: Int)

    @Query(
        """
        SELECT * FROM medicine_times
        WHERE medicineId = :medicineId
        ORDER BY hour ASC, minute ASC
        """
    )
    fun getTimesForMedicine(
        medicineId: Int
    ): Flow<List<MedicineTime>>

    @Query("SELECT * FROM medicine_times WHERE id = :timeId LIMIT 1")
    suspend fun getMedicineTimeById(timeId: Int): MedicineTime?

    @Query(
        """
        SELECT * FROM medicine_times
        WHERE isEnabled = 1
        ORDER BY hour ASC, minute ASC
        """
    )
    suspend fun getAllEnabledMedicineTimes(): List<MedicineTime>

    @Query(
        """
        UPDATE medicine_times
        SET isEnabled = :enabled
        WHERE id = :timeId
        """
    )
    suspend fun setMedicineTimeEnabled(
        timeId: Int,
        enabled: Boolean
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoseLog(doseLog: DoseLog): Long

    @Update
    suspend fun updateDoseLog(doseLog: DoseLog)

    @Query("SELECT * FROM dose_logs ORDER BY scheduledTime DESC")
    fun getAllDoseLogs(): Flow<List<DoseLog>>

    @Query(
        """
        SELECT * FROM dose_logs
        WHERE medicineId = :medicineId
        ORDER BY scheduledTime DESC
        """
    )
    fun getDoseLogsForMedicine(
        medicineId: Int
    ): Flow<List<DoseLog>>

    @Query("SELECT * FROM dose_logs WHERE id = :logId LIMIT 1")
    suspend fun getDoseLogById(logId: Int): DoseLog?

    @Query("""
    UPDATE dose_logs
    SET status = 'TAKEN',
        takenTime = :takenTime
    WHERE id = :logId
      AND status = 'PENDING'
""")
    suspend fun markDoseAsTaken(
        logId: Int,
        takenTime: Long
    ): Int
    @Query(
        """
    UPDATE medicines
    SET currentStock = currentStock - :unitsToDeduct
    WHERE id = :medicineId
      AND currentStock IS NOT NULL
    """
    )
    suspend fun deductMedicineStock(
        medicineId: Int,
        unitsToDeduct: Double
    ): Int

    @Transaction
    suspend fun markDoseAsTakenAndDeductStock(
        logId: Int,
        takenTime: Long
    ): Boolean {
        val doseLog = getDoseLogById(logId) ?: return false

        // Do not deduct stock twice for the same dose.
        if (doseLog.status != "PENDING") return false

        val updatedRows = markDoseAsTaken(logId, takenTime)
        if (updatedRows != 1) return false

        val medicineId = doseLog.medicineId ?: return true
        val medicine = getMedicineById(medicineId) ?: return true

        val stock = medicine.currentStock ?: return true
        val unitsPerDose = medicine.unitsPerDose ?: return true

        // Invalid or unconfigured stock quantities are not deducted.
        if (unitsPerDose <= 0.0) return true

        deductMedicineStock(
            medicineId = medicineId,
            unitsToDeduct = unitsPerDose
        )

        return true
    }
    @Query(
        """
        UPDATE dose_logs
        SET status = 'MISSED'
        WHERE id = :logId AND status = 'PENDING'
        """
    )
    suspend fun markDoseAsMissed(logId: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDoseLogIfAbsent(doseLog: DoseLog): Long

    @Query(
        """
        SELECT * FROM dose_logs
        WHERE reminderKey = :reminderKey
        LIMIT 1
        """
    )
    suspend fun getDoseLogByReminderKey(
        reminderKey: String
    ): DoseLog?

    @Query(
        """
        UPDATE dose_logs
        SET status = 'NOT_TAKEN', takenTime = NULL
        WHERE id = :logId AND status = 'PENDING'
        """
    )
    suspend fun markDoseAsNotTaken(logId: Int)

    @Query(
        """
        SELECT * FROM dose_logs
        WHERE scheduledTime >= :startOfDay
        AND scheduledTime < :endOfDay
        ORDER BY scheduledTime ASC
        """
    )
    fun getDoseLogsForDay(
        startOfDay: Long,
        endOfDay: Long
    ): Flow<List<DoseLog>>
    @Query(
        """
    UPDATE medicines
    SET currentStock = currentStock + :quantityToAdd,
        lowStockAlertActive = 0
    WHERE id = :medicineId
      AND currentStock IS NOT NULL
      AND :quantityToAdd > 0
    """
    )
    suspend fun addMedicineStock(
        medicineId: Int,
        quantityToAdd: Double
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduledDates(
        dates: List<MedicineScheduledDate>
    )

    @Query("""
    DELETE FROM medicine_scheduled_dates
    WHERE medicineId = :medicineId
""")
    suspend fun deleteScheduledDates(medicineId: Int)

    @Query("""
    SELECT * FROM medicine_scheduled_dates
    WHERE medicineId = :medicineId
    ORDER BY date ASC
""")
    suspend fun getScheduledDates(
        medicineId: Int
    ): List<MedicineScheduledDate>

    @Query("""
    SELECT date FROM medicine_scheduled_dates
    WHERE medicineId = :medicineId
    ORDER BY date ASC
""")
    suspend fun getScheduledDateStrings(
        medicineId: Int
    ): List<String>

}
