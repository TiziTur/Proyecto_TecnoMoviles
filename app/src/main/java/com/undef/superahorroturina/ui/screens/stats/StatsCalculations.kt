// Funciones puras de cálculo para las estadísticas de StatsScreen.
// Sin dependencias de Android/Hilt para poder testearlas directo con JUnit.
package com.undef.superahorroturina.ui.screens.stats

import com.undef.superahorroturina.model.PriceChange
import com.undef.superahorroturina.model.Purchase
import com.undef.superahorroturina.model.StatSummary
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

fun calcCurrentMonthSpent(purchases: List<Purchase>, today: LocalDate = LocalDate.now()): Double =
    purchases
        .filter { YearMonth.from(it.date) == YearMonth.from(today) }
        .sumOf { it.total }

fun calcPreviousMonthSpent(purchases: List<Purchase>, today: LocalDate = LocalDate.now()): Double =
    purchases
        .filter { YearMonth.from(it.date) == YearMonth.from(today).minusMonths(1) }
        .sumOf { it.total }

fun calcProjectedMonthSpent(currentMonthSpent: Double, today: LocalDate = LocalDate.now()): Double {
    val daysElapsed = today.dayOfMonth
    val daysInMonth = today.lengthOfMonth()
    return currentMonthSpent / daysElapsed * daysInMonth
}

fun calcMonthOverMonthPct(currentMonthSpent: Double, previousMonthSpent: Double): Double? =
    if (previousMonthSpent == 0.0) null
    else (currentMonthSpent - previousMonthSpent) / previousMonthSpent * 100

private val WEEKDAY_LABELS = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

fun calcWeekdayStats(purchases: List<Purchase>): List<StatSummary> {
    val sums = DoubleArray(7)
    purchases.forEach { purchase -> sums[purchase.date.dayOfWeek.value - 1] += purchase.total }
    return WEEKDAY_LABELS.indices.map { StatSummary(WEEKDAY_LABELS[it], sums[it]) }
}

fun calcAvgTicketBySupermarket(purchases: List<Purchase>): List<StatSummary> =
    purchases
        .groupBy { it.supermarket }
        .map { (name, ps) -> StatSummary(name, ps.sumOf { it.total } / ps.size) }
        .sortedByDescending { it.amount }
        .take(5)
