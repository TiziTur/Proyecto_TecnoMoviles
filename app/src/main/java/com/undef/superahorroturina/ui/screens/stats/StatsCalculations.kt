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

fun calcPriceIncreases(purchases: List<Purchase>): List<PriceChange> {
    data class PriceEntry(val date: LocalDate, val price: Double, val displayName: String)

    return purchases
        .flatMap { purchase ->
            purchase.products.map { product ->
                product.name.trim().lowercase() to PriceEntry(purchase.date, product.price, product.name)
            }
        }
        .groupBy({ it.first }, { it.second })
        .mapNotNull { (_, entries) ->
            if (entries.size < 2) return@mapNotNull null
            val sorted = entries.sortedBy { it.date }
            val oldest = sorted.first()
            val newest = sorted.last()
            if (oldest.price <= 0.0) return@mapNotNull null
            val pctChange = (newest.price - oldest.price) / oldest.price * 100
            if (pctChange <= 0.0) return@mapNotNull null
            PriceChange(
                productName = newest.displayName,
                oldPrice = oldest.price,
                newPrice = newest.price,
                pctChange = pctChange
            )
        }
        .sortedByDescending { it.pctChange }
        .take(3)
}

fun calcPurchaseCountThisMonth(purchases: List<Purchase>, today: LocalDate = LocalDate.now()): Int =
    purchases.count { YearMonth.from(it.date) == YearMonth.from(today) }

fun calcAvgItemsPerPurchase(purchases: List<Purchase>, today: LocalDate = LocalDate.now()): Double {
    val thisMonth = purchases.filter { YearMonth.from(it.date) == YearMonth.from(today) }
    return if (thisMonth.isEmpty()) 0.0
    else thisMonth.sumOf { it.productCount }.toDouble() / thisMonth.size
}
