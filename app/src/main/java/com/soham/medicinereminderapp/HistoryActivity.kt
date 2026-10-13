
package com.soham.medicinereminderapp

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.soham.medicinereminderapp.data.AppDatabase
import com.soham.medicinereminderapp.data.DoseLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var historyContainer: LinearLayout
    private lateinit var tvEmptyHistory: TextView

    private val dateFormat: SimpleDateFormat
        get() = SimpleDateFormat(
            "dd MMM yyyy, hh:mm a",
            Locale.getDefault()
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        historyContainer = findViewById(R.id.historyContainer)
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory)

        val dao = AppDatabase.getDatabase(applicationContext)
            .medicineDao()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                dao.getAllDoseLogs().collectLatest { logs ->
                    renderHistory(logs)
                }
            }
        }
    }

    private suspend fun renderHistory(logs: List<DoseLog>) {
        withContext(Dispatchers.Main) {
            historyContainer.removeAllViews()

            tvEmptyHistory.visibility =
                if (logs.isEmpty()) View.VISIBLE else View.GONE

            logs.forEach { log ->
                historyContainer.addView(createHistoryCard(log))
            }
        }
    }

    private fun createHistoryCard(log: DoseLog): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))

            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.rgb(229, 232, 240))
            }

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }

            elevation = dp(2).toFloat()
        }

        val medicineName = TextView(this).apply {
            text = log.medicineName
            textSize = 18f
            setTextColor(Color.rgb(32, 37, 68))
            setTypeface(null, Typeface.BOLD)
        }

        val dosage = TextView(this).apply {
            text = "Dosage: ${log.dosage}"
            textSize = 14f
            setTextColor(Color.rgb(115, 121, 145))
            setPadding(0, dp(6), 0, 0)
        }

        val scheduledTime = TextView(this).apply {
            text = "Scheduled: ${
                dateFormat.format(Date(log.scheduledTime))
            }"
            textSize = 14f
            setTextColor(Color.rgb(80, 85, 105))
            setPadding(0, dp(10), 0, 0)
        }

        val status = TextView(this).apply {
            text = "Status: ${log.status.replace('_', ' ')}"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(10), 0, 0)

            setTextColor(
                when (log.status.uppercase(Locale.ROOT)) {
                    "TAKEN" -> Color.rgb(33, 136, 56)
                    "NOT_TAKEN", "MISSED" ->
                        Color.rgb(198, 40, 40)
                    else -> Color.rgb(148, 98, 0)
                }
            )
        }

        card.addView(medicineName)
        card.addView(dosage)
        card.addView(scheduledTime)
        card.addView(status)

        log.takenTime?.let { takenTime ->
            val takenAt = TextView(this).apply {
                text = "Taken at: ${
                    dateFormat.format(Date(takenTime))
                }"
                textSize = 14f
                setTextColor(Color.rgb(80, 85, 105))
                setPadding(0, dp(6), 0, 0)
            }

            card.addView(takenAt)
        }

        return card
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
