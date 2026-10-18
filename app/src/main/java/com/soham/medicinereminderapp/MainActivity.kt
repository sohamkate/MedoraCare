
package com.soham.medicinereminderapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.soham.medicinereminderapp.data.AppDatabase
import com.soham.medicinereminderapp.data.Medicine
import com.soham.medicinereminderapp.reminder.LowStockWorkScheduler
import com.soham.medicinereminderapp.reminder.ReminderScheduler
import com.soham.medicinereminderapp.repository.MedicineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: MedicineAdapter
    private lateinit var repository: MedicineRepository

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                LowStockWorkScheduler.checkNow(this)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.notifications_disabled),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        val tvEmptyMessage = findViewById<TextView>(R.id.tvEmptyMessage)
        val btnAddMedicine = findViewById<Button>(R.id.btnAddMedicine)
        val rvMedicines = findViewById<RecyclerView>(R.id.rvMedicines)
        val layoutEmptyState = findViewById<View>(R.id.layoutEmptyState)

        setupLanguageSpinner()

        val bottomNavigation =
            findViewById<BottomNavigationView>(R.id.bottomNavigation)

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true

                R.id.nav_history -> {
                    startActivity(
                        Intent(this, HistoryActivity::class.java)
                    )
                    true
                }

                else -> false
            }
        }

        val dao = AppDatabase
            .getDatabase(applicationContext)
            .medicineDao()

        repository = MedicineRepository(dao)

        tvGreeting.text = getGreeting()

        requestNotificationPermission()

        // Schedule recurring stock checks and run one immediately.
        LowStockWorkScheduler.schedulePeriodic(this)
        LowStockWorkScheduler.checkNow(this)

        adapter = MedicineAdapter(
            onEditClick = { medicine ->
                val intent = Intent(
                    this,
                    AddMedicineActivity::class.java
                ).apply {
                    putExtra("medicineId", medicine.id)
                }
                startActivity(intent)
            },

            onDeleteClick = { medicine ->
                deleteMedicine(medicine)
            },

            onDoseAction = { doseLog, taken ->
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            if (taken) {
                                repository.markDoseAsTaken(
                                    doseLog.id,
                                    System.currentTimeMillis()
                                )
                            } else {
                                repository.markDoseAsNotTaken(doseLog.id)
                            }
                        }

                        // Recheck supply after stock is deducted.
                        if (taken) {
                            LowStockWorkScheduler.checkNow(
                                this@MainActivity
                            )
                        }

                        Toast.makeText(
                            this@MainActivity,
                            getString(
                                if (taken) {
                                    R.string.dose_marked_taken
                                } else {
                                    R.string.dose_marked_not_taken
                                }
                            ),
                            Toast.LENGTH_SHORT
                        ).show()

                    } catch (e: Exception) {
                        Toast.makeText(
                            this@MainActivity,
                            getString(
                                R.string.could_not_update_dose,
                                e.message ?: ""
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },

            onRefillClick = { medicine ->
                val intent = Intent(
                    this,
                    RefillMedicineActivity::class.java
                ).apply {
                    putExtra("medicineId", medicine.id)
                }

                startActivity(intent)
            }
        )

        rvMedicines.layoutManager = LinearLayoutManager(this)
        rvMedicines.adapter = adapter
        rvMedicines.isNestedScrollingEnabled = false

        btnAddMedicine.setOnClickListener {
            startActivity(
                Intent(this, AddMedicineActivity::class.java)
            )
        }

        observeMedicines(layoutEmptyState, tvEmptyMessage)
        observeTodayDoseLogs()
    }

    private fun setupLanguageSpinner() {
        val spinnerLanguage =
            findViewById<Spinner>(R.id.spinnerLanguage)

        val currentLanguage =
            AppCompatDelegate.getApplicationLocales()
                .get(0)?.language ?: "en"

        // Select the currently active app language without triggering
        // a language change during initial setup.
        spinnerLanguage.setSelection(
            if (currentLanguage == "hi") 1 else 0,
            false
        )

        spinnerLanguage.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val selectedLanguage = when (position) {
                        0 -> "en"
                        1 -> "hi"
                        else -> return
                    }

                    val activeLanguage =
                        AppCompatDelegate.getApplicationLocales()
                            .get(0)?.language ?: "en"

                    if (selectedLanguage != activeLanguage) {
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(
                                selectedLanguage
                            )
                        )
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) = Unit
            }
    }

    override fun onResume() {
        super.onResume()

        // Recheck stock when returning from Add/Edit/Refill.
        LowStockWorkScheduler.checkNow(this)
    }

    private fun observeMedicines(
        emptyState: View,
        emptyMessage: TextView
    ) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getMedicinesWithTimes().collect { medicines ->
                    adapter.submitList(medicines)

                    val isEmpty = medicines.isEmpty()

                    emptyState.visibility =
                        if (isEmpty) View.VISIBLE else View.GONE

                    emptyMessage.text =
                        if (isEmpty) {
                            getString(
                                R.string.no_medicines_added_yet_tap_add_medicine_to_get_started
                            )
                        } else {
                            getString(
                                R.string.your_medicines_will_appear_here
                            )
                        }
                }
            }
        }
    }

    private fun observeTodayDoseLogs() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val startOfDay = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.timeInMillis

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getDoseLogsForDay(
                    startOfDay,
                    endOfDay
                ).collect { logs ->
                    adapter.submitTodayDoseLogs(logs)
                }
            }
        }
    }

    private fun deleteMedicine(medicine: Medicine) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val medicineWithTimes =
                        repository.getMedicineWithTimes(medicine.id)

                    medicineWithTimes?.reminderTimes?.forEach { reminderTime ->
                        ReminderScheduler.cancelReminder(
                            this@MainActivity,
                            reminderTime.id
                        )

                        ReminderScheduler.cancelSnooze(
                            this@MainActivity,
                            reminderTime.id
                        )
                    }

                    repository.deleteMedicine(medicine)
                }

                LowStockWorkScheduler.checkNow(this@MainActivity)

                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.medicine_deleted),
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    getString(
                        R.string.could_not_delete_medicine,
                        e.message ?: ""
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getGreeting(): String {
        val hour = Calendar.getInstance()
            .get(Calendar.HOUR_OF_DAY)

        return when (hour) {
            in 5..11 -> getString(R.string.greeting_morning)
            in 12..16 -> getString(R.string.greeting_afternoon)
            in 17..20 -> getString(R.string.greeting_evening)
            else -> getString(R.string.greeting_hello)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS

            if (
                ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(permission)
            }
        }
    }
}
