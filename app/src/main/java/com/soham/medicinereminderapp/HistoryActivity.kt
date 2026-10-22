
package com.soham.medicinereminderapp

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.soham.medicinereminderapp.data.AppDatabase
import com.soham.medicinereminderapp.data.DoseLog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var historyContainer: LinearLayout
    private lateinit var tvEmptyHistory: TextView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var etSearchHistory: EditText

    private var allLogs: List<DoseLog> = emptyList()
    private var searchQuery: String = ""

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
        etSearchHistory = findViewById(R.id.etSearchHistory)

        setupSearch()
        setupBottomNavigation()
        observeDoseHistory()
    }

    private fun setupSearch() {
        etSearchHistory.addTextChangedListener { editable ->
            searchQuery = editable
                ?.toString()
                ?.trim()
                .orEmpty()

            renderHistory()
        }

        etSearchHistory.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                etSearchHistory.clearFocus()
                true
            } else {
                false
            }
        }
    }

    private fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        // ADD THIS LINE: Removes tint so your original images show
        bottomNavigation.itemIconTintList = null

        bottomNavigation.selectedItemId = R.id.nav_history // Make sure History is selected

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_history -> true
                R.id.nav_analytics -> {
                    startActivity(Intent(this, AdherenceAnalyticsActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun navigateTo(destination: Class<*>) {
        if (destination == this::class.java) return

        val intent = Intent(this, destination).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        startActivity(intent)
    }

    private fun observeDoseHistory() {
        val dao = AppDatabase
            .getDatabase(applicationContext)
            .medicineDao()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                dao.getAllDoseLogs().collectLatest { logs ->
                    allLogs = logs
                    renderHistory()
                }
            }
        }
    }

    private fun renderHistory() {
        historyContainer.removeAllViews()

        val filteredLogs = if (searchQuery.isBlank()) {
            allLogs
        } else {
            allLogs.filter { log ->
                log.medicineName.contains(
                    searchQuery,
                    ignoreCase = true
                )
            }
        }

        if (filteredLogs.isEmpty()) {
            tvEmptyHistory.visibility = View.VISIBLE

            tvEmptyHistory.setText(
                if (allLogs.isEmpty()) {
                    R.string.no_dose_history_available_yet
                } else {
                    R.string.no_matching_medicine_history
                }
            )

            return
        }

        tvEmptyHistory.visibility = View.GONE

        filteredLogs.forEach { log ->
            historyContainer.addView(createHistoryCard(log))
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
            text = getString(
                R.string.history_dosage_format,
                log.dosage
            )
            textSize = 14f
            setTextColor(Color.rgb(115, 121, 145))
            setPadding(0, dp(6), 0, 0)
        }

        val scheduledTime = TextView(this).apply {
            text = getString(
                R.string.history_scheduled_format,
                dateFormat.format(Date(log.scheduledTime))
            )
            textSize = 14f
            setTextColor(Color.rgb(80, 85, 105))
            setPadding(0, dp(10), 0, 0)
        }

        val status = TextView(this).apply {
            text = getString(
                R.string.history_status_format,
                log.status.replace('_', ' ')
            )
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(10), 0, 0)

            setTextColor(
                when (log.status.uppercase(Locale.ROOT)) {
                    "TAKEN" -> Color.rgb(33, 136, 56)

                    "NOT_TAKEN", "MISSED", "SKIPPED" ->
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
                text = getString(
                    R.string.history_taken_at_format,
                    dateFormat.format(Date(takenTime))
                )
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
