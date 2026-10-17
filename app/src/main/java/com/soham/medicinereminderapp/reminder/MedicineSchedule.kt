package com.soham.medicinereminderapp.reminder

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object MedicineSchedule {

    const val DATE_RANGE = "DATE_RANGE"
    const val SELECTED_DATES = "SELECTED_DATES"

    private fun formatter() =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
        }

    fun formatDate(calendar: Calendar): String {
        return formatter().format(calendar.time)
    }

    fun parseDate(date: String?): Calendar? {
        if (date.isNullOrBlank()) return null

        return try {
            val parsed: Date = formatter().parse(date) ?: return null

            Calendar.getInstance().apply {
                time = parsed
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun isEligible(
        scheduleType: String,
        date: String,
        startDate: String?,
        endDate: String?,
        selectedDates: Set<String>
    ): Boolean {
        return when (scheduleType) {
            DATE_RANGE -> {
                val start = startDate ?: return false
                val end = endDate ?: return false

                date >= start && date <= end && start <= end
            }

            SELECTED_DATES -> date in selectedDates

            else -> false
        }
    }

    fun nextOccurrence(
        hour: Int,
        minute: Int,
        scheduleType: String,
        startDate: String?,
        endDate: String?,
        selectedDates: Set<String>,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? {
        val now = Calendar.getInstance().apply {
            timeInMillis = nowMillis
        }

        val today = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // A range can be searched forward from today.
        // Individual dates are sorted and checked explicitly.
        val candidateDates = when (scheduleType) {
            DATE_RANGE -> {
                val start = startDate ?: return null
                val end = endDate ?: return null
                if (start > end) return null

                val first = maxOf(start, formatDate(today))
                if (first > end) return null

                generateSequence(first) { current ->
                    val day = parseDate(current) ?: return@generateSequence null
                    day.add(Calendar.DAY_OF_MONTH, 1)
                    formatDate(day).takeIf { it <= end }
                }.toList()
            }

            SELECTED_DATES -> selectedDates.sorted()

            else -> emptyList()
        }

        for (date in candidateDates) {
            val day = parseDate(date) ?: continue

            if (!isEligible(
                    scheduleType,
                    date,
                    startDate,
                    endDate,
                    selectedDates
                )
            ) continue

            day.set(Calendar.HOUR_OF_DAY, hour)
            day.set(Calendar.MINUTE, minute)
            day.set(Calendar.SECOND, 0)
            day.set(Calendar.MILLISECOND, 0)

            if (day.timeInMillis > now.timeInMillis) {
                return day.timeInMillis
            }
        }

        return null
    }
}