
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
    private val onDoseAction: (DoseLog, Boolean) -> Unit
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

        private val layoutDoseActions: LinearLayout =
            itemView.findViewById(R.id.layoutDoseActions)

        fun bind(item: MedicineWithTimes) {
            val medicine = item.medicine

            tvMedicineName.text = medicine.name
            tvDosage.text = medicine.dosage

            // Display medicine photo
            val imagePath = medicine.imagePath

            if (!imagePath.isNullOrBlank() && File(imagePath).exists()) {
                ivMedicineImage.setImageURI(Uri.fromFile(File(imagePath)))
            } else {
                ivMedicineImage.setImageResource(
                    android.R.drawable.ic_menu_gallery
                )
            }

            // Display optional notes
            val notes = medicine.notes?.trim()

            if (!notes.isNullOrEmpty()) {
                tvMedicineNotes.text = "Notes: $notes"
                tvMedicineNotes.visibility = View.VISIBLE
            } else {
                tvMedicineNotes.text = ""
                tvMedicineNotes.visibility = View.GONE
            }

            // Display enabled reminder times
            val enabledTimes = item.reminderTimes
                .filter { it.isEnabled }
                .sortedWith(compareBy({ it.hour }, { it.minute }))

            tvReminderTimes.text = if (enabledTimes.isEmpty()) {
                "No active reminders"
            } else {
                "Reminder: " + enabledTimes.joinToString(", ") {
                    String.format(
                        Locale.getDefault(),
                        "%02d:%02d",
                        it.hour,
                        it.minute
                    )
                }
            }

            btnEdit.setOnClickListener {
                onEditClick(medicine)
            }

            btnDelete.setOnClickListener {
                onDeleteClick(medicine)
            }

            // Display today's dose actions
            layoutDoseActions.removeAllViews()

            val logs = todayDoseLogs
                .filter { it.medicineId == medicine.id }
                .sortedBy { it.scheduledTime }

            if (logs.isEmpty()) {
                val message = TextView(itemView.context).apply {
                    text = "No dose records for today"
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
                val doseRow = LinearLayout(itemView.context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 10, 0, 10)
                }

                val label = TextView(itemView.context).apply {
                    text = "${timeFormat.format(Date(log.scheduledTime))} • " +
                            log.status.replace("_", " ")
                    textSize = 14f
                    setTextColor(Color.DKGRAY)
                }

                doseRow.addView(label)

                if (log.status == "PENDING") {
                    val actions = LinearLayout(itemView.context).apply {
                        orientation = LinearLayout.HORIZONTAL
                    }

                    val takenButton = Button(itemView.context).apply {
                        text = "Taken"
                        isAllCaps = false

                        setOnClickListener {
                            onDoseAction(log, true)
                        }
                    }

                    val notTakenButton = Button(itemView.context).apply {
                        text = "Not Taken"
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