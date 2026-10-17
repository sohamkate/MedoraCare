package com.soham.medicinereminderapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.soham.medicinereminderapp.data.AppDatabase
import com.soham.medicinereminderapp.repository.MedicineRepository
import com.soham.medicinereminderapp.reminder.LowStockWorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RefillMedicineActivity : AppCompatActivity() {

    private lateinit var repository: MedicineRepository
    private var medicineId: Int = -1

    private lateinit var tvMedicineName: TextView
    private lateinit var tvCurrentStock: TextView
    private lateinit var etRefillQuantity: EditText
    private lateinit var btnConfirmRefill: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_refill_medicine)

        tvMedicineName = findViewById(R.id.tvRefillMedicineName)
        tvCurrentStock = findViewById(R.id.tvCurrentStock)
        etRefillQuantity = findViewById(R.id.etRefillQuantity)
        btnConfirmRefill = findViewById(R.id.btnConfirmRefill)

        medicineId = intent.getIntExtra("medicineId", -1)

        if (medicineId <= 0) {
            Toast.makeText(this, "Medicine not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val dao = AppDatabase.getDatabase(applicationContext).medicineDao()
        repository = MedicineRepository(dao)

        loadMedicine()

        btnConfirmRefill.setOnClickListener {
            saveRefill()
        }
    }

    private fun loadMedicine() {
        lifecycleScope.launch {
            val medicine = withContext(Dispatchers.IO) {
                repository.getMedicineById(medicineId)
            }

            if (medicine == null) {
                Toast.makeText(
                    this@RefillMedicineActivity,
                    "Medicine not found",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return@launch
            }

            tvMedicineName.text = medicine.name

            val stock = medicine.currentStock
            tvCurrentStock.text = if (stock == null) {
                "Current stock: Not configured"
            } else {
                "Current stock: ${formatQuantity(stock)} ${medicine.stockUnit}"
            }

            btnConfirmRefill.isEnabled = stock != null
            etRefillQuantity.isEnabled = stock != null

            if (stock == null) {
                Toast.makeText(
                    this@RefillMedicineActivity,
                    "Set the current stock in medicine details before refilling.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun saveRefill() {
        val quantity = etRefillQuantity.text.toString().trim().toDoubleOrNull()

        if (quantity == null || !quantity.isFinite() || quantity <= 0.0) {
            etRefillQuantity.error = "Enter a quantity greater than zero"
            return
        }

        btnConfirmRefill.isEnabled = false

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                repository.addMedicineStock(medicineId, quantity)
            }

            if (success) {
                LowStockWorkScheduler.checkNow(this@RefillMedicineActivity)

                Toast.makeText(
                    this@RefillMedicineActivity,
                    "Medicine stock updated",
                    Toast.LENGTH_SHORT
                ).show()

                finish()
            } else {
                Toast.makeText(
                    this@RefillMedicineActivity,
                    "Stock was not updated. Check the medicine's stock settings.",
                    Toast.LENGTH_LONG
                ).show()

                btnConfirmRefill.isEnabled = true
            }
        }
    }

    private fun formatQuantity(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(java.util.Locale.getDefault(), "%.2f", value)
                .trimEnd('0')
                .trimEnd('.')
        }
    }
}