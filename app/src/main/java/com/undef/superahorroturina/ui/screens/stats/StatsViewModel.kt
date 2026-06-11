// ViewModel de estadísticas: agrupa compras por mes, supermercado, día de semana,
// presupuesto y precios para alimentar las 4 pestañas de StatsScreen.
package com.undef.superahorroturina.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.local.ThemeDataStore
import com.undef.superahorroturina.data.repository.ApiResult
import com.undef.superahorroturina.data.repository.PurchaseRepository
import com.undef.superahorroturina.model.PriceChange
import com.undef.superahorroturina.model.Purchase
import com.undef.superahorroturina.model.StatSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class StatsUiState(
    val isLoading: Boolean = true,
    val monthlyStats: List<StatSummary> = emptyList(),
    val supermarketStats: List<StatSummary> = emptyList(),
    val topProducts: List<StatSummary> = emptyList(),
    val totalAllTime: Double = 0.0,
    val avgPurchase: Double = 0.0,
    val error: String = "",
    // Presupuesto y proyección
    val monthlyLimit: Double = 0.0,
    val currentMonthSpent: Double = 0.0,
    val projectedMonthSpent: Double = 0.0,
    // Comparación con el mes anterior
    val previousMonthSpent: Double = 0.0,
    val monthOverMonthPct: Double? = null,
    // Gasto por día de semana
    val weekdayStats: List<StatSummary> = emptyList(),
    // Ticket promedio por supermercado
    val avgTicketBySupermarket: List<StatSummary> = emptyList(),
    // Productos con mayor aumento de precio
    val priceIncreases: List<PriceChange> = emptyList(),
    // Frecuencia y tamaño de canasta
    val purchaseCountThisMonth: Int = 0,
    val avgItemsPerPurchase: Double = 0.0
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val themeDataStore: ThemeDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init { loadStats() }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            purchaseRepository.refreshPurchases()
            val purchases = purchaseRepository.getPurchasesFlow().first()
            val purchasesWithProducts = purchases.map { purchase ->
                val detail = purchaseRepository.getPurchase(purchase.id)
                if (detail is ApiResult.Success) detail.data else purchase
            }
            val monthlyLimit = themeDataStore.monthlyLimit.first().toDouble()

            val today = LocalDate.now()
            val totalAllTime = purchases.sumOf { it.total }
            val currentMonthSpent = calcCurrentMonthSpent(purchases, today)
            val previousMonthSpent = calcPreviousMonthSpent(purchases, today)

            _uiState.value = StatsUiState(
                isLoading              = false,
                monthlyStats           = buildMonthlyStats(purchases),
                supermarketStats       = buildSupermarketStats(purchases),
                topProducts            = buildTopProducts(purchasesWithProducts),
                totalAllTime           = totalAllTime,
                avgPurchase            = if (purchases.isNotEmpty()) totalAllTime / purchases.size else 0.0,
                monthlyLimit           = monthlyLimit,
                currentMonthSpent      = currentMonthSpent,
                projectedMonthSpent    = calcProjectedMonthSpent(currentMonthSpent, today),
                previousMonthSpent     = previousMonthSpent,
                monthOverMonthPct      = calcMonthOverMonthPct(currentMonthSpent, previousMonthSpent),
                weekdayStats           = calcWeekdayStats(purchases),
                avgTicketBySupermarket = calcAvgTicketBySupermarket(purchases),
                priceIncreases         = calcPriceIncreases(purchasesWithProducts),
                purchaseCountThisMonth = calcPurchaseCountThisMonth(purchases, today),
                avgItemsPerPurchase    = calcAvgItemsPerPurchase(purchases, today)
            )
        }
    }

    private fun buildMonthlyStats(purchases: List<Purchase>): List<StatSummary> {
        val displayFmt = DateTimeFormatter.ofPattern("MMM yy")
        return purchases
            .groupBy { YearMonth.of(it.date.year, it.date.month) }
            .entries
            .sortedBy { (yearMonth, _) -> yearMonth }
            .takeLast(6)
            .map { (yearMonth, ps) ->
                val label = yearMonth.format(displayFmt)
                    .replaceFirstChar { c -> c.uppercaseChar() }
                StatSummary(label, ps.sumOf { it.total })
            }
    }

    private fun buildSupermarketStats(purchases: List<Purchase>): List<StatSummary> =
        purchases
            .groupBy { it.supermarket }
            .map { (name, ps) -> StatSummary(name, ps.sumOf { it.total }) }
            .sortedByDescending { it.amount }
            .take(5)

    private fun buildTopProducts(purchases: List<Purchase>): List<StatSummary> =
        purchases
            .flatMap { it.products }
            .groupBy { it.name }
            .map { (name, ps) -> StatSummary(name, ps.sumOf { it.price * it.quantity }) }
            .sortedByDescending { it.amount }
            .take(5)
}
