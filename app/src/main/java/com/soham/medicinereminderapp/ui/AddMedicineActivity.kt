
package com.soham.medicinereminderapp

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.soham.medicinereminderapp.data.AppDatabase
import com.soham.medicinereminderapp.data.Medicine
import com.soham.medicinereminderapp.data.MedicineScheduledDate
import com.soham.medicinereminderapp.data.MedicineTime
import com.soham.medicinereminderapp.reminder.LowStockWorkScheduler
import com.soham.medicinereminderapp.reminder.MedicineSchedule
import com.soham.medicinereminderapp.reminder.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddMedicineActivity : AppCompatActivity() {

    private lateinit var etMedicineName: EditText
    private lateinit var etDosage: EditText
    private lateinit var etMedicineNotes: EditText
    private lateinit var etInitialStock: EditText
    private lateinit var etUnitsPerDose: EditText
    private lateinit var etStockUnit: EditText

    private lateinit var tvSelectedTimes: TextView
    private lateinit var tvSelectedDates: TextView
    private lateinit var ivMedicinePreview: ImageView

    private lateinit var btnSaveMedicine: Button
    private lateinit var btnAddTime: Button
    private lateinit var btnCaptureMedicine: Button
    private lateinit var btnStartDate: Button
    private lateinit var btnEndDate: Button
    private lateinit var btnAddSelectedDate: Button
    private lateinit var rgScheduleType: RadioGroup

    private val dao by lazy {
        AppDatabase.getDatabase(applicationContext).medicineDao()
    }

    private data class ReminderTime(
        val hour: Int,
        val minute: Int
    )

    private data class StockInputs(
        val currentStock: Double?,
        val unitsPerDose: Double?,
        val stockUnit: String
    )

    private val reminderTimes = mutableListOf<ReminderTime>()
    private val selectedDates = sortedSetOf<String>()

    private var medicineId = 0
    private var medicineImagePath: String? = null
    private var photoFile: File? = null
    private var isSaving = false
    private var isEditMode = false

    private var scheduleType = MedicineSchedule.DATE_RANGE
    private var startDate: String? = null
    private var endDate: String? = null

    private val dateFormat =
        SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                photoFile?.let { file ->
                    medicineImagePath = file.absolutePath
                    displayMedicineImage(file.absolutePath)
                }
            } else {
                Toast.makeText(
                    this,
                    "Photo was not captured",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val requestCameraPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                openCamera()
            } else {
                Toast.makeText(
                    this,
                    "Camera permission is required to take a photo",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_medicine)

        initializeViews()
        setupCalendarControls()
        setupButtons()

        medicineId = intent.getIntExtra("medicineId", 0)
        isEditMode = medicineId > 0

        if (isEditMode) {
            btnSaveMedicine.text = "Update medicine"
            loadMedicineForEditing(medicineId)
        } else {
            setStockInputsEnabled(true)
            updateScheduleVisibility()
            displayReminderTimes()
            refreshSelectedDatesText()
        }
    }

    private fun initializeViews() {
        etMedicineName = findViewById(R.id.etMedicineName)
        etDosage = findViewById(R.id.etDosage)
        etMedicineNotes = findViewById(R.id.etMedicineNotes)
        etInitialStock = findViewById(R.id.etInitialStock)
        etUnitsPerDose = findViewById(R.id.etUnitsPerDose)
        etStockUnit = findViewById(R.id.etStockUnit)

        tvSelectedTimes = findViewById(R.id.tvSelectedTimes)
        tvSelectedDates = findViewById(R.id.tvSelectedDates)
        ivMedicinePreview = findViewById(R.id.ivMedicinePreview)

        btnSaveMedicine = findViewById(R.id.btnSaveMedicine)
        btnAddTime = findViewById(R.id.btnAddTime)
        btnCaptureMedicine = findViewById(R.id.btnCaptureMedicine)
        btnStartDate = findViewById(R.id.btnStartDate)
        btnEndDate = findViewById(R.id.btnEndDate)
        btnAddSelectedDate = findViewById(R.id.btnAddSelectedDate)
        rgScheduleType = findViewById(R.id.rgScheduleType)
    }

    private fun setupButtons() {
        btnAddTime.setOnClickListener {
            showTimePicker()
        }

        btnCaptureMedicine.setOnClickListener {
            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                openCamera()
            } else {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }

        btnSaveMedicine.setOnClickListener {
            saveMedicine()
        }
    }

    // ---------------- CALENDAR SCHEDULING ----------------

    private fun setupCalendarControls() {
        rgScheduleType.setOnCheckedChangeListener { _, checkedId ->
            scheduleType = if (checkedId == R.id.rbSelectedDates) {
                MedicineSchedule.SELECTED_DATES
            } else {
                MedicineSchedule.DATE_RANGE
            }

            updateScheduleVisibility()
        }

        btnStartDate.setOnClickListener {
            showDatePicker(startDate ?: todayString()) { date ->
                startDate = date
                updateDateButtonLabels()
            }
        }

        btnEndDate.setOnClickListener {
            showDatePicker(endDate ?: startDate ?: todayString()) { date ->
                endDate = date
                updateDateButtonLabels()
            }
        }

        btnAddSelectedDate.setOnClickListener {
            showDatePicker(todayString()) { date ->
                selectedDates.add(date)
                refreshSelectedDatesText()
            }
        }

        updateScheduleVisibility()
        updateDateButtonLabels()
    }

    private fun updateScheduleVisibility() {
        val isRange = scheduleType == MedicineSchedule.DATE_RANGE

        btnStartDate.visibility =
            if (isRange) View.VISIBLE else View.GONE

        btnEndDate.visibility =
            if (isRange) View.VISIBLE else View.GONE

        btnAddSelectedDate.visibility =
            if (isRange) View.GONE else View.VISIBLE

        tvSelectedDates.visibility =
            if (isRange) View.GONE else View.VISIBLE
    }

    private fun showDatePicker(
        initialDate: String,
        onSelected: (String) -> Unit
    ) {
        val calendar = parseCalendarDate(initialDate)
            ?: Calendar.getInstance()

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selected = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                onSelected(MedicineSchedule.formatDate(selected))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun todayString(): String {
        return MedicineSchedule.formatDate(Calendar.getInstance())
    }

    private fun parseCalendarDate(value: String): Calendar? {
        return try {
            val date = dateFormat.parse(value) ?: return null

            Calendar.getInstance().apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun updateDateButtonLabels() {
        btnStartDate.text = startDate?.let {
            "Start date: $it"
        } ?: "Choose start date"

        btnEndDate.text = endDate?.let {
            "End date: $it"
        } ?: "Choose end date"
    }

    private fun refreshSelectedDatesText() {
        tvSelectedDates.text = if (selectedDates.isEmpty()) {
            "No individual dates selected"
        } else {
            selectedDates.joinToString("\n") { date ->
                "• $date"
            }
        }
    }

    private fun validateSchedule(): Boolean {
        if (scheduleType == MedicineSchedule.DATE_RANGE) {
            val start = startDate
            val end = endDate

            if (start == null || end == null) {
                showToast("Please choose both start and end dates")
                return false
            }

            if (start > end) {
                showToast("End date must be on or after start date")
                return false
            }

            if (end < todayString()) {
                showToast("Please choose a future or current date range")
                return false
            }
        } else {
            if (selectedDates.isEmpty()) {
                showToast("Please select at least one calendar date")
                return false
            }

            if (selectedDates.none { it >= todayString() }) {
                showToast("Select at least one current or future date")
                return false
            }
        }

        return true
    }

    // ---------------- REMINDER TIMES ----------------

    private fun showTimePicker() {
        val now = Calendar.getInstance()

        TimePickerDialog(
            this,
            { _, hour, minute ->
                val duplicate = reminderTimes.any {
                    it.hour == hour && it.minute == minute
                }

                if (duplicate) {
                    showToast("This reminder time is already added")
                    return@TimePickerDialog
                }

                reminderTimes.add(ReminderTime(hour, minute))
                reminderTimes.sortWith(
                    compareBy<ReminderTime> { it.hour }
                        .thenBy { it.minute }
                )

                displayReminderTimes()
            },
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun displayReminderTimes() {
        tvSelectedTimes.text = if (reminderTimes.isEmpty()) {
            "No reminder times added"
        } else {
            reminderTimes.joinToString("\n") { time ->
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, time.hour)
                    set(Calendar.MINUTE, time.minute)
                }

                SimpleDateFormat("hh:mm a", Locale.getDefault())
                    .format(calendar.time)
            }
        }
    }

    // ---------------- MEDICINE PHOTO ----------------

    private fun openCamera() {
        try {
            val imageDirectory = File(filesDir, "medicine_images")

            if (!imageDirectory.exists()) {
                imageDirectory.mkdirs()
            }

            photoFile = File(
                imageDirectory,
                "medicine_${System.currentTimeMillis()}.jpg"
            )

            val imageUri: Uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                photoFile!!
            )

            takePictureLauncher.launch(imageUri)
        } catch (e: Exception) {
            android.util.Log.e(
                "AddMedicineActivity",
                "Could not open camera",
                e
            )
            showToast("Unable to open camera")
        }
    }

    private fun displayMedicineImage(path: String?) {
        if (path.isNullOrBlank()) {
            ivMedicinePreview.setImageResource(
                android.R.drawable.ic_menu_gallery
            )
            return
        }

        val file = File(path)

        if (!file.exists()) {
            ivMedicinePreview.setImageResource(
                android.R.drawable.ic_menu_gallery
            )
            return
        }

        val bitmap = BitmapFactory.decodeFile(path)

        if (bitmap != null) {
            ivMedicinePreview.setImageBitmap(bitmap)
        } else {
            ivMedicinePreview.setImageResource(
                android.R.drawable.ic_menu_gallery
            )
        }
    }

    // ---------------- STOCK INPUTS ----------------

    private fun setStockInputsEnabled(enabled: Boolean) {
        etInitialStock.isEnabled = enabled
        etUnitsPerDose.isEnabled = enabled
        etStockUnit.isEnabled = enabled
    }

    private fun readStockInputs(): StockInputs? {
        val stockText = etInitialStock.text.toString().trim()
        val unitsText = etUnitsPerDose.text.toString().trim()
        val unit = etStockUnit.text.toString().trim()
            .ifBlank { "tablets" }

        if (stockText.isBlank() && unitsText.isBlank()) {
            return StockInputs(
                currentStock = null,
                unitsPerDose = null,
                stockUnit = unit
            )
        }

        if (stockText.isBlank() || unitsText.isBlank()) {
            showToast("Enter both initial stock and units per dose")
            return null
        }

        val stock = stockText.toDoubleOrNull()
        val units = unitsText.toDoubleOrNull()

        if (stock == null || !stock.isFinite() || stock < 0.0) {
            showToast("Enter a valid non-negative stock quantity")
            return null
        }

        if (units == null || !units.isFinite() || units <= 0.0) {
            showToast("Units per dose must be greater than zero")
            return null
        }

        return StockInputs(
            currentStock = stock,
            unitsPerDose = units,
            stockUnit = unit
        )
    }

    // ---------------- LOAD EXISTING MEDICINE ----------------

    private fun loadMedicineForEditing(id: Int) {
        lifecycleScope.launch {
            try {
                val item = withContext(Dispatchers.IO) {
                    dao.getMedicineWithTimes(id)
                }

                if (item == null) {
                    showToast("Medicine not found")
                    finish()
                    return@launch
                }

                val medicine = item.medicine

                etMedicineName.setText(medicine.name)
                etDosage.setText(medicine.dosage)
                etMedicineNotes.setText(medicine.notes.orEmpty())

                medicineImagePath = medicine.imagePath
                displayMedicineImage(medicineImagePath)

                scheduleType = when (medicine.scheduleType) {
                    MedicineSchedule.SELECTED_DATES ->
                        MedicineSchedule.SELECTED_DATES

                    else -> MedicineSchedule.DATE_RANGE
                }

                startDate = medicine.startDate
                endDate = medicine.endDate

                selectedDates.clear()
                selectedDates.addAll(
                    withContext(Dispatchers.IO) {
                        dao.getScheduledDateStrings(id)
                    }
                )

                rgScheduleType.check(
                    if (scheduleType == MedicineSchedule.SELECTED_DATES) {
                        R.id.rbSelectedDates
                    } else {
                        R.id.rbDateRange
                    }
                )

                updateScheduleVisibility()
                updateDateButtonLabels()
                refreshSelectedDatesText()

                reminderTimes.clear()
                reminderTimes.addAll(
                    item.reminderTimes.map {
                        ReminderTime(it.hour, it.minute)
                    }
                )
                reminderTimes.sortWith(
                    compareBy<ReminderTime> { it.hour }
                        .thenBy { it.minute }
                )
                displayReminderTimes()

                etInitialStock.setText(
                    medicine.currentStock?.toString().orEmpty()
                )
                etUnitsPerDose.setText(
                    medicine.unitsPerDose?.toString().orEmpty()
                )
                etStockUnit.setText(medicine.stockUnit)

                // Preserve existing stock while editing this medicine.
                setStockInputsEnabled(false)

            } catch (e: Exception) {
                android.util.Log.e(
                    "AddMedicineActivity",
                    "Failed to load medicine",
                    e
                )
                showToast("Unable to load medicine details")
            }
        }
    }

    // ---------------- SAVE / UPDATE ----------------

    private fun saveMedicine() {
        if (isSaving) return

        val name = etMedicineName.text.toString().trim()
        val dosage = etDosage.text.toString().trim()
        val notes = etMedicineNotes.text.toString().trim()
            .ifBlank { null }

        if (name.isBlank()) {
            etMedicineName.error = "Enter medicine name"
            etMedicineName.requestFocus()
            return
        }

        if (dosage.isBlank()) {
            etDosage.error = "Enter dosage"
            etDosage.requestFocus()
            return
        }

        if (reminderTimes.isEmpty()) {
            showToast("Add at least one reminder time")
            return
        }

        if (!validateSchedule()) return

        val stockInputs = if (isEditMode) {
            null
        } else {
            readStockInputs() ?: return
        }

        isSaving = true
        btnSaveMedicine.isEnabled = false

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (isEditMode) {
                        updateExistingMedicine(
                            name = name,
                            dosage = dosage,
                            notes = notes
                        )
                    } else {
                        insertNewMedicine(
                            name = name,
                            dosage = dosage,
                            notes = notes,
                            stockInputs = stockInputs!!
                        )
                    }
                }

                // Update alarms after the medicine and its times have been saved.
                val savedItem = withContext(Dispatchers.IO) {
                    dao.getMedicineWithTimes(medicineId)
                }

                if (savedItem != null) {
                    savedItem.reminderTimes.forEach { time ->
                        if (time.isEnabled) {
                            ReminderScheduler.scheduleReminder(
                                this@AddMedicineActivity,
                                time,
                                savedItem.medicine.name,
                                savedItem.medicine.dosage
                            )
                        } else {
                            ReminderScheduler.cancelReminder(
                                this@AddMedicineActivity,
                                time.id
                            )
                        }
                    }
                }

                LowStockWorkScheduler.checkNow(applicationContext)

                showToast(
                    if (isEditMode) {
                        "Medicine updated successfully"
                    } else {
                        "Medicine saved successfully"
                    }
                )

                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                android.util.Log.e(
                    "AddMedicineActivity",
                    "Could not save medicine",
                    e
                )
                showToast("Could not save medicine: ${e.message ?: "unknown error"}")
            } finally {
                isSaving = false
                btnSaveMedicine.isEnabled = true
            }
        }
    }

    private suspend fun insertNewMedicine(
        name: String,
        dosage: String,
        notes: String?,
        stockInputs: StockInputs
    ) {
        val medicine = Medicine(
            name = name,
            dosage = dosage,
            imagePath = medicineImagePath,
            notes = notes,
            weekdays = "1,2,3,4,5,6,7",
            scheduleType = scheduleType,
            startDate = if (
                scheduleType == MedicineSchedule.DATE_RANGE
            ) startDate else null,
            endDate = if (
                scheduleType == MedicineSchedule.DATE_RANGE
            ) endDate else null,
            currentStock = stockInputs.currentStock,
            unitsPerDose = stockInputs.unitsPerDose,
            stockUnit = stockInputs.stockUnit,
            lowStockDays = 3
        )

        medicineId = dao.insertMedicine(medicine).toInt()

        saveSelectedDates()
        saveReminderTimes()
    }

    private suspend fun updateExistingMedicine(
        name: String,
        dosage: String,
        notes: String?
    ) {
        val oldItem = dao.getMedicineWithTimes(medicineId)
            ?: throw IllegalStateException("Medicine no longer exists")

        // Cancel alarms and snoozes before replacing their reminder times.
        oldItem.reminderTimes.forEach { time ->
            ReminderScheduler.cancelReminder(this, time.id)
            ReminderScheduler.cancelSnooze(this, time.id)
        }

        val updatedMedicine = oldItem.medicine.copy(
            name = name,
            dosage = dosage,
            imagePath = medicineImagePath,
            notes = notes,
            scheduleType = scheduleType,
            startDate = if (
                scheduleType == MedicineSchedule.DATE_RANGE
            ) startDate else null,
            endDate = if (
                scheduleType == MedicineSchedule.DATE_RANGE
            ) endDate else null
        )

        dao.updateMedicine(updatedMedicine)

        dao.deleteMedicineTimesForMedicine(medicineId)
        saveSelectedDates()
        saveReminderTimes()
    }

    private suspend fun saveSelectedDates() {
        dao.deleteScheduledDates(medicineId)

        if (scheduleType == MedicineSchedule.SELECTED_DATES) {
            val dateEntities = selectedDates.map { date ->
                MedicineScheduledDate(
                    medicineId = medicineId,
                    date = date
                )
            }

            if (dateEntities.isNotEmpty()) {
                dao.insertScheduledDates(dateEntities)
            }
        }
    }

    private suspend fun saveReminderTimes() {
        reminderTimes.forEach { time ->
            dao.insertMedicineTime(
                MedicineTime(
                    medicineId = medicineId,
                    hour = time.hour,
                    minute = time.minute,
                    isEnabled = true
                )
            )
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}
