package com.soham.medicinereminderapp

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.soham.medicinereminderapp.data.AppDatabase
import com.soham.medicinereminderapp.data.Medicine
import com.soham.medicinereminderapp.data.MedicineTime
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
    private lateinit var tvSelectedTimes: TextView
    private lateinit var ivMedicinePreview: ImageView
    private lateinit var btnSaveMedicine: Button
    private lateinit var btnAddTime: Button
    private lateinit var btnCaptureMedicine: Button

    private val database by lazy {
        AppDatabase.getDatabase(applicationContext)
    }

    private val dao by lazy {
        database.medicineDao()
    }

    private data class ReminderTime(
        val hour: Int,
        val minute: Int
    )

    // Calendar weekday constants:
    // Sunday = 1, Monday = 2, ... Saturday = 7.
    private val selectedWeekdays = mutableSetOf(
        Calendar.SUNDAY,
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY
    )

    private val reminderTimes = mutableListOf<ReminderTime>()

    private var medicineId = 0
    private var medicineImagePath: String? = null
    private var isSaving = false
    private var photoFile: File? = null

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->

            if (success && photoFile?.exists() == true) {
                medicineImagePath = photoFile!!.absolutePath

                ivMedicinePreview.setImageURI(
                    Uri.fromFile(photoFile)
                )

                Toast.makeText(
                    this,
                    "Medicine photo captured",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                photoFile?.delete()
                photoFile = null

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
        setupWeekdayChips()
        setupButtons()

        medicineId = intent.getIntExtra("medicineId", 0)

        if (medicineId != 0) {
            loadMedicineForEditing(medicineId)
        }

        displayReminderTimes()
    }

    private fun initializeViews() {
        etMedicineName = findViewById(R.id.etMedicineName)
        etDosage = findViewById(R.id.etDosage)
        etMedicineNotes = findViewById(R.id.etMedicineNotes)
        tvSelectedTimes = findViewById(R.id.tvSelectedTimes)
        ivMedicinePreview = findViewById(R.id.ivMedicinePreview)

        btnSaveMedicine = findViewById(R.id.btnSaveMedicine)
        btnAddTime = findViewById(R.id.btnAddTime)
        btnCaptureMedicine = findViewById(R.id.btnCaptureMedicine)
    }

    private fun setupButtons() {
        btnAddTime.setOnClickListener {
            showTimePicker()
        }

        btnCaptureMedicine.setOnClickListener {
            checkCameraPermission()
        }

        btnSaveMedicine.setOnClickListener {
            saveMedicine()
        }
    }

    // ---------------------------------------------------------
    // WEEKDAY SELECTION
    // ---------------------------------------------------------

    private fun getWeekdayChips(): Map<Int, Int> {
        return linkedMapOf(
            R.id.chipSunday to Calendar.SUNDAY,
            R.id.chipMonday to Calendar.MONDAY,
            R.id.chipTuesday to Calendar.TUESDAY,
            R.id.chipWednesday to Calendar.WEDNESDAY,
            R.id.chipThursday to Calendar.THURSDAY,
            R.id.chipFriday to Calendar.FRIDAY,
            R.id.chipSaturday to Calendar.SATURDAY
        )
    }

    private fun setupWeekdayChips() {
        getWeekdayChips().forEach { (viewId, day) ->

            val chip = findViewById<TextView>(viewId)

            chip.setOnClickListener {
                if (selectedWeekdays.contains(day)) {
                    selectedWeekdays.remove(day)
                } else {
                    selectedWeekdays.add(day)
                }

                // Always render from the current selection state.
                refreshWeekdayChips()
            }
        }

        refreshWeekdayChips()
    }

    private fun refreshWeekdayChips() {
        getWeekdayChips().forEach { (viewId, day) ->
            val chip = findViewById<TextView>(viewId)

            updateWeekdayChip(
                chip = chip,
                isSelected = selectedWeekdays.contains(day)
            )
        }
    }

    private fun updateWeekdayChip(
        chip: TextView,
        isSelected: Boolean
    ) {
        // Create the appearance directly so reversed XML drawable
        // definitions cannot reverse the selected state.

        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(12).toFloat()

            if (isSelected) {
                // SELECTED: highlighted lavender background.
                setColor(Color.parseColor("#E8E0F2"))
                setStroke(
                    dpToPx(1),
                    Color.parseColor("#B7A0D8")
                )
            } else {
                // UNSELECTED: transparent background.
                setColor(Color.TRANSPARENT)
                setStroke(0, Color.TRANSPARENT)
            }
        }

        chip.background = background

        chip.setTextColor(
            if (isSelected) {
                Color.parseColor("#29213A")
            } else {
                Color.parseColor("#29213A")
            }
        )

        chip.gravity = Gravity.CENTER
        chip.isSelected = isSelected

        chip.animate()
            .scaleX(if (isSelected) 1.03f else 1f)
            .scaleY(if (isSelected) 1.03f else 1f)
            .setDuration(100)
            .start()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun getSelectedWeekdaysString(): String {
        return selectedWeekdays
            .sorted()
            .joinToString(",")
    }

    private fun setSelectedWeekdays(weekdays: String?) {
        selectedWeekdays.clear()

        if (weekdays.isNullOrBlank()) {
            // Preserve the existing behavior for medicines without
            // a saved weekday value: select every day.
            selectedWeekdays.addAll(
                listOf(
                    Calendar.SUNDAY,
                    Calendar.MONDAY,
                    Calendar.TUESDAY,
                    Calendar.WEDNESDAY,
                    Calendar.THURSDAY,
                    Calendar.FRIDAY,
                    Calendar.SATURDAY
                )
            )
        } else {
            weekdays.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter {
                    it in Calendar.SUNDAY..Calendar.SATURDAY
                }
                .forEach { selectedWeekdays.add(it) }
        }

        refreshWeekdayChips()
    }

    // ---------------------------------------------------------
    // REMINDER TIMES
    // ---------------------------------------------------------

    private fun showTimePicker() {
        val now = Calendar.getInstance()

        TimePickerDialog(
            this,
            { _, hour, minute ->

                val alreadyExists = reminderTimes.any {
                    it.hour == hour && it.minute == minute
                }

                if (alreadyExists) {
                    Toast.makeText(
                        this,
                        "This reminder time is already added",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@TimePickerDialog
                }

                reminderTimes.add(
                    ReminderTime(hour, minute)
                )

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
        if (reminderTimes.isEmpty()) {
            tvSelectedTimes.text = "No reminder times added"
            return
        }

        val formatter = SimpleDateFormat(
            "hh:mm a",
            Locale.getDefault()
        )

        tvSelectedTimes.text = reminderTimes.joinToString("\n") { time ->
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, time.hour)
                set(Calendar.MINUTE, time.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            "• ${formatter.format(calendar.time)}"
        }
    }

    // ---------------------------------------------------------
    // CAMERA
    // ---------------------------------------------------------

    private fun checkCameraPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermission.launch(
                Manifest.permission.CAMERA
            )
        } else {
            openCamera()
        }
    }

    private fun openCamera() {
        try {
            val imageDirectory = File(
                filesDir,
                "medicine_images"
            )

            if (
                !imageDirectory.exists() &&
                !imageDirectory.mkdirs()
            ) {
                throw IllegalStateException(
                    "Unable to create image directory"
                )
            }

            photoFile = File(
                imageDirectory,
                "medicine_${System.currentTimeMillis()}.jpg"
            )

            val photoUri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                photoFile!!
            )

            takePictureLauncher.launch(photoUri)

        } catch (e: Exception) {
            photoFile?.delete()
            photoFile = null

            Toast.makeText(
                this,
                "Unable to open camera: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ---------------------------------------------------------
    // LOAD MEDICINE FOR EDITING
    // ---------------------------------------------------------

    private fun loadMedicineForEditing(id: Int) {
        lifecycleScope.launch {
            try {
                val relation = withContext(Dispatchers.IO) {
                    dao.getMedicineWithTimes(id)
                }

                if (relation == null) {
                    Toast.makeText(
                        this@AddMedicineActivity,
                        "Medicine not found",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()
                    return@launch
                }

                val medicine = relation.medicine

                etMedicineName.setText(medicine.name)
                etDosage.setText(medicine.dosage)
                etMedicineNotes.setText(medicine.notes.orEmpty())

                medicineImagePath = medicine.imagePath

                if (!medicine.imagePath.isNullOrBlank()) {
                    val imageFile = File(medicine.imagePath)

                    if (imageFile.exists()) {
                        ivMedicinePreview.setImageURI(
                            Uri.fromFile(imageFile)
                        )
                    }
                }

                setSelectedWeekdays(medicine.weekdays)

                reminderTimes.clear()

                relation.reminderTimes.forEach { time ->
                    reminderTimes.add(
                        ReminderTime(
                            hour = time.hour,
                            minute = time.minute
                        )
                    )
                }

                reminderTimes.sortWith(
                    compareBy<ReminderTime> { it.hour }
                        .thenBy { it.minute }
                )

                displayReminderTimes()

            } catch (e: Exception) {
                Toast.makeText(
                    this@AddMedicineActivity,
                    "Unable to load medicine: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ---------------------------------------------------------
    // SAVE OR UPDATE MEDICINE AND SCHEDULE REMINDERS
    // ---------------------------------------------------------

    private fun saveMedicine() {
        if (isSaving) return

        val medicineName = etMedicineName.text.toString().trim()
        val dosage = etDosage.text.toString().trim()

        val notes = etMedicineNotes.text.toString()
            .trim()
            .ifBlank { null }

        if (medicineName.isBlank()) {
            etMedicineName.error = "Enter medicine name"
            etMedicineName.requestFocus()
            return
        }

        if (dosage.isBlank()) {
            etDosage.error = "Enter dosage"
            etDosage.requestFocus()
            return
        }

        if (selectedWeekdays.isEmpty()) {
            Toast.makeText(
                this,
                "Select at least one weekday",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (reminderTimes.isEmpty()) {
            Toast.makeText(
                this,
                "Add at least one reminder time",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        isSaving = true
        btnSaveMedicine.isEnabled = false

        val weekdaysString = getSelectedWeekdaysString()
        val isNewMedicine = medicineId == 0

        // Take a snapshot so the data used for saving is consistent.
        val timesToSave = reminderTimes.toList()

        lifecycleScope.launch {
            try {
                val savedMedicineId = withContext(Dispatchers.IO) {

                    if (isNewMedicine) {
                        val medicine = Medicine(
                            name = medicineName,
                            dosage = dosage,
                            imagePath = medicineImagePath,
                            notes = notes,
                            weekdays = weekdaysString
                        )

                        dao.insertMedicine(medicine).toInt()

                    } else {
                        // Read old reminder IDs before deleting their records.
                        val oldRelation =
                            dao.getMedicineWithTimes(medicineId)

                        // Cancel existing alarms before replacing times.
                        oldRelation?.reminderTimes?.forEach { oldTime ->
                            ReminderScheduler.cancelReminder(
                                this@AddMedicineActivity,
                                oldTime.id
                            )

                            ReminderScheduler.cancelSnooze(
                                this@AddMedicineActivity,
                                oldTime.id
                            )
                        }

                        val medicine = Medicine(
                            id = medicineId,
                            name = medicineName,
                            dosage = dosage,
                            imagePath = medicineImagePath,
                            notes = notes,
                            weekdays = weekdaysString
                        )

                        dao.updateMedicine(medicine)
                        dao.deleteMedicineTimesForMedicine(medicineId)

                        medicineId
                    }.also { savedId ->

                        timesToSave.forEach { time ->
                            dao.insertMedicineTime(
                                MedicineTime(
                                    medicineId = savedId,
                                    hour = time.hour,
                                    minute = time.minute
                                )
                            )
                        }
                    }
                }

                // Load the persisted records to get their real database IDs.
                val savedRelation = withContext(Dispatchers.IO) {
                    dao.getMedicineWithTimes(savedMedicineId)
                } ?: throw IllegalStateException(
                    "Saved medicine could not be loaded"
                )

                // Schedule each reminder using the saved weekday selection.
                savedRelation.reminderTimes.forEach { time ->
                    ReminderScheduler.scheduleReminder(
                        this@AddMedicineActivity,
                        time,
                        savedRelation.medicine.name,
                        savedRelation.medicine.dosage,
                        savedRelation.medicine.weekdays
                    )
                }

                Toast.makeText(
                    this@AddMedicineActivity,
                    if (isNewMedicine) {
                        "Medicine saved successfully"
                    } else {
                        "Medicine updated successfully"
                    },
                    Toast.LENGTH_SHORT
                ).show()

                setResult(Activity.RESULT_OK)
                finish()

            } catch (e: Exception) {
                Toast.makeText(
                    this@AddMedicineActivity,
                    "Unable to save medicine: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()

                isSaving = false
                btnSaveMedicine.isEnabled = true
            }
        }
    }
}