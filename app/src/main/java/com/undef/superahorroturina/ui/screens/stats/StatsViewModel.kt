// ViewModel de estadísticas: agrupa compras por mes y por supermercado.
package com.undef.superahorroturina.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.repository.ApiResult
import com.undef.superahorroturina.data.repository.PurchaseRepository
import com.undef.superahorroturina.model.Purchase
import com.undef.superahorroturina.model.StatSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class StatsUiState(
    val isLoading: Boolean = true,
    val monthlyStats: List<StatSummary> = emptyList(),
    val supermarketStats: List<StatSummary> = emptyList(),
    val topProducts: List<StatSummary> = emptyList(),
    val totalAllTime: Double = 0.0,
    val error: String = ""
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val purchaseRepository: PurchaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init { loadStats() }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = purchaseRepository.getPurchases()) {
                is ApiResult.Success -> {
                    val purchases = result.data
                    _uiState.value = StatsUiState(
                        isLoading        = false,
                        monthlyStats     = buildMonthlyStats(purchases),
                        supermarketStats = buildSupermarketStats(purchases),
                        topProducts      = buildTopProducts(purchases),
                        totalAllTime     = purchases.sumOf { it.total }
                    )
                }
                is ApiResult.Error -> _uiState.value = StatsUiState(
                    isLoading = false,
                    error     = result.message
                )
            }
        }
    }

    private fun buildMonthlyStats(purchases: List<Purchase>): List<StatSummary> {
        val fmt = DateTimeFormatter.ofPattern("MMM yy")
        return purchases
            .groupBy { it.date.format(fmt) }
            .map { (month, ps) -> StatSummary(month, ps.sumOf { it.total }) }
            .sortedBy { it.label }
            .takeLast(6)
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
