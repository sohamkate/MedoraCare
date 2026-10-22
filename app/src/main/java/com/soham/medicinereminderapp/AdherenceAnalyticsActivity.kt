package com.soham.medicinereminderapp

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.soham.medicinereminderapp.data.AppDatabase
import com.soham.medicinereminderapp.data.DoseLog
import com.soham.medicinereminderapp.repository.MedicineRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AdherenceAnalyticsActivity : AppCompatActivity() {

    private lateinit var repository: MedicineRepository
    private lateinit var bottomNavigation: BottomNavigationView

    private lateinit var tvPeriod: TextView
    private lateinit var tvPercent: TextView
    private lateinit var progress: ProgressBar
    private lateinit var tvTaken: TextView
    private lateinit var tvMissed: TextView
    private lateinit var tvNotTaken: TextView
    private lateinit var tvPending: TextView
    private lateinit var tvTotal: TextView
    private lateinit var tvInsight: TextView
    private lateinit var tvEmpty: TextView

    private lateinit var tvCalendarMonth: TextView
    private lateinit var calendarGrid: LinearLayout

    private var displayedMonth = Calendar.getInstance()
        .get(Calendar.MONTH)

    private var displayedYear = Calendar.getInstance()
        .get(Calendar.YEAR)

    private var analyticsJob: Job? = null
    private var calendarJob: Job? = null

    private var monthLogs: List<DoseLog> = emptyList()

    private val monthFormatter =
        SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    private val dateFormatter =
        SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())

    private val timeFormatter =
        SimpleDateFormat("hh:mm a", Locale.getDefault())

    private val green = Color.rgb(101, 174, 36)
    private val red = Color.rgb(217, 54, 80)
    private val amber = Color.rgb(224, 164, 28)
    private val grey = Color.rgb(150, 157, 166)
    private val dark = Color.rgb(32, 37, 68)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adherence_analytics)

        val database = AppDatabase.getDatabase(applicationContext)
        repository = MedicineRepository(database.medicineDao())

        // IDs matching your actual XML
        bottomNavigation = findViewById(R.id.bottomNavigation)

        tvPeriod = findViewById(R.id.tvAnalyticsPeriod)
        tvPercent = findViewById(R.id.tvAdherencePercent)
        progress = findViewById(R.id.progressAdherence)

        tvTaken = findViewById(R.id.tvAnalyticsTaken)
        tvMissed = findViewById(R.id.tvAnalyticsMissed)
        tvNotTaken = findViewById(R.id.tvAnalyticsNotTaken)
        tvPending = findViewById(R.id.tvAnalyticsPending)
        tvTotal = findViewById(R.id.tvAnalyticsTotal)

        tvInsight = findViewById(R.id.tvAnalyticsInsight)
        tvEmpty = findViewById(R.id.tvAnalyticsEmpty)

        tvCalendarMonth = findViewById(R.id.tvCalendarMonth)
        calendarGrid = findViewById(R.id.calendarGrid)

        findViewById<View>(R.id.btnPreviousMonth)
            .setOnClickListener {
                changeMonth(-1)
            }

        findViewById<View>(R.id.btnNextMonth)
            .setOnClickListener {
                changeMonth(1)
            }

        setupBottomNavigation()
        observeAnalytics()
        observeCalendarMonth()
    }

    private fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        // ADD THIS LINE: Removes tint so your original images show
        bottomNavigation.itemIconTintList = null

        bottomNavigation.selectedItemId = R.id.nav_analytics // Make sure Analytics is selected

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_analytics -> true
                else -> false
            }
        }
    }

    private fun navigateTo(destination: Class<*>) {
        if (destination == this::class.java) return

        startActivity(
            Intent(this, destination).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
    }

    // ---------------------------------------------------------
    // LAST 30 DAYS ANALYTICS
    // ---------------------------------------------------------

    private fun observeAnalytics() {
        analyticsJob?.cancel()

        val (start, end) = dateWindow(30)
        tvPeriod.text = "Last 30 days"

        analyticsJob = lifecycleScope.launch {
            repository.getDoseLogsBetween(start, end)
                .collectLatest { logs ->
                    displayAnalytics(logs)
                }
        }
    }

    private fun dateWindow(days: Int): Pair<Long, Long> {
        val endCalendar = Calendar.getInstance()
        val end = endCalendar.timeInMillis

        val startCalendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -(days - 1))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return startCalendar.timeInMillis to end
    }

    private fun displayAnalytics(logs: List<DoseLog>) {
        val taken = logs.count {
            it.status.equals("TAKEN", ignoreCase = true)
        }

        val missed = logs.count {
            it.status.equals("MISSED", ignoreCase = true)
        }

        val notTaken = logs.count {
            it.status.equals("NOT_TAKEN", ignoreCase = true) ||
                    it.status.equals("SKIPPED", ignoreCase = true)
        }

        val pending = logs.count {
            it.status.equals("PENDING", ignoreCase = true)
        }

        val total = logs.size

        // Pending doses are excluded from the adherence percentage.
        val completedDoses = taken + missed + notTaken

        val percentage = if (completedDoses > 0) {
            (taken * 100.0 / completedDoses).toInt()
        } else {
            0
        }

        tvPercent.text = "$percentage%"
        progress.progress = percentage

        tvTaken.text = getString(
            R.string.doses_taken_count,
            taken
        )

        tvMissed.text = getString(
            R.string.doses_missed_count,
            missed
        )

        tvNotTaken.text = getString(
            R.string.doses_not_taken_count,
            notTaken
        )

        tvPending.text = getString(
            R.string.doses_pending_count,
            pending
        )

        tvTotal.text = getString(
            R.string.doses_total_count,
            total
        )

        tvInsight.text = when {
            completedDoses == 0 ->
                getString(R.string.adherence_empty)

            percentage >= 80 ->
                getString(R.string.adherence_insight_high)

            percentage >= 50 ->
                getString(R.string.adherence_insight_medium)

            else ->
                getString(R.string.adherence_insight_low)
        }

        tvEmpty.visibility =
            if (total == 0) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------
    // MONTHLY CALENDAR
    // ---------------------------------------------------------

    private fun changeMonth(amount: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayedYear)
            set(Calendar.MONTH, displayedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, amount)
        }

        displayedMonth = calendar.get(Calendar.MONTH)
        displayedYear = calendar.get(Calendar.YEAR)

        observeCalendarMonth()
    }

    private fun observeCalendarMonth() {
        calendarJob?.cancel()

        val monthStartCalendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayedYear)
            set(Calendar.MONTH, displayedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val start = monthStartCalendar.timeInMillis

        val monthEndCalendar =
            (monthStartCalendar.clone() as Calendar).apply {
                add(Calendar.MONTH, 1)
            }

        val end = monthEndCalendar.timeInMillis

        tvCalendarMonth.text = monthFormatter.format(Date(start))

        calendarJob = lifecycleScope.launch {
            repository.getDoseLogsBetween(start, end)
                .collectLatest { logs ->
                    monthLogs = logs
                    renderCalendar()
                }
        }
    }

    private fun renderCalendar() {
        calendarGrid.removeAllViews()

        val weekdayNames = listOf("S", "M", "T", "W", "T", "F", "S")

        val weekdayRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        weekdayNames.forEach { dayName ->
            weekdayRow.addView(
                TextView(this).apply {
                    text = dayName
                    gravity = Gravity.CENTER
                    setTextColor(dark)
                    setTypeface(null, Typeface.BOLD)
                    textSize = 12f

                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        dp(32),
                        1f
                    )
                }
            )
        }

        calendarGrid.addView(weekdayRow)

        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayedYear)
            set(Calendar.MONTH, displayedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }

        val firstDayOfWeek =
            calendar.get(Calendar.DAY_OF_WEEK) - 1

        val daysInMonth =
            calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        var currentDay = 1

        while (currentDay <= daysInMonth) {
            val weekRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            for (column in 0 until 7) {
                val dayNumber = when {
                    currentDay > daysInMonth -> null

                    currentDay == 1 &&
                            column < firstDayOfWeek -> null

                    else -> currentDay++
                }

                if (dayNumber == null) {
                    weekRow.addView(
                        View(this),
                        LinearLayout.LayoutParams(
                            0,
                            dp(42),
                            1f
                        )
                    )
                } else {
                    weekRow.addView(createDayCell(dayNumber))
                }
            }

            calendarGrid.addView(weekRow)
        }
    }

    private fun createDayCell(day: Int): View {
        val dayCalendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayedYear)
            set(Calendar.MONTH, displayedMonth)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val dayStart = dayCalendar.timeInMillis

        val nextDayCalendar =
            (dayCalendar.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, 1)
            }

        val dayEnd = nextDayCalendar.timeInMillis

        val dayLogs = monthLogs.filter {
            it.scheduledTime >= dayStart &&
                    it.scheduledTime < dayEnd
        }

        val statusColor = getDayStatusColor(dayLogs)

        val isToday = Calendar.getInstance().let { today ->
            today.get(Calendar.YEAR) == displayedYear &&
                    today.get(Calendar.MONTH) == displayedMonth &&
                    today.get(Calendar.DAY_OF_MONTH) == day
        }

        val cellBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f

            setColor(
                if (dayLogs.isEmpty()) {
                    Color.TRANSPARENT
                } else {
                    statusColor
                }
            )

            if (isToday) {
                setStroke(dp(2), dark)
            } else if (dayLogs.isNotEmpty()) {
                setStroke(dp(1), statusColor)
            }
        }

        return TextView(this).apply {
            text = day.toString()
            gravity = Gravity.CENTER
            textSize = 13f

            setTextColor(
                if (dayLogs.isNotEmpty()) Color.WHITE else dark
            )

            background = cellBackground

            layoutParams = LinearLayout.LayoutParams(
                0,
                dp(42),
                1f
            ).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }

            contentDescription = "$day, ${tvCalendarMonth.text}"

            setOnClickListener {
                showDayDetails(day, dayLogs)
            }
        }
    }

    private fun getDayStatusColor(logs: List<DoseLog>): Int {
        if (logs.isEmpty()) return Color.TRANSPARENT

        val hasMissed = logs.any {
            it.status.equals("MISSED", ignoreCase = true) ||
                    it.status.equals("NOT_TAKEN", ignoreCase = true) ||
                    it.status.equals("SKIPPED", ignoreCase = true)
        }

        val hasPending = logs.any {
            it.status.equals("PENDING", ignoreCase = true)
        }

        val allTaken = logs.all {
            it.status.equals("TAKEN", ignoreCase = true)
        }

        return when {
            hasMissed -> red
            hasPending -> amber
            allTaken -> green
            else -> grey
        }
    }

    private fun showDayDetails(day: Int, logs: List<DoseLog>) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayedYear)
            set(Calendar.MONTH, displayedMonth)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val dateTitle = dateFormatter.format(calendar.time)

        if (logs.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(dateTitle)
                .setMessage("No dose records for this day.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val details = logs
            .sortedBy { it.scheduledTime }
            .joinToString("\n\n") { log ->

                val status = when (
                    log.status.uppercase(Locale.ROOT)
                ) {
                    "TAKEN" -> "Taken"
                    "MISSED" -> "Missed"
                    "NOT_TAKEN" -> "Not taken"
                    "SKIPPED" -> "Skipped"
                    "PENDING" -> "Pending"
                    else -> log.status
                }

                "${log.medicineName}\n" +
                        "${log.dosage}\n" +
                        "Scheduled: ${
                            timeFormatter.format(Date(log.scheduledTime))
                        }\n" +
                        "Status: $status"
            }

        AlertDialog.Builder(this)
            .setTitle(dateTitle)
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun dp(value: Int): Int {
        return (
                value * resources.displayMetrics.density
                ).toInt()
    }

    override fun onDestroy() {
        analyticsJob?.cancel()
        calendarJob?.cancel()
        super.onDestroy()
    }
}