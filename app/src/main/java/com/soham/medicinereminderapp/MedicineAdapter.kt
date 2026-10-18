
package com.soham.medicinereminderapp

import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.soham.medicinereminderapp.data.DoseLog
import com.soham.medicinereminderapp.data.Medicine
import com.soham.medicinereminderapp.data.MedicineWithTimes
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MedicineAdapter(
    private val onEditClick: (Medicine) -> Unit,
    private val onDeleteClick: (Medicine) -> Unit,
    private val onDoseAction: (DoseLog, Boolean) -> Unit,
    private val onRefillClick: (Medicine) -> Unit
) : ListAdapter<MedicineWithTimes, MedicineAdapter.MedicineViewHolder>(
    DiffCallback
) {

    private var todayDoseLogs: List<DoseLog> = emptyList()

    fun submitTodayDoseLogs(logs: List<DoseLog>) {
        todayDoseLogs = logs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MedicineViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medicine, parent, false)

        return MedicineViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: MedicineViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    inner class MedicineViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        private val ivMedicineImage: ImageView =
            itemView.findViewById(R.id.ivMedicineImage)

        private val tvMedicineName: TextView =
            itemView.findViewById(R.id.tvMedicineName)

        private val tvDosage: TextView =
            itemView.findViewById(R.id.tvDosage)

        private val tvReminderTimes: TextView =
            itemView.findViewById(R.id.tvReminderTimes)

        private val tvMedicineNotes: TextView =
            itemView.findViewById(R.id.tvMedicineNotes)

        private val btnEdit: Button =
            itemView.findViewById(R.id.btnEdit)

        private val btnDelete: Button =
            itemView.findViewById(R.id.btnDelete)

        private val btnRefill: Button =
            itemView.findViewById(R.id.btnRefill)

        private val layoutDoseActions: LinearLayout =
            itemView.findViewById(R.id.layoutDoseActions)

        private val tvStockQuantity: TextView =
            itemView.findViewById(R.id.tvStockQuantity)

        private val tvStockDaysRemaining: TextView =
            itemView.findViewById(R.id.tvStockDaysRemaining)

        private val tvStockWarning: TextView =
            itemView.findViewById(R.id.tvStockWarning)

        private fun bindStockStatus(item: MedicineWithTimes) {
            val context = itemView.context
            val medicine = item.medicine
            val stock = medicine.currentStock
            val unitsPerDose = medicine.unitsPerDose

            tvStockWarning.visibility = View.GONE

            if (
                stock == null ||
                unitsPerDose == null ||
                unitsPerDose <= 0.0
            ) {
                tvStockQuantity.text =
                    context.getString(R.string.stock_not_set)

                tvStockDaysRemaining.text =
                    context.getString(R.string.supply_not_available)

                return
            }

            tvStockQuantity.text = context.getString(
                R.string.stock_remaining,
                formatQuantity(stock),
                medicine.stockUnit
            )

            val enabledTimes = item.reminderTimes
                .filter { it.isEnabled }

            val selectedDays = medicine.weekdays
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..7 }
                .distinct()

            if (enabledTimes.isEmpty() || selectedDays.isEmpty()) {
                tvStockDaysRemaining.text =
                    context.getString(R.string.schedule_unavailable)

                return
            }

            val dosesPerDay =
                enabledTimes.size.toDouble() * selectedDays.size / 7.0

            val unitsUsedPerDay = dosesPerDay * unitsPerDose

            if (unitsUsedPerDay <= 0.0) {
                tvStockDaysRemaining.text =
                    context.getString(R.string.supply_not_available)

                return
            }

            val daysRemaining = stock / unitsUsedPerDay

            val formattedDays = String.format(
                Locale.getDefault(),
                "%.1f",
                daysRemaining
            )

            tvStockDaysRemaining.text = context.getString(
                R.string.estimated_supply_days,
                formattedDays
            )

            if (stock <= 0.0) {
                tvStockWarning.text =
                    context.getString(R.string.stock_depleted_warning)

                tvStockWarning.setTextColor(Color.RED)
                tvStockWarning.visibility = View.VISIBLE

            } else if (daysRemaining <= medicine.lowStockDays) {
                tvStockWarning.text =
                    context.getString(R.string.low_stock_warning)

                tvStockWarning.setTextColor(Color.rgb(180, 83, 9))
                tvStockWarning.visibility = View.VISIBLE
            }
        }

        private fun formatQuantity(value: Double): String {
            return if (value % 1.0 == 0.0) {
                value.toLong().toString()
            } else {
                String.format(
                    Locale.getDefault(),
                    "%.2f",
                    value
                )
                    .trimEnd('0')
                    .trimEnd('.')
            }
        }

        fun bind(item: MedicineWithTimes) {
            val context = itemView.context
            val medicine = item.medicine

            tvMedicineName.text = medicine.name
            tvDosage.text = medicine.dosage

            bindStockStatus(item)

            // Display the saved medicine photo, if available.
            val imagePath = medicine.imagePath

            if (!imagePath.isNullOrBlank() && File(imagePath).exists()) {
                ivMedicineImage.setImageURI(Uri.fromFile(File(imagePath)))
            } else {
                ivMedicineImage.setImageResource(
                    android.R.drawable.ic_menu_gallery
                )
            }

            // Display optional notes.
            val notes = medicine.notes?.trim()

            if (!notes.isNullOrEmpty()) {
                tvMedicineNotes.text = context.getString(
                    R.string.medicine_notes_format,
                    notes
                )
                tvMedicineNotes.visibility = View.VISIBLE
            } else {
                tvMedicineNotes.text = ""
                tvMedicineNotes.visibility = View.GONE
            }

            // Display enabled reminder times.
            val enabledTimes = item.reminderTimes
                .filter { it.isEnabled }
                .sortedWith(compareBy({ it.hour }, { it.minute }))

            tvReminderTimes.text = if (enabledTimes.isEmpty()) {
                context.getString(R.string.no_active_reminders)
            } else {
                val times = enabledTimes.joinToString(", ") {
                    String.format(
                        Locale.getDefault(),
                        "%02d:%02d",
                        it.hour,
                        it.minute
                    )
                }

                context.getString(R.string.reminder_times_format, times)
            }

            btnEdit.text = context.getString(R.string.edit)
            btnDelete.text = context.getString(R.string.delete)
            btnRefill.text = context.getString(R.string.refill)

            btnEdit.setOnClickListener {
                onEditClick(medicine)
            }

            btnDelete.setOnClickListener {
                onDeleteClick(medicine)
            }

            btnRefill.setOnClickListener {
                onRefillClick(medicine)
            }

            // Display today's dose records and available actions.
            layoutDoseActions.removeAllViews()

            val logs = todayDoseLogs
                .filter { it.medicineId == medicine.id }
                .sortedBy { it.scheduledTime }

            if (logs.isEmpty()) {
                val message = TextView(context).apply {
                    text = context.getString(R.string.no_dose_records_today)
                    textSize = 13f
                    setTextColor(Color.GRAY)
                    setPadding(0, 8, 0, 8)
                }

                layoutDoseActions.addView(message)
                return
            }

            val timeFormat = SimpleDateFormat(
                "hh:mm a",
                Locale.getDefault()
            )

            logs.forEach { log ->
                val doseRow = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 10, 0, 10)
                }

                val label = TextView(context).apply {
                    val localizedStatus = when (log.status) {
                        "PENDING" -> context.getString(R.string.dose_pending)
                        "TAKEN" -> context.getString(R.string.dose_taken)
                        "NOT_TAKEN" -> context.getString(R.string.dose_not_taken)
                        "MISSED" -> context.getString(R.string.dose_missed)
                        else -> log.status.replace("_", " ")
                    }

                    text = context.getString(
                        R.string.dose_status_format,
                        timeFormat.format(Date(log.scheduledTime)),
                        localizedStatus
                    )

                    textSize = 14f
                    setTextColor(Color.DKGRAY)
                }

                doseRow.addView(label)

                if (log.status == "PENDING") {
                    val actions = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                    }

                    val takenButton = Button(context).apply {
                        text = context.getString(R.string.dose_taken_button)
                        isAllCaps = false

                        setOnClickListener {
                            onDoseAction(log, true)
                        }
                    }

                    val notTakenButton = Button(context).apply {
                        text = context.getString(R.string.dose_not_taken_button)
                        isAllCaps = false

                        setOnClickListener {
                            onDoseAction(log, false)
                        }
                    }

                    actions.addView(
                        takenButton,
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    )

                    actions.addView(
                        notTakenButton,
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    )

                    doseRow.addView(actions)
                }

                layoutDoseActions.addView(doseRow)
            }
        }
    }

    companion object {
        private val DiffCallback =
            object : DiffUtil.ItemCallback<MedicineWithTimes>() {

                override fun areItemsTheSame(
                    oldItem: MedicineWithTimes,
                    newItem: MedicineWithTimes
                ): Boolean {
                    return oldItem.medicine.id == newItem.medicine.id
                }

                override fun areContentsTheSame(
                    oldItem: MedicineWithTimes,
                    newItem: MedicineWithTimes
                ): Boolean {
                    return oldItem == newItem
                }
            }
    }
}
