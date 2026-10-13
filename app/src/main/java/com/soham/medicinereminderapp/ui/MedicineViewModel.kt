
package com.soham.medicinereminderapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.soham.medicinereminderapp.data.DoseLog
import com.soham.medicinereminderapp.data.Medicine
import com.soham.medicinereminderapp.data.MedicineTime
import com.soham.medicinereminderapp.data.MedicineWithTimes
import com.soham.medicinereminderapp.repository.MedicineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class MedicineViewModel(
    private val repository: MedicineRepository
) : ViewModel() {

    // ---------------- MEDICINES ----------------

    val allMedicines: Flow<List<Medicine>> =
        repository.getAllMedicines()

    val medicinesWithTimes: Flow<List<MedicineWithTimes>> =
        repository.getMedicinesWithTimes()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: Flow<List<Medicine>> =
        _searchQuery.flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList())
            } else {
                repository.searchMedicines(query.trim())
            }
        }

    fun searchMedicines(query: String) {
        _searchQuery.value = query
    }

    fun insertMedicine(medicine: Medicine, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertMedicine(medicine)
            onSaved(id)
        }
    }

    fun updateMedicine(medicine: Medicine) {
        viewModelScope.launch {
            repository.updateMedicine(medicine)
        }
    }

    fun deleteMedicine(medicine: Medicine) {
        viewModelScope.launch {
            repository.deleteMedicine(medicine)
        }
    }

    // ------------ REMINDER TIMES ------------

    fun insertMedicineTime(
        medicineTime: MedicineTime,
        onSaved: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val id = repository.insertMedicineTime(medicineTime)
            onSaved(id)
        }
    }

    fun updateMedicineTime(medicineTime: MedicineTime) {
        viewModelScope.launch {
            repository.updateMedicineTime(medicineTime)
        }
    }

    fun deleteMedicineTime(medicineTime: MedicineTime) {
        viewModelScope.launch {
            repository.deleteMedicineTime(medicineTime)
        }
    }

    fun setMedicineTimeEnabled(timeId: Int, enabled: Boolean) {
        viewModelScope.launch {
            repository.setMedicineTimeEnabled(timeId, enabled)
        }
    }

    // ---------------- DOSE HISTORY ----------------

    val doseHistory: Flow<List<DoseLog>> =
        repository.getAllDoseLogs()

    fun insertDoseLog(doseLog: DoseLog) {
        viewModelScope.launch {
            repository.insertDoseLog(doseLog)
        }
    }

    fun markDoseAsTaken(logId: Int) {
        viewModelScope.launch {
            repository.markDoseAsTaken(
                logId = logId,
                takenTime = System.currentTimeMillis()
            )
        }
    }

    fun markDoseAsMissed(logId: Int) {
        viewModelScope.launch {
            repository.markDoseAsMissed(logId)
        }
    }
}

class MedicineViewModelFactory(
    private val repository: MedicineRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MedicineViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MedicineViewModel(repository) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}
