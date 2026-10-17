
package com.soham.medicinereminderapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
                    "Notifications are disabled. Enable them in Settings to receive reminders.",
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
                            if (taken) {
                                "Dose marked as taken"
                            } else {
                                "Dose marked as not taken"
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@MainActivity,
                            "Could not update dose: ${e.message}",
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
                            "No medicines added yet. Tap Add Medicine to get started."
                        } else {
                            "Your medicines will appear here."
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
                    "Medicine deleted",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Could not delete medicine: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        return when (hour) {
            in 5..11 -> "Good morning, User!"
            in 12..16 -> "Good afternoon, User!"
            in 17..20 -> "Good evening, User!"
            else -> "Hello, User!"
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
