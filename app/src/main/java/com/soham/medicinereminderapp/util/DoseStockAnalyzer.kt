package com.soham.medicinereminderapp.util

import kotlin.math.ceil

data class StockAnalysis(
    val currentStock: Double,
    val unitsPerDay: Double,
    val estimatedDaysRemaining: Int?,
    val lowStock: Boolean
)

object DoseStockAnalyzer {

    fun analyze(
        currentStock: Double,
        unitsPerDose: Double,
        dosesPerDay: Int,
        lowStockDays: Int = 3
    ): StockAnalysis? {

        if (!currentStock.isFinite() ||
            !unitsPerDose.isFinite() ||
            currentStock < 0.0 ||
            unitsPerDose <= 0.0 ||
            dosesPerDay <= 0 ||
            lowStockDays < 0
        ) {
            return null
        }

        val unitsPerDay = unitsPerDose * dosesPerDay
        val daysRemaining =
            ceil(currentStock / unitsPerDay).toInt()

        return StockAnalysis(
            currentStock = currentStock,
            unitsPerDay = unitsPerDay,
            estimatedDaysRemaining = daysRemaining,
            lowStock = currentStock / unitsPerDay <= lowStockDays
        )
    }
}