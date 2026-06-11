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
