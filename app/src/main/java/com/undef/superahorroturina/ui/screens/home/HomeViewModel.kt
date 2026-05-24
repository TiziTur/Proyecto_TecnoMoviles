// ViewModel de la pantalla principal conectado al backend real.
// Carga el nombre del usuario desde DataStore y las compras recientes desde la API.
// v2: también lee monthlyLimit desde ThemeDataStore para mostrar indicador de presupuesto.
package com.undef.superahorroturina.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undef.superahorroturina.data.local.SessionDataStore
import com.undef.superahorroturina.data.local.ThemeDataStore
import com.undef.superahorroturina.data.repository.ApiResult
import com.undef.superahorroturina.data.repository.PurchaseRepository
import com.undef.superahorroturina.ui.state.HomeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val sessionDataStore: SessionDataStore,
    private val themeDataStore: ThemeDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
        // Observar cambios en el límite mensual en tiempo real
        viewModelScope.launch {
            themeDataStore.monthlyLimit.collect { limit ->
                _uiState.value = _uiState.value.copy(monthlyLimit = limit)
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val session      = sessionDataStore.session.first()
            val monthlyLimit = themeDataStore.monthlyLimit.first()

            when (val result = purchaseRepository.getPurchases()) {
                is ApiResult.Success -> {
                    val purchases = result.data
                    val recent    = purchases.sortedByDescending { it.date }.take(5)
                    val now       = java.time.LocalDate.now()
                    val thisMonth = purchases
                        .filter { it.date.monthValue == now.monthValue && it.date.year == now.year }
                        .sumOf { it.total }

                    _uiState.value = HomeUiState(
                        isLoading        = false,
                        isRefreshing     = false,
                        userName         = session.firstName,
                        totalThisMonth   = thisMonth,
                        monthlyLimit     = monthlyLimit,
                        recentPurchases  = recent,
                        purchaseCount    = purchases.size,
                        supermarketCount = purchases.map { it.supermarket }.distinct().size
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading    = false,
                        isRefreshing = false,
                        userName     = session.firstName,
                        monthlyLimit = monthlyLimit
                    )
                }
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true, isLoading = false)
        loadData()
    }
}
